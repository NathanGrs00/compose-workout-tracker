package viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import models.Exercise
import repository.ExerciseRepository

/**
 * ViewModel for managing the state of exercises in the UI.
 */
sealed class ExerciseUiState {
    data object Loading : ExerciseUiState()
    data class Success(val exercise: List<Exercise>) : ExerciseUiState()
    data class Error(val message: String) : ExerciseUiState()
}

class ExerciseViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow<ExerciseUiState>(ExerciseUiState.Loading)
    val uiState: StateFlow<ExerciseUiState> = _uiState

    init {
        loadExercises()
    }

    fun loadExercises() {
        scope.launch {
            _uiState.value = ExerciseUiState.Loading
            try {
                val exercises = ExerciseRepository.getAll()
                _uiState.value = ExerciseUiState.Success(exercises)
            } catch (e: Exception) {
                _uiState.value = ExerciseUiState.Error(e.message ?: "Failed to load exercises")
            }
        }
    }

    fun addExercise(exercise: Exercise) {
        scope.launch {
            try {
                println("Attempting to save: $exercise")
                ExerciseRepository.insert(exercise)
                println("Save successful")
                loadExercises()
            } catch (e: Exception) {
                println("Error saving exercise: ${e.message}")
                e.printStackTrace()
                _uiState.value = ExerciseUiState.Error(e.message ?: "Failed to add exercise")
            }
        }
    }

    fun deleteExercise(id: String) {
        scope.launch {
            try {
                ExerciseRepository.delete(id)
                loadExercises()
            } catch (e: Exception) {
                _uiState.value = ExerciseUiState.Error(e.message ?: "Failed to delete exercise")
            }
        }
    }
}