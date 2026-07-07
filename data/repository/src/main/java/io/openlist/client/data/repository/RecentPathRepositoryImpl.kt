package io.openlist.client.data.repository

import io.openlist.client.core.database.dao.RecentPathDao
import io.openlist.client.core.database.entity.RecentPathEntity
import io.openlist.client.core.domain.RecentPathRepository
import io.openlist.client.core.model.RecentPath
import io.openlist.client.core.network.OpenListPathCodec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RecentPathRepositoryImpl @Inject constructor(
    private val recentPathDao: RecentPathDao,
) : RecentPathRepository {

    override fun observeByInstance(instanceId: String): Flow<List<RecentPath>> =
        recentPathDao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<RecentPath>> =
        recentPathDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun recordPath(instanceId: String, path: String, displayName: String?) {
        val normalizedPath = OpenListPathCodec.normalize(path)
        recentPathDao.upsert(
            RecentPathEntity(
                instanceId = instanceId,
                path = normalizedPath,
                displayName = displayName?.takeIf { it.isNotBlank() } ?: normalizedPath.toDisplayName(),
                visitedAt = System.currentTimeMillis(),
            ),
        )
        recentPathDao.trimToLimit(instanceId, RECENT_LIMIT)
    }

    override suspend fun deleteByInstanceId(instanceId: String) {
        recentPathDao.deleteByInstanceId(instanceId)
    }

    private fun RecentPathEntity.toDomain() = RecentPath(
        instanceId = instanceId,
        path = path,
        displayName = displayName,
        visitedAt = visitedAt,
    )

    private fun String.toDisplayName(): String =
        if (this == "/") {
            "根目录"
        } else {
            trimEnd('/').substringAfterLast('/').ifBlank { this }
        }

    private companion object {
        const val RECENT_LIMIT = 50
    }
}
