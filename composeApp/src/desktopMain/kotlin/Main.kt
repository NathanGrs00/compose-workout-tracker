import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import db.WorkoutDatabase
import ui.brand.RepvaneIconPainter
import java.io.File
import java.util.Properties

/**
 * Main entry point for the desktop app. Data lives in ~/.repvane/workout.db.
 */
fun main() {
    val home = System.getProperty("user.home")
    val dir = File(home, ".repvane")
    // Versions before the Repvane rename kept their data in ~/.workout-tracker: move it over once.
    val legacy = File(home, ".workout-tracker")
    if (!dir.exists() && legacy.isDirectory) legacy.renameTo(dir)
    dir.mkdirs()

    val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dir, "workout.db").absolutePath}", Properties(), WorkoutDatabase.Schema)
    val container = AppContainer(driver)
    container.syncManager.start()
    val icon = RepvaneIconPainter()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Repvane",
            icon = icon,
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            App(container)
        }
    }
}
