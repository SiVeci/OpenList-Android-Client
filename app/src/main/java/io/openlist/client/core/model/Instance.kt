package io.openlist.client.core.model

/** Domain model — deliberately separate from InstanceEntity so Room/DTO details
 * (annotations, raw JSON) never leak past the Repository boundary. */
data class Instance(
    val id: String,
    val name: String,
    val baseUrl: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
    val isCurrent: Boolean,
    val note: String?,
)
