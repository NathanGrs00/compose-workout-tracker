import analytics.Insights
import analytics.NextWorkout
import analytics.Plates
import analytics.RestState
import analytics.RestTimer
import analytics.Supersets
import analytics.Warmup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import models.Exercise
import models.LoggedSet
import models.Rating
import models.WorkoutDay
import models.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingTest {
    private val tz = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(tz).toEpochMilliseconds() + 18 * 3_600_000L
    private fun session(id: String, at: Long, dayId: String? = null) = WorkoutSession(id, dayId, "W", emptyList(), at, at + 3_600_000)
    private fun set(id: String, session: String, exercise: String, weight: Double, reps: Int, at: Long = 0) =
        LoggedSet(id, session, exercise, weight, reps, at)

    @Test
    fun platesPerSide() {
        assertEquals(listOf(25.0, 5.0), Plates.perSide(80.0).perSide)
        assertEquals(listOf(25.0, 5.0, 1.25), Plates.perSide(82.5).perSide)
        assertEquals(listOf(20.0, 10.0), Plates.perSide(80.0, plates = listOf(20.0, 10.0, 5.0)).perSide)
        assertEquals(emptyList(), Plates.perSide(20.0).perSide)
        assertTrue(Plates.perSide(15.0).belowBar)
        assertEquals(1.0, Plates.perSide(21.0).remainder) // 0.5 per side can't be loaded
        assertEquals(listOf(25.0, 25.0, 25.0, 25.0, 20.0, 1.25), Plates.perSide(15.0 + 2 * 121.25, bar = 15.0).perSide)
    }

    @Test
    fun warmupRamp() {
        val sets = Warmup.sets(100.0)
        assertEquals(listOf(20.0 to 10, 40.0 to 8, 60.0 to 5, 80.0 to 3), sets.map { it.weight to it.reps })
        // Light working weight: steps at or below the bar are skipped, no duplicates.
        assertEquals(listOf(20.0 to 10, 25.0 to 5, 32.5 to 3), Warmup.sets(40.0).map { it.weight to it.reps })
        assertTrue(Warmup.sets(20.0).isEmpty())
    }

    @Test
    fun supersetOrderKeepsPartnersTogether() {
        val pairs = listOf("c" to "a")
        assertEquals(listOf("c", "a", "b", "d"), Supersets.order(listOf("c", "b", "a", "d"), pairs))
        assertEquals(listOf("b", "a", "c", "d"), Supersets.order(listOf("b", "a", "c", "d"), listOf("a" to "c")))
        assertEquals("a", Supersets.partnerOf("c", pairs))
        assertNull(Supersets.partnerOf("b", pairs))
        assertEquals(listOf("a" to "c"), Supersets.reorder(pairs, listOf("a", "c")))
    }

    @Test
    fun noRestBetweenSupersetExercises() {
        val ex = mapOf("a" to Exercise("a", "A", "x", restSeconds = 60), "b" to Exercise("b", "B", "x", restSeconds = 90))
        val pairs = listOf("a" to "b")
        // After A's set: go to B, no rest.
        val afterA = RestTimer.state(listOf(set("1", "s", "a", 10.0, 8, 0)), listOf("a", "b"), ex, now = 1_000, supersets = pairs)
        assertEquals(RestState.SupersetNext("b"), afterA)
        // After B's set: rest (B's rest time).
        val afterB = RestTimer.state(
            listOf(set("1", "s", "a", 10.0, 8, 0), set("2", "s", "b", 10.0, 8, 1_000)), listOf("a", "b"), ex, now = 2_000, supersets = pairs,
        )
        assertIs<RestState.Resting>(afterB)
        assertEquals(89_000, afterB.remainingMillis)
        assertEquals(91_000, afterB.endAt)
    }

    @Test
    fun nextWorkoutIsLeastRecentlyDone() {
        val push = WorkoutDay("push", "Push", emptyList())
        val pull = WorkoutDay("pull", "Pull", emptyList())
        val legs = WorkoutDay("legs", "Legs", emptyList())
        val sessions = listOf(
            session("1", day(2026, 10, 10), "push"),
            session("2", day(2026, 10, 12), "pull"),
            session("3", day(2026, 10, 14), "legs"),
        )
        assertEquals("push", NextWorkout.suggest(listOf(push, pull, legs), sessions)?.first?.id)
        // A routine never done comes first.
        val arms = WorkoutDay("arms", "Arms", emptyList())
        assertEquals("arms" to null, NextWorkout.suggest(listOf(push, pull, legs, arms), sessions)?.let { it.first.id to it.second })
        assertNull(NextWorkout.suggest(emptyList(), sessions))
    }

    @Test
    fun weeklyReportComparesWeeksAndFindsStalls() {
        val sessions = (0 until 6).map { session("s$it", day(2026, 9, 7) + it * 7 * 86_400_000L) } // weekly
        // Bench improves twice, then stalls for 4 sessions.
        val weights = listOf(80.0, 82.5, 85.0, 85.0, 82.5, 85.0)
        val sets = weights.mapIndexed { i, w -> set("b$i", "s$i", "bench", w, 5) } +
            listOf(set("x", "s5", "squat", 100.0, 5))
        val report = Insights.weeklyReport(sets, sessions, now = day(2026, 10, 14), timeZone = tz)
        assertEquals(1, report.thisWeek.workouts)
        assertEquals(1, report.lastWeek.workouts)
        val stalled = report.stalled.single()
        assertEquals("bench", stalled.exerciseId)
        assertEquals(3, stalled.sessionsWithoutProgress)
    }

    @Test
    fun feelTrendAndOftenBad() {
        val sessions = mapOf("s1" to session("s1", day(2026, 10, 6)), "s2" to session("s2", day(2026, 10, 13)))
        val feel = Insights.weeklyFeel(
            listOf("s1" to Rating.GOOD, "s1" to Rating.BAD, "s2" to Rating.OKAY), sessions, weeks = 3, now = day(2026, 10, 14), timeZone = tz,
        )
        assertEquals(listOf(null, 0.5, 0.5), feel.map { it.second })
        val bad = Insights.oftenBad(
            listOf(Triple("s1", "bench", Rating.BAD), Triple("s2", "bench", Rating.BAD), Triple("s2", "squat", Rating.BAD)),
            sessions, since = 0,
        )
        assertEquals(listOf("bench" to 2), bad)
    }
}
