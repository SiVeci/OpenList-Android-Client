package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.RecentPathEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPathDao {
    @Query("SELECT * FROM recent_paths WHERE instanceId = :instanceId ORDER BY visitedAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<RecentPathEntity>>

    @Query("SELECT * FROM recent_paths ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<RecentPathEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentPathEntity)

    /** Caps history at [limit] entries per instance (v1.1 DEC-H2: 50). */
    @Query(
        "DELETE FROM recent_paths WHERE instanceId = :instanceId AND path NOT IN " +
            "(SELECT path FROM recent_paths WHERE instanceId = :instanceId ORDER BY visitedAt DESC LIMIT :limit)",
    )
    suspend fun trimToLimit(instanceId: String, limit: Int)

    @Query("DELETE FROM recent_paths WHERE instanceId = :instanceId AND path = :path")
    suspend fun delete(instanceId: String, path: String)

    @Query("DELETE FROM recent_paths WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
