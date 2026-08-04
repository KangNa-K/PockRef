package com.pockref.app.model

/**
 * V_NOW (2026-06-08 速写练习): the user-facing configuration for a
 * speed-sketching practice session. Held inside [PracticeState] so the
 * active session can render the same values to the user that were chosen
 * at start time (the post-start reducer rejects configuration changes —
 * mid-session duration tweaks were explicitly cut from the spec to keep
 * the focus on drawing, not fiddling).
 *
 * Persisted to [com.pockref.app.data.AppPreferences.lastPracticeConfig]
 * so the next time the user opens the pre-flight config sheet, the
 * chips/switches come up with their last choice (no first-launch
 * "everything is default 60s/30s/shuffle-off" awkwardness).
 *
 * Default values are tuned for an intermediate-level speed-sketcher:
 *   - [practiceDurationMs] = 60s (one minute per image is the most
 *     common practice length for figure / gesture work)
 *   - [restDurationMs] = 30s (enough to roll a fresh sheet of paper
 *     or look up a reference, not so long that the user loses the
 *     flow)
 *   - [shuffle] = false (selection order is what the user expected to
 *     practice in — surprise ordering is opt-in)
 *   - [resetTimerOnImageChange] = true (manually changing images gives
 *     the new image a complete drawing interval by default)
 *
 * V_NOW (user feedback "去掉倒计时提示音的功能"): the previous
 * `soundEnabled` field was removed entirely. The countdown-sound
 * feature was scoped for commit 4 (audio) but the user decided the
 * practice mode should be silent — only the visual countdown text
 * remains. Removing the field rather than just hiding the switch
 * means the data class no longer carries dead state, and the
 * [com.pockref.app.data.AppPreferences.lastPracticeConfig] getter
 * doesn't need a [coerceIn] / null-handling branch for a value
 * that nothing reads. Existing users with the
 * `last_practice_sound` pref on disk will have the key ignored
 * (no migration needed; new write path drops the key).
 */
data class PracticeConfig(
    val practiceDurationMs: Long = 60_000L,
    val restDurationMs: Long = 30_000L,
    val shuffle: Boolean = false,
    val resetTimerOnImageChange: Boolean = true,
) {
    init {
        // Defensive bounds so a corrupted pref (manually edited
        // SharedPreferences, an out-of-range value from a future
        // configuration source, etc.) cannot produce a 0-ms timer
        // or a 24-hour countdown. The pre-flight config sheet's
        // sliders already clamp to these ranges, but the clamp
        // belongs at the model layer too — a future "load from
        // .pur" path that hydrates a config from a scene file
        // would otherwise trust the on-disk bytes blindly.
        require(practiceDurationMs in MIN_PRACTICE_MS..MAX_PRACTICE_MS) {
            "practiceDurationMs=$practiceDurationMs out of range " +
                "[$MIN_PRACTICE_MS, $MAX_PRACTICE_MS]"
        }
        require(restDurationMs in MIN_REST_MS..MAX_REST_MS) {
            "restDurationMs=$restDurationMs out of range " +
                "[$MIN_REST_MS, $MAX_REST_MS]"
        }
    }

    companion object {
        /** 5s floor — anything shorter isn't a "practice", it's a flicker. */
        const val MIN_PRACTICE_MS: Long = 5_000L
        /** 1h ceiling — a 90-minute single-image hold isn't practice,
         *  it's a stuck timer. Long practice sessions are achievable
         *  by combining this with image count, not by lengthening
         *  the per-image hold. */
        const val MAX_PRACTICE_MS: Long = 3_600_000L
        /** 0s allowed: the "no rest" mode where COUNTDOWN transitions
         *  directly to the next image (skipping REST entirely). */
        const val MIN_REST_MS: Long = 0L
        /** 10min ceiling — the rest interval is meant to be a brief
         *  breath, not a lunch break. */
        const val MAX_REST_MS: Long = 600_000L
    }
}

/**
 * Lifecycle of a practice session. [IDLE] is the "no session in
 * progress" state — [PockRefUiState.practiceState] is `null` in this
 * state (the [com.pockref.app.ui.PureRefViewModelPractice.exit]
 * reducer path explicitly nulls it out). [FINISHED] is kept as a
 * non-null [PracticeState] so the UI can render a "completed N / N
 * images in M:SS" summary screen — see
 * [PracticeState.progressLabel] for the string the UI reads.
 */
enum class PracticeStatus { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * Which phase of the cycle the current image is in. [COUNTDOWN] is
 * "show this image, the user is drawing"; [REST] is "dim the image,
 * the user is taking a break between images". Phase transitions
 * happen inside the reducer when the ticker's [remainingMs] reaches
 * 0 (see [com.pockref.app.ui.PracticeReducer]).
 */
enum class PracticePhase { COUNTDOWN, REST }

/**
 * Live state of a practice session. Held in
 * [PockRefUiState.practiceState]; `null` when no session is
 * active. The session is a small state machine with two phase
 * dimensions ([status] × [phase]) and a single rolling [remainingMs]
 * counter that the ticker updates each frame.
 *
 * Read sites that need to know "is the user currently in a
 * practice session?" should use [isActive] (= status != IDLE),
 * which subsumes both RUNNING and PAUSED and FINISHED. Use
 * [isRunning] when you specifically need "the ticker is the
 * authority right now" (e.g. the practice screen's
 * `LaunchedEffect` that drives the count-down animation).
 *
 * Invariants enforced by [com.pockref.app.ui.PracticeReducer]:
 *   1. [status] == IDLE ⇒ [imageIds] is empty and [remainingMs] == 0
 *   2. [status] == RUNNING / PAUSED ⇒ [imageIds] is non-empty
 *   3. [currentIndex] is always in `0 until imageIds.size`
 *   4. [remainingMs] is in `0..phaseDuration`, where phaseDuration
 *      is [PracticeConfig.practiceDurationMs] for COUNTDOWN and
 *      [PracticeConfig.restDurationMs] for REST
 *   5. The reducer never mutates an existing session's [config]
 *      — see the kdoc on [com.pockref.app.model.PracticeConfig]
 *      for why mid-session duration tweaks were cut from the spec
 *
 * The convenience getters ([totalImages], [currentImageId],
 * [isLastImage], [progressLabel]) are derived (no backing
 * fields) so the reducer can stay simple and tests can assert
 * the derived values without re-computing them by hand.
 */
data class PracticeState(
    val status: PracticeStatus = PracticeStatus.IDLE,
    val imageIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val phase: PracticePhase = PracticePhase.COUNTDOWN,
    val remainingMs: Long = 0L,
    val config: PracticeConfig = PracticeConfig(),
) {
    val totalImages: Int get() = imageIds.size

    val isActive: Boolean get() = status != PracticeStatus.IDLE
    val isRunning: Boolean get() = status == PracticeStatus.RUNNING
    val isPaused: Boolean get() = status == PracticeStatus.PAUSED
    val isFinished: Boolean get() = status == PracticeStatus.FINISHED

    /** Id of the image the user should currently be drawing,
     *  or `null` if the state is IDLE / no images. */
    val currentImageId: String? get() = imageIds.getOrNull(currentIndex)

    /** True when the current image is the last one in [imageIds].
     *  Used by the reducer to decide whether to transition to
     *  FINISHED after the next REST phase ends (vs starting a
     *  new COUNTDOWN for the next image). */
    val isLastImage: Boolean get() =
        imageIds.isNotEmpty() && currentIndex == imageIds.lastIndex

    /** "3 / 10" style progress label for the top-bar overlay.
     *  Returns "" when no session is active so the UI can
     *  early-return on an empty string and avoid a 0/0
     *  display on first launch. Returns the full total when
     *  the session finished so the user sees the completed
     *  count one last time. */
    val progressLabel: String get() = when {
        totalImages == 0 -> ""
        status == PracticeStatus.FINISHED -> "$totalImages / $totalImages"
        else -> "${currentIndex + 1} / $totalImages"
    }

    companion object {
        /** Singleton "no session" state. Used by the reducer's
         *  [com.pockref.app.ui.PracticeEvent.Exit] handler and
         *  by [com.pockref.app.ui.PureRefViewModelPractice] as
         *  a safe default when reading
         *  [PockRefUiState.practiceState] before init. */
        val Idle: PracticeState = PracticeState()
    }
}
