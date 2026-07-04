package io.openlist.client.core.model

/** One `/api/fs/search` hit (v0.3_EXECUTION_PLAN.md §11). */
data class SearchResultItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val type: Int,
)

/** Domain projection of `SearchHistoryEntity`. */
data class SearchHistoryItem(
    val id: String,
    val keyword: String,
    val scopePath: String?,
    val searchedAt: Long,
)
