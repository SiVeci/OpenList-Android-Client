package io.openlist.client.core.model

/** How a file's content should be classified for preview purposes
 * (v0.4_EXECUTION_PLAN.md §11 S1-T3). Pure classification — carries no
 * decision about *how* to open it; see [PreviewOpenMode] for that. */
enum class PreviewKind {
    IMAGE,
    TEXT,
    MARKDOWN,
    VIDEO,
    AUDIO,
    PDF,
    OFFICE,
    UNKNOWN,
}

/** How the app should actually open a resolved [PreviewTarget]. Kept separate
 * from [PreviewKind] because the same kind can resolve to different modes
 * (e.g. a PDF might route to an external app rather than an in-app viewer
 * depending on later sprints' capability checks). */
enum class PreviewOpenMode {
    IN_APP_IMAGE,
    IN_APP_TEXT,
    IN_APP_MARKDOWN,
    IN_APP_VIDEO,
    IN_APP_AUDIO,
    EXTERNAL_APP,
    DOWNLOAD,
    WEB,
    UNSUPPORTED,
}

/** Minimal source-of-content representation. S1 only needs "a URL the
 * PreviewRepository resolved this target to" — S2 (image loading) and S3
 * (text/markdown reading) both consume this the same way, so it stays a
 * single variant rather than a wider hierarchy until a second shape is
 * actually needed. */
sealed class PreviewSource {
    data class RemoteUrl(val url: String, val headersRequired: Boolean = false) : PreviewSource()
}

/** Minimal set of bottom-line actions the UI can always fall back to when a
 * preview can't render in-app (v0.4_EXECUTION_PLAN.md §11 S1-T3). */
enum class PreviewFallback {
    DOWNLOAD,
    EXTERNAL_APP,
    WEB,
    RETRY,
}

/**
 * Fully-resolved description of one previewable file (the output of
 * `PreviewRepository.resolvePreview`). [fallbacks] lists which bottom-line
 * actions the UI should offer if [openMode] can't be honored (e.g. no app
 * installed for [PreviewOpenMode.EXTERNAL_APP]).
 */
data class PreviewTarget(
    val instanceId: String,
    val path: String,
    val name: String,
    val mimeType: String?,
    val kind: PreviewKind,
    val openMode: PreviewOpenMode,
    val size: Long?,
    val modifiedAt: Long?,
    val source: PreviewSource?,
    val fallbacks: List<PreviewFallback>,
)
