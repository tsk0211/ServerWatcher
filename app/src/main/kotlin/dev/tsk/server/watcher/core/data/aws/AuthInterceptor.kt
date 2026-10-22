package dev.tsk.server.watcher.core.data.aws

import android.util.Log
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SW"

/**
 * Wraps every AWS API call with proactive token refresh + one-shot retry on auth error.
 * If the retry also fails with an auth error the session-expired signal is emitted so the
 * NavGraph can redirect the user to the login screen.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: CognitoTokenManager,
) {

    suspend fun <T> execute(block: suspend () -> T): Result<T> {
        tokenManager.autoRefreshIfNeeded()

        val firstAttempt = try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e  // never swallow coroutine cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (firstAttempt.isSuccess) return firstAttempt
        val error = firstAttempt.exceptionOrNull()!!
        if (!error.isAuthError()) return firstAttempt

        Log.w(TAG, "[Interceptor] auth error on first attempt (${error::class.simpleName}), forcing refresh + retry")
        return try {
            // The cached token was rejected even though it looked valid, so skip the cache.
            tokenManager.getValidAccessToken(forceRefresh = true)
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isAuthError()) {
                Log.e(TAG, "[Interceptor] still failing after refresh — emitting session expired", e)
                tokenManager.emitSessionExpired()
            }
            Result.failure(e)
        }
    }
}
