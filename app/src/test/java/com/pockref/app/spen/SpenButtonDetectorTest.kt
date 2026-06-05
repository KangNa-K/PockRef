package com.pockref.app.spen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for [SpenButtonDetector]. No Robolectric,
 * no Android Looper — the detector takes a [FakeSpenButtonScheduler]
 * and a manual clock so the tests advance time synchronously.
 */
class SpenButtonDetectorTest {

    /**
     * Helper: build a detector with a fake scheduler driven by
     * the test's [now] variable. Each call to [tick] advances
     * the clock by [deltaMs] and runs any runnable whose deadline
     * has passed.
     */
    private class Harness(
        val doubleClickWindowMs: Long = 300L,
        val debounceMs: Long = 30L,
        val mode: SpenMode = SpenMode.PRESS_AND_HOLD,
    ) {
        var now: Long = 0L
        val events: MutableList<SpenButtonDetector.ButtonEvent> = mutableListOf()
        val scheduler = FakeSpenButtonScheduler(clock = { now })
        val detector = SpenButtonDetector(
            doubleClickWindowMs = doubleClickWindowMs,
            debounceMs = debounceMs,
            mode = mode,
            clock = { now },
            scheduler = scheduler,
            onEvent = { events.add(it) },
        )

        fun tick(deltaMs: Long) {
            now += deltaMs
            scheduler.runDue()
        }
    }

    @Test
    fun singleTap_thenTimeout_firesBoxStartBoxEndMulti() {
        val h = Harness()
        h.detector.onDown()
        h.tick(50)         // short hold
        h.detector.onUp()
        // No second DOWN within the window
        h.tick(300)
        // V_NOW (press-and-hold): every UP from a held state
        // emits BoxEnd so the coordinator can pull the user
        // out of box mode. The window's Multi then fires
        // (single-tap classification), which the coordinator
        // ignores.
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
            ),
            h.events,
        )
        assertEquals(0, h.scheduler.pendingCount)
    }

    @Test
    fun doubleTap_firesSingle() {
        val h = Harness()
        h.detector.onDown()
        h.tick(50)
        h.detector.onUp()
        h.tick(100)        // 100 ms since the first UP — well within the 300 ms window
        h.detector.onDown() // second DOWN → Single
        h.tick(50)
        h.detector.onUp()
        // V_NOW (press-and-hold): each UP from a held state
        // fires BoxEnd. First UP (FIRST_DOWN → WAITING_DOUBLE)
        // fires BoxEnd; second UP (SECOND_DOWN → IDLE) also
        // fires BoxEnd. Coordinator ignores both since the
        // user is already out of box mode after the first.
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Single,
                SpenButtonDetector.ButtonEvent.BoxEnd,
            ),
            h.events,
        )
        // The Single's UP doesn't re-fire Multi, and the second
        // DOWN consumed the pending runnable.
        assertEquals(0, h.scheduler.pendingCount)
    }

    @Test
    fun doubleTap_tooSlow_firesMultiThenBoxStart() {
        val h = Harness()
        h.detector.onDown()
        h.tick(50)
        h.detector.onUp()
        h.tick(500)        // 500 ms — past the 300 ms window → Multi fires
        h.detector.onDown() // fresh BoxStart
        h.tick(50)
        h.detector.onUp()
        h.tick(300)
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
            ),
            h.events,
        )
    }

    @Test
    fun downBounce_withinDebounce_isIgnored() {
        val h = Harness()
        h.detector.onDown()           // BoxStart
        h.tick(10)                    // 10 ms — under the 30 ms debounce
        h.detector.onDown()           // should be ignored (already FIRST_DOWN)
        h.tick(10)                    // 20 ms
        h.detector.onDown()           // still FIRST_DOWN
        h.tick(20)                    // 30 ms after the last registered down — but this is on the bounce path, not a fresh IDLE→FIRST_DOWN
        h.detector.onUp()
        h.tick(300)
        // The two bounce DOWNs should not produce extra BoxStart
        // events. The UP from FIRST_DOWN schedules Multi, which
        // fires at 300 ms. So we expect exactly BoxStart + BoxEnd
        // + Multi.
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
            ),
            h.events,
        )
    }

    @Test
    fun downThenLongHold_noEventsUntilReleased() {
        val h = Harness()
        h.detector.onDown()
        h.tick(5_000)        // 5 s hold, no UP
        // Only BoxStart should have fired.
        assertEquals(listOf(SpenButtonDetector.ButtonEvent.BoxStart), h.events)
        h.detector.onUp()
        h.tick(300)
        // V_NOW: the long hold's UP fires BoxEnd; the
        // window's Multi fires 300 ms after.
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
            ),
            h.events,
        )
    }

    @Test
    fun upWithoutDown_isIgnored() {
        val h = Harness()
        h.detector.onUp()
        h.tick(500)
        // V_NOW: spurious UPs (no matching DOWN) still don't
        // fire BoxEnd, because the coordinator was never told
        // to enter box mode in the first place — pairing
        // BoxStart with a no-op BoxEnd would be a redundant
        // "out then in" round-trip on the state machine.
        assertEquals(emptyList<SpenButtonDetector.ButtonEvent>(), h.events)
    }

    @Test
    fun reset_cancelsPendingRunnables() {
        val h = Harness()
        h.detector.onDown()
        h.tick(50)
        h.detector.onUp()
        // Pending Multi runnable is scheduled; reset before it fires
        h.detector.reset()
        h.tick(500)
        // No Multi fires because reset() pulled the runnable
        // before its deadline. BoxEnd still fired on the UP
        // before reset (the reset clears pending runnables, not
        // already-emitted events).
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
            ),
            h.events,
        )
        assertEquals(0, h.scheduler.pendingCount)
    }

    @Test
    fun isButtonHeld_reflectsState() {
        val h = Harness()
        assertEquals(false, h.detector.isButtonHeld)
        h.detector.onDown()
        assertEquals(true, h.detector.isButtonHeld)
        h.tick(50)
        h.detector.onUp()
        assertEquals(false, h.detector.isButtonHeld)  // WAITING_DOUBLE — not held
        h.tick(100)
        h.detector.onDown()
        assertEquals(true, h.detector.isButtonHeld)   // SECOND_DOWN — held again
        h.detector.onUp()
        assertEquals(false, h.detector.isButtonHeld)
    }

    @Test
    fun secondTapArrivesJustInTime_firesSingle() {
        val h = Harness()
        h.detector.onDown()
        h.tick(20)
        h.detector.onUp()
        h.tick(299)         // 1 ms before the window would close
        h.detector.onDown()
        h.detector.onUp()
        // V_NOW: two UPs, two BoxEnds, sandwiching the Single.
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Single,
                SpenButtonDetector.ButtonEvent.BoxEnd,
            ),
            h.events,
        )
    }

    @Test
    fun secondTapArrivesOneMsTooLate_firesMulti() {
        val h = Harness()
        h.detector.onDown()
        h.tick(20)
        h.detector.onUp()
        h.tick(300)         // window just closed → Multi fires
        h.detector.onDown() // new gesture, fresh BoxStart
        h.detector.onUp()
        h.tick(300)
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.Multi,
            ),
            h.events,
        )
    }

    @Test
    fun debounceAcrossGestures_works() {
        val h = Harness(debounceMs = 50L)
        // First gesture: BoxStart + BoxEnd + Multi
        h.detector.onDown()
        h.tick(20)
        h.detector.onUp()
        h.tick(300)
        // Second DOWN 20ms after the first UP — well within
        // the debounce window. Since the detector is in IDLE
        // (Multi already fired), this should still be treated
        // as a fresh press. The debounce only filters
        // successive DOWNs without an intervening UP.
        h.detector.onDown()
        h.tick(50)
        h.detector.onUp()
        h.tick(300)
        // 6 events: BoxStart + BoxEnd + Multi + BoxStart + BoxEnd + Multi
        assertEquals(6, h.events.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOGGLE mode tests
    //
    // Semantics (see [SpenMode.TOGGLE]): first DOWN fires
    // BoxStart; UP edges are invisible; next DOWN fires BoxEnd;
    // repeat. No Multi / Single events; no double-click window.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun toggle_firstDownFiresBoxStart_secondDownFiresBoxEnd() {
        val h = Harness(mode = SpenMode.TOGGLE)
        h.detector.onDown()
        h.tick(50)            // hold a bit (UP is ignored)
        h.detector.onUp()
        h.tick(200)           // user lifts, waits, comes back
        h.detector.onDown()   // second tap → toggle off
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
            ),
            h.events,
        )
    }

    @Test
    fun toggle_upDoesNotFireAnything() {
        val h = Harness(mode = SpenMode.TOGGLE)
        h.detector.onDown()
        // BoxStart has fired. UP, several more UPs, time
        // passing — the toggle must STAY ON. No BoxEnd, no
        // Multi, no Single, no spurious events.
        h.detector.onUp()
        h.detector.onUp()
        h.detector.onUp()
        h.tick(5_000)
        assertEquals(listOf(SpenButtonDetector.ButtonEvent.BoxStart), h.events)
    }

    @Test
    fun toggle_repeatedDownsAlternateBoxStartBoxEnd() {
        val h = Harness(mode = SpenMode.TOGGLE)
        // Three toggle cycles: each cycle is one DOWN. The
        // first fires BoxStart (OFF→ON); the second fires
        // BoxEnd (ON→OFF); the third fires BoxStart again
        // (OFF→ON). Tick between cycles so the debounce
        // (default 30 ms) doesn't lump them into a single
        // chatter — real S Pen taps are ~100 ms apart, not
        // 0 ms apart.
        h.detector.onDown()
        h.detector.onUp()
        h.tick(100)
        h.detector.onDown()
        h.detector.onUp()
        h.tick(100)
        h.detector.onDown()
        h.detector.onUp()
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.BoxStart,
            ),
            h.events,
        )
    }

    @Test
    fun toggle_longPauseBetweenDowns_stillToggles() {
        val h = Harness(mode = SpenMode.TOGGLE)
        h.detector.onDown()
        h.tick(60_000)         // user releases, goes to lunch
        h.detector.onUp()      // no-op
        h.tick(60_000)
        h.detector.onDown()    // come back, tap → toggle off
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
            ),
            h.events,
        )
    }

    @Test
    fun toggle_neverEmitsMultiOrSingle() {
        val h = Harness(mode = SpenMode.TOGGLE)
        // Drive the detector through a sequence that would
        // emit Multi / Single in PRESS_AND_HOLD mode. The
        // double-click window and the Single-on-second-DOWN
        // rule don't apply here — those are PRESS_AND_HOLD
        // semantics.
        h.detector.onDown()
        h.tick(20)
        h.detector.onUp()
        h.tick(50)             // well within the press-and-hold window
        h.detector.onDown()    // second tap (toggle off)
        h.detector.onUp()
        h.tick(500)
        h.detector.onDown()    // third tap (toggle on)
        h.detector.onUp()
        h.tick(500)
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.BoxStart,
            ),
            h.events,
        )
    }

    @Test
    fun toggle_isButtonHeld_staysTrueBetweenDowns() {
        val h = Harness(mode = SpenMode.TOGGLE)
        assertEquals(false, h.detector.isButtonHeld)
        h.detector.onDown()
        assertEquals(true, h.detector.isButtonHeld)    // ON after first tap
        h.tick(50)
        h.detector.onUp()                               // UP ignored
        assertEquals(true, h.detector.isButtonHeld)    // still ON
        h.tick(5_000)
        assertEquals(true, h.detector.isButtonHeld)    // still ON after long wait
        h.detector.onDown()                            // second tap
        assertEquals(false, h.detector.isButtonHeld)   // OFF
    }

    @Test
    fun toggle_debounceStillApplies() {
        val h = Harness(mode = SpenMode.TOGGLE, debounceMs = 50L)
        // The debounce filters rapid DOWN chatter (typical
        // S Pen hardware bounce on a single physical press).
        // It does NOT filter legitimate re-presses that
        // happen after a real UP — the real usage is
        // DOWN / wait / UP / wait / DOWN with hundreds of
        // ms between presses, so 50ms debounce is invisible
        // to the user.
        //
        // Drive a single-press chatter pattern: first DOWN
        // sets hasDown + lastDownTime; a second DOWN 10ms
        // later is filtered by debounce. Then a real UP,
        // a real wait (well outside the debounce window),
        // and a second press.
        h.detector.onDown()    // t=0   fires BoxStart (ON)
        h.tick(10)            // chatter in flight
        h.detector.onDown()    // 10 - 0 < 50 → debounce-skipped
        h.detector.onUp()      // ignored in TOGGLE
        h.tick(200)            // user lifts finger, takes finger off, real wait
        h.detector.onDown()    // t=210  210 - 0 = 210 > 50 → fires BoxEnd (OFF)
        h.detector.onUp()
        h.tick(200)
        h.detector.onDown()    // t=410  410 - 210 = 200 > 50 → fires BoxStart (ON)
        assertEquals(
            listOf(
                SpenButtonDetector.ButtonEvent.BoxStart,
                SpenButtonDetector.ButtonEvent.BoxEnd,
                SpenButtonDetector.ButtonEvent.BoxStart,
            ),
            h.events,
        )
    }

    @Test
    fun toggle_reset_clearsOnState() {
        val h = Harness(mode = SpenMode.TOGGLE)
        h.detector.onDown()    // ON
        assertEquals(true, h.detector.isButtonHeld)
        h.detector.reset()
        assertEquals(false, h.detector.isButtonHeld)
        // Clear the events list so the assertion below
        // checks the post-reset gesture alone, not the
        // pre-reset BoxStart still in the buffer.
        h.events.clear()
        // First DOWN after reset starts a fresh cycle
        h.detector.onDown()
        assertEquals(listOf(SpenButtonDetector.ButtonEvent.BoxStart), h.events)
    }
}
