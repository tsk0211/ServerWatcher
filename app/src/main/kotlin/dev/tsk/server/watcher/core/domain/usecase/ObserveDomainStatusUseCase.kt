package dev.tsk.server.watcher.core.domain.usecase

import android.util.Log
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.model.hostName
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val TAG = "SW"
private const val DEFAULT_INTERVAL_SECONDS = 30
private const val MIN_INTERVAL_SECONDS = 5
private const val TRANSITIONAL_DELAY_MS = 2_000L

data class InstanceStatus(
    val instance: Instance,
    val ec2State: Ec2State,
    val healthStatus: HealthStatus,
    val publicIp: String?,
)

class ObserveDomainStatusUseCase @Inject constructor(
    private val instanceRepository: IInstanceRepository,
    private val subdomainRepository: ISubdomainRepository,
    private val domainRepository: IDomainRepository,
    private val ec2Repository: IEc2Repository,
    private val healthRepository: IHealthRepository,
) {
    operator fun invoke(domainId: String): Flow<List<InstanceStatus>> = flow {
        var pollCount = 0
        while (true) {
            pollCount++
            val instances = instanceRepository.getAllByDomain(domainId)
            val domain = domainRepository.getById(domainId)
            Log.d(TAG, "[Poll#$pollCount] domainId=$domainId instances=${instances.size}")

            val polled = coroutineScope {
                instances.map { instance ->
                    async {
                        val ec2State = try {
                            ec2Repository.getState(instance.instanceId, instance.region)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "[Poll] EC2 getState failed for ${instance.instanceId}: ${e.message}")
                            Ec2State.UNKNOWN
                        }

                        val publicIp = try {
                            ec2Repository.getPublicIp(instance.instanceId, instance.region)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "[Poll] EC2 getPublicIp failed for ${instance.instanceId}: ${e.message}")
                            null
                        }

                        Log.d(TAG, "[Poll] ${instance.instanceId} → state=$ec2State ip=$publicIp")

                        val subdomain = subdomainRepository.getById(instance.subdomainId)
                        val healthStatus = if (
                            ec2State == Ec2State.RUNNING &&
                            subdomain != null &&
                            domain != null &&
                            subdomain.healthPath.isNotBlank() &&
                            publicIp != null
                        ) {
                            try {
                                healthRepository.check(
                                    url = "https://${subdomain.hostName(domain)}",
                                    path = subdomain.healthPath,
                                    expectedRangeStart = subdomain.healthRangeStart,
                                    expectedRangeEnd = subdomain.healthRangeEnd,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "[Poll] health check failed for $publicIp: ${e.message}")
                                HealthStatus.Unknown
                            }
                        } else {
                            HealthStatus.Unknown
                        }

                        Log.d(TAG, "[Poll] ${instance.instanceId} → health=$healthStatus")

                        InstanceStatus(
                            instance = instance,
                            ec2State = ec2State,
                            healthStatus = healthStatus,
                            publicIp = publicIp,
                        ) to subdomain?.healthIntervalSeconds
                    }
                }.awaitAll()
            }
            val statuses = polled.map { it.first }
            // Poll as often as the most demanding subdomain asks for.
            val intervalSeconds = polled.mapNotNull { it.second }.minOrNull() ?: DEFAULT_INTERVAL_SECONDS
            emit(statuses)
            val anyTransitional = statuses.any { it.ec2State.isTransitional }
            val nextDelay = if (anyTransitional) TRANSITIONAL_DELAY_MS else intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS) * 1_000L
            Log.d(TAG, "[Poll#$pollCount] done, next poll in ${nextDelay / 1000}s (transitional=$anyTransitional)")
            delay(nextDelay)
        }
    }
}
