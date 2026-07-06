package io.openlist.client.core.model

/**
 * Classification result for one Markdown image reference (v1.0_PRD §7.4/§10.3).
 * [Internal] is a same-instance signed URL resolved via the normal (no
 * password, no extra headers) file-preview mechanism (V-608); [External] is
 * an absolute URL left completely untouched — never given credentials.
 */
sealed class MarkdownImageSource {
    data class Internal(val url: String) : MarkdownImageSource()
    data class External(val url: String) : MarkdownImageSource()
    data object Unresolvable : MarkdownImageSource()
}
