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
    val contentWidth = (widestLine * textSize * MarkupTextWidthFactor).coerceAtLeast(MarkupTextMinHitSizePx)
    val contentHeight = (lines.size * lineHeight).coerceAtLeast(MarkupTextMinHitSizePx)
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
): CanvasMarkupText? = texts.asReversed().firstOrNull { item ->
    markupTextScreenBounds(item, centerOffset, canvasOffset, canvasScale)?.contains(point) == true
}

internal fun moveMarkupTextState(
    state: PockRefUiState,
    textId: String,
    delta: Offset,
): PockRefUiState {
    if (delta == Offset.Zero) return state
    return state.copy(
        markupTexts = state.markupTexts.map { item ->
            if (item.id == textId) item.copy(position = item.position + delta) else item
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
    val fontSize = state.pendingMarkupTextFontSize.coerceIn(24f, 240f)
    val texts = if (editingId != null && state.markupTexts.any { it.id == editingId }) {
        state.markupTexts.map { item ->
            if (item.id == editingId) {
                item.copy(text = text, position = position, fontSize = fontSize)
            } else {
                item
            }
        }
    } else {
        state.markupTexts + CanvasMarkupText(
            text = text,
            position = position,
            fontSize = fontSize,
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
