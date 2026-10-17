package ui

import androidx.compose.runtime.Composable

/** Returns a function that gives a short vibration (Android). No-op on desktop. */
@Composable
expect fun rememberVibrate(): () -> Unit

/** Keeps the screen on while in composition (Android), e.g. during a workout. No-op on desktop. */
@Composable
expect fun KeepScreenOn()
