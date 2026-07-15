package io.openlist.client.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Isolates Activity-result URI writing from the transaction repository. */
@Singleton
class SystemDocumentDraftExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun export(draft: File, destinationUri: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(Uri.parse(destinationUri), "w")?.use { output ->
            draft.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        true
    }.getOrDefault(false)
}
