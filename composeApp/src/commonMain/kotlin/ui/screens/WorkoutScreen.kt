package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import analytics.RestState
import analytics.RestTimer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import models.WorkoutDay
import ui.KeepScreenOn
import ui.components.edgeGlow
import ui.rememberVibrate
import ui.theme.successColor
import ui.theme.warningColor
import models.WorkoutSession
import repository.WorkoutRepository
import repository.nowMillis
import ui.Format
import ui.components.TabHeader

/**
 * The "Workout" tab: start a workout (empty or from a routine), or continue the one in progress.
 */
@Composable
fun WorkoutScreen(
    repository: WorkoutRepository,
    syncIndicator: @Composable () -> Unit,
    onEditRoutine: (String?) -> Unit,
) {
    // Wrapped in a Pair so "loading" (null) differs from "no workout in progress" (Pair(null)).
    val inProgress by remember { repository.inProgressSession.map { Pair(it, Unit) } }.collectAsState(null)
    val current = inProgress ?: return

    val session = current.first
    if (session != null) {
        ActiveWorkout(session, repository, syncIndicator)
    } else {
        StartWorkout(repository, syncIndicator, onEditRoutine)
    }
}

@Composable
private fun StartWorkout(
    repository: WorkoutRepository,
    syncIndicator: @Composable () -> Unit,
    onEditRoutine: (String?) -> Unit,
) {
    val routines by repository.workoutDays.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    val sessions by repository.sessions.collectAsState(emptyList())
    val exerciseNames = remember(exercises) { exercises.associate { it.id to it.name } }
    val lastSession = sessions.firstOrNull { !it.isInProgress }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TabHeader("Workout", Modifier.padding(top = 16.dp)) { syncIndicator() } }
        item {
            Button(
                onClick = { repository.startSession(null) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start empty workout", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (lastSession != null) {
            item {
                Text(
                    "Last workout: ${lastSession.name} · ${Format.ago(lastSession.startedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEditRoutine(null) }) { Text("+ New routine") }
            }
        }
        if (routines.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text(
                        "Create routines like \"Push\", \"Pull\" and \"Legs\" to start a workout with its exercises already filled in.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(routines, key = { it.id }) { routine ->
            RoutineCard(
                routine = routine,
                exerciseNames = routine.exerciseIds.mapNotNull { exerciseNames[it] },
                lastDone = sessions.firstOrNull { it.workoutDayId == routine.id && !it.isInProgress }?.startedAt,
                onStart = { repository.startSession(routine) },
                onEdit = { onEditRoutine(routine.id) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RoutineCard(
    routine: WorkoutDay,
    exerciseNames: List<String>,
    lastDone: Long?,
    onStart: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    exerciseNames.ifEmpty { listOf("No exercises yet") }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (lastDone != null) {
                    Text("Done ${Format.ago(lastDone)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit ${routine.name}") }
            FilledTonalButton(onClick = onStart) { Text("Start") }
        }
    }
}

@Composable
private fun ActiveWorkout(
    session: WorkoutSession,
    repository: WorkoutRepository,
    syncIndicator: @Composable () -> Unit,
) {
    val sets by remember(session.id) { repository.setsForSession(session.id) }.collectAsState(emptyList())
    var now by remember { mutableLongStateOf(nowMillis()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = nowMillis()
        }
    }

    val exercises by repository.exercises.collectAsState(emptyList())
    val exerciseById = remember(exercises) { exercises.associateBy { it.id } }
    val exerciseIds = (session.exerciseIds + sets.map { it.exerciseId }).distinct()
    val rest = RestTimer.state(sets, exerciseIds, exerciseById, now)
    val glowColor = when (rest) {
        is RestState.Resting -> warningColor()
        is RestState.Done -> successColor()
        RestState.None -> null
    }

    // Buzz once when rest ends (only right at the moment, not when reopening the app later).
    val vibrate = rememberVibrate()
    val doneSetId = (rest as? RestState.Done)?.takeIf { it.overMillis < 3_000 }?.lastSetId
    LaunchedEffect(doneSetId) { if (doneSetId != null) vibrate() }
    KeepScreenOn()

    Box(Modifier.fillMaxSize().edgeGlow(glowColor, pulse = rest is RestState.Done)) {
        SessionEditor(
            session = session,
            repository = repository,
            autoAdvance = true,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.name,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    syncIndicator()
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Workout options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Discard workout", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDiscard = true },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Duration ${Format.duration(now - session.startedAt)} · ${sets.size} sets",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { confirmFinish = true }) { Text("Finish") }
                }
            }
        }

        // Floating rest timer, visible wherever you've scrolled.
        if (glowColor != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(glowColor))
                    Text(
                        when (rest) {
                            is RestState.Resting -> "Rest ${Format.duration(rest.remainingMillis + 999)} left"
                            is RestState.Done -> "Rest done, go! (+${Format.duration(rest.overMillis)})"
                            RestState.None -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(if (sets.isEmpty()) "Discard empty workout?" else "Finish workout?") },
            text = {
                Text(
                    if (sets.isEmpty()) "No sets were logged, so nothing will be saved."
                    else "${sets.size} sets · ${Format.thousands(sets.sumOf { it.volume })} kg volume in ${Format.durationShort(now - session.startedAt)}."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; repository.finishSession(session.id) }) {
                    Text(if (sets.isEmpty()) "Discard" else "Finish")
                }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("All ${sets.size} sets of this workout will be deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; repository.deleteSession(session.id) }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(session.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename workout") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { renaming = false; repository.updateSession(session.copy(name = name)) }, enabled = name.isNotBlank()) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
