package com.pockref.app.data

import android.content.Context
import android.content.SharedPreferences
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.spen.SpenMode

/**
 * Lightweight wrapper around the app's user-preferences [SharedPreferences].
 *
 * Right now this only persists the user's chosen "default background preset" —
 * the swatch they last picked from the toolbar palette. The selection lives
 * across app restarts and is reapplied when a new scene is created (or the
 * current one is closed), so the canvas always opens on the colour the user
 * last chose instead of a hard-coded default.
 *
 * The [Custom] preset is intentionally not persisted: by definition it is a
 * user-supplied RGB triple that varies between sessions, and falling back to
 * the most recent named preset keeps the default discoverable.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultBackgroundPreset: BackgroundPreset
        get() {
            val stored = prefs.getString(KEY_DEFAULT_BG_PRESET, null) ?: return BackgroundPreset.DefaultPreset
            return runCatching { BackgroundPreset.valueOf(stored) }
                .getOrDefault(BackgroundPreset.DefaultPreset)
        }
        set(value) {
            if (value == BackgroundPreset.Custom) {
                return
            }
            prefs.edit().putString(KEY_DEFAULT_BG_PRESET, value.name).apply()
        }

    var frostedGlassEnabled: Boolean
        get() = prefs.getBoolean(KEY_FROSTED_GLASS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FROSTED_GLASS_ENABLED, value).apply()
        }

    var themeMode: ThemeMode
        get() {
            val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.LIGHT
            return runCatching { ThemeMode.valueOf(stored) }
                .getOrDefault(ThemeMode.LIGHT)
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /**
     * Whether the edge-to-edge snap feature is enabled. Default ON — most
     * users want it — but exposed as a setting because some users find
     * snap fights their hand. The setting lives in the same prefs file
     * as the rest of the user-visible toggles so it survives restart and
     * follows the user's last choice.
     */
    var edgeSnapEnabled: Boolean
        get() = prefs.getBoolean(KEY_EDGE_SNAP_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EDGE_SNAP_ENABLED, value).apply()
        }

    /**
     * Whether rotation snaps to 0°/90°/180°/270° during a transform
     * gesture. Default ON — most users want angles to "click" to the
     * cardinal orientations. Independent of [edgeSnapEnabled]; the user
     * can opt out of one without affecting the other.
     */
    var rotationSnapEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROTATION_SNAP_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ROTATION_SNAP_ENABLED, value).apply()
        }

    /**
     * Set of [com.pockref.app.ui.GestureId] strings the user has
     * explicitly disabled. Stored as a StringSet so default-on
     * gestures need no per-key Boolean or SharedPreferences
     * migration. Default empty → every default-on gesture is
     * enabled unless the user has toggled it off in the settings
     * panel. The default-off single-finger canvas pan mode has
     * its own Boolean below. Read by
     * [com.pockref.app.ui.PockRefViewModel] when hydrating
     * [com.pockref.app.model.PockRefUiState.gestureToggles] and
     * re-written by the [com.pockref.app.ui.PockRefAction.SetGestureEnabled]
     * action handler.
     */
    var disabledGestureIds: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED_GESTURE_IDS, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_DISABLED_GESTURE_IDS, value).apply()
        }

    var singleFingerCanvasPanEnabled: Boolean
        get() = prefs.getBoolean(KEY_SINGLE_FINGER_CANVAS_PAN_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SINGLE_FINGER_CANVAS_PAN_ENABLED, value).apply()
        }

    /**
     * V_NOW: `spenButtonEnabled` pref removed. The previous S Pen
     * detection path used a SYSTEM_ALERT_WINDOW overlay that the
     * user could grant or deny; the pref was the in-app
     * enable/disable. The new detection path runs in
     * `MainActivity.dispatchTouchEvent` /
     * `dispatchGenericMotionEvent` and needs no permission, so
     * there is no permission-denied state to gate on. If a future
     * settings-page entry wants an in-app S Pen button toggle,
     * add it back here with a fresh KEY and a fresh default
     * (true) — the old `spen_button_enabled` key has been left
     * on disk for one upgrade cycle so users who toggled it off
     * don't suddenly get default-ON behavior, but the new
     * detection ignores it entirely.
     */
    // (removed: spenButtonEnabled getter / setter)

    /**
     * V_NOW: S Pen side-button gesture mode. User picks this in
     * the settings panel (see PockRefApp.kt settings sheet —
     * RadioButton pair). V_NOW (D3): default is now
     * [SpenMode.DOUBLE_TAP] — pure tap semantics, no
     * holding required. Users who preferred the old
     * press-and-hold behaviour can switch back to
     * [SpenMode.PRESS_AND_HOLD] from the settings sheet.
     *
     * `apply()` is fire-and-forget; the read site
     * (`MainActivity.onCreate` → constructs detector with the
     * value) and write site (settings panel) are decoupled
     * across UI recompositions, so the user sees the new mode
     * after dismissing + reopening the settings sheet (or
     * after the next process restart, whichever comes first).
     * The detector is rebuilt with the new mode on the next
     * MainActivity onCreate, not hot-swapped mid-gesture —
     * in-flight gestures from the previous mode finish in the
     * old state machine before the swap, which is the safe
     * behavior (no half-state from a hot-swap race).
     */
    var spenMode: SpenMode
        get() {
            val stored = prefs.getString(KEY_SPEN_MODE, null) ?: return SpenMode.DOUBLE_TAP
            return runCatching { SpenMode.valueOf(stored) }
                .getOrDefault(SpenMode.DOUBLE_TAP)
        }
        set(value) {
            prefs.edit().putString(KEY_SPEN_MODE, value.name).apply()
        }

    /**
     * V_NOW: auto-save interval, in milliseconds. The
     * [com.pockref.app.data.AutoSaveManager] reads this on every
     * `delay(intervalMs)` cycle inside its background coroutine, so
     * changing the value here takes effect on the NEXT cycle (no
     * need to restart the loop). Default is
     * [com.pockref.app.data.AutoSaveManager.DEFAULT_INTERVAL_MS]
     * (5 minutes) — the same value the project used before the
     * user-facing setting was added; keeping it as the default
     * preserves prior behavior for users who never touch the new
     * setting.
     *
     * Slider range in the UI: 1 minute (60_000) to 1 hour
     * (3_600_000). Values outside that range are clamped by the
     * slider's `valueRange` and never reach this setter, but the
     * setter tolerates them anyway in case some future
     * configuration source writes a wider value (e.g. a debug
     * toggle that says "save every 5 seconds" for reproducing
     * auto-save bugs).
     */
    var autoSaveIntervalMs: Long
        get() = prefs.getLong(
            KEY_AUTO_SAVE_INTERVAL_MS,
            com.pockref.app.data.AutoSaveManager.DEFAULT_INTERVAL_MS,
        )
        set(value) {
            prefs.edit().putLong(KEY_AUTO_SAVE_INTERVAL_MS, value).apply()
        }

    /**
     * V_NOW: master switch for the auto-save feature.
     * `true` (default) means the [com.pockref.app.data.AutoSaveManager]
     * coroutine runs its normal `delay(intervalMs)` cycle and
     * writes backup files when the content has changed. `false`
     * short-circuits `shouldAutoSave()` to `false` (and the
     * settings sheet dims the interval slider). The
     * [PockRefUiState.autoSaveEnabled] mirror in the ViewModel
     * reads from this on init so the Switch renders the
     * correct state on first launch.
     *
     * Default: `true`. We default to ON (preserving prior
     * behavior for users who never touch the new switch) —
     * the alternative (default OFF) would silently regress
     * auto-save for the entire installed base on first
     * launch with the new build.
     */
    var autoSaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SAVE_ENABLED, value).apply()
        }

    /**
     * Whether save and export pickers should start in the folder selected
     * below. Default false means DocumentsUI chooses its own location.
     */
    var defaultSavePathEnabled: Boolean
        get() {
            // The previous build shipped this key defaulted to true for a
            // Downloads-only switch. Migrate that obsolete setting once so
            // the new opt-in custom-directory behavior starts disabled.
            if (!prefs.getBoolean(KEY_DEFAULT_SAVE_PATH_MIGRATION_DONE, false)) {
                prefs.edit()
                    .remove(KEY_DEFAULT_SAVE_PATH_ENABLED)
                    .putBoolean(KEY_DEFAULT_SAVE_PATH_MIGRATION_DONE, true)
                    .apply()
            }
            return prefs.getBoolean(KEY_DEFAULT_SAVE_PATH_ENABLED, false)
        }
        set(value) {
            prefs.edit().putBoolean(KEY_DEFAULT_SAVE_PATH_ENABLED, value).apply()
        }

    /**
     * Persisted Storage Access Framework tree URI used as the default
     * destination for user-initiated saves and exports. A null value means
     * no custom folder has been chosen, so callers let DocumentsUI choose
     * the initial location.
     */
    var defaultSaveDirectoryUri: String?
        get() = prefs.getString(KEY_DEFAULT_SAVE_DIRECTORY_URI, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) {
                    remove(KEY_DEFAULT_SAVE_DIRECTORY_URI)
                } else {
                    putString(KEY_DEFAULT_SAVE_DIRECTORY_URI, value)
                }
            }.apply()
        }

    /** Display name cached from the selected tree for the settings summary. */
    var defaultSaveDirectoryName: String?
        get() = prefs.getString(KEY_DEFAULT_SAVE_DIRECTORY_NAME, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) {
                    remove(KEY_DEFAULT_SAVE_DIRECTORY_NAME)
                } else {
                    putString(KEY_DEFAULT_SAVE_DIRECTORY_NAME, value)
                }
            }.apply()
        }

    /**
     * V_NOW: minimap custom position (x, y) in raw screen pixels.
     * Persisted so a fresh launch (or process restart) restores
     * the minimap to wherever the user last dragged it. Pairs
     * with [minimapDockEdge] / [minimapCollapsed] to fully
     * restore the minimap's last state. The raw pixel value
     * works for fixed-size panels because the panel size is
     * deterministic (see [CanvasMinimap]'s computeSceneBboxForMinimap);
     * a future tablet rotation might want to clamp to the new
     * viewport bounds on hydration.
     */
    var minimapPositionX: Float
        get() = prefs.getFloat(KEY_MINIMAP_POSITION_X, Float.NaN)
        set(value) {
            prefs.edit().putFloat(KEY_MINIMAP_POSITION_X, value).apply()
        }
    var minimapPositionY: Float
        get() = prefs.getFloat(KEY_MINIMAP_POSITION_Y, Float.NaN)
        set(value) {
            prefs.edit().putFloat(KEY_MINIMAP_POSITION_Y, value).apply()
        }

    /**
     * V_NOW: which edge the minimap is currently docked to
     * (or NONE for free-positioned). Persisted as the
     * [com.pockref.app.model.MinimapDockEdge] name string;
     * any unparseable value falls back to NONE so a stale
     * value (e.g. an enum that was renamed in a future
     * version) doesn't brick the launch path.
     */
    var minimapDockEdge: com.pockref.app.model.MinimapDockEdge
        get() {
            val stored = prefs.getString(KEY_MINIMAP_DOCK_EDGE, null) ?: return com.pockref.app.model.MinimapDockEdge.NONE
            return runCatching { com.pockref.app.model.MinimapDockEdge.valueOf(stored) }
                .getOrDefault(com.pockref.app.model.MinimapDockEdge.NONE)
        }
        set(value) {
            prefs.edit().putString(KEY_MINIMAP_DOCK_EDGE, value.name).apply()
        }

    /**
     * V_NOW: whether the minimap starts the session in the
     * collapsed (edge-icon) state. When the user docks the
     * minimap to an edge, both [minimapDockEdge] AND
     * [minimapCollapsed] flip to non-default. The collapsed
     * flag is persisted separately so the auto-collapse
     * timer (3s of no interaction, matching the top toolbar)
     * can flip collapsed without also touching dockEdge.
     */
    var minimapCollapsed: Boolean
        get() = prefs.getBoolean(KEY_MINIMAP_COLLAPSED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MINIMAP_COLLAPSED, value).apply()
        }

    /**
     * V_NOW: pinch-to-zoom scale for the full minimap panel.
     * 1.0f = default (canvas-derived). Persisted so the
     * user's last pinch survives a process kill / fresh
     * launch. NaN / out-of-range values are clamped on read
     * (defensive — a corrupted pref should not produce a
     * 0.0-px panel).
     */
    var minimapZoomScale: Float
        get() = prefs.getFloat(KEY_MINIMAP_ZOOM_SCALE, 1f).coerceIn(0.5f, 2.0f)
        set(value) {
            prefs.edit().putFloat(KEY_MINIMAP_ZOOM_SCALE, value.coerceIn(0.5f, 2.0f)).apply()
        }

    /**
     * V_NOW (2026-06-08 "global image lock" feature):
     * Whether the app-wide image lock is on. When `true`,
     * every image on the canvas is treated as locked —
     * users can't select, move, rotate, scale, or
     * delete any image. The view-only flag is toggled
     * by the top-right lock button in [PockRefApp] and
     * persisted across launches so a user who locks the
     * canvas can close and reopen the app and find it
     * still locked.
     *
     * Default `false` — a fresh install must NOT come
     * up with all images locked, that would be hostile
     * to anyone who imported a few pictures on first
     * launch.
     */
    var globalImageLock: Boolean
 get() = prefs.getBoolean(KEY_GLOBAL_IMAGE_LOCK, false)
 set(value) {
 prefs.edit().putBoolean(KEY_GLOBAL_IMAGE_LOCK, value).apply()
 }

 /**
 * V_NOW (2026-06-09 user spec "长按全局锁定时画布不可移动
 * 和旋转,同时按钮会变红"): the user's "hard lock" mode
 * — independent of [globalImageLock] — that freezes
 * the canvas itself (pan / zoom / minimap interactions
 * all blocked). Persisted so a user in hard mode can
 * restart the app and find the canvas still frozen.
 * Default `false`.
 */
 var lockCanvasFrozen: Boolean
 get() = prefs.getBoolean(KEY_LOCK_CANVAS_FROZEN, false)
 set(value) {
 prefs.edit().putBoolean(KEY_LOCK_CANVAS_FROZEN, value).apply()
 }

    /**
     * V_NOW (2026-06-08 速写练习): the user's last-used
     * [com.pockref.app.model.PracticeConfig] (practice
     * duration / rest duration / shuffle / reset-on-image-change), persisted
     * so the pre-flight config sheet (commit 2) comes up
     * with the user's last choices instead of the hard-coded
     * defaults every time.
     *
     * Returns `null` on a fresh install or after the user
     * clears app data — the pre-flight sheet then falls back
     * to [com.pockref.app.model.PracticeConfig]'s default
     * constructor (60s / 30s / shuffle-off / reset-on).
     *
     * Storage uses four separate primitive keys (one per
     * [com.pockref.app.model.PracticeConfig] field) rather
     * than a serialized blob. This matches the rest of the
     * settings in this file (each field is a separate
     * SharedPreferences entry) and keeps the read-side logic
     * trivial — no JSON / parcelable dance for a 4-field
     * record. The three original keys remain required so
     * existing installs migrate cleanly; the new reset key
     * defaults to `true` when absent.
     * The setter validates the constructed config via the
     * `init { require(...) }` block in [PracticeConfig] —
     * a corrupted pref with an out-of-range duration (e.g.
     * manually edited SharedPreferences) throws
     * [IllegalArgumentException] at write-time, which the
     * caller ([PockRefViewModelPractice.startPractice])
     * will not see because the corruption would have
     * already thrown on the previous read; in that case
     * the getter's default fallback kicks in and the
     * setter is never reached. Defensive `coerceIn` on
     * each read site further protects against a single
     * bad pref value corrupting the whole config.
     */
    var lastPracticeConfig: com.pockref.app.model.PracticeConfig?
        get() {
            // The three original keys absent → first launch since
            // the practice feature was added. Return null
            // and let the caller fall back to
            // [PracticeConfig]'s default ctor.
            if (!prefs.contains(KEY_LAST_PRACTICE_DURATION_MS) ||
                !prefs.contains(KEY_LAST_REST_DURATION_MS) ||
                !prefs.contains(KEY_LAST_PRACTICE_SHUFFLE)
            ) {
                return null
            }
            // Defensive clamping: if a manually edited
            // pref holds e.g. -1 or 99_999_999ms, the
            // [PracticeConfig.init] `require` would
            // throw, which would brick the pre-flight
            // sheet (the user can't even dismiss it
            // because the getter throws before the
            // sheet can render). Clamp on read so
            // corruption degrades to "weird default"
            // rather than "app crash on launch".
            val practiceMs = prefs.getLong(
                KEY_LAST_PRACTICE_DURATION_MS,
                com.pockref.app.model.PracticeConfig.MIN_PRACTICE_MS,
            ).coerceIn(
                com.pockref.app.model.PracticeConfig.MIN_PRACTICE_MS,
                com.pockref.app.model.PracticeConfig.MAX_PRACTICE_MS,
            )
            val restMs = prefs.getLong(
                KEY_LAST_REST_DURATION_MS,
                com.pockref.app.model.PracticeConfig.MIN_REST_MS,
            ).coerceIn(
                com.pockref.app.model.PracticeConfig.MIN_REST_MS,
                com.pockref.app.model.PracticeConfig.MAX_REST_MS,
            )
            val shuffle = prefs.getBoolean(KEY_LAST_PRACTICE_SHUFFLE, false)
            return com.pockref.app.model.PracticeConfig(
                practiceDurationMs = practiceMs,
                restDurationMs = restMs,
                shuffle = shuffle,
                resetTimerOnImageChange = prefs.getBoolean(
                    KEY_LAST_PRACTICE_RESET_TIMER_ON_IMAGE_CHANGE,
                    true,
                ),
            )
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_LAST_PRACTICE_DURATION_MS)
                editor.remove(KEY_LAST_REST_DURATION_MS)
                editor.remove(KEY_LAST_PRACTICE_SHUFFLE)
                editor.remove(KEY_LAST_PRACTICE_RESET_TIMER_ON_IMAGE_CHANGE)
                // V_NOW (2026-06-08 user feedback "去掉倒计时提示音的功能"):
                // [KEY_LAST_PRACTICE_SOUND] is no longer written
                // here — the field is gone from [PracticeConfig]
                // entirely. Existing on-disk entries with this key
                // are simply ignored on the next read (the getter
                // doesn't read the key), and the next `set(null)`
                // or first config save will leave it orphaned but
                // harmless. No explicit migration needed.
            } else {
                editor.putLong(KEY_LAST_PRACTICE_DURATION_MS, value.practiceDurationMs)
                editor.putLong(KEY_LAST_REST_DURATION_MS, value.restDurationMs)
                editor.putBoolean(KEY_LAST_PRACTICE_SHUFFLE, value.shuffle)
                editor.putBoolean(
                    KEY_LAST_PRACTICE_RESET_TIMER_ON_IMAGE_CHANGE,
                    value.resetTimerOnImageChange,
                )
            }
            editor.apply()
        }

    /**
     * V_NOW: how the minimap area is displayed. MINIMAP = the
     * traditional thumbnail overview; SLIDER = a persistent zoom
     * slider. Stored as the enum name string.
     */
    var minimapDisplayMode: com.pockref.app.model.MinimapDisplayMode
        get() {
            val stored = prefs.getString(KEY_MINIMAP_DISPLAY_MODE, null)
                ?: return com.pockref.app.model.MinimapDisplayMode.MINIMAP
            return runCatching { com.pockref.app.model.MinimapDisplayMode.valueOf(stored) }
                .getOrDefault(com.pockref.app.model.MinimapDisplayMode.MINIMAP)
        }
        set(value) {
            prefs.edit().putString(KEY_MINIMAP_DISPLAY_MODE, value.name).apply()
        }

    /**
     * V_NOW: slider orientation when display mode is SLIDER.
     * ADAPTIVE picks horizontal or vertical based on scene
     * aspect ratio. Stored as the enum name string.
     */
    var minimapSliderOrientation: com.pockref.app.model.MinimapSliderOrientation
        get() {
            val stored = prefs.getString(KEY_MINIMAP_SLIDER_ORIENTATION, null)
                ?: return com.pockref.app.model.MinimapSliderOrientation.VERTICAL
            return runCatching { com.pockref.app.model.MinimapSliderOrientation.valueOf(stored) }
                .getOrDefault(com.pockref.app.model.MinimapSliderOrientation.VERTICAL)
        }
        set(value) {
            prefs.edit().putString(KEY_MINIMAP_SLIDER_ORIENTATION, value.name).apply()
        }

    // V_NOW (2026-06-16 user feedback "去掉设置里的启动
    // 页显示导出日志的开关"): the [showStartPageLogExportFab]
    // getter/setter + [KEY_SHOW_START_PAGE_LOG_EXPORT_FAB]
    // constant are removed. The FAB visibility is now a
    // Compose-local remember in
    // [com.pockref.app.ui.EmptyCanvasState] (defaults to
    // false on every fresh app launch, unlocked by tapping
    // the PockRef title 7 times). No persistent backing
    // store is needed. The previous key string
    // ("show_start_page_log_export_fab") remains on-disk for
    // any users who have toggled it before; it's harmless
    // because the read path is gone and no new write path
    // touches it — exactly the same migration strategy used
    // for the removed [KEY_LAST_PRACTICE_SOUND] in the
    // 速写练习 commit.

    companion object {
        private const val PREFS_NAME = "pockref_settings"
        private const val KEY_DEFAULT_BG_PRESET = "default_background_preset"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FROSTED_GLASS_ENABLED = "frosted_glass_enabled"
        private const val KEY_EDGE_SNAP_ENABLED = "edge_snap_enabled"
        private const val KEY_ROTATION_SNAP_ENABLED = "rotation_snap_enabled"
        private const val KEY_DISABLED_GESTURE_IDS = "disabled_gesture_ids"
        private const val KEY_SINGLE_FINGER_CANVAS_PAN_ENABLED = "single_finger_canvas_pan_enabled"
        // V_NOW: KEY_SPEN_BUTTON_ENABLED is intentionally NOT
        // referenced from any new code path. The previous
        // `spenButtonEnabled` getter was removed because the
        // SYSTEM_ALERT_WINDOW overlay it gated is gone. The key
        // constant is kept for one upgrade cycle so a user who
        // previously toggled the S Pen binding off doesn't get
        // a stale "disabled" read — see the comment above the
        // (now-removed) getter. Safe to drop the constant and
        // the on-disk key entirely in a future migration.
        private const val KEY_SPEN_BUTTON_ENABLED = "spen_button_enabled"
        private const val KEY_SPEN_MODE = "spen_mode"
        private const val KEY_AUTO_SAVE_INTERVAL_MS = "auto_save_interval_ms"
        private const val KEY_AUTO_SAVE_ENABLED = "auto_save_enabled"
        private const val KEY_DEFAULT_SAVE_PATH_ENABLED = "default_save_path_enabled"
        private const val KEY_DEFAULT_SAVE_PATH_MIGRATION_DONE = "default_save_path_migration_done"
        private const val KEY_DEFAULT_SAVE_DIRECTORY_URI = "default_save_directory_uri"
        private const val KEY_DEFAULT_SAVE_DIRECTORY_NAME = "default_save_directory_name"
        // V_NOW: minimap position / dock / collapsed state.
        // Position uses NaN as the "no value yet" sentinel
        // (since 0/0 is a valid docked coordinate), and the
        // UI uses [PockRefUiState.minimapPosition] ==
        // Offset.Unspecified to mean "fall back to default
        // BottomStart 24dp corner".
        private const val KEY_MINIMAP_POSITION_X = "minimap_position_x"
        private const val KEY_MINIMAP_POSITION_Y = "minimap_position_y"
        private const val KEY_MINIMAP_DOCK_EDGE = "minimap_dock_edge"
        private const val KEY_MINIMAP_COLLAPSED = "minimap_collapsed"
        // V_NOW: per-process pinching zoom scale for the
        // full minimap panel. 1.0f = default (canvas-
        // derived base size); 0.5f = half-size overview;
        // 2.0f = double-size detail. Clamped on read
        // AND on write to [0.5f, 2.0f] so a corrupted
        // pref value cannot produce a 0.0-px panel on
        // next launch. The bar (collapsed) view ignores
        // this value; only the expanded panel uses it.
        private const val KEY_MINIMAP_ZOOM_SCALE = "minimap_zoom_scale"
        private const val KEY_MINIMAP_DISPLAY_MODE = "minimap_display_mode"
        private const val KEY_MINIMAP_SLIDER_ORIENTATION = "minimap_slider_orientation"
        // V_NOW (2026-06-08): see [globalImageLock] field above.
        private const val KEY_GLOBAL_IMAGE_LOCK = "global_image_lock"
 private const val KEY_LOCK_CANVAS_FROZEN = "lock_canvas_frozen"
        // V_NOW (2026-06-08 速写练习): see [lastPracticeConfig] field
        // — match the rest of the settings in this file and avoid a
        // JSON / parcelable serialization layer. The three original
        // keys are required to recognize a saved config; the new reset
        // key is optional for backward compatibility and defaults on.
        //
        // V_NOW (2026-06-08 user feedback "去掉倒计时提示音的功能"):
        // the previous [KEY_LAST_PRACTICE_SOUND] was removed
        // (the [soundEnabled] field on [PracticeConfig] is gone).
        // Any on-disk key of that name is ignored on the next
        // read and not overwritten by the next write; an explicit
        // migration is unnecessary because the new read path
        // doesn't read the key and the new write path doesn't
        // touch it. The orphaned key is harmless.
        private const val KEY_LAST_PRACTICE_DURATION_MS = "last_practice_duration_ms"
        private const val KEY_LAST_REST_DURATION_MS = "last_rest_duration_ms"
        private const val KEY_LAST_PRACTICE_SHUFFLE = "last_practice_shuffle"
        private const val KEY_LAST_PRACTICE_RESET_TIMER_ON_IMAGE_CHANGE =
            "last_practice_reset_timer_on_image_change"
    }
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}
