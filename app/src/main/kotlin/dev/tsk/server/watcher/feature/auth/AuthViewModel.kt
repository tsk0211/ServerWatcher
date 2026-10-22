package dev.tsk.server.watcher.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.data.aws.AuthChallenge
import dev.tsk.server.watcher.core.data.aws.AuthResult
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import dev.tsk.server.watcher.core.data.aws.MfaMethod
import dev.tsk.server.watcher.core.data.aws.TotpSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SW"

/** Which screen of the sign-in flow the user is on. */
sealed interface AuthStep {
    data object Credentials : AuthStep
    data class Mfa(val challenge: AuthChallenge, val method: MfaMethod) : AuthStep
    data class NewPassword(val challenge: AuthChallenge) : AuthStep
    /** [setup] is null while the authenticator secret is being generated. */
    data class MfaSetup(val challenge: AuthChallenge, val setup: TotpSetup?) : AuthStep
    data object Done : AuthStep
}

/**
 * Step, loading flag and error are separate on purpose: a wrong code is an error *on* the current
 * step, not a new step, so the user stays on the MFA form instead of being sent back to the start.
 */
data class AuthUiState(
    val step: AuthStep = AuthStep.Credentials,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val tokenManager: CognitoTokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        run(duringChallenge = false) { tokenManager.login(email.trim(), password) }
    }

    fun submitMfa(code: String) {
        val step = _uiState.value.step as? AuthStep.Mfa ?: return
        run(duringChallenge = true) { tokenManager.submitMfa(step.challenge, step.method, code.trim()) }
    }

    fun setNewPassword(newPassword: String) {
        val step = _uiState.value.step as? AuthStep.NewPassword ?: return
        run(duringChallenge = true) { tokenManager.completeNewPassword(step.challenge, newPassword) }
    }

    fun submitTotpSetup(code: String) {
        val step = _uiState.value.step as? AuthStep.MfaSetup ?: return
        val session = step.setup?.session ?: return
        run(duringChallenge = true) { tokenManager.finishTotpSetup(step.challenge, session, code.trim()) }
    }

    fun retryTotpSetup() {
        val step = _uiState.value.step as? AuthStep.MfaSetup ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadTotpSetup(step.challenge)
    }

    fun backToLogin() {
        _uiState.value = AuthUiState()
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun run(duringChallenge: Boolean, call: suspend () -> Result<AuthResult>) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            call()
                .onSuccess { applyResult(it) }
                .onFailure { e -> fail(e, duringChallenge) }
        }
    }

    private fun applyResult(result: AuthResult) {
        when (result) {
            AuthResult.LoggedIn -> _uiState.value = AuthUiState(step = AuthStep.Done)
            is AuthResult.MfaRequired ->
                _uiState.value = AuthUiState(step = AuthStep.Mfa(result.challenge, result.method))
            is AuthResult.NewPasswordRequired ->
                _uiState.value = AuthUiState(step = AuthStep.NewPassword(result.challenge))
            is AuthResult.MfaSetupRequired -> {
                _uiState.value = AuthUiState(step = AuthStep.MfaSetup(result.challenge, setup = null), isLoading = true)
                loadTotpSetup(result.challenge)
            }
        }
    }

    private fun loadTotpSetup(challenge: AuthChallenge) {
        viewModelScope.launch {
            tokenManager.startTotpSetup(challenge)
                .onSuccess { setup ->
                    _uiState.value = AuthUiState(step = AuthStep.MfaSetup(challenge, setup))
                }
                .onFailure { e -> fail(e, duringChallenge = true) }
        }
    }

    private fun fail(e: Throwable, duringChallenge: Boolean) {
        val message = e.toAuthMessage(duringChallenge)
        Log.w(TAG, "[AuthVM] ${e::class.simpleName}")
        if (duringChallenge && e.isChallengeSessionLost()) {
            // The challenge session is gone (they last ~3 minutes); only a fresh sign-in helps.
            _uiState.value = AuthUiState(error = message)
        } else {
            _uiState.update { it.copy(isLoading = false, error = message) }
        }
    }
}
