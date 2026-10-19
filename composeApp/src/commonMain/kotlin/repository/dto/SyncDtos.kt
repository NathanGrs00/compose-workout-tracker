package repository.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Data Transfer Objects for the Supabase tables. Every table carries:
 *  - updated_at: client-side edit time (ms), used to resolve conflicts (newest edit wins)
 *  - deleted: soft-delete flag so deletions sync to other devices
 *  - server_updated_at: set by a database trigger, used to pull only what changed
 */

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    @SerialName("muscle_group") val muscleGroup: String,
    @SerialName("rep_min") val repMin: Int = 8,
    @SerialName("rep_max") val repMax: Int = 12,
    @SerialName("weight_increment") val weightIncrement: Double = 2.5,
    @SerialName("target_sets") val targetSets: Int = 3,
    @SerialName("rest_seconds") val restSeconds: Int = 90,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
    @SerialName("is_barbell") val isBarbell: Boolean = false,
    @SerialName("setup_note") val setupNote: String = "",
    @SerialName("load_type") val loadType: String = "WEIGHT",
)

@Serializable
data class WorkoutDayDto(
    val id: String,
    val name: String,
    @SerialName("exercise_ids") val exerciseIds: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
    val supersets: List<List<String>> = emptyList(),
)

@Serializable
data class WorkoutSessionDto(
    val id: String,
    @SerialName("workout_day_id") val workoutDayId: String? = null,
    val name: String,
    @SerialName("exercise_ids") val exerciseIds: List<String> = emptyList(),
    @SerialName("started_at") val startedAt: Long,
    @SerialName("finished_at") val finishedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
    val supersets: List<List<String>> = emptyList(),
)

@Serializable
data class LoggedSetDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val weight: Double,
    val reps: Int,
    @SerialName("performed_at") val performedAt: Long,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
)

@Serializable
data class ExerciseRatingDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val rating: String,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
)

@Serializable
data class SessionNoteDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val note: String,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
)

@Serializable
data class BodyWeightDto(
    val id: String,
    @SerialName("measured_at") val measuredAt: Long,
    val weight: Double,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val deleted: Boolean = false,
    @SerialName("server_updated_at") val serverUpdatedAt: Long? = null,
)
