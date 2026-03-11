package repository.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for Exercise, used for database interactions. It includes the exercise's ID,
 * name, and the muscle group it targets.
 * The ID is nullable to allow for auto-generated IDs when inserting new exercises.
 */
@Serializable
data class ExerciseDto(
    val id: String? = null,
    val name: String,
    @SerialName("muscle_group")
    val muscleGroup: String
)