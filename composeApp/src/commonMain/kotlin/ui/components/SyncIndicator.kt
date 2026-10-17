package ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import repository.sync.SyncState
import repository.sync.SyncStatus

/** Small chip showing the cloud sync state. Tapping it syncs now. */
@Composable
fun SyncIndicator(state: SyncState, pendingChanges: Long, onClick: () -> Unit) {
    val label = when (state.status) {
        SyncStatus.NotConfigured -> "Local only"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.Offline -> if (pendingChanges > 0) "Offline · $pendingChanges" else "Offline"
        SyncStatus.Error -> "Sync error"
        SyncStatus.Idle -> if (pendingChanges > 0) "$pendingChanges pending" else "Synced"
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            when (state.status) {
                SyncStatus.Syncing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                SyncStatus.Error, SyncStatus.Offline -> Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
                SyncStatus.Idle -> Icon(if (pendingChanges > 0) Icons.Default.Refresh else Icons.Default.Check, null, Modifier.size(18.dp))
                SyncStatus.NotConfigured -> Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            }
        },
        colors = if (state.status == SyncStatus.Error) {
            AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.error,
                leadingIconContentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}
