package repository.mappers

import models.Exercise
import models.ExerciseRating
import models.Rating
import models.LoggedSet
import models.WorkoutDay
import models.WorkoutSession
import repository.splitIds
import db.Exercise as ExerciseRow
import db.Exercise_rating as ExerciseRatingRow
import db.Logged_set as LoggedSetRow
import db.Workout_day as WorkoutDayRow
import db.Workout_session as WorkoutSessionRow

/**
 * Extension functions to convert local database rows into domain models.
 */
fun ExerciseRow.toExercise() = Exercise(
    id = id,
    name = name,
    muscleGroup = muscle_group,
    repMin = rep_min.toInt(),
    repMax = rep_max.toInt(),
    weightIncrement = weight_increment,
)

/** Null if the stored rating is unknown (e.g. written by a newer app version). */
fun ExerciseRatingRow.toExerciseRating(): ExerciseRating? =
    Rating.fromName(rating)?.let { ExerciseRating(sessionId = session_id, exerciseId = exercise_id, rating = it) }

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
