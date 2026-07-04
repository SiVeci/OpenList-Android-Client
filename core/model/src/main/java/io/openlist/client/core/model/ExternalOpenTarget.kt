package io.openlist.client.core.model

/** Result of `ExternalOpenRepository.resolveExternalOpen` — everything the UI
 * needs to hand a file off to another app (v0.4_EXECUTION_PLAN.md §11
 * S1-T4). [externalUri] is what gets put on the `Intent.ACTION_VIEW` / chooser
 * Intent; [webUrl] is an optional browser fallback if no app claims the
 * mimeType; [canDownload] tells the UI whether "download instead" is a valid
 * fallback action for this target. */
data class ExternalOpenTarget(
    val externalUri: String,
    val webUrl: String?,
    val canDownload: Boolean,
    val mimeType: String?,
)
