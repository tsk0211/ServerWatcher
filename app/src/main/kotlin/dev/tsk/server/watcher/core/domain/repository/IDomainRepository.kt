package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.Domain
import kotlinx.coroutines.flow.Flow

interface IDomainRepository {
    fun observeAll(): Flow<List<Domain>>
    suspend fun getById(id: String): Domain?
    suspend fun insert(domain: Domain)
    suspend fun update(domain: Domain)
    suspend fun delete(id: String)
}
