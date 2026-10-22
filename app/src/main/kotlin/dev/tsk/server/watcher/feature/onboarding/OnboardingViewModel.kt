package dev.tsk.server.watcher.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import dev.tsk.server.watcher.core.domain.model.AwsConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val configRepository: AwsConfigRepository,
) : ViewModel() {

    private val _saved = MutableSharedFlow<Unit>(replay = 0)
    val saved = _saved.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun save(
        userPoolId: String,
        clientId: String,
        identityPoolId: String,
        region: String,
    ) {
        viewModelScope.launch {
            runCatching {
                configRepository.saveConfig(
                    AwsConfig(
                        userPoolId = userPoolId.trim(),
                        clientId = clientId.trim(),
                        identityPoolId = identityPoolId.trim(),
                        region = region.trim(),
                    )
                )
            }
                .onSuccess { _saved.emit(Unit) }
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() { _error.value = null }
}
