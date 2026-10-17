package models

/**
 * A performed workout. [finishedAt] is null while the workout is still in progress.
 * [exerciseIds] keeps the order of exercises shown during the workout.
 * Times are epoch milliseconds.
 */
data class WorkoutSession(
    val id: String,
    val workoutDayId: String?,
    val name: String,
    val exerciseIds: List<String>,
    val startedAt: Long,
    val finishedAt: Long?,
) {
    val isInProgress: Boolean get() = finishedAt == null
}
