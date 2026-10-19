package app.repvane

import AppContainer
import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import db.WorkoutDatabase

/** Holds the single [AppContainer] for the process and starts background sync. */
class WorkoutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val driver = AndroidSqliteDriver(WorkoutDatabase.Schema, this, "workout.db")
        container = AppContainer(driver, RestTimerService.notifier(this))
        RestTimerService.createChannels(this)
        container.syncManager.start()
    }

    companion object {
        /** True while the app's screen is visible (set by [MainActivity]). */
        @Volatile
        var isInForeground: Boolean = false
    }
}
