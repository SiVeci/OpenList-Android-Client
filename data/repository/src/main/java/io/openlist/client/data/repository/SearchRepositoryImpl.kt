package io.openlist.client.data.repository

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.common.DomainError
import io.openlist.client.core.database.dao.SearchHistoryDao
import io.openlist.client.core.database.entity.SearchHistoryEntity
import io.openlist.client.core.domain.InstanceRepository
import io.openlist.client.core.domain.SearchRepository
import io.openlist.client.core.model.SearchHistoryItem
import io.openlist.client.core.model.SearchResultItem
import io.openlist.client.core.network.InstanceContext
import io.openlist.client.core.network.InstanceScope
import io.openlist.client.core.network.OpenListClientFactory
import io.openlist.client.core.network.OpenListPathCodec
import io.openlist.client.core.network.dto.SearchNodeResp
import io.openlist.client.core.network.dto.SearchReq
import io.openlist.client.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val instanceRepository: InstanceRepository,
    private val clientFactory: OpenListClientFactory,
    private val instanceContext: InstanceContext,
) : SearchRepository {

    override suspend fun search(instanceId: String, keyword: String, scopePath: String?): ApiResult<List<SearchResultItem>> {
        val instance = instanceRepository.getById(instanceId) ?: return ApiResult.Failure(DomainError.InvalidInstance)
        instanceContext.set(InstanceScope(instance.id, instance.baseUrl))
        val api = clientFactory.apiFor(instance.baseUrl)
        val parent = OpenListPathCodec.normalize(scopePath ?: "/")
        val req = SearchReq(parent = parent, keywords = keyword)
        return when (val result = safeApiCall { api.fsSearch(req) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.content.map { it.toDomain(fallbackParent = parent) })
            is ApiResult.Failure -> ApiResult.Failure(result.error.remapIndexNotBuilt())
        }
    }

    /** The backend's exact "no search index" error shape isn't confirmed yet
     * (v0.3_EXECUTION_PLAN.md V-04) — this keyword heuristic is a provisional
     * best guess pending real-device verification; any other server message
     * passes through unchanged. */
    private fun DomainError.remapIndexNotBuilt(): DomainError {
        val message = (this as? DomainError.OpenListError)?.message ?: return this
        val lower = message.lowercase()
        return if ("index" in lower || "未建立索引" in message || "尚未" in message) {
            DomainError.SearchNotAvailable
        } else {
            this
        }
    }

    override fun observeSearchHistory(instanceId: String): Flow<List<SearchHistoryItem>> =
        searchHistoryDao.observeByInstance(instanceId).map { list -> list.map { it.toDomain() } }

    override suspend fun saveSearchKeyword(instanceId: String, keyword: String, scopePath: String?) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        searchHistoryDao.upsert(
            SearchHistoryEntity(
                id = UUID.randomUUID().toString(),
                instanceId = instanceId,
                keyword = trimmed,
                scopePath = scopePath,
                searchedAt = System.currentTimeMillis(),
            ),
        )
        searchHistoryDao.trimToLimit(instanceId, HISTORY_LIMIT)
    }

    override suspend fun deleteSearchKeyword(instanceId: String, keyword: String, scopePath: String?) {
        searchHistoryDao.delete(instanceId, keyword, scopePath)
    }

    override suspend fun clearSearchHistory(instanceId: String) {
        searchHistoryDao.clearInstance(instanceId)
    }

    private fun SearchNodeResp.toDomain(fallbackParent: String) = SearchResultItem(
        name = name,
        path = OpenListPathCodec.child(parent.ifBlank { fallbackParent }, name),
        isDir = isDir,
        size = size,
        type = type,
    )

    private fun SearchHistoryEntity.toDomain() = SearchHistoryItem(
        id = id,
        keyword = keyword,
        scopePath = scopePath,
        searchedAt = searchedAt,
    )

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
