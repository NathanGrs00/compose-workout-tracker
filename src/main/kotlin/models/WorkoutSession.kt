package models

import java.time.LocalDate

/**
 * WorkoutSession gathers all the information about a workout session, including the date,
 * the exercises performed, and the total volume lifted.
 */
data class WorkoutSession(
    val id: String,
    val workoutDayId: String,
    val date: LocalDate,
    val exerciseLogs: List<ExerciseLog>,
    val totalVolume: Double = exerciseLogs.sumOf { it.totalVolume }
)