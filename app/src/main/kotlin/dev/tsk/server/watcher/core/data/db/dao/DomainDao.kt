package dev.tsk.server.watcher.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tsk.server.watcher.core.data.db.entity.DomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains ORDER BY name ASC")
    fun observeAll(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE id = :id")
    suspend fun getById(id: String): DomainEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DomainEntity)

    @Update
    suspend fun update(entity: DomainEntity)

    @Query("DELETE FROM domains WHERE id = :id")
    suspend fun delete(id: String)
}
