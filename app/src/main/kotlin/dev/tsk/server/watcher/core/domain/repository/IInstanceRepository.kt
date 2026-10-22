package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.Instance
import kotlinx.coroutines.flow.Flow

interface IInstanceRepository {
    fun observeBySubdomain(subdomainId: String): Flow<List<Instance>>
    suspend fun getById(id: String): Instance?
    suspend fun insert(instance: Instance)
    suspend fun update(instance: Instance)
    suspend fun delete(id: String)
    suspend fun getAllByDomain(domainId: String): List<Instance>
    fun observeAllByDomain(domainId: String): Flow<List<Instance>>
}
