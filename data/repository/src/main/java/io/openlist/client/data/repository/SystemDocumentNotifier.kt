package io.openlist.client.data.repository

import android.content.Context
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Sends narrow SAF invalidations after a background directory refresh. */
@Singleton
class SystemDocumentNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyChildDocumentsChanged(parentDocumentId: String) {
        context.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId),
            null,
        )
    }

    fun notifyRootsChanged() {
        context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
    }

    fun notifyDocumentChanged(documentId: String) {
        context.contentResolver.notifyChange(
            DocumentsContract.buildDocumentUri(authority, documentId),
            null,
        )
    }

    private val authority: String
        get() = "${context.packageName}.documents"
}
