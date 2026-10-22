package dev.tsk.server.watcher.feature.instancedetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import dev.tsk.server.watcher.core.domain.usecase.PushInstanceConfigUseCase
import dev.tsk.server.watcher.core.domain.usecase.StartInstanceUseCase
import dev.tsk.server.watcher.core.domain.usecase.StopInstanceUseCase
import dev.tsk.server.watcher.core.domain.usecase.UpdateDnsRecordUseCase
import dev.tsk.server.watcher.ui.components.ToastManager
import dev.tsk.server.watcher.ui.components.ToastType
import dev.tsk.server.watcher.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SW"

data class InstanceData(
    val id: String,
    val instanceId: String,
    val label: String,
    val region: String,
    val ec2State: Ec2State,
    val healthStatus: HealthStatus,
    val publicIp: String?,
    val isPrimary: Boolean = false,
    val domainHostedZoneId: String? = null,
    val isStatusLoading: Boolean = true,
    val isToggling: Boolean = false,
    val isPushingConfig: Boolean = false,
    val isDnsUpdating: Boolean = false,
)

private data class StatusResult(
    val ec2State: Ec2State,
    val healthStatus: HealthStatus,
    val publicIp: String?,
)

@HiltViewModel
class InstanceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val instanceRepository: IInstanceRepository,
    private val subdomainRepository: ISubdomainRepository,
    private val ec2Repository: IEc2Repository,
    private val healthRepository: IHealthRepository,
    private val domainRepository: IDomainRepository,
    private val instanceConfigRepository: IInstanceConfigRepository,
    private val startInstance: StartInstanceUseCase,
    private val stopInstance: StopInstanceUseCase,
    private val pushInstanceConfig: PushInstanceConfigUseCase,
    private val updateDnsRecordUseCase: UpdateDnsRecordUseCase,
    private val toastManager: ToastManager,
) : ViewModel() {

    private val instanceRowId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<InstanceData>>(UiState.Loading())
    val uiState: StateFlow<UiState<InstanceData>> = _uiState.asStateFlow()

    // Configs are keyed by EC2 instance ID so the same machine shares one set
    // across all subdomains. Starts with a placeholder until loadInstance resolves the EC2 ID.
    private val _ec2InstanceId = MutableStateFlow<String?>(null)

    val configs: StateFlow<List<InstanceConfig>> = _ec2InstanceId
        .flatMapLatest { ec2Id ->
            if (ec2Id != null) instanceConfigRepository.observeByInstance(ec2Id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var statusPollingJob: Job? = null

    init {
        Log.d(TAG, "[InstVM] init — instanceRowId=$instanceRowId")
        loadInstance()
    }

    fun retry() {
        Log.d(TAG, "[InstVM] retry()")
        statusPollingJob?.cancel()
        _uiState.value = UiState.Loading()
        loadInstance()
    }

    private suspend fun fetchCurrentStatus(instance: Instance): StatusResult {
        val ec2State = ec2Repository.getState(instance.instanceId, instance.region)
        val publicIp = ec2Repository.getPublicIp(instance.instanceId, instance.region)
        val healthStatus = if (ec2State == Ec2State.RUNNING && publicIp != null) {
            val subdomain = subdomainRepository.getById(instance.subdomainId)
            if (subdomain != null && subdomain.healthPath.isNotBlank()) {
                healthRepository.check(
                    url = "http://$publicIp",
                    path = subdomain.healthPath,
                    expectedRangeStart = subdomain.healthRangeStart,
                    expectedRangeEnd = subdomain.healthRangeEnd,
                )
            } else HealthStatus.Unknown
        } else HealthStatus.Unknown
        return StatusResult(ec2State, healthStatus, publicIp)
    }

    private fun loadInstance() {
        viewModelScope.launch {
            Log.d(TAG, "[InstVM] loadInstance()")

            // Step 1: DB read — fast, show screen immediately
            val instance = runCatching { instanceRepository.getById(instanceRowId) }
                .getOrNull()
            if (instance == null) {
                _uiState.value = UiState.Error("Instance not found", null)
                return@launch
            }
            Log.d(TAG, "[InstVM] loaded instance=${instance.instanceId} region=${instance.region}")

            val subdomain = subdomainRepository.getById(instance.subdomainId)
            val domain = subdomain?.let { domainRepository.getById(it.domainId) }

            _ec2InstanceId.value = instance.instanceId

            _uiState.value = UiState.Success(
                InstanceData(
                    id = instance.id,
                    instanceId = instance.instanceId,
                    label = instance.label,
                    region = instance.region,
                    ec2State = Ec2State.UNKNOWN,
                    healthStatus = HealthStatus.Unknown,
                    publicIp = null,
                    isPrimary = instance.isPrimary,
                    domainHostedZoneId = domain?.hostedZoneId,
                    isStatusLoading = true,
                )
            )

            // Step 2: EC2 status — slow, update in place when done
            val status = runCatching { fetchCurrentStatus(instance) }
                .getOrElse { e ->
                    Log.e(TAG, "[InstVM] status fetch failed: ${e.message}", e)
                    StatusResult(Ec2State.UNKNOWN, HealthStatus.Error(e.message ?: "Status fetch failed"), null)
                }
            Log.d(TAG, "[InstVM] status loaded — ec2=${status.ec2State} health=${status.healthStatus}")

            val current = _uiState.value as? UiState.Success ?: return@launch
            _uiState.value = UiState.Success(
                current.data.copy(
                    ec2State = status.ec2State,
                    healthStatus = status.healthStatus,
                    publicIp = status.publicIp,
                    isStatusLoading = false,
                )
            )

            if (status.ec2State.isTransitional) {
                pollUntilStable()
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val currentSuccess = _uiState.value as? UiState.Success ?: return@launch
            val data = currentSuccess.data
            if (data.instanceId.isEmpty()) return@launch
            Log.d(TAG, "[InstVM] refreshStatus instanceId=${data.instanceId}")
            val instance = instanceRepository.getById(instanceRowId) ?: return@launch
            runCatching { fetchCurrentStatus(instance) }
                .onSuccess { status ->
                    Log.d(TAG, "[InstVM] refreshStatus — ec2=${status.ec2State} health=${status.healthStatus}")
                    val afterRefresh = _uiState.value as? UiState.Success ?: return@onSuccess
                    _uiState.value = UiState.Success(
                        afterRefresh.data.copy(
                            ec2State = status.ec2State,
                            healthStatus = status.healthStatus,
                            publicIp = status.publicIp,
                        )
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] refreshStatus failed: ${e.message}", e)
                    val afterFail = _uiState.value as? UiState.Success
                    if (afterFail != null) {
                        _uiState.value = UiState.Success(
                            afterFail.data.copy(healthStatus = HealthStatus.Error(e.message ?: "Refresh failed"))
                        )
                    }
                    toastManager.showToast("Failed to refresh status: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun updateInstance(label: String, region: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            Log.d(TAG, "[InstVM] updateInstance label=$label region=$region")
            runCatching {
                val existing = instanceRepository.getById(instanceRowId) ?: error("Instance not found")
                instanceRepository.update(existing.copy(label = label.trim(), region = region.trim()))
            }
                .onSuccess {
                    Log.d(TAG, "[InstVM] updateInstance SUCCESS")
                    val current = _uiState.value as? UiState.Success ?: return@onSuccess
                    _uiState.value = UiState.Success(current.data.copy(label = label.trim(), region = region.trim()))
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] updateInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to update instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun deleteInstance(onComplete: () -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "[InstVM] deleteInstance id=$instanceRowId")
            runCatching { instanceRepository.delete(instanceRowId) }
                .onSuccess {
                    Log.d(TAG, "[InstVM] deleteInstance SUCCESS")
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] deleteInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun addConfig(key: String, value: String) {
        viewModelScope.launch {
            val ec2Id = _ec2InstanceId.value ?: return@launch
            Log.d(TAG, "[InstVM] addConfig key=$key ec2Id=$ec2Id")
            runCatching {
                instanceConfigRepository.insert(
                    InstanceConfig(
                        id = UUID.randomUUID().toString(),
                        instanceId = ec2Id,
                        key = key.trim(),
                        value = value.trim(),
                    )
                )
            }.onFailure { e ->
                Log.e(TAG, "[InstVM] addConfig failed: ${e.message}", e)
                toastManager.showToast("Failed to add config: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun updateConfig(id: String, key: String, value: String) {
        viewModelScope.launch {
            val ec2Id = _ec2InstanceId.value ?: return@launch
            Log.d(TAG, "[InstVM] updateConfig id=$id key=$key ec2Id=$ec2Id")
            runCatching {
                instanceConfigRepository.update(
                    InstanceConfig(id = id, instanceId = ec2Id, key = key.trim(), value = value.trim())
                )
            }.onFailure { e ->
                Log.e(TAG, "[InstVM] updateConfig failed: ${e.message}", e)
                toastManager.showToast("Failed to update config: ${e.message}", ToastType.ERROR)
            }
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            Log.d(TAG, "[InstVM] deleteConfig id=$id")
            runCatching { instanceConfigRepository.delete(id) }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] deleteConfig failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete config: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun importConfigs(entries: Map<String, String>) {
        viewModelScope.launch {
            val ec2Id = _ec2InstanceId.value ?: return@launch
            Log.d(TAG, "[InstVM] importConfigs ec2Id=$ec2Id count=${entries.size}")
            runCatching {
                instanceConfigRepository.deleteAllForInstance(ec2Id)
                entries.forEach { (key, value) ->
                    instanceConfigRepository.insert(
                        InstanceConfig(
                            id = UUID.randomUUID().toString(),
                            instanceId = ec2Id,
                            key = key.trim(),
                            value = value.trim(),
                        )
                    )
                }
            }
                .onSuccess {
                    Log.d(TAG, "[InstVM] importConfigs SUCCESS")
                    toastManager.showToast("Imported ${entries.size} variable(s)", ToastType.SUCCESS)
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] importConfigs failed: ${e.message}", e)
                    toastManager.showToast("Import failed: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun showImportError(message: String?) {
        toastManager.showToast("Import failed: ${message ?: "invalid file"}", ToastType.ERROR)
    }

    fun updateDnsRecord() {
        viewModelScope.launch {
            val successState = _uiState.value as? UiState.Success ?: return@launch
            val data = successState.data
            val ip = data.publicIp ?: run {
                toastManager.showToast("Instance has no public IP yet", ToastType.ERROR)
                return@launch
            }
            Log.d(TAG, "[InstVM] updateDnsRecord instanceId=${data.instanceId} ip=$ip")
            _uiState.value = UiState.Success(data.copy(isDnsUpdating = true))
            updateDnsRecordUseCase(instanceRowId, ip)
                .onSuccess {
                    Log.d(TAG, "[InstVM] updateDnsRecord SUCCESS")
                    val after = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(after.copy(isDnsUpdating = false))
                    toastManager.showToast("DNS A record updated successfully", ToastType.SUCCESS)
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] updateDnsRecord FAILED: ${e.message}", e)
                    val after = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(after.copy(isDnsUpdating = false))
                    toastManager.showToast("DNS update failed: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun pushConfig() {
        viewModelScope.launch {
            val successState = _uiState.value as? UiState.Success ?: return@launch
            val data = successState.data
            Log.d(TAG, "[InstVM] pushConfig instanceId=${data.instanceId} region=${data.region}")
            _uiState.value = UiState.Success(data.copy(isPushingConfig = true))
            pushInstanceConfig(data.instanceId, data.region)
                .onSuccess {
                    Log.d(TAG, "[InstVM] pushConfig SUCCESS")
                    val after = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(after.copy(isPushingConfig = false))
                    toastManager.showToast("Config pushed to EC2 successfully", ToastType.SUCCESS)
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] pushConfig FAILED: ${e.message}", e)
                    val after = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(after.copy(isPushingConfig = false))
                    toastManager.showToast("Push config failed: ${e.message}", ToastType.ERROR)
                }
        }
    }

    private fun pollUntilStable() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            Log.d(TAG, "[InstVM] pollUntilStable — starting 2s loop")
            while (true) {
                delay(2_000L)
                val instance = instanceRepository.getById(instanceRowId) ?: break
                val status = try {
                    fetchCurrentStatus(instance)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[InstVM] pollUntilStable — fetch failed (will retry): ${e.message}")
                    continue
                }
                val current = _uiState.value as? UiState.Success ?: break
                _uiState.value = UiState.Success(
                    current.data.copy(
                        ec2State = status.ec2State,
                        healthStatus = status.healthStatus,
                        publicIp = status.publicIp,
                    )
                )
                Log.d(TAG, "[InstVM] pollUntilStable — ec2=${status.ec2State}")
                if (!status.ec2State.isTransitional) {
                    Log.d(TAG, "[InstVM] pollUntilStable — state stable, stopping poll")
                    if (status.ec2State == Ec2State.RUNNING && instance.isPrimary && status.publicIp != null) {
                        Log.d(TAG, "[InstVM] pollUntilStable — primary reached RUNNING, auto-updating DNS")
                        updateDnsRecordUseCase(instanceRowId, status.publicIp)
                            .onSuccess { Log.d(TAG, "[InstVM] auto DNS update SUCCESS") }
                            .onFailure { e -> Log.w(TAG, "[InstVM] auto DNS update failed (non-fatal): ${e.message}") }
                    }
                    break
                }
            }
        }
    }

    fun toggleInstance() {
        viewModelScope.launch {
            val successState = _uiState.value as? UiState.Success ?: return@launch
            val data = successState.data
            Log.d(TAG, "[InstVM] toggleInstance — current state=${data.ec2State}")

            _uiState.value = UiState.Success(data.copy(isToggling = true))
            val result = if (data.ec2State.isOn) {
                stopInstance(data.instanceId, data.region)
            } else {
                startInstance(data.instanceId, data.region)
            }
            result
                .onSuccess {
                    Log.d(TAG, "[InstVM] toggleInstance SUCCESS")
                    val afterToggle = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(afterToggle.copy(isToggling = false))
                    pollUntilStable()
                }
                .onFailure { e ->
                    Log.e(TAG, "[InstVM] toggleInstance FAILED: ${e.message}", e)
                    val afterToggle = (_uiState.value as? UiState.Success)?.data ?: data
                    _uiState.value = UiState.Success(afterToggle.copy(isToggling = false))
                    toastManager.showToast(
                        "Failed to ${if (data.ec2State.isOn) "stop" else "start"} instance: ${e.message}",
                        ToastType.ERROR,
                    )
                }
        }
    }
}
