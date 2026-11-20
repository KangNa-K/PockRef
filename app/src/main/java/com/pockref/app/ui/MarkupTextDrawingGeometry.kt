package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.Size2D
import kotlin.math.cos
import kotlin.math.sin

/** World-space layout corners, without the extra area reserved for touch interaction. */
internal fun markupTextDrawingCorners(
    text: CanvasMarkupText,
    size: Size2D = SharedMarkupRenderer.textSize(text),
): List<Offset> {
    if (text.text.isEmpty() || !size.width.isFinite() || !size.height.isFinite() ||
        size.width <= 0f || size.height <= 0f
    ) return emptyList()
    val halfWidth = size.width / 2f
    val halfHeight = size.height / 2f
    val c = cos(text.rotation)
    val s = sin(text.rotation)
    return listOf(
        Offset(-halfWidth, -halfHeight), Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight), Offset(-halfWidth, halfHeight),
    ).map { text.position + Offset(it.x * c - it.y * s, it.x * s + it.y * c) }
}
