package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.Exercise
import ui.components.DeleteConfirmDialog
import ui.components.ListItem
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
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Home")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Exercises", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNavigateToAddExercise) { Text("+ Add Exercise") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is ExerciseUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ExerciseUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is ExerciseUiState.Success -> {
                if (state.exercise.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No exercises yet, add one!")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.exercise) { exercise ->
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
}