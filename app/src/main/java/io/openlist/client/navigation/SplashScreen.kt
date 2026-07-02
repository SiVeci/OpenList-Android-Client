package io.openlist.client.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import io.openlist.client.core.designsystem.components.LoadingState

/**
 * Routing-only screen: resolves where to go (v0.1_PRD §6.1) and never renders
 * content itself. Login-validity checks (steps 4-6) live in LoginViewModel, not
 * here, so this stays a one-shot instance lookup.
 */
@Composable
fun SplashScreen(
    onNavigate: (route: String, popSplash: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        onNavigate(viewModel.resolveStartRoute(), true)
    }
    LoadingState()
}
