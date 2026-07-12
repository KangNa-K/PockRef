package com.pockref.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasViewportDecodeFlowTest {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun debounceCanvasPanViewportUpdates_emitsOnceAfterLatestPanDelay() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val emissions = mutableListOf<Unit>()
        val job = launch {
            source
                .debounceCanvasPanViewportUpdates()
                .collect { emissions += it }
        }

        runCurrent()

        source.emit(Unit)
        advanceTimeBy(79)
        assertTrue(emissions.isEmpty())

        source.emit(Unit)
        advanceTimeBy(79)
        assertTrue(emissions.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, emissions.size)

        job.cancel()
    }
}
