package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.entity.InstanceEntity
import io.openlist.client.core.database.entity.SystemDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemDocumentMappingStoreTest {
    private val dao = mockk<SystemDocumentDao>(relaxed = true)
    private val store = SystemDocumentMappingStore(dao)

    @Test
    fun `root UUID is opaque and is retained when an instance is renamed`() = runTest {
        val instance = instance(name = "家庭")
        val rootSlot = slot<List<SystemDocumentEntity>>()
        coEvery { dao.getActiveByPath(INSTANCE_ID, "/") } returns null
        coEvery { dao.upsertAll(capture(rootSlot)) } returns Unit

        val first = store.ensureRoot(instance)
        assertTrue(first.documentId.matches(Regex("[0-9a-f-]{36}")))
        assertEquals("/", first.currentPath)

        coEvery { dao.getActiveByPath(INSTANCE_ID, "/") } returns first
        val renamed = store.ensureRoot(instance.copy(name = "办公室"))

        assertEquals(first.documentId, renamed.documentId)
        assertEquals("办公室", renamed.displayName)
    }

    @Test
    fun `external rename tombstones old mapping and allocates a new UUID`() = runTest {
        val root = root()
        val old = document(id = "old-id", parentId = root.documentId, path = "/old.txt", name = "old.txt")
        val upsertSlot = slot<List<SystemDocumentEntity>>()
        coEvery { dao.getActiveChildren(INSTANCE_ID, root.documentId) } returns listOf(old)
        coEvery { dao.tombstonePathPrefix(INSTANCE_ID, "/old.txt", any()) } returns 1
        coEvery { dao.upsertAll(capture(upsertSlot)) } returns Unit

        store.reconcileChildren(
            root,
            listOf(SystemRemoteDocument("renamed.txt", false, "text/plain", 12L, 10L)),
        )

        coVerify(exactly = 1) { dao.tombstonePathPrefix(INSTANCE_ID, "/old.txt", any()) }
        val mapped = upsertSlot.captured.single()
        assertNotEquals(old.documentId, mapped.documentId)
        assertEquals("/renamed.txt", mapped.currentPath)
    }

    @Test
    fun `same path refresh retains UUID and rejects unsafe child names`() = runTest {
        val root = root()
        val old = document(id = "same-id", parentId = root.documentId, path = "/safe.txt", name = "safe.txt")
        val upsertSlot = slot<List<SystemDocumentEntity>>()
        coEvery { dao.getActiveChildren(INSTANCE_ID, root.documentId) } returns listOf(old)
        coEvery { dao.upsertAll(capture(upsertSlot)) } returns Unit

        val mapped = store.reconcileChildren(
            root,
            listOf(
                SystemRemoteDocument("safe.txt", false, "text/plain", 99L, 20L),
                SystemRemoteDocument("../escape", false, "text/plain", 1L, 20L),
            ),
        )

        assertEquals(listOf("same-id"), mapped.map { it.documentId })
        assertEquals(99L, upsertSlot.captured.single().sizeBytes)
        coVerify(exactly = 0) { dao.tombstonePathPrefix(any(), any(), any()) }
    }

    private fun instance(name: String) = InstanceEntity(
        id = INSTANCE_ID,
        name = name,
        baseUrl = "https://example.test",
        createdAt = 1L,
        updatedAt = 1L,
        lastUsedAt = 1L,
        isCurrent = true,
        note = null,
    )

    private fun root() = document(
        id = "root-id",
        parentId = null,
        path = "/",
        name = "实例",
        directory = true,
    )

    private fun document(id: String, parentId: String?, path: String, name: String, directory: Boolean = false) =
        SystemDocumentEntity(
            documentId = id,
            instanceId = INSTANCE_ID,
            parentDocumentId = parentId,
            currentPath = path,
            lastKnownPath = path,
            displayName = name,
            isDirectory = directory,
            mimeType = if (directory) SystemDocumentMappingStore.DIRECTORY_MIME_TYPE else "text/plain",
            sizeBytes = null,
            modifiedAt = null,
            hashInfo = null,
            provider = null,
            lifecycle = SystemDocumentMappingStore.LIFECYCLE_ACTIVE,
            unsupportedCapabilitiesMask = 0L,
            capabilityUpdatedAt = null,
            lastSeenAt = 1L,
            updatedAt = 1L,
        )

    private companion object {
        const val INSTANCE_ID = "instance-1"
    }
}
