package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.PreviewCacheEntity

@Dao
interface PreviewCacheDao {
    @Query("SELECT * FROM preview_cache WHERE id = :id")
    suspend fun getById(id: String): PreviewCacheEntity?

    @Query("SELECT * FROM preview_cache WHERE cacheKey = :cacheKey")
    suspend fun getByCacheKey(cacheKey: String): PreviewCacheEntity?

    @Query("SELECT * FROM preview_cache WHERE instanceId = :instanceId AND path = :path")
    suspend fun getByInstanceAndPath(instanceId: String, path: String): List<PreviewCacheEntity>

    /** Conflicts on the (instanceId, path, kind) unique index, so re-caching
     * the same preview target just refreshes the existing row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PreviewCacheEntity)

    @Query("DELETE FROM preview_cache WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    @Query("DELETE FROM preview_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Exact-path cache invalidation (S3-T4, PreviewRepository.invalidate) —
     * used after a single file is overwritten (e.g. upload) so its stale
     * cached body can't resurface. */
    @Query("DELETE FROM preview_cache WHERE instanceId = :instanceId AND path = :path")
    suspend fun deleteByInstanceAndPath(instanceId: String, path: String)

    /** Subtree cache invalidation (S3-T4, PreviewRepository.invalidateByPrefix)
     * — same LIKE-prefix matching as [io.openlist.client.core.database.dao.FileCacheDao.deleteByPathPrefix],
     * used after rename/remove/move/copy so a moved/deleted subtree's stale
     * cached bodies can't resurface. */
    @Query("DELETE FROM preview_cache WHERE instanceId = :instanceId AND (path = :pathPrefix OR path LIKE :pathPrefix || '/%')")
    suspend fun deleteByPathPrefix(instanceId: String, pathPrefix: String)

    /** Read-before-delete companion to [deleteByPathPrefix]: callers must
     * delete each row's [PreviewCacheEntity.localFilePath] file before
     * dropping the metadata rows, since this DAO only owns the metadata
     * table (same split as [deleteExpired]). */
    @Query("SELECT * FROM preview_cache WHERE instanceId = :instanceId AND (path = :pathPrefix OR path LIKE :pathPrefix || '/%')")
    suspend fun getByPathPrefix(instanceId: String, pathPrefix: String): List<PreviewCacheEntity>

    /** Batch TTL cleanup (P-415: cache rows carry an [PreviewCacheEntity.expiresAt]
     * so a periodic/opportunistic sweep can drop stale entries without a full
     * per-instance wipe). Callers are responsible for deleting the
     * corresponding files under `context.cacheDir/preview/...` for the rows
     * this returns/removes — this DAO only owns the metadata table. */
    @Query("DELETE FROM preview_cache WHERE expiresAt IS NOT NULL AND expiresAt < :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)
}
