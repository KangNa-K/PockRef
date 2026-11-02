package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.Size2D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2

internal enum class MarkupHandle { SCALE, ROTATE }

/** Reuse selected objects as independent copies, offset in screen space. */
internal fun duplicateMarkupSelection(state: PockRefUiState): PockRefUiState {
    val ids = state.markupSelection
    val offset = Offset(24f, 24f) / state.canvasScale.coerceAtLeast(0.001f)
    var zOrder = maxOf(state.markupStrokes.maxOfOrNull { it.zOrder } ?: 0,
        state.markupTexts.maxOfOrNull { it.zOrder } ?: 0)
    val strokes = state.markupStrokes.filter { it.id in ids && !it.locked }.map {
        it.copy(id = java.util.UUID.randomUUID().toString(), points = it.points.map { point -> point + offset },
            zOrder = ++zOrder)
    }
    val texts = state.markupTexts.filter { it.id in ids && !it.locked }.map {
        it.copy(id = java.util.UUID.randomUUID().toString(), position = it.position + offset, zOrder = ++zOrder)
    }
    val copiedIds = (strokes.map { it.id } + texts.map { it.id }).toSet()
    if (copiedIds.isEmpty()) return state
    return state.copy(markupStrokes = state.markupStrokes + strokes, markupTexts = state.markupTexts + texts,
        selectedMarkupId = copiedIds.first(), selectedMarkupIds = copiedIds)
}

internal fun markupHandlePositions(state: PockRefUiState): Map<MarkupHandle, Offset> {
    val bounds = markupSelectionBounds(state) ?: return emptyMap()
    val spacing = 32f/state.canvasScale.coerceAtLeast(0.001f)
    return mapOf(MarkupHandle.SCALE to bounds.bottomRight,
        MarkupHandle.ROTATE to Offset(bounds.center.x, bounds.top-spacing))
}

internal fun hitMarkupHandle(state: PockRefUiState, point: Offset): MarkupHandle? =
    markupHandlePositions(state).entries.firstOrNull { (_, position) ->
        (position-point).getDistance() <= 12f/state.canvasScale.coerceAtLeast(0.001f)
    }?.key

internal fun dragMarkupHandle(base: PockRefUiState, handle: MarkupHandle, start: Offset, point: Offset): PockRefUiState {
    val pivot = markupSelectionBounds(base)?.center ?: return base
    val initial = start-pivot
    val current = point-pivot
    return when (handle) {
        MarkupHandle.SCALE -> if (initial.getDistance() < 0.001f) base else
            transformMarkupSelection(base, (current.getDistance()/initial.getDistance()).coerceIn(0.01f,100f), 0f)
        MarkupHandle.ROTATE -> transformMarkupSelection(base, 1f,
            atan2(current.y,current.x)-atan2(initial.y,initial.x))
    }
}

internal val PockRefUiState.markupSelection: Set<String>
    get() = selectedMarkupIds.ifEmpty { setOfNotNull(selectedMarkupId) }

internal fun validMarkupPoint(point: Offset): Boolean =
    point.x.isFinite() && point.y.isFinite() && abs(point.x) <= 10_000_000f && abs(point.y) <= 10_000_000f

internal fun markupTextCorners(text: CanvasMarkupText): List<Offset> {
    val bounds = markupTextScreenBounds(text.copy(rotation = 0f), Offset.Zero, Offset.Zero, 1f) ?: return emptyList()
    val c = cos(text.rotation); val s = sin(text.rotation)
    return listOf(bounds.topLeft, bounds.topRight, bounds.bottomRight, bounds.bottomLeft).map {
        val delta = it - text.position
        text.position + Offset(delta.x*c - delta.y*s, delta.x*s + delta.y*c)
    }
}

internal fun markupSelectionBounds(state: PockRefUiState, ids: Set<String> = state.markupSelection): Rect? {
    val points = state.markupStrokes.filter { it.id in ids }.flatMap { markupPolylines(it).flatten() } +
        state.markupTexts.filter { it.id in ids }.flatMap(::markupTextCorners)
    if (points.isEmpty()) return null
    return Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

internal fun selectMarkupInLasso(state: PockRefUiState, polygon: List<Offset>): Set<String> {
    if (polygon.size < 3 || polygon.any { !validMarkupPoint(it) }) return emptySet()
    val edges = (polygon + polygon.first()).zipWithNext()
    fun contains(point: Offset): Boolean {
        var inside = false
        for ((a,b) in edges) {
            if (markupSegmentDistance(point, point, a, b) < 0.001f) return true
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x-a.x)*(point.y-a.y)/(b.y-a.y)+a.x) inside = !inside
        }
        return inside
    }
    fun intersects(path: List<Offset>): Boolean = path.any(::contains) || path.zipWithNext().any { (a,b) ->
        edges.any { (c,d) -> markupSegmentDistance(a,b,c,d) < 0.001f }
    }
    return (state.markupStrokes.filter { !it.locked && markupPolylines(it).any(::intersects) }.map { it.id } +
        state.markupTexts.filter { text ->
            val corners = markupTextCorners(text)
            !text.locked && corners.isNotEmpty() && intersects(corners + corners.first())
        }.map { it.id }).toSet()
}

/** Transform the whole selection around one pivot, preserving every object's identity. */
internal fun transformMarkupSelection(state: PockRefUiState, scale: Float, rotation: Float): PockRefUiState {
    if (!scale.isFinite() || scale <= 0f || !rotation.isFinite()) return state
    val ids = state.markupSelection
    val center = markupSelectionBounds(state)?.center ?: return state
    val c = cos(rotation); val s = sin(rotation)
    fun position(point: Offset): Offset {
        val delta = (point-center)*scale
        return center + Offset(delta.x*c-delta.y*s, delta.x*s+delta.y*c)
    }
    val strokes = state.markupStrokes.map { stroke ->
        if (stroke.id !in ids || stroke.locked || stroke.points.isEmpty()) stroke else {
            val pivot = Offset((stroke.points.minOf { it.x }+stroke.points.maxOf { it.x })/2,
                (stroke.points.minOf { it.y }+stroke.points.maxOf { it.y })/2)
            stroke.copy(points = stroke.points.map { position(pivot)+(it-pivot)*scale },
                lineWidth = stroke.lineWidth*scale, rotation = stroke.rotation+rotation)
        }
    }
    val texts = state.markupTexts.map { text ->
        if (text.id !in ids || text.locked) text else text.copy(position = position(text.position),
            fontSize = text.fontSize*scale, rotation = text.rotation+rotation,
            boxSize = text.boxSize?.let { Size2D(it.width*scale,it.height*scale) })
    }
    if (strokes.any { it.lineWidth !in 0.01f..10000f || !it.rotation.isFinite() || it.points.any { p -> !validMarkupPoint(p) } } ||
        texts.any { !validMarkupPoint(it.position) || it.fontSize !in 0.01f..10000f || !it.rotation.isFinite() ||
            it.boxSize?.let { b -> !validMarkupPoint(Offset(b.width,b.height)) } == true }) return state
    return state.copy(markupStrokes = strokes, markupTexts = texts)
}
