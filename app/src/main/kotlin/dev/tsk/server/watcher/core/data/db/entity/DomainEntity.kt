package dev.tsk.server.watcher.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String,
    val isMasterOn: Boolean = false,
    val hostedZoneId: String? = null,
    val hostedZoneName: String? = null,
)
