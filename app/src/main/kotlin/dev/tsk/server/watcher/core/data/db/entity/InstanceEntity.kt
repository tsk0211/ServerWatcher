package dev.tsk.server.watcher.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "instances",
    foreignKeys = [
        ForeignKey(
            entity = SubdomainEntity::class,
            parentColumns = ["id"],
            childColumns = ["subdomainId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("subdomainId")],
)
data class InstanceEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val subdomainId: String,
    val label: String,
    val region: String,
    val isPrimary: Boolean = false,
)
