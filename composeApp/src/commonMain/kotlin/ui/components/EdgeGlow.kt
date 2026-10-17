package ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws a soft colored frame around the edges of the content (on top of it).
 * With [pulse] the glow breathes gently to draw attention. Null [color] draws nothing.
 */
@Composable
fun Modifier.edgeGlow(color: Color?, pulse: Boolean = false): Modifier {
    if (color == null) return this
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse) 0.45f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "edgeGlowAlpha",
    )
    return drawWithContent {
        drawContent()
        val layer = 2.dp.toPx()
        val layers = 6
        for (i in 0 until layers) {
            val inset = i * layer + layer / 2
            val alpha = pulseAlpha * 0.9f * (1f - i.toFloat() / layers)
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = layer),
            )
        }
    }
}
