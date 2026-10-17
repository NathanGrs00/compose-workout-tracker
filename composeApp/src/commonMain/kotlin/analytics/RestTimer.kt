package analytics

import models.Exercise
import models.LoggedSet

/** Where the rest timer stands after the last logged set. */
sealed interface RestState {
    /** No rest running (no sets yet, rest disabled, workout complete, or long past). */
    data object None : RestState

    data class Resting(val remainingMillis: Long, val totalMillis: Long) : RestState {
        val progress: Float get() = 1f - remainingMillis.toFloat() / totalMillis
    }

    /** Rest is over; [overMillis] is how long ago it ended. */
    data class Done(val overMillis: Long, val lastSetId: String) : RestState
}

object RestTimer {
    /** How long the "rest is over" signal stays visible. */
    const val DONE_VISIBLE_MILLIS = 5 * 60_000L

    /**
     * @param sessionSets the sets logged so far in the workout.
     * @param exerciseIds exercises planned in the workout (to know when everything is done).
     * @param exercises exercise settings (rest time, planned sets) by id.
     */
    fun state(
        sessionSets: List<LoggedSet>,
        exerciseIds: List<String>,
        exercises: Map<String, Exercise>,
        now: Long,
    ): RestState {
        val last = sessionSets.maxByOrNull { it.performedAt } ?: return RestState.None
        val allPlannedDone = exerciseIds.isNotEmpty() && exerciseIds.all { id ->
            sessionSets.count { it.exerciseId == id } >= (exercises[id]?.targetSets ?: 3)
        }
        if (allPlannedDone) return RestState.None
        val restMillis = (exercises[last.exerciseId]?.restSeconds ?: 90) * 1000L
        if (restMillis <= 0) return RestState.None
        val elapsed = (now - last.performedAt).coerceAtLeast(0)
        return when {
            elapsed < restMillis -> RestState.Resting(restMillis - elapsed, restMillis)
            elapsed < restMillis + DONE_VISIBLE_MILLIS -> RestState.Done(elapsed - restMillis, last.id)
            else -> RestState.None
        }
    }
}
