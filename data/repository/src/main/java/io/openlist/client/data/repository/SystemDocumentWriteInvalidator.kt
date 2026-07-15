package io.openlist.client.data.repository

import io.openlist.client.core.database.dao.FileCacheDao
import io.openlist.client.core.database.dao.SystemDocumentDao
import io.openlist.client.core.domain.PreviewRepository
import io.openlist.client.core.network.OpenListPathCodec
import javax.inject.Inject
import javax.inject.Singleton

/** Best-effort local invalidation after a remote system-document save is verified. */
@Singleton
class SystemDocumentWriteInvalidator @Inject constructor(
    private val fileCacheDao: FileCacheDao,
    private val previewRepository: PreviewRepository,
    private val documentDao: SystemDocumentDao,
    private val notifier: SystemDocumentNotifier,
) {
    suspend fun onCommitted(instanceId: String, documentId: String?, targetPath: String) {
        fileCacheDao.clearDirectory(instanceId, OpenListPathCodec.parent(targetPath))
        previewRepository.invalidateByPrefix(instanceId, targetPath)
        documentId?.let { id ->
            documentDao.getById(id)?.parentDocumentId?.let(notifier::notifyChildDocumentsChanged)
            notifier.notifyDocumentChanged(id)
        }
    }
}
