package dev.tsk.server.watcher

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val configRepository: AwsConfigRepository,
    private val tokenManager: CognitoTokenManager,
) : ViewModel() {
    fun isConfigured(): Boolean = configRepository.hasConfig()
    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    // Emits when the refresh token is expired / invalid — navigate to login.
    val sessionExpired: SharedFlow<Unit> = tokenManager.sessionExpiredFlow
}
