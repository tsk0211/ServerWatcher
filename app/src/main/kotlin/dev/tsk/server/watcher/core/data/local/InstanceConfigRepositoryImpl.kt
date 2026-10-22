package dev.tsk.server.watcher.core.data.local

import dev.tsk.server.watcher.core.data.db.dao.InstanceConfigDao
import dev.tsk.server.watcher.core.data.db.mapper.toEntity
import dev.tsk.server.watcher.core.data.db.mapper.toModel
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InstanceConfigRepositoryImpl @Inject constructor(
    private val dao: InstanceConfigDao,
) : IInstanceConfigRepository {

    override fun observeByInstance(instanceId: String): Flow<List<InstanceConfig>> =
        dao.observeByInstance(instanceId).map { list -> list.map { it.toModel() } }

    override suspend fun getByInstance(instanceId: String): List<InstanceConfig> =
        dao.getByInstance(instanceId).map { it.toModel() }

    override suspend fun insert(config: InstanceConfig) =
        dao.insert(config.toEntity())

    override suspend fun update(config: InstanceConfig) =
        dao.update(config.toEntity())

    override suspend fun delete(id: String) =
        dao.delete(id)

    override suspend fun deleteAllForInstance(instanceId: String) =
        dao.deleteAllForInstance(instanceId)
}
