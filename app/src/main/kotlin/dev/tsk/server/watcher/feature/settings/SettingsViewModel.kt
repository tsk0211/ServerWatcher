package dev.tsk.server.watcher.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import dev.tsk.server.watcher.core.domain.model.AwsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsState(
    val userPoolId: String = "",
    val clientId: String = "",
    val identityPoolId: String = "",
    val region: String = "",
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: AwsConfigRepository,
    private val tokenManager: CognitoTokenManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        val config = configRepository.getConfig()
        if (config != null) {
            _state.value = SettingsState(
                userPoolId = config.userPoolId,
                clientId = config.clientId,
                identityPoolId = config.identityPoolId,
                region = config.region,
            )
        }
    }

    fun startEditing() {
        _state.value = _state.value.copy(isEditing = true, saved = false)
    }

    fun update(userPoolId: String? = null, clientId: String? = null, identityPoolId: String? = null, region: String? = null) {
        _state.value = _state.value.copy(
            userPoolId = userPoolId ?: _state.value.userPoolId,
            clientId = clientId ?: _state.value.clientId,
            identityPoolId = identityPoolId ?: _state.value.identityPoolId,
            region = region ?: _state.value.region,
        )
    }

    fun saveConfig() {
        val s = _state.value
        configRepository.saveConfig(AwsConfig(
            userPoolId = s.userPoolId.trim(),
            clientId = s.clientId.trim(),
            identityPoolId = s.identityPoolId.trim(),
            region = s.region.trim(),
        ))
        _state.value = s.copy(isEditing = false, saved = true)
    }

    fun cancelEditing() {
        val config = configRepository.getConfig() ?: return
        _state.value = _state.value.copy(
            userPoolId = config.userPoolId,
            clientId = config.clientId,
            identityPoolId = config.identityPoolId,
            region = config.region,
            isEditing = false,
        )
    }

    fun logout() {
        tokenManager.logout()
    }
}
