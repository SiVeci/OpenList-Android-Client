package io.openlist.client.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder host for the `admin/{instanceId}?tab={tab}` route (v0.5_EXECUTION_PLAN.md
 * §11 S1-T2). This is only a minimal stand-in so the route/module wiring compiles and
 * is navigable end-to-end in S1; the real gated overview + 7-Tab admin console
 * (`AdminScaffold`/`AdminTabRow`/gate screen) is built starting S2 (§11 S2-T3/T4).
 *
 * [instanceId] and [tab] are accepted so the eventual real implementation's call site
 * in `OpenListNavHost` doesn't need to change shape later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHostScreen(
    instanceId: String,
    tab: String?,
    onBack: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("管理台（占位，v0.5 S2+ 实现）")
            Text("instanceId=$instanceId" + (tab?.let { " tab=$it" } ?: ""))
        }
    }
}
