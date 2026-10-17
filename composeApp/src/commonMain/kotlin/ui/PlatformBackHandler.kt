package ui

import androidx.compose.runtime.Composable

/** Handles the system back button/gesture on Android. No-op on desktop. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
