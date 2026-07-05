package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.AdminCacheEntity

/**
 * Basic CRUD for [AdminCacheEntity] (v0.5_EXECUTION_PLAN.md §8.3, S1-T6). TTL
 * expiry is a Repository-layer concern (each Admin* repository compares its
 * own TTL against [AdminCacheEntity.cachedAt] — 1 min for users, 30s for
 * storages, 5 min for settings per PRD §13.1), not something this DAO
 * enforces, same split as [io.openlist.client.core.database.dao.PreviewCacheDao].
 */
@Dao
interface AdminCacheDao {
    @Query("SELECT * FROM admin_cache WHERE instanceId = :instanceId AND scope = :scope AND cacheKey = :cacheKey")
    suspend fun get(instanceId: String, scope: String, cacheKey: String): AdminCacheEntity?

    /** Conflicts on the (instanceId, scope, cacheKey) unique index, so
     * re-caching the same scope+key just refreshes the existing row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AdminCacheEntity)

    @Query("DELETE FROM admin_cache WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    @Query("DELETE FROM admin_cache WHERE instanceId = :instanceId AND scope = :scope")
    suspend fun deleteByScope(instanceId: String, scope: String)
}
