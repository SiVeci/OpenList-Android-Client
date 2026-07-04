package io.openlist.client.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.RemoteTaskDao
import io.openlist.client.core.database.dao.SearchHistoryDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.dao.ShareDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.FileCacheEntity
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.RemoteTaskEntity
import io.openlist.client.core.database.entity.SearchHistoryEntity
import io.openlist.client.core.database.entity.SessionEntity
import io.openlist.client.core.database.entity.ShareEntity
import io.openlist.client.core.database.entity.UploadTaskEntity

// v0.1.0 has shipped, so schema bumps from here on use hand-written Migrations
// (registered in di/DatabaseModule) instead of a destructive fallback.
@Database(
    entities = [
        InstanceEntity::class,
        SessionEntity::class,
        FileCacheEntity::class,
        DownloadTaskEntity::class,
        UploadTaskEntity::class,
        ShareEntity::class,
        SearchHistoryEntity::class,
        RemoteTaskEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class OpenListDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun sessionDao(): SessionDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun uploadTaskDao(): UploadTaskDao
    abstract fun shareDao(): ShareDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun remoteTaskDao(): RemoteTaskDao
}
