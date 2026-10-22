package dev.tsk.server.watcher.core.data.local

import dev.tsk.server.watcher.core.data.db.dao.DomainDao
import dev.tsk.server.watcher.core.data.db.mapper.toEntity
import dev.tsk.server.watcher.core.data.db.mapper.toModel
import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DomainRepositoryImpl @Inject constructor(
    private val dao: DomainDao,
) : IDomainRepository {

    override fun observeAll(): Flow<List<Domain>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun getById(id: String): Domain? =
        dao.getById(id)?.toModel()

    override suspend fun insert(domain: Domain) =
        dao.insert(domain.toEntity())

    override suspend fun update(domain: Domain) =
        dao.update(domain.toEntity())

    override suspend fun delete(id: String) =
        dao.delete(id)
}
