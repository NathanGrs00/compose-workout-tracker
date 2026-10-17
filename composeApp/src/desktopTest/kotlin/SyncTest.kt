import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import db.WorkoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import repository.SettingsStore
import repository.WorkoutRepository
import repository.sync.RemoteStore
import repository.sync.SyncManager
import repository.sync.SyncStatus
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory stand-in for Supabase that assigns server_updated_at like the database trigger does. */
class FakeRemote : RemoteStore {
    val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
    var online = true
    private var serverClock = 1_000_000L

    override suspend fun upsert(table: String, rows: List<JsonObject>) {
        if (!online) throw IOException("offline", java.net.UnknownHostException("no network"))
        val t = tables.getOrPut(table) { mutableMapOf() }
        rows.forEach { row ->
            val id = row.getValue("id").jsonPrimitive.content
            t[id] = JsonObject(row + ("server_updated_at" to JsonPrimitive(serverClock++)))
        }
    }

    override suspend fun fetchChangedSince(table: String, since: Long, offset: Int, limit: Int): List<JsonObject> {
        if (!online) throw java.net.UnknownHostException("no network")
        return tables[table].orEmpty().values
            .filter { it.getValue("server_updated_at").jsonPrimitive.long > since }
            .sortedBy { it.getValue("server_updated_at").jsonPrimitive.long }
            .drop(offset).take(limit)
    }
}

/** One app install: its own local database, repository and sync manager. */
class Device(remote: RemoteStore, startClock: Long) {
    private var time = startClock
    private val clock = { time++ }
    val db = WorkoutDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WorkoutDatabase.Schema.create(it) })
    val repo = WorkoutRepository(db, clock)
    val settings = SettingsStore(db).apply { saveSupabase("https://example.supabase.co", "key") }
    val sync = SyncManager(db, settings, emptyFlow(), CoroutineScope(Dispatchers.Unconfined), { _, _ -> remote }, clock)
}

class SyncTest {
    @Test
    fun workoutLoggedOnPhoneShowsUpOnPc() = runBlocking {
        val remote = FakeRemote()
        val phone = Device(remote, startClock = 1_000)
        val pc = Device(remote, startClock = 50_000)

        val bench = phone.repo.addExercise("Bench Press", "Chest")
        val session = phone.repo.startSession(null)
        phone.repo.logSet(session.id, bench.id, 80.0, 8)
        phone.repo.logSet(session.id, bench.id, 80.0, 7)
        assertTrue(phone.repo.finishSession(session.id))

        assertTrue(phone.sync.syncNow())
        assertEquals(0L, phone.repo.pendingChanges.first())
        assertTrue(pc.sync.syncNow())

        assertEquals(listOf("Bench Press"), pc.repo.exercises.first().map { it.name })
        val pcSessions = pc.repo.sessions.first()
        assertEquals(1, pcSessions.size)
        assertFalse(pcSessions.single().isInProgress)
        assertEquals(listOf(8, 7), pc.repo.setsForSession(session.id).first().map { it.reps })
        assertEquals(listOf(bench.id), pcSessions.single().exerciseIds)
        assertEquals(0L, pc.repo.pendingChanges.first())
    }

    @Test
    fun offlineLoggingIsKeptAndUploadedLater() = runBlocking {
        val remote = FakeRemote().apply { online = false }
        val phone = Device(remote, startClock = 1_000)
        val squat = phone.repo.addExercise("Squat", "Quads")
        val session = phone.repo.startSession(null)
        phone.repo.logSet(session.id, squat.id, 100.0, 5)

        assertFalse(phone.sync.syncNow())
        assertEquals(SyncStatus.Offline, phone.sync.state.value.status)
        assertTrue(phone.repo.pendingChanges.first() > 0)

        remote.online = true
        assertTrue(phone.sync.syncNow())
        assertEquals(SyncStatus.Idle, phone.sync.state.value.status)
        assertEquals(0L, phone.repo.pendingChanges.first())
        assertEquals(1, remote.tables["logged_sets"]?.size)
    }

    @Test
    fun deletesPropagate() = runBlocking {
        val remote = FakeRemote()
        val phone = Device(remote, startClock = 1_000)
        val pc = Device(remote, startClock = 50_000)
        val ex = phone.repo.addExercise("Curl", "Biceps")
        val session = phone.repo.startSession(null)
        phone.repo.logSet(session.id, ex.id, 20.0, 10)
        phone.repo.finishSession(session.id)
        phone.sync.syncNow()
        pc.sync.syncNow()
        assertEquals(1, pc.repo.sessions.first().size)

        pc.repo.deleteSession(session.id)
        pc.sync.syncNow()
        phone.sync.syncNow()
        assertTrue(phone.repo.sessions.first().isEmpty())
        assertTrue(phone.repo.allSets.first().isEmpty())
    }

    @Test
    fun newerLocalEditIsNotOverwrittenByPull() = runBlocking {
        val remote = FakeRemote()
        val phone = Device(remote, startClock = 1_000)
        val pc = Device(remote, startClock = 50_000)
        val ex = phone.repo.addExercise("Row", "Upper Back")
        val session = phone.repo.startSession(null)
        val set = phone.repo.logSet(session.id, ex.id, 60.0, 10)
        phone.sync.syncNow()
        pc.sync.syncNow()

        // PC corrects the set (newer edit, clock is ahead) while the phone is offline.
        pc.repo.updateSet(set.copy(weight = 65.0))
        pc.sync.syncNow()
        // Phone pulls: it had no local edits, so it takes the PC's version.
        phone.sync.syncNow()
        assertEquals(65.0, phone.repo.setsForSession(session.id).first().single().weight)

        // Phone now edits again (dirty, not yet uploaded) and an older remote copy must not win.
        phone.repo.updateSet(set.copy(weight = 70.0))
        remote.online = false
        phone.sync.syncNow()
        remote.online = true
        phone.sync.syncNow()
        pc.sync.syncNow()
        assertEquals(70.0, pc.repo.setsForSession(session.id).first().single().weight)
    }

    @Test
    fun finishingEmptyWorkoutDiscardsIt() = runBlocking {
        val phone = Device(FakeRemote(), startClock = 1_000)
        val session = phone.repo.startSession(null)
        assertFalse(phone.repo.finishSession(session.id))
        assertNull(phone.repo.inProgressSession.first())
        assertTrue(phone.repo.sessions.first().isEmpty())
    }

    @Test
    fun routineStartsWorkoutWithItsExercises() = runBlocking {
        val phone = Device(FakeRemote(), startClock = 1_000)
        phone.repo.addStarterExercises()
        val exercises = phone.repo.exercises.first()
        assertTrue(exercises.size > 10)
        val ids = exercises.take(3).map { it.id }
        phone.repo.saveWorkoutDay(models.WorkoutDay("r1", "Push", ids))
        val routine = phone.repo.workoutDays.first().single()
        val session = phone.repo.startSession(routine)
        assertEquals("Push", session.name)
        assertEquals(ids, phone.repo.inProgressSession.first()?.exerciseIds)
        // Adding starters twice doesn't duplicate.
        phone.repo.addStarterExercises()
        assertEquals(exercises.size, phone.repo.exercises.first().size)
    }

    @Test
    fun notConfiguredStaysLocal() = runBlocking {
        val db = WorkoutDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WorkoutDatabase.Schema.create(it) })
        val sync = SyncManager(db, SettingsStore(db), emptyFlow(), CoroutineScope(Dispatchers.Unconfined), { _, _ -> error("should not connect") })
        if (SettingsStore(db).isSyncConfigured) return@runBlocking // build has default credentials baked in
        assertFalse(sync.syncNow())
        assertEquals(SyncStatus.NotConfigured, sync.state.value.status)
    }
}

class RatingAndMigrationTest {
    @Test
    fun ratingsAndRepRangesSyncBetweenDevices() = runBlocking {
        val remote = FakeRemote()
        val phone = Device(remote, startClock = 1_000)
        val pc = Device(remote, startClock = 50_000)
        val bench = phone.repo.addExercise("Bench Press", "Chest", repMin = 5, repMax = 8, weightIncrement = 1.25)
        val session = phone.repo.startSession(null)
        phone.repo.logSet(session.id, bench.id, 80.0, 8)
        phone.repo.setRating(session.id, bench.id, models.Rating.GOOD)
        phone.repo.finishSession(session.id)
        phone.sync.syncNow()
        pc.sync.syncNow()

        val pcBench = pc.repo.exercises.first().single()
        assertEquals(5 to 8, pcBench.repMin to pcBench.repMax)
        assertEquals(1.25, pcBench.weightIncrement)
        assertEquals(models.Rating.GOOD, pc.repo.ratings.first().single().rating)

        // Change the rating on the PC; the phone follows. Clearing removes it everywhere.
        pc.repo.setRating(session.id, bench.id, models.Rating.BAD)
        pc.sync.syncNow(); phone.sync.syncNow()
        assertEquals(models.Rating.BAD, phone.repo.ratings.first().single().rating)
        phone.repo.setRating(session.id, bench.id, null)
        phone.sync.syncNow(); pc.sync.syncNow()
        assertTrue(pc.repo.ratings.first().isEmpty())
    }

    @Test
    fun deletingWorkoutRemovesItsRatings() = runBlocking {
        val phone = Device(FakeRemote(), startClock = 1_000)
        val ex = phone.repo.addExercise("Squat", "Quads")
        val session = phone.repo.startSession(null)
        phone.repo.logSet(session.id, ex.id, 100.0, 5)
        phone.repo.setRating(session.id, ex.id, models.Rating.OKAY)
        phone.repo.deleteSession(session.id)
        assertTrue(phone.repo.ratings.first().isEmpty())
    }

    @Test
    fun upgradesVersion1Database() {
        val file = java.io.File.createTempFile("workout-v1", ".db").apply { delete(); deleteOnExit() }
        val url = "jdbc:sqlite:${file.absolutePath}"
        // A database as created by the first release (schema version 1).
        java.sql.DriverManager.getConnection(url).use { c ->
            c.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, muscle_group TEXT NOT NULL, " +
                        "updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)"
                )
                st.executeUpdate("CREATE TABLE workout_day (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, exercise_ids TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)")
                st.executeUpdate("CREATE TABLE workout_session (id TEXT NOT NULL PRIMARY KEY, workout_day_id TEXT, name TEXT NOT NULL, exercise_ids TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)")
                st.executeUpdate("CREATE TABLE logged_set (id TEXT NOT NULL PRIMARY KEY, session_id TEXT NOT NULL, exercise_id TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, performed_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1)")
                st.executeUpdate("CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                st.executeUpdate("INSERT INTO exercise VALUES ('e1', 'Bench Press', 'Chest', 5, 0, 0)")
                st.executeUpdate("PRAGMA user_version = 1")
            }
        }

        val driver = JdbcSqliteDriver(url, java.util.Properties(), WorkoutDatabase.Schema)
        val repo = WorkoutRepository(WorkoutDatabase(driver))
        runBlocking {
            val bench = repo.exercises.first().single()
            assertEquals("Bench Press", bench.name)
            assertEquals(8 to 12, bench.repMin to bench.repMax)
            assertEquals(2.5, bench.weightIncrement)
            val session = repo.startSession(null)
            repo.setRating(session.id, bench.id, models.Rating.GOOD)
            assertEquals(1, repo.ratings.first().size)
        }
        driver.close()
    }
}
