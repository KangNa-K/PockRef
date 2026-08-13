package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCanvasMemoryPolicyTest {

    @Test
    fun sampleSizeKeepsDecodedDimensionsWithinTwoTimesTarget() {
        val sample = calculateMemorySafeSampleSize(
            width = 8000,
            height = 6000,
            targetWidth = 2048,
            targetHeight = 1536,
        )

        assertEquals(2, sample)
        assertTrue(8000 / sample <= 4096)
        assertTrue(6000 / sample <= 3072)
    }

    @Test
    fun sampleSizeUsesMoreAggressiveSamplingForVeryLargeSource() {
        val sample = calculateMemorySafeSampleSize(
            width = 16000,
            height = 12000,
            targetWidth = 1024,
            targetHeight = 768,
        )

        assertEquals(8, sample)
        assertTrue(16000 / sample <= 2048)
        assertTrue(12000 / sample <= 1536)
    }

    @Test
    fun safeCanvasDimensionStaysWithinPolicyBounds() {
        val highHeapDim = selectedCanvasSafeMaxDim(
            maxMemoryBytes = 512L * 1024L * 1024L,
            usedHeapBytes = 64L * 1024L * 1024L,
        )
        assertTrue(highHeapDim in 7000..8192)
        val mediumHeapDim = selectedCanvasSafeMaxDim(
            maxMemoryBytes = 128L * 1024L * 1024L,
            usedHeapBytes = 80L * 1024L * 1024L,
        )
        assertTrue(mediumHeapDim in 2500..2700)
        assertEquals(
            4096,
            selectedCanvasSourceTargetDim(
                maxMemoryBytes = 512L * 1024L * 1024L,
                usedHeapBytes = 64L * 1024L * 1024L,
            ),
        )
        assertEquals(
            2048,
            selectedCanvasSourceTargetDim(
                maxMemoryBytes = 128L * 1024L * 1024L,
                usedHeapBytes = 80L * 1024L * 1024L,
            ),
        )
    }
}
