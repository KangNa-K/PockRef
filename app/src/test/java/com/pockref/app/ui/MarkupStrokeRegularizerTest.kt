package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupStrokeRegularizerTest {

    @Test
    fun regularize_nearlyStraightStroke_becomesLine() {
        val points = (0..20).map { index ->
            val x = index * 5f
            Offset(x, if (index % 2 == 0) 0.8f else -0.8f)
        }

        val regularized = regularizeMarkupStrokeForCommit(
            CanvasMarkupStroke(lineWidth = 4f, points = points),
        )

        assertEquals(2, regularized.points.size)
        assertEquals(points.first(), regularized.points.first())
        assertEquals(points.last(), regularized.points.last())
    }

    @Test
    fun regularize_rectangleStroke_closesToFourCorners() {
        val points = buildList {
            for (x in 0..100 step 10) add(Offset(x.toFloat(), 0f))
            for (y in 10..60 step 10) add(Offset(100f, y.toFloat()))
            for (x in 90 downTo 0 step 10) add(Offset(x.toFloat(), 60f))
            for (y in 50 downTo 0 step 10) add(Offset(0f, y.toFloat()))
        }

        val regularized = regularizeMarkupStrokeForCommit(
            CanvasMarkupStroke(lineWidth = 4f, points = points),
        )

        assertEquals(5, regularized.points.size)
        assertEquals(regularized.points.first(), regularized.points.last())
    }

    @Test
    fun regularize_freehandStroke_smoothsWithoutShapeSnap() {
        val points = listOf(
            Offset(0f, 0f),
            Offset(8f, 12f),
            Offset(16f, -10f),
            Offset(24f, 14f),
            Offset(32f, -12f),
            Offset(40f, 8f),
            Offset(48f, 0f),
        )

        val regularized = regularizeMarkupStrokeForCommit(
            CanvasMarkupStroke(lineWidth = 4f, points = points),
        )

        assertTrue(regularized.points.size > 2)
        assertEquals(points.first(), regularized.points.first())
        assertEquals(points.last(), regularized.points.last())
        assertTrue(regularized.points[1].y < points[1].y)
    }
}
