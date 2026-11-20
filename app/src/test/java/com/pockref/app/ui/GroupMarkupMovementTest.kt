package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMarkupMovementTest {
    private val bounds = Rect(-100f, -100f, 100f, 100f)
    private val note = CanvasMarkupText(id = "note", text = "便签", position = Offset.Zero,
        fontSize = 12f, boxSize = Size2D(20f, 20f))
    private fun stroke(id: String, kind: String = "pen", x: Float = 0f) = CanvasMarkupStroke(
        id = id, kind = kind, points = listOf(Offset(x - 10f, -10f), Offset(x + 10f, 10f)),
    )

    @Test fun enclosedNotesDrawingsAndEveryShapeFollowTheGroup() {
        val kinds = listOf("pen", "highlighter", "line", "arrow", "rectangle", "ellipse")
        val state = PockRefUiState(
            markupTexts = listOf(note),
            markupStrokes = kinds.map { stroke(it, it) },
        )
        val ids = markupIdsInsideGroupBounds(state, listOf(bounds))
        assertEquals(kinds.toSet() + note.id, ids)
        val delta = Offset(50f, -30f)
        val moved = state.moveGroupMarkup(ids, delta)
        assertEquals(note.copy(position = delta), moved.markupTexts.single())
        state.markupStrokes.zip(moved.markupStrokes).forEach { (before, after) ->
            assertEquals(before.copy(points = before.points.map { it + delta }), after)
        }
    }

    @Test fun smallNoteInsideSmallGroupMovesDespiteItsLargerTouchTarget() {
        val frame = Rect(-32f, -32f, 32f, 32f)
        val state = PockRefUiState(markupTexts = listOf(note))
        assertTrue(markupTextCorners(note).any { !frame.contains(it) })
        val ids = markupIdsInsideGroupBounds(state, listOf(frame))
        assertEquals(setOf(note.id), ids)
        assertEquals(Offset(50f, 30f), state.moveGroupMarkup(ids, Offset(50f, 30f)).markupTexts.single().position)
    }

    @Test fun noteAtGroupEdgeIsIncludedButCrossingNoteIsExcluded() {
        val edge = note.copy(position = Offset(90f, 0f))
        val crossing = note.copy(id = "crossing", position = Offset(91f, 0f))
        val state = PockRefUiState(markupTexts = listOf(edge, crossing))
        assertEquals(setOf(edge.id), markupIdsInsideGroupBounds(state, listOf(bounds)))
    }

    @Test fun rotatedNoteUsesItsDrawingCornersWithoutTouchPadding() {
        val rotated = note.copy(boxSize = Size2D(80f, 20f), rotation = (Math.PI / 2).toFloat())
        val state = PockRefUiState(markupTexts = listOf(rotated))
        assertEquals(setOf(note.id), markupIdsInsideGroupBounds(state, listOf(Rect(-11f, -41f, 11f, 41f))))
        assertTrue(markupIdsInsideGroupBounds(state, listOf(Rect(-41f, -11f, 41f, 11f))).isEmpty())
    }

    @Test fun unboxedTextGeometryUsesMeasuredLayoutSize() {
        val unboxed = note.copy(text = "第一行\n第二行", boxSize = null, position = Offset(5f, 10f))
        assertEquals(listOf(Offset(-35f, -20f), Offset(45f, -20f), Offset(45f, 40f), Offset(-35f, 40f)),
            markupTextDrawingCorners(unboxed, size = Size2D(80f, 60f)))
        assertTrue(markupTextDrawingCorners(unboxed.copy(text = ""), size = Size2D(80f, 60f)).isEmpty())
    }

    @Test fun outsideCrossingAndLockedMarkupStayInPlace() {
        val inside = stroke("inside")
        val outside = stroke("outside", x = 200f)
        val crossing = stroke("crossing").copy(points = listOf(Offset.Zero, Offset(200f, 0f)))
        val locked = stroke("locked").copy(locked = true)
        val state = PockRefUiState(
            markupStrokes = listOf(inside, outside, crossing, locked),
            markupTexts = listOf(note.copy(locked = true), note.copy(id = "outside-note", position = Offset(200f, 0f))),
        )
        val ids = markupIdsInsideGroupBounds(state, listOf(bounds))
        assertEquals(setOf(inside.id), ids)
        val moved = state.moveGroupMarkup(ids, Offset(20f, 30f))
        assertEquals(state.markupStrokes.drop(1), moved.markupStrokes.drop(1))
        assertEquals(state.markupTexts, moved.markupTexts)
    }

    @Test fun membershipUsesRotatedGeometry() {
        val line = stroke("rotated", "line").copy(
            points = listOf(Offset(-80f, 0f), Offset(80f, 0f)), rotation = (Math.PI / 2).toFloat(),
        )
        val state = PockRefUiState(markupStrokes = listOf(line))
        assertTrue(markupIdsInsideGroupBounds(state, listOf(Rect(-100f, -20f, 100f, 20f))).isEmpty())
        assertEquals(setOf(line.id), markupIdsInsideGroupBounds(state, listOf(bounds)))
    }

    @Test fun overlappingGroupsTranslateSharedMarkupOnceAndDoNotPickUpPassingContent() {
        val state = PockRefUiState(markupTexts = listOf(note, note.copy(id = "other", position = Offset(300f, 0f))))
        val ids = markupIdsInsideGroupBounds(state, listOf(bounds, bounds))
        val delta = Offset(300f, 0f)
        val moved = state.moveGroupMarkup(ids, delta)
        assertEquals(delta, moved.markupTexts.first().position)
        assertEquals(state.markupTexts.last(), moved.markupTexts.last())
        assertEquals(state, moved.moveGroupMarkup(ids, -delta))
    }

    @Test fun noMovementAndNewlyLockedMarkupAreUnchanged() {
        val state = PockRefUiState(markupTexts = listOf(note.copy(locked = true)))
        assertEquals(state, state.moveGroupMarkup(setOf(note.id), Offset(20f, 30f)))
        assertEquals(state, state.moveGroupMarkup(setOf(note.id), Offset.Zero))
        assertEquals(state, state.moveGroupMarkup(emptySet(), Offset(20f, 30f)))
        assertTrue(state.copy(editingGroupId = "editing").markupIdsForGroupMove(setOf("image")).isEmpty())
    }

    @Test fun movingImagesAndMarkupUsesOneCompleteHistorySnapshot() {
        val image = SceneImage(
            id = "image", name = "Image", mimeType = "image/png", intrinsicWidth = 200,
            intrinsicHeight = 200, center = Offset.Zero, displaySize = Size2D(200f, 200f),
        )
        val before = PockRefUiState(images = listOf(image), markupTexts = listOf(note),
            markupStrokes = listOf(stroke("drawing")))
        val delta = Offset(30f, 60f)
        val after = before.moveGroupMarkup(setOf(note.id, "drawing"), delta)
            .copy(images = listOf(image.copy(center = delta)))
        // Also cover non-checkpoint entries: a combined move must not become an image-only diff.
        val entry = buildHistoryEntry(before, after, stackSize = 1)
        assertEquals(before.toHistoryEntry(), entry)
        assertEquals(after.toHistoryEntry(), buildHistoryEntry(after, before, stackSize = 2))
    }
}
