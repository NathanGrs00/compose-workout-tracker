package models

/**
 * This is a class to represent an exercise, which consists of an ID, a name, and a muscle group.
 */
data class Exercise (
    val id: String,
    val name: String,
    val muscleGroup: String
)