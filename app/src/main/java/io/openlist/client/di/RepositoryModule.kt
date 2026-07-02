package io.openlist.client.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.data.repository.AuthRepositoryImpl
import io.openlist.client.data.repository.FilesRepositoryImpl
import io.openlist.client.data.repository.InstanceRepositoryImpl
import io.openlist.client.data.repository.TransferRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindInstanceRepository(impl: InstanceRepositoryImpl): InstanceRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFilesRepository(impl: FilesRepositoryImpl): FilesRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository
}
