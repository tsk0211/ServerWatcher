package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.Ec2InstanceSummary
import dev.tsk.server.watcher.core.domain.model.Ec2State

interface IEc2Repository {
    suspend fun getState(instanceId: String, region: String): Ec2State
    suspend fun startInstance(instanceId: String, region: String): Result<Unit>
    suspend fun stopInstance(instanceId: String, region: String): Result<Unit>
    suspend fun getPublicIp(instanceId: String, region: String): String?
    suspend fun modifyUserData(instanceId: String, region: String, userData: ByteArray): Result<Unit>
    suspend fun listInstances(region: String): Result<List<Ec2InstanceSummary>>
}
