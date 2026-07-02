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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
