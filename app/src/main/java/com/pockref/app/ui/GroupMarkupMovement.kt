package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.PockRefUiState

/** Capture membership before a move so passing over other annotations cannot pick them up. */
internal fun PockRefUiState.markupIdsForGroupMove(imageIds: Set<String>): Set<String> {
    if (editingGroupId != null || imageIds.isEmpty()) return emptySet()
    val bounds = groups.filter { group ->
        !group.positionLocked && group.imageIds.isNotEmpty() && group.imageIds.all { it in imageIds }
    }.mapNotNull { group ->
        group.canvasBounds(images, padding = 12f)?.let { Rect(it.left, it.top, it.right, it.bottom) }
    }
    return markupIdsInsideGroupBounds(this, bounds)
}

internal fun markupIdsInsideGroupBounds(state: PockRefUiState, bounds: List<Rect>): Set<String> {
    if (bounds.isEmpty()) return emptySet()
    fun contained(points: List<Offset>): Boolean = points.isNotEmpty() && bounds.any { rect ->
        points.all { it.x in rect.left..rect.right && it.y in rect.top..rect.bottom }
    }
    return buildSet {
        state.markupStrokes.forEach { stroke ->
            if (!stroke.locked && contained(markupPolylines(stroke).flatten())) add(stroke.id)
        }
        state.markupTexts.forEach { text ->
            if (!text.locked && contained(markupTextDrawingCorners(text))) add(text.id)
        }
    }
}

/** Apply the same world-space translation as the images, without changing style or identity. */
internal fun PockRefUiState.moveGroupMarkup(ids: Set<String>, delta: Offset): PockRefUiState {
    if (ids.isEmpty() || delta == Offset.Zero) return this
    return copy(
        markupStrokes = markupStrokes.map { stroke ->
            if (stroke.id in ids && !stroke.locked) {
                stroke.copy(points = stroke.points.map { it + delta })
            } else stroke
        },
        markupTexts = markupTexts.map { text ->
            if (text.id in ids && !text.locked) text.copy(position = text.position + delta) else text
        },
    )
}
