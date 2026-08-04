package com.pockref.app.ui

import com.pockref.app.model.PracticeConfig
import com.pockref.app.model.PracticePhase
import com.pockref.app.model.PracticeState
import com.pockref.app.model.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V_NOW (2026-06-08 速写练习): pure JVM tests for the
 * [PracticeReducer] state machine. Every test exercises one
 * transition rule so a future change to the reducer is
 * forced to update the matching test (and surface a
 * behaviour change to the reviewer).
 *
 * The reducer is the only testable unit for the practice
 * feature in commit 1 — the [PockRefViewModelPractice]
 * controller wraps it with side effects (prefs persistence,
 * _uiState.update) that need a real ViewModel to exercise,
 * which the unit-test source set deliberately avoids (no
 * Robolectric / Android instrumentation in this layer).
 * The controller gets an integration-level smoke test in
 * the androidTest source set once the practice screen
 * (commit 3) is wired up.
 */
class PracticeStateMachineTest {

    private val imageIds = listOf("a", "b", "c", "d", "e")
    private val config = PracticeConfig(
        practiceDurationMs = 60_000L,
        restDurationMs = 30_000L,
    )

    // ── Start ─────────────────────────────────────────────────────

    @Test
    fun start_fromIdle_setsRunningFirstImageCountdown() {
        val next = PracticeReducer.reduce(
            PracticeState.Idle,
            PracticeEvent.Start(imageIds, config),
        )
        assertEquals(PracticeStatus.RUNNING, next.status)
        assertEquals(imageIds, next.imageIds)
        assertEquals(0, next.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, next.phase)
        assertEquals(60_000L, next.remainingMs)
        assertEquals(config, next.config)
        assertEquals("1 / 5", next.progressLabel)
        assertEquals("a", next.currentImageId)
        assertFalse(next.isLastImage)
    }

    @Test
    fun start_emptyList_isNoOp() {
        val before = PracticeState.Idle
        val after = PracticeReducer.reduce(
            before,
            PracticeEvent.Start(emptyList(), config),
        )
        assertSame("empty list should leave the state untouched", before, after)
    }

    @Test
    fun start_whileAlreadyRunning_isNoOp() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 2,
            phase = PracticePhase.REST,
            remainingMs = 15_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            running,
            PracticeEvent.Start(listOf("x", "y"), config),
        )
        assertSame(running, after)
    }

    @Test
    fun start_withShuffle_usesShuffledOrder() {
        // Deterministic check: a 10-image shuffle should
        // produce a permutation of the input (same
        // elements, possibly different order). We don't
        // pin the order because [shuffled] uses a
        // non-deterministic default Random — the contract
        // is "set equality", not "specific order".
        val tenIds = (0 until 10).map { "img$it" }
        val shuffled = PracticeReducer.reduce(
            PracticeState.Idle,
            PracticeEvent.Start(tenIds, config.copy(shuffle = true)),
        )
        assertEquals(tenIds.toSet(), shuffled.imageIds.toSet())
        assertEquals(10, shuffled.imageIds.size)
    }

    // ── OnTick (no-transition) ───────────────────────────────────

    @Test
    fun onTick_positiveRemaining_updatesCount() {
        val before = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 60_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            before,
            PracticeEvent.OnTick(remainingMs = 45_000L),
        )
        assertEquals(45_000L, after.remainingMs)
        assertEquals(0, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
    }

    @Test
    fun onTick_whilePaused_isNoOp() {
        val paused = PracticeState(
            status = PracticeStatus.PAUSED,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 20_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            paused,
            PracticeEvent.OnTick(remainingMs = 19_000L),
        )
        // The ticker should not be firing while PAUSED,
        // but if a stale frame slips through, the reducer
        // must reject it rather than advance the state.
        assertSame(paused, after)
    }

    @Test
    fun onTick_whileFinished_isNoOp() {
        val finished = PracticeState(
            status = PracticeStatus.FINISHED,
            imageIds = imageIds,
            currentIndex = 4,
            phase = PracticePhase.REST,
            remainingMs = 0L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            finished,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        assertSame(finished, after)
    }

    // ── OnTick (transition: COUNTDOWN → REST) ─────────────────────

    @Test
    fun onTick_countdownEndWithRest_goesToRest() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 1L,  // about to expire
            config = config,  // rest = 30_000
        )
        val after = PracticeReducer.reduce(
            running,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        assertEquals(PracticePhase.REST, after.phase)
        assertEquals(30_000L, after.remainingMs)
        assertEquals(1, after.currentIndex)  // still showing the same image
        assertEquals(PracticeStatus.RUNNING, after.status)
    }

    @Test
    fun onTick_countdownEndWithZeroRest_advancesDirectly() {
        val zeroRestConfig = config.copy(restDurationMs = 0L)
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 0L,
            config = zeroRestConfig,
        )
        val after = PracticeReducer.reduce(
            running,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        // 0s rest = COUNTDOWN flows directly to next image's
        // COUNTDOWN. No REST phase observed.
        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    @Test
    fun onTick_negativeRemainingAlsoTransitions() {
        // 16ms-frame jitter that overshoots zero by a few ms
        // should not strand the session in an inconsistent state.
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 5L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            running,
            PracticeEvent.OnTick(remainingMs = -3L),
        )
        assertEquals(PracticePhase.REST, after.phase)
    }

    // ── OnTick (transition: REST → next) ──────────────────────────

    @Test
    fun onTick_restEnd_movesToNextImageCountdown() {
        val resting = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.REST,
            remainingMs = 0L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            resting,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
        assertEquals("3 / 5", after.progressLabel)
    }

    @Test
    fun onTick_lastImageCountdownEnd_goesToRestNotFinish() {
        // Spec: after the last image's COUNTDOWN, the user
        // gets a "you finished!" rest breath before the
        // FINISHED screen appears. So a non-zero rest
        // config should produce REST here, not FINISHED.
        val lastCountdown = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = listOf("only"),
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 0L,
            config = config,  // rest = 30_000
        )
        val after = PracticeReducer.reduce(
            lastCountdown,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        assertEquals(PracticePhase.REST, after.phase)
        assertEquals(0, after.currentIndex)
        assertEquals(30_000L, after.remainingMs)
    }

    @Test
    fun onTick_lastImageRestEnd_finishesSession() {
        val lastRest = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = listOf("only"),
            currentIndex = 0,
            phase = PracticePhase.REST,
            remainingMs = 0L,
            config = config,
        )
        val after = PracticeReducer.reduce(
            lastRest,
            PracticeEvent.OnTick(remainingMs = 0L),
        )
        assertEquals(PracticeStatus.FINISHED, after.status)
        assertEquals(0L, after.remainingMs)
        assertEquals("1 / 1", after.progressLabel)
    }

    // ── Pause / Resume ────────────────────────────────────────────

    @Test
    fun pause_whileRunning_keepsRemainingMs() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 2,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 17_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(running, PracticeEvent.Pause)
        assertEquals(PracticeStatus.PAUSED, after.status)
        assertEquals(17_000L, after.remainingMs)  // preserved
        assertEquals(2, after.currentIndex)
    }

    @Test
    fun pause_whilePaused_isNoOp() {
        val paused = PracticeState(
            status = PracticeStatus.PAUSED,
            imageIds = imageIds,
            currentIndex = 2,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 17_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(paused, PracticeEvent.Pause)
        assertSame(paused, after)
    }

    @Test
    fun resume_whilePaused_keepsRemainingMs() {
        val paused = PracticeState(
            status = PracticeStatus.PAUSED,
            imageIds = imageIds,
            currentIndex = 2,
            phase = PracticePhase.REST,
            remainingMs = 8_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(paused, PracticeEvent.Resume)
        assertEquals(PracticeStatus.RUNNING, after.status)
        assertEquals(8_000L, after.remainingMs)  // preserved
        assertEquals(PracticePhase.REST, after.phase)  // preserved
    }

    @Test
    fun resume_whileRunning_isNoOp() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 60_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(running, PracticeEvent.Resume)
        assertSame(running, after)
    }

    // ── Manual page navigation ────────────────────────────────────

    @Test
    fun previousImage_movesBackAndResetsCountdown() {
        val resting = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 2,
            phase = PracticePhase.REST,
            remainingMs = 4_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(resting, PracticeEvent.PreviousImage)

        assertEquals(PracticeStatus.RUNNING, after.status)
        assertEquals(1, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    @Test
    fun nextImage_movesForwardAndResetsCountdown() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 12_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(running, PracticeEvent.NextImage)

        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    @Test
    fun nextImage_whenResetDisabled_preservesCountdown() {
        val configWithoutReset = config.copy(resetTimerOnImageChange = false)
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 12_000L,
            config = configWithoutReset,
        )
        val after = PracticeReducer.reduce(running, PracticeEvent.NextImage)

        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(12_000L, after.remainingMs)
    }

    @Test
    fun navigation_fromRest_startsTargetCountdownWhenResetDisabled() {
        val configWithoutReset = config.copy(resetTimerOnImageChange = false)
        val resting = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.REST,
            remainingMs = 4_000L,
            config = configWithoutReset,
        )
        val after = PracticeReducer.reduce(resting, PracticeEvent.NextImage)

        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    @Test
    fun previousImage_atFirstPage_isNoOp() {
        val first = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 12_000L,
            config = config,
        )

        assertSame(first, PracticeReducer.reduce(first, PracticeEvent.PreviousImage))
    }

    @Test
    fun nextImage_atLastPage_isNoOp() {
        val last = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = imageIds.lastIndex,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 12_000L,
            config = config,
        )

        assertSame(last, PracticeReducer.reduce(last, PracticeEvent.NextImage))
    }

    @Test
    fun navigation_fromPaused_preservesPausedStatus() {
        val paused = PracticeState(
            status = PracticeStatus.PAUSED,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.REST,
            remainingMs = 4_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(paused, PracticeEvent.NextImage)

        assertEquals(PracticeStatus.PAUSED, after.status)
        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    // ── SkipRest ──────────────────────────────────────────────────

    @Test
    fun skipRest_duringRest_movesToNextImage() {
        val resting = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.REST,
            remainingMs = 25_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(resting, PracticeEvent.SkipRest)
        assertEquals(2, after.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, after.phase)
        assertEquals(60_000L, after.remainingMs)
    }

    @Test
    fun skipRest_duringCountdown_isNoOp() {
        val countdown = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = 30_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(countdown, PracticeEvent.SkipRest)
        assertSame(countdown, after)
    }

    @Test
    fun skipRest_duringPaused_isNoOp() {
        // The skip button only renders during RUNNING + REST.
        // A stale dispatch (e.g. from a debug hook) during
        // PAUSED must not advance the state — the user
        // explicitly asked to be paused.
        val pausedRest = PracticeState(
            status = PracticeStatus.PAUSED,
            imageIds = imageIds,
            currentIndex = 1,
            phase = PracticePhase.REST,
            remainingMs = 25_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(pausedRest, PracticeEvent.SkipRest)
        assertSame(pausedRest, after)
    }

    // ── Exit ──────────────────────────────────────────────────────

    @Test
    fun exit_fromAnyState_returnsIdle() {
        val running = PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = imageIds,
            currentIndex = 3,
            phase = PracticePhase.REST,
            remainingMs = 5_000L,
            config = config,
        )
        val after = PracticeReducer.reduce(running, PracticeEvent.Exit)
        assertSame(PracticeState.Idle, after)
    }

    // ── progressLabel edge cases ──────────────────────────────────

    @Test
    fun progressLabel_idleIsEmpty() {
        // Empty string (not "0 / 0") so the UI can early-
        // return and avoid a 0/0 display on first launch.
        assertEquals("", PracticeState.Idle.progressLabel)
    }

    @Test
    fun progressLabel_finishedShowsFullCount() {
        val finished = PracticeState(
            status = PracticeStatus.FINISHED,
            imageIds = listOf("a", "b", "c"),
            currentIndex = 2,
            phase = PracticePhase.REST,
            remainingMs = 0L,
            config = config,
        )
        assertEquals("3 / 3", finished.progressLabel)
    }

    // ── Multi-step walk: realistic 3-image session ────────────────

    @Test
    fun fullSession_walksThroughAllPhases() {
        val ids = listOf("a", "b", "c")
        var s = PracticeReducer.reduce(
            PracticeState.Idle,
            PracticeEvent.Start(ids, config),
        )
        // Image A: COUNTDOWN 60s
        assertEquals(0, s.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, s.phase)
        assertEquals(60_000L, s.remainingMs)

        // Ticks down to 0 → goes to REST
        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))
        assertEquals(0, s.currentIndex)
        assertEquals(PracticePhase.REST, s.phase)
        assertEquals(30_000L, s.remainingMs)

        // User pauses mid-rest
        s = PracticeReducer.reduce(s, PracticeEvent.Pause)
        assertEquals(PracticeStatus.PAUSED, s.status)
        assertEquals(30_000L, s.remainingMs)

        // Resume, then tick through to 0
        s = PracticeReducer.reduce(s, PracticeEvent.Resume)
        assertEquals(PracticeStatus.RUNNING, s.status)
        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))

        // Should now be on image B's COUNTDOWN
        assertEquals(1, s.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, s.phase)
        assertEquals(60_000L, s.remainingMs)
        assertEquals("2 / 3", s.progressLabel)

        // Skip the rest of the way: COUNTDOWN → REST → FINISHED
        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))  // B's COUNTDOWN end
        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))  // B's REST end → C's COUNTDOWN
        assertEquals(2, s.currentIndex)
        assertEquals(PracticePhase.COUNTDOWN, s.phase)

        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))  // C's COUNTDOWN end
        // Non-zero rest config → C's REST (the "you finished!" breath)
        assertEquals(2, s.currentIndex)
        assertEquals(PracticePhase.REST, s.phase)
        assertEquals(PracticeStatus.RUNNING, s.status)

        s = PracticeReducer.reduce(s, PracticeEvent.OnTick(0L))  // C's REST end → FINISHED
        assertEquals(PracticeStatus.FINISHED, s.status)
        assertEquals("3 / 3", s.progressLabel)
    }

    // ── config defaults & bounds ──────────────────────────────────

    @Test
    fun practiceConfig_defaultsAreReasonable() {
        val defaults = PracticeConfig()
        assertEquals(60_000L, defaults.practiceDurationMs)
        assertEquals(30_000L, defaults.restDurationMs)
        assertFalse(defaults.shuffle)
        assertTrue(defaults.resetTimerOnImageChange)
        // V_NOW (2026-06-08 user feedback "去掉倒计时提示音的功能"):
        // [PracticeConfig.soundEnabled] was removed entirely.
        // The countdown-sound feature is gone; the practice
        // mode is silent by design. No assertion needed
        // for the removed field.
    }

    @Test
    fun practiceConfig_rejectsOutOfRange() {
        // Too short
        try {
            PracticeConfig(practiceDurationMs = 1_000L)
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
        // Too long
        try {
            PracticeConfig(practiceDurationMs = 4 * 60 * 60 * 1000L)  // 4h
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
        // Rest can be 0 (no rest mode)
        PracticeConfig(restDurationMs = 0L)  // does not throw
    }

    // ── helpers not used here, but document the contract ─────────

    @Test
    fun currentImageId_isNullWhenIdle() {
        assertNull(PracticeState.Idle.currentImageId)
    }

    @Test
    fun isLastImage_isFalseWhenIdle() {
        assertFalse(PracticeState.Idle.isLastImage)
    }
}
