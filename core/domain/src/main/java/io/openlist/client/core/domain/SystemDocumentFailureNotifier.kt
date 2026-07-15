package io.openlist.client.core.domain

/**
 * Posts a privacy-safe notification for a system-document save that needs user action.
 * Implementations must not put paths, tokens, signed URLs, draft content, or transaction
 * identifiers into notification text or intents.
 */
interface SystemDocumentFailureNotifier {
    fun notifySaveNeedsAttention(instanceId: String)
}
