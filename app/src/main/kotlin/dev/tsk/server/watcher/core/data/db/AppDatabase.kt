package dev.tsk.server.watcher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun subdomainDao(): SubdomainDao
    abstract fun instanceDao(): InstanceDao
    abstract fun instanceConfigDao(): InstanceConfigDao

    companion object {
        const val DB_NAME = "server_watcher.db"

        /** Adds the per-subdomain health-check scheme, port and timeout. Existing rows keep today's behaviour. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subdomains ADD COLUMN healthScheme TEXT NOT NULL DEFAULT 'https'")
                db.execSQL("ALTER TABLE subdomains ADD COLUMN healthPort INTEGER")
                db.execSQL("ALTER TABLE subdomains ADD COLUMN healthTimeoutSeconds INTEGER NOT NULL DEFAULT 10")
            }
        }
    }
}
