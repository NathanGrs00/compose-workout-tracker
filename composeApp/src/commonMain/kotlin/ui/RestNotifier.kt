package ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows the rest countdown outside the app and alerts when rest is over
 * (Android: notification + vibration + sound). Desktop: no-op.
 */
interface RestNotifier {
    /** Starts (or restarts) the countdown that ends at [endAt] (epoch ms). */
    fun start(endAt: Long, title: String)

    fun cancel()

    object None : RestNotifier {
        override fun start(endAt: Long, title: String) {}
        override fun cancel() {}
    }
}

/** Bar weight (kg) for the plate calculator and warm-ups. */
val LocalBarWeight = staticCompositionLocalOf { 20.0 }
