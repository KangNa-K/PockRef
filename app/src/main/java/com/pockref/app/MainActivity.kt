package com.pockref.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockref.app.data.AppPreferences
import com.pockref.app.data.CrashHandler
import com.pockref.app.data.RecoverySessionManager
import com.pockref.app.data.shouldRestoreBackup
import com.pockref.app.model.SelectionMode
import com.pockref.app.spen.SpenButtonDetector
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.PockRefApp
import com.pockref.app.ui.PockRefViewModel
import com.pockref.app.ui.theme.PockRefTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PockRefViewModel by viewModels()
    private lateinit var recoverySessionManager: RecoverySessionManager

    /**
     * S Pen side-button state machine. Maps rising / falling
     * edges of the stylus button bits in [MotionEvent.getButtonState]
     * to one of [SpenButtonDetector.ButtonEvent]s (BoxStart / BoxEnd /
     * Multi / Single), and [dispatchSpenEvent] turns those into
     * PockRefActions (SetSelectionMode(BOX) / SetSelectionMode(SINGLE)
     * etc.).
     *
     * **No permission required.** The detection runs entirely in
     * the Activity's two dispatch hooks — [dispatchTouchEvent]
     * (for pen-tip-on-screen) and [dispatchGenericMotionEvent]
     * (for pen-hovering-near-screen). Both are framework-level
     * event paths that Samsung's fork does NOT strip, so the
     * SM-X810 stylus button bit survives into the app even
     * though the OEM-private paths (a11y services, certain
     * overlay windows) see it zeroed. This is the same
     * technique the SpenButtonMapper reference app uses in
     * `MainActivity.detectStylusButtonState` (which it combines
     * with a TYPE_APPLICATION_OVERLAY for cross-app coverage,
     * but the cross-app overlay needs a11y to start — see
     * SpenButtonMapper's `SPenMapperService.onServiceConnected`).
     *
     * The previous implementation in this app used an
     * SYSTEM_ALERT_WINDOW overlay (started from MainActivity
     * itself, NOT a11y) for the same purpose, and was
     * confirmed not to fire on SM-X810 — see commit history
     * before this one. The dispatch-hook approach is what
     * unblocks the device.
     */
    private lateinit var spenDetector: SpenButtonDetector

    /**
     * Previous frame's stylus button bitmask, used to detect
     * rising / falling edges. Cleared on [onPause] so a
     * backgrounded app doesn't fire a stale edge when it
     * resumes mid-press.
     */
    private var lastStylusButtonBits: Int = 0

    /**
     * S Pen side-button touch hook. The system dispatches
     * `ACTION_DOWN` / `ACTION_MOVE` / `ACTION_UP` to this
     * Activity's [dispatchTouchEvent] whether or not there's
     * an overlay on top — the overlay (if any) sits above
     * but doesn't consume touch. We read
     * [MotionEvent.getButtonState] and feed the edge into
     * [spenDetector]. The event then flows through to the
     * Compose `pointerInput` chain unchanged.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::spenDetector.isInitialized) {
            handleMotionEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * S Pen side-button hover hook. Samsung S Pen reports
     * `ACTION_HOVER_MOVE` / `ACTION_HOVER_ENTER` /
     * `ACTION_HOVER_EXIT` with the same `buttonState` field
     * as touch events. `dispatchGenericMotionEvent` is the
     * framework entry point for these; reading
     * [MotionEvent.getButtonState] here catches the side
     * button while the pen tip is NEAR the screen but not
     * touching. Without this hook, the user has to make
     * contact with the screen to register a button press,
     * which kills the "hover-and-trigger-toolbar" UX.
     *
     * `dispatchGenericMotionEvent` is called by the framework
     * for every generic motion event the Activity receives
     * — touch, hover, trackball, etc. — and `return
     * super.dispatchGenericMotionEvent(event)` forwards the
     * event unchanged to the Compose pointer chain (which
     * ignores hover events for non-stylus sources but still
     * does the right thing for stylus hover, e.g. the
     * magnifier overlay).
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::spenDetector.isInitialized) {
            handleMotionEvent(event)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Read [MotionEvent.getButtonState] and feed
     * rising / falling edges of the S Pen stylus bits
     * (PRIMARY | SECONDARY | TERTIARY) to [spenDetector].
     *
     * The three bits are OR'd together: a Samsung fork
     * may map the side button to any of them depending on
     * the device tree, and the detector only cares about
     * "any stylus button is currently held" vs "none". The
     * actual button identity (PRIMARY vs SECONDARY) is
     * dropped here — the user's spec is "press-and-hold
     * to enter box-select, release to exit", and a single
     * boolean is enough for that.
     */
    private fun handleMotionEvent(event: MotionEvent) {
        val buttonState = event.buttonState
        val stylusBits = buttonState and (
            MotionEvent.BUTTON_STYLUS_PRIMARY or
                MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_TERTIARY
            )
        if (stylusBits == 0 && lastStylusButtonBits == 0) return
        if (stylusBits != 0 && lastStylusButtonBits == 0) {
            spenDetector.onDown()
        } else if (stylusBits == 0 && lastStylusButtonBits != 0) {
            spenDetector.onUp()
        }
        lastStylusButtonBits = stylusBits
    }

    /**
     * Map a detector edge event to ViewModel actions.
     *
     * The user's spec for the S Pen binding (in either
     * [com.pockref.app.spen.SpenMode]):
     *   - BoxStart → enter BOX mode (rectangle drag)
     *   - BoxEnd   → exit box mode; if 2+ images are
     *                currently selected, fall through to
     *                MULTI (lasso); otherwise fall through to
     *                SINGLE (default tap-to-select).
     *                The "selected count" is read from the
     *                LATEST uiState snapshot at the moment
     *                of the BoxEnd dispatch — same value the
     *                user sees on screen.
     *
     * The Multi / Single detector events (PRESS_AND_HOLD
     * mode only) are emitted by the detector for future
     * re-use (e.g. "tap the S Pen button to enter multi
     * lasso regardless of box selection") and currently
     * silently ignored here.
     */
    private fun dispatchSpenEvent(event: SpenButtonDetector.ButtonEvent) {
        when (event) {
            SpenButtonDetector.ButtonEvent.BoxStart -> {
                viewModel.onAction(PockRefAction.SetSelectionMode(SelectionMode.BOX))
                viewModel.onAction(PockRefAction.BeginBoxSelect)
            }
            SpenButtonDetector.ButtonEvent.BoxEnd -> {
                // V_NOW: per the user's spec, the post-BoxEnd
                // selection mode depends on how many images
                // the user ended up selecting inside the box.
                // Read the LATEST uiState value (the one
                // reducer wrote to in response to the box
                // commit), not a stale snapshot from
                // detector construction.
                val selectedCount = viewModel.uiState.value
                    .selectedImageIds
                    .size
                val nextMode = if (selectedCount >= 2) {
                    SelectionMode.MULTI
                } else {
                    // 0 or 1 images selected — fall back to
                    // the single-tap baseline.
                    SelectionMode.SINGLE
                }
                viewModel.onAction(PockRefAction.SetSelectionMode(nextMode))
            }
            SpenButtonDetector.ButtonEvent.Multi -> Unit
            SpenButtonDetector.ButtonEvent.Single -> {
                // V_NOW (D3): the DoubleTap mode fires
                // Single alongside BoxEnd as the "I
                // double-tapped to exit, force single
                // selection regardless of box outcome"
                // signal. The caller (us) responds by
                // switching the selection mode back to
                // SINGLE. This OVERRIDES the BoxEnd
                // above's count-based MULTI choice
                // (the user's double-tap is an
                // explicit "I don't want multi"
                // signal). The reducer's
                // SetSelectionMode(SINGLE) cascades
                // through [selectedImageIds] (single-
                // select mode keeps at most 1 image
                // selected) so the canvas visibly
                // collapses any multi-selection down
                // to a single image (or empty if the
                // box didn't hit anything).
                viewModel.onAction(PockRefAction.SetSelectionMode(SelectionMode.SINGLE))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // V_NOW (2026-06-16, "启动页导出运行日志" feature):
        // Install the crash handler BEFORE any other init so
        // we catch crashes from enableEdgeToEdge / setContent /
        // Compose initialization onward. Idempotent — a
        // second install is a no-op.
        CrashHandler.install(application)
        recoverySessionManager = RecoverySessionManager(application)
        val previousSessionActive = recoverySessionManager.beginSession()
        enableEdgeToEdge()

        spenDetector = SpenButtonDetector(
            // V_NOW: gesture mode is read from
            // AppPreferences (default PRESS_AND_HOLD) on
            // every MainActivity onCreate. Changing the mode
            // in the settings panel takes effect on the next
            // MainActivity recreation — not mid-gesture —
            // because the detector is a state machine, not a
            // hot-swappable policy layer. In-flight gestures
            // from the previous mode finish in the old state
            // machine before the swap, which is the safe
            // behavior (no half-state from a hot-swap race).
            mode = AppPreferences(this).spenMode,
            onEvent = ::dispatchSpenEvent,
        )

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PockRefTheme(themeMode = state.themeMode) {
                PockRefApp(
                    state = state,
                    onAction = viewModel::onAction,
                    imageLazyLoader = viewModel.imageLazyLoader,
                    imageDecoder = viewModel.imageDecoder,
                    errorsFlow = viewModel.errors,
                    // V_NOW (2026-06-16): bridge the
                    // [LogExportFab] click to
                    // [PockRefViewModel.startLogExport].
                    onStartLogExport = viewModel::startLogExport,
                    exportInProgressFlow = viewModel.logExportInProgress,
                )
            }
        }

        window.decorView.setOnDragListener(dragListener)
        val hasExternalOpenRequest = handleOpenIntent(intent)
        if (shouldRestoreBackup(previousSessionActive, hasExternalOpenRequest, savedInstanceState != null)) {
            viewModel.restorePreviousSessionIfAvailable()
        }
    }

    override fun onPause() {
        super.onPause()
        // Reset the edge state so a backgrounded app
        // doesn't fire a stale UP when it resumes mid-
        // press (the user's finger was on the button
        // when the app went to background; on resume the
        // system won't replay the DOWN, so the next
        // event we see is the UP — without this reset
        // we'd treat it as an "UP after a never-felt
        // DOWN" and skip it, but the state machine
        // would also still be in FIRST_DOWN from the
        // pre-background press, which is wrong).
        lastStylusButtonBits = 0
        if (::spenDetector.isInitialized) {
            spenDetector.reset()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && ::recoverySessionManager.isInitialized) {
            viewModel.endRecoverySession()
        }
        super.onDestroy()
    }

    /**
     * V_NOW: window-blur → unified menu collapse.
     *
     * Fires every time the window's focus state changes (the
     * framework contract — see [android.app.Activity.onWindowFocusChanged]
     * KDoc). We care about the *loses-focus* edge:
     *
     *   - System status-bar pulled down
     *   - A dialog / permission prompt / notification shade
     *     drawn on top
     *   - Recent-apps switcher or split-screen another-app
     *     entering focus
     *   - S Pen button triggered the system air-command
     *
     * all surface to the user as "the software lost focus",
     * and our spec is that the 3 floating menu bars (top
     * toolbar / selection floating toolbar / bottom-right
     * action panel) all collapse so that "失焦期间软件内
     * 无任何活跃菜单栏". The single
     * [PockRefAction.SetMenuBarsCollapsed] reducer handles
     * all 3 collapses atomically (one `state.copy(...)` in
     * one StateFlow update), so the user can't observe an
     * intermediate frame where 2 of 3 are still open.
     *
     * On the *gains-focus* edge we deliberately do NOT
     * dispatch anything — the spec is "聚焦时不主动恢复
     * 菜单栏(保留失焦前用户状态)". The chrome stays
     * collapsed until the user manually re-opens it. This
     * matches the "no surprises after a stray notification"
     * UX contract: the user always comes back to a clean
     * canvas + just-the-FAB state, never to a stale
     * half-open menu.
     *
     * Dispatching the action via [viewModel] (not via a
     * `setContent { ... }` lambda) means the order is
     * deterministic relative to any in-flight UI gestures
     * — the VM serializes the action on its own
     * single-threaded dispatcher, so no Compose recomposition
     * can interleave between the blur edge and the collapse
     * copy.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            viewModel.onAction(PockRefAction.SetMenuBarsCollapsed)
        } else {
            // V_NOW (focus-restore follow-up): the user
            // spec is "窗口重新聚焦后,菜单栏应恢复为失焦
            // 前用户设定的状态,而非默认全收起或默认全
            // 展开". The reducer reads the snapshot written
            // by the previous collapse copy and writes the
            // pre-blur values back. Idempotent on the initial
            // Activity onCreate focus edge (uiState.menuBarsCollapsed
            // = false → reducer no-ops). The dispatch path is
            // symmetric to the blur branch above — same
            // viewModel-serialized guarantee, no Compose
            // interleave.
            viewModel.onAction(PockRefAction.RestoreMenuBarsOnFocus)
        }
    }

    /**
     * If the activity was started (or resumed) with a VIEW/SEND intent carrying
     * a `.pur` file, dispatch it to the ViewModel as [PockRefAction.OpenRecentScene].
     */
    private fun handleOpenIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null && (intent.type == null || intent.type == "*/*" ||
                intent.type.equals("application/octet-stream", ignoreCase = true) ||
                uri.lastPathSegment?.endsWith(".pur", ignoreCase = true) == true)
        ) {
            viewModel.onAction(PockRefAction.OpenRecentScene(uri.toString()))
            return true
        }
        return false
    }

    private val dragListener = View.OnDragListener { _, event ->
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val mimeTypes = event.clipDescription?.let { desc ->
                    (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }
                } ?: emptyList()
                mimeTypes.any { it.startsWith("image/") }
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                viewModel.onAction(PockRefAction.SetDragOver(true))
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                viewModel.onAction(PockRefAction.SetDragOver(false))
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                viewModel.onAction(PockRefAction.SetDragOver(false))
                true
            }
            DragEvent.ACTION_DROP -> {
                val clipData = event.clipData ?: return@OnDragListener false
                val permission = requestDragAndDropPermissions(event)
                val uris = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { uris.add(it) }
                }
                if (uris.isNotEmpty()) {
                    viewModel.onAction(PockRefAction.DropImages(uris, permission))
                } else {
                    permission.release()
                }
                viewModel.onAction(PockRefAction.SetDragOver(false))
                uris.isNotEmpty()
            }
            else -> false
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * V_NOW: MIME type for the app's native `.pur` scene
         * format. Declared in [AndroidManifest.xml] as the
         * third intent filter (after `application/octet-stream`
         * and the `star slash star` catch-all for backward
         * compat) so PockRef is registered as a handler for
         * this MIME. File managers that show a "Open with"
         * picker use the URI's MIME type as the filter key;
         * with this MIME, tapping a .pur file in a file
         * manager will surface PockRef in the picker (and,
         * after the user picks it once, the system remembers
         * it as the default for this MIME).
         *
         * The `application/x-` prefix is the de-facto custom-
         * MIME convention on Android (e.g. F-Droid, KDE
         * Connect, Termux all do this). The full IANA-registered
         * path would be a `vnd.*` subtype, but those require
         * a formal IETF submission which a single-app file
         * format doesn't need. `x-` keeps the registration
         * effort at "write the constant" while still letting
         * the system distinguish PockRef scenes from generic
         * binary blobs.
         *
         * Old .pur files saved with `application/octet-stream`
         * are still openable via the catch-all (any-MIME)
         * filter — the new MIME is additive, not replacing.
         *
         * V_NOW Kotlin gotcha: writing the star-slash-star
         * catch-all MIME literally in a KDoc comment is a
         * parse error because `star slash` is the block-
         * comment terminator. This block had it inside
         * backticks; the lexer still terminates the comment
         * early. Wording the literal as "star slash star" /
         * "(any-MIME)" sidesteps it.
         */
        const val PUR_SCENE_MIME: String = "application/x-pockref-scene"
    }
}
