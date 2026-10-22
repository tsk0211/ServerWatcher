package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.common.runCatchingCancellable
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import javax.inject.Inject

class StopDomainUseCase @Inject constructor(
    private val domainRepository: IDomainRepository,
    private val instanceRepository: IInstanceRepository,
    private val stopInstance: StopInstanceUseCase,
) {
    suspend operator fun invoke(domainId: String): Result<Unit> = runCatchingCancellable {
        val domain = domainRepository.getById(domainId) ?: error("Domain not found")
        val instances = instanceRepository.getAllByDomain(domainId)

        val failures = applyToAll(instances) { stopInstance(it.instanceId, it.region) }
        if (failures.isNotEmpty()) throw partialFailure("stop", instances.size, failures)

        domainRepository.update(domain.copy(isMasterOn = false))
    }
}
