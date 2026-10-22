package dev.tsk.server.watcher.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subdomains",
    foreignKeys = [
        ForeignKey(
            entity = DomainEntity::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("domainId")],
)
data class SubdomainEntity(
    @PrimaryKey val id: String,
    val domainId: String,
    val prefix: String,
    val isApex: Boolean,
    val healthPath: String,
    val healthRangeStart: Int,
    val healthRangeEnd: Int,
    val healthIntervalSeconds: Int,
)
