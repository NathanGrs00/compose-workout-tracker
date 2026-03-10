package models

/**
 * This is a class to represent a workout day, which consists of a name and a list of exercise IDs.
 */
data class WorkoutDay (
    val id: String,
    val name: String,
    val exerciseIds: List<String>
)