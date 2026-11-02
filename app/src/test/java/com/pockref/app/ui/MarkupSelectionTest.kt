package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.*
import org.junit.Assert.*
import org.junit.Test

class MarkupSelectionTest {
    @Test fun reusePreservesOriginalsAndCopiesOnlyUnlockedSelection() {
        val stroke = CanvasMarkupStroke(id = "ink", points = listOf(Offset.Zero, Offset(40f, 20f)),
            rotation = 0.3f, zOrder = 4, sourceJson = "{\"custom\":true}")
        val text = CanvasMarkupText(id = "text", text = "备注", position = Offset(60f, 80f), zOrder = 8)
        val locked = text.copy(id = "locked", locked = true)
        val state = PockRefUiState(canvasScale = 2f, markupStrokes = listOf(stroke),
            markupTexts = listOf(text, locked), selectedMarkupIds = setOf("ink", "text", "locked"))
        val result = duplicateMarkupSelection(state)
        assertEquals(stroke, result.markupStrokes.first())
        assertEquals(listOf(text, locked), result.markupTexts.take(2))
        val copiedStroke = result.markupStrokes.last()
        val copiedText = result.markupTexts.last()
        assertEquals(stroke.points.map { it + Offset(12f, 12f) }, copiedStroke.points)
        assertEquals(text.position + Offset(12f, 12f), copiedText.position)
        assertEquals(stroke.rotation, copiedStroke.rotation)
        assertEquals(stroke.sourceJson, copiedStroke.sourceJson)
        assertEquals(setOf(copiedStroke.id, copiedText.id), result.markupSelection)
        assertEquals(5, (result.markupStrokes.map { it.id } + result.markupTexts.map { it.id }).toSet().size)
        assertTrue(copiedStroke.zOrder > text.zOrder)
        assertTrue(copiedText.zOrder > copiedStroke.zOrder)
    }

    @Test fun reuseWithoutEditableSelectionDoesNothing() {
        val state = PockRefUiState(markupTexts = listOf(CanvasMarkupText(id = "locked", text = "备注",
            position = Offset.Zero, locked = true)), selectedMarkupIds = setOf("locked"))
        assertEquals(state, duplicateMarkupSelection(state))
        assertEquals(PockRefUiState(), duplicateMarkupSelection(PockRefUiState()))
    }

    @Test fun handlesTransformFromOriginalGeometryWithoutAccumulatingDelta() {
        val state = PockRefUiState(markupStrokes = listOf(CanvasMarkupStroke(id="shape", kind="rectangle",
            points=listOf(Offset(-50f,-25f),Offset(50f,25f)))), selectedMarkupIds=setOf("shape"))
        val handles = markupHandlePositions(state)
        val scaleStart = handles.getValue(MarkupHandle.SCALE)
        assertEquals(MarkupHandle.SCALE,hitMarkupHandle(state,scaleStart))
        assertNull(hitMarkupHandle(state,Offset(500f,500f)))
        val scaled = dragMarkupHandle(state,MarkupHandle.SCALE,scaleStart,scaleStart*2f)
        assertEquals(Offset(100f,50f),scaled.markupStrokes.single().points.last())
        val rotateStart = handles.getValue(MarkupHandle.ROTATE)
        val rotated = dragMarkupHandle(state,MarkupHandle.ROTATE,rotateStart,Offset(-rotateStart.y,rotateStart.x))
        assertEquals((Math.PI/2).toFloat(),rotated.markupStrokes.single().rotation,0.001f)
        assertEquals("shape",rotated.markupStrokes.single().id)
    }
    @Test fun lassoSelectsMixedObjectsButExcludesLockedAndOutsideObjects() {
        val state = PockRefUiState(markupStrokes = listOf(
            CanvasMarkupStroke(id = "inside", points = listOf(Offset.Zero, Offset(20f, 20f))),
            CanvasMarkupStroke(id = "locked", points = listOf(Offset.Zero), locked = true),
            CanvasMarkupStroke(id = "outside", points = listOf(Offset(300f,300f)))),
            markupTexts = listOf(CanvasMarkupText(id = "text", text = "中文", position = Offset(30f, 30f), fontSize = 18f)))
        val polygon = listOf(Offset(-100f,-100f),Offset(100f,-100f),Offset(100f,100f),Offset(-100f,100f))
        assertEquals(setOf("inside", "text"), selectMarkupInLasso(state, polygon))
        assertTrue(selectMarkupInLasso(state, polygon.take(2)).isEmpty())
    }

    @Test fun lassoCatchesSparseSegmentsCrossingPolygon() {
        val state = PockRefUiState(markupStrokes = listOf(CanvasMarkupStroke(id="crossing",
            points=listOf(Offset(-200f,0f),Offset(200f,0f)))))
        assertEquals(setOf("crossing"), selectMarkupInLasso(state,
            listOf(Offset(-50f,-50f),Offset(50f,-50f),Offset(50f,50f),Offset(-50f,50f))))
    }

    @Test fun groupRotationMovesCentersAndRetainsStableIDs() {
        val state = PockRefUiState(markupStrokes = listOf(
            CanvasMarkupStroke(id="a",points=listOf(Offset(-100f,0f))),
            CanvasMarkupStroke(id="b",points=listOf(Offset(100f,0f)))),selectedMarkupIds=setOf("a","b"))
        val result = transformMarkupSelection(state, 2f, (Math.PI/2).toFloat())
        assertEquals(listOf("a","b"), result.markupStrokes.map { it.id })
        assertEquals(-200f,result.markupStrokes.first().points.first().y,0.001f)
        assertEquals(200f,result.markupStrokes.last().points.first().y,0.001f)
        assertEquals(state,transformMarkupSelection(state,Float.MAX_VALUE,0f))
        assertEquals(state,transformMarkupSelection(state,1f,Float.NaN))
    }
}
