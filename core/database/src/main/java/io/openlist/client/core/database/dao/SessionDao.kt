package io.openlist.client.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.openlist.client.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE instanceId = :instanceId")
    suspend fun getByInstanceId(instanceId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE instanceId = :instanceId")
    fun observeByInstanceId(instanceId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
