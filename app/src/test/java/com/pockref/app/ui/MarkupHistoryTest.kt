package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupHistoryTest {
    @Test
    fun undoAndRedoRestoreOnlyMarkupState() {
        val history = MarkupHistory()
        val before = PockRefUiState(markupModeActive = true)
        val after = before.copy(
            markupStrokes = listOf(
                CanvasMarkupStroke(points = listOf(Offset.Zero, Offset(1f, 1f))),
            ),
            markupTexts = listOf(CanvasMarkupText(text = "note", position = Offset(2f, 3f))),
        )

        history.record(before.toMarkupHistorySnapshot())
        val undone = after.restoreMarkupHistorySnapshot(history.undo(after.toMarkupHistorySnapshot())!!)

        assertTrue(history.canRedo)
        assertEquals(emptyList<CanvasMarkupStroke>(), undone.markupStrokes)
        assertEquals(emptyList<CanvasMarkupText>(), undone.markupTexts)

        val redone = undone.restoreMarkupHistorySnapshot(history.redo(undone.toMarkupHistorySnapshot())!!)

        assertFalse(history.canRedo)
        assertEquals(after.markupStrokes, redone.markupStrokes)
        assertEquals(after.markupTexts, redone.markupTexts)
    }

    @Test
    fun recordClearsRedoStack() {
        val history = MarkupHistory()
        val first = PockRefUiState(markupModeActive = true)
        val second = first.copy(markupTexts = listOf(CanvasMarkupText(text = "a", position = Offset.Zero)))

        history.record(first.toMarkupHistorySnapshot())
        history.undo(second.toMarkupHistorySnapshot())
        assertTrue(history.canRedo)

        history.record(second.toMarkupHistorySnapshot())

        assertFalse(history.canRedo)
    }
}
