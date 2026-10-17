package ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import models.Exercise
import models.muscleGroups

/**
 * Searchable list of exercises. If nothing matches, the typed name can be created on the spot.
 */
@Composable
fun ExercisePickerDialog(
    exercises: List<Exercise>,
    excludeIds: Set<String> = emptySet(),
    onPick: (Exercise) -> Unit,
    onCreate: (name: String, muscleGroup: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf("") }
    val filtered = exercises
        .filter { it.id !in excludeIds }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.muscleGroup.contains(query.trim(), ignoreCase = true) }
    val exactMatch = exercises.any { it.name.equals(query.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (creating) "New exercise" else "Add exercise", style = MaterialTheme.typography.titleLarge)
                if (!creating) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search or type a new name") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        if (query.isNotBlank() && !exactMatch) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Create \"${query.trim()}\"", color = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider()
                            }
                        }
                        items(filtered, key = { it.id }) { exercise ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(exercise) }.padding(vertical = 10.dp)) {
                                Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                                Text(exercise.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (filtered.isEmpty() && query.isBlank()) {
                            item { Text("No exercises yet. Type a name to create one.", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                } else {
                    Text(query.trim(), style = MaterialTheme.typography.titleMedium)
                    AppDropdown(value = newGroup, onValueChange = { newGroup = it }, label = "Muscle group", options = muscleGroups)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (creating) creating = false else onDismiss() }) { Text(if (creating) "Back" else "Cancel") }
                    if (creating) {
                        TextButton(
                            onClick = { onCreate(query.trim(), newGroup) },
                            enabled = newGroup.isNotBlank(),
                        ) { Text("Create & add") }
                    }
                }
            }
        }
    }
}
