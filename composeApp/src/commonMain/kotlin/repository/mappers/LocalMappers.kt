package repository.mappers

import models.Exercise
import models.LoggedSet
import models.WorkoutDay
import models.WorkoutSession
import repository.splitIds
import db.Exercise as ExerciseRow
import db.Logged_set as LoggedSetRow
import db.Workout_day as WorkoutDayRow
import db.Workout_session as WorkoutSessionRow

/**
 * Extension functions to convert local database rows into domain models.
 */
fun ExerciseRow.toExercise() = Exercise(id = id, name = name, muscleGroup = muscle_group)

fun WorkoutDayRow.toWorkoutDay() = WorkoutDay(id = id, name = name, exerciseIds = exercise_ids.splitIds())

fun WorkoutSessionRow.toWorkoutSession() = WorkoutSession(
    id = id,
    workoutDayId = workout_day_id,
    name = name,
    exerciseIds = exercise_ids.splitIds(),
    startedAt = started_at,
    finishedAt = finished_at,
)

fun LoggedSetRow.toLoggedSet() = LoggedSet(
    id = id,
    sessionId = session_id,
    exerciseId = exercise_id,
    weight = weight,
    reps = reps.toInt(),
    performedAt = performed_at,
)
