package com.pockref.app.ui

import com.pockref.app.model.PracticeConfig
import com.pockref.app.model.PracticePhase
import com.pockref.app.model.PracticeState
import com.pockref.app.model.PracticeStatus
import kotlinx.coroutines.flow.update

/**
 * V_NOW (2026-06-08 速写练习): internal reducer API for the practice
 * state machine. Kept separate from [PockRefAction] so the reducer
 * stays a pure `(state, event) -> state` function with no Android /
 * PockRef dependencies — this is the surface the unit tests target.
 *
 * The controller [PockRefViewModelPractice] is the only caller:
 * it translates [PockRefAction.StartPractice] etc. into the matching
 * [PracticeEvent] and pipes the result through
 * [PockRefViewModel]._uiState.update.
 */
internal sealed interface PracticeEvent {
    data class Start(
        val imageIds: List<String>,
        val config: PracticeConfig,
    ) : PracticeEvent
    data class OnTick(val remainingMs: Long) : PracticeEvent
    data object Pause : PracticeEvent
    data object Resume : PracticeEvent
    data object SkipRest : PracticeEvent
    data object Exit : PracticeEvent
}

/**
 * V_NOW: pure reducer for the practice state machine. No Android
 * types, no coroutines, no side effects — every method takes a
 * [PracticeState] and returns the next [PracticeState]. This is the
 * testable unit; the [PockRefViewModelPractice] controller wraps it
 * with state-write + persistence side effects.
 *
 * The reducer is deliberately a single `object` (not a class) so
 * tests can call [reduce] without constructing a dependency graph.
 * If a future feature needs an injected clock, the signature can
 * grow a third parameter without breaking existing call sites.
 */
internal object PracticeReducer {

    fun reduce(state: PracticeState, event: PracticeEvent): PracticeState =
        when (event) {
            is PracticeEvent.Start -> start(state, event)
            is PracticeEvent.OnTick -> onTick(state, event.remainingMs)
            PracticeEvent.Pause -> pause(state)
            PracticeEvent.Resume -> resume(state)
            PracticeEvent.SkipRest -> skipRest(state)
            PracticeEvent.Exit -> PracticeState.Idle
        }

    /** Begin a new session. Rejects empty list (controller reports
     *  the error to the UI) and rejects if a session is already
     *  active (caller must [PracticeEvent.Exit] first). When
     *  [PracticeConfig.shuffle] is on, the image order is randomized
     *  via [shuffled] (Kotlin's default Random — non-deterministic
     *  but the user only sees the order they get, so determinism
     *  isn't a requirement here). */
    private fun start(
        state: PracticeState,
        event: PracticeEvent.Start,
    ): PracticeState {
        if (event.imageIds.isEmpty()) return state
        if (state.status != PracticeStatus.IDLE) return state
        val orderedIds = if (event.config.shuffle) {
            event.imageIds.shuffled()
        } else {
            event.imageIds
        }
        return PracticeState(
            status = PracticeStatus.RUNNING,
            imageIds = orderedIds,
            currentIndex = 0,
            phase = PracticePhase.COUNTDOWN,
            remainingMs = event.config.practiceDurationMs,
            config = event.config,
        )
    }

    /**
     * Ticker update. Three branches:
     *   - status != RUNNING: no-op (the ticker should not be
     *     firing while PAUSED / FINISHED; this is a defensive
     *     check in case a stale frame slips through after a pause)
     *   - remainingMs > 0: refresh the count
     *   - remainingMs <= 0: phase ended — transition to the
     *     next state per the spec (see [advanceOrFinish] for
     *     the COUNTDOWN / REST → next-image-or-FINISHED rules)
     *
     * Negative values are accepted (treated as "phase ended")
     * so a 16ms-frame jitter that overshoots zero by a few
     * ms doesn't strand the session in an inconsistent state.
     */
    private fun onTick(state: PracticeState, remainingMs: Long): PracticeState {
        if (state.status != PracticeStatus.RUNNING) return state
        if (remainingMs > 0L) return state.copy(remainingMs = remainingMs)
        return when (state.phase) {
            PracticePhase.COUNTDOWN -> {
                if (state.config.restDurationMs > 0L) {
                    // Per spec: after every COUNTDOWN, take a
                    // rest (even after the last image) so the
                    // user gets a "you finished!" breath
                    // before the FINISHED screen appears.
                    state.copy(
                        phase = PracticePhase.REST,
                        remainingMs = state.config.restDurationMs,
                    )
                } else {
                    // 0s rest mode: COUNTDOWN flows directly
                    // to the next image (or FINISHED).
                    advanceOrFinish(state)
                }
            }
            PracticePhase.REST -> advanceOrFinish(state)
        }
    }

    /** Move to the next image and start its COUNTDOWN, or finish
     *  the session if the current image was the last one. */
    private fun advanceOrFinish(state: PracticeState): PracticeState =
        if (state.currentIndex < state.imageIds.lastIndex) {
            state.copy(
                currentIndex = state.currentIndex + 1,
                phase = PracticePhase.COUNTDOWN,
                remainingMs = state.config.practiceDurationMs,
            )
        } else {
            state.copy(
                status = PracticeStatus.FINISHED,
                remainingMs = 0L,
            )
        }

    private fun pause(state: PracticeState): PracticeState =
        if (state.status == PracticeStatus.RUNNING) {
            state.copy(status = PracticeStatus.PAUSED)
        } else {
            state
        }

    private fun resume(state: PracticeState): PracticeState =
        if (state.status == PracticeStatus.PAUSED) {
            state.copy(status = PracticeStatus.RUNNING)
        } else {
            state
        }

    /** User explicitly skipped the current rest. No-op if not
     *  currently in REST (the "Skip rest" button only renders
     *  during REST, so the guard is defense-in-depth). */
    private fun skipRest(state: PracticeState): PracticeState =
        if (state.status == PracticeStatus.RUNNING &&
            state.phase == PracticePhase.REST
        ) {
            advanceOrFinish(state)
        } else {
            state
        }
}

/**
 * V_NOW (2026-06-08 速写练习): thin controller that pipes
 * [PockRefAction] variants through [PracticeReducer] and
 * applies the result to [PockRefViewModel].uiState. Follows
 * the same pattern as [PockRefViewModelSelection] /
 * [PockRefViewModelTransform]: an [internal] class that
 * holds a reference back to the main VM and mutates
 * `_uiState` via `update { ... }`.
 *
 * Side effects owned by the controller (not the reducer):
 *   - persisting the active [PracticeConfig] to
 *     [com.pockref.app.data.AppPreferences.lastPracticeConfig]
 *     on Start (so the next session's pre-flight sheet comes
 *     up with the user's last choices)
 *   - reporting an [com.pockref.app.ui.UiError] when Start is
 *     called with an empty image list (the reducer silently
 *     rejects, but the user needs to know why nothing happened)
 */
internal class PockRefViewModelPractice(
    private val mainVm: PockRefViewModel,
) {

    internal fun startPractice(imageIds: List<String>, config: PracticeConfig) {
        if (imageIds.isEmpty()) {
            mainVm.reportError("请先选择至少一张图片再开始速写练习")
            return
        }
        val current = mainVm.uiState.value.practiceState ?: PracticeState.Idle
        val next = PracticeReducer.reduce(
            current,
            PracticeEvent.Start(imageIds, config),
        )
        if (next === current) {
            // Reducer rejected (already in a session). The UI
            // is supposed to not let the user dispatch this
            // in that state, but if it does, we no-op rather
            // than thrash the state.
            return
        }
        // Persist last-used config for the next session's
        // pre-flight sheet (separate from the active state
        // — we want the defaults to come up even after Exit).
        mainVm.appPrefs.lastPracticeConfig = config
        mainVm._uiState.update { it.copy(practiceState = next) }
    }

    internal fun onTick(remainingMs: Long) {
        val current = mainVm.uiState.value.practiceState ?: return
        if (current.status != PracticeStatus.RUNNING) return
        val next = PracticeReducer.reduce(current, PracticeEvent.OnTick(remainingMs))
        if (next === current) return
        mainVm._uiState.update { it.copy(practiceState = next) }
    }

    internal fun pause() {
        val current = mainVm.uiState.value.practiceState ?: return
        val next = PracticeReducer.reduce(current, PracticeEvent.Pause)
        if (next === current) return
        mainVm._uiState.update { it.copy(practiceState = next) }
    }

    internal fun resume() {
        val current = mainVm.uiState.value.practiceState ?: return
        val next = PracticeReducer.reduce(current, PracticeEvent.Resume)
        if (next === current) return
        mainVm._uiState.update { it.copy(practiceState = next) }
    }

    internal fun skipRest() {
        val current = mainVm.uiState.value.practiceState ?: return
        val next = PracticeReducer.reduce(current, PracticeEvent.SkipRest)
        if (next === current) return
        mainVm._uiState.update { it.copy(practiceState = next) }
    }

    internal fun exit() {
        val current = mainVm.uiState.value.practiceState ?: return
        val next = PracticeReducer.reduce(current, PracticeEvent.Exit)
        if (next === current) return
        // Exit collapses back to null (not the [PracticeState.Idle]
        // sentinel — the null lets the [PockRefApp] composables
        // early-return with `if (state.practiceState != null)`,
        // which is the more common read pattern than checking
        // `state.practiceState?.status == IDLE`).
        mainVm._uiState.update { it.copy(practiceState = null) }
    }
}
