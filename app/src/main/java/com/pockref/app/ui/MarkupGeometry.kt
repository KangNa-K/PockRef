package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import kotlin.math.*

/** Stored shape endpoints remain editable; tessellation is a rendering concern only. */
internal fun markupPolylines(stroke: CanvasMarkupStroke): List<List<Offset>> {
    if (!stroke.isRenderable) return emptyList()
    if (stroke.points.size == 1) return listOf(listOf(stroke.points.single(), stroke.points.single()))
    val a = stroke.points.first()
    val b = stroke.points.last()
    val center = Offset((stroke.points.minOf { it.x } + stroke.points.maxOf { it.x })/2f,
        (stroke.points.minOf { it.y } + stroke.points.maxOf { it.y })/2f)
    val paths = when (stroke.kind) {
        "rectangle" -> listOf(listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a))
        "ellipse" -> listOf((0..96).map {
            val angle = it * 2.0 * PI / 96
            center + Offset((abs(b.x-a.x)/2 * cos(angle)).toFloat(), (abs(b.y-a.y)/2 * sin(angle)).toFloat())
        })
        "arrow" -> {
            val vector = b-a
            val length = vector.getDistance()
            if (length < 0.001f) listOf(listOf(a,b)) else {
                val unit = vector/length
                val head = min(max(stroke.lineWidth*4, length*0.12f), length*0.4f)
                val normal = Offset(-unit.y, unit.x)
                listOf(listOf(a,b), listOf(b-unit*head+normal*(head*0.5f), b, b-unit*head-normal*(head*0.5f)))
            }
        }
        "line" -> listOf(listOf(a,b))
        else -> listOf(stroke.points)
    }
    if (stroke.rotation == 0f) return paths
    val c = cos(stroke.rotation); val s = sin(stroke.rotation)
    return paths.map { path -> path.map { p ->
        val delta = p-center
        center + Offset(delta.x*c-delta.y*s, delta.x*s+delta.y*c)
    } }
}

internal fun hitMarkupStroke(stroke: CanvasMarkupStroke, point: Offset, tolerance: Float): Boolean =
    hitMarkupStrokeAlongSegment(stroke, point, point, tolerance)

internal fun hitMarkupStrokeAlongSegment(stroke: CanvasMarkupStroke, from: Offset, to: Offset, tolerance: Float): Boolean =
    !stroke.locked && markupPolylines(stroke).any { path -> path.zipWithNext().any { (a,b) ->
        markupSegmentDistance(from, to, a, b) <= tolerance + stroke.lineWidth / 2
    } }

internal fun markupSegmentDistance(a: Offset, b: Offset, c: Offset, d: Offset): Float {
    fun cross(u: Offset, v: Offset) = u.x * v.y - u.y * v.x
    fun pointDistance(point: Offset, start: Offset, end: Offset): Float {
        val v = end - start
        val lengthSquared = v.x * v.x + v.y * v.y
        val t = if (lengthSquared < 0.000001f) 0f else
            (((point - start).x * v.x + (point - start).y * v.y) / lengthSquared).coerceIn(0f, 1f)
        return (point - (start + v * t)).getDistance()
    }
    val denominator = cross(b - a, d - c)
    if (abs(denominator) > 0.000001f) {
        val t = cross(c - a, d - c) / denominator
        val u = cross(c - a, b - a) / denominator
        if (t in 0f..1f && u in 0f..1f) return 0f
    }
    return minOf(pointDistance(a, c, d), pointDistance(b, c, d),
        pointDistance(c, a, b), pointDistance(d, a, b))
}
