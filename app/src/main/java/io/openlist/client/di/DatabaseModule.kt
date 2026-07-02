package io.openlist.client.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.database.OpenListDatabase
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.SessionDao
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
            // require hand-written Migrations (registered here as they're added,
            // starting with MIGRATION_4_5 in v0.2) instead of a destructive fallback.
            .build()

    @Provides
    fun provideInstanceDao(database: OpenListDatabase): InstanceDao = database.instanceDao()

    @Provides
    fun provideSessionDao(database: OpenListDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideFileCacheDao(database: OpenListDatabase): FileCacheDao = database.fileCacheDao()

    @Provides
    fun provideDownloadTaskDao(database: OpenListDatabase): DownloadTaskDao = database.downloadTaskDao()
}
