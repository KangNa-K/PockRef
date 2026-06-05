package com.pockref.app.spen

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Schedules a Runnable to run after a delay. The detector uses
 * this to fire the "double-tap window has elapsed, this was a
 * single tap → Multi" event, so the Scheduler is the only piece
 * of timing that lives outside the detector.
 *
 * Abstracted so unit tests can run on plain JUnit without
 * Robolectric. The default implementation is a thin wrapper
 * over [android.os.Handler]; tests use [FakeScheduler] which
 * runs runnables synchronously when [advanceTimeBy] is called.
 */
interface SpenButtonScheduler {
    fun postDelayed(runnable: Runnable, delayMs: Long)
    fun cancel(runnable: Runnable)
}

/**
 * Production scheduler. Wraps an [android.os.Handler] bound
 * to the main looper — the detector's runnable posts a
 * [PockRefAction.SetSelectionMode] to the ViewModel, which
 * must be touched on the main thread.
 */
class HandlerSpenButtonScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : SpenButtonScheduler {
    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}

/**
 * Test scheduler. Queues runnables by their target fire time;
 * [advanceTimeBy] runs every runnable whose deadline has been
 * reached. [pendingCount] lets tests assert that no stray
 * runnable survived a [advanceTimeBy] call.
 *
 * Time is whatever the test's [clock] returns — typically a
 * manually-advanced Long. No wall clock involvement.
 */
class FakeSpenButtonScheduler(
    private val clock: () -> Long,
) : SpenButtonScheduler {
    private data class Entry(val runnable: Runnable, val fireAt: Long)

    private val entries = mutableListOf<Entry>()

    val pendingCount: Int get() = entries.size

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        entries.add(Entry(runnable, clock() + delayMs))
    }

    override fun cancel(runnable: Runnable) {
        entries.removeAll { it.runnable === runnable }
    }

    /**
     * Run every runnable whose deadline has been reached.
     * Call once per "I want the clock to advance" step in the
     * test. The caller is responsible for bumping the
     * underlying [clock] before calling this — we read
     * `clock()` once to get a consistent snapshot of "now",
     * then drain everything at or before that.
     */
    fun runDue() {
        val now = clock()
        val due = entries.filter { it.fireAt <= now }.toList()
        for (e in due) {
            entries.remove(e)
            e.runnable.run()
        }
    }
}

/**
 * The S Pen side-button binding has two user-selectable
 * gesture modes (settings panel exposes a radio-button pair
 * for this — see PockRefApp.kt settings sheet).
 *
 * - [PRESS_AND_HOLD] (default) — physical press-and-hold
 *   semantics. First DOWN edge fires [ButtonEvent.BoxStart]
 *   (entering box-select mode). UP edge fires
 *   [ButtonEvent.BoxEnd] (exiting box mode). A second DOWN
 *   within [doubleClickWindowMs] of the first UP is a
 *   double-tap → [ButtonEvent.Single] (caller can use this
 *   to override selection state, e.g. "force single-select
 *   regardless of what the box selection picked"). The
 *   [ButtonEvent.Multi] arming runnable fires if no second
 *   DOWN arrives in the window — same idea, "treat as a
 *   single tap → multi-lasso mode" (currently the caller
 *   ignores Multi, but the event is preserved for future
 *   re-use).
 *
 * - [TOGGLE] — first DOWN edge fires [ButtonEvent.BoxStart];
 *   the user can release and the mode STAYS ON. The next
 *   DOWN edge fires [ButtonEvent.BoxEnd] (mode OFF).
 *   UP edges are ignored entirely — the user doesn't have
 *   to keep the button held. Double-click and Multi /
 *   Single are NOT emitted in this mode (no double-click
 *   concept when the button isn't a click). The
 *   [doubleClickWindowMs] parameter is unused in this mode.
 */
enum class SpenMode { PRESS_AND_HOLD, TOGGLE }

/**
 * State machine for the S Pen side button. Maps raw press /
 * release edges to one of [ButtonEvent]s based on the
 * configured [SpenMode].
 *
 * Common event semantics (both modes):
 *
 * - [ButtonEvent.BoxStart] — fire on the DOWN edge that
 *   ENTERS box-select mode. Caller should switch to BOX
 *   (rectangle drag) mode.
 * - [ButtonEvent.BoxEnd] — fire on the edge that EXITS box
 *   mode. In PRESS_AND_HOLD that's UP; in TOGGLE that's
 *   the second DOWN. Caller should switch to MULTI / lasso
 *   (or SINGLE, depending on selection size — caller
 *   decides) on receipt.
 *
 * PRESS_AND_HOLD only:
 *
 * - [ButtonEvent.Multi] — single-tap → MULTI semantics,
 *   fired by the [doubleClickWindowMs] timeout runnable.
 *   Currently the caller ignores this; the event survives
 *   in the wire format for a future "tap to enter multi-
 *   lasso" spec.
 * - [ButtonEvent.Single] — double-tap → SINGLE override,
 *   fired on the second DOWN within the window.
 *
 * Unit-testable: takes a [clock] and [scheduler] in the
 * constructor, so tests can drive elapsed time and runnable
 * scheduling without touching Android Looper.
 */
class SpenButtonDetector(
    /**
     * Maximum time (ms) between the first UP and the second DOWN
     * for the gesture to count as a double-tap. Default 300 ms —
     * matches Material Design's tap-throttle guidance and the
     * existing in-app "double-tap empty canvas" path in
     * [com.pockref.app.ui.PockRefApp]. Anything longer than this is
     * a single-tap → Multi. Only used in [SpenMode.PRESS_AND_HOLD];
     * ignored in [SpenMode.TOGGLE] (no double-click concept there).
     */
    private val doubleClickWindowMs: Long = 300L,

    /**
     * Minimum time (ms) between two adjacent DOWN edges to count
     * as separate presses. S Pen hardware can chatter on a single
     * physical press (bounce), and the OS's own debounce in
     * [com.pockref.app.MainActivity] runs at the dispatch-hook
     * level (so this 30 ms ceiling filters out the residual
     * bounce that gets past the OS layer without ever swallowing
     * a deliberate second tap).
     */
    private val debounceMs: Long = 30L,

    /**
     * Which gesture mode to run. The user picks this in
     * the settings panel (see PockRefApp.kt settings sheet —
     * a RadioButton pair). Changing it via [reset] + new
     * detector instance is the supported hot-swap path; the
     * detector itself does not re-branch mid-gesture.
     */
    val mode: SpenMode = SpenMode.PRESS_AND_HOLD,

    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val scheduler: SpenButtonScheduler = HandlerSpenButtonScheduler(),
    private val onEvent: (ButtonEvent) -> Unit = {},
) {
    /**
     * States used in [SpenMode.PRESS_AND_HOLD]. Kept as a
     * separate enum from the TOGGLE states because the
     * branches share no edges and the combined enum would
     * force every `when` to be exhaustive over irrelevant
     * cases.
     */
    private enum class PressHoldState {
        IDLE,
        FIRST_DOWN,
        WAITING_DOUBLE,
        SECOND_DOWN,
    }

    /**
     * States used in [SpenMode.TOGGLE]. Two states only —
     * the toggle is either OFF (IDLE) or ON. UP edges are
     * ignored. NEXT_DOWN flips between them.
     */
    private enum class ToggleState {
        OFF,
        ON,
    }

    /**
     * Event types emitted to the caller. See the class
     * doc-comment for per-event firing rules across the
     * two [SpenMode]s.
     */
    enum class ButtonEvent { BoxStart, BoxEnd, Multi, Single }

    private var pressHoldState: PressHoldState = PressHoldState.IDLE
    private var toggleState: ToggleState = ToggleState.OFF

    // Sentinel for "no down recorded yet". A plain 0L would
    // falsely trip the debounce check on a test whose clock
    // starts at 0 (and on a real device at boot). Long.MIN_VALUE
    // arithmetic with a real elapsedRealtime() doesn't overflow
    // on a real device (the device has been on for seconds, not
    // ~9 quintillion ms), but in tests the clock may sit at 0
    // long enough that `0 - Long.MIN_VALUE` overflows. The
    // [hasDown] flag avoids the issue cleanly.
    private var hasDown: Boolean = false
    private var lastDownTime: Long = 0L
    private var pendingRunnable: Runnable? = null

    /** True while a press is being held in PRESS_AND_HOLD mode
     *  ([PressHoldState.FIRST_DOWN] or [PressHoldState.SECOND_DOWN]),
     *  OR while the toggle is ON in [SpenMode.TOGGLE]. */
    val isButtonHeld: Boolean
        get() = when (mode) {
            SpenMode.PRESS_AND_HOLD ->
                pressHoldState == PressHoldState.FIRST_DOWN ||
                    pressHoldState == PressHoldState.SECOND_DOWN
            SpenMode.TOGGLE -> toggleState == ToggleState.ON
        }

    /**
     * Notify the detector that the S Pen side button just pressed
     * down (rising edge only — the caller is responsible for
     * filtering to edges, not levels).
     */
    fun onDown() {
        val now = clock()
        if (hasDown && now - lastDownTime < debounceMs) return
        hasDown = true
        lastDownTime = now

        when (mode) {
            SpenMode.PRESS_AND_HOLD -> onDownPressHold()
            SpenMode.TOGGLE -> onDownToggle()
        }
    }

    /**
     * Notify the detector that the S Pen side button just
     * released (falling edge only).
     */
    fun onUp() {
        when (mode) {
            SpenMode.PRESS_AND_HOLD -> onUpPressHold()
            SpenMode.TOGGLE -> {
                // In TOGGLE mode UP is invisible — the
                // user can release and the toggle stays
                // ON. No state change, no event, no
                // debounce bookkeeping.
            }
        }
    }

    // ── PRESS_AND_HOLD state machine ──
    private fun onDownPressHold() {
        when (pressHoldState) {
            PressHoldState.IDLE -> {
                pressHoldState = PressHoldState.FIRST_DOWN
                onEvent(ButtonEvent.BoxStart)
            }
            PressHoldState.WAITING_DOUBLE -> {
                // Second press arrived within the
                // double-click window. This is the
                // "double-tap" trigger.
                cancelPending()
                pressHoldState = PressHoldState.SECOND_DOWN
                onEvent(ButtonEvent.Single)
            }
            PressHoldState.FIRST_DOWN, PressHoldState.SECOND_DOWN -> {
                // Already held — ignore the duplicate.
                // The dispatch hook's debounce should
                // have caught this; the guard is a
                // safety net.
            }
        }
    }

    private fun onUpPressHold() {
        when (pressHoldState) {
            PressHoldState.FIRST_DOWN -> {
                // Press-and-hold release: the user
                // pressed (BoxStart fired on the prior
                // DOWN), and now released before the
                // double-click window closed. Emit
                // BoxEnd so the caller can pull the
                // user out of box mode. Then arm the
                // window: if a second DOWN arrives
                // within it, treat the gesture as a
                // double-tap; otherwise it's a single
                // tap.
                onEvent(ButtonEvent.BoxEnd)
                pressHoldState = PressHoldState.WAITING_DOUBLE
                pendingRunnable = Runnable {
                    if (pressHoldState == PressHoldState.WAITING_DOUBLE) {
                        pressHoldState = PressHoldState.IDLE
                        onEvent(ButtonEvent.Multi)
                    }
                }
                scheduler.postDelayed(pendingRunnable!!, doubleClickWindowMs)
            }
            PressHoldState.SECOND_DOWN -> {
                // Double-tap is already fired on the
                // second DOWN, so a second UP just
                // clears state without re-firing Multi.
                // The BoxEnd here mirrors FIRST_DOWN's
                // release: the user did physically lift
                // the button after the second tap. We
                // send BoxEnd so the caller also drops
                // them out of box mode.
                onEvent(ButtonEvent.BoxEnd)
                pressHoldState = PressHoldState.IDLE
            }
            PressHoldState.IDLE, PressHoldState.WAITING_DOUBLE -> {
                // Spurious UP (e.g. the dispatch hook
                // saw a button release without a
                // matching press, or the double-click
                // window already fired Multi and reset
                // us to IDLE). Ignore — no BoxEnd
                // needed because the caller was never
                // told we entered box mode.
            }
        }
    }

    // ── TOGGLE state machine ──
    // First DOWN edge: OFF → ON, fire BoxStart.
    // Second DOWN edge while ON: ON → OFF, fire BoxEnd.
    // UP edges: ignored entirely. The user can release
    // and re-press at any pace; the toggle stays ON
    // until the next DOWN. There is no double-click
    // concept in this mode — [doubleClickWindowMs] is
    // not consulted.
    private fun onDownToggle() {
        when (toggleState) {
            ToggleState.OFF -> {
                toggleState = ToggleState.ON
                onEvent(ButtonEvent.BoxStart)
            }
            ToggleState.ON -> {
                toggleState = ToggleState.OFF
                onEvent(ButtonEvent.BoxEnd)
            }
        }
    }

    /**
     * Cancel any pending runnable and reset to IDLE / OFF.
     * Call from the activity's onPause to make sure a
     * backgrounded detector doesn't post a stale Multi
     * event after the activity resumes.
     */
    fun reset() {
        cancelPending()
        pressHoldState = PressHoldState.IDLE
        toggleState = ToggleState.OFF
        hasDown = false
        lastDownTime = 0L
    }

    private fun cancelPending() {
        pendingRunnable?.let { scheduler.cancel(it) }
        pendingRunnable = null
    }
}
