package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import javax.inject.Inject

class StopDomainUseCase @Inject constructor(
    private val domainRepository: IDomainRepository,
    private val instanceRepository: IInstanceRepository,
    private val stopInstance: StopInstanceUseCase,
) {
    suspend operator fun invoke(domainId: String): Result<Unit> = runCatching {
        val instances = instanceRepository.getAllByDomain(domainId)
        val domain = domainRepository.getById(domainId) ?: error("Domain not found")
        instances.forEach { instance ->
            stopInstance(instance.instanceId, domain.region).getOrThrow()
        }
        domainRepository.update(domain.copy(isMasterOn = false))
    }
}
