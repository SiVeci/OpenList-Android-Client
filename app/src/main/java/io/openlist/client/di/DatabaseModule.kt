package io.openlist.client.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.database.MIGRATION_4_5
import io.openlist.client.core.database.MIGRATION_5_6
import io.openlist.client.core.database.MIGRATION_6_7
import io.openlist.client.core.database.MIGRATION_7_8
import io.openlist.client.core.database.MIGRATION_8_9
import io.openlist.client.core.database.MIGRATION_9_10
import io.openlist.client.core.database.MIGRATION_10_11
import io.openlist.client.core.database.OpenListDatabase
import io.openlist.client.core.database.dao.AdminCacheDao
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.PreviewCacheDao
import io.openlist.client.core.database.dao.RecentPathDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.SearchHistoryDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.dao.SystemWriteTransactionDao
import io.openlist.client.core.database.dao.UploadTaskDao
import javax.inject.Singleton

// AppPreferences is provided via its @Inject constructor (@ApplicationContext),
// so it is intentionally not declared here — a @Provides for it would be a
// duplicate binding.
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenListDatabase =
        Room.databaseBuilder(context, OpenListDatabase::class.java, "openlist.db")
            // v0.1.0 has shipped with real user data, so schema bumps from here on
            // require hand-written Migrations instead of a destructive fallback.
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()

    @Provides
    fun provideInstanceDao(database: OpenListDatabase): InstanceDao = database.instanceDao()

    @Provides
    fun provideSessionDao(database: OpenListDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideFileCacheDao(database: OpenListDatabase): FileCacheDao = database.fileCacheDao()

    @Provides
    fun provideDownloadTaskDao(database: OpenListDatabase): DownloadTaskDao = database.downloadTaskDao()

    @Provides
    fun provideUploadTaskDao(database: OpenListDatabase): UploadTaskDao = database.uploadTaskDao()

    @Provides
    fun provideShareDao(database: OpenListDatabase): ShareDao = database.shareDao()

    @Provides
    fun provideSearchHistoryDao(database: OpenListDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideRemoteTaskDao(database: OpenListDatabase): RemoteTaskDao = database.remoteTaskDao()

    @Provides
    fun providePreviewCacheDao(database: OpenListDatabase): PreviewCacheDao = database.previewCacheDao()

    @Provides
    fun provideAdminCacheDao(database: OpenListDatabase): AdminCacheDao = database.adminCacheDao()

    @Provides
    fun provideRecentPathDao(database: OpenListDatabase): RecentPathDao = database.recentPathDao()

    @Provides
    fun provideSystemDocumentDao(database: OpenListDatabase): SystemDocumentDao = database.systemDocumentDao()

    @Provides
    fun provideSystemWriteTransactionDao(database: OpenListDatabase): SystemWriteTransactionDao = database.systemWriteTransactionDao()
}
