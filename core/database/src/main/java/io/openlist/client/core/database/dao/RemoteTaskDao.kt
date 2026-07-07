package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.openlist.client.core.database.entity.RemoteTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteTaskDao {
    @Query("SELECT * FROM remote_tasks WHERE instanceId = :instanceId ORDER BY cachedAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<RemoteTaskEntity>>

    @Query("SELECT * FROM remote_tasks WHERE id = :id AND instanceId = :instanceId")
    suspend fun getById(id: String, instanceId: String): RemoteTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<RemoteTaskEntity>)

    @Query("DELETE FROM remote_tasks WHERE instanceId = :instanceId AND taskType = :taskType")
    suspend fun clearType(instanceId: String, taskType: String)

    @Query("DELETE FROM remote_tasks WHERE instanceId = :instanceId AND status = 'SUCCESS'")
    suspend fun deleteFinishedByInstanceId(instanceId: String)

    /** Overwrites one task type's cached rows atomically (§18.3: "远程任务...
     * 刷新并 replaceByInstance") so tasks no longer returned by the backend
     * (e.g. purged history) don't linger locally. */
    @Transaction
    suspend fun replaceType(instanceId: String, taskType: String, tasks: List<RemoteTaskEntity>) {
        clearType(instanceId, taskType)
        upsertAll(tasks)
    }

    @Query("DELETE FROM remote_tasks WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
