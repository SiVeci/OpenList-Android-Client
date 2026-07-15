package io.openlist.client.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.domain.AdminGateRepository
import io.openlist.client.core.domain.AdminIndexRepository
import io.openlist.client.core.domain.AdminSettingsRepository
import io.openlist.client.core.domain.AdminStorageRepository
import io.openlist.client.core.domain.AdminTaskRepository
import io.openlist.client.core.domain.AdminUserRepository
import io.openlist.client.core.domain.AdminWebFallbackRepository
import io.openlist.client.core.domain.AuthRepository
import io.openlist.client.core.domain.DirectoryPickerRepository
import io.openlist.client.core.domain.ExternalOpenRepository
import io.openlist.client.core.domain.FileOperationRepository
import io.openlist.client.core.domain.FilesRepository
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.MediaRepository
import io.openlist.client.core.domain.OfflineDownloadRepository
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.domain.RecentPathRepository
import io.openlist.client.core.domain.SearchRepository
import io.openlist.client.core.domain.ShareRepository
import io.openlist.client.core.domain.SubtitleRepository
import io.openlist.client.core.domain.TaskAggregationRepository
import io.openlist.client.core.domain.TaskRepository
import io.openlist.client.core.domain.TransferRepository
import io.openlist.client.core.domain.UploadRepository
import io.openlist.client.core.domain.SystemDocumentsRepository
import io.openlist.client.data.repository.AndroidMimeTypeResolver
import io.openlist.client.data.repository.AdminGateRepositoryImpl
import io.openlist.client.data.repository.AdminIndexRepositoryImpl
import io.openlist.client.data.repository.AdminSettingsRepositoryImpl
import io.openlist.client.data.repository.AdminStorageRepositoryImpl
import io.openlist.client.data.repository.AdminTaskRepositoryImpl
import io.openlist.client.data.repository.AdminUserRepositoryImpl
import io.openlist.client.data.repository.AdminWebFallbackRepositoryImpl
import io.openlist.client.data.repository.AuthRepositoryImpl
import io.openlist.client.data.repository.DirectoryPickerRepositoryImpl
import io.openlist.client.data.repository.ExternalOpenRepositoryImpl
import io.openlist.client.data.repository.FileOperationRepositoryImpl
import io.openlist.client.data.repository.FilesRepositoryImpl
import io.openlist.client.data.repository.InstanceRepositoryImpl
import io.openlist.client.data.repository.MediaRepositoryImpl
import io.openlist.client.data.repository.MimeTypeResolver
import io.openlist.client.data.repository.OfflineDownloadRepositoryImpl
import io.openlist.client.data.repository.PreviewRepositoryImpl
import io.openlist.client.data.repository.RecentPathRepositoryImpl
import io.openlist.client.data.repository.SearchRepositoryImpl
import io.openlist.client.data.repository.ShareRepositoryImpl
import io.openlist.client.data.repository.SubtitleRepositoryImpl
import io.openlist.client.data.repository.TaskAggregationRepositoryImpl
import io.openlist.client.data.repository.TaskRepositoryImpl
import io.openlist.client.data.repository.TransferRepositoryImpl
import io.openlist.client.data.repository.UploadRepositoryImpl
import io.openlist.client.data.repository.SystemDocumentsRepositoryImpl
import io.openlist.client.data.repository.AndroidSystemDocumentVolume
import io.openlist.client.data.repository.SystemDocumentVolume
import io.openlist.client.data.repository.SystemDocumentLocalCommitter
import io.openlist.client.data.repository.StrongSystemDocumentLocalCommitter
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
    abstract fun bindRecentPathRepository(impl: RecentPathRepositoryImpl): RecentPathRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindOfflineDownloadRepository(impl: OfflineDownloadRepositoryImpl): OfflineDownloadRepository

    @Binds
    @Singleton
    abstract fun bindTaskAggregationRepository(impl: TaskAggregationRepositoryImpl): TaskAggregationRepository

    @Binds
    @Singleton
    abstract fun bindPreviewRepository(impl: PreviewRepositoryImpl): PreviewRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleRepository(impl: SubtitleRepositoryImpl): SubtitleRepository

    @Binds
    @Singleton
    abstract fun bindExternalOpenRepository(impl: ExternalOpenRepositoryImpl): ExternalOpenRepository

    @Binds
    @Singleton
    abstract fun bindMimeTypeResolver(impl: AndroidMimeTypeResolver): MimeTypeResolver

    @Binds
    @Singleton
    abstract fun bindAdminGateRepository(impl: AdminGateRepositoryImpl): AdminGateRepository

    @Binds
    @Singleton
    abstract fun bindAdminUserRepository(impl: AdminUserRepositoryImpl): AdminUserRepository

    @Binds
    @Singleton
    abstract fun bindAdminStorageRepository(impl: AdminStorageRepositoryImpl): AdminStorageRepository

    @Binds
    @Singleton
    abstract fun bindAdminTaskRepository(impl: AdminTaskRepositoryImpl): AdminTaskRepository

    @Binds
    @Singleton
    abstract fun bindAdminIndexRepository(impl: AdminIndexRepositoryImpl): AdminIndexRepository

    @Binds
    @Singleton
    abstract fun bindAdminSettingsRepository(impl: AdminSettingsRepositoryImpl): AdminSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAdminWebFallbackRepository(impl: AdminWebFallbackRepositoryImpl): AdminWebFallbackRepository

    @Binds
    @Singleton
    abstract fun bindSystemDocumentsRepository(impl: SystemDocumentsRepositoryImpl): SystemDocumentsRepository

    @Binds
    @Singleton
    abstract fun bindSystemDocumentVolume(impl: AndroidSystemDocumentVolume): SystemDocumentVolume

    @Binds
    @Singleton
    abstract fun bindSystemDocumentLocalCommitter(impl: StrongSystemDocumentLocalCommitter): SystemDocumentLocalCommitter
}
