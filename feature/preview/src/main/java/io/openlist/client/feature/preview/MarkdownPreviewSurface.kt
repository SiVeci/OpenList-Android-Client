package io.openlist.client.feature.preview

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.openlist.client.core.designsystem.Spacing
import io.openlist.client.core.model.MarkdownPreviewContent

/**
 * Real in-app Markdown preview (v0.4_EXECUTION_PLAN.md §11 S3-T3), rendered
 * via Markwon **core only** (DEC-2) bridged into Compose through
 * `AndroidView(TextView)`.
 *
 * Safety configuration verified directly against Markwon 4.6.2's source
 * (not assumed) before writing this — see the three notes below, which are
 * this Sprint's mandatory DoD record for §15.2.2:
 *
 * 1. **HTML**: no `markwon-html` plugin is added, and `CorePlugin` (the only
 *    plugin in use) registers `HtmlBlock`/`HtmlInline` with the commonmark
 *    parser's `enabledBlockTypes()` but never registers a visitor for either
 *    type in `configureVisitor()`. Un-visited nodes simply produce no output
 *    span, so raw HTML in a `.md` file is silently dropped from the
 *    rendered view — never executed, never shown as literal text either.
 * 2. **External links**: `CorePlugin.afterSetText` applies
 *    `LinkMovementMethod.getInstance()` to the TextView whenever the caller
 *    hasn't set an explicit one (redundant with the explicit call below, but
 *    harmless), making `LinkSpan`s clickable. `LinkSpan.onClick` delegates to
 *    a `LinkResolver`, and `MarkwonConfiguration.Builder.build()` defaults
 *    that resolver to `new LinkResolverDef()` whenever `Markwon.builder(...)`
 *    (or `Markwon.create(...)`) isn't given a custom one — which is the case
 *    here. `LinkResolverDef.resolve()` builds an `ACTION_VIEW` Intent and
 *    already wraps `context.startActivity(intent)` in
 *    `try/catch (ActivityNotFoundException)`, logging and swallowing it
 *    instead of crashing. So P-410 (external links open via the system
 *    browser/handler) and the "must not crash on an unhandled scheme"
 *    requirement are both satisfied by Markwon's own default — no custom
 *    `LinkResolver` needed here.
 * 3. **Images**: not implemented this Sprint. Rendering embedded images
 *    would need the `markwon-image` artifact (not currently a project
 *    dependency) plus a hand-written Coil-backed `AsyncDrawableLoader`/
 *    `SchemeHandler` — a real implementation surface that can't be verified
 *    without compiling, on top of the basePath-relative-URL resolution
 *    already being sign-less or best-effort (V-409). Skipped as a deliberate
 *    scope cut; markdown documents with embedded images still render their
 *    text content correctly, just without inline images. Left for a later
 *    version.
 *
 * Rendering itself (`markwon.setMarkdown`) is wrapped in `runCatching`
 * (§14.3): a parser/render exception falls back to showing the raw Markdown
 * source as plain monospace text rather than crashing or leaving a blank
 * screen.
 */
@Composable
fun MarkdownPreviewSurface(
    content: MarkdownPreviewContent,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val density = LocalDensity.current
    val paddingPx = remember(density) { with(density) { Spacing.md.roundToPx() } }

    Column(modifier = modifier.fillMaxSize()) {
        if (content.isTruncated) {
            TruncatedNotice(
                message = "仅显示前 512KB 内容，下载查看完整文件",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val renderFailed = remember(content.rawMarkdown) {
            runCatching { markwon.toMarkdown(content.rawMarkdown) }.isFailure
        }

        if (renderFailed) {
            SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = content.rawMarkdown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                        textSize = 15f
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                    }
                },
                update = { textView ->
                    runCatching { markwon.setMarkdown(textView, content.rawMarkdown) }
                        .onFailure { textView.text = content.rawMarkdown }
                },
            )
        }
    }
}
