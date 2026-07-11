package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasZoomBoundsTest {

    @Test
    fun canvasZoom_allowsDeepInspectionBeyondTenTimes() {
        assertEquals(40f, MAX_CANVAS_SCALE, 0f)
        assertEquals(40f, 100f.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE), 0f)
    }
}
