package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// iOS PencilCanvas uses the same order: line first, then closed
// triangle / rectangle / ellipse. Android stores these as regular
// stroke point lists so the existing .pur codec remains unchanged.
internal fun regularizeMarkupStrokeForCommit(stroke: CanvasMarkupStroke): CanvasMarkupStroke {
    val shapePoints = recognizedMarkupShapePoints(stroke.points, stroke.lineWidth)
    val points = shapePoints ?: smoothMarkupStrokePoints(stroke.points)
    return stroke.copy(points = points)
}

private fun recognizedMarkupShapePoints(points: List<Offset>, lineWidth: Float): List<Offset>? {
    val cleaned = cleanedMarkupPoints(points)
    if (cleaned.size < 12) return null
    val bounds = cleaned.markupBounds() ?: return null
    val maxDim = max(bounds.width, bounds.height)
    val minShapeSize = max(8f, lineWidth * 6f)
    if (maxDim < minShapeSize) return null

    recognizedMarkupLine(cleaned, bounds, lineWidth)?.let { return it }
    if (markupDistance(cleaned.first(), cleaned.last()) > maxDim * 0.28f) return null

    recognizedMarkupPolygon(cleaned, expectedCorners = 3, bounds = bounds, lineWidth = lineWidth)?.let { return it }
    recognizedMarkupPolygon(cleaned, expectedCorners = 4, bounds = bounds, lineWidth = lineWidth)?.let { return it }
    return recognizedMarkupEllipse(cleaned, bounds)
}

private fun recognizedMarkupLine(
    points: List<Offset>,
    bounds: MarkupBounds,
    lineWidth: Float,
): List<Offset>? {
    val first = points.first()
    val last = points.last()
    val length = markupDistance(first, last)
    if (length < max(bounds.width, bounds.height) * 0.75f) return null
    val maxDistance = points.maxOf { it.distanceToLine(first, last) }
    if (maxDistance > max(lineWidth * 1.5f, length * 0.06f)) return null
    return listOf(first, last)
}

private fun recognizedMarkupPolygon(
    points: List<Offset>,
    expectedCorners: Int,
    bounds: MarkupBounds,
    lineWidth: Float,
): List<Offset>? {
    val maxDim = max(bounds.width, bounds.height)
    var simplified = rdpMarkupPoints(points, epsilon = max(lineWidth * 1.5f, maxDim * 0.055f)).toMutableList()
    if (simplified.size > 1 && markupDistance(simplified.first(), simplified.last()) <= maxDim * 0.12f) {
        simplified.removeAt(simplified.lastIndex)
    }
    simplified = removeNearMarkupDuplicates(simplified, minDistance = max(lineWidth * 2f, maxDim * 0.08f)).toMutableList()
    if (simplified.size != expectedCorners) return null

    if (expectedCorners == 3) {
        val area = abs(markupPolygonArea(simplified))
        if (area <= bounds.width * bounds.height * 0.18f) return null
    } else if (expectedCorners == 4 && !isMarkupRectangleLike(simplified)) {
        return null
    }
    return simplified + simplified.first()
}

private fun recognizedMarkupEllipse(points: List<Offset>, bounds: MarkupBounds): List<Offset>? {
    var left = bounds.left
    var top = bounds.top
    var right = bounds.right
    var bottom = bounds.bottom
    if (bounds.width < 8f || bounds.height < 8f) return null
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val rx = bounds.width / 2f
    val ry = bounds.height / 2f
    if (rx <= 0f || ry <= 0f) return null
    val meanError = points
        .map { point ->
            val nx = (point.x - cx) / rx
            val ny = (point.y - cy) / ry
            abs(sqrt(nx * nx + ny * ny) - 1f)
        }
        .average()
        .toFloat()
    if (meanError >= 0.16f) return null

    val ratio = minOf(bounds.width, bounds.height) / max(bounds.width, bounds.height)
    if (ratio > 0.86f) {
        val side = (bounds.width + bounds.height) / 2f
        left = cx - side / 2f
        right = cx + side / 2f
        top = cy - side / 2f
        bottom = cy + side / 2f
    }
    return ellipseMarkupPoints(left, top, right, bottom, samples = 120)
}

private fun smoothMarkupStrokePoints(points: List<Offset>): List<Offset> {
    val cleaned = cleanedMarkupPoints(points)
    if (cleaned.size < 4) return cleaned
    return buildList {
        add(cleaned.first())
        for (i in 1 until cleaned.lastIndex) {
            val prev = cleaned[i - 1]
            val cur = cleaned[i]
            val next = cleaned[i + 1]
            add(
                Offset(
                    x = prev.x * 0.25f + cur.x * 0.5f + next.x * 0.25f,
                    y = prev.y * 0.25f + cur.y * 0.5f + next.y * 0.25f,
                ),
            )
        }
        add(cleaned.last())
    }
}

private fun cleanedMarkupPoints(points: List<Offset>): List<Offset> {
    val result = ArrayList<Offset>(points.size)
    for (point in points) {
        if (result.lastOrNull()?.let { markupDistance(it, point) > 0f } != false) {
            result += point
        }
    }
    return result
}

private fun removeNearMarkupDuplicates(points: List<Offset>, minDistance: Float): List<Offset> {
    val result = ArrayList<Offset>(points.size)
    for (point in points) {
        if (result.lastOrNull()?.let { markupDistance(it, point) } ?: Float.MAX_VALUE >= minDistance) {
            result += point
        }
    }
    if (result.size > 1 && markupDistance(result.first(), result.last()) < minDistance) {
        result.removeAt(result.lastIndex)
    }
    return result
}

private fun rdpMarkupPoints(points: List<Offset>, epsilon: Float): List<Offset> {
    if (points.size <= 2) return points
    val first = points.first()
    val last = points.last()
    var maxDistance = 0f
    var maxIndex = 0
    for (index in 1 until points.lastIndex) {
        val distance = points[index].distanceToLine(first, last)
        if (distance > maxDistance) {
            maxDistance = distance
            maxIndex = index
        }
    }
    if (maxDistance > epsilon) {
        val left = rdpMarkupPoints(points.subList(0, maxIndex + 1), epsilon)
        val right = rdpMarkupPoints(points.subList(maxIndex, points.size), epsilon)
        return left.dropLast(1) + right
    }
    return listOf(first, last)
}

private fun isMarkupRectangleLike(points: List<Offset>): Boolean {
    if (points.size != 4) return false
    for (index in 0 until 4) {
        val a = points[(index + 3) % 4]
        val b = points[index]
        val c = points[(index + 1) % 4]
        val v1 = a - b
        val v2 = c - b
        val dot = abs(v1.x * v2.x + v1.y * v2.y)
        val lengths = max(markupLength(v1) * markupLength(v2), 0.0001f)
        if (dot / lengths >= 0.32f) return false
    }
    return true
}

private fun markupPolygonArea(points: List<Offset>): Float {
    if (points.size <= 2) return 0f
    var area = 0f
    for (index in points.indices) {
        val next = points[(index + 1) % points.size]
        area += points[index].x * next.y - next.x * points[index].y
    }
    return area / 2f
}

private fun ellipseMarkupPoints(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    samples: Int,
): List<Offset> {
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val rx = (right - left) / 2f
    val ry = (bottom - top) / 2f
    return (0..samples).map { index ->
        val angle = index.toFloat() / samples.toFloat() * PI.toFloat() * 2f
        Offset(
            x = cx + cos(angle) * rx,
            y = cy + sin(angle) * ry,
        )
    }
}

private fun List<Offset>.markupBounds(): MarkupBounds? {
    val first = firstOrNull() ?: return null
    var left = first.x
    var right = first.x
    var top = first.y
    var bottom = first.y
    for (point in drop(1)) {
        if (point.x < left) left = point.x
        if (point.x > right) right = point.x
        if (point.y < top) top = point.y
        if (point.y > bottom) bottom = point.y
    }
    return MarkupBounds(left = left, top = top, right = right, bottom = bottom)
}

private fun Offset.distanceToLine(start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0.0001f) return markupDistance(this, start)
    return abs((x - start.x) * dy - (y - start.y) * dx) / len
}

private fun markupDistance(a: Offset, b: Offset): Float = markupLength(a - b)

private fun markupLength(value: Offset): Float = sqrt(value.x * value.x + value.y * value.y)

private data class MarkupBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
