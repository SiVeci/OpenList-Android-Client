package io.openlist.client.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local opaque UUID mapping for a DocumentsProvider document. */
@Entity(
    tableName = "system_documents",
    indices = [
        Index(value = ["instanceId", "currentPath"], unique = true),
        Index(value = ["instanceId", "parentDocumentId", "lifecycle"]),
        Index(value = ["instanceId", "lastKnownPath"]),
    ],
)
data class SystemDocumentEntity(
    @PrimaryKey val documentId: String,
    val instanceId: String,
    val parentDocumentId: String?,
    val currentPath: String?,
    val lastKnownPath: String,
    val displayName: String,
    val isDirectory: Boolean,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
    val hashInfo: String?,
    val provider: String?,
    val lifecycle: String,
    val unsupportedCapabilitiesMask: Long,
    val capabilityUpdatedAt: Long?,
    val lastSeenAt: Long,
    val updatedAt: Long,
)
