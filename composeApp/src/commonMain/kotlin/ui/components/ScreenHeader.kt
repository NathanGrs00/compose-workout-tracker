package ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A reusable header component for screens in the app that includes a title and a back navigation button.
 */
@Composable
fun ScreenHeader(
    title: String,
    onNavigateBack: () -> Unit,
    backLabel: String = "Back"
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(4.dp))
                Text(backLabel)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}