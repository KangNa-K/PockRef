package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasPanFrameCoalescerTest {

    @Test
    fun enqueueThenFlush_coalescesIntoOneDelta() {
        val coalescer = CanvasPanFrameCoalescer()

        coalescer.enqueue(Offset(2f, -1f))
        coalescer.enqueue(Offset(3f, 4f))

        assertEquals(Offset(5f, 3f), coalescer.flush())
    }

    @Test
    fun flush_resetsPendingDeltaToZero() {
        val coalescer = CanvasPanFrameCoalescer()

        coalescer.enqueue(Offset(2f, -1f))
        coalescer.flush()

        assertEquals(Offset.Zero, coalescer.flush())
    }

    @Test
    fun enqueueAfterFlush_startsNewBatch() {
        val coalescer = CanvasPanFrameCoalescer()

        coalescer.enqueue(Offset(2f, -1f))
        coalescer.flush()
        coalescer.enqueue(Offset(4f, 1f))

        assertEquals(Offset(4f, 1f), coalescer.flush())
    }

    @Test
    fun multipleSamplesInOneFrame_preserveTheFullFingerTravelDistance() {
        val coalescer = CanvasPanFrameCoalescer()

        coalescer.enqueue(Offset(1.25f, 0.5f))
        coalescer.enqueue(Offset(2.75f, -1.5f))

        assertEquals(Offset(4f, -1f), coalescer.flush())
    }

    @Test
    fun largeFrameBatch_isSplitWithoutDroppingAnyPanDistance() {
        assertEquals(
            listOf(Offset(200f, -200f), Offset(100f, -50f)),
            splitCanvasPanDelta(Offset(300f, -250f)),
        )
    }
}
