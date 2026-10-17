package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import models.Exercise
import repository.WorkoutRepository

/**
 * ViewModel for managing the state of exercises in the UI.
 */
sealed class ExerciseUiState {
    data object Loading : ExerciseUiState()
    data class Success(val exercise: List<Exercise>) : ExerciseUiState()
    data class Error(val message: String) : ExerciseUiState()
}

class ExerciseViewModel(private val repository: WorkoutRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val uiState: StateFlow<ExerciseUiState> = repository.exercises
        .map<List<Exercise>, ExerciseUiState> { ExerciseUiState.Success(it) }
        .catch { emit(ExerciseUiState.Error(it.message ?: "Failed to load exercises")) }
        .stateIn(scope, SharingStarted.Eagerly, ExerciseUiState.Loading)

    fun addExercise(name: String, muscleGroup: String) {
        repository.addExercise(name, muscleGroup)
    }

    fun updateExercise(exercise: Exercise) {
        repository.updateExercise(exercise)
    }

    fun deleteExercise(id: String) {
        repository.deleteExercise(id)
    }

    fun addStarterExercises() {
        repository.addStarterExercises()
    }

    fun clear() {
        scope.cancel()
    }
}
