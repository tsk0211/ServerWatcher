package dev.tsk.server.watcher.core.domain.usecase

import android.util.Log
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import dev.tsk.server.watcher.core.domain.repository.IRoute53Repository
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import javax.inject.Inject

private const val TAG = "SW"

class UpdateDnsRecordUseCase @Inject constructor(
    private val instanceRepository: IInstanceRepository,
    private val subdomainRepository: ISubdomainRepository,
    private val domainRepository: IDomainRepository,
    private val route53Repository: IRoute53Repository,
) {
    suspend operator fun invoke(instanceRowId: String, publicIp: String): Result<Unit> {
        val instance = instanceRepository.getById(instanceRowId)
            ?: return Result.failure(IllegalStateException("Instance not found: $instanceRowId"))
        val subdomain = subdomainRepository.getById(instance.subdomainId)
            ?: return Result.failure(IllegalStateException("Subdomain not found: ${instance.subdomainId}"))
        val domain = domainRepository.getById(subdomain.domainId)
            ?: return Result.failure(IllegalStateException("Domain not found: ${subdomain.domainId}"))

        val hostedZoneId = domain.hostedZoneId
            ?: return Result.failure(IllegalStateException("Domain has no hosted zone linked"))
        val zoneName = domain.hostedZoneName
            ?: return Result.failure(IllegalStateException("Domain hosted zone name unknown — re-select the zone in Edit Domain"))

        val recordName = if (subdomain.isApex) zoneName else "${subdomain.prefix}.$zoneName"
        Log.d(TAG, "[UpdateDNS] zone=$hostedZoneId record=$recordName ip=$publicIp")
        return route53Repository.upsertARecord(hostedZoneId, recordName, publicIp)
    }
}
