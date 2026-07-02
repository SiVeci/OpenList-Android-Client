package io.openlist.client.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.openlist.client.core.database.dao.DownloadTaskDao
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.InstanceDao
import io.openlist.client.core.database.dao.SessionDao
import io.openlist.client.core.database.entity.DownloadTaskEntity
import io.openlist.client.core.database.entity.FileCacheEntity
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.SessionEntity

// All v0.1 entities are now present. No app has shipped yet, so schema bumps
// before v0.1's release use fallbackToDestructiveMigration (see di/DatabaseModule)
// instead of hand-written Migrations.
@Database(
    entities = [
        InstanceEntity::class,
        SessionEntity::class,
        FileCacheEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class OpenListDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun sessionDao(): SessionDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
