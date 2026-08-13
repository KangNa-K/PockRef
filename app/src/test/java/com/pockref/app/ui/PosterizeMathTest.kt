package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterizeMathTest {
    private val epsilon = 1e-4f

    @Test
    fun quantizeLuminance_twoLevels_hasOnlyBlackAndWhite() {
        assertEquals(0f, quantizeLuminance(0.24f, 2), epsilon)
        assertEquals(1f, quantizeLuminance(0.75f, 2), epsilon)
    }

    @Test
    fun quantizeLuminance_usesNearestLevel() {
        assertEquals(1f / 3f, quantizeLuminance(0.34f, 4), epsilon)
        assertEquals(2f / 3f, quantizeLuminance(0.66f, 4), epsilon)
    }

    @Test
    fun quantizeLuminance_rejectsUnsupportedLevels() {
        assertEquals(0.5f, quantizeLuminance(0.5f, 3), epsilon)
    }

    @Test
    fun posterizeArgb_preservesAlpha() {
        val result = posterizeArgb(0x80404040.toInt(), 2)
        assertEquals(0x80, (result ushr 24) and 0xFF)
    }

    @Test
    fun posterizeArgb_twoLevels_producesOnlyBlackOrWhiteRgb() {
        val dark = posterizeArgb(0xFF303030.toInt(), 2)
        val light = posterizeArgb(0xFFDDDDDD.toInt(), 2)
        assertEquals(0xFF000000.toInt(), dark)
        assertEquals(0xFFFFFFFF.toInt(), light)
    }
}
