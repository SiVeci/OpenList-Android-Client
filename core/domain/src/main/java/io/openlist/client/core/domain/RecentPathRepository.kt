package io.openlist.client.core.domain

import io.openlist.client.core.model.RecentPath
import kotlinx.coroutines.flow.Flow

interface RecentPathRepository {
    fun observeByInstance(instanceId: String): Flow<List<RecentPath>>

    fun observeAll(): Flow<List<RecentPath>>

    suspend fun recordPath(instanceId: String, path: String, displayName: String? = null)

    suspend fun deleteByInstanceId(instanceId: String)
}
