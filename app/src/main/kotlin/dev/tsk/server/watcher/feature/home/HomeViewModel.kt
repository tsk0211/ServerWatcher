package dev.tsk.server.watcher.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IRoute53Repository
import dev.tsk.server.watcher.core.domain.usecase.ManageDomainUseCase
import dev.tsk.server.watcher.core.domain.usecase.ObserveDomainStatusUseCase
import dev.tsk.server.watcher.ui.components.ToastManager
import dev.tsk.server.watcher.ui.components.ToastType
import dev.tsk.server.watcher.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SW"

enum class DomainStatus { UNKNOWN, ALL_RUNNING, ALL_STOPPED, MIXED, TRANSITIONING }

data class DomainUiModel(
    val domainId: String,
    val domainName: String,
    val region: String,
    val isMasterOn: Boolean,
    val instanceCount: Int = 0,
    val runningCount: Int = 0,
    val status: DomainStatus = DomainStatus.UNKNOWN,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val domainRepository: IDomainRepository,
    private val manageDomain: ManageDomainUseCase,
    private val observeDomainStatus: ObserveDomainStatusUseCase,
    private val route53Repository: IRoute53Repository,
    private val toastManager: ToastManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<DomainUiModel>>>(UiState.Loading())
    val uiState: StateFlow<UiState<List<DomainUiModel>>> = _uiState.asStateFlow()

    private val _hostedZones = MutableStateFlow<List<HostedZoneSummary>>(emptyList())
    val hostedZones: StateFlow<List<HostedZoneSummary>> = _hostedZones.asStateFlow()

    private val pollingJobs = mutableMapOf<String, Job>()
    private var domainObserveJob: Job? = null

    init {
        Log.d(TAG, "[HomeVM] init")
        observeDomains()
        loadHostedZones()
    }

    private fun loadHostedZones() {
        viewModelScope.launch {
            route53Repository.listHostedZones()
                .onSuccess { _hostedZones.value = it }
                .onFailure { e -> Log.w(TAG, "[HomeVM] listHostedZones failed (non-fatal): ${e.message}") }
        }
    }

    private fun observeDomains() {
        domainObserveJob?.cancel()
        domainObserveJob = domainRepository.observeAll()
            .onEach { list ->
                Log.d(TAG, "[HomeVM] domains emission — count=${list.size}")
                startPollingForNewDomains(list)
                val existing = (_uiState.value as? UiState.Success)?.data ?: emptyList()
                _uiState.value = UiState.Success(buildUiModels(list, existing))
            }
            .catch { e ->
                Log.e(TAG, "[HomeVM] domain flow error: ${e.message}", e)
                _uiState.value = UiState.Error("Failed to load domains: ${e.message}", e as? Exception)
                toastManager.showToast("Error loading domains: ${e.message}", ToastType.ERROR)
            }
            .launchIn(viewModelScope)
    }

    fun retry() {
        Log.d(TAG, "[HomeVM] retry()")
        _uiState.value = UiState.Loading()
        observeDomains()
    }

    private fun startPollingForNewDomains(domains: List<Domain>) {
        val newIds = domains.map { it.id }.toSet()

        pollingJobs.keys.filter { it !in newIds }.forEach { id ->
            Log.d(TAG, "[HomeVM] stopping poll for removed domain $id")
            pollingJobs.remove(id)?.cancel()
        }

        domains.filter { it.id !in pollingJobs }.forEach { domain ->
            Log.d(TAG, "[HomeVM] starting poll for domain ${domain.name} (${domain.id})")
            pollingJobs[domain.id] = observeDomainStatus(domain.id)
                .onEach { statuses ->
                    val total = statuses.size
                    val running = statuses.count { it.ec2State == Ec2State.RUNNING }
                    val transitioning = statuses.any {
                        it.ec2State == Ec2State.PENDING ||
                        it.ec2State == Ec2State.STOPPING ||
                        it.ec2State == Ec2State.SHUTTING_DOWN
                    }
                    val newStatus = when {
                        total == 0       -> DomainStatus.UNKNOWN
                        transitioning    -> DomainStatus.TRANSITIONING
                        running == total -> DomainStatus.ALL_RUNNING
                        running == 0     -> DomainStatus.ALL_STOPPED
                        else             -> DomainStatus.MIXED
                    }
                    Log.d(TAG, "[HomeVM] poll domain=${domain.name} total=$total running=$running status=$newStatus")

                    val current = _uiState.value
                    if (current is UiState.Success) {
                        _uiState.value = UiState.Success(current.data.map { uiModel ->
                            if (uiModel.domainId == domain.id) {
                                val masterOn = when (newStatus) {
                                    DomainStatus.ALL_RUNNING, DomainStatus.MIXED -> true
                                    DomainStatus.ALL_STOPPED                     -> false
                                    DomainStatus.TRANSITIONING                   -> uiModel.isMasterOn
                                    DomainStatus.UNKNOWN                         -> false
                                }
                                uiModel.copy(instanceCount = total, runningCount = running, status = newStatus, isMasterOn = masterOn)
                            } else uiModel
                        })
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun buildUiModels(list: List<Domain>, existing: List<DomainUiModel>): List<DomainUiModel> =
        list.map { domain ->
            val current = existing.find { it.domainId == domain.id }
            DomainUiModel(
                domainId = domain.id,
                domainName = domain.name,
                region = domain.region,
                isMasterOn = domain.isMasterOn,
                instanceCount = current?.instanceCount ?: 0,
                runningCount = current?.runningCount ?: 0,
                status = current?.status ?: DomainStatus.UNKNOWN,
            )
        }

    fun toggleMasterSwitch(domainId: String, newState: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "[HomeVM] toggleMaster domainId=$domainId newState=$newState")
            manageDomain.toggle(domainId, newState)
                .onSuccess { Log.d(TAG, "[HomeVM] toggleMaster SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[HomeVM] toggleMaster failed: ${e.message}", e)
                    toastManager.showToast("Failed to toggle domain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun deleteDomain(domainId: String) {
        viewModelScope.launch {
            Log.d(TAG, "[HomeVM] deleteDomain domainId=$domainId")
            pollingJobs.remove(domainId)?.cancel()
            runCatching { manageDomain.delete(domainId) }
                .onSuccess { Log.d(TAG, "[HomeVM] deleteDomain SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[HomeVM] deleteDomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to delete domain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    fun addDomain(name: String, region: String, hostedZoneId: String?, hostedZoneName: String? = null) {
        viewModelScope.launch {
            Log.d(TAG, "[HomeVM] addDomain name=$name region=$region hostedZoneId=$hostedZoneId zoneName=$hostedZoneName")
            runCatching {
                domainRepository.insert(
                    Domain(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        region = region.trim(),
                        hostedZoneId = hostedZoneId?.trim()?.ifBlank { null },
                        hostedZoneName = hostedZoneName?.trim()?.ifBlank { null },
                    )
                )
            }
                .onSuccess { Log.d(TAG, "[HomeVM] addDomain SUCCESS") }
                .onFailure { e ->
                    Log.e(TAG, "[HomeVM] addDomain failed: ${e.message}", e)
                    toastManager.showToast("Failed to add domain: ${e.message}", ToastType.ERROR)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJobs.values.forEach { it.cancel() }
    }
}
