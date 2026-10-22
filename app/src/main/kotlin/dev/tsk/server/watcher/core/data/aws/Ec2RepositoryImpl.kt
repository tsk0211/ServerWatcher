package dev.tsk.server.watcher.core.data.aws

import android.util.Log
import aws.sdk.kotlin.services.ec2.model.BlobAttributeValue
import aws.sdk.kotlin.services.ec2.model.DescribeInstancesRequest
import aws.sdk.kotlin.services.ec2.model.Filter
import aws.sdk.kotlin.services.ec2.model.ModifyInstanceAttributeRequest
import aws.sdk.kotlin.services.ec2.model.StartInstancesRequest
import aws.sdk.kotlin.services.ec2.model.StopInstancesRequest
import dev.tsk.server.watcher.core.domain.model.Ec2InstanceSummary
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SW"

class Ec2RepositoryImpl @Inject constructor(
    private val clientProvider: AwsClientProvider,
    private val authInterceptor: AuthInterceptor,
) : IEc2Repository {

    override suspend fun getState(instanceId: String, region: String): Ec2State {
        Log.d(TAG, "[EC2] getState — instanceId=$instanceId region=$region")
        return authInterceptor.execute {
            // withContext(IO) ensures client.close() (network teardown) never runs on main thread
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    val response = client.describeInstances(
                        DescribeInstancesRequest { instanceIds = listOf(instanceId) }
                    )
                    val stateName = response.reservations
                        ?.firstOrNull()?.instances?.firstOrNull()?.state?.name?.value
                        ?: return@withContext Ec2State.UNKNOWN
                    when (stateName.lowercase()) {
                        "pending"       -> Ec2State.PENDING
                        "running"       -> Ec2State.RUNNING
                        "shutting-down" -> Ec2State.SHUTTING_DOWN
                        "terminated"    -> Ec2State.TERMINATED
                        "stopping"      -> Ec2State.STOPPING
                        "stopped"       -> Ec2State.STOPPED
                        else            -> Ec2State.UNKNOWN
                    }.also { Log.d(TAG, "[EC2] getState result=$it") }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "[EC2] getState failed: ${e.message}", e)
            Ec2State.UNKNOWN
        }
    }

    override suspend fun startInstance(instanceId: String, region: String): Result<Unit> {
        Log.d(TAG, "[EC2] startInstance — instanceId=$instanceId region=$region")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    client.startInstances(StartInstancesRequest { instanceIds = listOf(instanceId) })
                    Log.d(TAG, "[EC2] startInstance SUCCESS")
                }
            }
        }
    }

    override suspend fun stopInstance(instanceId: String, region: String): Result<Unit> {
        Log.d(TAG, "[EC2] stopInstance — instanceId=$instanceId region=$region")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    client.stopInstances(StopInstancesRequest { instanceIds = listOf(instanceId) })
                    Log.d(TAG, "[EC2] stopInstance SUCCESS")
                }
            }
        }
    }

    override suspend fun modifyUserData(instanceId: String, region: String, userData: ByteArray): Result<Unit> {
        Log.d(TAG, "[EC2] modifyUserData — instanceId=$instanceId region=$region")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    client.modifyInstanceAttribute(ModifyInstanceAttributeRequest {
                        this.instanceId = instanceId
                        this.userData = BlobAttributeValue { value = userData }
                    })
                    Log.d(TAG, "[EC2] modifyUserData SUCCESS")
                }
            }
        }
    }

    override suspend fun listInstances(region: String): Result<List<Ec2InstanceSummary>> {
        Log.d(TAG, "[EC2] listInstances — region=$region")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    val response = client.describeInstances(DescribeInstancesRequest {
                        filters = listOf(
                            Filter {
                                name = "instance-state-name"
                                values = listOf("pending", "running", "stopping", "stopped")
                            }
                        )
                    })
                    (response.reservations?.flatMap { it.instances ?: emptyList() } ?: emptyList())
                        .mapNotNull { inst ->
                            val id = inst.instanceId ?: return@mapNotNull null
                            val name = inst.tags?.firstOrNull { it.key == "Name" }?.value?.ifBlank { null }
                            val state = inst.state?.name?.value ?: "unknown"
                            Ec2InstanceSummary(instanceId = id, name = name, stateName = state)
                        }
                        .sortedWith(compareBy({ it.stateName != "running" }, { it.displayName }))
                        .also { Log.d(TAG, "[EC2] listInstances count=${it.size}") }
                }
            }
        }
    }

    override suspend fun getPublicIp(instanceId: String, region: String): String? {
        Log.d(TAG, "[EC2] getPublicIp — instanceId=$instanceId region=$region")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getEc2Client(region).use { client ->
                    client.describeInstances(
                        DescribeInstancesRequest { instanceIds = listOf(instanceId) }
                    ).reservations
                        ?.firstOrNull()?.instances?.firstOrNull()?.publicIpAddress
                        .also { Log.d(TAG, "[EC2] getPublicIp result=$it") }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "[EC2] getPublicIp failed: ${e.message}", e)
            null
        }
    }
}
