package io.openlist.client.core.domain

import io.openlist.client.core.common.ApiResult
import io.openlist.client.core.model.SearchHistoryItem
import io.openlist.client.core.model.SearchResultItem
import kotlinx.coroutines.flow.Flow

/**
 * `/api/fs/search` (requires the backend's search index — see
 * [io.openlist.client.core.common.DomainError.SearchNotAvailable]) plus
 * per-instance search history. Results are never cached (§18.2).
 */
interface SearchRepository {
    suspend fun search(instanceId: String, keyword: String, scopePath: String?): ApiResult<List<SearchResultItem>>

    fun observeSearchHistory(instanceId: String): Flow<List<SearchHistoryItem>>

    /** Upserts by (instanceId, keyword, scopePath) and trims to 20 entries per instance. */
    suspend fun saveSearchKeyword(instanceId: String, keyword: String, scopePath: String?)

    suspend fun deleteSearchKeyword(instanceId: String, keyword: String, scopePath: String?)

    suspend fun clearSearchHistory(instanceId: String)
}
