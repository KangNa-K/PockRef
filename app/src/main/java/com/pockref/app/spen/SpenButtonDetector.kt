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
 * The S Pen side-button binding has three user-selectable
 * gesture modes (settings panel exposes a radio-button
 * pair for this — see PockRefApp.kt settings sheet).
 *
 * - [PRESS_AND_HOLD] — physical press-and-hold
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
 *
 * - [DOUBLE_TAP] (V_NOW D3 — default) — pure tap
 *   semantics, no holding required. Each DOWN edge is
 *   an independent tap. The first DOWN fires
 *   [ButtonEvent.BoxStart]. A second DOWN within
 *   [doubleTapWindowMs] of the first DOWN is the
 *   "double-tap" trigger → fires [ButtonEvent.BoxEnd] +
 *   [ButtonEvent.Single] (the caller uses Single to
 *   force single-select regardless of box outcome). If
 *   no second DOWN arrives within
 *   [doubleTapWindowMs], the detector transitions to a
 *   STAY-OPEN state where the user is "in box mode
 *   indefinitely" — subsequent DOWN edges RE-ARM the
 *   double-tap window but do NOT re-fire BoxStart (the
 *   user is already in box mode). UP edges are ignored
 *   entirely (just like [TOGGLE] — the user never has
 *   to keep the button held). The
 *   [doubleClickWindowMs] parameter is unused in this
 *   mode; the rapid-tap window is the
 *   [doubleTapWindowMs]-driven one. Default mode in
 *   [com.pockref.app.data.AppPreferences].
 */
enum class SpenMode { PRESS_AND_HOLD, TOGGLE, DOUBLE_TAP }

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
     * V_NOW (D3): the rapid-tap window used by
     * [SpenMode.DOUBLE_TAP] (200 ms by default).
     * Different from [doubleClickWindowMs] which is the
     * PRESS_AND_HOLD double-click window (300 ms,
     * matches Material Design's tap-throttle guidance).
     * The user spec for DOUBLE_TAP was 200 ms — a
     * tighter window because the user is doing
     * deliberate "tap-tap to cancel" rather than
     * accidental rapid bouncing. Only consulted in
     * [SpenMode.DOUBLE_TAP]; ignored in the other two
     * modes.
     */
    private val doubleTapWindowMs: Long = 200L,

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
     * V_NOW (D3): states used in [SpenMode.DOUBLE_TAP].
     * Three states — IDLE (no button activity, no box
     * mode), WAITING_DOUBLE (first DOWN fired, watching
     * for a second DOWN within [doubleTapWindowMs] to
     * fire the "double-tap → BoxEnd + Single" exit),
     * and STAY_OPEN (first DOWN fired and the
     * [doubleTapWindowMs] elapsed with no second tap,
     * so we're in box mode indefinitely until either
     * (a) the user does a quick double-tap (re-arms
     * the window via a fresh WAITING_DOUBLE transition
     * — see [onDownDoubleTap]) or (b) the caller exits
     * box mode via an external mechanism like the
     * SelectionBottomSheet X button).
     */
    private enum class DoubleTapState {
        IDLE,
        WAITING_DOUBLE,
        STAY_OPEN,
    }

    /**
     * Event types emitted to the caller. See the class
     * doc-comment for per-event firing rules across the
     * two [SpenMode]s.
     */
    enum class ButtonEvent { BoxStart, BoxEnd, Multi, Single }

    private var pressHoldState: PressHoldState = PressHoldState.IDLE
    private var toggleState: ToggleState = ToggleState.OFF
    private var doubleTapState: DoubleTapState = DoubleTapState.IDLE

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
     *  OR while the toggle is ON in [SpenMode.TOGGLE], OR
     *  while the detector is in box mode under
     *  [SpenMode.DOUBLE_TAP] ([DoubleTapState.WAITING_DOUBLE]
     *  or [DoubleTapState.STAY_OPEN]). */
    val isButtonHeld: Boolean
        get() = when (mode) {
            SpenMode.PRESS_AND_HOLD ->
                pressHoldState == PressHoldState.FIRST_DOWN ||
                    pressHoldState == PressHoldState.SECOND_DOWN
            SpenMode.TOGGLE -> toggleState == ToggleState.ON
            SpenMode.DOUBLE_TAP ->
                doubleTapState == DoubleTapState.WAITING_DOUBLE ||
                    doubleTapState == DoubleTapState.STAY_OPEN
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
            SpenMode.DOUBLE_TAP -> onDownDoubleTap()
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
            SpenMode.DOUBLE_TAP -> {
                // V_NOW (D3): same as TOGGLE — UP is
                // invisible. The user never has to
                // keep the button held; each tap is
                // independent.
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

    // ── DOUBLE_TAP state machine (V_NOW D3) ──
    // IDLE → DOWN → WAITING_DOUBLE (fire BoxStart, arm
    //   [doubleTapWindowMs] timer).
    // WAITING_DOUBLE → DOWN within window → IDLE (fire
    //   BoxEnd + Single; the user explicitly tapped
    //   twice to enter + immediately exit).
    // WAITING_DOUBLE → timer fires → STAY_OPEN (no
    //   event; user is in box mode indefinitely).
    // STAY_OPEN → DOWN → WAITING_DOUBLE (re-arm the
    //   rapid-tap window; do NOT re-fire BoxStart —
    //   the user is already in box mode and a second
    //   tap should be interpreted as "I'm trying to
    //   double-tap to exit").
    //
    // UP edges are ignored entirely (see [onUp]).
    // [doubleClickWindowMs] is NOT consulted in this
    // mode — the rapid-tap window is
    // [doubleTapWindowMs]-driven (200 ms by default,
    // tighter than the 300 ms PRESS_AND_HOLD window
    // because the user is doing deliberate "tap-tap
    // to cancel" rather than accidental rapid
    // bouncing).
    private fun onDownDoubleTap() {
        when (doubleTapState) {
            DoubleTapState.IDLE -> {
                doubleTapState = DoubleTapState.WAITING_DOUBLE
                onEvent(ButtonEvent.BoxStart)
                pendingRunnable = Runnable {
                    if (doubleTapState == DoubleTapState.WAITING_DOUBLE) {
                        // No second tap within the rapid-tap
                        // window — transition to STAY_OPEN.
                        // No event fired; the user is in
                        // box mode and stays there until
                        // they double-tap to exit (or use
                        // an external mechanism like the
                        // bottom sheet's X button).
                        doubleTapState = DoubleTapState.STAY_OPEN
                    }
                }
                scheduler.postDelayed(pendingRunnable!!, doubleTapWindowMs)
            }
            DoubleTapState.WAITING_DOUBLE -> {
                // Second tap within the rapid-tap
                // window — this is the "double-tap to
                // exit" trigger. Fire both BoxEnd
                // (caller switches selection mode) and
                // Single (caller can use this to force
                // single-select regardless of box
                // outcome).
                cancelPending()
                doubleTapState = DoubleTapState.IDLE
                onEvent(ButtonEvent.BoxEnd)
                onEvent(ButtonEvent.Single)
            }
            DoubleTapState.STAY_OPEN -> {
                // User is already in box mode. Re-arm
                // the rapid-tap window so the next tap
                // (if within [doubleTapWindowMs]) is
                // interpreted as a double-tap exit.
                // Do NOT fire BoxStart — that would be
                // a no-op for the caller (it's already
                // in BOX mode) and would spam the
                // event stream with redundant
                // BoxStarts.
                cancelPending()
                doubleTapState = DoubleTapState.WAITING_DOUBLE
                pendingRunnable = Runnable {
                    if (doubleTapState == DoubleTapState.WAITING_DOUBLE) {
                        doubleTapState = DoubleTapState.STAY_OPEN
                    }
                }
                scheduler.postDelayed(pendingRunnable!!, doubleTapWindowMs)
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
        doubleTapState = DoubleTapState.IDLE
        hasDown = false
        lastDownTime = 0L
    }

    /** End an active box selection when its input window is interrupted. */
    fun cancel() {
        val boxActive = when (mode) {
            SpenMode.PRESS_AND_HOLD -> pressHoldState == PressHoldState.FIRST_DOWN
            SpenMode.TOGGLE -> toggleState == ToggleState.ON
            SpenMode.DOUBLE_TAP -> doubleTapState != DoubleTapState.IDLE
        }
        reset()
        if (boxActive) onEvent(ButtonEvent.BoxEnd)
    }

    private fun cancelPending() {
        pendingRunnable?.let { scheduler.cancel(it) }
        pendingRunnable = null
    }
}
