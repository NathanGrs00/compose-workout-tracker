package models

/**
 * A single logged set: [weight] (kg) x [reps] for an exercise in a workout session.
 * Bodyweight exercises are logged with weight 0, in which case volume counts reps.
 */
data class LoggedSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val weight: Double,
    val reps: Int,
    val performedAt: Long,
) {
    val volume: Double get() = if (weight > 0) weight * reps else reps.toDouble()
}
