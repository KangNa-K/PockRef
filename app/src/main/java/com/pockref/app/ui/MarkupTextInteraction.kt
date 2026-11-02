package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState

private const val MarkupTextWidthFactor = 0.58f
private const val MarkupTextLineHeightFactor = 1.18f
private const val MarkupTextMinHitSizePx = 44f
private const val MarkupTextPaddingPx = 12f

internal fun markupTextScreenBounds(
    item: CanvasMarkupText,
    centerOffset: Offset,
    canvasOffset: Offset,
    canvasScale: Float,
): Rect? {
    val lines = item.text.trim().lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    val scale = canvasScale.coerceAtLeast(0.0001f)
    val textSize = (item.fontSize * scale).coerceAtLeast(8f)
    val lineHeight = textSize * MarkupTextLineHeightFactor
    val widestLine = lines.maxOf { it.length }.coerceAtLeast(1)
    val baseWidth = (item.boxSize?.width?.times(scale) ?: (widestLine * textSize * MarkupTextWidthFactor)).coerceAtLeast(MarkupTextMinHitSizePx)
    val baseHeight = (item.boxSize?.height?.times(scale) ?: (lines.size * lineHeight)).coerceAtLeast(MarkupTextMinHitSizePx)
    val c = kotlin.math.abs(kotlin.math.cos(item.rotation)); val sin = kotlin.math.abs(kotlin.math.sin(item.rotation))
    val contentWidth = baseWidth*c + baseHeight*sin
    val contentHeight = baseWidth*sin + baseHeight*c
    val x = centerOffset.x + canvasOffset.x + item.position.x * scale
    val y = centerOffset.y + canvasOffset.y + item.position.y * scale
    return Rect(
        left = x - contentWidth / 2f - MarkupTextPaddingPx,
        top = y - contentHeight / 2f - MarkupTextPaddingPx,
        right = x + contentWidth / 2f + MarkupTextPaddingPx,
        bottom = y + contentHeight / 2f + MarkupTextPaddingPx,
    )
}

internal fun hitTestMarkupText(
    texts: List<CanvasMarkupText>,
    point: Offset,
    centerOffset: Offset,
    canvasOffset: Offset,
    canvasScale: Float,
): CanvasMarkupText? = texts.asReversed().sortedByDescending { it.zOrder }.firstOrNull { item ->
    val center = centerOffset + canvasOffset + item.position * canvasScale
    val delta = point - center
    val c = kotlin.math.cos(item.rotation); val s = kotlin.math.sin(item.rotation)
    val localPoint = center + Offset(delta.x * c + delta.y * s, -delta.x * s + delta.y * c)
    !item.locked && markupTextScreenBounds(item.copy(rotation = 0f), centerOffset, canvasOffset, canvasScale)?.contains(localPoint) == true
}

internal fun hitMarkupTextAlongSegment(item: CanvasMarkupText, from: Offset, to: Offset): Boolean {
    if (item.locked) return false
    val bounds = markupTextScreenBounds(item.copy(rotation = 0f), Offset.Zero, Offset.Zero, 1f) ?: return false
    fun local(point: Offset): Offset {
        val delta = point - item.position
        val c = kotlin.math.cos(item.rotation); val s = kotlin.math.sin(item.rotation)
        return item.position + Offset(delta.x*c + delta.y*s, -delta.x*s + delta.y*c)
    }
    val a = local(from); val b = local(to)
    if (bounds.contains(a) || bounds.contains(b)) return true
    val corners = listOf(bounds.topLeft, bounds.topRight, bounds.bottomRight, bounds.bottomLeft, bounds.topLeft)
    return corners.zipWithNext().any { (c, d) -> markupSegmentDistance(a, b, c, d) < 0.001f }
}

internal fun moveMarkupTextState(
    state: PockRefUiState,
    textId: String,
    delta: Offset,
): PockRefUiState {
    if (delta == Offset.Zero) return state
    return state.copy(
        markupTexts = state.markupTexts.map { item ->
            if (item.id == textId && !item.locked) item.copy(position = item.position + delta) else item
        },
    )
}

internal fun commitPendingMarkupTextState(state: PockRefUiState): PockRefUiState {
    val position = state.pendingMarkupTextPosition ?: return state
    val text = state.pendingMarkupTextValue.trim()
    val editingId = state.editingMarkupTextId
    if (text.isBlank()) {
        return state.copy(
            pendingMarkupTextPosition = null,
            pendingMarkupTextValue = "",
            pendingMarkupTextFontSize = 80f,
            editingMarkupTextId = null,
            bottomRightPanelExpanded = false,
        )
    }
    val fontSize = state.pendingMarkupTextFontSize.coerceIn(0.01f, 10000f)
    val texts = if (editingId != null && state.markupTexts.any { it.id == editingId }) {
        state.markupTexts.map { item ->
            if (item.id == editingId) {
                item.copy(text = text, position = position, fontSize = fontSize, boxSize = if (item.text == text && item.fontSize == fontSize) item.boxSize else null)
            } else {
                item
            }
        }
    } else {
        state.markupTexts + CanvasMarkupText(
            text = text,
            position = position,
            fontSize = fontSize,
            color = state.markupColor,
            zOrder = (state.markupStrokes.map { it.zOrder } + state.markupTexts.map { it.zOrder }).maxOrNull()?.plus(1) ?: 0,
        )
    }
    return state.copy(
        markupTexts = texts,
        markupVisible = true,
        pendingMarkupTextPosition = null,
        pendingMarkupTextValue = "",
        pendingMarkupTextFontSize = 80f,
        editingMarkupTextId = null,
        bottomRightPanelExpanded = false,
    )
}
