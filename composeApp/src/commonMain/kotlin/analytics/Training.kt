package analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import models.LoggedSet
import models.Rating
import models.WorkoutDay
import models.WorkoutSession
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plates to load on each side of a barbell. */
object Plates {
    val DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /**
     * @property perSide plates for one side, heaviest first.
     * @property remainder kg that can't be loaded with the available plates (total, both sides).
     */
    data class Loading(val perSide: List<Double>, val remainder: Double, val belowBar: Boolean)

    fun perSide(total: Double, bar: Double = 20.0, plates: List<Double> = DEFAULT_PLATES): Loading {
        if (total < bar - 1e-9) return Loading(emptyList(), 0.0, belowBar = true)
        var side = (total - bar) / 2
        val result = mutableListOf<Double>()
        for (plate in plates.sortedDescending()) {
            while (side >= plate - 1e-9) {
                result += plate
                side -= plate
            }
        }
        val remainder = (side * 2 * 100).roundToInt() / 100.0
        return Loading(result, if (abs(remainder) < 1e-6) 0.0 else remainder, belowBar = false)
    }
}

/** Warm-up ramp for heavy (barbell) lifts. Not logged, just a guide. */
object Warmup {
    data class WarmupSet(val weight: Double, val reps: Int)

    private val ramp = listOf(0.4 to 8, 0.6 to 5, 0.8 to 3)

    /** Empty bar ×10, then ~40% ×8, 60% ×5, 80% ×3 of [working], rounded to 2.5 kg and only if lighter than [working]. */
    fun sets(working: Double, bar: Double = 20.0): List<WarmupSet> {
        if (working <= bar) return emptyList()
        val result = mutableListOf(WarmupSet(bar, 10))
        for ((fraction, reps) in ramp) {
            val weight = (working * fraction / 2.5).roundToInt() * 2.5
            if (weight > result.last().weight && weight < working) result += WarmupSet(weight, reps)
        }
        return result
    }
}

/** Supersets: pairs of exercises done back to back. */
object Supersets {
    fun partnerOf(exerciseId: String, pairs: List<Pair<String, String>>): String? = pairs.firstNotNullOfOrNull {
        when (exerciseId) {
            it.first -> it.second
            it.second -> it.first
            else -> null
        }
    }

    /** Whether [exerciseId] is the first (A) exercise of its pair. */
    fun isFirst(exerciseId: String, pairs: List<Pair<String, String>>): Boolean = pairs.any { it.first == exerciseId }

    /** Makes the exercise that comes first in [order] the "A" of each pair. */
    fun reorder(pairs: List<Pair<String, String>>, order: List<String>): List<Pair<String, String>> = pairs.map {
        if (order.indexOf(it.second) in 0 until order.indexOf(it.first)) it.second to it.first else it
    }

    /** Exercise order with each superset partner placed directly after the first member that appears. */
    fun order(ids: List<String>, pairs: List<Pair<String, String>>): List<String> {
        val result = mutableListOf<String>()
        for (id in ids) {
            if (id in result) continue
            result += id
            val partner = partnerOf(id, pairs)
            if (partner != null && partner in ids && partner !in result) result += partner
        }
        return result
    }
}

/** Suggests which routine to do next. */
object NextWorkout {
    /** The routine done least recently (never-done routines first, in list order) and when it was last done. */
    fun suggest(routines: List<WorkoutDay>, sessions: List<WorkoutSession>): Pair<WorkoutDay, Long?>? {
        if (routines.isEmpty()) return null
        val lastDone = sessions.filter { !it.isInProgress && it.workoutDayId != null }
            .groupBy { it.workoutDayId!! }
            .mapValues { (_, list) -> list.maxOf { it.startedAt } }
        return routines
            .map { it to lastDone[it.id] }
            .minWith(compareBy<Pair<WorkoutDay, Long?>> { it.second != null }.thenBy { it.second ?: 0L })
    }
}

data class StalledExercise(val exerciseId: String, val sessionsWithoutProgress: Int, val bestE1rm: Double)

data class WeeklyReport(
    val thisWeek: WeekBucket,
    val lastWeek: WeekBucket,
    val recordsThisWeek: Int,
    val stalled: List<StalledExercise>,
)

/** Weekly summary and rating trends. */
object Insights {
    fun weeklyReport(
        sets: List<LoggedSet>,
        sessions: List<WorkoutSession>,
        now: Long,
        timeZone: TimeZone,
    ): WeeklyReport {
        val weeks = Analytics.weeklySummary(sets, sessions, 2, now, timeZone)
        val sessionsById = sessions.associateBy { it.id }
        val thisWeekStart = weeks.last().weekStart
        val records = Analytics.recordSetIds(sets, sessionsById)
        val recordsThisWeek = sets.count {
            it.id in records && sessionsById[it.sessionId]?.let { s -> Analytics.weekStart(s.startedAt, timeZone) == thisWeekStart } == true
        }
        return WeeklyReport(weeks.last(), weeks.first(), recordsThisWeek, stalledExercises(sets, sessionsById))
    }

    /**
     * Exercises (with weights) whose best estimated 1RM hasn't improved in their last [window] or more sessions,
     * most stalled first.
     */
    fun stalledExercises(
        sets: List<LoggedSet>,
        sessions: Map<String, WorkoutSession>,
        window: Int = 3,
    ): List<StalledExercise> = sets.map { it.exerciseId }.distinct().mapNotNull { exerciseId ->
        val history = Analytics.exerciseHistory(exerciseId, sets, sessions).filter { it.bestE1rm > 0 }
        if (history.size < window + 1) return@mapNotNull null
        val best = history.maxOf { it.bestE1rm }
        // Count sessions after the first time the best was reached; ties don't count as progress.
        val firstBestIndex = history.indexOfFirst { it.bestE1rm >= best }
        val since = history.size - 1 - firstBestIndex
        if (since >= window) StalledExercise(exerciseId, since, history[firstBestIndex].bestE1rm) else null
    }.sortedByDescending { it.sessionsWithoutProgress }

    private fun Rating.score(): Double = when (this) {
        Rating.GOOD -> 1.0
        Rating.OKAY -> 0.5
        Rating.BAD -> 0.0
    }

    /** Average "feel" per week (Good = 100%, Okay = 50%, Bad = 0%), null for weeks without ratings. Oldest first. */
    fun weeklyFeel(
        ratings: List<Pair<String, Rating>>,
        sessions: Map<String, WorkoutSession>,
        weeks: Int,
        now: Long,
        timeZone: TimeZone,
    ): List<Pair<LocalDate, Double?>> {
        val current = Analytics.weekStart(now, timeZone)
        val byWeek = ratings.mapNotNull { (sessionId, rating) ->
            sessions[sessionId]?.let { Analytics.weekStart(it.startedAt, timeZone) to rating.score() }
        }.groupBy({ it.first }, { it.second })
        return (weeks - 1 downTo 0).map { ago ->
            val week = current.minus(ago * 7, DateTimeUnit.DAY)
            week to byWeek[week]?.average()
        }
    }

    /** Exercises rated Bad at least [minimum] times since [since], most first. */
    fun oftenBad(
        ratings: List<Triple<String, String, Rating>>,
        sessions: Map<String, WorkoutSession>,
        since: Long,
        minimum: Int = 2,
    ): List<Pair<String, Int>> = ratings
        .filter { (sessionId, _, rating) -> rating == Rating.BAD && (sessions[sessionId]?.startedAt ?: 0) >= since }
        .groupingBy { it.second }.eachCount()
        .filter { it.value >= minimum }
        .toList()
        .sortedByDescending { it.second }
}
