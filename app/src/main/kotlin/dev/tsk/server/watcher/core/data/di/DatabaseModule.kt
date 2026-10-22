package dev.tsk.server.watcher.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tsk.server.watcher.core.data.db.AppDatabase
import dev.tsk.server.watcher.core.data.db.DatabaseKeyProvider
import dev.tsk.server.watcher.core.data.db.dao.DomainDao
import dev.tsk.server.watcher.core.data.db.dao.InstanceConfigDao
import dev.tsk.server.watcher.core.data.db.dao.InstanceDao
import dev.tsk.server.watcher.core.data.db.dao.SubdomainDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): AppDatabase {
        val passphrase = keyProvider.getPassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDomainDao(db: AppDatabase): DomainDao = db.domainDao()

    @Provides
    fun provideSubdomainDao(db: AppDatabase): SubdomainDao = db.subdomainDao()

    @Provides
    fun provideInstanceDao(db: AppDatabase): InstanceDao = db.instanceDao()

    @Provides
    fun provideInstanceConfigDao(db: AppDatabase): InstanceConfigDao = db.instanceConfigDao()
}
