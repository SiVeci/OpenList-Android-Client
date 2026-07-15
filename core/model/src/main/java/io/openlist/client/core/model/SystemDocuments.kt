package io.openlist.client.core.model

/**
 * v1.4 system-file model. IDs deliberately carry no path, instance address,
 * account or credential information: SAF URI grants must never reveal a
 * remote identity through their document ID.
 */
data class SystemDocumentRoot(
    val rootId: String,
    val instanceId: String,
    val title: String,
    val documentId: String,
    val isAuthenticated: Boolean,
)

data class SystemDocument(
    val documentId: String,
    val instanceId: String,
    val parentDocumentId: String?,
    val displayName: String,
    val mimeType: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
    val capability: SystemDocumentCapability,
    val lifecycle: SystemDocumentLifecycle = SystemDocumentLifecycle.ACTIVE,
)

/** Operation bits are independent of Android's DocumentsContract flags. */
data class SystemDocumentCapability(
    val canRead: Boolean,
    val canWrite: Boolean = false,
    val canCreate: Boolean = false,
    val canDelete: Boolean = false,
    val canRename: Boolean = false,
    val canMove: Boolean = false,
    val canCopy: Boolean = false,
)

enum class SystemDocumentLifecycle { ACTIVE, TOMBSTONED }

/** The only modes accepted from DocumentsProvider. */
enum class SystemDocumentOpenMode {
    READ,
    WRITE_TRUNCATE,
    READ_WRITE_TRUNCATE,
    READ_WRITE,
    WRITE_APPEND;

    companion object {
        fun parse(mode: String): SystemDocumentOpenMode? = when (mode) {
            "r" -> READ
            "w", "wt" -> WRITE_TRUNCATE
            "rwt" -> READ_WRITE_TRUNCATE
            "rw" -> READ_WRITE
            "wa" -> WRITE_APPEND
            else -> null
        }
    }
}

sealed interface SystemDocumentError {
    data object InvalidDocument : SystemDocumentError
    data object InvalidName : SystemDocumentError
    data object CrossInstanceOperation : SystemDocumentError
    data object UnsupportedOperation : SystemDocumentError
    data object InsufficientSpace : SystemDocumentError
    data object AuthenticationRequired : SystemDocumentError
    data object UnconfirmedRemoteResult : SystemDocumentError
    data object VerificationFailed : SystemDocumentError
}
