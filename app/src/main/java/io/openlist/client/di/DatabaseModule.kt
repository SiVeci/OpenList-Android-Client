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
            // Pre-release only: no shipped v0.1 build has real user data yet, so
            // schema bumps recreate tables instead of requiring hand-written
            // Migrations. Replace with real Migrations once v0.1 ships.
            .fallbackToDestructiveMigration()
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
