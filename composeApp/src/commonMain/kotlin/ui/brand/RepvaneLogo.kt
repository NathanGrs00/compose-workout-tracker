package ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Repvane brand colours (taken from the app theme). */
object RepvaneColors {
    val Primary = Color(0xFF2B5EA7)
    val Navy = Color(0xFF001B3F)
    val Container = Color(0xFFD7E3FF)
    val LightBlue = Color(0xFFABC7FF)
    val Accent = Color(0xFFFFB870)
}

/**
 * Draws the Repvane mark: a dumbbell as a compass needle pointing up and right,
 * inside a compass ring. Designed on a 200×200 grid and scaled to the draw area.
 * With [background] it is drawn on the navy rounded-square app-icon tile.
 */
fun DrawScope.drawRepvaneMark(background: Boolean = false) {
    val s = minOf(size.width, size.height) / 200f
    val left = (size.width - 200f * s) / 2
    val top = (size.height - 200f * s) / 2
    withTransform({
        translate(left, top)
        scale(s, s, pivot = Offset.Zero)
    }) {
        if (background) {
            drawRoundRect(RepvaneColors.Navy, size = Size(200f, 200f), cornerRadius = CornerRadius(46f))
        }
        val center = Offset(100f, 100f)
        drawCircle(RepvaneColors.Container, radius = 66f, center = center, style = Stroke(width = 6f))
        val tick = RepvaneColors.Container.copy(alpha = 0.7f)
        listOf(
            Offset(100f, 40f) to Offset(100f, 50f),
            Offset(160f, 100f) to Offset(150f, 100f),
            Offset(100f, 160f) to Offset(100f, 150f),
            Offset(40f, 100f) to Offset(50f, 100f),
        ).forEach { (a, b) -> drawLine(tick, a, b, strokeWidth = 5f, cap = StrokeCap.Round) }
        rotate(45f, pivot = center) {
            drawRect(Color.White, topLeft = Offset(95f, 62f), size = Size(10f, 76f))
            drawRoundRect(RepvaneColors.Accent, topLeft = Offset(78f, 46f), size = Size(44f, 22f), cornerRadius = CornerRadius(7f))
            drawRoundRect(Color.White, topLeft = Offset(78f, 132f), size = Size(44f, 22f), cornerRadius = CornerRadius(7f))
        }
    }
}

/** The Repvane mark as a composable. */
@Composable
fun RepvaneMark(size: Dp, modifier: Modifier = Modifier, background: Boolean = false) {
    Canvas(modifier.size(size)) { drawRepvaneMark(background) }
}

/** The app icon as a [Painter] (e.g. the desktop window icon). */
class RepvaneIconPainter : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)
    override fun DrawScope.onDraw() {
        drawRepvaneMark(background = true)
    }
}
