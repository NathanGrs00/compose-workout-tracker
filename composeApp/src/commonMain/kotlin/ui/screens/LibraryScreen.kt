package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.Exercise
import models.WorkoutDay
import repository.WorkoutRepository
import ui.components.DeleteConfirmDialog
import ui.components.ListItem
import ui.components.TabHeader
import viewmodel.ExerciseViewModel

/** The "Library" tab: exercises and routines. */
@Composable
fun LibraryScreen(
    repository: WorkoutRepository,
    exerciseViewModel: ExerciseViewModel,
    syncIndicator: @Composable () -> Unit,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onAddExercise: () -> Unit,
    onEditExercise: (Exercise) -> Unit,
    onEditRoutine: (String?) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TabHeader("Library") { syncIndicator() }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { onSelectTab(0) }, text = { Text("Exercises") })
            Tab(selected = selectedTab == 1, onClick = { onSelectTab(1) }, text = { Text("Routines") })
        }
        Column(Modifier.padding(top = 12.dp)) {
            if (selectedTab == 0) {
                ExerciseLibraryScreen(exerciseViewModel, onAddExercise, onEditExercise)
            } else {
                RoutineList(repository, onEditRoutine)
            }
        }
    }
}

@Composable
private fun RoutineList(repository: WorkoutRepository, onEditRoutine: (String?) -> Unit) {
    val routines by repository.workoutDays.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    val names = remember(exercises) { exercises.associate { it.id to it.name } }
    var toDelete by remember { mutableStateOf<WorkoutDay?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Button(onClick = { onEditRoutine(null) }) { Text("+ New routine") } }
        if (routines.isEmpty()) {
            item { Text("No routines yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(routines, key = { it.id }) { routine ->
            ListItem(
                title = routine.name,
                subtitle = routine.exerciseIds.mapNotNull { names[it] }.joinToString(" · ").ifEmpty { "No exercises" },
                onClick = { onEditRoutine(routine.id) },
                onDeleteClick = { toDelete = routine },
            )
        }
    }

    toDelete?.let { routine ->
        DeleteConfirmDialog(
            itemName = routine.name,
            onConfirm = { repository.deleteWorkoutDay(routine.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
