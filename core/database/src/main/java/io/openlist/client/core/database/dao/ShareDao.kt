package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.openlist.client.core.database.entity.ShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {
    @Query("SELECT * FROM shares WHERE instanceId = :instanceId ORDER BY createdAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<ShareEntity>>

    @Query("SELECT * FROM shares WHERE id = :id AND instanceId = :instanceId")
    suspend fun getById(id: String, instanceId: String): ShareEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(share: ShareEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(shares: List<ShareEntity>)

    @Query("DELETE FROM shares WHERE instanceId = :instanceId")
    suspend fun clearInstance(instanceId: String)

    /** Overwrites an instance's cached share list atomically (§18.1: "请求成功后覆盖缓存"). */
    @Transaction
    suspend fun replaceAll(instanceId: String, shares: List<ShareEntity>) {
        clearInstance(instanceId)
        upsertAll(shares)
    }

    @Query("DELETE FROM shares WHERE id = :id AND instanceId = :instanceId")
    suspend fun deleteById(id: String, instanceId: String)

    @Query("DELETE FROM shares WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
