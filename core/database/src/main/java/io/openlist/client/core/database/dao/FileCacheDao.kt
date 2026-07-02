package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.openlist.client.core.database.entity.FileCacheEntity

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM file_cache WHERE instanceId = :instanceId AND parentPath = :parentPath ORDER BY isDir DESC, name COLLATE NOCASE ASC")
    suspend fun getByParent(instanceId: String, parentPath: String): List<FileCacheEntity>

    @Query("SELECT MIN(cachedAt) FROM file_cache WHERE instanceId = :instanceId AND parentPath = :parentPath")
    suspend fun getCachedAt(instanceId: String, parentPath: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<FileCacheEntity>)

    @Query("DELETE FROM file_cache WHERE instanceId = :instanceId AND parentPath = :parentPath")
    suspend fun clearDirectory(instanceId: String, parentPath: String)

    /** Overwrites a directory's cached listing atomically (v0.1_PRD §8.5 rule 4:
     * "请求成功后覆盖缓存") so entries removed server-side don't linger. */
    @Transaction
    suspend fun replaceDirectory(instanceId: String, parentPath: String, entries: List<FileCacheEntity>) {
        clearDirectory(instanceId, parentPath)
        upsertAll(entries)
    }

    @Query("DELETE FROM file_cache WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    /** Settings' "清理缓存" — every instance's directory cache. */
    @Query("DELETE FROM file_cache")
    suspend fun clearAll()
}
