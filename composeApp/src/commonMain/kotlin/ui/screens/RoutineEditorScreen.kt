package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import models.WorkoutDay
import repository.WorkoutRepository
import repository.moved
import repository.newId
import ui.components.AppButton
import ui.components.AppTextField
import ui.components.ExercisePickerDialog
import ui.components.ScreenHeader

/** Create or edit a routine: a name and an ordered list of exercises. */
@Composable
fun RoutineEditorScreen(
    routineId: String?,
    repository: WorkoutRepository,
    onDone: () -> Unit,
) {
    val exercises by repository.exercises.collectAsState(emptyList())
    val exerciseById = remember(exercises) { exercises.associateBy { it.id } }
    var loaded by remember { mutableStateOf(routineId == null) }
    var name by remember { mutableStateOf("") }
    var exerciseIds by remember { mutableStateOf(listOf<String>()) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (routineId != null) {
            repository.workoutDays.first().firstOrNull { it.id == routineId }?.let {
                name = it.name
                exerciseIds = it.exerciseIds
            }
            loaded = true
        }
    }

    Column(
        Modifier.fillMaxSize().widthIn(max = 720.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(if (routineId == null) "New routine" else "Edit routine", onDone, backLabel = "Back")
        if (!loaded) return@Column
        AppTextField(value = name, onValueChange = { name = it }, label = "Name", placeholder = "e.g. Push day")
        Text("Exercises", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(exerciseIds, key = { _, id -> id }) { index, id ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${exerciseById[id]?.name ?: "Unknown"}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { exerciseIds = exerciseIds.moved(id, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { exerciseIds = exerciseIds.moved(id, 1) }, enabled = index < exerciseIds.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, "Move down")
                        }
                        IconButton(onClick = { exerciseIds = exerciseIds - id }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add exercise") }
            }
        }
        AppButton(
            text = "Save routine",
            onClick = {
                repository.saveWorkoutDay(WorkoutDay(routineId ?: newId(), name, exerciseIds))
                onDone()
            },
            enabled = name.isNotBlank(),
            isFullWidth = true,
        )
    }

    if (showPicker) {
        ExercisePickerDialog(
            exercises = exercises,
            excludeIds = exerciseIds.toSet(),
            onPick = { exerciseIds = exerciseIds + it.id; showPicker = false },
            onCreate = { n, group ->
                val created = repository.addExercise(n, group)
                exerciseIds = exerciseIds + created.id
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
