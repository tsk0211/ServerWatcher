package dev.tsk.server.watcher.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tsk.server.watcher.core.data.db.entity.InstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {
    @Query("SELECT * FROM instances WHERE subdomainId = :subdomainId ORDER BY label ASC")
    fun observeBySubdomain(subdomainId: String): Flow<List<InstanceEntity>>

    @Query("SELECT * FROM instances WHERE id = :id")
    suspend fun getById(id: String): InstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstanceEntity)

    @Update
    suspend fun update(entity: InstanceEntity)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        SELECT i.* FROM instances i
        INNER JOIN subdomains s ON i.subdomainId = s.id
        WHERE s.domainId = :domainId
    """)
    suspend fun getAllByDomain(domainId: String): List<InstanceEntity>

    @Query("""
        SELECT i.* FROM instances i
        INNER JOIN subdomains s ON i.subdomainId = s.id
        WHERE s.domainId = :domainId
        ORDER BY i.label ASC
    """)
    fun observeAllByDomain(domainId: String): Flow<List<InstanceEntity>>
}
