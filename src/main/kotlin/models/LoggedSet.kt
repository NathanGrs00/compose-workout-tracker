package models

/**
 * This is a class to represent a logged set, which consists of a weight, a number of reps, and a calculated volume.
 */
data class LoggedSet (
    val weight: Double,
    val reps: Int,
    val volume: Double = weight * reps
)