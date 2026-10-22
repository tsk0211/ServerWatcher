package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import kotlinx.coroutines.flow.Flow

interface IInstanceConfigRepository {
    fun observeByInstance(instanceId: String): Flow<List<InstanceConfig>>
    suspend fun getByInstance(instanceId: String): List<InstanceConfig>
    suspend fun insert(config: InstanceConfig)
    suspend fun update(config: InstanceConfig)
    suspend fun delete(id: String)
    suspend fun deleteAllForInstance(instanceId: String)
}
