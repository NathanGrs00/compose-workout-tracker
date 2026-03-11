import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Main entry point for the Workout Tracker application.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Workout Tracker"
    ) {
        App()
    }
}