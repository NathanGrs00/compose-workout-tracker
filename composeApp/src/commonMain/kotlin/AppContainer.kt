import app.cash.sqldelight.db.SqlDriver
import db.WorkoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import repository.SettingsStore
import repository.WorkoutRepository
import repository.sync.SyncManager

/**
 * Creates and holds the app's long-lived objects. One instance per process.
 * The platform supplies the SQLite driver (Android or desktop JDBC).
 */
class AppContainer(driver: SqlDriver) {
    val database = WorkoutDatabase(driver)
    val repository = WorkoutRepository(database)
    val settings = SettingsStore(database)
    val syncManager = SyncManager(
        db = database,
        settings = settings,
        localChanges = repository.localChanges,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
