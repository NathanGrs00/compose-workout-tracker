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
import ui.Format
import ui.components.DeleteConfirmDialog
import ui.components.ListItem
import ui.components.LoadingStateHandler
import viewmodel.ExerciseUiState
import viewmodel.ExerciseViewModel

/**
 * Displays the list of exercises in the library, allowing users to view, add, edit and delete exercises.
 */
@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseViewModel,
    onNavigateToAddExercise: () -> Unit,
    onEditExercise: (Exercise) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var filter by remember { mutableStateOf("") }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Search") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onNavigateToAddExercise) { Text("+ Add") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LoadingStateHandler(
            isLoading = uiState is ExerciseUiState.Loading,
            error = (uiState as? ExerciseUiState.Error)?.message,
            data = (uiState as? ExerciseUiState.Success)?.exercise,
        ) { exercises ->
            if (exercises.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No exercises yet, add one!")
                    FilledTonalButton(onClick = viewModel::addStarterExercises) {
                        Text("Add common exercises")
                    }
                }
            } else {
                val shown = exercises.filter {
                    filter.isBlank() || it.name.contains(filter.trim(), true) || it.muscleGroup.contains(filter.trim(), true)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(shown, key = { it.id }) { exercise ->
                        ListItem(
                            title = exercise.name,
                            subtitle = "${exercise.muscleGroup}${if (exercise.isAssisted) " · assisted" else ""} · ${exercise.targetSets} × ${exercise.repMin}–${exercise.repMax} · rest ${Format.duration(exercise.restSeconds * 1000L)}",
                            onClick = { onEditExercise(exercise) },
                            onDeleteClick = { exerciseToDelete = exercise }
                        )
                    }
                }
            }
        }
    }
}
