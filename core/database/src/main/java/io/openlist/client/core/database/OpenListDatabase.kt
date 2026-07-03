package io.openlist.client.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.dao.UploadTaskDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.FileCacheEntity
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.SessionEntity
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
    ],
    version = 6,
    exportSchema = true,
)
abstract class OpenListDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun sessionDao(): SessionDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun uploadTaskDao(): UploadTaskDao
}
