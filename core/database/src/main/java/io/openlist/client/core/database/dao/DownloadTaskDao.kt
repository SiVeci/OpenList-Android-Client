package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks WHERE instanceId = :instanceId ORDER BY createdAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    /** Rows DownloadManager might still be working on — P9's status-refresh
     * only needs to query these, never the ones already at a terminal status. */
    @Query("SELECT * FROM download_tasks WHERE instanceId = :instanceId AND status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED')")
    suspend fun getActiveByInstance(instanceId: String): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query(
        "UPDATE download_tasks SET status = :status, progress = :progress, errorMessage = :errorMessage, " +
            "localUri = :localUri, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: String, status: String, progress: Int?, errorMessage: String?, localUri: String?, updatedAt: Long)

    @Query("DELETE FROM download_tasks WHERE instanceId = :instanceId AND status = 'SUCCESS'")
    suspend fun deleteFinishedByInstanceId(instanceId: String)

    @Query("DELETE FROM download_tasks WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
