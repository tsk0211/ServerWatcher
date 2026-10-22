package dev.tsk.server.watcher.core.data.aws

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ChallengeNameType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.InitiateAuthRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SW"

sealed class AuthResult {
    data class LoggedIn(val accessToken: String, val idToken: String, val refreshToken: String) : AuthResult()
    data class MfaRequired(val session: String) : AuthResult()
}

@Singleton
class CognitoTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: AwsConfigRepository,
) {
    private companion object {
        const val PREFS_NAME = "cognito_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_ID = "id_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRY = "token_expiry"
        const val REFRESH_BUFFER_MS = 120_000L // refresh 2 min before expiry
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val refreshMutex = Mutex()

    @Volatile private var lastRefreshTime = 0L

    private val _sessionExpiredFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow.asSharedFlow()

    // --- Token utility methods ---

    fun getTokenExpiryTime(): Long = prefs.getLong(KEY_EXPIRY, 0L)

    fun isTokenExpiringSoon(minutesLeft: Int = 2): Boolean {
        val expiry = getTokenExpiryTime()
        if (expiry == 0L) return true
        return System.currentTimeMillis() > expiry - minutesLeft * 60_000L
    }

    fun getTimeSinceLastRefresh(): Long =
        if (lastRefreshTime == 0L) Long.MAX_VALUE
        else System.currentTimeMillis() - lastRefreshTime

    fun shouldRefreshToken(): Boolean {
        if (refreshMutex.isLocked) return false
        return isTokenExpiringSoon(minutesLeft = 2)
    }

    suspend fun autoRefreshIfNeeded() {
        if (shouldRefreshToken()) {
            Log.d(TAG, "[Token] autoRefreshIfNeeded — proactively refreshing (expires in ${(getTokenExpiryTime() - System.currentTimeMillis()) / 1000}s)")
            getValidAccessToken()
        }
    }

    suspend fun emitSessionExpired() {
        Log.e(TAG, "[Token] emitSessionExpired — forcing logout")
        _sessionExpiredFlow.emit(Unit)
    }

    // --- Auth flows ---

    suspend fun login(email: String, password: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val config = configRepository.getConfig() ?: error("AWS not configured — complete onboarding first")
            Log.d(TAG, "[Auth] login start → region=${config.region} clientId=${config.clientId}")
            val client = CognitoIdentityProviderClient { region = config.region }
            val response = client.initiateAuth(
                InitiateAuthRequest {
                    authFlow = AuthFlowType.UserPasswordAuth
                    clientId = config.clientId
                    authParameters = mapOf(
                        "USERNAME" to email,
                        "PASSWORD" to password,
                    )
                }
            )
            client.close()
            Log.d(TAG, "[Auth] initiateAuth response → challenge=${response.challengeName}")

            when {
                response.challengeName == ChallengeNameType.SoftwareTokenMfa ||
                response.challengeName == ChallengeNameType.SmsMfa -> {
                    Log.d(TAG, "[Auth] MFA required")
                    val session = response.session ?: error("Missing MFA session")
                    AuthResult.MfaRequired(session)
                }
                response.authenticationResult != null -> {
                    val result = response.authenticationResult!!
                    val expiresIn = result.expiresIn ?: 3600
                    Log.d(TAG, "[Auth] login SUCCESS — expiresIn=${expiresIn}s")
                    saveTokens(
                        accessToken = result.accessToken ?: error("Missing access token"),
                        idToken = result.idToken ?: error("Missing ID token"),
                        refreshToken = result.refreshToken ?: error("Missing refresh token"),
                        expiresInSeconds = expiresIn,
                    )
                    lastRefreshTime = System.currentTimeMillis()
                    AuthResult.LoggedIn(
                        accessToken = result.accessToken!!,
                        idToken = result.idToken!!,
                        refreshToken = result.refreshToken!!,
                    )
                }
                else -> error("Unexpected auth state: ${response.challengeName}")
            }
        }.onFailure { e ->
            Log.e(TAG, "[Auth] login FAILED → ${e::class.simpleName}: ${e.message}", e)
        }
    }

    suspend fun submitMfa(session: String, code: String): Result<AuthResult.LoggedIn> = withContext(Dispatchers.IO) {
        runCatching {
            val config = configRepository.getConfig() ?: error("AWS not configured — complete onboarding first")
            Log.d(TAG, "[Auth] submitMfa start")
            val client = CognitoIdentityProviderClient { region = config.region }
            val response = client.respondToAuthChallenge(
                RespondToAuthChallengeRequest {
                    clientId = config.clientId
                    challengeName = ChallengeNameType.SoftwareTokenMfa
                    this.session = session
                    challengeResponses = mapOf("SOFTWARE_TOKEN_MFA_CODE" to code)
                }
            )
            client.close()

            val result = response.authenticationResult ?: error("MFA failed: no tokens returned")
            val expiresIn = result.expiresIn ?: 3600
            Log.d(TAG, "[Auth] MFA SUCCESS — expiresIn=${expiresIn}s")
            saveTokens(
                accessToken = result.accessToken ?: error("Missing access token"),
                idToken = result.idToken ?: error("Missing ID token"),
                refreshToken = result.refreshToken ?: error("Missing refresh token"),
                expiresInSeconds = expiresIn,
            )
            lastRefreshTime = System.currentTimeMillis()
            AuthResult.LoggedIn(
                accessToken = result.accessToken!!,
                idToken = result.idToken!!,
                refreshToken = result.refreshToken!!,
            )
        }.onFailure { e ->
            Log.e(TAG, "[Auth] MFA FAILED → ${e::class.simpleName}: ${e.message}", e)
        }
    }

    // Returns a valid (non-expired) access token, refreshing automatically if needed.
    // Uses a Mutex to prevent concurrent refresh calls.
    suspend fun getValidAccessToken(): String = withContext(Dispatchers.IO) {
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        val accessToken = prefs.getString(KEY_ACCESS, null)

        if (accessToken != null && System.currentTimeMillis() < expiry - REFRESH_BUFFER_MS) {
            Log.d(TAG, "[Token] using cached token (expires in ${(expiry - System.currentTimeMillis()) / 1000}s)")
            return@withContext accessToken
        }

        refreshMutex.withLock {
            // Double-check inside lock — another coroutine may have refreshed while we waited
            val freshExpiry = prefs.getLong(KEY_EXPIRY, 0L)
            val freshToken = prefs.getString(KEY_ACCESS, null)
            if (freshToken != null && System.currentTimeMillis() < freshExpiry - REFRESH_BUFFER_MS) {
                Log.d(TAG, "[Token] refresh done by concurrent call, reusing")
                return@withContext freshToken
            }

            Log.d(TAG, "[Token] refreshing token (timeSinceLastRefresh=${getTimeSinceLastRefresh() / 1000}s)")
            val config = configRepository.getConfig() ?: run {
                _sessionExpiredFlow.emit(Unit)
                error("AWS not configured — complete onboarding first")
            }
            val refreshToken = prefs.getString(KEY_REFRESH, null) ?: run {
                Log.e(TAG, "[Token] no refresh token — session expired")
                _sessionExpiredFlow.emit(Unit)
                error("Not logged in — no refresh token")
            }

            try {
                val client = CognitoIdentityProviderClient { region = config.region }
                val response = client.initiateAuth(
                    InitiateAuthRequest {
                        authFlow = AuthFlowType.RefreshTokenAuth
                        clientId = config.clientId
                        authParameters = mapOf("REFRESH_TOKEN" to refreshToken)
                    }
                )
                client.close()

                val result = response.authenticationResult ?: run {
                    Log.e(TAG, "[Token] refresh returned null result — session expired")
                    _sessionExpiredFlow.emit(Unit)
                    error("Token refresh failed — null result")
                }
                val expiresIn = result.expiresIn ?: 3600
                saveTokens(
                    accessToken = result.accessToken ?: error("Missing access token"),
                    idToken = result.idToken ?: error("Missing ID token"),
                    refreshToken = result.refreshToken ?: refreshToken,
                    expiresInSeconds = expiresIn,
                )
                lastRefreshTime = System.currentTimeMillis()
                Log.d(TAG, "[Token] refresh SUCCESS — new expiry in ${expiresIn}s")
                result.accessToken!!
            } catch (e: Exception) {
                Log.e(TAG, "[Token] refresh FAILED — ${e::class.simpleName}: ${e.message}", e)
                // Emit session expired for NotAuthorizedException (refresh token itself expired)
                if (e.message?.contains("NotAuthorizedException") == true ||
                    e.message?.contains("invalid_grant") == true) {
                    Log.e(TAG, "[Token] refresh token expired — forcing logout")
                    _sessionExpiredFlow.emit(Unit)
                }
                throw e
            }
        }
    }

    // Returns the current ID token, refreshing if the access token is expiring soon.
    suspend fun getValidIdToken(): String = withContext(Dispatchers.IO) {
        getValidAccessToken() // triggers refresh if needed, which updates idToken too
        prefs.getString(KEY_ID, null) ?: run {
            _sessionExpiredFlow.emit(Unit)
            error("Not logged in — no ID token")
        }
    }

    fun getIdToken(): String =
        prefs.getString(KEY_ID, null) ?: error("Not logged in — no ID token")

    fun logout() {
        Log.d(TAG, "[Auth] logout — clearing tokens")
        lastRefreshTime = 0L
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.getString(KEY_ACCESS, null) != null

    private fun saveTokens(
        accessToken: String,
        idToken: String,
        refreshToken: String,
        expiresInSeconds: Int = 3600,
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_ID, idToken)
            .putString(KEY_REFRESH, refreshToken)
            .putLong(KEY_EXPIRY, System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
        Log.d(TAG, "[Token] saved tokens — expiresIn=${expiresInSeconds}s")
    }
}
