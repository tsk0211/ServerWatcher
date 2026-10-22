package dev.tsk.server.watcher.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instance_configs")
data class InstanceConfigEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val key: String,
    val value: String,
)
