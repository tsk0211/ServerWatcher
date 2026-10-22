package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.Subdomain
import kotlinx.coroutines.flow.Flow

interface ISubdomainRepository {
    fun observeByDomain(domainId: String): Flow<List<Subdomain>>
    suspend fun getById(id: String): Subdomain?
    suspend fun insert(subdomain: Subdomain)
    suspend fun update(subdomain: Subdomain)
    suspend fun delete(id: String)
}
