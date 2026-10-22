package dev.tsk.server.watcher.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.data.aws.AuthResult
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SW"

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class MfaRequired(val session: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data object Success : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val tokenManager: CognitoTokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            Log.d(TAG, "[AuthVM] login() called for $email")
            _uiState.value = AuthUiState.Loading
            tokenManager.login(email.trim(), password)
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.MfaRequired -> {
                            Log.d(TAG, "[AuthVM] state → MfaRequired")
                            _uiState.value = AuthUiState.MfaRequired(result.session)
                        }
                        is AuthResult.LoggedIn -> {
                            Log.d(TAG, "[AuthVM] state → Success, navigating to Home")
                            _uiState.value = AuthUiState.Success
                        }
                    }
                }
                .onFailure { e ->
                    val msg = "${e::class.simpleName}: ${e.message}"
                    Log.e(TAG, "[AuthVM] state → Error: $msg", e)
                    _uiState.value = AuthUiState.Error(msg)
                }
        }
    }

    fun submitMfa(session: String, code: String) {
        viewModelScope.launch {
            Log.d(TAG, "[AuthVM] submitMfa() called")
            _uiState.value = AuthUiState.Loading
            tokenManager.submitMfa(session, code)
                .onSuccess {
                    Log.d(TAG, "[AuthVM] MFA state → Success")
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { e ->
                    val msg = "${e::class.simpleName}: ${e.message}"
                    Log.e(TAG, "[AuthVM] MFA state → Error: $msg", e)
                    _uiState.value = AuthUiState.Error(msg)
                }
        }
    }

    fun resetError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }
}
