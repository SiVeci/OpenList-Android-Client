package io.openlist.client.core.model

/**
 * Pure classification of a file name (optionally aided by a mimeType hint)
 * into a [PreviewKind] (v0.4_EXECUTION_PLAN.md §11 S1-T3). Extension-based
 * because OpenList's directory listing gives a reliable file name but a
 * frequently-absent/unreliable mimeType; [mimeType] is accepted for future
 * use (e.g. disambiguating an extension-less file) but is not yet consulted
 * by any branch below — every current rule matches on extension alone.
 */
object PreviewKindResolver {

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic",
    )

    private val TEXT_EXTENSIONS = setOf(
        "txt", "json", "xml", "yaml", "yml", "log", "csv", "ini", "conf",
        "sh", "gradle", "kt", "kts", "java", "py", "js", "css", "html",
        "properties", "toml",
    )

    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "rmvb", "3gp", "ts", "m4v",
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "ape",
    )

    private val PDF_EXTENSIONS = setOf("pdf")

    private val OFFICE_EXTENSIONS = setOf(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    )

    fun resolve(name: String, mimeType: String? = null): PreviewKind {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension.isEmpty()) return PreviewKind.UNKNOWN

        return when (extension) {
            in MARKDOWN_EXTENSIONS -> PreviewKind.MARKDOWN
            in IMAGE_EXTENSIONS -> PreviewKind.IMAGE
            in TEXT_EXTENSIONS -> PreviewKind.TEXT
            in VIDEO_EXTENSIONS -> PreviewKind.VIDEO
            in AUDIO_EXTENSIONS -> PreviewKind.AUDIO
            in PDF_EXTENSIONS -> PreviewKind.PDF
            in OFFICE_EXTENSIONS -> PreviewKind.OFFICE
            else -> PreviewKind.UNKNOWN
        }
    }

    /** Kinds that route to the v0.4 in-app preview screen instead of a plain
     * file-detail/download screen when tapped (v0.4_EXECUTION_PLAN.md §11
     * S2-T4, P-404). PDF/OFFICE/UNKNOWN deliberately return false here — they
     * stay on the pre-v0.4 detail-screen path until a real in-app handler
     * exists for them.
     *
     * Promoted (S6-T3) from a `:feature:files`-only `PREVIEWABLE_KINDS` set
     * so every entry point that needs this same decision (file list, search
     * results, share detail, ...) shares one authoritative definition instead
     * of each feature module re-declaring its own copy — `:feature:search`/
     * `:feature:share` have no dependency on `:feature:files` to reuse its
     * previously-private constant, but all of them already depend on
     * `:core:model`. */
    fun isInAppPreviewable(kind: PreviewKind): Boolean = kind in IN_APP_PREVIEWABLE_KINDS

    private val IN_APP_PREVIEWABLE_KINDS = setOf(
        PreviewKind.IMAGE,
        PreviewKind.VIDEO,
        PreviewKind.AUDIO,
        PreviewKind.TEXT,
        PreviewKind.MARKDOWN,
    )
}
