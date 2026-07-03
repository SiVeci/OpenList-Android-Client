package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.UploadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadTaskDao {
    @Query("SELECT * FROM upload_tasks WHERE instanceId = :instanceId ORDER BY createdAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_tasks WHERE id = :id")
    suspend fun getById(id: String): UploadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: UploadTaskEntity)

    @Query("UPDATE upload_tasks SET status = :status, uploadedBytes = :uploadedBytes, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, uploadedBytes: Long, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE upload_tasks SET workRequestId = :workRequestId WHERE id = :id")
    suspend fun setWorkRequestId(id: String, workRequestId: String)

    @Query("DELETE FROM upload_tasks WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
