package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasPanGestureTest {

    @Test
    fun singlePointerPanDelta_isIgnored() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 1,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
        )

        assertEquals(Offset.Zero, delta)
    }

    @Test
    fun twoPointerPanDelta_tracksCentroidMovement() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 2,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
        )

        assertEquals(Offset(15f, 25f), delta)
    }

    @Test
    fun threePointerPanDelta_isIgnored() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 3,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
        )

        assertEquals(Offset.Zero, delta)
    }
}
