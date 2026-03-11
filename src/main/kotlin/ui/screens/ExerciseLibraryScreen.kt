package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.Exercise
import ui.components.DeleteConfirmDialog
import ui.components.ListItem
import ui.components.LoadingStateHandler
import ui.components.ScreenHeader
import viewmodel.ExerciseUiState
import viewmodel.ExerciseViewModel

/**
 * Screen that displays the list of exercises in the library, allowing users to view, add, and delete exercises.
 */
@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseViewModel,
    onNavigateToAddExercise: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    exerciseToDelete?.let { exercise ->
        DeleteConfirmDialog(
            itemName = exercise.name,
            onConfirm = {
                viewModel.deleteExercise(exercise.id)
                exerciseToDelete = null
            },
            onDismiss = { exerciseToDelete = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = "Exercises",
            onNavigateBack = onNavigateBack,
            backLabel = "Home"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateToAddExercise) { Text("+ Add Exercise") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LoadingStateHandler(
            isLoading = uiState is ExerciseUiState.Loading,
            error = (uiState as? ExerciseUiState.Error)?.message,
            data = (uiState as? ExerciseUiState.Success)?.exercise,
        ) { exercises ->
            if (exercises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No exercises yet, add one!")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exercises) { exercise ->
                        ListItem(
                            title = exercise.name,
                            subtitle = exercise.muscleGroup,
                            onDeleteClick = { exerciseToDelete = exercise }
                        )
                    }
                }
            }
        }
    }
}