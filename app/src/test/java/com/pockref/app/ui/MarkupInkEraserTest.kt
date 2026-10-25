package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import org.junit.Assert.*
import org.junit.Test

class MarkupInkEraserTest {
    private val ink = CanvasMarkupStroke(id="source",points=listOf(Offset(-100f,0f),Offset(100f,0f)),lineWidth=4f)

    @Test fun sweptEraseSplitsSparseInkWithoutDeletingWholeStroke() {
        val fragments = eraseMarkupInk(ink,Offset(0f,-50f),Offset(0f,50f),10f)
        assertEquals(2,fragments.size)
        assertEquals("source",fragments.first().id)
        assertNotEquals(fragments.first().id,fragments.last().id)
        assertEquals(-12f,fragments.first().points.last().x,0.001f)
        assertEquals(12f,fragments.last().points.first().x,0.001f)
        assertEquals(ink.points.first(),fragments.first().points.first())
        assertEquals(ink.points.last(),fragments.last().points.last())
    }

    @Test fun pointEraserClipsCircleAndFarEraserLeavesIdentityUntouched() {
        assertEquals(2,eraseMarkupInk(ink,Offset.Zero,Offset.Zero,10f).size)
        assertEquals(listOf(ink),eraseMarkupInk(ink,Offset(0f,200f),Offset(0f,300f),10f))
    }

    @Test fun lockedShapesAndTextAreNotConvertedToInk() {
        for (stroke in listOf(ink.copy(locked=true),ink.copy(kind="rectangle"),ink.copy(kind="arrow"))) {
            assertEquals(listOf(stroke),eraseMarkupInk(stroke,Offset.Zero,Offset.Zero,10f))
        }
    }

    @Test fun rotatedInkUsesWorldGeometryAndRetainsOpacity() {
        val fragments = eraseMarkupInk(ink.copy(rotation=(Math.PI/2).toFloat(),opacity=0.35f),
            Offset(-50f,0f),Offset(50f,0f),10f)
        assertEquals(2,fragments.size)
        assertEquals(-100f,fragments.first().points.first().y,0.001f)
        assertTrue(fragments.all { it.rotation == 0f && it.opacity == 0.35f })
    }

    @Test fun singlePointInkCanBeErasedCompletely() {
        assertTrue(eraseMarkupInk(ink.copy(points=listOf(Offset.Zero)),Offset.Zero,Offset.Zero,10f).isEmpty())
    }
}
