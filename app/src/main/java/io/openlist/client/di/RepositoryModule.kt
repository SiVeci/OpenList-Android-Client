package io.openlist.client.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.OfflineDownloadRepository
import io.openlist.client.core.domain.SearchRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.data.repository.AuthRepositoryImpl
import io.openlist.client.data.repository.DirectoryPickerRepositoryImpl
import io.openlist.client.data.repository.FileOperationRepositoryImpl
import io.openlist.client.data.repository.FilesRepositoryImpl
import io.openlist.client.data.repository.InstanceRepositoryImpl
import io.openlist.client.data.repository.OfflineDownloadRepositoryImpl
import io.openlist.client.data.repository.SearchRepositoryImpl
import io.openlist.client.data.repository.ShareRepositoryImpl
import io.openlist.client.data.repository.TaskAggregationRepositoryImpl
import io.openlist.client.data.repository.TaskRepositoryImpl
import io.openlist.client.data.repository.TransferRepositoryImpl
import io.openlist.client.data.repository.UploadRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindFileOperationRepository(impl: FileOperationRepositoryImpl): FileOperationRepository

    @Binds
    @Singleton
    abstract fun bindDirectoryPickerRepository(impl: DirectoryPickerRepositoryImpl): DirectoryPickerRepository

    @Binds
    @Singleton
    abstract fun bindUploadRepository(impl: UploadRepositoryImpl): UploadRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindOfflineDownloadRepository(impl: OfflineDownloadRepositoryImpl): OfflineDownloadRepository

    @Binds
    @Singleton
    abstract fun bindTaskAggregationRepository(impl: TaskAggregationRepositoryImpl): TaskAggregationRepository
}
