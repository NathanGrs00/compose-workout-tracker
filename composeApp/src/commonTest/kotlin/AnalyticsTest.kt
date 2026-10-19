import analytics.Analytics
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import models.Exercise
import models.LoggedSet
import models.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsTest {
    private val tz = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(tz).toEpochMilliseconds() + 18 * 3_600_000L
    private fun session(id: String, at: Long) = WorkoutSession(id, null, "W", emptyList(), at, at + 3_600_000)
    private fun set(id: String, session: String, exercise: String, weight: Double, reps: Int, at: Long = 0) =
        LoggedSet(id, session, exercise, weight, reps, at)

    @Test
    fun epleyEstimate() {
        assertEquals(100.0, Analytics.estimatedOneRepMax(100.0, 1))
        assertEquals(80.0 * (1 + 8 / 30.0), Analytics.estimatedOneRepMax(80.0, 8), 1e-9)
        assertEquals(0.0, Analytics.estimatedOneRepMax(0.0, 12))
    }

    @Test
    fun exerciseHistoryGroupsBySessionAndSortsByDate() {
        val sessions = mapOf("s1" to session("s1", day(2026, 1, 5)), "s2" to session("s2", day(2026, 1, 1)))
        val sets = listOf(
            set("a", "s1", "bench", 60.0, 8),
            set("b", "s1", "bench", 62.5, 6),
            set("c", "s2", "bench", 57.5, 8),
            set("d", "s2", "squat", 100.0, 5),
            set("e", "deleted-session", "bench", 200.0, 1),
        )
        val history = Analytics.exerciseHistory("bench", sets, sessions)
        assertEquals(listOf("s2", "s1"), history.map { it.sessionId })
        assertEquals(62.5, history[1].topWeight)
        assertEquals(60.0 * 8 + 62.5 * 6, history[1].volume)
        assertEquals(8, history[1].maxReps)
        assertEquals(62.5, history[1].bestSet.weight)
    }

    @Test
    fun personalRecords() {
        val sessions = mapOf("s1" to session("s1", day(2026, 1, 1)), "s2" to session("s2", day(2026, 1, 8)))
        val sets = listOf(
            set("a", "s1", "bench", 100.0, 1),
            set("b", "s2", "bench", 90.0, 6),
            set("c", "s2", "bench", 50.0, 20),
        )
        val records = Analytics.personalRecords(Analytics.exerciseHistory("bench", sets, sessions))
        assertEquals("a", records.heaviest?.id)
        assertEquals("b", records.bestE1rmSet?.id) // 90 x 6 = 108 > 100
        assertEquals("c", records.mostReps?.id)
        assertEquals("s2", records.bestSessionVolume?.sessionId)
    }

    @Test
    fun trendPerWeekIsSlopeOfLinearFit() {
        val week = 7 * 24 * 3_600_000L
        val trend = Analytics.trendPerWeek(listOf(0L to 100.0, week to 102.0, 2 * week to 104.0, 3 * week to 106.0))
        assertNotNull(trend)
        assertEquals(2.0, trend, 1e-9)
        assertNull(Analytics.trendPerWeek(listOf(0L to 1.0, 1L to 2.0)))
    }

    @Test
    fun weekStartIsMonday() {
        val sunday = day(2026, 10, 18)
        assertEquals(LocalDate(2026, 10, 12), Analytics.weekStart(sunday, tz))
        assertEquals(DayOfWeek.MONDAY, Analytics.weekStart(day(2026, 10, 12), tz).dayOfWeek)
    }

    @Test
    fun weeklySummaryIncludesEmptyWeeks() {
        val sessions = listOf(session("s1", day(2026, 10, 13)), session("s2", day(2026, 9, 29)))
        val sets = listOf(set("a", "s1", "x", 50.0, 10), set("b", "s1", "x", 50.0, 10), set("c", "s2", "x", 10.0, 10))
        val weeks = Analytics.weeklySummary(sets, sessions, weeks = 4, now = day(2026, 10, 17), timeZone = tz)
        assertEquals(4, weeks.size)
        assertEquals(LocalDate(2026, 10, 12), weeks.last().weekStart)
        assertEquals(1000.0, weeks.last().volume)
        assertEquals(1, weeks.last().workouts)
        assertEquals(0.0, weeks[2].volume)
        assertEquals(100.0, weeks[1].volume)
    }

    @Test
    fun weekStreakCountsConsecutiveWeeks() {
        val now = day(2026, 10, 17)
        val sessions = listOf(session("a", day(2026, 10, 6)), session("b", day(2026, 9, 30)), session("c", day(2026, 9, 10)))
        // Nothing this week yet, but last week and the one before count.
        assertEquals(2, Analytics.weekStreak(sessions, now, tz))
        assertEquals(0, Analytics.weekStreak(emptyList(), now, tz))
    }

    @Test
    fun effectiveLoadUsesBodyWeightAtTheTime() {
        val sessions = mapOf("s1" to session("s1", day(2026, 10, 1)), "s2" to session("s2", day(2026, 10, 10)))
        val weights = listOf(
            models.BodyWeightEntry("w1", day(2026, 9, 28), 82.0),
            models.BodyWeightEntry("w2", day(2026, 10, 8), 80.0),
        )
        val sets = listOf(set("a", "s1", "ap", -4.5, 8), set("b", "s2", "ap", -2.0, 8), set("c", "s2", "bench", 60.0, 5))
        val effective = Analytics.withEffectiveLoad(sets, sessions, weights)
        assertEquals(listOf(77.5, 78.0, 60.0), effective.map { it.weight })
        // Without body weight nothing changes.
        assertEquals(sets, Analytics.withEffectiveLoad(sets, sessions, emptyList()))
        // Before the first entry, the earliest entry is used.
        assertEquals(82.0, Analytics.bodyWeightAt(0, weights))
    }

    @Test
    fun setsPerMuscleGroup() {
        val exercises = listOf(Exercise("b", "Bench", "Chest"), Exercise("s", "Squat", "Quads"))
        val sets = listOf(set("1", "s", "b", 1.0, 1), set("2", "s", "b", 1.0, 1), set("3", "s", "s", 1.0, 1), set("4", "s", "?", 1.0, 1))
        assertEquals(listOf("Chest" to 2, "Quads" to 1, "Other" to 1), Analytics.setsPerMuscleGroup(sets, exercises))
    }

    @Test
    fun recordSetsAreOnlyImprovementsAfterFirstSession() {
        val sessions = mapOf(
            "s1" to session("s1", day(2026, 1, 1)),
            "s2" to session("s2", day(2026, 1, 8)),
            "s3" to session("s3", day(2026, 1, 15)),
        )
        val sets = listOf(
            set("a", "s1", "bench", 100.0, 5),
            set("b", "s2", "bench", 95.0, 5),
            set("c", "s3", "bench", 102.5, 5),
        )
        assertEquals(setOf("c"), Analytics.recordSetIds(sets, sessions))
    }

    @Test
    fun previousPerformanceUsesMostRecentEarlierSession() {
        val sessions = mapOf(
            "old" to session("old", day(2026, 1, 1)),
            "prev" to session("prev", day(2026, 1, 8)),
            "now" to session("now", day(2026, 1, 15)),
        )
        val sets = listOf(
            set("a", "old", "bench", 50.0, 5, 1),
            set("b", "prev", "bench", 60.0, 5, 2),
            set("c", "prev", "bench", 60.0, 4, 3),
            set("d", "now", "bench", 70.0, 5, 4),
        )
        val previous = Analytics.previousPerformance("bench", "now", sets, sessions)
        assertEquals(listOf("b", "c"), previous.map { it.id })
        assertTrue(Analytics.previousPerformance("squat", "now", sets, sessions).isEmpty())
    }
}
