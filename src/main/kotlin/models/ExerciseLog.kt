package models

/**
 * This represents a logged exercise, which totals the volume
 * of all the sets performed for that exercise in a workout session.
 */
data class ExerciseLog (
    val id: String,
    val exerciseId: String,
    val sets: List<LoggedSet>,
    val totalVolume: Double = sets.sumOf { it.volume }
)