package io.openlist.client.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.database.entity.SystemDocumentEntity
import io.openlist.client.core.domain.PreviewRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SystemDocumentWriteInvalidatorTest {
    private val fileCacheDao = mockk<FileCacheDao>(relaxed = true)
    private val previewRepository = mockk<PreviewRepository>(relaxed = true)
    private val documentDao = mockk<SystemDocumentDao>()
    private val notifier = mockk<SystemDocumentNotifier>(relaxed = true)

    @Test
    fun `verified save invalidates only its instance path and document URIs`() = runTest {
        coEvery { documentDao.getById("document-1") } returns document()
        val invalidator = SystemDocumentWriteInvalidator(fileCacheDao, previewRepository, documentDao, notifier)

        invalidator.onCommitted("instance-1", "document-1", "/folder/note.txt")

        coVerify(exactly = 1) { fileCacheDao.clearDirectory("instance-1", "/folder") }
        coVerify(exactly = 1) { previewRepository.invalidateByPrefix("instance-1", "/folder/note.txt") }
        coVerify(exactly = 1) { notifier.notifyChildDocumentsChanged("parent-1") }
        coVerify(exactly = 1) { notifier.notifyDocumentChanged("document-1") }
    }

    private fun document() = SystemDocumentEntity(
        documentId = "document-1",
        instanceId = "instance-1",
        parentDocumentId = "parent-1",
        currentPath = "/folder/note.txt",
        lastKnownPath = "/folder/note.txt",
        displayName = "note.txt",
        isDirectory = false,
        mimeType = "text/plain",
        sizeBytes = 1L,
        modifiedAt = null,
        hashInfo = null,
        provider = null,
        lifecycle = SystemDocumentMappingStore.LIFECYCLE_ACTIVE,
        unsupportedCapabilitiesMask = 0L,
        capabilityUpdatedAt = null,
        lastSeenAt = 1L,
        updatedAt = 1L,
    )
}
