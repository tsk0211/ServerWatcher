package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.common.runCatchingCancellable
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import javax.inject.Inject

class StartDomainUseCase @Inject constructor(
    private val domainRepository: IDomainRepository,
    private val instanceRepository: IInstanceRepository,
    private val startInstance: StartInstanceUseCase,
) {
    suspend operator fun invoke(domainId: String): Result<Unit> = runCatchingCancellable {
        val domain = domainRepository.getById(domainId) ?: error("Domain not found")
        val instances = instanceRepository.getAllByDomain(domainId)

        // Each instance lives in its own region — never assume the domain's region.
        val failures = applyToAll(instances) { startInstance(it.instanceId, it.region) }
        if (failures.isNotEmpty()) throw partialFailure("start", instances.size, failures)

        domainRepository.update(domain.copy(isMasterOn = true))
    }
}
