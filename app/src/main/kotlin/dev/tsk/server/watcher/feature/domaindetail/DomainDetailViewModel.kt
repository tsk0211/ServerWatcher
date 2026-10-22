package dev.tsk.server.watcher.feature.domaindetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.model.Ec2InstanceSummary
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.core.domain.model.Subdomain
import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import dev.tsk.server.watcher.core.domain.repository.IRoute53Repository
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import dev.tsk.server.watcher.core.domain.usecase.ManageDomainUseCase
import dev.tsk.server.watcher.core.domain.usecase.ObserveDomainStatusUseCase
import dev.tsk.server.watcher.core.domain.usecase.StartInstanceUseCase
import dev.tsk.server.watcher.core.domain.usecase.StopInstanceUseCase
import dev.tsk.server.watcher.core.domain.usecase.UpdateDnsRecordUseCase
import dev.tsk.server.watcher.ui.components.ToastManager
import dev.tsk.server.watcher.ui.components.ToastType
import dev.tsk.server.watcher.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SW"

data class InstanceRowUiModel(
    val id: String,
    val instanceId: String,
    val label: String,
    val ec2State: Ec2State,
    val healthStatus: HealthStatus,
    val region: String,
    val isPrimary: Boolean = false,
)

data class SubdomainSectionUiModel(
    val subdomain: Subdomain,
    val instances: List<InstanceRowUiModel>,
)

data class DomainDetailData(
    val domainId: String,
    val domainName: String,
    val domainRegion: String,
    val hostedZoneId: String?,
    val hostedZoneName: String?,
    val sections: List<SubdomainSectionUiModel>,
    val isStatusLoaded: Boolean = false,
)

@HiltViewModel
class DomainDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subdomainRepository: ISubdomainRepository,
    private val instanceRepository: IInstanceRepository,
    private val instanceConfigRepository: IInstanceConfigRepository,
    private val ec2Repository: IEc2Repository,
    private val route53Repository: IRoute53Repository,
    private val observeDomainStatus: ObserveDomainStatusUseCase,
    private val startInstance: StartInstanceUseCase,
    private val stopInstance: StopInstanceUseCase,
    private val manageDomain: ManageDomainUseCase,
    private val updateDnsRecord: UpdateDnsRecordUseCase,
    private val toastManager: ToastManager,
) : ViewModel() {

    private val domainId: String = checkNotNull(savedStateHandle["domainId"])

    private val _uiState = MutableStateFlow<UiState<DomainDetailData>>(UiState.Loading())
    val uiState: StateFlow<UiState<DomainDetailData>> = _uiState.asStateFlow()

    private val statusMapFlow = MutableStateFlow<Map<String, Pair<Ec2State, HealthStatus>>>(emptyMap())
    private val domainFlow = MutableStateFlow<Domain?>(null)
    private val _statusLoaded = MutableStateFlow(false)

    private val _hostedZones = MutableStateFlow<List<HostedZoneSummary>>(emptyList())
    val hostedZones: StateFlow<List<HostedZoneSummary>> = _hostedZones.asStateFlow()

    private val _ec2Instances = MutableStateFlow<List<Ec2InstanceSummary>>(emptyList())
    val ec2Instances: StateFlow<List<Ec2InstanceSummary>> = _ec2Instances.asStateFlow()

    private val _instancesLoading = MutableStateFlow(false)
    val instancesLoading: StateFlow<Boolean> = _instancesLoading.asStateFlow()

    private var contentJob: Job? = null
    private var statusJob: Job? = null

    init {
        Log.d(TAG, "[DetailVM] init — domainId=$domainId")
        loadDomain()
        loadHostedZones()
        observeContent()
        observeStatuses()
    }

    fun retry() {
        Log.d(TAG, "[DetailVM] retry()")
        _uiState.value = UiState.Loading()
        statusMapFlow.value = emptyMap()
        _statusLoaded.value = false
        loadDomain()
        observeContent()
        observeStatuses()
    }

    private fun loadDomain() {
        viewModelScope.launch {
            manageDomain.getById(domainId)?.let { domain ->
                Log.d(TAG, "[DetailVM] loaded domain name=${domain.name} hzId=${domain.hostedZoneId}")
                domainFlow.value = domain
            }
        }
    }

    private fun loadHostedZones() {
        viewModelScope.launch {
            route53Repository.listHostedZones()
                .onSuccess { _hostedZones.value = it }
                .onFailure { e -> Log.w(TAG, "[DetailVM] listHostedZones failed (non-fatal): ${e.message}") }
        }
    }

    fun loadEc2Instances(region: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] loadEc2Instances region=$region")
            _instancesLoading.value = true
            _ec2Instances.value = emptyList()
            ec2Repository.listInstances(region)
                .onSuccess {
                    Log.d(TAG, "[DetailVM] loadEc2Instances count=${it.size}")
                    _ec2Instances.value = it
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] loadEc2Instances failed: ${e.message}", e)
                    toastManager.showToast("Failed to load EC2 instances: ${e.message}", ToastType.ERROR)
                }
            _instancesLoading.value = false
        }
    }

    private fun observeContent() {
        contentJob?.cancel()
        contentJob = combine(
            subdomainRepository.observeByDomain(domainId),
            instanceRepository.observeAllByDomain(domainId),
            statusMapFlow,
            domainFlow,
            _statusLoaded,
        ) { subdomains, allInstances, statusMap, domain, statusLoaded ->
            val sections = subdomains.map { subdomain ->
                val subInstances = allInstances.filter { it.subdomainId == subdomain.id }
                SubdomainSectionUiModel(
                    subdomain = subdomain,
                    instances = subInstances.map { inst ->
                        val (ec2, health) = statusMap[inst.id] ?: (Ec2State.UNKNOWN to HealthStatus.Unknown)
                        InstanceRowUiModel(
                            id = inst.id,
                            instanceId = inst.instanceId,
                            label = inst.label,
                            ec2State = ec2,
                            healthStatus = health,
                            region = inst.region,
                            isPrimary = inst.isPrimary,
                        )
                    },
                )
            }
            DomainDetailData(
                domainId = domainId,
                domainName = domain?.name ?: "",
                domainRegion = domain?.region ?: "",
                hostedZoneId = domain?.hostedZoneId,
                hostedZoneName = domain?.hostedZoneName,
                sections = sections,
                isStatusLoaded = statusLoaded,
            )
        }
            .onEach { data ->
                Log.d(TAG, "[DetailVM] content updated — sections=${data.sections.size} domain=${data.domainName}")
                _uiState.value = UiState.Success(data)
            }
            .catch { e ->
                Log.e(TAG, "[DetailVM] content flow error: ${e.message}", e)
                _uiState.value = UiState.Error("Failed to load domain details: ${e.message}", e as? Exception)
                toastManager.showToast("Error loading domain: ${e.message}", ToastType.ERROR)
            }
            .launchIn(viewModelScope)
    }

    private fun observeStatuses() {
        statusJob?.cancel()
        statusJob = observeDomainStatus(domainId)
            .onEach { statuses ->
                Log.d(TAG, "[DetailVM] statuses updated — count=${statuses.size}")
                statusMapFlow.value = statuses.associate { it.instance.id to (it.ec2State to it.healthStatus) }
                _statusLoaded.value = true
            }
            .launchIn(viewModelScope)
    }

    fun toggleInstanceSwitch(instanceId: String, region: String, newState: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] toggleInstance id=$instanceId region=$region newState=$newState")

            // Immediately show transitional state — don't wait for the next poll cycle
            val rowId = (_uiState.value as? UiState.Success)?.data?.sections
                ?.flatMap { it.instances }
                ?.firstOrNull { it.instanceId == instanceId }
                ?.id
            val optimisticState = if (newState) Ec2State.PENDING else Ec2State.STOPPING
            if (rowId != null) {
                statusMapFlow.value = statusMapFlow.value + (rowId to (optimisticState to HealthStatus.Unknown))
            }

            val isPrimary = (_uiState.value as? UiState.Success)?.data?.sections
                ?.flatMap { it.instances }
                ?.firstOrNull { it.instanceId == instanceId }
                ?.isPrimary == true

            val result = if (newState) startInstance(instanceId, region) else stopInstance(instanceId, region)
            result
                .onSuccess {
                    Log.d(TAG, "[DetailVM] toggleInstance SUCCESS isPrimary=$isPrimary")
                    if (newState && isPrimary && rowId != null) {
                        awaitRunningAndUpdateDns(rowId, instanceId, region)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] toggleInstance FAILED: ${e.message}", e)
                    // Roll back the optimistic update so the next poll restores the real state
                    if (rowId != null) {
                        statusMapFlow.value = statusMapFlow.value - rowId
                    }
                    toastManager.showToast("Failed to ${if (newState) "start" else "stop"} instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    private fun awaitRunningAndUpdateDns(instanceRowId: String, ec2InstanceId: String, region: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] awaitRunningAndUpdateDns rowId=$instanceRowId ec2=$ec2InstanceId")
            repeat(30) {
                delay(3_000L)
                val state = runCatching { ec2Repository.getState(ec2InstanceId, region) }.getOrNull()
                    ?: return@repeat
                if (state == Ec2State.RUNNING) {
                    val ip = runCatching { ec2Repository.getPublicIp(ec2InstanceId, region) }.getOrNull()
                        ?: return@repeat
                    Log.d(TAG, "[DetailVM] primary reached RUNNING ip=$ip — updating DNS")
                    updateDnsRecord(instanceRowId, ip)
                        .onSuccess {
                            Log.d(TAG, "[DetailVM] auto DNS update SUCCESS")
                            toastManager.showToast("DNS A record updated", ToastType.SUCCESS)
                        }
                        .onFailure { e ->
                            Log.w(TAG, "[DetailVM] auto DNS update failed: ${e.message}")
                            toastManager.showToast("DNS update failed: ${e.message}", ToastType.ERROR)
                        }
                    return@launch
                }
                if (!state.isTransitional) return@launch
            }
        }
    }

    fun deleteInstance(id: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] deleteInstance id=$id")
            runCatching { instanceRepository.delete(id) }
                .onSuccess { Log.d(TAG, "[DetailVM] deleteInstance SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] deleteInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun updateDomain(name: String, hostedZoneId: String?, hostedZoneName: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] updateDomain name=$name hostedZoneId=$hostedZoneId zoneName=$hostedZoneName")
            runCatching { manageDomain.updateDomain(domainId, name, hostedZoneId, hostedZoneName) }
                .onSuccess {
                    Log.d(TAG, "[DetailVM] updateDomain SUCCESS")
                    domainFlow.value = domainFlow.value?.copy(name = name, hostedZoneId = hostedZoneId, hostedZoneName = hostedZoneName)
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] updateDomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to update domain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun setPrimaryInstance(instanceRowId: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] setPrimaryInstance id=$instanceRowId")
            runCatching {
                val allInstances = instanceRepository.getAllByDomain(domainId)
                allInstances.forEach { inst ->
                    if (inst.isPrimary || inst.id == instanceRowId) {
                        instanceRepository.update(inst.copy(isPrimary = inst.id == instanceRowId))
                    }
                }
            }
                .onSuccess { Log.d(TAG, "[DetailVM] setPrimaryInstance SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] setPrimaryInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to set primary instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun updateSubdomain(
        id: String,
        prefix: String,
        isApex: Boolean,
        healthPath: String,
        rangeStart: Int,
        rangeEnd: Int,
        intervalSeconds: Int,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] updateSubdomain id=$id prefix=$prefix isApex=$isApex")
            runCatching {
                val existing = subdomainRepository.getById(id) ?: error("Subdomain not found: $id")
                subdomainRepository.update(
                    existing.copy(
                        prefix = prefix.trim(),
                        isApex = isApex,
                        healthPath = healthPath.trim(),
                        healthRangeStart = rangeStart,
                        healthRangeEnd = rangeEnd,
                        healthIntervalSeconds = intervalSeconds,
                    )
                )
            }
                .onSuccess {
                    Log.d(TAG, "[DetailVM] updateSubdomain SUCCESS")
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] updateSubdomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to update subdomain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun updateInstance(id: String, label: String, region: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] updateInstance id=$id label=$label region=$region")
            runCatching {
                val existing = instanceRepository.getById(id) ?: error("Instance not found: $id")
                instanceRepository.update(existing.copy(label = label.trim(), region = region.trim()))
            }
                .onSuccess {
                    Log.d(TAG, "[DetailVM] updateInstance SUCCESS")
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] updateInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to update instance: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun addSubdomain(prefix: String, isApex: Boolean, path: String, rangeStart: Int, rangeEnd: Int, intervalSeconds: Int) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] addSubdomain prefix=$prefix isApex=$isApex")
            runCatching {
                subdomainRepository.insert(
                    Subdomain(
                        id = UUID.randomUUID().toString(),
                        domainId = domainId,
                        prefix = prefix.trim(),
                        isApex = isApex,
                        healthPath = path.trim(),
                        healthRangeStart = rangeStart,
                        healthRangeEnd = rangeEnd,
                        healthIntervalSeconds = intervalSeconds,
                    )
                )
            }
                .onSuccess { Log.d(TAG, "[DetailVM] addSubdomain SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] addSubdomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to add subdomain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun deleteSubdomain(id: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] deleteSubdomain id=$id")
            runCatching { subdomainRepository.delete(id) }
                .onSuccess { Log.d(TAG, "[DetailVM] deleteSubdomain SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] deleteSubdomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete subdomain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun deleteDomain(onComplete: () -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] deleteDomain id=$domainId")
            runCatching { manageDomain.delete(domainId) }
                .onSuccess {
                    Log.d(TAG, "[DetailVM] deleteDomain SUCCESS")
                    onComplete()
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] deleteDomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete domain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun addInstance(subdomainId: String, instanceId: String, label: String, region: String) {
        viewModelScope.launch {
            Log.d(TAG, "[DetailVM] addInstance instanceId=$instanceId subdomainId=$subdomainId")
            val rowId = UUID.randomUUID().toString()
            runCatching {
                val existingCount = instanceRepository.getAllByDomain(domainId)
                    .count { it.subdomainId == subdomainId }
                instanceRepository.insert(
                    Instance(
                        id = rowId,
                        instanceId = instanceId.trim(),
                        subdomainId = subdomainId,
                        label = label.trim(),
                        region = region.trim(),
                        isPrimary = existingCount == 0,
                    )
                )
            }
                .onSuccess {
                    Log.d(TAG, "[DetailVM] addInstance SUCCESS — seeding default config")
                    runCatching {
                        val domain = domainFlow.value
                        val swDomain = domain?.hostedZoneId
                            ?.let { hzId -> _hostedZones.value.firstOrNull { it.id == hzId }?.name }
                            ?: domain?.name
                            ?: ""
                        val ec2Id = instanceId.trim()
                        val alreadyHasDomain = instanceConfigRepository.getByInstance(ec2Id)
                            .any { it.key == "SW_DOMAIN" }
                        if (!alreadyHasDomain) {
                            instanceConfigRepository.insert(
                                InstanceConfig(
                                    id = UUID.randomUUID().toString(),
                                    instanceId = ec2Id,
                                    key = "SW_DOMAIN",
                                    value = swDomain,
                                )
                            )
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "[DetailVM] config seed failed (non-fatal): ${e.message}", e)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "[DetailVM] addInstance failed: ${e.message}", e)
                    toastManager.showToast("Failed to add instance: ${e.message}", ToastType.ERROR)
                }
        }
    }
}
