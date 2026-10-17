package ui.screens

import analytics.Supersets
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import models.Exercise
import models.WorkoutDay
import repository.WorkoutRepository
import repository.moved
import repository.newId
import ui.components.AppButton
import ui.components.AppTextField
import ui.components.ExercisePickerDialog
import ui.components.ScreenHeader

/**
 * Create or edit a routine: a name, an ordered list of exercises, and optional supersets
 * (two exercises done back to back, with rest after the pair).
 */
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
    var supersets by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var showPicker by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (routineId != null) {
            repository.workoutDays.first().firstOrNull { it.id == routineId }?.let {
                name = it.name
                exerciseIds = it.exerciseIds
                supersets = it.supersets
            }
            loaded = true
        }
    }

    // Display order keeps superset partners together; moving works on that order.
    val ordered = Supersets.order(exerciseIds, supersets)
    fun move(id: String, direction: Int) {
        val moved = ordered.moved(id, direction)
        exerciseIds = moved
        supersets = Supersets.reorder(supersets, moved)
    }
    fun remove(id: String) {
        exerciseIds = exerciseIds - id
        supersets = supersets.filter { id != it.first && id != it.second }
    }
    val free = ordered.filter { Supersets.partnerOf(it, supersets) == null }

    Column(
        Modifier.fillMaxSize().widthIn(max = 720.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(if (routineId == null) "New routine" else "Edit routine", onDone, backLabel = "Back")
        if (!loaded) return@Column
        AppTextField(value = name, onValueChange = { name = it }, label = "Name", placeholder = "e.g. Push day")
        Text("Exercises", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Group: a single exercise, or a superset pair.
            val groups = mutableListOf<List<String>>()
            ordered.forEach { id ->
                val partner = Supersets.partnerOf(id, supersets)
                if (partner != null && groups.lastOrNull()?.contains(partner) == true) groups[groups.lastIndex] = groups.last() + id
                else groups += listOf(id)
            }
            items(groups, key = { it.joinToString("+") }) { group ->
                if (group.size == 2) {
                    OutlinedCard(
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Superset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { supersets = supersets.filter { it.first !in group } }) { Text("Unlink") }
                        }
                        group.forEachIndexed { i, id ->
                            ExerciseRow(
                                label = "${if (i == 0) "A" else "B"}. ${exerciseById[id]?.name ?: "Unknown"}",
                                canUp = ordered.indexOf(id) > 0,
                                canDown = ordered.indexOf(id) < ordered.lastIndex,
                                onUp = { move(id, -1) },
                                onDown = { move(id, 1) },
                                onRemove = { remove(id) },
                            )
                        }
                    }
                } else {
                    val id = group.single()
                    Card(Modifier.fillMaxWidth()) {
                        ExerciseRow(
                            label = "${ordered.indexOf(id) + 1}. ${exerciseById[id]?.name ?: "Unknown"}",
                            canUp = ordered.indexOf(id) > 0,
                            canDown = ordered.indexOf(id) < ordered.lastIndex,
                            onUp = { move(id, -1) },
                            onDown = { move(id, 1) },
                            onRemove = { remove(id) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) { Text("+ Exercise") }
                    OutlinedButton(
                        onClick = { showSupersetPicker = true },
                        enabled = free.size >= 2,
                        modifier = Modifier.weight(1f),
                    ) { Text("+ Superset") }
                }
            }
        }
        AppButton(
            text = "Save routine",
            onClick = {
                repository.saveWorkoutDay(WorkoutDay(routineId ?: newId(), name, Supersets.order(exerciseIds, supersets), supersets))
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
    if (showSupersetPicker) {
        SupersetPickerDialog(
            candidates = free.mapNotNull { exerciseById[it] },
            onCreate = { a, b ->
                supersets = supersets + (a to b)
                showSupersetPicker = false
            },
            onDismiss = { showSupersetPicker = false },
        )
    }
}

@Composable
private fun ExerciseRow(
    label: String,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
        IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove") }
    }
}

/** Pick exactly two exercises to pair as a superset (the first picked is done first). */
@Composable
private fun SupersetPickerDialog(
    candidates: List<Exercise>,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create superset") },
        text = {
            Column {
                Text(
                    "Pick 2 exercises. You'll alternate between them, resting after each pair.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(candidates, key = { it.id }) { exercise ->
                        val index = picked.indexOf(exercise.id)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                picked = when {
                                    index >= 0 -> picked - exercise.id
                                    picked.size < 2 -> picked + exercise.id
                                    else -> picked
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = index >= 0, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(exercise.name, modifier = Modifier.weight(1f))
                            if (index >= 0) Text(if (index == 0) "A" else "B", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(picked[0], picked[1]) }, enabled = picked.size == 2) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
