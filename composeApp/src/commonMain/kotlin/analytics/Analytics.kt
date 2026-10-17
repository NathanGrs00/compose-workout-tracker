package analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import models.Exercise
import models.LoggedSet
import models.WorkoutSession

/** One workout's result for a single exercise. */
data class SessionPoint(
    val sessionId: String,
    val date: Long,
    val topWeight: Double,
    val bestE1rm: Double,
    val volume: Double,
    val maxReps: Int,
    val totalReps: Int,
    val sets: List<LoggedSet>,
) {
    val bestSet: LoggedSet get() = sets.maxWith(compareBy<LoggedSet>({ it.weight }, { it.reps }))
}

data class PersonalRecords(
    val heaviest: LoggedSet?,
    val bestE1rm: Double,
    val bestE1rmSet: LoggedSet?,
    val mostReps: LoggedSet?,
    val bestSessionVolume: SessionPoint?,
)

data class WeekBucket(
    val weekStart: LocalDate,
    val volume: Double,
    val workouts: Int,
    val sets: Int,
)

enum class ProgressMetric(val label: String) {
    E1RM("Est. 1RM"),
    TOP_WEIGHT("Top weight"),
    VOLUME("Volume"),
    MAX_REPS("Max reps");

    fun valueOf(point: SessionPoint): Double = when (this) {
        E1RM -> point.bestE1rm
        TOP_WEIGHT -> point.topWeight
        VOLUME -> point.volume
        MAX_REPS -> point.maxReps.toDouble()
    }
}

/**
 * Pure analysis functions over logged data. No database or UI dependencies, so they are easy to test.
 */
object Analytics {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Estimated one-rep max using the Epley formula. Returns 0 for bodyweight (weight 0) sets. */
    fun estimatedOneRepMax(weight: Double, reps: Int): Double = when {
        weight <= 0 || reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1 + reps / 30.0)
    }

    /** Per-workout results for one exercise, oldest first. Sets of deleted/unknown sessions are ignored. */
    fun exerciseHistory(
        exerciseId: String,
        sets: List<LoggedSet>,
        sessions: Map<String, WorkoutSession>,
    ): List<SessionPoint> = sets
        .filter { it.exerciseId == exerciseId && it.sessionId in sessions }
        .groupBy { it.sessionId }
        .map { (sessionId, sessionSets) ->
            SessionPoint(
                sessionId = sessionId,
                date = sessions.getValue(sessionId).startedAt,
                topWeight = sessionSets.maxOf { it.weight },
                bestE1rm = sessionSets.maxOf { estimatedOneRepMax(it.weight, it.reps) },
                volume = sessionSets.sumOf { it.volume },
                maxReps = sessionSets.maxOf { it.reps },
                totalReps = sessionSets.sumOf { it.reps },
                sets = sessionSets.sortedBy { it.performedAt },
            )
        }
        .sortedBy { it.date }

    fun personalRecords(history: List<SessionPoint>): PersonalRecords {
        val sets = history.flatMap { it.sets }
        val bestE1rmSet = sets.maxByOrNull { estimatedOneRepMax(it.weight, it.reps) }
        return PersonalRecords(
            heaviest = sets.maxWithOrNull(compareBy<LoggedSet>({ it.weight }, { it.reps })),
            bestE1rm = bestE1rmSet?.let { estimatedOneRepMax(it.weight, it.reps) } ?: 0.0,
            bestE1rmSet = bestE1rmSet,
            mostReps = sets.maxWithOrNull(compareBy<LoggedSet>({ it.reps }, { it.weight })),
            bestSessionVolume = history.maxByOrNull { it.volume },
        )
    }

    /**
     * Least-squares trend of [values] (time ms -> value), expressed as change per week.
     * Needs at least 3 points spread over at least a week, otherwise null.
     */
    fun trendPerWeek(values: List<Pair<Long, Double>>): Double? {
        if (values.size < 3) return null
        val span = values.maxOf { it.first } - values.minOf { it.first }
        if (span < 7 * DAY_MILLIS) return null
        val xs = values.map { it.first.toDouble() / (7 * DAY_MILLIS) }
        val ys = values.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
        if (denominator == 0.0) return null
        return xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / denominator
    }

    /** Monday of the week containing [millis]. */
    fun weekStart(millis: Long, timeZone: TimeZone): LocalDate {
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
        return date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    }

    /** Volume, workouts and sets per week for the last [weeks] weeks (oldest first, including empty weeks). */
    fun weeklySummary(
        sets: List<LoggedSet>,
        sessions: List<WorkoutSession>,
        weeks: Int,
        now: Long,
        timeZone: TimeZone,
    ): List<WeekBucket> {
        val sessionWeek = sessions.associate { it.id to weekStart(it.startedAt, timeZone) }
        val currentWeek = weekStart(now, timeZone)
        return (weeks - 1 downTo 0).map { ago ->
            val week = currentWeek.minus(ago * 7, DateTimeUnit.DAY)
            val weekSets = sets.filter { sessionWeek[it.sessionId] == week }
            WeekBucket(
                weekStart = week,
                volume = weekSets.sumOf { it.volume },
                workouts = sessions.count { sessionWeek[it.id] == week && weekSets.any { s -> s.sessionId == it.id } },
                sets = weekSets.size,
            )
        }
    }

    /** Number of sets per muscle group, most trained first. */
    fun setsPerMuscleGroup(sets: List<LoggedSet>, exercises: List<Exercise>): List<Pair<String, Int>> {
        val groupOf = exercises.associate { it.id to it.muscleGroup }
        return sets.groupingBy { groupOf[it.exerciseId] ?: "Other" }.eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    /**
     * Consecutive weeks with at least one workout, counting back from this week
     * (an empty current week doesn't break the streak yet).
     */
    fun weekStreak(sessions: List<WorkoutSession>, now: Long, timeZone: TimeZone): Int {
        val trainedWeeks = sessions.map { weekStart(it.startedAt, timeZone) }.toSet()
        var week = weekStart(now, timeZone)
        if (week !in trainedWeeks) week = week.minus(7, DateTimeUnit.DAY)
        var streak = 0
        while (week in trainedWeeks) {
            streak++
            week = week.minus(7, DateTimeUnit.DAY)
        }
        return streak
    }

    /**
     * IDs of sets that set a new estimated-1RM record for their exercise at the time they were done
     * (the first-ever session of an exercise doesn't count as a record).
     */
    fun recordSetIds(sets: List<LoggedSet>, sessions: Map<String, WorkoutSession>): Set<String> {
        val records = mutableSetOf<String>()
        sets.filter { it.sessionId in sessions && it.weight > 0 }
            .groupBy { it.exerciseId }
            .forEach { (_, exerciseSets) ->
                var best = 0.0
                exerciseSets
                    .groupBy { it.sessionId }
                    .entries
                    .sortedBy { sessions.getValue(it.key).startedAt }
                    .forEachIndexed { index, (_, sessionSets) ->
                        val top = sessionSets.maxBy { estimatedOneRepMax(it.weight, it.reps) }
                        val e1rm = estimatedOneRepMax(top.weight, top.reps)
                        if (index > 0 && e1rm > best) records += top.id
                        best = maxOf(best, e1rm)
                    }
            }
        return records
    }

    /** Sets of the most recent other session in which [exerciseId] was done (for "last time" hints). */
    fun previousPerformance(
        exerciseId: String,
        currentSessionId: String,
        sets: List<LoggedSet>,
        sessions: Map<String, WorkoutSession>,
    ): List<LoggedSet> {
        val currentStart = sessions[currentSessionId]?.startedAt ?: Long.MAX_VALUE
        val previousSessionId = sets
            .filter { it.exerciseId == exerciseId && it.sessionId != currentSessionId }
            .mapNotNull { set -> sessions[set.sessionId]?.takeIf { it.startedAt <= currentStart } }
            .maxByOrNull { it.startedAt }?.id ?: return emptyList()
        return sets.filter { it.sessionId == previousSessionId && it.exerciseId == exerciseId }.sortedBy { it.performedAt }
    }
}

/** Convenience used by the week chart labels. */
fun LocalDate.plusWeeks(weeks: Int): LocalDate = plus(weeks * 7, DateTimeUnit.DAY)
