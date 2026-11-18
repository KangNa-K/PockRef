package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Draw only visible lines, sharing the images' world origin and presentation offset. */
internal fun DrawScope.drawCanvasGrid(
    backgroundColor: Color,
    canvasScale: Float,
    canvasOffset: Offset,
) {
    if (!canvasScale.isFinite() || canvasScale <= 0f ||
        !canvasOffset.x.isFinite() || !canvasOffset.y.isFinite() ||
        size.width <= 0f || size.height <= 0f
    ) return

    // Double precision keeps the phase stable when panning far from the world origin.
    var spacing = 64.0 * canvasScale
    val minSpacing = 16.dp.toPx().toDouble()
    while (spacing < minSpacing) spacing *= 2.0

    val originX = size.width / 2.0 + canvasOffset.x
    val originY = size.height / 2.0 + canvasOffset.y
    // A positive remainder works on either side of the origin, including negative pans.
    val firstX = ((originX % spacing) + spacing) % spacing
    val firstY = ((originY % spacing) + spacing) % spacing
    val lineColor = if (backgroundColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }

    val verticalCount = ceil((size.width - firstX) / spacing).toInt().coerceAtLeast(0)
    repeat(verticalCount) { index ->
        val x = (firstX + index * spacing).toFloat()
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
    val horizontalCount = ceil((size.height - firstY) / spacing).toInt().coerceAtLeast(0)
    repeat(horizontalCount) { index ->
        val y = (firstY + index * spacing).toFloat()
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}
