package ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Reusable button component throughout the app.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isFullWidth: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (isFullWidth) modifier.fillMaxWidth() else modifier
    ) {
        Text(text)
    }
}