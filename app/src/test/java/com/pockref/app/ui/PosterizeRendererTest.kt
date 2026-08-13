package com.pockref.app.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizeRendererTest {
    @Test
    fun cacheKey_changesWhenPosterizeInputsChange() {
        val base = PosterizeCacheKey(
            imageId = "image",
            sourceToken = "source-v1",
            decodeLevel = 2,
            tileToken = null,
            levels = 2,
            brightnessBits = 0f.toBits(),
            contrastBits = 0f.toBits(),
            saturationBits = (-1f).toBits(),
        )

        assertNotEquals(base, base.copy(levels = 4))
        assertNotEquals(base, base.copy(tileToken = "0:1"))
        assertNotEquals(base, base.copy(sourceToken = "source-v2"))
    }

    @Test
    fun rendererAcceptsOnlySupportedLevels() {
        assertTrue(PosterizeRenderer.isSupported(2))
        assertTrue(PosterizeRenderer.isSupported(4))
        assertTrue(PosterizeRenderer.isSupported(8))
        assertTrue(PosterizeRenderer.isSupported(16))
    }

    @Test
    fun sourceToCanvasMapping_mapsFullSourceToFullDestination() {
        val mapping = posterizeSourceToCanvasMapping(
            sourceLeft = 0f,
            sourceTop = 0f,
            sourceWidth = 100f,
            sourceHeight = 50f,
            destinationLeft = 20f,
            destinationTop = 30f,
            destinationWidth = 400f,
            destinationHeight = 200f,
        )

        assertEquals(4f, mapping.scaleX, 0.0001f)
        assertEquals(4f, mapping.scaleY, 0.0001f)
        assertEquals(20f, mapping.translateX, 0.0001f)
        assertEquals(30f, mapping.translateY, 0.0001f)
        assertEquals(20f, 0f * mapping.scaleX + mapping.translateX, 0.0001f)
        assertEquals(420f, 100f * mapping.scaleX + mapping.translateX, 0.0001f)
        assertEquals(30f, 0f * mapping.scaleY + mapping.translateY, 0.0001f)
        assertEquals(230f, 50f * mapping.scaleY + mapping.translateY, 0.0001f)
    }

    @Test
    fun sourceToCanvasMapping_mapsCroppedSourceToEntireDestination() {
        val mapping = posterizeSourceToCanvasMapping(
            sourceLeft = 25f,
            sourceTop = 10f,
            sourceWidth = 50f,
            sourceHeight = 30f,
            destinationLeft = 0f,
            destinationTop = 0f,
            destinationWidth = 200f,
            destinationHeight = 120f,
        )

        assertEquals(4f, mapping.scaleX, 0.0001f)
        assertEquals(4f, mapping.scaleY, 0.0001f)
        assertEquals(-100f, mapping.translateX, 0.0001f)
        assertEquals(-40f, mapping.translateY, 0.0001f)
        assertEquals(0f, 25f * mapping.scaleX + mapping.translateX, 0.0001f)
        assertEquals(200f, 75f * mapping.scaleX + mapping.translateX, 0.0001f)
        assertEquals(0f, 10f * mapping.scaleY + mapping.translateY, 0.0001f)
        assertEquals(120f, 40f * mapping.scaleY + mapping.translateY, 0.0001f)
    }
}
