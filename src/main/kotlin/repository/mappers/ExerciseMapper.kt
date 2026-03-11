package repository.mappers

import models.Exercise
import repository.dto.ExerciseDto

/**
 * Extension functions to convert between Exercise and ExerciseDto, allowing for seamless mapping
 * between the domain model and the data transfer object used for database interactions.
 */
fun ExerciseDto.toExercise() = Exercise(
    id = id ?: "",
    name = name,
    muscleGroup = muscleGroup
)

fun Exercise.toDto() = ExerciseDto(
    id = id,
    name = name,
    muscleGroup = muscleGroup
)