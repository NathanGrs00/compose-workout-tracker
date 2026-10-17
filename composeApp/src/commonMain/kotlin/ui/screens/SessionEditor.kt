package ui.screens

import analytics.Analytics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ui.theme.successColor
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import analytics.Progression
import analytics.Plates
import analytics.Supersets
import analytics.Warmup
import androidx.compose.material3.OutlinedTextField
import ui.LocalBarWeight
import analytics.SessionPoint
import analytics.Suggestion
import models.Exercise
import models.Rating
import ui.components.RatingBadge
import ui.components.RatingSelector
import models.LoggedSet
import models.WorkoutSession
import repository.WorkoutRepository
import ui.Format
import ui.components.ExercisePickerDialog
import ui.components.NumberStepper

/**
 * Editable view of one workout: exercise cards with logged sets and quick inputs.
 * Used for the workout in progress and for editing past workouts.
 * [header] is placed above the exercise list inside the same scrolling column.
 * With [autoAdvance], finishing the planned sets of an exercise scrolls to the next unfinished one.
 */
@Composable
fun SessionEditor(
    session: WorkoutSession,
    repository: WorkoutRepository,
    modifier: Modifier = Modifier,
    autoAdvance: Boolean = false,
    header: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exercises by repository.exercises.collectAsState(emptyList())
    val sessionSets by remember(session.id) { repository.setsForSession(session.id) }.collectAsState(emptyList())
    val allSets by repository.allSets.collectAsState(emptyList())
    val sessions by repository.sessions.collectAsState(emptyList())
    val sessionsById = remember(sessions) { sessions.associateBy { it.id } }
    val recordIds = remember(allSets, sessionsById) { Analytics.recordSetIds(allSets, sessionsById) }
    val exerciseById = remember(exercises) { exercises.associateBy { it.id } }
    val ratings by repository.ratings.collectAsState(emptyList())
    val notes by repository.sessionNotes.collectAsState(emptyList())
    var showPicker by remember { mutableStateOf(false) }

    // Exercises in session order (superset partners kept together), plus any that only appear in sets
    // (e.g. synced from another device).
    val exerciseIds = Supersets.order((session.exerciseIds + sessionSets.map { it.exerciseId }).distinct(), session.supersets)

    fun plannedSets(id: String) = exerciseById[id]?.targetSets ?: 3
    fun doneSets(id: String) = sessionSets.count { it.exerciseId == id }
    fun scrollTo(id: String) {
        // Item 0 is the header.
        scope.launch { listState.animateScrollToItem(1 + exerciseIds.indexOf(id)) }
    }

    /** Scrolls to the next exercise (after [exerciseId], wrapping around) that still has planned sets left. */
    fun advanceFrom(exerciseId: String) {
        val index = exerciseIds.indexOf(exerciseId)
        val next = (exerciseIds.drop(index + 1) + exerciseIds.take(index))
            .firstOrNull { id -> id != exerciseId && doneSets(id) < plannedSets(id) }
            ?: return
        scrollTo(next)
    }

    /** Where to go after logging a set of [exerciseId], which now has [done] sets. */
    fun afterLog(exerciseId: String, done: Int) {
        val partner = Supersets.partnerOf(exerciseId, session.supersets)
        if (partner == null) {
            if (done == plannedSets(exerciseId)) advanceFrom(exerciseId)
            return
        }
        val partnerDone = doneSets(partner)
        when {
            // Superset: alternate A → B → (rest) → A …
            partnerDone < done && partnerDone < plannedSets(partner) -> scrollTo(partner)
            done >= plannedSets(exerciseId) && partnerDone >= plannedSets(partner) -> advanceFrom(exerciseId)
            done < plannedSets(exerciseId) -> scrollTo(if (Supersets.isFirst(exerciseId, session.supersets)) exerciseId else partner)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { header() }
        items(exerciseIds, key = { it }) { exerciseId ->
            val setsForExercise = sessionSets.filter { it.exerciseId == exerciseId }
            val exercise = exerciseById[exerciseId]
            val history = remember(allSets, sessionsById, exerciseId) {
                Analytics.previousSessions(exerciseId, session.id, allSets, sessionsById)
            }
            val exerciseRatings = remember(ratings, exerciseId) {
                ratings.filter { it.exerciseId == exerciseId }.associate { it.sessionId to it.rating }
            }
            val suggestion = remember(history, exerciseRatings, exercise) {
                exercise?.let { Progression.suggest(it, history, exerciseRatings) }
            }
            val partner = Supersets.partnerOf(exerciseId, session.supersets)
            val nextFree = exerciseIds.getOrNull(exerciseIds.indexOf(exerciseId) + 1)
                ?.takeIf { partner == null && Supersets.partnerOf(it, session.supersets) == null }
            val notesForExercise = notes.filter { it.exerciseId == exerciseId }
            ExerciseCard(
                exerciseId = exerciseId,
                exercise = exercise,
                sessionNote = notesForExercise.firstOrNull { it.sessionId == session.id }?.note,
                previousNote = history.firstOrNull()?.let { prev -> notesForExercise.firstOrNull { it.sessionId == prev.sessionId }?.note },
                onSaveSessionNote = { repository.setSessionNote(session.id, exerciseId, it) },
                onSaveSetupNote = { note -> exercise?.let { repository.updateExercise(it.copy(setupNote = note)) } },
                supersetLabel = partner?.let { p ->
                    val position = if (Supersets.isFirst(exerciseId, session.supersets)) "A" else "B"
                    "Superset $position · with ${exerciseById[p]?.name ?: "?"}"
                },
                supersetWithName = nextFree?.let { exerciseById[it]?.name },
                onSupersetWithNext = { nextFree?.let { repository.setSessionSuperset(session.id, exerciseId, it) } },
                onRemoveSuperset = { repository.removeSessionSuperset(session.id, exerciseId) },
                sets = setsForExercise,
                previous = history.firstOrNull(),
                previousRating = history.firstOrNull()?.let { exerciseRatings[it.sessionId] },
                suggestion = suggestion,
                rating = exerciseRatings[session.id],
                onRate = { repository.setRating(session.id, exerciseId, it) },
                recordSetIds = recordIds,
                canMoveUp = exerciseIds.first() != exerciseId,
                canMoveDown = exerciseIds.last() != exerciseId,
                onLog = { weight, reps ->
                    repository.logSet(session.id, exerciseId, weight, reps)
                    if (autoAdvance) afterLog(exerciseId, setsForExercise.size + 1)
                },
                onUpdateSet = repository::updateSet,
                onDeleteSet = { repository.deleteSet(it.id) },
                onRemove = { repository.removeExerciseFromSession(session.id, exerciseId) },
                onMove = { direction -> repository.moveExerciseInSession(session.id, exerciseId, direction) },
            )
        }
        item {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add exercise")
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            exercises = exercises,
            excludeIds = exerciseIds.toSet(),
            onPick = {
                repository.addExerciseToSession(session.id, it.id)
                showPicker = false
            },
            onCreate = { name, group ->
                val created = repository.addExercise(name, group)
                repository.addExerciseToSession(session.id, created.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ExerciseCard(
    exerciseId: String,
    exercise: Exercise?,
    sessionNote: String?,
    previousNote: String?,
    onSaveSessionNote: (String) -> Unit,
    onSaveSetupNote: (String) -> Unit,
    supersetLabel: String?,
    supersetWithName: String?,
    onSupersetWithNext: () -> Unit,
    onRemoveSuperset: () -> Unit,
    sets: List<LoggedSet>,
    previous: SessionPoint?,
    previousRating: Rating?,
    suggestion: Suggestion?,
    rating: Rating?,
    onRate: (Rating?) -> Unit,
    recordSetIds: Set<String>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLog: (Double, Int) -> Unit,
    onUpdateSet: (LoggedSet) -> Unit,
    onDeleteSet: (LoggedSet) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var weight by remember(exerciseId) { mutableStateOf("") }
    var reps by remember(exerciseId) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<LoggedSet?>(null) }
    var editingSessionNote by remember { mutableStateOf(false) }
    var editingSetupNote by remember { mutableStateOf(false) }
    val barWeight = LocalBarWeight.current
    // A card collapses once its planned sets are done; it can be reopened for an extra set or edits.
    var expanded by remember(exerciseId) { mutableStateOf(false) }
    val target = exercise?.targetSets ?: 3
    val complete = sets.size >= target
    val collapsed = complete && !expanded

    // Pre-fill: repeat today's last set; before the first set use the suggestion (or last time's first set).
    val prefill: Pair<Double, Int>? = sets.lastOrNull()?.let { it.weight to it.reps }
        ?: suggestion?.let { it.weight to it.reps }
        ?: previous?.sets?.firstOrNull()?.let { it.weight to it.reps }
    fun apply(values: Pair<Double, Int>) {
        weight = if (values.first > 0) Format.number(values.first) else ""
        reps = values.second.toString()
    }
    LaunchedEffect(exerciseId, prefill) {
        if (prefill != null && weight.isEmpty() && reps.isEmpty()) apply(prefill)
    }
    val parsedWeight = if (weight.isBlank()) 0.0 else Format.parseDecimal(weight)
    val parsedReps = reps.toIntOrNull()
    val canLog = parsedWeight != null && parsedReps != null && parsedReps > 0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise?.name ?: "Unknown exercise", style = MaterialTheme.typography.titleMedium)
                    if (exercise != null && !collapsed) {
                        Text(
                            "${exercise.muscleGroup} · ${exercise.repMin}–${exercise.repMax} reps · rest ${Format.duration(exercise.restSeconds * 1000L)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SetProgressLabel(done = sets.size, target = target, collapsed = collapsed)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Exercise options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (sessionNote.isNullOrBlank()) "Add note for this workout" else "Edit workout note") },
                            onClick = { menuOpen = false; editingSessionNote = true },
                        )
                        if (exercise != null) {
                            DropdownMenuItem(
                                text = { Text(if (exercise.setupNote.isBlank()) "Add setup note" else "Edit setup note") },
                                onClick = { menuOpen = false; editingSetupNote = true },
                            )
                        }
                        if (supersetLabel != null) {
                            DropdownMenuItem(text = { Text("Remove superset") }, onClick = { menuOpen = false; onRemoveSuperset() })
                        } else if (supersetWithName != null) {
                            DropdownMenuItem(text = { Text("Superset with $supersetWithName") }, onClick = { menuOpen = false; onSupersetWithNext() })
                        }
                        if (canMoveUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menuOpen = false; onMove(-1) })
                        if (canMoveDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menuOpen = false; onMove(1) })
                        DropdownMenuItem(
                            text = { Text("Remove from workout", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                if (sets.isEmpty()) onRemove() else confirmRemove = true
                            },
                        )
                    }
                }
            }

            if (collapsed) {
                Text(
                    sets.mapIndexed { i, set -> Format.set(set.weight, set.reps) + if (i >= target) " (extra)" else "" }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                )
                RatingSelector(selected = rating, onSelect = onRate)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Extra set")
                    }
                    TextButton(onClick = { expanded = true }) { Text("Edit sets") }
                }
            }

            if (supersetLabel != null) {
                Text(supersetLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (exercise != null && exercise.setupNote.isNotBlank()) {
                NoteLine("Setup", exercise.setupNote, onClick = { editingSetupNote = true })
            }
            if (!sessionNote.isNullOrBlank()) {
                NoteLine("Today", sessionNote, onClick = { editingSessionNote = true })
            }

            if (!collapsed && previous != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Last time (${Format.date(previous.date)}): " + previous.sets.joinToString(", ") { Format.set(it.weight, it.reps) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (previousRating != null) RatingBadge(previousRating)
                }
                if (!previousNote.isNullOrBlank()) {
                    Text(
                        "Last note: $previousNote",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!collapsed && suggestion != null && sets.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Suggested: " + if (suggestion.weight > 0) "${Format.weight(suggestion.weight)} × ${suggestion.reps}" else "${suggestion.reps} reps",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { apply(suggestion.weight to suggestion.reps) }) { Text("Use") }
                    }
                }
            }

            if (!collapsed) {
                sets.forEachIndexed { index, set ->
                    SetRow(index + 1, set, isRecord = set.id in recordSetIds, isExtra = index >= target, onClick = { editingSet = set })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberStepper(weight, { weight = it }, "kg", step = exercise?.weightIncrement ?: 2.5, decimal = true, modifier = Modifier.weight(1f))
                    NumberStepper(reps, { reps = it }, "reps", step = 1.0, decimal = false, modifier = Modifier.weight(1f))
                }
                if (exercise?.isBarbell == true && parsedWeight != null && parsedWeight > 0) {
                    if (sets.isEmpty()) WarmupBox(parsedWeight, barWeight)
                    PlateLine(parsedWeight, barWeight)
                }
                Button(
                    onClick = {
                        onLog(parsedWeight ?: 0.0, parsedReps ?: 0)
                        if (sets.size + 1 >= target) expanded = false
                    },
                    enabled = canLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (complete) "Log extra set" else "Log set ${sets.size + 1} of $target")
                }
                if (sets.isNotEmpty()) {
                    RatingSelector(selected = rating, onSelect = onRate)
                }
                if (complete) {
                    TextButton(onClick = { expanded = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${exercise?.name ?: "exercise"}?") },
            text = { Text("This also deletes the ${sets.size} set(s) logged for it in this workout.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }

    if (editingSessionNote) {
        NoteDialog(
            title = "Note for this workout",
            placeholder = "e.g. left shoulder tight",
            initial = sessionNote.orEmpty(),
            onSave = { onSaveSessionNote(it); editingSessionNote = false },
            onDismiss = { editingSessionNote = false },
        )
    }
    if (editingSetupNote && exercise != null) {
        NoteDialog(
            title = "Setup note · ${exercise.name}",
            placeholder = "e.g. seat height 4, wide grip",
            initial = exercise.setupNote,
            onSave = { onSaveSetupNote(it); editingSetupNote = false },
            onDismiss = { editingSetupNote = false },
        )
    }

    editingSet?.let { set ->
        SetEditDialog(
            set = set,
            onSave = { onUpdateSet(it); editingSet = null },
            onDelete = { onDeleteSet(set); editingSet = null },
            onDismiss = { editingSet = null },
        )
    }
}

@Composable
private fun NoteLine(label: String, note: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(
            "$label: $note",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** "Plates per side: 20 + 10 + 1.25" for the weight in the input. */
@Composable
private fun PlateLine(weight: Double, bar: Double) {
    val loading = Plates.perSide(weight, bar)
    val text = when {
        loading.belowBar -> "Below the bar (${Format.weight(bar)})"
        loading.perSide.isEmpty() && loading.remainder == 0.0 -> "Empty bar"
        else -> "Per side: " + loading.perSide.joinToString(" + ") { Format.number(it) } +
            if (loading.remainder > 0) " (+${Format.number(loading.remainder)} kg can't be loaded)" else ""
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

/** Warm-up ramp for the working weight; a guide only, not logged. */
@Composable
private fun WarmupBox(working: Double, bar: Double) {
    val sets = Warmup.sets(working, bar)
    if (sets.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("Warm-up (not logged)", style = MaterialTheme.typography.labelMedium)
            sets.forEach { w ->
                val plates = Plates.perSide(w.weight, bar).perSide
                Text(
                    (if (w.weight <= bar) "Empty bar" else Format.weight(w.weight)) + " × ${w.reps}" +
                        if (plates.isNotEmpty()) "   ·   per side ${plates.joinToString(" + ") { Format.number(it) }}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NoteDialog(title: String, placeholder: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (text.isBlank() && initial.isNotBlank()) "Remove" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetProgressLabel(done: Int, target: Int, collapsed: Boolean) {
    val color = if (done >= target) successColor() else MaterialTheme.colorScheme.onSurfaceVariant
    val text = when {
        collapsed -> "✓ $done/$target"
        done >= target -> "Extra set"
        else -> "Set ${done + 1} of $target"
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SetRow(number: Int, set: LoggedSet, isRecord: Boolean, isExtra: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            if (set.weight > 0) "${Format.weight(set.weight)} × ${set.reps}" else "${set.reps} reps",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (isExtra) {
            Text("Extra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        if (isRecord) {
            Icon(Icons.Default.Star, "Personal record", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        val e1rm = Analytics.estimatedOneRepMax(set.weight, set.reps)
        if (e1rm > 0 && set.reps > 1) {
            Text("1RM ≈ ${Format.number(e1rm, 1)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetEditDialog(set: LoggedSet, onSave: (LoggedSet) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var weight by remember { mutableStateOf(if (set.weight > 0) Format.number(set.weight) else "") }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    val parsedWeight = if (weight.isBlank()) 0.0 else Format.parseDecimal(weight)
    val parsedReps = reps.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set") },
        text = {
            Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper(weight, { weight = it }, "kg", step = 2.5, decimal = true)
                NumberStepper(reps, { reps = it }, "reps", step = 1.0, decimal = false)
                TextButton(onClick = onDelete) { Text("Delete set", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(set.copy(weight = parsedWeight ?: 0.0, reps = parsedReps ?: 0)) },
                enabled = parsedWeight != null && parsedReps != null && parsedReps > 0,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
