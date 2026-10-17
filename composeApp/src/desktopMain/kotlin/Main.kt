import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import db.WorkoutDatabase
import java.io.File
import java.util.Properties

/**
 * Main entry point for the desktop app. Data lives in ~/.workout-tracker/workout.db.
 */
fun main() {
    val dir = File(System.getProperty("user.home"), ".workout-tracker").apply { mkdirs() }
    val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dir, "workout.db").absolutePath}", Properties(), WorkoutDatabase.Schema)
    val container = AppContainer(driver)
    container.syncManager.start()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Workout Tracker",
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            App(container)
        }
    }
}
