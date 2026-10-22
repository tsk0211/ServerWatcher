package dev.tsk.server.watcher.core.data.aws

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.associateSoftwareToken
import aws.sdk.kotlin.services.cognitoidentityprovider.initiateAuth
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthenticationResultType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ChallengeNameType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.VerifySoftwareTokenResponseType
import aws.sdk.kotlin.services.cognitoidentityprovider.respondToAuthChallenge
import aws.sdk.kotlin.services.cognitoidentityprovider.setUserMfaPreference
import aws.sdk.kotlin.services.cognitoidentityprovider.verifySoftwareToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tsk.server.watcher.core.common.runCatchingCancellable
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import dev.tsk.server.watcher.core.domain.model.AwsConfig
import kotlinx.coroutines.CancellationException
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

/** Identifies an in-progress Cognito sign-in challenge (valid for ~3 minutes). */
data class AuthChallenge(val session: String, val username: String)

enum class MfaMethod(val challengeName: ChallengeNameType, val responseKey: String) {
    TOTP(ChallengeNameType.SoftwareTokenMfa, "SOFTWARE_TOKEN_MFA_CODE"),
    SMS(ChallengeNameType.SmsMfa, "SMS_MFA_CODE"),
}

sealed class AuthResult {
    /** Tokens have been stored; nothing sensitive is passed around in the result. */
    data object LoggedIn : AuthResult()
    data class MfaRequired(val challenge: AuthChallenge, val method: MfaMethod) : AuthResult()
    /** Account was created with a temporary password; the user must choose a permanent one. */
    data class NewPasswordRequired(val challenge: AuthChallenge) : AuthResult()
    /** The pool requires MFA and the user has not enrolled an authenticator yet. */
    data class MfaSetupRequired(val challenge: AuthChallenge) : AuthResult()
}

/**
 * A freshly generated TOTP secret. [session] is only set when enrolling during sign-in;
 * it is null when enrolling an already signed-in user.
 */
data class TotpSetup(val secret: String, val session: String?)

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
        const val KEY_USERNAME = "username"
        const val REFRESH_BUFFER_MS = 120_000L // refresh 2 min before expiry
        const val DEFAULT_EXPIRY_SECONDS = 3600
        const val DEVICE_NAME = "ServerWatcher"
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

    private val _sessionExpiredFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow.asSharedFlow()

    // --- Token state ---

    fun isLoggedIn(): Boolean = prefs.getString(KEY_ACCESS, null) != null

    /** The email/username the user signed in with, for labelling authenticator entries. */
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    private fun isTokenExpiringSoon(): Boolean {
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        return expiry == 0L || System.currentTimeMillis() >= expiry - REFRESH_BUFFER_MS
    }

    private fun cachedAccessTokenOrNull(): String? =
        prefs.getString(KEY_ACCESS, null)?.takeIf { !isTokenExpiringSoon() }

    fun shouldRefreshToken(): Boolean = !refreshMutex.isLocked && isTokenExpiringSoon()

    suspend fun autoRefreshIfNeeded() {
        if (shouldRefreshToken()) {
            Log.d(TAG, "[Token] autoRefreshIfNeeded — proactively refreshing")
            getValidAccessToken()
        }
    }

    /** Clears the stored session and tells the UI to go back to the sign-in screen. */
    fun emitSessionExpired() {
        Log.e(TAG, "[Token] session expired — clearing tokens and forcing logout")
        clearTokens()
        _sessionExpiredFlow.tryEmit(Unit)
    }

    fun logout() {
        Log.d(TAG, "[Auth] logout — clearing tokens")
        clearTokens()
    }

    // --- Sign-in flows ---

    suspend fun login(email: String, password: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            withIdp { config, client ->
                Log.d(TAG, "[Auth] login start")
                val response = client.initiateAuth {
                    authFlow = AuthFlowType.UserPasswordAuth
                    clientId = config.clientId
                    authParameters = mapOf("USERNAME" to email, "PASSWORD" to password)
                }
                handleAuthResponse(
                    fallbackUsername = email,
                    challengeName = response.challengeName,
                    session = response.session,
                    challengeParameters = response.challengeParameters,
                    authResult = response.authenticationResult,
                )
            }
        }.onFailure { e -> Log.e(TAG, "[Auth] login FAILED → ${e::class.simpleName}: ${e.message}", e) }
    }

    suspend fun submitMfa(challenge: AuthChallenge, method: MfaMethod, code: String): Result<AuthResult> =
        respondToChallenge(
            challenge = challenge,
            challengeName = method.challengeName,
            responses = mapOf(method.responseKey to code),
            label = "MFA",
        )

    suspend fun completeNewPassword(challenge: AuthChallenge, newPassword: String): Result<AuthResult> =
        respondToChallenge(
            challenge = challenge,
            challengeName = ChallengeNameType.NewPasswordRequired,
            responses = mapOf("NEW_PASSWORD" to newPassword),
            label = "NewPassword",
        )

    private suspend fun respondToChallenge(
        challenge: AuthChallenge,
        challengeName: ChallengeNameType,
        responses: Map<String, String>,
        label: String,
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            withIdp { config, client ->
                Log.d(TAG, "[Auth] $label challenge response")
                val response = client.respondToAuthChallenge {
                    clientId = config.clientId
                    this.challengeName = challengeName
                    session = challenge.session
                    challengeResponses = responses + ("USERNAME" to challenge.username)
                }
                handleAuthResponse(
                    fallbackUsername = challenge.username,
                    challengeName = response.challengeName,
                    session = response.session,
                    challengeParameters = response.challengeParameters,
                    authResult = response.authenticationResult,
                )
            }
        }.onFailure { e -> Log.e(TAG, "[Auth] $label FAILED → ${e::class.simpleName}: ${e.message}", e) }
    }

    // --- TOTP enrolment ---

    /** Enrol an authenticator during sign-in (pool requires MFA, user has none yet). */
    suspend fun startTotpSetup(challenge: AuthChallenge): Result<TotpSetup> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            withIdp { _, client ->
                val response = client.associateSoftwareToken { session = challenge.session }
                TotpSetup(
                    secret = response.secretCode ?: error("Cognito did not return a secret key"),
                    session = response.session,
                )
            }
        }.onFailure { e -> Log.e(TAG, "[Auth] startTotpSetup FAILED → ${e::class.simpleName}: ${e.message}", e) }
    }

    suspend fun finishTotpSetup(challenge: AuthChallenge, session: String, code: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                withIdp { config, client ->
                    val verified = client.verifySoftwareToken {
                        this.session = session
                        userCode = code
                        friendlyDeviceName = DEVICE_NAME
                    }
                    check(verified.status == VerifySoftwareTokenResponseType.Success) { "That code is not correct" }

                    val response = client.respondToAuthChallenge {
                        clientId = config.clientId
                        challengeName = ChallengeNameType.MfaSetup
                        this.session = verified.session ?: session
                        challengeResponses = mapOf("USERNAME" to challenge.username)
                    }
                    handleAuthResponse(
                        fallbackUsername = challenge.username,
                        challengeName = response.challengeName,
                        session = response.session,
                        challengeParameters = response.challengeParameters,
                        authResult = response.authenticationResult,
                    )
                }
            }.onFailure { e -> Log.e(TAG, "[Auth] finishTotpSetup FAILED → ${e::class.simpleName}: ${e.message}", e) }
        }

    /** Enrol an authenticator for a user who is already signed in (Settings). */
    suspend fun startTotpEnrollment(): Result<TotpSetup> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val accessToken = getValidAccessToken()
            withIdp { _, client ->
                val response = client.associateSoftwareToken { this.accessToken = accessToken }
                TotpSetup(
                    secret = response.secretCode ?: error("Cognito did not return a secret key"),
                    session = null,
                )
            }
        }.onFailure { e -> Log.e(TAG, "[Auth] startTotpEnrollment FAILED → ${e::class.simpleName}: ${e.message}", e) }
    }

    suspend fun finishTotpEnrollment(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val accessToken = getValidAccessToken()
            withIdp { _, client ->
                val verified = client.verifySoftwareToken {
                    this.accessToken = accessToken
                    userCode = code
                    friendlyDeviceName = DEVICE_NAME
                }
                check(verified.status == VerifySoftwareTokenResponseType.Success) { "That code is not correct" }

                client.setUserMfaPreference {
                    this.accessToken = accessToken
                    softwareTokenMfaSettings {
                        enabled = true
                        preferredMfa = true
                    }
                }
            }
            Unit
        }.onFailure { e -> Log.e(TAG, "[Auth] finishTotpEnrollment FAILED → ${e::class.simpleName}: ${e.message}", e) }
    }

    // --- Access / ID tokens for AWS calls ---

    /**
     * Returns a valid (non-expired) access token, refreshing if needed. Uses a Mutex so concurrent
     * callers share one refresh. With [forceRefresh] the cached token is ignored (used after the
     * service rejected it), but callers queued behind an in-flight refresh still reuse its result.
     */
    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedAccessTokenOrNull()?.let { return@withContext it }

        val tokenWhenRequested = prefs.getString(KEY_ACCESS, null)
        refreshMutex.withLock {
            // Another coroutine may have refreshed while we waited for the lock.
            val current = prefs.getString(KEY_ACCESS, null)
            if (current != null) {
                val refreshedByOther = forceRefresh && current != tokenWhenRequested
                if (refreshedByOther || (!forceRefresh && !isTokenExpiringSoon())) {
                    Log.d(TAG, "[Token] refresh done by concurrent call, reusing")
                    return@withLock current
                }
            }
            refreshTokens()
        }
    }

    /** Returns the current ID token, refreshing first if the access token is expiring. */
    suspend fun getValidIdToken(): String = withContext(Dispatchers.IO) {
        getValidAccessToken() // a refresh updates the ID token too
        prefs.getString(KEY_ID, null) ?: run {
            emitSessionExpired()
            error("Not logged in — no ID token")
        }
    }

    // Must be called with refreshMutex held.
    private suspend fun refreshTokens(): String {
        val config = configRepository.getConfig() ?: error("AWS not configured — complete onboarding first")
        val refreshToken = prefs.getString(KEY_REFRESH, null) ?: run {
            Log.e(TAG, "[Token] no refresh token — session expired")
            emitSessionExpired()
            error("Not logged in — no refresh token")
        }

        return try {
            Log.d(TAG, "[Token] refreshing token")
            val response = CognitoIdentityProviderClient { region = config.region }.use { client ->
                client.initiateAuth {
                    authFlow = AuthFlowType.RefreshTokenAuth
                    clientId = config.clientId
                    authParameters = mapOf("REFRESH_TOKEN" to refreshToken)
                }
            }
            val result = response.authenticationResult ?: error("Token refresh failed — empty result")
            val accessToken = result.accessToken ?: error("Missing access token")
            saveTokens(
                accessToken = accessToken,
                idToken = result.idToken ?: error("Missing ID token"),
                // Cognito does not rotate the refresh token on refresh unless rotation is enabled.
                refreshToken = result.refreshToken ?: refreshToken,
                expiresInSeconds = result.expiresIn,
            )
            accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[Token] refresh FAILED — ${e::class.simpleName}: ${e.message}", e)
            // Only a rejected refresh token means the session is over. Network errors and
            // throttling are transient and must not sign the user out.
            if (e.isNotAuthorized()) emitSessionExpired()
            throw e
        }
    }

    // --- Helpers ---

    private suspend fun <T> withIdp(
        block: suspend (config: AwsConfig, client: CognitoIdentityProviderClient) -> T,
    ): T {
        val config = configRepository.getConfig() ?: error("AWS not configured — complete onboarding first")
        return CognitoIdentityProviderClient { region = config.region }.use { client -> block(config, client) }
    }

    private fun handleAuthResponse(
        fallbackUsername: String,
        challengeName: ChallengeNameType?,
        session: String?,
        challengeParameters: Map<String, String>?,
        authResult: AuthenticationResultType?,
    ): AuthResult {
        if (authResult != null) {
            saveTokens(
                accessToken = authResult.accessToken ?: error("Missing access token"),
                idToken = authResult.idToken ?: error("Missing ID token"),
                refreshToken = authResult.refreshToken ?: error("Missing refresh token"),
                expiresInSeconds = authResult.expiresIn,
                username = fallbackUsername,
            )
            Log.d(TAG, "[Auth] sign-in complete")
            return AuthResult.LoggedIn
        }

        Log.d(TAG, "[Auth] challenge=${challengeName?.value}")
        val challenge = AuthChallenge(
            session = session ?: error("Cognito returned no session for the ${challengeName?.value} challenge"),
            // USER_ID_FOR_SRP is the canonical username when the user signed in with an alias.
            username = challengeParameters?.get("USER_ID_FOR_SRP") ?: fallbackUsername,
        )
        return when (challengeName) {
            ChallengeNameType.SoftwareTokenMfa -> AuthResult.MfaRequired(challenge, MfaMethod.TOTP)
            ChallengeNameType.SmsMfa -> AuthResult.MfaRequired(challenge, MfaMethod.SMS)
            ChallengeNameType.NewPasswordRequired -> AuthResult.NewPasswordRequired(challenge)
            ChallengeNameType.MfaSetup -> AuthResult.MfaSetupRequired(challenge)
            else -> error("Unsupported sign-in step: ${challengeName?.value}")
        }
    }

    private fun saveTokens(
        accessToken: String,
        idToken: String,
        refreshToken: String,
        expiresInSeconds: Int,
        username: String? = null,
    ) {
        val lifetime = if (expiresInSeconds > 0) expiresInSeconds else DEFAULT_EXPIRY_SECONDS
        prefs.edit().apply {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_ID, idToken)
            putString(KEY_REFRESH, refreshToken)
            putLong(KEY_EXPIRY, System.currentTimeMillis() + lifetime * 1000L)
            if (username != null) putString(KEY_USERNAME, username)
        }.apply()
        Log.d(TAG, "[Token] saved tokens — expiresIn=${lifetime}s")
    }

    private fun clearTokens() {
        prefs.edit().clear().apply()
    }
}
