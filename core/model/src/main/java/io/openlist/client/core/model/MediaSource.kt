package io.openlist.client.core.model

/**
 * Fully-resolved, in-memory description of one playable media file (the
 * output of `MediaRepository.resolveMedia`/`refreshMediaSource`, consumed by
 * S4/S5's ExoPlayer integration). Intentionally **not** a Room `@Entity` —
 * media playback URLs are short-lived (signed, time-limited) and never
 * persisted; only [io.openlist.client.core.database.entity.PreviewCacheEntity]
 * (a different, metadata-only table) is persisted, and only for the
 * text/image/markdown preview flows, not video/audio.
 */
data class MediaSource(
    val instanceId: String,
    val path: String,
    val title: String,
    val mimeType: String?,
    val url: String,
    val headersRequired: Boolean,
    val expiresAt: Long?,
    val subtitles: List<SubtitleCandidate>,
)

/** Where a [SubtitleCandidate] came from — whether `SubtitleRepository.findCandidates`
 * discovered it automatically (matching filename heuristics, e.g. a sibling
 * `.srt` file) or the user picked it explicitly from a file browser. */
enum class SubtitleSourceType {
    AUTO_DISCOVERED,
    USER_SELECTED,
}

/** One subtitle track offered to the player before it's been resolved to an
 * actual loadable source (that's [SubtitleSource], returned by
 * `SubtitleRepository.resolveSubtitle`). */
data class SubtitleCandidate(
    val path: String,
    val name: String,
    val language: String?,
    val format: String?,
    val source: SubtitleSourceType,
)

/** Resolved, loadable subtitle track — the output of
 * `SubtitleRepository.resolveSubtitle`. [url] is wherever ExoPlayer should
 * actually read the subtitle content from (a remote URL or a local cache
 * file path written during resolution); [format] mirrors
 * [SubtitleCandidate.format] (e.g. "srt"/"vtt"/"ass") so the player can pick
 * the right MimeType without re-sniffing the file extension. */
data class SubtitleSource(
    val path: String,
    val url: String,
    val format: String?,
)
