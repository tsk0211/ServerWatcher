package dev.tsk.server.watcher.core.data.local

import dev.tsk.server.watcher.core.data.db.dao.InstanceDao
import dev.tsk.server.watcher.core.data.db.mapper.toEntity
import dev.tsk.server.watcher.core.data.db.mapper.toModel
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InstanceRepositoryImpl @Inject constructor(
    private val dao: InstanceDao,
) : IInstanceRepository {

    override fun observeBySubdomain(subdomainId: String): Flow<List<Instance>> =
        dao.observeBySubdomain(subdomainId).map { list -> list.map { it.toModel() } }

    override suspend fun getById(id: String): Instance? =
        dao.getById(id)?.toModel()

    override suspend fun insert(instance: Instance) =
        dao.insert(instance.toEntity())

    override suspend fun update(instance: Instance) =
        dao.update(instance.toEntity())

    override suspend fun delete(id: String) =
        dao.delete(id)

    override suspend fun getAllByDomain(domainId: String): List<Instance> =
        dao.getAllByDomain(domainId).map { it.toModel() }

    override fun observeAllByDomain(domainId: String): Flow<List<Instance>> =
        dao.observeAllByDomain(domainId).map { list -> list.map { it.toModel() } }
}
