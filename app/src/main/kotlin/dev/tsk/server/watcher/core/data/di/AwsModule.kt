package dev.tsk.server.watcher.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tsk.server.watcher.core.data.aws.AwsClientProvider
import dev.tsk.server.watcher.core.data.aws.CognitoTokenManager
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AwsModule {

    @Provides
    @Singleton
    fun provideAwsClientProvider(
        tokenManager: CognitoTokenManager,
        configRepository: AwsConfigRepository,
    ): AwsClientProvider = AwsClientProvider(tokenManager, configRepository)
}
