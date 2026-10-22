package dev.tsk.server.watcher.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tsk.server.watcher.core.data.db.entity.InstanceConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceConfigDao {
    @Query("SELECT * FROM instance_configs WHERE instanceId = :instanceId ORDER BY key ASC")
    fun observeByInstance(instanceId: String): Flow<List<InstanceConfigEntity>>

    @Query("SELECT * FROM instance_configs WHERE instanceId = :instanceId ORDER BY key ASC")
    suspend fun getByInstance(instanceId: String): List<InstanceConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstanceConfigEntity)

    @Update
    suspend fun update(entity: InstanceConfigEntity)

    @Query("DELETE FROM instance_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM instance_configs WHERE instanceId = :instanceId")
    suspend fun deleteAllForInstance(instanceId: String)
}
