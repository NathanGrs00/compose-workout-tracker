package models

/**
 * A routine (workout template), e.g. "Push day", with an ordered list of exercise IDs.
 */
data class WorkoutDay(
    val id: String,
    val name: String,
    val exerciseIds: List<String>
)
