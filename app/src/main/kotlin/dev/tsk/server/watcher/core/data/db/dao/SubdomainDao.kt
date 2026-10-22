package dev.tsk.server.watcher.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tsk.server.watcher.core.data.db.entity.SubdomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubdomainDao {
    @Query("SELECT * FROM subdomains WHERE domainId = :domainId ORDER BY prefix ASC")
    fun observeByDomain(domainId: String): Flow<List<SubdomainEntity>>

    @Query("SELECT * FROM subdomains WHERE id = :id")
    suspend fun getById(id: String): SubdomainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubdomainEntity)

    @Update
    suspend fun update(entity: SubdomainEntity)

    @Query("DELETE FROM subdomains WHERE id = :id")
    suspend fun delete(id: String)
}
