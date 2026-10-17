package ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import repository.SettingsStore
import repository.sync.SyncManager
import repository.sync.SyncState
import repository.sync.SyncStatus
import ui.Format
import ui.components.SectionCard
import ui.components.TabHeader

/** Cloud sync setup and status. */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    syncManager: SyncManager,
    syncState: SyncState,
    pendingChanges: Long,
    barWeight: Double,
    onBarWeightChange: (Double) -> Unit,
) {
    var bar by remember { mutableStateOf(Format.number(barWeight)) }
    var url by remember { mutableStateOf(settings.supabaseUrl) }
    var key by remember { mutableStateOf(settings.supabaseKey) }
    var showKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed = url.trim().trimEnd('/') != settings.supabaseUrl || key.trim() != settings.supabaseKey

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TabHeader("Settings")

        SectionCard(
            "Sync between phone and PC",
            subtitle = "Everything is saved on this device first and synced through your Supabase project when online.",
        ) {
            val status = when (syncState.status) {
                SyncStatus.NotConfigured -> "Not set up: data stays on this device only."
                SyncStatus.Syncing -> "Syncing…"
                SyncStatus.Idle -> "Up to date"
                SyncStatus.Offline -> "Offline"
                SyncStatus.Error -> "Sync failed"
            }
            Text(status, style = MaterialTheme.typography.titleSmall)
            syncState.lastSyncedAt?.let { Text("Last synced ${Format.ago(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("$pendingChanges change(s) waiting to upload", style = MaterialTheme.typography.bodySmall)
            syncState.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Supabase project URL") },
                placeholder = { Text("https://xyzcompany.supabase.co") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Supabase anon key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { syncManager.reconfigure(url, key) }, enabled = changed) { Text("Save") }
                OutlinedButton(
                    onClick = { scope.launch { syncManager.syncNow() } },
                    enabled = settings.isSyncConfigured && syncState.status != SyncStatus.Syncing,
                ) { Text("Sync now") }
            }
            Text(
                "Use the same URL and key on your phone and your PC. Find them in Supabase under " +
                    "Project Settings → API. The database tables are created by running supabase/schema.sql " +
                    "in the Supabase SQL editor (see README).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Plate calculator", subtitle = "Used for barbell exercises: plates per side and warm-ups.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ui.components.NumberStepper(
                    value = bar,
                    onValueChange = { bar = it },
                    label = "Bar (kg)",
                    step = 2.5,
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
                val parsed = Format.parseDecimal(bar)?.takeIf { it in 0.0..50.0 }
                Button(onClick = { parsed?.let(onBarWeightChange) }, enabled = parsed != null && parsed != barWeight) { Text("Save") }
            }
            Text(
                "Plates: 25, 20, 15, 10, 5, 2.5, 1.25 kg.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("About") {
            Text("Workout Tracker 1.3.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Weights are in kg. Estimated 1RM uses the Epley formula: weight × (1 + reps / 30).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
