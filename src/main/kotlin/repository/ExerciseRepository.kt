package repository

import models.Exercise
import repository.dto.ExerciseDto
import io.github.jan.supabase.postgrest.from
import repository.mappers.toDto
import repository.mappers.toExercise

/**
 * Methods to interact with the "exercises" table in the Supabase database,
 * including fetching all exercises, inserting a new exercise, and deleting an exercise by ID.
 */
object ExerciseRepository {
    suspend fun getAll(): List<Exercise> {
        return SupabaseClient.client
            .from("exercises")
            .select()
            .decodeList<ExerciseDto>()
            .map { it.toExercise() }
    }

    suspend fun insert(exercise: Exercise) {
        SupabaseClient.client
            .from("exercises")
            .insert(exercise.toDto())
    }

    suspend fun delete(id: String) {
        SupabaseClient.client
            .from("exercises")
            .delete{
                filter { eq("id", id) }
            }
    }
}