package io.openlist.client.data.repository

import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.SystemDocumentEntity
import io.openlist.client.core.network.OpenListPathCodec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable UUID-to-remote-path reconciliation for the DocumentsProvider.
 *
 * A path match is used only to preserve identity for an object that remains at
 * that exact path.  An externally renamed or deleted entry is tombstoned; a
 * later entry at a different path receives a new UUID instead of guessing that
 * it is the same remote object.
 */
@Singleton
class SystemDocumentMappingStore @Inject constructor(
    private val documentDao: SystemDocumentDao,
) {
    suspend fun ensureRoot(instance: InstanceEntity): SystemDocumentEntity {
        val existing = documentDao.getActiveByPath(instance.id, ROOT_PATH)
        val now = System.currentTimeMillis()
        val displayName = instance.name.trim().ifBlank { instance.id }
        val root = if (existing == null) {
            SystemDocumentEntity(
                documentId = UUID.randomUUID().toString(),
                instanceId = instance.id,
                parentDocumentId = null,
                currentPath = ROOT_PATH,
                lastKnownPath = ROOT_PATH,
                displayName = displayName,
                isDirectory = true,
                mimeType = DIRECTORY_MIME_TYPE,
                sizeBytes = null,
                modifiedAt = null,
                hashInfo = null,
                provider = null,
                lifecycle = LIFECYCLE_ACTIVE,
                unsupportedCapabilitiesMask = 0L,
                capabilityUpdatedAt = null,
                lastSeenAt = now,
                updatedAt = now,
            )
        } else {
            existing.copy(displayName = displayName, lastSeenAt = now, updatedAt = now)
        }
        documentDao.upsertAll(listOf(root))
        return root
    }

    suspend fun reconcileChildren(
        parent: SystemDocumentEntity,
        children: List<SystemRemoteDocument>,
    ): List<SystemDocumentEntity> {
        require(parent.lifecycle == LIFECYCLE_ACTIVE && parent.isDirectory)
        val parentPath = parent.currentPath ?: return emptyList()
        if (!OpenListPathCodec.isSafeNormalizedPath(parentPath)) return emptyList()

        val now = System.currentTimeMillis()
        val validChildren = children.mapNotNull { child ->
            val childPath = OpenListPathCodec.safeChild(parentPath, child.displayName) ?: return@mapNotNull null
            child.copy(path = childPath)
        }
        val existingChildren = documentDao.getActiveChildren(parent.instanceId, parent.documentId)
        val existingByPath = existingChildren.associateBy { it.currentPath }
        val incomingPaths = validChildren.mapTo(mutableSetOf()) { it.path }

        // Reconciliation proves deletion only for entries in this listed
        // parent. Tombstoning a directory also invalidates its descendants.
        existingChildren
            .filter { it.currentPath !in incomingPaths }
            .forEach { entity -> entity.currentPath?.let { documentDao.tombstonePathPrefix(parent.instanceId, it, now) } }

        val mapped = validChildren.map { child ->
            val existing = existingByPath[child.path]
            if (existing == null) {
                SystemDocumentEntity(
                    documentId = UUID.randomUUID().toString(),
                    instanceId = parent.instanceId,
                    parentDocumentId = parent.documentId,
                    currentPath = child.path,
                    lastKnownPath = child.path,
                    displayName = child.displayName,
                    isDirectory = child.isDirectory,
                    mimeType = child.mimeType,
                    sizeBytes = child.sizeBytes,
                    modifiedAt = child.modifiedAt,
                    hashInfo = child.hashInfo,
                    provider = child.provider,
                    lifecycle = LIFECYCLE_ACTIVE,
                    unsupportedCapabilitiesMask = 0L,
                    capabilityUpdatedAt = null,
                    lastSeenAt = now,
                    updatedAt = now,
                )
            } else {
                existing.copy(
                    displayName = child.displayName,
                    isDirectory = child.isDirectory,
                    mimeType = child.mimeType,
                    sizeBytes = child.sizeBytes,
                    modifiedAt = child.modifiedAt,
                    hashInfo = child.hashInfo,
                    provider = child.provider,
                    lastSeenAt = now,
                    updatedAt = now,
                )
            }
        }
        if (mapped.isNotEmpty()) documentDao.upsertAll(mapped)
        return mapped
    }

    /** Maps a provider-created object without reconciling/tombstoning siblings. */
    suspend fun mapCreatedChild(
        parent: SystemDocumentEntity,
        displayName: String,
        isDirectory: Boolean,
        mimeType: String,
        sizeBytes: Long?,
    ): SystemDocumentEntity? {
        val parentPath = parent.currentPath ?: return null
        val path = OpenListPathCodec.safeChild(parentPath, displayName) ?: return null
        documentDao.getActiveByPath(parent.instanceId, path)?.let { return it }
        val now = System.currentTimeMillis()
        val mapped = SystemDocumentEntity(
            documentId = UUID.randomUUID().toString(),
            instanceId = parent.instanceId,
            parentDocumentId = parent.documentId,
            currentPath = path,
            lastKnownPath = path,
            displayName = displayName,
            isDirectory = isDirectory,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            modifiedAt = now,
            hashInfo = null,
            provider = null,
            lifecycle = LIFECYCLE_ACTIVE,
            unsupportedCapabilitiesMask = 0L,
            capabilityUpdatedAt = null,
            lastSeenAt = now,
            updatedAt = now,
        )
        documentDao.insert(mapped)
        return mapped
    }

    companion object {
        const val ROOT_PATH = "/"
        const val DIRECTORY_MIME_TYPE = "vnd.android.document/directory"
        const val LIFECYCLE_ACTIVE = "ACTIVE"
    }
}

data class SystemRemoteDocument(
    val displayName: String,
    val isDirectory: Boolean,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
    val hashInfo: String? = null,
    val provider: String? = null,
    val path: String = "",
)
