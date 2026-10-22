package dev.tsk.server.watcher.core.data.local

import dev.tsk.server.watcher.core.data.db.dao.SubdomainDao
import dev.tsk.server.watcher.core.data.db.mapper.toEntity
import dev.tsk.server.watcher.core.data.db.mapper.toModel
import dev.tsk.server.watcher.core.domain.model.Subdomain
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SubdomainRepositoryImpl @Inject constructor(
    private val dao: SubdomainDao,
) : ISubdomainRepository {

    override fun observeByDomain(domainId: String): Flow<List<Subdomain>> =
        dao.observeByDomain(domainId).map { list -> list.map { it.toModel() } }

    override suspend fun getById(id: String): Subdomain? =
        dao.getById(id)?.toModel()

    override suspend fun insert(subdomain: Subdomain) =
        dao.insert(subdomain.toEntity())

    override suspend fun update(subdomain: Subdomain) =
        dao.update(subdomain.toEntity())

    override suspend fun delete(id: String) =
        dao.delete(id)
}
