package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeImageDecodeSizeTest {

    @Test
    fun unmeasuredWindow_skipsDecode() {
        assertNull(practiceImageDecodeSide(0, 0))
        assertNull(practiceImageDecodeSide(2800, 0))
        assertNull(practiceImageDecodeSide(0, 1752))
    }

    @Test
    fun tabletWindowInPx_usesTheLargestRung() {
        // SM-X810 landscape is about 2800×1752 px (1317 dp wide). The old dp
        // input picked 2048; the px long side needs 4096.
        assertEquals(4096, practiceImageDecodeSide(2800, 1752))
        assertEquals(4096, practiceImageDecodeSide(1752, 2800))
        assertEquals(4096, practiceImageDecodeSide(2560, 1600))
    }

    @Test
    fun rungBoundaries_coverTheLongSide() {
        assertEquals(2048, practiceImageDecodeSide(2048, 1))
        assertEquals(4096, practiceImageDecodeSide(2049, 1))
        assertEquals(1024, practiceImageDecodeSide(900, 1000))
        assertEquals(128, practiceImageDecodeSide(1, 1))
    }

    @Test
    fun oversizedWindow_isCappedAtTheLargestRung() {
        assertEquals(4096, practiceImageDecodeSide(7680, 4320))
    }

    @Test
    fun resizeWithinOneRung_keepsTheSameDecodeSide() {
        // Floating-window drag-resize should not re-decode per pixel.
        val sides = (1100..2000 step 37).map { practiceImageDecodeSide(it, 800) }.toSet()
        assertEquals(setOf(2048), sides)
    }
}
