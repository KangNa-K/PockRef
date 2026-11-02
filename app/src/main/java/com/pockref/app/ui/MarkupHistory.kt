package com.pockref.app.ui

import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState

internal data class MarkupHistorySnapshot(
    val strokes: List<CanvasMarkupStroke>,
    val texts: List<CanvasMarkupText>,
)

internal fun PockRefUiState.toMarkupHistorySnapshot(): MarkupHistorySnapshot = MarkupHistorySnapshot(
    strokes = markupStrokes,
    texts = markupTexts,
)

internal fun PockRefUiState.restoreMarkupHistorySnapshot(snapshot: MarkupHistorySnapshot): PockRefUiState = copy(
    markupStrokes = snapshot.strokes,
    markupTexts = snapshot.texts,
    draftMarkupStroke = null,
    pendingMarkupTextPosition = null,
    pendingMarkupTextValue = "",
    pendingMarkupTextFontSize = 80f,
    editingMarkupTextId = null,
    selectedMarkupId = null,
    selectedMarkupIds = emptySet(),
    markupLassoPoints = emptyList(),
)

internal class MarkupHistory {
    private val undoStack: ArrayDeque<MarkupHistorySnapshot> = ArrayDeque()
    private val redoStack: ArrayDeque<MarkupHistorySnapshot> = ArrayDeque()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun record(snapshot: MarkupHistorySnapshot) {
        if (undoStack.lastOrNull() == snapshot) return
        undoStack.addLast(snapshot)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: MarkupHistorySnapshot): MarkupHistorySnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        if (redoStack.size > 50) redoStack.removeFirst()
        return previous
    }

    fun redo(current: MarkupHistorySnapshot): MarkupHistorySnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        if (undoStack.size > 50) undoStack.removeFirst()
        return next
    }
}
