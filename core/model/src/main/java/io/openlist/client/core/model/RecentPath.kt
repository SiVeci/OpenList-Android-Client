package io.openlist.client.core.model

data class RecentPath(
    val instanceId: String,
    val path: String,
    val displayName: String,
    val visitedAt: Long,
)
