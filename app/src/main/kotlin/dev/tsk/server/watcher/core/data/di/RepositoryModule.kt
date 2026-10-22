package dev.tsk.server.watcher.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tsk.server.watcher.core.data.aws.Ec2RepositoryImpl
import dev.tsk.server.watcher.core.data.aws.Route53RepositoryImpl
import dev.tsk.server.watcher.core.data.health.HealthRepositoryImpl
import dev.tsk.server.watcher.core.data.local.DomainRepositoryImpl
import dev.tsk.server.watcher.core.data.local.InstanceConfigRepositoryImpl
import dev.tsk.server.watcher.core.data.local.InstanceRepositoryImpl
import dev.tsk.server.watcher.core.data.local.SubdomainRepositoryImpl
import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IRoute53Repository
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import dev.tsk.server.watcher.core.domain.repository.ISubdomainRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDomainRepository(impl: DomainRepositoryImpl): IDomainRepository

    @Binds
    @Singleton
    abstract fun bindSubdomainRepository(impl: SubdomainRepositoryImpl): ISubdomainRepository

    @Binds
    @Singleton
    abstract fun bindInstanceRepository(impl: InstanceRepositoryImpl): IInstanceRepository

    @Binds
    @Singleton
    abstract fun bindEc2Repository(impl: Ec2RepositoryImpl): IEc2Repository

    @Binds
    @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): IHealthRepository

    @Binds
    @Singleton
    abstract fun bindInstanceConfigRepository(impl: InstanceConfigRepositoryImpl): IInstanceConfigRepository

    @Binds
    @Singleton
    abstract fun bindRoute53Repository(impl: Route53RepositoryImpl): IRoute53Repository
}
