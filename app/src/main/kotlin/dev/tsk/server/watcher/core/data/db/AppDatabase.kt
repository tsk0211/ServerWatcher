package dev.tsk.server.watcher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

import dev.tsk.server.watcher.core.data.db.dao.DomainDao
import dev.tsk.server.watcher.core.data.db.dao.InstanceConfigDao
import dev.tsk.server.watcher.core.data.db.dao.InstanceDao
import dev.tsk.server.watcher.core.data.db.dao.SubdomainDao
import dev.tsk.server.watcher.core.data.db.entity.DomainEntity
import dev.tsk.server.watcher.core.data.db.entity.InstanceConfigEntity
import dev.tsk.server.watcher.core.data.db.entity.InstanceEntity
import dev.tsk.server.watcher.core.data.db.entity.SubdomainEntity

@Database(
    entities = [DomainEntity::class, SubdomainEntity::class, InstanceEntity::class, InstanceConfigEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun subdomainDao(): SubdomainDao
    abstract fun instanceDao(): InstanceDao
    abstract fun instanceConfigDao(): InstanceConfigDao

    companion object {
        const val DB_NAME = "server_watcher.db"
    }
}
