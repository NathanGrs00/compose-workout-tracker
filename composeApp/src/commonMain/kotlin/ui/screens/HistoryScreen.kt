package ui.screens

import analytics.Analytics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.LoggedSet
import models.WorkoutSession
import repository.WorkoutRepository
import ui.Format
import ui.components.DeleteConfirmDialog
import ui.components.ScreenHeader
import ui.components.TabHeader

/** List of finished workouts, newest first, grouped by month. */
@Composable
fun HistoryScreen(
    repository: WorkoutRepository,
    syncIndicator: @Composable () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val sessions by repository.sessions.collectAsState(emptyList())
    val sets by repository.allSets.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    val finished = sessions.filter { !it.isInProgress }
    val setsBySession = remember(sets) { sets.groupBy { it.sessionId } }
    val sessionsById = remember(sessions) { sessions.associateBy { it.id } }
    val recordIds = remember(sets, sessionsById) { Analytics.recordSetIds(sets, sessionsById) }
    val exerciseNames = remember(exercises) { exercises.associate { it.id to it.name } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TabHeader("History", Modifier.padding(top = 16.dp)) { syncIndicator() } }
        if (finished.isEmpty()) {
            item { Text("No finished workouts yet.", style = MaterialTheme.typography.bodyLarge) }
        }
        finished.groupBy { Format.monthYear(it.startedAt) }.forEach { (month, monthSessions) ->
            item(key = "header-$month") {
                Text(
                    month,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(monthSessions, key = { it.id }) { session ->
                val sessionSets = setsBySession[session.id].orEmpty()
                SessionCard(
                    session = session,
                    sets = sessionSets,
                    exerciseNames = (session.exerciseIds + sessionSets.map { it.exerciseId }).distinct().mapNotNull { exerciseNames[it] },
                    records = sessionSets.count { it.id in recordIds },
                    onClick = { onOpenSession(session.id) },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSession,
    sets: List<LoggedSet>,
    exerciseNames: List<String>,
    records: Int,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (records > 0) {
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text(" $records PR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            val duration = session.finishedAt?.let { " · " + Format.durationShort(it - session.startedAt) }.orEmpty()
            Text(
                "${Format.date(session.startedAt)} · ${Format.time(session.startedAt)}$duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${sets.size} sets · ${Format.thousands(sets.sumOf { it.volume })} kg",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (exerciseNames.isNotEmpty()) {
                Text(
                    exerciseNames.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** A past workout: summary plus the same editor used while training, so mistakes can be fixed. */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    repository: WorkoutRepository,
    onNavigateBack: () -> Unit,
) {
    val session by remember(sessionId) { repository.session(sessionId) }.collectAsState(null)
    val sets by remember(sessionId) { repository.setsForSession(sessionId) }.collectAsState(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val current = session

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (current == null) {
            Column(Modifier.padding(16.dp)) { ScreenHeader("Workout", onNavigateBack, backLabel = "History") }
            return@Box
        }
        SessionEditor(
            session = current,
            repository = repository,
            modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                ScreenHeader(current.name, onNavigateBack, backLabel = "History")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val duration = current.finishedAt?.let { " · " + Format.durationShort(it - current.startedAt) }.orEmpty()
                    Column(Modifier.weight(1f)) {
                        Text("${Format.date(current.startedAt)} · ${Format.time(current.startedAt)}$duration")
                        Text(
                            "${sets.size} sets · ${Format.thousands(sets.sumOf { it.volume })} kg volume",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete workout", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete && current != null) {
        DeleteConfirmDialog(
            itemName = current.name,
            onConfirm = {
                confirmDelete = false
                repository.deleteSession(current.id)
                onNavigateBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
