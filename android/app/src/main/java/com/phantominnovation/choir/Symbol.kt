package com.phantominnovation.choir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Mark { WAVE, SEARCH, BACK, CHEVRON, PLUS, EXTERNAL, LINK, EDIT, CLOSE, SECTIONS, LIBRARY, FOLDER, LOCK, DELETE, HYMNS }

@Composable
fun Symbol(mark: Mark, color: Color = Blue, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 1.7f * u, StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>) {
            drawPath(Path().apply {
                moveTo(points[0].first * u, points[0].second * u)
                points.drop(1).forEach { lineTo(it.first * u, it.second * u) }
            }, color, style = stroke)
        }
        when (mark) {
            Mark.HYMNS -> { listOf(5f, 12f, 19f).forEach { y -> line(3f, y, 5f, y); line(9f, y, 21f, y) } }
            Mark.WAVE -> listOf(4f to (9f to 17f), 9f to (4f to 21f), 14f to (7f to 18f), 19f to (2f to 14f))
                .forEach { (x, ends) -> drawLine(color, Offset(x * u, ends.first * u), Offset(x * u, ends.second * u), 3f * u, StrokeCap.Round) }
            Mark.SEARCH -> { drawCircle(color, 7f * u, Offset(10f * u, 10f * u), style = stroke); line(15f, 15f, 21f, 21f) }
            Mark.BACK -> { path(10f to 5f, 3f to 12f, 10f to 19f); line(3f, 12f, 21f, 12f) }
            Mark.CHEVRON -> path(9f to 5f, 16f to 12f, 9f to 19f)
            Mark.PLUS -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
            Mark.CLOSE -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
            Mark.EXTERNAL -> { path(14f to 3f, 21f to 3f, 21f to 10f); line(21f, 3f, 11f, 13f); path(10f to 5f, 4f to 5f, 4f to 20f, 19f to 20f, 19f to 14f) }
            Mark.LINK -> { path(10f to 7f, 14f to 3f, 20f to 3f, 21f to 9f, 17f to 13f); path(14f to 17f, 10f to 21f, 4f to 21f, 3f to 15f, 7f to 11f); line(8f, 16f, 16f, 8f) }
            Mark.EDIT -> { path(4f to 16f, 16f to 4f, 20f to 8f, 8f to 20f, 4f to 20f, 4f to 16f); line(13f, 7f, 17f, 11f) }
            Mark.SECTIONS -> listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x, y) ->
                drawRoundRect(color, Offset(x * u, y * u), Size(7f * u, 7f * u), CornerRadius(1.5f * u), style = stroke)
            }
            Mark.LIBRARY -> { path(4f to 3f, 20f to 3f, 20f to 21f, 4f to 21f, 4f to 3f); line(8f, 3f, 8f, 21f); line(12f, 8f, 17f, 8f); line(12f, 12f, 17f, 12f) }
            Mark.FOLDER -> path(3f to 6f, 10f to 6f, 12f to 9f, 21f to 9f, 21f to 20f, 3f to 20f, 3f to 6f)
            Mark.LOCK -> { path(7f to 10f, 7f to 6f, 9f to 3f, 15f to 3f, 17f to 6f, 17f to 10f); path(4f to 10f, 20f to 10f, 20f to 21f, 4f to 21f, 4f to 10f); line(12f, 14f, 12f, 17f) }
            Mark.DELETE -> { line(3f, 6f, 21f, 6f); path(8f to 6f, 8f to 3f, 16f to 3f, 16f to 6f); path(5f to 6f, 7f to 21f, 17f to 21f, 19f to 6f); line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f) }
        }
    }
}
