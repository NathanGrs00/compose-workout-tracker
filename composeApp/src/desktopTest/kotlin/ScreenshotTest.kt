import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import db.WorkoutDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import models.WorkoutDay
import org.jetbrains.skia.EncodedImageFormat
import repository.WorkoutRepository
import repository.nowMillis
import ui.screens.HistoryScreen
import ui.screens.ProgressScreen
import ui.screens.WorkoutScreen
import ui.theme.WorkoutTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders key screens with realistic sample data to PNG files in build/screenshots.
 * Checks that screens compose without crashing and lets CI publish what they look like.
 */
class ScreenshotTest {
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun sampleRepository(): WorkoutRepository {
        var time = nowMillis() - 84L * 24 * 3_600_000
        val db = WorkoutDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WorkoutDatabase.Schema.create(it) })
        val repo = WorkoutRepository(db) { time }
        repo.addStarterExercises()
        val byName = runBlocking { repo.exercises.first() }.associateBy { it.name }
        val push = WorkoutDay("push", "Push", listOf("Bench Press", "Overhead Press", "Lateral Raise", "Triceps Pushdown").map { byName.getValue(it).id })
        val pull = WorkoutDay("pull", "Pull", listOf("Pull-up", "Barbell Row", "Face Pull", "Barbell Curl").map { byName.getValue(it).id })
        val legs = WorkoutDay("legs", "Legs", listOf("Squat", "Romanian Deadlift", "Leg Extension", "Calf Raise").map { byName.getValue(it).id })
        listOf(push, pull, legs).forEach(repo::saveWorkoutDay)
        val base = mapOf(
            "Bench Press" to 70.0, "Overhead Press" to 40.0, "Lateral Raise" to 10.0, "Triceps Pushdown" to 25.0,
            "Pull-up" to 0.0, "Barbell Row" to 60.0, "Face Pull" to 20.0, "Barbell Curl" to 30.0,
            "Squat" to 90.0, "Romanian Deadlift" to 80.0, "Leg Extension" to 45.0, "Calf Raise" to 60.0,
        )
        val days = listOf(push, pull, legs)
        for (i in 0 until 30) {
            time = nowMillis() - (84L - i * 2.8).toLong() * 24 * 3_600_000
            val day = days[i % 3]
            val session = repo.startSession(day)
            day.exerciseIds.forEach { id ->
                val name = byName.values.first { it.id == id }.name
                val weight = base.getValue(name) * (1 + i * 0.006) - ((i * 7) % 3) * 0.5
                repeat(3) { set ->
                    time += 150_000
                    repo.logSet(session.id, id, (weight * 4).toInt() / 4.0, 8 + (i % 3) - set)
                }
                repo.setRating(session.id, id, models.Rating.entries[(i + id.length) % 3])
            }
            time += 600_000
            repo.finishSession(session.id)
        }
        // A workout in progress "now".
        time = nowMillis() - 25 * 60_000
        val active = repo.startSession(push)
        time += 4 * 60_000
        repo.logSet(active.id, push.exerciseIds[0], 80.0, 8)
        time += 3 * 60_000
        repo.logSet(active.id, push.exerciseIds[0], 80.0, 7)
        return repo
    }

    private fun render(name: String, widthDp: Int, heightDp: Int, dark: Boolean = false, content: @Composable () -> Unit) {
        val density = 2f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            WorkoutTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box { content() } }
            }
        }
        var image = scene.render()
        repeat(15) {
            Thread.sleep(100)
            image = scene.render((it + 1) * 100_000_000L)
        }
        val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(outDir, "$name.png").writeBytes(bytes)
        scene.close()
        assertTrue(bytes.size > 1000)
    }

    @Test
    fun renderScreens() {
        val repo = sampleRepository()
        render("phone-workout-active", 412, 900) { WorkoutScreen(repo, {}, {}) }
        render("phone-history", 412, 900) { HistoryScreen(repo, {}, {}) }
        render("phone-progress", 412, 1400) { ProgressScreen(repo, {}) }
        render("pc-progress", 1280, 1100) { ProgressScreen(repo, {}) }
        render("pc-progress-dark", 1280, 1100, dark = true) { ProgressScreen(repo, {}) }
        // Start screen (no workout in progress)
        runBlocking { repo.inProgressSession.first()?.let { repo.deleteSession(it.id) } }
        render("phone-workout-start", 412, 900) { WorkoutScreen(repo, {}, {}) }
    }
}
