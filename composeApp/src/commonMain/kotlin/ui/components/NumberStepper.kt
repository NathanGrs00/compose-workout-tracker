package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.Format

/**
 * Text field with big − / + buttons, so weight and reps can be adjusted with one thumb.
 */
@Composable
fun NumberStepper(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    step: Double,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    fun bump(direction: Int) {
        val current = Format.parseDecimal(value) ?: 0.0
        val next = (current + direction * step).coerceAtLeast(0.0)
        onValueChange(if (decimal) Format.number(next) else next.toInt().toString())
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(onClick = { bump(-1) }, modifier = Modifier.size(40.dp)) { Text("−") }
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                val allowed = if (decimal) text.filter { it.isDigit() || it == '.' || it == ',' } else text.filter { it.isDigit() }
                onValueChange(allowed.take(6))
            },
            label = { Text(label, maxLines = 1) },
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = { bump(1) }, modifier = Modifier.size(40.dp)) { Text("+") }
    }
}
