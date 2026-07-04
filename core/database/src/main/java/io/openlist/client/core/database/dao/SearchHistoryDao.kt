package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE instanceId = :instanceId ORDER BY searchedAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<SearchHistoryEntity>>

    /** Conflicts on the (instanceId, keyword, scopePath) unique index, so a
     * repeated search just refreshes [SearchHistoryEntity.searchedAt]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE instanceId = :instanceId AND keyword = :keyword AND scopePath IS :scopePath")
    suspend fun delete(instanceId: String, keyword: String, scopePath: String?)

    @Query("DELETE FROM search_history WHERE instanceId = :instanceId")
    suspend fun clearInstance(instanceId: String)

    /** Caps history at [limit] entries per instance (§11: "上限 20 条/实例"). */
    @Query(
        "DELETE FROM search_history WHERE instanceId = :instanceId AND id NOT IN " +
            "(SELECT id FROM search_history WHERE instanceId = :instanceId ORDER BY searchedAt DESC LIMIT :limit)",
    )
    suspend fun trimToLimit(instanceId: String, limit: Int)

    @Query("DELETE FROM search_history WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
