package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.toScene
import org.junit.Assert.*
import org.junit.Test

class MarkupGeometryTest {
    @Test fun fastEraseIntersectsStrokeBetweenPointerSamples() {
        val stroke = CanvasMarkupStroke(points = listOf(Offset(0f,-50f),Offset(0f,50f)))
        assertFalse(hitMarkupStroke(stroke, Offset(-100f,0f), 1f))
        assertFalse(hitMarkupStroke(stroke, Offset(100f,0f), 1f))
        assertTrue(hitMarkupStrokeAlongSegment(stroke, Offset(-100f,0f), Offset(100f,0f), 1f))
        assertFalse(hitMarkupStrokeAlongSegment(stroke.copy(locked=true), Offset(-100f,0f), Offset(100f,0f), 1f))
    }
    @Test fun singlePointStrokeCanBeSelected() {
        val stroke = CanvasMarkupStroke(points = listOf(Offset(20f,30f)))
        assertTrue(stroke.isRenderable)
        assertTrue(hitMarkupStroke(stroke, Offset(20f,30f), 1f))
        assertFalse(hitMarkupStroke(stroke, Offset.Zero, 1f))
    }
    @Test fun rectangleUsesEdgesInsteadOfDiagonal() {
        val stroke = CanvasMarkupStroke(kind="rectangle",points=listOf(Offset.Zero,Offset(100f,50f)))
        assertTrue(hitMarkupStroke(stroke,Offset(50f,0f),1f))
        assertFalse(hitMarkupStroke(stroke,Offset(50f,25f),1f))
    }
    @Test fun rotatedLineHitTestMatchesRendering() {
        val stroke = CanvasMarkupStroke(kind="line",points=listOf(Offset.Zero,Offset(100f,0f)),rotation=(Math.PI/2).toFloat())
        assertTrue(hitMarkupStroke(stroke,Offset(50f,40f),1f))
        assertFalse(hitMarkupStroke(stroke,Offset(90f,0f),1f))
        assertFalse(hitMarkupStroke(stroke.copy(locked=true),Offset(50f,40f),1f))
    }
    @Test fun arrowHasSeparateHeadSegments() {
        val stroke = CanvasMarkupStroke(kind="arrow",points=listOf(Offset.Zero,Offset(100f,0f)))
        assertEquals(2,markupPolylines(stroke).size)
    }
    @Test fun autosaveOmitsActiveSessionDraftButFinishedSceneIncludesIt() {
        val stroke = CanvasMarkupStroke(points=listOf(Offset.Zero,Offset(10f,10f)))
        val state = PockRefUiState(markupModeActive=true,markupSessionBaseStrokes=emptyList(),
            markupSessionBaseTexts=emptyList(),markupStrokes=listOf(stroke))
        assertTrue(state.toScene().markupStrokes.isEmpty())
        assertEquals(1,state.copy(markupModeActive=false).toScene().markupStrokes.size)
    }
}
