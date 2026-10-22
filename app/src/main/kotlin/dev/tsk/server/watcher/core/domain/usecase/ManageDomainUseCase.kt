package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import javax.inject.Inject

class ManageDomainUseCase @Inject constructor(
    private val domainRepository: IDomainRepository,
    private val startDomain: StartDomainUseCase,
    private val stopDomain: StopDomainUseCase,
) {
    suspend fun toggle(domainId: String, turnOn: Boolean): Result<Unit> =
        if (turnOn) startDomain(domainId) else stopDomain(domainId)

    suspend fun delete(domainId: String) = domainRepository.delete(domainId)

    suspend fun getById(domainId: String): Domain? = domainRepository.getById(domainId)

    suspend fun updateName(domainId: String, name: String) {
        val domain = domainRepository.getById(domainId) ?: return
        domainRepository.update(domain.copy(name = name))
    }

    suspend fun updateDomain(domainId: String, name: String, hostedZoneId: String?, hostedZoneName: String? = null) {
        val domain = domainRepository.getById(domainId) ?: return
        domainRepository.update(domain.copy(name = name, hostedZoneId = hostedZoneId, hostedZoneName = hostedZoneName))
    }
}
