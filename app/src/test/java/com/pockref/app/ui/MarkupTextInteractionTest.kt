package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkupTextInteractionTest {
    @Test
    fun hitTestMarkupText_returnsTopmostTextAtScreenPoint() {
        val back = CanvasMarkupText(
            id = "back",
            text = "Back",
            position = Offset.Zero,
            fontSize = 40f,
        )
        val front = CanvasMarkupText(
            id = "front",
            text = "Front",
            position = Offset.Zero,
            fontSize = 40f,
        )

        val hit = hitTestMarkupText(
            texts = listOf(back, front),
            point = Offset(100f, 80f),
            centerOffset = Offset(100f, 80f),
            canvasOffset = Offset.Zero,
            canvasScale = 1f,
        )

        assertEquals("front", hit?.id)
        assertNull(
            hitTestMarkupText(
                texts = listOf(back, front),
                point = Offset(260f, 80f),
                centerOffset = Offset(100f, 80f),
                canvasOffset = Offset.Zero,
                canvasScale = 1f,
            ),
        )
    }

    @Test
    fun commitPendingMarkupText_updatesExistingTextWhenEditing() {
        val existing = CanvasMarkupText(
            id = "note-1",
            text = "old",
            position = Offset(12f, 34f),
            fontSize = 48f,
        )
        val state = PockRefUiState(
            markupTexts = listOf(existing),
            markupVisible = false,
            pendingMarkupTextPosition = existing.position,
            pendingMarkupTextValue = "new",
            pendingMarkupTextFontSize = 96f,
            editingMarkupTextId = existing.id,
        )

        val updated = commitPendingMarkupTextState(state)

        assertEquals(1, updated.markupTexts.size)
        assertEquals("new", updated.markupTexts.single().text)
        assertEquals(96f, updated.markupTexts.single().fontSize, 0f)
        assertEquals(true, updated.markupVisible)
        assertNull(updated.editingMarkupTextId)
        assertNull(updated.pendingMarkupTextPosition)
    }

    @Test
    fun moveMarkupTextState_movesOnlyTheTargetText() {
        val target = CanvasMarkupText(
            id = "target",
            text = "move me",
            position = Offset(10f, 20f),
        )
        val other = CanvasMarkupText(
            id = "other",
            text = "stay",
            position = Offset(100f, 200f),
        )
        val state = PockRefUiState(markupTexts = listOf(target, other))

        val updated = moveMarkupTextState(state, "target", Offset(3f, -4f))

        assertEquals(Offset(13f, 16f), updated.markupTexts.first { it.id == "target" }.position)
        assertEquals(Offset(100f, 200f), updated.markupTexts.first { it.id == "other" }.position)
    }
}
