package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.openlist.client.core.database.entity.InstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {
    @Query("SELECT * FROM instances ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<InstanceEntity>>

    @Query("SELECT * FROM instances WHERE id = :id")
    suspend fun getById(id: String): InstanceEntity?

    @Query("SELECT * FROM instances WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): InstanceEntity?

    @Query("SELECT COUNT(*) FROM instances")
    suspend fun count(): Int

    /** Case-insensitive exact match, used to reject duplicate Base URLs on add. */
    @Query("SELECT * FROM instances WHERE baseUrl = :baseUrl COLLATE NOCASE LIMIT 1")
    suspend fun getByBaseUrl(baseUrl: String): InstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: InstanceEntity)

    @Update
    suspend fun update(instance: InstanceEntity)

    @Delete
    suspend fun delete(instance: InstanceEntity)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE instances SET isCurrent = 0 WHERE id != :keepId")
    suspend fun clearCurrentExcept(keepId: String)
}
