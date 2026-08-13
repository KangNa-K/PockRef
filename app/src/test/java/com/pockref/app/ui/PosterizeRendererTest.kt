package com.pockref.app.ui

import org.junit.Assert.assertNotEquals
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
}
