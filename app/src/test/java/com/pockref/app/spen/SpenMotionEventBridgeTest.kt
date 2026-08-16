package com.pockref.app.spen

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SpenMotionEventBridgeTest {

    private class Harness {
        var now = 0L
        val events = mutableListOf<SpenButtonDetector.ButtonEvent>()
        private val scheduler = FakeSpenButtonScheduler { now }
        private val detector = SpenButtonDetector(
            mode = SpenMode.PRESS_AND_HOLD,
            clock = { now },
            scheduler = scheduler,
            onEvent = events::add,
        )
        val bridge = SpenMotionEventBridge(detector)
    }

    @Test
    fun eachSupportedStylusBit_opensAndClosesBoxSelectionOnce() {
        val supportedBits = listOf(
            MotionEvent.BUTTON_STYLUS_PRIMARY,
            MotionEvent.BUTTON_STYLUS_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
        )

        supportedBits.forEach { bit ->
            val h = Harness()
            h.bridge.onButtonState(bit)
            h.bridge.onButtonState(bit)
            h.bridge.onButtonState(0)

            assertEquals(
                listOf(
                    SpenButtonDetector.ButtonEvent.BoxStart,
                    SpenButtonDetector.ButtonEvent.BoxEnd,
                ),
                h.events,
            )
        }
    }

    @Test
    fun unrelatedButtonState_doesNotReachDetector() {
        val h = Harness()

        h.bridge.onButtonState(MotionEvent.BUTTON_PRIMARY)
        h.bridge.onButtonState(0)

        assertEquals(emptyList<SpenButtonDetector.ButtonEvent>(), h.events)
    }

    @Test
    fun reset_discardsHeldLevel_beforeTheNextPress() {
        val h = Harness()

        h.bridge.onButtonState(MotionEvent.BUTTON_STYLUS_PRIMARY)
        h.bridge.reset()
        h.bridge.onButtonState(0)
        h.bridge.onButtonState(MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxStart,
            ),
            h.events,
        )
    }
}
