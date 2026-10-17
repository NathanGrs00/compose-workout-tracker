package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import models.Rating
import ui.theme.successColor
import ui.theme.warningColor

/** Status color for a rating, readable in light and dark theme. Always shown together with its label. */
@Composable
fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.GOOD -> successColor()
    Rating.OKAY -> warningColor()
    Rating.BAD -> MaterialTheme.colorScheme.error
}

/** Small read-only label, e.g. "Good". */
@Composable
fun RatingBadge(rating: Rating, modifier: Modifier = Modifier) {
    val color = ratingColor(rating)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(rating.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** "How did it go?" Good / Okay / Bad. Tapping the selected one clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSelector(selected: Rating?, onSelect: (Rating?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        Text(
            "How did it go?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Rating.entries.forEach { rating ->
            val color = ratingColor(rating)
            FilterChip(
                selected = selected == rating,
                onClick = { onSelect(if (selected == rating) null else rating) },
                label = { Text(rating.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.18f),
                    selectedLabelColor = color,
                ),
            )
        }
    }
}
