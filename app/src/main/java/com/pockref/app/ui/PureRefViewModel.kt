package com.pockref.app.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pockref.app.data.AppPreferences
import com.pockref.app.data.AutoSaveManager
import com.pockref.app.data.ImageOptimizer
import com.pockref.app.data.ImageTilePolicy
import com.pockref.app.data.PurFileParser
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.data.RecoverySessionManager
import com.pockref.app.data.SceneImageDecoder
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.decodeRecentScenes
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.SceneNaming
import com.pockref.app.model.SceneImage
import com.pockref.app.model.hasSceneContent
import com.pockref.app.model.toScene
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Canvas (world) zoom factor bounds.
 *
 * **MIN_CANVAS_SCALE = 0.02f** (was 0.1f before 1.3.x):
 * the previous 0.1 lower bound stopped the user from
 * shrinking the canvas enough to fit a wide spread of
 * reference images around the work area — the viewport
 * ceiling at 10% of the world size was too tight once
 * 20+ refs surrounded the canvas. 0.02 gives a 5×
 * reduction headroom (10× vs. the screen width budget)
 * while still keeping thumbnail chips large enough
 * (sub-1px rendering kicks in around 0.005, so 0.02
 * stays well above GPU-flicker territory).
 *
 * MAX_CANVAS_SCALE = 40f: keeps the zoom bounded, but gives
 * enough headroom for HD/original-resolution inspection after
 * the previous 10× ceiling became visibly too tight.
 */
internal const val MIN_CANVAS_SCALE: Float = 0.02f
internal const val MAX_CANVAS_SCALE: Float = 40f
internal const val MAX_CANVAS_PAN_STEP_PX: Float = 200f

/**
 * Some DocumentsProvider implementations wrap a missing document in an
 * [IllegalArgumentException] rather than returning a [java.io.FileNotFoundException].
 * A stale recent-file entry should be removed in either case.
 */
internal fun isMissingDocumentFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is java.io.FileNotFoundException) return true
        val message = current.message.orEmpty()
        if (
            message.contains("Missing file", ignoreCase = true) ||
            message.contains("FileNotFoundException", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * A user-facing error to surface through the Snackbar channel.
 * @param message short, already-localized text shown in the UI
 * @param throwable the original cause (for Logcat / future report); never displayed
 */
// Public so that the public `errors: SharedFlow<UiError>` field doesn't leak an
// internal type across packages (MainActivity reads it from `com.pockref.app`).
data class UiError(val message: String, val throwable: Throwable? = null)

class PockRefViewModel(application: Application) : AndroidViewModel(application) {
    internal val contentResolver = application.contentResolver
    internal val codec = PurSceneCodec().also { it.initCacheDir(application.cacheDir) }
    val imageLazyLoader: PurFileParser get() = codec.imageLazyLoader
    val imageDecoder: SceneImageDecoder get() = codec.imageDecoder
    private val prefs = application.getSharedPreferences("pockref_ui", Context.MODE_PRIVATE)
    internal val appPrefs = AppPreferences(application)
    internal val autoSaveManager = AutoSaveManager(application, codec)
    internal val recoverySessionManager = RecoverySessionManager(application)
    internal val imageOptimizer = ImageOptimizer(application)

    internal val undoStack: ArrayDeque<HistoryEntry> = ArrayDeque()
    internal val redoStack: ArrayDeque<HistoryEntry> = ArrayDeque()
    internal val markupHistory = MarkupHistory()
    internal var sceneGestureActive: Boolean = false
    internal var gestureUndoRecorded: Boolean = false
        // Resetting the flag (e.g. by loadScene in PockRefViewModelScene, which
        // we can't modify) must also drop the captured "before" state so a
        // subsequent gesture doesn't diff against a stale image list.
        set(value) {
            if (!value) gestureUndoBefore = null
            field = value
        }
    // Captured state at the start of the current gesture, used to compute
    // a single diff when the gesture ends. Null means "no gesture in progress
    // (or the gesture produced no change yet)".
    internal var gestureUndoBefore: PockRefUiState? = null
    private var autoSaveJob: Job? = null
    private var recoveryAttempted = false
    internal var currentSceneUri: Uri? = null
    internal var isImportedPurFile: Boolean = false
    internal var lastSaveSnapshotHash: String = ""
    internal var preOptimizeBackup: Map<String, Pair<ByteArray, String>>? = null
    internal var pendingBatchTasks: List<ImageOptimizer.BatchTask>? = null
    internal var pendingLazyTasks: List<ImageOptimizer.LazyBatchTask>? = null
    internal var pendingSaveUri: Uri? = null
    internal var pendingSaveTruncate: Boolean = false
    internal var backupDir: File? = null
    internal var untitledCounter: Int = 0

    internal val _uiState = MutableStateFlow(
        PockRefUiState(
            backgroundColor = appPrefs.defaultBackgroundPreset.color,
            backgroundCustomColor = appPrefs.defaultBackgroundPreset.color,
            themeMode = appPrefs.themeMode,
            // V_NOW: spenMode pref → uiState. The detector in
            // MainActivity reads the pref directly (not the
            // uiState) on construction — see MainActivity.onCreate
            // — so this uiState field is read by the settings
            // sheet's RadioButton group to position the
            // selection, and is otherwise inert. Keeping the
            // source of truth in the pref (instead of the
            // uiState) means a process restart never has to
            // re-read the uiState snapshot to recover the
            // user's last choice.
            spenMode = appPrefs.spenMode,
            recentScenes = loadRecentScenes(),
            edgeSnapEnabled = appPrefs.edgeSnapEnabled,
            rotationSnapEnabled = appPrefs.rotationSnapEnabled,
            // V_NOW: autoSaveEnabled pref → uiState. The
            // Switch in SettingsAutoSaveSection reads
            // this on every recomposition; the reducer
            // also writes the pref in the same handler
            // so a future process restart sees the same
            // value. The auto-save coroutine itself reads
            // the pref directly on every cycle, so this
            // uiState field is purely a UI mirror — but
            // keeping the source of truth in the pref
            // (and mirroring it into uiState for
            // recomposition) avoids the
            // "Switch + Slider show stale value for one
            // frame" race that a `mutableStateOf` local
            // + set-on-action pattern would have.
            autoSaveEnabled = appPrefs.autoSaveEnabled,
            // Save picker behavior is opt-in. When false, DocumentsUI
            // chooses its own last/default location.
            defaultSavePathEnabled = appPrefs.defaultSavePathEnabled,
            // Persisted custom save directory. It is only used when the
            // switch above is enabled.
            defaultSaveDirectoryUri = appPrefs.defaultSaveDirectoryUri,
            defaultSaveDirectoryName = appPrefs.defaultSaveDirectoryName,
            // V_NOW (2026-06-08 "global image lock" feature):
            // mirror the persisted app-wide lock toggle into
            // [uiState] at startup so the top-right lock
            // button shows the correct state on the very
            // first frame after launch — without this, the
            // button would briefly show "unlocked" before
            // the action handler ran and read the pref, which
            // a fast-finger user could exploit (tap → no
            // change) or be confused by. Same pref → state
            // mirror pattern as [autoSaveEnabled] above.
            globalImageLock = appPrefs.globalImageLock,
 lockCanvasFrozen = appPrefs.lockCanvasFrozen,
            // V_NOW (follow-up after auto-save-switch task):
            // mirror the persisted auto-save interval into
            // [uiState] at startup so the SettingsAutoSaveSection
            // slider position matches the pref before the user
            // touches anything. Without this, the slider would
            // show the default (60 min) even if the pref holds
            // a different value (e.g. 5 min from a previous
            // session). Mirrors the same pref → state pattern
            // used for [autoSaveEnabled] one block above.
            autoSaveIntervalMs = appPrefs.autoSaveIntervalMs,
            // V_NOW (follow-up after "auto-save silent" bug):
            // hydrate [lastAutoSaveAt] from the on-disk pref
            // (which [AutoSaveManager] persists on every
            // successful write — see
            // [AutoSaveManager.autoSave] KEY_LAST_SAVE_TS.put)
            // so the settings panel's "last saved" line
            // shows the real timestamp on first launch, not
            // "never". `0L` is the sentinel for "never saved
            // yet" in [AutoSaveManager] (it's the default of
            // `prefs.getLong(KEY_LAST_SAVE_TS, 0L)`), so we
            // map 0 → null here. The auto-save coroutine
            // updates this field on every successful write
            // — the in-memory state is the source of truth
            // for the UI; the pref is the durable copy.
            lastAutoSaveAt = autoSaveManager.lastSaveTimestamp
                .takeIf { it > 0L },
            // Default-on gesture toggles hydrate from the disabled
            // set. Single-finger canvas pan is default-off, so it
            // hydrates from its dedicated Boolean preference.
            gestureToggles = buildMap {
                com.pockref.app.ui.GestureId.ALL.forEach { id ->
                    put(
                        id,
                        if (id == com.pockref.app.ui.GestureId.SINGLE_FINGER_PAN) {
                            appPrefs.singleFingerCanvasPanEnabled
                        } else {
                            id !in appPrefs.disabledGestureIds
                        },
                    )
                }
            },
            // V_NOW: hydrate minimap state from the same pref
            // keys the [setMinimapPosition] / [setMinimapDockEdge]
            // / [setMinimapCollapsed] handlers write. NaN is the
            // "no value yet" sentinel (since 0/0 is a valid
            // docked coordinate for the top-left corner), and
            // the renderer interprets [Offset.Unspecified] as
            // "use the default BottomStart 24dp corner". On
            // first launch all three prefs are absent, so the
            // minimap starts at the default position with no
            // dock + not collapsed — matching the pre-feature
            // behavior.
            minimapPosition = run {
                val px = appPrefs.minimapPositionX
                val py = appPrefs.minimapPositionY
                if (px.isNaN() || py.isNaN()) {
                    androidx.compose.ui.geometry.Offset.Unspecified
                } else {
                    androidx.compose.ui.geometry.Offset(px, py)
                }
            },
            minimapDockEdge = appPrefs.minimapDockEdge,
            minimapCollapsed = appPrefs.minimapCollapsed,
            // V_NOW (iOS PiP-style minimap): the user's last
            // pinch zoom survives a process kill / fresh launch
            // via the [AppPreferences.minimapZoomScale] backing
            // store. On first launch the pref is absent, so
            // `appPrefs.minimapZoomScale` returns its default
            // (1.0f, clamped to [0.5f, 2.0f]) and the
            // panel renders at its canvas-derived base size.
            minimapZoomScale = appPrefs.minimapZoomScale,
            minimapDisplayMode = appPrefs.minimapDisplayMode,
            minimapSliderOrientation = appPrefs.minimapSliderOrientation,
            // V_NOW (2026-06-16 user feedback "把导出日志按钮
            // 改成默认不显示,点 pockref 图标 7 次后才会显示"):
            // the [showStartPageLogExportFab] field is removed
            // from [PockRefUiState] (and the AppPreferences
            // backing field too). FAB visibility is now a
            // Compose-local remember in
            // [com.pockref.app.ui.EmptyCanvasState] — no init
            // read needed here, the visibility defaults to
            // false on every fresh app launch via the
            // `remember { mutableStateOf(false) }` default
            // value, and the 7-tap counter in EmptyCanvasState
            // flips it to true via the [onLogExportFab] callback.
        )
    )
    val uiState: StateFlow<PockRefUiState> = _uiState.asStateFlow()

    // One-shot error channel. Subscribers (UI) must show a Snackbar/Toast and
    // call [PockRefAction.ConsumeError] to clear the current value. The buffer
    // is large enough to cover a burst of failures during a single batch
    // operation (e.g. several import-image failures in a row).
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 8)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    // Latest displayed error — kept on the VM (rather than in [PockRefUiState]
    // in Models.kt, which the task marked as off-limits) so the UI can confirm
    // a Snackbar has been seen and the next emission can overwrite it without
    // being filtered as a duplicate. PockRefAction.ConsumeError clears this.
    private val _lastError = MutableStateFlow<UiError?>(null)
    val lastError: StateFlow<UiError?> = _lastError.asStateFlow()

    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Whether a log export is currently in progress. The FAB
    // observes this via [logExportInProgress] and disables
    // itself to prevent duplicate clicks. Mirrors the
    // [lastError] StateFlow pattern (no PockRefUiState field)
    // because only the FAB cares about this state.
    private val _logExportInProgress = MutableStateFlow(false)
    val logExportInProgress: StateFlow<Boolean> = _logExportInProgress.asStateFlow()

    // V_NOW (2026-06-16): Snapshot of recent [UiError]s that
    // the LogExportFab will include in its export. We keep an
    // internal ring buffer rather than reflecting it into
    // [PockRefUiState] because (a) nothing else in the app reads
    // it, and (b) PockRefUiState is already 100+ fields — pushing
    // 50 strings in there would trigger expensive recomposition
    // on every error report.
    //
    // Capacity 50 matches the cap documented in the spec; if a
    // future bug requires more history, bump MAX_RECENT_ERRORS
    // in the companion object.
    private val recentErrors = ArrayDeque<UiError>(MAX_RECENT_ERRORS)
    private val recentErrorsLock = Any()

    internal fun snapshotRecentErrors(): List<UiError> = synchronized(recentErrorsLock) {
        recentErrors.toList()
    }

    /**
     * Log an error and surface it to the UI through [errors]. The throwable is
     * always written to Logcat (with a stable [TAG] for filtering) and may be
     * kept on the [UiError] for richer diagnostic UI later; never expose the
     * throwable message to end users — pass a localized `message` instead.
     */
    internal fun reportError(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        val uiError = UiError(message, throwable)
        // tryEmit is non-suspending and respects the buffer; the only way it
        // returns false here is if extraBufferCapacity is exhausted, in which
        // case we drop silently — Logcat still has the full stack trace.
        _errors.tryEmit(uiError)
        // Always update the latest-error slot, even if the SharedFlow dropped
        // the event: a snackbar is fire-and-forget but a banner / persistence
        // layer would need this. Equality-based distinctUntilChanged on the
        // StateFlow side keeps recomposition cheap.
        _lastError.value = uiError
        // V_NOW (2026-06-16): mirror into the log-export ring buffer.
        // Push is bounded by capacity (50); oldest entries are evicted FIFO.
        synchronized(recentErrorsLock) {
            recentErrors.addLast(uiError)
            while (recentErrors.size > MAX_RECENT_ERRORS) {
                recentErrors.removeFirst()
            }
        }
    }

    /**
     * Acknowledge the most recent [UiError] so the next emission can take its
     * place. Idempotent: safe to call when there is no current error.
     */
    internal fun consumeError() {
        _lastError.value = null
    }

    internal val undoStackSize: Int get() = undoStack.size
    internal val redoStackSize: Int get() = redoStack.size

    // Controllers (constructed in init)
    internal val transformController: PockRefViewModelTransform
    internal val batchController: PockRefViewModelBatch
    internal val selectionController: PockRefViewModelSelection
    internal val sceneController: PockRefViewModelScene
    // V_NOW (2026-06-08 速写练习): wraps the pure
    // [PracticeReducer] and applies its output to
    // [PockRefUiState.practiceState]. The eight
    // [PockRefAction.StartPractice] / [PracticeTick] /
    // PausePractice / ResumePractice / SkipRest /
    // PreviousPracticeImage / NextPracticeImage /
    // ExitPractice actions route through this controller.
    // Follows the same constructor pattern as the other
    // four controllers (passes `this`, reads
    // [uiState.value], writes via [_uiState.update]).
    internal val practiceController: PockRefViewModelPractice

    init {
        transformController = PockRefViewModelTransform(this)
        batchController = PockRefViewModelBatch(this)
        selectionController = PockRefViewModelSelection(this)
        sceneController = PockRefViewModelScene(this)
        practiceController = PockRefViewModelPractice(this)
        startAutoSaveLoop()
    }

    private fun startAutoSaveLoop() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(autoSaveManager.intervalMs)
                val state = _uiState.value
                Log.d("AutoSave", "loop tick: images=${state.images.size}, " +
                    "enabled=${autoSaveManager.autoSaveEnabled}, " +
                    "intervalMs=${autoSaveManager.intervalMs}, " +
                    "lastTs=${autoSaveManager.lastSaveTimestamp}")
                if (!state.hasSceneContent) continue
                val scene = state.toScene()
                if (autoSaveManager.shouldAutoSave(System.currentTimeMillis())) {
                    Log.d("AutoSave", "shouldAutoSave=true, calling autoSave()")
                    val result = autoSaveManager.autoSave(scene, currentSceneUri, contentResolver)
                    Log.d("AutoSave", "autoSave() returned result=$result, uri=$currentSceneUri")
                    if (result.originalSaved) {
                        // V_NOW: sync [lastSaveSnapshotHash] so
                        // [hasUnsavedChanges] returns false
                        // after a successful auto-save. Without
                        // this, the "unsaved changes" dialog
                        // still appears on close even though
                        // the content was already persisted.
                        lastSaveSnapshotHash = computeQuickHash(scene)
                        syncDirectSaveState()
                        // V_NOW (follow-up after "auto-save
                        // silent" bug): mirror the
                        // successful-write timestamp into
                        // [uiState] so the settings panel's
                        // "last saved: X秒前" line updates
                        // without a process restart.
                        // [AutoSaveManager.autoSave] already
                        // wrote the durable copy
                        // ([KEY_LAST_SAVE_TS]) — this just
                        // surfaces it to the UI mirror.
                    }
                    if (result.backupSaved) {
                        _uiState.update { it.copy(lastAutoSaveAt = System.currentTimeMillis()) }
                    }
                }
                // V_NOW (follow-up after "1 min never
                // fires" bug): the previous version of
                // this loop computed an in-memory
                // [computeQuickHash] of the scene and
                // compared it to [lastAutoSaveSnapshotHash]
                // to derive `hasChanged`, which was then
                // passed to [shouldAutoSave]. The in-memory
                // hash was reset by every
                // [notifyContentChanged] call — but
                // rotation-snap engagement
                // (PureRefViewModelTransform.kt:1126),
                // [applyHistoryEntry] (line 1360), and
                // [applyDiff] (line 1379) all mutate
                // [uiState.images] directly via
                // [uiState.update] without calling
                // [notifyContentChanged], so the hash
                // never reset and `hasChanged` was
                // permanently `false` after the first
                // save. Auto-save stopped firing on the
                // very next tick — and stayed stopped
                // regardless of what the user did — until
                // the process was killed and restarted.
                //
                // The fix removes the in-memory hash
                // comparison entirely. [shouldAutoSave]
                // now only checks the master switch and
                // the elapsed-time gate. The "did the
                // content change" decision is delegated
                // to [AutoSaveManager.autoSave], which
                // computes a fresh SHA-256 hash of the
                // live scene on every invocation and
                // compares it to the durable
                // [KEY_LAST_CONTENT_HASH] in the
                // auto-save [SharedPreferences]. If the
                // content hasn't changed, [autoSave]
                // returns `false` after one cheap hash
                // compare and never touches the disk. The
                // upshot: every [uiState.update] site —
                // the ones that go through
                // [notifyContentChanged] AND the ones
                // that don't (rotation snap, undo/redo,
                // history apply) — is correctly
                // recognised the next tick, because the
                // next tick reads the live [uiState.value]
                // and hashes it fresh.
            }
        }
    }

    fun restorePreviousSessionIfAvailable() {
        if (recoveryAttempted) return
        recoveryAttempted = true
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                autoSaveManager.loadLatestValidBackup()
            } ?: return@launch
            sceneController.restoreAutoSave(restored.scene)
            reportError("已恢复上次异常退出前的内容")
        }
    }

    fun endRecoverySession() {
        autoSaveManager.clearRecoveryBackups()
        recoverySessionManager.endSession()
    }

    internal fun computeQuickHash(scene: PockRefScene): String {
        val sb = StringBuilder()
        sb.append(scene.name)
        sb.append(scene.images.size)
        scene.images.forEach { img ->
            sb.append(img.id)
            sb.append(img.mimeType)
            sb.append(img.imageBytes.size)
            sb.append(img.intrinsicWidth)
            sb.append(img.intrinsicHeight)
            sb.append(img.center.x.toInt())
            sb.append(img.center.y.toInt())
            sb.append(img.displaySize.width.toBits())
            sb.append(img.displaySize.height.toBits())
            sb.append(img.scale.toBits())
            sb.append(img.rotationDegrees.toBits())
            sb.append(img.flipX)
            sb.append(img.flipY)
            sb.append(img.zIndex)
            sb.append(img.colorAdjust.brightness.toBits())
            sb.append(img.colorAdjust.contrast.toBits())
            sb.append(img.colorAdjust.saturation.toBits())
        }
        sb.append(scene.markupStrokes.size)
        scene.markupStrokes.forEach { stroke ->
            sb.append(stroke.id)
            sb.append(stroke.color.value)
            sb.append(stroke.lineWidth.toBits())
            stroke.points.forEach { point ->
                sb.append(point.x.toBits())
                sb.append(point.y.toBits())
            }
        }
        sb.append(scene.markupTexts.size)
        scene.markupTexts.forEach { text ->
            sb.append(text.id)
            sb.append(text.text)
            sb.append(text.position.x.toBits())
            sb.append(text.position.y.toBits())
            sb.append(text.color.value)
            sb.append(text.fontSize.toBits())
        }
        return sb.toString().hashCode().toString(16)
    }

    internal fun notifyContentChanged() {
        // V_NOW (follow-up after "1 min never fires"
        // bug): the previous version of this function
        // also reset an in-memory
        // [lastAutoSaveSnapshotHash] field. The
        // in-memory hash was the auto-save loop's
        // `hasContentChanged` check input, and the bug
        // was that several [uiState.update] sites
        // (rotation snap engagement, [applyHistoryEntry],
        // [applyDiff]) bypassed this notification — the
        // hash never reset, `hasContentChanged` was
        // permanently false, and auto-save stopped
        // firing after the first save. The in-memory
        // hash field has been removed; auto-save now
        // delegates content-change detection to
        // [AutoSaveManager.autoSave]'s SHA-256 hash
        // compare, which reads the live [uiState.value]
        // on every tick and doesn't depend on a
        // notification path that some [uiState.update]
        // sites were skipping.
        //
        // All that remains here is the
        // [syncDirectSaveState] call (which updates
        // [uiState.hasUnsavedChanges] / [canDirectSave]
        // for the title bar's save indicator). That
        // contract is unchanged.
        syncDirectSaveState()
    }

    internal fun syncDirectSaveState() {
        val directSave = isImportedPurFile && currentSceneUri != null && hasUnsavedChanges
        val current = _uiState.value
        val hasSceneContent = current.images.isNotEmpty() ||
            current.markupStrokes.isNotEmpty() ||
            current.markupTexts.isNotEmpty()
        val dirty = hasUnsavedChanges && hasSceneContent
        val cur = _uiState.value
        if (cur.canDirectSave != directSave || cur.hasUnsavedChanges != dirty) {
            _uiState.update { it.copy(canDirectSave = directSave, hasUnsavedChanges = dirty) }
        }
    }

    val hasUnsavedChanges: Boolean
        get() {
            val scene = _uiState.value.toScene()
            if (scene.images.isEmpty() && scene.markupStrokes.isEmpty() && scene.markupTexts.isEmpty()) {
                return false
            }
            val currentHash = computeQuickHash(scene)
            return currentHash != lastSaveSnapshotHash
        }

    val canDirectSave: Boolean
        get() = isImportedPurFile && currentSceneUri != null && hasUnsavedChanges

    fun onAction(action: PockRefAction) {
        when (action) {
            PockRefAction.ResetView -> batchController.resetView()
            PockRefAction.FitToScreen -> batchController.fitToScreen()
            PockRefAction.Undo -> {
                if (_uiState.value.markupModeActive) undoMarkupStep() else transformController.undo()
            }
            PockRefAction.Redo -> {
                if (_uiState.value.markupModeActive) redoMarkupStep() else transformController.redo()
            }
            PockRefAction.ToggleMultiSelectMode -> selectionController.toggleMultiSelectMode()
            is PockRefAction.ImportImages -> sceneController.importImages(action.uris)
            is PockRefAction.LoadScene -> sceneController.loadScene(action.uri)
            is PockRefAction.SaveScene -> sceneController.saveScene(action.uri)
            is PockRefAction.OpenRecentScene -> sceneController.loadScene(
                Uri.parse(action.uri),
                dropRecentOnFailure = true,
            )
            is PockRefAction.RemoveRecentScene -> removeRecentScene(action.uri)
            is PockRefAction.RenameRecentScene -> renameRecentScene(action.uri, action.newLabel)
            is PockRefAction.TogglePinRecentScene -> togglePinRecentScene(action.uri)
            // V_NOW (2026-06-10, "启动页多选 + 底部 Bulk Action
            // Bar 置顶"): start-page recent-files multi-select.
            // See [PockRefAction.EnterRecentMultiSelect] kdoc
            // for the full feature rationale.
            is PockRefAction.EnterRecentMultiSelect -> enterRecentMultiSelect(action.uri)
            PockRefAction.ExitRecentMultiSelect -> exitRecentMultiSelect()
            is PockRefAction.ToggleRecentSelection -> toggleRecentSelection(action.uri)
            PockRefAction.SelectAllRecentScenes -> selectAllRecentScenes()
            PockRefAction.ClearRecentSelection -> clearRecentSelection()
            is PockRefAction.BulkSetRecentPin -> bulkSetRecentPin(action.pinned)
            PockRefAction.RequestBulkDeleteConfirm -> _uiState.update {
                it.copy(bulkDeleteConfirmOpen = true)
            }
            PockRefAction.DismissBulkDeleteConfirm -> _uiState.update {
                it.copy(bulkDeleteConfirmOpen = false)
            }
            is PockRefAction.BulkDeleteRecentScenes -> bulkDeleteRecentScenes(action.uris)
            is PockRefAction.ImportMixed -> importMixed(action.uris)
            PockRefAction.CloseScene -> sceneController.onCloseScene()
            is PockRefAction.SelectImage -> transformController.selectImage(action.imageId)
            PockRefAction.Deselect -> transformController.deselect()
            is PockRefAction.FlashSelection -> _uiState.update {
                // V_NOW (P2-C3): set the one-shot
                // flash marker so the renderer can
                // briefly tint the matching image.
                // The marker is allowed to naturally
                // expire; the renderer reads
                // `now - atMs` per frame and renders
                // alpha = 0 once the elapsed exceeds
                // 200ms, so no reducer-side clear is
                // needed. See
                // [PockRefUiState.selectionFlash]
                // for the full rationale.
                it.copy(
                    selectionFlash = com.pockref.app.model.SelectionFlash(
                        imageId = action.imageId,
                        atMs = System.currentTimeMillis(),
                    ),
                )
            }
            PockRefAction.TickSelectionSheet -> _uiState.update {
                // V_NOW (SelectionBottomSheet "弹不出来"
                // 修复): bump the monotonically
                // increasing counter that the
                // [SelectionBottomSheet] keys a
                // [kotlinx.coroutines.LaunchedEffect]
                // on. The counter changes on every
                // image-tap event, which forces
                // `sheetState.show()` to be called
                // even after the user has previously
                // dismissed the sheet (where the M3
                // 1.2 ModalBottomSheet's built-in
                // auto-show LaunchedEffect has
                // already fired once and won't re-
                // fire because the SheetState
                // instance is the same). See
                // [PockRefUiState.selectionSheetShowTick]
                // for the full rationale.
                //
                // V_NOW (2026-06-08 "连续弹出两次"
                // 修复 v2): debounce against
                // [lastSheetShowMs]. The previous v1
                // debounce in the [PureRefApp]
                // pointerInput only covered the
                // single-finger-tap path
                // (CycleImageUnderPoint with
                // [showSheet = false] → 200ms
                // scheduled TickSelectionSheet). It
                // MISSED the two-finger-down
                // [SelectImage] + [TickSelectionSheet]
                // pair, the long-press-on-selected
                // path, and the
                // [cycleImageUnderPoint] internal
                // bump — all three of which dispatch
                // [TickSelectionSheet] directly. When
                // the user's tap brought a second
                // pointer down (S Pen barrel, palm,
                // ghost touch), the single-finger
                // path AND the two-finger path both
                // bumped [selectionSheetShowTick] in
                // the same gesture window, the
                // `key(selectionSheetShowTick)` block
                // re-keyed twice, M3's
                // `LaunchedEffect(sheetState)
                // { sheetState.show() }` re-ran twice,
                // and the user saw the sheet pop
                // twice. The debounce window of 800ms
                // covers the M3 slide-up animation
                // (~200ms) + the user's "is it there?"
                // reaction time (~600ms). Anything
                // within 800ms is treated as the same
                // "user wants the sheet on this image"
                // intent.
                val now = System.currentTimeMillis()
                if (now - it.lastSheetShowMs < 800L) {
                    // Within the debounce window —
                    // the sheet was already shown
                    // (or just shown) in the last
                    // 800ms. Skip the bump so the
                    // `key(selectionSheetShowTick)`
                    // block doesn't re-key, M3's
                    // `LaunchedEffect(sheetState)
                    // { sheetState.show() }` doesn't
                    // re-run, and the sheet doesn't
                    // pop a second time. The
                    // [selectionSheetDismissTick]
                    // reset is also skipped so any
                    // pending dismiss isn't cancelled
                    // by a no-op show.
                    return@update it
                }
                it.copy(
                    selectionSheetShowTick = it.selectionSheetShowTick + 1,
                    // V_NOW: reset dismiss-tick so the
                    // [SelectionBottomSheet]'s
                    // [LaunchedEffect(selectionSheetDismissTick)]
                    // doesn't re-fire with a stale non-zero
                    // value when the [key(showTick)] block
                    // is re-created on the next tap.
                    selectionSheetDismissTick = 0,
                    // V_NOW: stamp AFTER the bump so
                    // the 800ms window measures "time
                    // since sheet was last shown",
                    // not "time since job was
                    // scheduled".
                    lastSheetShowMs = now,
                )
            }
            PockRefAction.DismissSelectionSheet -> _uiState.update {
                it.copy(selectionSheetDismissTick = it.selectionSheetDismissTick + 1)
            }
            PockRefAction.DismissBottomRightPanel -> _uiState.update {
                if (it.bottomRightPanelExpanded) {
                    it.copy(bottomRightPanelExpanded = false)
                } else {
                    it
                }
            }
            is PockRefAction.SetMarkupModeActive -> setMarkupModeActive(action.active)
            is PockRefAction.BeginMarkupStroke -> beginMarkupStroke(action.point)
            is PockRefAction.UpdateMarkupStroke -> updateMarkupStroke(action.point)
            PockRefAction.CommitMarkupStroke -> commitMarkupStroke()
            PockRefAction.CancelMarkupStroke -> cancelMarkupStroke()
            PockRefAction.CancelMarkupSession -> cancelMarkupSession()
            PockRefAction.RequestMarkupText -> requestMarkupText()
            is PockRefAction.EditMarkupText -> editMarkupText(action.textId)
            is PockRefAction.MoveMarkupText -> moveMarkupText(action.textId, action.delta)
            is PockRefAction.SetPendingMarkupText -> _uiState.update {
                it.copy(pendingMarkupTextValue = action.value)
            }
            is PockRefAction.SetPendingMarkupTextFontSize -> _uiState.update {
                it.copy(pendingMarkupTextFontSize = action.size.coerceIn(24f, 240f))
            }
            PockRefAction.CommitMarkupText -> commitMarkupText()
            PockRefAction.CancelMarkupText -> _uiState.update {
                it.copy(
                    pendingMarkupTextPosition = null,
                    pendingMarkupTextValue = "",
                    pendingMarkupTextFontSize = 80f,
                    editingMarkupTextId = null,
                )
            }
            is PockRefAction.CycleImageUnderPoint -> transformController.cycleImageUnderPoint(action.point, action.showSheet)
            is PockRefAction.BringImageToFront -> transformController.bringImageToFront(action.imageId)
            // V_NOW (2026-06-10 user spec "单选 sheet 第一行
            // 按钮 = ... / 设为封面"): cover is a UI-only flag
            // (no scene change, no undo). Just write
            // [PockRefUiState.coverImageId]. `null` clears
            // the cover (the export-thumbnail path already
            // falls back to the first image in the scene when
            // coverImageId is null, so there's nothing else
            // to update).
            //
            // V_NOW (2026-06-10 revert repair): re-apply the
            // 934b80f handler that was silently removed by
            // 720078f. See PureRefActions.SetAsCover docstring
            // for the full story.
            is PockRefAction.SetAsCover -> {
                _uiState.update { it.copy(coverImageId = action.imageId) }
                // V_NOW (2026-06-10 user spec "启动页封面的显示规则"):
                // persist the cover into the matching RecentSceneEntry
                // so it survives app restart.
                val sceneUri = currentSceneUri?.toString()
                if (sceneUri != null) {
                    val updated = _uiState.value.recentScenes.map {
                        if (it.uri == sceneUri) it.copy(coverImageId = action.imageId) else it
                    }
                    persistRecentScenes(updated)
                }
            }
            PockRefAction.BeginSceneGesture -> beginSceneGesture()
            PockRefAction.EndSceneGesture -> endSceneGesture()
            is PockRefAction.BeginImageDrag -> transformController.beginImageDrag(action.imageId)
            is PockRefAction.UpdateImageDrag -> transformController.updateImageDrag(action.delta)
 PockRefAction.CommitImageDrag -> transformController.commitImageDrag()
 PockRefAction.CancelImageDrag -> transformController.cancelImageDrag()
 is PockRefAction.MoveImage -> transformController.moveImage(action.imageId, action.delta)
 is PockRefAction.PanCanvas -> {
 // V_NOW (2026-06-09 user spec "长按全局锁定时画布不可移动"):
 // hard mode (canvas frozen) blocks canvas pan. The
 // minimap drag-to-pan (which dispatches this same
 // [PanCanvas] action) is also blocked. Soft mode
 // ([globalImageLock] on but [lockCanvasFrozen] off)
 // still allows canvas pan — the existing behavior.
 if (!_uiState.value.lockCanvasFrozen) {
 _uiState.update {
 val dx = action.delta.x.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX)
 val dy = action.delta.y.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX)
 it.copy(canvasOffset = it.canvasOffset + Offset(dx, dy), fittedImageId = null)
 }
 }
 }
is PockRefAction.SetCanvasOffsetFromMinimap -> {
  // V_NOW (2026-06-09): minimap click-to-jump blocked
  // when canvas is frozen. The minimap remains visible
  // (so the user can see where they are) but clicking
  // on it doesn't move the canvas.
  if (!_uiState.value.lockCanvasFrozen) {
  _uiState.update {
  it.copy(canvasOffset = action.offset, fittedImageId = null)
  }
  }
  }
            is PockRefAction.SetCanvasScaleAndOffset -> {
 // V_NOW: when an image is focused
 // ([fittedImageId] != null), ignore
 // canvas adjustments from the
 // auto-adjust / restore animations.
 // Without this guard, the animation
 // overwrites [FitImageToScreen]'s
 // canvas state and clears
 // [fittedImageId], causing the focus
 // to be immediately cancelled.
 val current = _uiState.value
 if (current.fittedImageId != null) {
 // focused — don't touch the canvas
 } else {
 _uiState.update {
 val newScale = action.scale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
 it.copy(canvasScale = newScale, canvasOffset = action.offset, fittedImageId = null)
 }
 }
 }
 is PockRefAction.ZoomCanvas -> {
 // V_NOW (2026-06-09): hard mode blocks canvas
 // zoom (pinch + the auto-adjust's external zoom
 // triggers). Soft mode still allows zoom.
 if (!_uiState.value.lockCanvasFrozen) {
 transformController.zoomCanvas(action.scaleChange, action.focus)
 }
 }
 is PockRefAction.SetCanvasZoom -> {
 // V_NOW (2026-06-09): hard mode blocks the
 // minimap's long-press zoom slider too.
 if (!_uiState.value.lockCanvasFrozen) {
 transformController.setCanvasZoom(action.scale)
 }
 }
            is PockRefAction.TransformSelectedImage -> transformController.transformSelectedImage(action.scaleChange, action.rotationChange, action.pan)
            is PockRefAction.SetSelectedImagesScale -> batchController.setSelectedImagesScale(
                action.factor,
                action.initialImageAabbs,
                action.initialScales,
                action.pivotX,
                action.pivotY,
            )
            is PockRefAction.SetSingleImageCornerScale -> batchController.setSingleImageCornerScale(
                action.imageId,
                action.newScale,
                action.newCenter,
            )
            is PockRefAction.SetColorAdjust -> transformController.updateColorAdjust(action.brightness, action.contrast, action.saturation)
            PockRefAction.RotateLeft -> transformController.rotateSelected(-90f)
            PockRefAction.RotateRight -> transformController.rotateSelected(90f)
            PockRefAction.FlipHorizontal -> transformController.flipSelected(horizontal = true)
            PockRefAction.FlipVertical -> transformController.flipSelected(horizontal = false)
            PockRefAction.BringToFront -> transformController.bringToFront()
            PockRefAction.SendToBack -> transformController.sendToBack()
            PockRefAction.DeleteSelected -> transformController.deleteSelected()
            PockRefAction.DuplicateSelected -> transformController.duplicateSelected()
            PockRefAction.ToggleLockSelected -> transformController.toggleLockSelected()
            PockRefAction.ToggleOriginalDisplaySelected -> transformController.toggleOriginalDisplaySelected()
            // V_NOW (2026-06-08 "global image lock" feature):
            // flip the [PockRefUiState.globalImageLock] flag
            // in-place AND mirror to prefs so a process kill /
            // fresh launch restores the user's last choice.
            // This is a view-mode toggle (not a scene change),
            // so it intentionally does NOT enter the undo
            // stack and does NOT clear redo — same policy as
            // [ToggleMultiSelectMode] / [ToggleBoxSelectMode].
            PockRefAction.ToggleGlobalImageLock -> {
                val currentState = _uiState.value
                val newValue = !currentState.globalImageLock
                val retainedHdImageId = if (newValue) {
                    ImageTilePolicy.hdSingleSelectedImageId(
                        images = currentState.images,
                        selectedImageIds = currentState.selectedImageIds,
                        selectedImageId = currentState.selectedImageId,
                    ) ?: currentState.lockRetainedHdImageId
                } else {
                    currentState.lockRetainedHdImageId
                }
                appPrefs.globalImageLock = newValue
                // V_NOW (2026-06-08 "开启全局锁定自动取消选中"):
                // when the user TURNS ON [globalImageLock],
                // every image becomes locked (see
                // [isImageLocked]) and therefore
                // un-draggable / un-transformable. A
                // pre-existing selection would otherwise
                // leave the user staring at a
                // [SelectionBottomSheet] that advertises
                // actions (flip / rotate / crop / align /
                // grayscale / export / delete) which
                // mostly become no-ops when
                // [globalImageLock] is on. The sheet's
                // own `if (selectedCount == 0) return`
                // guard unmounts the sheet once the
                // selection empties, so the user gets
                // the visual signal "selection cleared,
                // lock is active" via the sheet slide-out
                // animation. We only deselect on enable
                // (newValue == true) — disabling the
                // lock is a permissive operation and the
                // user might still want their selection
                // intact for follow-up actions.
                if (newValue) {
                    transformController.deselect()
                }
                _uiState.update {
                    it.copy(
                        globalImageLock = newValue,
                        lockRetainedHdImageId = when {
                            newValue || it.lockCanvasFrozen -> retainedHdImageId
                            else -> null
                        },
                        // V_NOW: auto-close box-select when locking
                        boxSelectMode = if (newValue) false else it.boxSelectMode,
                        boxSelectRect = if (newValue) null else it.boxSelectRect,
                        boxSelectPreviewIds = if (newValue) emptySet() else it.boxSelectPreviewIds,
                        // V_NOW (2026-06-19 "全局锁定+冻结
                        // 模式下点应用界面,锁定按钮隐藏"
                        // feature): reset the lock-button
                        // visibility toggle when the user
                        // FULLY exits lock mode
                        // (newValue == false) so the lock
                        // button re-appears immediately.
                        // Without the reset, a user who
                        // toggled the button hidden in
                        // hard mode and then walked out of
                        // hard mode via the lock button's
                        // onLongClick (which fires both
                        // [ToggleLockCanvasFrozen] and
                        // [ToggleGlobalImageLock]) would
                        // see no lock button at all
                        // afterwards — they'd have to
                        // remember to tap the canvas to
                        // bring it back. The reset
                        // guarantees the lock button is
                        // visible by default whenever
                        // [globalImageLock] is off, which
                        // is the right UX. We don't reset
                        // on entry (newValue == true)
                        // because [ToggleGlobalImageLock]
                        // alone only enters SOFT mode
                        // (button is always visible there
                        // regardless of [lockButtonHidden])
                        // and the previous
                        // [ToggleLockCanvasFrozen]
                        // reducer already reset on hard-
                        // mode entry if needed.
                        lockButtonHidden = if (newValue) it.lockButtonHidden else false,
                    )
                }
            }
            // V_NOW (2026-06-10 bugfix): the previous
            // `ToggleLockCanvasFrozen -> Unit` stub
            // above was shadowing the real implementation
            // directly below it; Kotlin's `when` matches
            // in source order so the stub fired first and
            // the real branch was unreachable, which is
            // why long-pressing the global-lock button
            // never engaged freeze mode. The stub is
            // removed; the real `ToggleLockCanvasFrozen`
            // branch further below is now the only
            // branch for this action.
 // V_NOW (2026-06-09 user spec "长按全局锁定时画布不可移动
 // 和旋转,同时按钮会变红"): the global-lock button's
 // onLongClick handler. Toggles
 // [PockRefUiState.lockCanvasFrozen] — independent
 // of [ToggleGlobalImageLock] which is the soft
 // (click) toggle for the same button.
 //
 // Coupling: turning the frozen flag ON also
 // engages [globalImageLock] (a "hard lock" implies
 // images are also locked — soft lock on a frozen
 // canvas would be a half-state nobody wants).
 // Turning the frozen flag OFF leaves
 // [globalImageLock] alone — the user might want
 // soft lock (images locked, canvas movable) after
 // stepping out of hard mode, and resetting that
 // silently is a hostile UX.
 //
 // The deselect-on-enable pattern from
 // [ToggleGlobalImageLock] is repeated here so
 // the [SelectionBottomSheet] doesn't show actions
 // for selected-but-frozen images.
 PockRefAction.ToggleLockCanvasFrozen -> {
 val currentState = _uiState.value
 val nowFrozen = currentState.lockCanvasFrozen
 val newFrozen = !nowFrozen
 val retainedHdImageId = if (newFrozen) {
 ImageTilePolicy.hdSingleSelectedImageId(
 images = currentState.images,
 selectedImageIds = currentState.selectedImageIds,
 selectedImageId = currentState.selectedImageId,
 ) ?: currentState.lockRetainedHdImageId
 } else {
 currentState.lockRetainedHdImageId
 }
 if (newFrozen) {
 // turning ON frozen: also engage globalImageLock.
 // Deselect so the SelectionBottomSheet doesn't
 // linger on a no-op action set.
 transformController.deselect()
 }
 appPrefs.lockCanvasFrozen = newFrozen
 appPrefs.globalImageLock = if (newFrozen) true else _uiState.value.globalImageLock
 _uiState.update {
 it.copy(
 lockCanvasFrozen = newFrozen,
 globalImageLock = if (newFrozen) true else it.globalImageLock,
 lockRetainedHdImageId = when {
 newFrozen || it.globalImageLock -> retainedHdImageId
 else -> null
 },
 // V_NOW: auto-close box-select when freezing
 boxSelectMode = if (newFrozen) false else it.boxSelectMode,
 boxSelectRect = if (newFrozen) null else it.boxSelectRect,
 boxSelectPreviewIds = if (newFrozen) emptySet() else it.boxSelectPreviewIds,
 // V_NOW (2026-06-19 "全局锁定+冻结模式下点应用
 // 界面,锁定按钮隐藏" feature): reset the
 // lock-button visibility toggle whenever the
 // user exits hard mode (newFrozen == false) so
 // the lock button is visible by default again.
 // The user is now in soft mode
 // ([globalImageLock] = true,
 // [lockCanvasFrozen] = false), where the lock
 // button is the primary unlock affordance — it
 // must be visible. Without this reset, a user
 // who hid the button in hard mode and then
 // tapped the lock button to back out would
 // land in soft mode with the button still
 // hidden, and the only way to recover is to
 // remember to tap the canvas. We don't reset
 // on entry (newFrozen == true) because we
 // want the user's last preference to carry
 // across a soft→hard transition (rare, but
 // possible if they backed out via the
 // system-back gesture). Combined with the
 // [ToggleGlobalImageLock] reset (which fires
 // on the full-exit case), this guarantees the
 // lock button is visible whenever the user is
 // outside hard mode.
 lockButtonHidden = if (newFrozen) it.lockButtonHidden else false,
 )
 }
 }
 // V_NOW (2026-06-19 user spec "全局锁定+冻结
 // 模式下点一下应用界面,右上角锁定按钮隐藏/
 // 再点显示"): flip
 // [PockRefUiState.lockButtonHidden]. Only
 // dispatched by the
 // [com.pockref.app.ui.PureRefApp]
 // `LockVisibilityToggleOverlay` (which only
 // renders in hard mode), so no extra
 // reducer-level guard is needed — a stray
 // dispatch outside hard mode would still just
 // flip the flag harmlessly (the gate in
 // [com.pockref.app.ui.PureRefApp.GlobalLockButton]
 // reads `!state.lockButtonHidden` and the
 // other chrome elements are gated by
 // `!isLockEngaged`, so the flag is
 // effectively a no-op outside hard mode).
 // The two reset paths in
 // [ToggleGlobalImageLock] (full exit) and
 // [ToggleLockCanvasFrozen] (hard-mode exit)
 // are what keeps the flag from "leaking"
 // across sessions.
 PockRefAction.ToggleLockButtonVisibility -> _uiState.update {
 it.copy(lockButtonHidden = !it.lockButtonHidden)
 }
            PockRefAction.ToggleMarkupVisibility -> _uiState.update {
                it.copy(markupVisible = !it.markupVisible)
            }
            PockRefAction.BeginRenameScene -> _uiState.update {
                it.copy(renameDialogValue = SceneNaming.sanitizeForStorage(it.sceneName))
            }
            is PockRefAction.ConfirmRenameScene -> {
                _uiState.update { it.copy(renameDialogValue = null) }
                sceneController.setSceneName(SceneNaming.sanitizeForStorage(action.newName))
            }
            PockRefAction.CancelRenameScene -> _uiState.update { it.copy(renameDialogValue = null) }
            PockRefAction.ExportSelected -> _uiState.update { it.copy(showExportDialog = true) }
            PockRefAction.CancelExport -> _uiState.update { it.copy(showExportDialog = false) }
            is PockRefAction.ConfirmExport -> {
                _uiState.update { it.copy(showExportDialog = false) }
                sceneController.exportSelected(action.uri, action.format)
            }
            is PockRefAction.ConfirmExportGif -> {
                _uiState.update { it.copy(showExportDialog = false) }
                sceneController.exportSelectedGif(action.uri)
            }
            is PockRefAction.ConfirmExportBatch -> {
                _uiState.update { it.copy(showExportDialog = false) }
                sceneController.exportBatchToTree(action.treeUri, action.format)
            }
            PockRefAction.ExportCanvas -> {
 _uiState.update { it.copy(showExportCanvasDialog = true) }
 }
 /**
 * V_NOW (2026-06-09 "导出画布选项 — 无尺寸上限"): the
 * canvas-export dialog's "标准 (最长8192px)" / "原始画质
 * (无上限)" buttons dispatch this. The reducer:
 *1) stores the chosen cap on
 * [PockRefUiState.exportCanvasMaxDim] (null = no cap), and
 *2) dismisses [PockRefUiState.showExportCanvasDialog]
 * so the dialog composable stops rendering.
 *
 * The dialog composable then watches the dismiss + SAF
 * picker flow — it doesn't launch the picker itself. We
 * launch from the dialog composable (which has the
 * ActivityResultLauncher in scope) on the same button
 * click that dispatches this action, so by the time the
 * SAF picker returns the URI, the state has already
 * updated.
 *
 * Defensive clamp: maxDim is coerced to >=1 when
 * non-null. A zero / negative value would degenerate
 * the export into a1×N or N×1 canvas with no
 * meaningful drawable area.
 */
  is PockRefAction.SetExportCanvasMaxDim -> {
  // V_NOW (2026-06-16): removed the `showExportCanvasDialog = false`
  // dismiss that was here when the dialog didn't exist. The dialog
  // now drives dismissal via [ConfirmExportCanvas] (user picked a
  // save URI from the SAF picker) or [CancelExportCanvas] (user
  // dismissed the dialog). Writing the cap and dismissing in the
  // same dispatch closed the dialog on every chip click, which
  // made it impossible to compose a "标准 8192" + "画布背景色"
  // + "确认导出" three-step flow. Now the cap is just a state
  // value; the dialog reads it back for visual selected-state
  // and only the confirm / cancel paths flip the visibility flag.
  _uiState.update {
  it.copy(
  exportCanvasMaxDim = action.maxDim?.coerceAtLeast(1),
  )
  }
  }
  is PockRefAction.SetExportCanvasBackground -> {
      // V_NOW (2026-06-16): the dialog's "透明背景" /
      // "画布背景色" segmented button dispatches this. We
      // only write [exportCanvasBackground] — unlike
      // [SetExportCanvasMaxDim] we do NOT dismiss the
      // dialog, because the dialog's "确认导出" button is
      // the only thing that picks up the new value and
      // launches the SAF picker. Dismissing here would
      // close the dialog on every chip click, forcing the
      // user to re-open it to confirm.
      _uiState.update { it.copy(exportCanvasBackground = action.mode) }
  }
  PockRefAction.CancelExportCanvas -> {
  _uiState.update { it.copy(showExportCanvasDialog = false) }
  }
  is PockRefAction.ConfirmExportCanvas -> {
                _uiState.update { it.copy(showExportCanvasDialog = false) }
                sceneController.exportCanvas(action.uri, action.backgroundMode)
            }
            is PockRefAction.UpdateCanvasViewport -> sceneController.updateCanvasViewport(action.width, action.height)
            is PockRefAction.SetSceneName -> sceneController.setSceneName(action.value)
            is PockRefAction.EnterCropMode -> sceneController.enterCropMode()
            PockRefAction.CancelCrop -> sceneController.cancelCrop()
            PockRefAction.CommitCrop -> sceneController.commitCrop()
            is PockRefAction.UpdateCropRect -> sceneController.updateCropRect(action.cropRect)
            is PockRefAction.SetCropAspect -> sceneController.setCropAspect(action.aspect)
            is PockRefAction.SetCropRotation -> sceneController.setCropRotation(action.degrees)
            is PockRefAction.FitImageToScreen -> transformController.fitImageToScreen(action.imageId)
            PockRefAction.ScaleUp -> transformController.scaleSelected(1.15f)
            PockRefAction.ScaleDown -> transformController.scaleSelected(0.85f)
            PockRefAction.ResetTransform -> transformController.resetTransform()
            is PockRefAction.BatchAlign -> batchController.batchAlign(action.mode)
            is PockRefAction.BatchScale -> batchController.batchScale(action.scaleFactor)
            PockRefAction.BatchFlipHorizontal -> batchController.batchFlipHorizontal()
            PockRefAction.ConvertToGrayscale -> transformController.convertToGrayscale()
            is PockRefAction.LassoSelect -> selectionController.lassoSelect(action.rect)
            PockRefAction.ToggleBoxSelectMode -> selectionController.toggleBoxSelectMode()
            is PockRefAction.SetSelectionMode -> selectionController.setSelectionMode(action.mode)
            PockRefAction.BeginBoxSelect -> selectionController.beginBoxSelect()
            is PockRefAction.BeginBoxSelectAt -> selectionController.beginBoxSelectAt(action.start)
            is PockRefAction.UpdateBoxSelectRect -> selectionController.updateBoxSelectRect(action.rect, action.current)
            PockRefAction.CommitBoxSelect -> selectionController.commitBoxSelect()
            PockRefAction.ExitBoxSelectMode -> selectionController.exitBoxSelectMode()
            PockRefAction.SaveSceneDirect -> sceneController.saveSceneDirect()
            PockRefAction.UnsavedDialogSave -> sceneController.onUnsavedDialogSave()
            PockRefAction.UnsavedDialogDiscard -> sceneController.onUnsavedDialogDiscard()
            PockRefAction.UnsavedDialogCancel -> sceneController.onUnsavedDialogCancel()
            PockRefAction.ClearSaveAsTrigger -> sceneController.clearSaveAsTrigger()
            PockRefAction.RequestSaveScene -> sceneController.requestSaveScene()
            is PockRefAction.ConfirmSaveSceneName -> sceneController.confirmSaveSceneName(action.name)
            PockRefAction.DismissSaveSceneNameDialog -> sceneController.dismissSaveSceneNameDialog()
            PockRefAction.ConsumePendingSaveSceneLaunch -> sceneController.consumePendingSaveSceneLaunch()
            PockRefAction.ExecuteAutoLayout -> batchController.executeAutoLayout()
            PockRefAction.UndoLastOptimization -> sceneController.undoLastOptimization()
            PockRefAction.ConfirmBatchOptimize -> sceneController.confirmBatchOptimize()
            PockRefAction.SkipBatchOptimize -> sceneController.skipBatchOptimize()
            PockRefAction.CancelBatchOptimize -> sceneController.cancelBatchOptimize()
            PockRefAction.PauseBatchOptimize -> sceneController.pauseBatchOptimize()
            PockRefAction.ResumeBatchOptimize -> sceneController.resumeBatchOptimize()
            PockRefAction.RetryFailedOptimization -> sceneController.retryFailedOptimization()
            is PockRefAction.SetBackgroundPreset -> setBackgroundPreset(action.preset)
            is PockRefAction.SetBackgroundColor -> setBackgroundColor(action.color)
            is PockRefAction.ShowBackgroundPicker -> setBackgroundPickerVisible(action.show)
            is PockRefAction.SetThemeMode -> setThemeMode(action.mode)
            PockRefAction.ConsumeError -> consumeError()
            is PockRefAction.SetHover -> setHover(action.imageId, action.position)
            PockRefAction.ClearHover -> clearHover()
            is PockRefAction.SetHoverPreview -> setHoverPreview(action.imageId)
            is PockRefAction.SetDragOver -> setDragOver(action.isOver)
            is PockRefAction.DropImages -> sceneController.dropImages(action.uris, action.dragPermission)
            is PockRefAction.SetTopToolbarExpanded -> setTopToolbarExpanded(action.expanded)
            is PockRefAction.SetBottomRightPanelExpanded -> setBottomRightPanelExpanded(action.expanded)
            is PockRefAction.SetMinimapPosition -> setMinimapPosition(action.x, action.y)
            is PockRefAction.SetMinimapDockEdge -> setMinimapDockEdge(action.edge)
            is PockRefAction.SetMinimapCollapsed -> setMinimapCollapsed(action.collapsed)
            is PockRefAction.SetMinimapZoomScale -> setMinimapZoomScale(action.scale)
            is PockRefAction.SetMinimapDisplayMode -> setMinimapDisplayMode(action.mode)
            is PockRefAction.SetMinimapSliderOrientation -> setMinimapSliderOrientation(action.orientation)
            // V_NOW (2026-06-16): the
            // [PockRefAction.SetShowStartPageLogExportFab] dispatch
            // arm is removed — the action itself no longer
            // exists (it was added in 880a345 alongside the
            // now-removed Settings toggle and the
            // [PockRefUiState.showStartPageLogExportFab] field).
            PockRefAction.SetMenuBarsCollapsed -> collapseMenuBarsOnWindowBlur()
            PockRefAction.RestoreMenuBarsOnFocus -> restoreMenuBarsOnWindowFocus()
            is PockRefAction.SetSettingsSheetOpen -> setSettingsSheetOpen(action.open)
            is PockRefAction.SetImportSourceMenuOpen -> setImportSourceMenuOpen(action.open)
            // V_NOW (2026-06-10 user hand-modification from 23:31 APK):
            // clipboard import is a dedicated action because it goes through
            // a separate IO-coroutine path (read ClipboardManager, decode +
            // optimize bytes, create SceneImage) rather than the existing
            // [ImportImages] (which takes pre-resolved content URIs from
            // the SAF picker). Dispatched from the [+] import button's
            // "剪贴板" dropdown item.
            PockRefAction.ImportFromClipboard -> sceneController.importFromClipboard()
            is PockRefAction.SetSettingsTab -> setSettingsTab(action.tab)
            is PockRefAction.SnapJustCommitted -> setSnapHighlight(action.highlight)
            PockRefAction.ClearSnapHighlight -> clearSnapHighlight()
            is PockRefAction.SetEdgeSnapEnabled -> setEdgeSnapEnabled(action.enabled)
            is PockRefAction.SetRotationSnapEnabled -> setRotationSnapEnabled(action.enabled)
            is PockRefAction.SetGestureEnabled -> setGestureEnabled(action.id, action.enabled)
            is PockRefAction.SetSpenMode -> setSpenMode(action.mode)
            is PockRefAction.SetAutoSaveIntervalMs -> setAutoSaveIntervalMs(action.intervalMs)
            is PockRefAction.SetAutoSaveEnabled -> setAutoSaveEnabled(action.enabled)
            is PockRefAction.SetDefaultSavePathEnabled -> setDefaultSavePathEnabled(action.enabled)
            is PockRefAction.SetDefaultSaveDirectory -> setDefaultSaveDirectory(
                action.treeUri,
                action.displayName,
            )
            // V_NOW (2026-06-08 速写练习): the six practice
            // variants. All delegate to [practiceController]
            // which wraps [PracticeReducer] — the reducer is
            // the testable pure-function unit, the controller
            // is the side-effectful shim that persists
            // [lastPracticeConfig] and updates [_uiState].
            is PockRefAction.StartPractice -> practiceController.startPractice(
                action.imageIds,
                action.config,
            )
            is PockRefAction.PracticeTick -> practiceController.onTick(
                remainingMs = action.remainingMs,
                imageIndex = action.imageIndex,
                phase = action.phase,
            )
            PockRefAction.PausePractice -> practiceController.pause()
            PockRefAction.ResumePractice -> practiceController.resume()
            PockRefAction.SkipRest -> practiceController.skipRest()
            PockRefAction.PreviousPracticeImage -> practiceController.previousImage()
            PockRefAction.NextPracticeImage -> practiceController.nextImage()
            PockRefAction.ExitPractice -> practiceController.exit()
            // V_NOW (2026-06-08 速写练习 commit 2): the
            // pre-flight config sheet toggle. The
            // reducer is the single source of truth
            // for [PockRefUiState.practiceConfigSheetOpen]
            // so the [PracticeConfigSheet] ModalBottomSheet
            // re-renders on flag change without the
            // [PockRefApp] composables needing their own
            // remember/mutableStateOf.
            PockRefAction.OpenPracticeConfigSheet -> _uiState.update {
                it.copy(practiceConfigSheetOpen = true)
            }
            PockRefAction.DismissPracticeConfigSheet -> _uiState.update {
                it.copy(practiceConfigSheetOpen = false)
            }
            // V_NOW (2026-06-11): TakePhoto is handled
            // entirely by the UI layer (camera launcher +
            // FileProvider). The result comes back as
            // [ImportImages] via the launcher callback.
            PockRefAction.TakePhoto -> { /* no-op: UI layer */ }
            // V_NOW (2026-06-16, "启动页导出运行日志" feature):
            // Lifecycle observers for the log-export feature.
            // Actual work happens in [startLogExport] which the
            // FAB invokes directly via VM reference. The Started/
            // Finished branches are no-op observers; Failed is
            // surfaced as a normal reportError so the user sees a
            // Snackbar.
            PockRefAction.RequestLogExport -> Unit
            PockRefAction.LogExportStarted -> Unit
            PockRefAction.LogExportFinished -> Unit
            is PockRefAction.LogExportFailed -> reportError(
                "Log export failed: ${action.reason}",
            )
        }
    }

    internal fun setHover(imageId: String?, position: Offset?) {
        _uiState.update { current ->
            if (current.hoveredImageId == imageId && current.hoverPointScreen == position) {
                current
            } else {
                current.copy(hoveredImageId = imageId, hoverPointScreen = position)
            }
        }
    }

    internal fun clearHover() {
        _uiState.update { current ->
            if (current.hoveredImageId == null && current.hoverPointScreen == null) {
                current
            } else {
                current.copy(hoveredImageId = null, hoverPointScreen = null)
            }
        }
    }

    internal fun setHoverPreview(imageId: String?) {
        _uiState.update { current ->
            if (current.hoverPreviewImageId == imageId) {
                current
            } else {
                current.copy(hoverPreviewImageId = imageId)
            }
        }
    }

    internal fun setDragOver(isOver: Boolean) {
        _uiState.update { current ->
            if (current.isDragOver == isOver) current
            else current.copy(isDragOver = isOver)
        }
    }

    internal fun setTopToolbarExpanded(expanded: Boolean) {
        _uiState.update { current ->
            if (current.topToolbarExpanded == expanded) current
            else current.copy(topToolbarExpanded = expanded)
        }
    }

    internal fun setBottomRightPanelExpanded(expanded: Boolean) {
        _uiState.update { current ->
            if (current.bottomRightPanelExpanded == expanded) current
            else current.copy(bottomRightPanelExpanded = expanded)
        }
    }

    /**
     * V_NOW: persist + apply a new minimap screen-space position.
     * Called continuously during a long-press drag. The
     * persisted value is read back in [init] so a fresh
     * process launch restores the user's last position.
     */
    internal fun setMinimapPosition(x: Float, y: Float) {
        appPrefs.minimapPositionX = x
        appPrefs.minimapPositionY = y
        _uiState.update { it.copy(minimapPosition = androidx.compose.ui.geometry.Offset(x, y)) }
    }

    /**
     * V_NOW: persist + apply a new dock edge. Dispatched when
     * the user releases the minimap near a screen edge (the
     * caller — PureRefApp — is responsible for the "is near
     * an edge" check) or when re-expanding from a docked state
     * (in which case the caller passes [MinimapDockEdge.NONE]).
     */
    internal fun setMinimapDockEdge(edge: com.pockref.app.model.MinimapDockEdge) {
        appPrefs.minimapDockEdge = edge
        _uiState.update { it.copy(minimapDockEdge = edge) }
    }

    /**
     * V_NOW: persist + apply the collapsed flag. Independent of
     * the dock edge so the 3s auto-collapse (matching the top
     * toolbar's contract) can collapse to the edge icon
     * WITHOUT forcing a fresh [MinimapDockEdge.NONE] state.
     * The user can be in (a) expanded free-positioned, (b)
     * expanded docked (rare — would need an explicit click on
     * the docked icon to enter), or (c) collapsed docked. (a)
     * → (c) is the auto-collapse path; (c) → (a) is the
     * click-on-edge-icon path.
     */
    internal fun setMinimapCollapsed(collapsed: Boolean) {
        appPrefs.minimapCollapsed = collapsed
        _uiState.update { it.copy(minimapCollapsed = collapsed) }
    }

    /**
     * V_NOW (iOS PiP-style minimap): persist + apply a new
     * pinch-zoom scale for the full minimap panel. The
     * scale is clamped to [0.5f, 2.0f] here so callers
     * (e.g. the [Modifier.pointerInput] handler's
     * [detectTransformGestures] pinch callback) can pass
     * through unclamped gesture values without worrying
     * about the range. Dispatched continuously during a
     * two-finger pinch — the [AppPreferences.minimapZoomScale]
     * setter also clamps, so a corrupt or out-of-range
     * pref survives the next launch without producing a
     * 0.0-px panel.
     *
     * The bar (collapsed) view ignores this value; only
     * the expanded panel uses it.
     */
    internal fun setMinimapZoomScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 2.0f)
        if (clamped == _uiState.value.minimapZoomScale) return
        appPrefs.minimapZoomScale = clamped
        _uiState.update { it.copy(minimapZoomScale = clamped) }
    }

    internal fun setMinimapDisplayMode(mode: com.pockref.app.model.MinimapDisplayMode) {
        if (mode == _uiState.value.minimapDisplayMode) return
        appPrefs.minimapDisplayMode = mode
        _uiState.update { it.copy(minimapDisplayMode = mode) }
    }

    internal fun setMinimapSliderOrientation(orientation: com.pockref.app.model.MinimapSliderOrientation) {
        if (orientation == _uiState.value.minimapSliderOrientation) return
        appPrefs.minimapSliderOrientation = orientation
        _uiState.update { it.copy(minimapSliderOrientation = orientation) }
    }

    // V_NOW (2026-06-16): the [setShowStartPageLogExportFab]
    // reducer / setter is removed. The 7-tap easter-egg state
    // is now a Compose-local remember in
    // [com.pockref.app.ui.EmptyCanvasState] and never round-trips
    // through the ViewModel (intentional — see the field
    // removal KDoc on [PockRefUiState.showStartPageLogExportFab]
    // for the full rationale).

    /**
     * V_NOW: unified chrome collapse on window-blur. Triggered
     * by [PockRefAction.SetMenuBarsCollapsed] dispatched from
     * [com.pockref.app.MainActivity.onWindowFocusChanged] when
     * `hasFocus == false`. The single copy() in one StateFlow
     * update step guarantees all floating menus transition in
     * the same frame (no race / no intermediate "two menus
     * open" frame the user could observe). Collapses:
     *   1. top toolbar (expanded → collapsed)
     *   2. SelectionFloatingToolbar (visible → hidden,
     *      via menuBarsCollapsed flag; covers single-select,
     *      multi-select, and box-select modes)
     *   3. bottom-right action panel (expanded → collapsed)
     * Idempotent: if the state is already fully collapsed the
     * copy is a no-op (data-class equality short-circuits the
     * update publish).
     *
     * V_NOW (focus-restore follow-up): the copy also
     * snapshots [PockRefUiState.topToolbarExpanded] /
     * [PockRefUiState.bottomRightPanelExpanded] into the
     * pre-blur fields BEFORE zeroing them, so the focus
     * reducer [restoreMenuBarsOnWindowFocus] can write the
     * pre-blur values back. The snapshot is gated by
     * `!current.menuBarsCollapsed` so a second consecutive
     * blur (e.g. notification shade yanked twice without
     * focusing) preserves the original snapshot — the
     * focus restore later pulls the pre-first-blur values,
     * not the post-blur "all false" values.
     *
     * The SelectionFloatingToolbar doesn't need a snapshot:
     * its pre-blur "visible" condition is `selectedCount > 0`,
     * and the user's selection is naturally preserved
     * through blur/focus (we never touch the selection
     * state). On focus restore we just clear
     * [PockRefUiState.menuBarsCollapsed] and the
     * SelectionFloatingToolbar re-renders naturally
     * (early-return no longer fires).
     *
     * Side-effects for chrome that lives outside UiState
     * (the recent-files `menuExpanded` local Composable
     * state) are handled in [com.pockref.app.ui.PockRefApp]
     * via a LaunchedEffect keyed on [PockRefUiState.menuBarsCollapsed]
     * with a local `savedMenuExpanded` remember slot.
     */
    internal fun collapseMenuBarsOnWindowBlur() {
        _uiState.update { current ->
            if (current.menuBarsCollapsed) {
                // Already collapsed (double-blur / re-entry
                // before focus restore). Do NOT re-snapshot,
                // otherwise we'd capture the post-blur
                // "all false" values and lose the original.
                // The previous snapshot stays in place.
                current
            } else {
                current.copy(
                    // Snapshot BEFORE zeroing — the focus
                    // restore writes these back to the
                    // live fields. Order matters: snapshot
                    // reads must see pre-blur values, not
                    // the just-zeroed ones.
                    preBlurTopToolbarExpanded = current.topToolbarExpanded,
                    preBlurBottomRightPanelExpanded = current.bottomRightPanelExpanded,
                    topToolbarExpanded = false,
                    bottomRightPanelExpanded = false,
                    menuBarsCollapsed = true,
                )
            }
        }
    }

    /**
     * V_NOW (focus-restore follow-up): the inverse of
     * [collapseMenuBarsOnWindowBlur]. Triggered by
     * [PockRefAction.RestoreMenuBarsOnFocus] dispatched from
     * [com.pockref.app.MainActivity.onWindowFocusChanged]
     * when `hasFocus == true`. Writes the pre-blur snapshot
     * back to the live fields and clears
     * [PockRefUiState.menuBarsCollapsed] so all three
     * Composables re-evaluate their natural visibility
     * from the user's pre-blur state.
     *
     * Idempotent: if [PockRefUiState.menuBarsCollapsed]
     * is already `false` (the typical case on initial
     * Activity onCreate, which fires
     * `onWindowFocusChanged(true)` with a fresh uiState),
     * the reducer no-ops.
     *
     * Why not also snapshot / restore the
     * [PockRefUiState.menuBarsCollapsed] flag itself
     * (= the SelectionFloatingToolbar's "was the toolbar
     * showing pre-blur" state)? Because the natural
     * visibility of SelectionFloatingToolbar is derived
     * from `selectedCount > 0`, which is preserved across
     * blur/focus (we never deselect). The flag is purely
     * the blur-time override; clearing it on focus lets
     * the derived state re-emerge correctly.
     */
    internal fun restoreMenuBarsOnWindowFocus() {
        _uiState.update { current ->
            if (!current.menuBarsCollapsed) {
                // Not collapsed (initial onCreate focus,
                // or focus after a no-op blur). No-op.
                current
            } else {
                current.copy(
                    topToolbarExpanded = current.preBlurTopToolbarExpanded,
                    bottomRightPanelExpanded = current.preBlurBottomRightPanelExpanded,
                    menuBarsCollapsed = false,
                )
            }
        }
    }

    internal fun setSettingsSheetOpen(open: Boolean) {
        _uiState.update { current ->
            if (current.settingsSheetOpen == open) current
            else current.copy(settingsSheetOpen = open)
        }
    }

    internal fun setImportSourceMenuOpen(open: Boolean) {
        _uiState.update { current ->
            if (current.importSourceMenuOpen == open) current
            else current.copy(importSourceMenuOpen = open)
        }
    }

    internal fun setSettingsTab(tab: com.pockref.app.model.SettingsTab) {
        _uiState.update { current ->
            if (current.settingsTab == tab) current
            else current.copy(settingsTab = tab)
        }
    }


    private fun setBackgroundPreset(preset: BackgroundPreset) {
        if (preset == BackgroundPreset.Custom) {
            _uiState.update { it.copy(showBackgroundPicker = true) }
            return
        }
        val target = preset.color
        if (_uiState.value.backgroundColor.toArgb() == target.toArgb()) {
            return
        }
        appPrefs.defaultBackgroundPreset = preset
        // V_NOW: removed the side effect that also flipped
        // [themeMode] based on the preset's luminance. The
        // user's spec is: "背景颜色就只能改颜色,主题只能
        // 在主题那里更改" — background-color and theme are
        // two independent settings. Previously, picking
        // 深灰 / 纯黑 forced theme to DARK and picking
        // 纯白 / 浅灰 / 米黄 / 薄荷 / 天蓝 forced theme to
        // LIGHT, which silently overrode the user's explicit
        // theme choice. The theme is now only changed via
        // [SetThemeMode] (the segmented row in the settings
        // sheet) and the HSV color picker / custom swatch
        // touches backgroundColor + backgroundCustomColor only.
        _uiState.update {
            it.copy(
                backgroundColor = target,
                showBackgroundPicker = false,
            )
        }
        notifyContentChanged()
    }

    private fun setBackgroundColor(color: Color) {
        // V_NOW: same fix as [setBackgroundPreset] — stopped
        // also flipping [themeMode] to LIGHT/DARK based on
        // the chosen color's luminance. Theme is now
        // independent; this method only touches the canvas
        // background color (and the persistent custom-color
        // slot for the "Custom" preset).
        _uiState.update {
            it.copy(
                backgroundColor = color,
                backgroundCustomColor = color,
                showBackgroundPicker = false,
            )
        }
        notifyContentChanged()
    }

    private fun setBackgroundPickerVisible(show: Boolean) {
        if (_uiState.value.showBackgroundPicker == show) return
        _uiState.update { it.copy(showBackgroundPicker = show) }
    }

    private fun setThemeMode(mode: com.pockref.app.data.ThemeMode) {
        if (_uiState.value.themeMode == mode) return
        appPrefs.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    // ---------------------------------------------------------------------
    // Shared scene mutation helpers — used by multiple controllers.
    // ---------------------------------------------------------------------

    internal fun setBusy(value: Boolean) {
        _uiState.update { it.copy(isBusy = value) }
    }

    // ---------------------------------------------------------------------
    // Snap highlight lifecycle.
    //
    // When a drag ends on a snap, the renderer briefly shows a fading
    // outline + edge dots on the snapped group + target image. We store
    // the guide + start time on the UI state; the UI schedules a
    // ClearSnapHighlight via a delay (well, the UI doesn't have a delay
    // hook, so PockRefApp drives it via withFrameNanos — see
    // PockRefCanvas). Setter is a no-op if the same highlight is already
    // active.
    // ---------------------------------------------------------------------

    internal fun setSnapHighlight(highlight: com.pockref.app.model.SnapHighlight) {
        _uiState.update { current ->
            val cur = current.snapHighlight
            if (cur != null &&
                cur.guide == highlight.guide &&
                cur.startTimeMs == highlight.startTimeMs
            ) {
                current
            } else {
                current.copy(snapHighlight = highlight)
            }
        }
    }

    internal fun clearSnapHighlight() {
        _uiState.update { current ->
            if (current.snapHighlight == null) current else current.copy(snapHighlight = null)
        }
    }

    /**
     * Toggle the edge-to-edge snap feature. Persists the new value to
     * AppPreferences so the choice survives app restart. When the user
     * flips the switch mid-drag the new value applies to the NEXT drag
     * — the in-flight drag is left alone (touching the snap guide
     * mid-frame would cause a visible jolt). If a snap guide happens
     * to be visible at the moment of toggle, we also clear it so the
     * user immediately sees their setting took effect.
     */
    internal fun setEdgeSnapEnabled(enabled: Boolean) {
        if (_uiState.value.edgeSnapEnabled == enabled) return
        appPrefs.edgeSnapEnabled = enabled
        _uiState.update { current ->
            current.copy(
                edgeSnapEnabled = enabled,
                // If disabling, immediately retire any visible guide /
                // highlight so the user gets instant visual feedback.
                snapGuide = if (enabled) current.snapGuide else null,
                snapHighlight = if (enabled) current.snapHighlight else null,
            )
        }
    }

    /**
     * Toggle the rotation-angle-snap feature (0°/90°/180°/270° lock).
     * Persists the new value to AppPreferences so the choice survives
     * app restart.
     *
     * Unlike [setEdgeSnapEnabled] we deliberately do NOT clear the
     * live [angleSnapGuide] on toggle — a continuous rotation gesture
     * is "in flight" on every frame, so clearing mid-gesture would
     * cause a visible jolt. The next gesture begin will clear it
     * cleanly.
     */
    internal fun setRotationSnapEnabled(enabled: Boolean) {
        if (_uiState.value.rotationSnapEnabled == enabled) return
        appPrefs.rotationSnapEnabled = enabled
        _uiState.update { current ->
            current.copy(
                rotationSnapEnabled = enabled,
                // Don't touch angleSnapGuide here — the in-flight gesture
                // owns it; the transform layer will stop firing it on
                // the next frame because of the !enabled gate.
            )
        }
    }

    /**
     * Toggle a single gesture on/off (key from [com.pockref.app.ui.GestureId]).
     * Persisted to DataStore so the choice survives process death.
     * The actual gating happens in [com.pockref.app.ui.PockRefApp]'s
     * gesture handlers — they read [PockRefUiState.gestureToggles]
     * (or [com.pockref.app.ui.isGestureEnabled] which defaults
     * `true` for missing keys) and short-circuit when the user
     * has disabled that gesture. This VM method only mutates
     * the state; no business logic here.
     */
    internal fun setGestureEnabled(id: String, enabled: Boolean) {
        if (_uiState.value.gestureToggles[id] == enabled) return
        if (id == GestureId.SINGLE_FINGER_PAN) {
            appPrefs.singleFingerCanvasPanEnabled = enabled
        } else {
            // Persist as the DISABLED set: a missing entry = enabled
            // (the default), so we only store ids the user has
            // turned off. This keeps the prefs file small and means
            // "delete the prefs entry" = "reset to default = on".
            val currentDisabled = appPrefs.disabledGestureIds
            val newDisabled = if (enabled) currentDisabled - id else currentDisabled + id
            appPrefs.disabledGestureIds = newDisabled
        }
        _uiState.update { current ->
            current.copy(gestureToggles = current.gestureToggles + (id to enabled))
        }
    }

    /**
     * Set the auto-save interval and persist it. V_NOW.
     *
     * No state update is needed: the auto-save loop reads
     * [com.pockref.app.data.AppPreferences.autoSaveIntervalMs]
     * directly (not the in-memory [autoSaveManager] state, which is
     * itself a computed property that reads the same preference),
     * so the new value takes effect on the next iteration of the
     * loop without any further wiring. The settings panel also
     * re-reads the preference on every recomposition, so the
     * slider position stays in sync with the live value as soon
     * as we write it.
     *
     * The clamp here matches the slider's [valueRange] in the
     * settings panel (60_000..3_600_000 ms = 1 min..1 h). The
     * slider can't currently produce out-of-range values, but a
     * future code path (debug toggle, deep link) that calls this
     * action with a wider value would silently corrupt the
     * `delay()` call in the auto-save loop — defensive clamping
     * costs nothing and keeps the invariant local to the
     * handler.
     */
    internal fun setAutoSaveIntervalMs(intervalMs: Long) {
        val clamped = intervalMs.coerceIn(60_000L, 3_600_000L)
        if (clamped == appPrefs.autoSaveIntervalMs && _uiState.value.autoSaveIntervalMs == clamped) return
        appPrefs.autoSaveIntervalMs = clamped
        // V_NOW (follow-up after auto-save-switch task):
        // mirror pref → uiState in the same call so the
        // SettingsAutoSaveSection slider position
        // recomposes on the next frame. Without this
        // line, the slider's `value` is read from
        // [appPrefs.autoSaveIntervalMs] (a plain Kotlin
        // getter) which is not a Compose State, so
        // dragging the slider writes the pref but
        // doesn't trigger recomposition → slider
        // visually sticks at the old position. The
        // master switch's [setAutoSaveEnabled] already
        // follows this pattern; this just brings the
        // interval slider in line.
        _uiState.update { it.copy(autoSaveIntervalMs = clamped) }
        // V_NOW (follow-up after "auto-save didn't fire
        // when I set 1 min and waited 1 min" bug): the
        // auto-save coroutine was started once in [init]
        // and reads `autoSaveManager.intervalMs` on each
        // iteration's `delay(...)` call — so the very first
        // `delay()` after a settings change would still
        // use the value the pref had at the time the loop
        // started (e.g. the 5-minute default). The user
        // setting 1 min and waiting 1 min would see
        // nothing because the in-flight 5-min delay
        // hadn't elapsed yet. Restarting the loop here
        // cancels the in-flight delay and starts a fresh
        // one keyed on the new interval, so the next save
        // fires `clamped` ms after this call returns.
        // Cheap (one Job cancel + one new launch) and the
        // common case is exactly one restart per settings
        // drag (the slider's `onValueChange` fires per
        // integer stop, not per pixel), so the call
        // frequency is bounded by the number of discrete
        // stops (13) rather than the drag resolution.
        startAutoSaveLoop()
    }

    /**
     * V_NOW: master switch handler. Writes the pref
     * (so the auto-save coroutine in
     * [AutoSaveManager.shouldAutoSave] honors the new
     * value on its next `delay(intervalMs)` tick) AND
     * updates [_uiState] (so the Switch position
     * + Slider enabled state in the settings sheet
     * reflect the change in the same recomposition).
     *
     * No-op if the value is unchanged: avoids a
     * pointless pref write when the Switch re-fires
     * its onCheckedChange (e.g. if a future code path
     * re-dispatches this action as part of a wider
     * state hydration).
     */
    internal fun setAutoSaveEnabled(enabled: Boolean) {
        if (_uiState.value.autoSaveEnabled == enabled) return
        appPrefs.autoSaveEnabled = enabled
        _uiState.update { it.copy(autoSaveEnabled = enabled) }
        // V_NOW (follow-up after "auto-save didn't fire
        // when I set 1 min and waited 1 min" bug): restart
        // the loop so the in-flight `delay()` honors the
        // new master switch on its next iteration. Without
        // this, toggling the switch off mid-delay still
        // spends one full `delay(intervalMs)` doing
        // no-op ticks (the loop ticks, but [shouldAutoSave]
        // short-circuits because the switch is off — pure
        // CPU + dispatcher waste). Toggling on similarly
        // waits for the in-flight delay to elapse before the
        // first post-enable save fires, which is the same
        // "1 min feels like 5 min" surprise users hit on the
        // interval slider. Restarting fixes both: the next
        // `delay()` reads the new pref immediately.
        startAutoSaveLoop()
    }

    /**
     * Mirror the save-path switch into preferences and uiState. The selected
     * directory is retained while disabled so turning the switch back on does
     * not make the user choose the same folder again.
     */
    internal fun setDefaultSavePathEnabled(enabled: Boolean) {
        if (_uiState.value.defaultSavePathEnabled == enabled) return
        appPrefs.defaultSavePathEnabled = enabled
        _uiState.update { it.copy(defaultSavePathEnabled = enabled) }
    }

    /**
     * Persist the user's preferred SAF tree and mirror it into uiState.
     * Releasing the old persistable grant avoids accumulating stale folder
     * permissions when the user changes or resets the setting.
     */
    internal fun setDefaultSaveDirectory(treeUri: Uri?, displayName: String?) {
        val newUri = treeUri?.toString()?.takeIf { it.isNotBlank() }
        val oldUri = appPrefs.defaultSaveDirectoryUri
        val normalizedName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (
            oldUri == newUri &&
            _uiState.value.defaultSaveDirectoryName == normalizedName
        ) {
            return
        }
        if (oldUri != null && oldUri != newUri) {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldUri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        appPrefs.defaultSaveDirectoryUri = newUri
        appPrefs.defaultSaveDirectoryName = normalizedName
        _uiState.update {
            it.copy(
                defaultSaveDirectoryUri = newUri,
                defaultSaveDirectoryName = normalizedName,
            )
        }
    }

    /**
     * V_NOW: S Pen gesture mode. Writes the pref AND
     * updates the uiState so the settings sheet's
     * RadioButton group re-renders to show the new
     * selection immediately. The detector in
     * MainActivity is NOT hot-swapped — the next process
     * start (or MainActivity recreation, e.g. after a
     * config change) re-reads the pref and constructs a
     * new detector with the new mode. In-flight gestures
     * from the previous mode finish in the old state
     * machine before the swap, which is the safe
     * behavior.
     */
    internal fun setSpenMode(mode: com.pockref.app.spen.SpenMode) {
        appPrefs.spenMode = mode
        _uiState.update { it.copy(spenMode = mode) }
    }

    internal fun beginSceneGesture() {
        if (sceneGestureActive) return
        sceneGestureActive = true
        gestureUndoRecorded = false
        gestureUndoBefore = null
        // Clear any stale angle-snap guide from a prior gesture so the
        // new gesture starts from a clean visual baseline. Mirrors
        // what beginImageDrag does for the drag state machine.
        _uiState.update {
            syncVisibleHistoryFlags(
                it.copy(
                    angleSnapGuide = null,
                ),
            )
        }
    }

    internal fun endSceneGesture() {
        if (!sceneGestureActive) return
        sceneGestureActive = false
        val wasRecorded = gestureUndoRecorded
        val before = gestureUndoBefore
        gestureUndoRecorded = false
        gestureUndoBefore = null
        // Push a single diff for the whole gesture: capture-vs-final.
        if (wasRecorded && before != null) {
            val after = _uiState.value
            if (before.sceneName != after.sceneName ||
                before.backgroundColor != after.backgroundColor ||
                before.images != after.images ||
                before.markupStrokes != after.markupStrokes ||
                before.markupTexts != after.markupTexts
            ) {
                pushHistoryEntry(buildHistoryEntry(before, after, undoStack.size))
            }
        }
        // Clear the angle-snap guide on commit — the in-flight rotation
        // gesture that owned it is now over. Mirrors the snapHighlight
        // / snapGuide clearing in commitImageDrag.
        _uiState.update {
            syncVisibleHistoryFlags(
                it.copy(
                    angleSnapGuide = null,
                ).normalizeSelection(),
            )
        }
    }

    internal fun mutateScene(transform: PockRefUiState.() -> PockRefUiState) {
        val before = _uiState.value
        val after = before.transform().normalizeSelection()
        if (before.sceneName != after.sceneName ||
            before.backgroundColor != after.backgroundColor ||
            before.images != after.images ||
            before.markupStrokes != after.markupStrokes ||
            before.markupTexts != after.markupTexts
        ) {
            pushHistoryEntry(buildHistoryEntry(before, after, undoStack.size))
        }
        _uiState.value = after.syncHistoryFlags(undoStackSize, redoStackSize)
        notifyContentChanged()
    }

    internal fun updateSceneForGesture(transform: PockRefUiState.() -> PockRefUiState) {
        if (!sceneGestureActive) {
            mutateScene(transform)
            return
        }
        if (!gestureUndoRecorded) {
            // Capture the state at the start of the gesture; the diff is
            // pushed at endSceneGesture so a multi-frame gesture produces
            // exactly one undo entry.
            gestureUndoBefore = _uiState.value
            gestureUndoRecorded = true
        }
        _uiState.update { state -> transform(state).normalizeSelection().syncHistoryFlags(undoStackSize, redoStackSize) }
    }

    /**
     * Fallback path used by external callers (e.g. [PockRefViewModelScene.importImages])
     * that can't easily express their change as a `before -> after` pair. We
     * push a full [HistoryEntry.Snapshot] so the call site keeps its current
     * "snapshot of the before-state" semantics. The internal controllers
     * (which can compute precise diffs) use [mutateScene] instead.
     */
    internal fun recordUndoState() {
        pushHistoryEntry(_uiState.value.toHistoryEntry())
    }

    /** Push an entry to the undo stack, enforce the 50-entry cap, and clear redo. */
    private fun pushHistoryEntry(entry: HistoryEntry) {
        undoStack.addLast(entry)
        if (undoStack.size > 50) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    private fun syncVisibleHistoryFlags(state: PockRefUiState): PockRefUiState =
        if (state.markupModeActive) {
            state.copy(canUndo = markupHistory.canUndo, canRedo = markupHistory.canRedo)
        } else {
            state.syncHistoryFlags(undoStackSize, redoStackSize)
        }

    internal fun undoMarkupStep() {
        val current = _uiState.value
        if (!current.markupModeActive) return
        val snapshot = markupHistory.undo(current.toMarkupHistorySnapshot()) ?: return
        _uiState.value = syncVisibleHistoryFlags(current.restoreMarkupHistorySnapshot(snapshot))
    }

    internal fun redoMarkupStep() {
        val current = _uiState.value
        if (!current.markupModeActive) return
        val snapshot = markupHistory.redo(current.toMarkupHistorySnapshot()) ?: return
        _uiState.value = syncVisibleHistoryFlags(current.restoreMarkupHistorySnapshot(snapshot))
    }

    internal fun setMarkupModeActive(active: Boolean) {
        if (active) {
            markupHistory.clear()
            _uiState.update { current ->
                current.copy(
                    markupModeActive = true,
                    bottomRightPanelExpanded = false,
                    selectedImageId = null,
                    selectedImageIds = emptySet(),
                    multiSelectMode = false,
                    boxSelectMode = false,
                    boxSelectRect = null,
                    boxSelectPreviewIds = emptySet(),
                    draftMarkupStroke = null,
                    markupSessionBaseStrokes = current.markupStrokes,
                    markupSessionBaseTexts = current.markupTexts,
                    pendingMarkupTextPosition = null,
                    pendingMarkupTextValue = "",
                    pendingMarkupTextFontSize = 80f,
                    editingMarkupTextId = null,
                    canUndo = false,
                    canRedo = false,
                )
            }
            return
        }

        val beforeState = _uiState.value
        val baseStrokes = beforeState.markupSessionBaseStrokes
        val baseTexts = beforeState.markupSessionBaseTexts
        val afterState = beforeState.copy(
            markupModeActive = false,
            draftMarkupStroke = null,
            markupSessionBaseStrokes = null,
            markupSessionBaseTexts = null,
            pendingMarkupTextPosition = null,
            pendingMarkupTextValue = "",
            pendingMarkupTextFontSize = 80f,
            editingMarkupTextId = null,
        ).syncHistoryFlags(undoStackSize, redoStackSize)
        if (beforeState.markupModeActive && baseStrokes != null && baseTexts != null &&
            (baseStrokes != afterState.markupStrokes || baseTexts != afterState.markupTexts)
        ) {
            val historyBefore = beforeState.copy(
                markupStrokes = baseStrokes,
                markupTexts = baseTexts,
                markupModeActive = false,
                draftMarkupStroke = null,
                markupSessionBaseStrokes = null,
                markupSessionBaseTexts = null,
                pendingMarkupTextPosition = null,
                pendingMarkupTextValue = "",
                pendingMarkupTextFontSize = 80f,
                editingMarkupTextId = null,
            )
            pushHistoryEntry(buildHistoryEntry(historyBefore, afterState, undoStack.size))
        }
        markupHistory.clear()
        _uiState.value = afterState.syncHistoryFlags(undoStackSize, redoStackSize)
        notifyContentChanged()
    }

    internal fun beginMarkupStroke(point: Offset) {
        _uiState.update { current ->
            if (!current.markupModeActive) current else current.copy(
                draftMarkupStroke = CanvasMarkupStroke(
                    lineWidth = (4f / current.canvasScale).coerceIn(1f, 80f),
                    points = listOf(point),
                ),
            )
        }
    }

    internal fun updateMarkupStroke(point: Offset) {
        _uiState.update { current ->
            val draft = current.draftMarkupStroke
            if (!current.markupModeActive || draft == null) {
                current
            } else {
                val last = draft.points.lastOrNull()
                val minWorldDistance = (0.75f / current.canvasScale.coerceAtLeast(0.0001f))
                    .coerceIn(0.02f, 2f)
                if (last != null && (last - point).getDistance() < minWorldDistance) {
                    current
                } else {
                    current.copy(draftMarkupStroke = draft.copy(points = draft.points + point))
                }
            }
        }
    }

    internal fun commitMarkupStroke() {
        _uiState.update { current ->
            val draft = current.draftMarkupStroke
            if (!current.markupModeActive || draft == null) {
                current
            } else {
                val committed = regularizeMarkupStrokeForCommit(draft)
                if (committed.isRenderable) {
                    markupHistory.record(current.toMarkupHistorySnapshot())
                }
                syncVisibleHistoryFlags(
                    current.copy(
                        markupStrokes = if (committed.isRenderable) current.markupStrokes + committed else current.markupStrokes,
                        draftMarkupStroke = null,
                    ),
                )
            }
        }
    }

    internal fun cancelMarkupStroke() {
        _uiState.update { it.copy(draftMarkupStroke = null) }
    }

    internal fun cancelMarkupSession() {
        markupHistory.clear()
        _uiState.update { current ->
            current.copy(
                markupStrokes = current.markupSessionBaseStrokes ?: current.markupStrokes,
                markupTexts = current.markupSessionBaseTexts ?: current.markupTexts,
                markupModeActive = false,
                draftMarkupStroke = null,
                markupSessionBaseStrokes = null,
                markupSessionBaseTexts = null,
                pendingMarkupTextPosition = null,
                pendingMarkupTextValue = "",
                pendingMarkupTextFontSize = 80f,
                editingMarkupTextId = null,
            ).syncHistoryFlags(undoStackSize, redoStackSize)
        }
    }

    internal fun requestMarkupText() {
        val current = _uiState.value
        val viewportCenter = Offset(
            x = -current.canvasOffset.x / current.canvasScale,
            y = -current.canvasOffset.y / current.canvasScale,
        )
        _uiState.update {
            it.copy(
                pendingMarkupTextPosition = viewportCenter,
                pendingMarkupTextValue = "",
                pendingMarkupTextFontSize = (80f / it.canvasScale).coerceIn(24f, 240f),
                editingMarkupTextId = null,
                bottomRightPanelExpanded = false,
            )
        }
    }

    internal fun editMarkupText(textId: String) {
        val current = _uiState.value
        val text = current.markupTexts.firstOrNull { it.id == textId } ?: return
        _uiState.update {
            it.copy(
                pendingMarkupTextPosition = text.position,
                pendingMarkupTextValue = text.text,
                pendingMarkupTextFontSize = text.fontSize.coerceIn(24f, 240f),
                editingMarkupTextId = text.id,
                bottomRightPanelExpanded = false,
            )
        }
    }

    internal fun commitMarkupText() {
        val current = _uiState.value
        if (current.pendingMarkupTextPosition == null) return
        if (current.markupModeActive) {
            _uiState.update {
                val before = it.toMarkupHistorySnapshot()
                val after = commitPendingMarkupTextState(it)
                if (before != after.toMarkupHistorySnapshot()) {
                    markupHistory.record(before)
                }
                syncVisibleHistoryFlags(after)
            }
            return
        }
        mutateScene { commitPendingMarkupTextState(this) }
    }

    internal fun moveMarkupText(textId: String, delta: Offset) {
        val current = _uiState.value
        if (current.markupModeActive) {
            if (sceneGestureActive && !gestureUndoRecorded) {
                markupHistory.record(current.toMarkupHistorySnapshot())
                gestureUndoRecorded = true
            } else if (!sceneGestureActive) {
                markupHistory.record(current.toMarkupHistorySnapshot())
            }
            _uiState.update { syncVisibleHistoryFlags(moveMarkupTextState(it, textId, delta)) }
            return
        }
        updateSceneForGesture { moveMarkupTextState(this, textId, delta) }
    }

    // ---------------------------------------------------------------------
    // Recent scenes persistence.
    // ---------------------------------------------------------------------

    internal fun rememberRecentScene(uri: Uri, label: String, source: String) {
        val filePath = queryDisplayNameSync(uri) ?: ""
        val fileSize = queryFileSizeSync(uri).coerceAtLeast(0L)
        // V_NOW (2026-06-11): if the user has already
        // given this scene a custom label (e.g. via the
        // start-page rename dialog), preserve it instead
        // of overwriting with the scene-internal name or
        // the URI display name. The fallback label is
        // only used when there is no existing entry or
        // the existing entry's label is blank.
        val existingLabel = _uiState.value.recentScenes
            .firstOrNull { it.uri == uri.toString() }?.label
        val resolvedLabel = if (!existingLabel.isNullOrBlank()) existingLabel else label
        val existingPinned = _uiState.value.recentScenes
            .firstOrNull { it.uri == uri.toString() }?.pinned ?: false
        val existingCoverImageId = _uiState.value.recentScenes
            .firstOrNull { it.uri == uri.toString() }?.coverImageId.orEmpty()
        val normalized = RecentSceneEntry(
            uri = uri.toString(),
            label = resolvedLabel,
            source = source,
            filePath = filePath,
            fileSize = fileSize,
            pinned = existingPinned,
            coverImageId = existingCoverImageId,
        )
        val updated = buildList {
            add(normalized)
            addAll(_uiState.value.recentScenes.filterNot { it.uri == normalized.uri }.take(19))
        }
        prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
            listOf(
                it.uri,
                it.label.replace("|", "/"),
                it.source,
                it.timestamp.toString(),
                it.filePath.replace("|", "/"),
                it.fileSize.toString(),
                if (it.pinned) "1" else "0",
                it.coverImageId.orEmpty(),
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    // V_NOW (L2 polish 2026-06-10 + 2026-06-11 "label 与磁盘不同步"
    // fix): rename a recent entry's display label. For file:// URIs
    // the on-disk .pur file is also renamed so the start-page label
    // and the file manager view stay in sync. The prefs row is
    // re-serialized with the new label + (for file URIs) the new
    // filePath + new uri. Empty newLabel is silently dropped.
    internal fun renameRecentScene(uri: String, newLabel: String) {
        if (newLabel.isBlank()) return
        if (_uiState.value.recentScenes.none { it.uri == uri }) return

        // Try to rename the on-disk .pur file when we can.
        // The function returns the (uri, filePath) to use in
        // the persisted row — they may differ from the input
        // if the file was actually renamed, or may be the
        // original values if rename isn't supported for this
        // URI (e.g. content:// on Android 11+ where the SAF
        // tree doesn't expose writable rename for files the
        // app didn't create).
        val (resolvedUri, resolvedFilePath) = renameUnderlyingFile(
            uri = Uri.parse(uri),
            oldLabel = _uiState.value.recentScenes.first { it.uri == uri }.label,
            newLabel = newLabel,
        )
        val resolvedUriString = resolvedUri.toString()

        val updated = _uiState.value.recentScenes.map {
            if (it.uri == uri) {
                it.copy(
                    label = newLabel,
                    uri = resolvedUriString,
                    filePath = resolvedFilePath,
                )
            } else {
                it
            }
        }
        prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
            listOf(
                it.uri,
                it.label.replace("|", "/"),
                it.source,
                it.timestamp.toString(),
                it.filePath.replace("|", "/"),
                it.fileSize.toString(),
                if (it.pinned) "1" else "0",
                it.coverImageId.orEmpty(),
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    /**
     * Try to rename the underlying .pur file so the start-page
     * label and the file manager view stay in sync.
     *
     * Returns the (uri, filePath) pair to persist in
     * [RecentSceneEntry]. For file:// URIs we use
     * [java.io.File.renameTo] and rebuild the uri. For SAF
     * document URIs we attempt
     * [android.provider.DocumentsContract.renameDocument] —
     * this works on most providers (Files app, Documents UI,
     * Drive, SD card) but is a no-op on MediaStore (which
     * doesn't allow renaming through the SAF tree). On a
     * no-op we keep the original uri/filePath.
     */
    private fun renameUnderlyingFile(
        uri: Uri,
        oldLabel: String,
        newLabel: String,
    ): Pair<Uri, String> {
        val context = getApplication<Application>()
        val currentEntry = _uiState.value.recentScenes.firstOrNull { it.uri == uri.toString() }
        val currentFilePath = currentEntry?.filePath ?: ""
        // V_NOW (2026-06-11): strip the .pur extension
        // from oldLabel before computing the desired
        // new name — the new label typed by the user may
        // or may not include the extension, and we want
        // the file on disk to always end in .pur.
        val newName = if (newLabel.endsWith(".pur", ignoreCase = true)) {
            newLabel
        } else {
            "$newLabel.pur"
        }
        try {
            when (uri.scheme) {
                "file" -> {
                    val oldFile = java.io.File(uri.path ?: return uri to currentFilePath)
                    val parent = oldFile.parentFile ?: return uri to currentFilePath
                    val newFile = java.io.File(parent, newName)
                    if (oldFile.absolutePath == newFile.absolutePath) {
                        return uri to currentFilePath
                    }
                    if (!oldFile.renameTo(newFile)) {
                        android.util.Log.w(
                            "PockRefRename",
                            "File.renameTo returned false for ${oldFile.absolutePath} -> ${newFile.absolutePath}",
                        )
                        return uri to currentFilePath
                    }
                    return Uri.fromFile(newFile) to newName
                }
                "content" -> {
                    val resolver = context.contentResolver
                    val newDisplayName = newName
                    val renamed = runCatching {
                        android.provider.DocumentsContract.renameDocument(
                            resolver,
                            uri,
                            newDisplayName,
                        )
                    }.getOrNull()
                    return if (renamed != null) {
                        // Re-query the display name so the
                        // persisted [filePath] matches the
                        // new file.
                        val refreshedName = queryDisplayNameSync(renamed) ?: newDisplayName
                        renamed to refreshedName
                    } else {
                        // Provider doesn't support rename
                        // (e.g. MediaStore on some OEMs).
                        // Just keep the original uri but
                        // update the persisted label so
                        // the start page shows the new
                        // name even if the file manager
                        // shows the old one.
                        uri to currentFilePath
                    }
                }
                else -> return uri to currentFilePath
            }
        } catch (t: Throwable) {
            android.util.Log.e("PockRefRename", "rename failed", t)
            return uri to currentFilePath
        }
    }

    // V_NOW (2026-06-17, "启动页左滑删除 = 真删本地文件"): the
    // single-file start-page delete (dispatched by
    // [PockRefAction.RemoveRecentScene] from the swipe-to-delete
    // button in [com.pockref.app.ui.RecentFileGrid]) used to
    // be a *list-only* delete — it filtered the URI out of the
    // recent-scenes prefs entry but left the underlying .pur
    // file on disk. The user saw "scene disappeared from the
    // grid" but the file was still on disk and would resurface
    // next time it was picked / opened by another app.
    //
    // Fixed by routing through the same disk-delete path that
    // [bulkDeleteRecentScenes] uses — the bulk handler is
    // already idempotent, async-safe, handles the lost-URI /
    // already-gone / unsupported-provider exception cases,
    // surfaces a snackbar with the result, and re-filters the
    // recent-scenes list to drop only the URIs that actually
    // deleted. We pass `listOf(uri)` so the bulk path runs
    // unchanged for the single-file case.
    //
    // UX: the swipe already has a two-step commit
    // (swipe-past-threshold → tap revealed delete button), so
    // we don't add a second confirmation dialog here. The
    // result snackbar ("已删除 1 个文件" / "删除失败") is the
    // feedback.
    internal fun removeRecentScene(uri: String) {
        bulkDeleteRecentScenes(listOf(uri))
    }

    internal fun dropRecentSceneFromList(uri: String) {
        val updated = _uiState.value.recentScenes.filterNot { it.uri == uri }
        if (updated.size == _uiState.value.recentScenes.size) return
        persistRecentScenes(updated)
        _uiState.update { current ->
            current.copy(
                recentSelection = current.recentSelection - uri,
                recentMultiSelectMode = current.recentMultiSelectMode &&
                    (current.recentSelection - uri).isNotEmpty(),
            )
        }
    }

    private fun loadRecentScenes(): List<RecentSceneEntry> {
        // V_NOW (2026-06-11): delegate to the pure
        // [com.pockref.app.model.decodeRecentScenes] so the
        // encode/decode round-trip is unit-testable on the
        // JVM. The format is byte-identical to the inline
        // implementation that lived here previously — see
        // [com.pockref.app.model.encodeRecentScenes] for the
        // 8-field layout.
        //
        // V_NOW (2026-06-11): we intentionally do NOT
        // auto-prune entries whose underlying file
        // no longer exists. The user has explicitly
        // asked for the recent-scenes list to keep
        // showing entries even when the file is
        // temporarily unreachable (e.g. SD card
        // unmounted, file moved). The user can
        // long-press to delete the entry manually
        // if they want to remove it permanently.
        return decodeRecentScenes(prefs.getString(RECENT_SCENES_KEY, null))
    }

    // V_NOW (L2 empty-canvas revamp 2026-06-10): flip the
    // pinned flag on the entry whose URI matches uri.
    // Pinned entries float to the top of the RecentFileGrid
    // regardless of timestamp sort. Re-serializes the prefs
    // entry so the flag survives process death.
    internal fun togglePinRecentScene(uri: String) {
        val target = _uiState.value.recentScenes.firstOrNull { it.uri == uri } ?: return
        val updated = _uiState.value.recentScenes.map {
            if (it.uri == uri) it.copy(pinned = !it.pinned) else it
        }
        prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
            listOf(
                it.uri,
                it.label.replace("|", "/"),
                it.source,
                it.timestamp.toString(),
                it.filePath.replace("|", "/"),
                it.fileSize.toString(),
                if (it.pinned) "1" else "0",
                it.coverImageId.orEmpty(),
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    // ── Start-page multi-select (V_NOW 2026-06-10) ─────────────────
    // The seven internal handlers below drive the new
    // start-page multi-select chrome. They share a single
    // prefs serialization helper ([persistRecentScenes])
    // that wraps the same `|`-delimited row format the
    // four pre-existing single-file handlers
    // ([addRecentScene] / [removeRecentScene] /
    // [renameRecentScene] / [togglePinRecentScene]) write
    // inline. The four pre-existing handlers are left
    // untouched in THIS commit to keep the diff additive
    // (a follow-up commit can migrate them to the
    // helper; the on-disk format is identical either way
    // so a user who pins from the start page sees the
    // same prefs row as one who pins from the canvas-
    // side "edit scene" path).
    //
    // V_NOW: every recent-scene mutation — single-file
    // AND bulk — goes through the same `RECENT_SCENES_KEY`
    // prefs key, so a future migration to a Room/SQLite
    // recent-scenes table only needs to migrate one key.

    /**
     * V_NOW (2026-06-10): single-source prefs writer for
     * the recent-scenes list, called by the bulk-pin
     * handler below. The four pre-existing single-file
     * handlers still inline the same `|`-serialization
     * (left untouched for this commit to keep the diff
     * additive) but the on-disk format is byte-identical
     * — the helper is the source of truth going forward,
     * the inlined copies are kept only for
     * diff-minimality. Takes the full updated list and
     * writes it as a single atomic `apply()`.
     */
    private fun persistRecentScenes(updated: List<RecentSceneEntry>) {
        prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
            listOf(
                it.uri,
                it.label.replace("|", "/"),
                it.source,
                it.timestamp.toString(),
                it.filePath.replace("|", "/"),
                it.fileSize.toString(),
                if (it.pinned) "1" else "0",
                it.coverImageId.orEmpty(),
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    // V_NOW (2026-06-10): enter multi-select mode on the
    // start page. Idempotent — dispatching when mode is
    // already true is a no-op (the selection set is left
    // untouched in that case so a stray re-dispatch
    // doesn't drop the user's picks). The optional
    // [seedUri] is the URI the long-press happened on;
    // it's pre-selected so the user has a visible target
    // on entry.
    internal fun enterRecentMultiSelect(seedUri: String?) {
        if (_uiState.value.recentMultiSelectMode) return
        _uiState.update {
            it.copy(
                recentMultiSelectMode = true,
                recentSelection = if (seedUri != null) setOf(seedUri) else emptySet(),
            )
        }
    }

    // V_NOW (2026-06-10): exit multi-select mode AND
    // clear the selection. The reducer flips both the
    // mode flag and the selection set in a single update
    // so the UI sees a consistent "no chrome, no picks"
    // state. Idempotent.
    internal fun exitRecentMultiSelect() {
        if (!_uiState.value.recentMultiSelectMode &&
            _uiState.value.recentSelection.isEmpty()
        ) return
        _uiState.update {
            it.copy(
                recentMultiSelectMode = false,
                recentSelection = emptySet(),
            )
        }
    }

    // V_NOW (2026-06-10): toggle [uri] in
    // [PockRefUiState.recentSelection]. No-op if
    // multi-select mode is off (a stray dispatch from a
    // tap that arrived during the mode-flip transition
    // shouldn't pollute the selection set when the chrome
    // is about to disappear anyway). Also no-ops if
    // [uri] doesn't correspond to a current
    // [PockRefUiState.recentScenes] entry — defensive
    // against a stale dispatch from a deleted file.
    internal fun toggleRecentSelection(uri: String) {
        val state = _uiState.value
        if (!state.recentMultiSelectMode) return
        if (state.recentScenes.none { it.uri == uri }) return
        _uiState.update {
            val next = it.recentSelection.toMutableSet().apply {
                if (!add(uri)) remove(uri)
            }
            it.copy(recentSelection = next)
        }
    }

    // V_NOW (2026-06-10): select every
    // [RecentSceneEntry] in the current [recentScenes]
    // list. Replaces (not adds to) the existing
    // selection set — matches the Gmail "全选" semantics
    // where the button is an absolute state, not a
    // "select the rest" toggle. No-op on an empty
    // [recentScenes] (button click while the list is
    // empty is invisible anyway since the chrome doesn't
    // render when there are no recent files).
    internal fun selectAllRecentScenes() {
        if (!_uiState.value.recentMultiSelectMode) return
        _uiState.update {
            it.copy(
                recentSelection = it.recentScenes.map { entry -> entry.uri }.toSet(),
            )
        }
    }

    // V_NOW (2026-06-10): clear the selection WITHOUT
    // exiting multi-select mode. The bottom bar's "清空"
    // affordance (or its "all cleared" sentinel state)
    // dispatches this. Idempotent.
    internal fun clearRecentSelection() {
        if (_uiState.value.recentSelection.isEmpty()) return
        _uiState.update { it.copy(recentSelection = emptySet()) }
    }

    // V_NOW (2026-06-10): bulk-set the `pinned` flag on
    // every URI in the current selection. Filters the
    // selection to only URIs that still exist in
    // [recentScenes] (an orphan from a deleted file would
    // be a no-op anyway, but filtering up front keeps
    // the on-disk format clean). No-op when selection is
    // empty. Writes the prefs entry once at the end —
    // NOT once per entry — so a 12-file pin is a single
    // `apply()` instead of 12 fsyncs.
    internal fun bulkSetRecentPin(pinned: Boolean) {
        val state = _uiState.value
        val targets = state.recentSelection
            .intersect(state.recentScenes.map { it.uri }.toSet())
        if (targets.isEmpty()) return
        val updated = state.recentScenes.map { entry ->
            if (entry.uri in targets) entry.copy(pinned = pinned) else entry
        }
        persistRecentScenes(updated)
    }

    // V_NOW (2026-06-10): bulk-delete the URIs in
    // [uris]. Filters the input to only URIs that still
    // exist in [recentScenes] (defense against a stale
    // dispatch). After the delete, the selection set is
    // filtered to keep only the survivors — a leftover
    // selection count that points at no files would
    // confuse the bottom-bar count. If the delete leaves
    // [recentScenes] empty, multi-select mode is
    // auto-exited so the chrome doesn't hang around an
    // empty list. The bulk-delete confirm dialog is
    // auto-closed in the same update (matches the
    // user's mental model: "I confirmed, the dialog
    // went away, the files are gone").
    // V_NOW (2026-06-10, user spec "启动页删除 = 真删本地文件"):
    // the bulk-delete handler now ACTUALLY deletes
    // the files from disk via [DocumentsContract.deleteDocument]
    // (the canonical SAF API), not just removes them
    // from the recent-scenes prefs list. The
    // previous behaviour (list-only) silently
    // diverged from the user's mental model — they
    // tapped "删除" in a confirmation dialog that
    // said "此操作不可撤销" and the file stayed on
    // disk.
    //
    // Flow:
    //   1. Synchronously close the confirm dialog
    //      (snappy UX — the user has confirmed,
    //      the dialog should not block).
    //   2. Launch a [viewModelScope] coroutine on
    //      [Dispatchers.IO] for the actual disk
    //      deletes. Each delete is a per-URI
    //      [DocumentsContract.deleteDocument] call,
    //      wrapped in [try-catch] so a single
    //      failure (e.g. [SecurityException] for a
    //      lost URI permission, [FileNotFoundException]
    //      for an already-deleted file) doesn't
    //      abort the rest of the batch.
    //   3. After the batch completes, filter the
    //      recent-scenes list to remove ONLY the
    //      URIs that successfully deleted (a file
    //      that failed to delete stays in the
    //      recents so the user can retry, and the
    //      [RecentSceneEntry] row lets them see
    //      what's there).
    //   4. Surface a result snackbar via
    //      [errorsFlow] / [reportError] so the user
    //      gets feedback even when not in
    //      start-page mode (e.g. the deletion
    //      finishes after they navigated away).
    //
    // Permission: each recent-scene URI has a
    // [FLAG_GRANT_PERSISTABLE_URI_PERMISSION]
    // (granted by the SAF picker when the user
    // originally picked the file — see
    // [com.pockref.app.ui.PureRefApp] near the
    // SAF import launchers, and
    // [takePersistableUriPermission] further
    // down in this file). The delete API
    // requires WRITE permission, which the
    // persistable grant covers for files the
    // user picked. [SecurityException] is
    // expected only for stale prefs entries whose
    // URI is no longer valid (e.g. the user
    // revoked the permission in Settings) — those
    // entries are also failed-deletes that stay
    // in the recents and show a snackbar hint.
    internal fun bulkDeleteRecentScenes(uris: List<String>) {
        val state = _uiState.value
        val targets = uris
            .intersect(state.recentScenes.map { it.uri }.toSet())
        if (targets.isEmpty()) return
        // Step 1: close the dialog immediately. The
        // user has confirmed; the dialog should
        // not linger while the disk deletes run.
        _uiState.update { it.copy(bulkDeleteConfirmOpen = false) }
        // Step 2: launch the disk-delete batch on
        // a coroutine. The handler returns
        // synchronously so the dispatched
        // action's call site (the AlertDialog's
        // onClick) completes immediately; the
        // actual deletes happen in the
        // background.
        viewModelScope.launch(Dispatchers.IO) {
            val (deletedUris, failedUris) = deleteFilesInBatch(targets)
            // Step 3: re-fetch the latest state. The
            // user may have entered / exited
            // multi-select, started a practice
            // session, etc. between the dialog
            // dismiss and the batch completion.
            val current = _uiState.value
            val survivors = current.recentScenes.map { it.uri }.toSet()
            val toRemoveFromList = deletedUris.intersect(survivors)
            if (toRemoveFromList.isNotEmpty()) {
                val updated = current.recentScenes.filterNot { it.uri in toRemoveFromList }
                val survivingSelection = current.recentSelection.intersect(
                    updated.map { it.uri }.toSet(),
                )
                val newMultiSelectMode = current.recentMultiSelectMode && updated.isNotEmpty()
                prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
                    listOf(
                        it.uri,
                        it.label.replace("|", "/"),
                        it.source,
                        it.timestamp.toString(),
                        it.filePath.replace("|", "/"),
                        it.fileSize.toString(),
                        if (it.pinned) "1" else "0",
                        it.coverImageId.orEmpty(),
                    ).joinToString("|")
                }).apply()
                _uiState.update {
                    it.copy(
                        recentScenes = updated,
                        recentSelection = survivingSelection,
                        recentMultiSelectMode = newMultiSelectMode,
                    )
                }
            }
            // Step 4: surface the result. The
            // message is intentionally concise —
            // a verbose "deleted N out of M files"
            // would be noisy if the user just
            // selected 1 file and it succeeded.
            // The three cases map to three
            // distinct messages.
            when {
                failedUris.isEmpty() && deletedUris.isNotEmpty() ->
                    reportError("已删除 ${deletedUris.size} 个文件")
                deletedUris.isEmpty() && failedUris.isNotEmpty() ->
                    // All failed — the most common
                    // case is the user revoked
                    // SAF permissions, or the file
                    // is on a provider that doesn't
                    // support delete (some cloud
                    // providers return
                    // UnsupportedOperationException).
                    reportError("删除失败：${failedUris.size} 个文件无法删除")
                else ->
                    // Mixed result — surface the
                    // successful count and note the
                    // failure count so the user
                    // knows some files are still
                    // there.
                    reportError(
                        "已删除 ${deletedUris.size} 个文件,${failedUris.size} 个失败",
                    )
            }
        }
    }

    /**
     * V_NOW (2026-06-10, "启动页删除 = 真删本地文件" backing
     * helper): performs the actual disk deletion of a
     * batch of [android.net.Uri]s via
     * [DocumentsContract.deleteDocument]. Returns a
     * `(deleted, failed)` pair partitioned by success.
     *
     * The helper is called from
     * [bulkDeleteRecentScenes] inside a
     * [viewModelScope] coroutine on [Dispatchers.IO],
     * so the per-URI blocking IO does not stall the
     * main thread. Each delete is independent — a
     * failure on one URI does NOT abort the rest of
     * the batch (the SAF delete API can fail for
     * unrelated reasons per URI, e.g. one file's
     * provider has stricter ACLs than another's).
     *
     * Expected exception classes:
     *   - [SecurityException]: URI permission lost
     *     (user revoked the persistable grant in
     *     Settings, or the URI was never persistable
     *     in the first place). Treated as "deleted"
     *     — the entry is a ghost the user can't
     *     open or re-delete, so we remove it from
     *     the list.
     *   - [FileNotFoundException]: file already gone
     *     (deleted out-of-band, moved, etc.).
     *     Treated as "deleted" — the file no longer
     *     exists, so the entry should be removed.
     *   - [UnsupportedOperationException]: the
     *     provider for this URI doesn't implement
     *     delete (some read-only cloud providers)
     *   - [IllegalArgumentException]: malformed URI, or a missing
     *     document wrapped by some OEM providers. The latter is identified
     *     by [isMissingDocumentFailure] and treated as already deleted.
     *
     * [SecurityException], direct file-not-found failures, and documented
     * missing-document wrappers are treated as "deleted" (entry removed from
     * list). The remaining exceptions map to "failed" and are returned to
     * the caller for state-update branching + snackbar message.
     */
    private fun deleteFilesInBatch(
        uris: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val deleted = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        uris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            try {
                DocumentsContract.deleteDocument(contentResolver, uri)
                deleted.add(uriString)
            } catch (e: SecurityException) {
                // V_NOW (2026-06-12 "本地已删除文件无法清除" fix):
                // Lost URI permission — the file
                // may have been deleted out-of-band
                // (file manager), moved, or the
                // persistable grant was revoked.
                // Either way the entry is a ghost:
                // the user can't open it, and
                // retrying delete will also fail.
                // Remove from the list so the user
                // isn't stuck with an un-deletable
                // entry.
                deleted.add(uriString)
                android.util.Log.i(
                    "PockRefRecent",
                    "bulkDelete: lost URI permission, removing entry: $uriString",
                )
            } catch (e: Throwable) {
                if (isMissingDocumentFailure(e)) {
                    // Some OEM providers report an already-moved/deleted file as
                    // IllegalArgumentException("... Missing file ...") instead of
                    // FileNotFoundException. The recent entry is still a ghost.
                    deleted.add(uriString)
                    android.util.Log.i(
                        "PockRefRecent",
                        "bulkDelete: file already gone, removing entry: $uriString",
                    )
                } else {
                    // UnsupportedOperationException, invalid URIs, and other
                    // provider failures leave the entry intact so it can be retried.
                    failed.add(uriString)
                    android.util.Log.w(
                        "PockRefRecent",
                        "bulkDelete: ${e.javaClass.simpleName} for $uriString: ${e.message}",
                    )
                }
            }
        }
        return deleted to failed
    }

    // V_NOW (L2 empty-canvas revamp 2026-06-10): the new
    // single-launcher import path. Reads the URI's MIME
    // via ContentResolver and routes the URI list to
    // importImages for image MIME or to loadScene for
    // the PockRef scene MIME / octet-stream / unknown. Empty
    // lists are silently dropped.
    internal fun importMixed(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val firstMime = contentResolver.getType(uris.first())
        val isSceneMime = firstMime == "application/x-pockref-scene" ||
            firstMime == "application/octet-stream"
        if (isSceneMime) {
            sceneController.loadScene(uris.first())
        } else {
            sceneController.importImages(uris)
        }
    }

    // ---------------------------------------------------------------------
    // Image byte resolution.
    // ---------------------------------------------------------------------

    internal fun resolveImageBytes(image: SceneImage): ByteArray {
        if (image.imageBytes.isNotEmpty()) return image.imageBytes
        if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
            // Prefer SceneImageDecoder's RAF (same handle used for canvas decode)
            imageDecoder.readImageBytes(image.imageByteOffset, image.imageByteLength)?.let { return it }
            return imageLazyLoader.readImageBytes(image.imageByteOffset, image.imageByteLength)
                ?: error("Unable to read image data for ${image.name}")
        }
        error("No image data available for ${image.name}")
    }

    internal fun resolveRawImageBytes(image: SceneImage): ByteArray {
        if (image.imageBytes.isNotEmpty()) return image.imageBytes
        if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
            imageDecoder.readImageBytes(image.imageByteOffset, image.imageByteLength)?.let { return it }
            return codec.imageLazyLoader.readImageBytes(image.imageByteOffset, image.imageByteLength) ?: ByteArray(0)
        }
        return ByteArray(0)
    }

    // ---------------------------------------------------------------------
    // URI / file helpers used by scene open / save.
    // ---------------------------------------------------------------------

    internal suspend fun queryDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        queryDisplayNameSync(uri)
    }

    internal fun queryDisplayNameSync(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
        }.onFailure { error ->
            Log.w(TAG, "queryDisplayName failed for $uri", error)
        }.getOrNull()
    }

    internal fun queryFileSizeSync(uri: Uri): Long {
        queryUriSize(uri)?.let { return it }
        for (candidate in sceneUriCandidates(uri)) {
            queryUriSize(candidate)?.let { return it }
        }
        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0L) return descriptor.length
            }
        }
        return -1L
    }

    private fun queryUriSize(uri: Uri): Long? {
        return runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
        }.getOrNull()
    }

    internal fun openSceneFile(uri: Uri): CachedSceneFile {
        val expectedSize = queryFileSizeSync(uri)
        val tempFile = File.createTempFile("pockref_scene_", ".pur", getApplication<Application>().cacheDir)
        val attempts = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        try {
            for (candidate in sceneUriCandidates(uri)) {
                copySceneCandidate(candidate, tempFile, attempts, failures)?.let { copiedBytes ->
                    if (copiedBytes > 0L) return CachedSceneFile(tempFile)
                }
            }
            android.util.Log.w(TAG, "All scene read attempts produced 0 bytes for $uri: ${attempts.joinToString("; ")}")
            throw IllegalStateException(
                "读取到 0 字节；URI=$uri，提供方大小=${formatDiagnosticFileSize(expectedSize)}，已尝试 ${attempts.size} 条读取路径",
                failures.lastOrNull(),
            )
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    private fun sceneUriCandidates(uri: Uri): List<Uri> {
        val candidates = linkedSetOf(uri)
        if (DocumentsContract.isDocumentUri(getApplication(), uri) &&
            uri.authority == "com.android.providers.downloads.documents"
        ) {
            runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?.let { documentId ->
                    if (documentId.startsWith("raw:")) {
                        candidates += Uri.fromFile(File(Uri.decode(documentId.removePrefix("raw:"))))
                    }
                    val numericId = documentId.substringAfter(':').toLongOrNull()
                    if (numericId != null) {
                        candidates += ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            numericId,
                        )
                        candidates += ContentUris.withAppendedId(
                            Uri.parse("content://downloads/my_downloads"),
                            numericId,
                        )
                        candidates += ContentUris.withAppendedId(
                            Uri.parse("content://downloads/all_downloads"),
                            numericId,
                        )
                        candidates += ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"),
                            numericId,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            candidates += ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                numericId,
                            )
                            candidates += ContentUris.withAppendedId(
                                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                                numericId,
                            )
                        }
                    }
                }
        }
        return candidates.toList()
    }

    private fun copySceneCandidate(
        uri: Uri,
        target: File,
        attempts: MutableList<String>,
        failures: MutableList<Throwable>,
    ): Long? {
        copyFromInputStream(uri, target, attempts, failures)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        copyFromAssetFileDescriptor(uri, target, attempts, failures)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        copyFromFileDescriptor(uri, target, attempts, failures)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        return null
    }

    private fun copyFromInputStream(
        uri: Uri,
        target: File,
        attempts: MutableList<String>,
        failures: MutableList<Throwable>,
    ): Long? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target, false).use { output ->
                    input.copyTo(output, 65536)
                }
            }
        }.fold(
            onSuccess = { copiedBytes ->
                attempts += "input stream@$uri=${copiedBytes ?: "null"}"
                copiedBytes
            },
            onFailure = { error ->
                attempts += "input stream@$uri failed:${error.javaClass.simpleName}"
                failures += error
                Log.w(TAG, "openSceneFile input stream failed for $uri", error)
                null
            },
        )
    }

    private fun copyFromAssetFileDescriptor(
        uri: Uri,
        target: File,
        attempts: MutableList<String>,
        failures: MutableList<Throwable>,
    ): Long? {
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.createInputStream().use { input ->
                    FileOutputStream(target, false).use { output ->
                        input.copyTo(output, 65536)
                    }
                }
            }
        }.fold(
            onSuccess = { copiedBytes ->
                attempts += "asset fd@$uri=${copiedBytes ?: "null"}"
                copiedBytes
            },
            onFailure = { error ->
                attempts += "asset fd@$uri failed:${error.javaClass.simpleName}"
                failures += error
                Log.w(TAG, "openSceneFile asset fd failed for $uri", error)
                null
            },
        )
    }

    private fun copyFromFileDescriptor(
        uri: Uri,
        target: File,
        attempts: MutableList<String>,
        failures: MutableList<Throwable>,
    ): Long? {
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(target, false).use { output ->
                        input.copyTo(output, 65536)
                    }
                }
            }
        }.fold(
            onSuccess = { copiedBytes ->
                attempts += "file descriptor@$uri=${copiedBytes ?: "null"}"
                copiedBytes
            },
            onFailure = { error ->
                attempts += "file descriptor@$uri failed:${error.javaClass.simpleName}"
                failures += error
                Log.w(TAG, "openSceneFile file descriptor failed for $uri", error)
                null
            },
        )
    }

    internal fun takePersistablePermission(uri: Uri, read: Boolean, write: Boolean) {
        val flags = (if (read) android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (write) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags == 0) return
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
            .onFailure { e ->
                android.util.Log.w(
                    "PockRefSave",
                    "takePersistableUriPermission failed for uri=$uri: ${e.javaClass.simpleName}: ${e.message}",
                )
            }
    }

    private fun formatDiagnosticFileSize(size: Long): String {
        return if (size >= 0L) "$size bytes" else "unknown"
    }

    /**
     * V_NOW (2026-06-16, "启动页导出运行日志" feature):
     * Build the run-log text on IO and hand it back via [onReady].
     * The Composable owns the SAF launcher and uses [onReady] to
     * receive the assembled text + suggested filename. The text
     * never enters StateFlow (it's too big — logcat alone can be
     * hundreds of KB).
     *
     * Idempotent: a second call while one is in progress is a no-op.
     *
     * @param appLabel  Result of `context.applicationInfo.loadLabel(...)`
     *                  or the literal "PockRef". Used as the filename prefix.
     * @param onReady   Called on the main thread when the text is ready.
     * @param onError   Called on the main thread if [com.pockref.app.data.LogExporter.buildForContext]
     *                  throws.
     */
    fun startLogExport(
        appLabel: String,
        onReady: (suggestedName: String, content: String) -> Unit,
        onError: (reason: String) -> Unit,
    ) {
        if (_logExportInProgress.value) return
        viewModelScope.launch {
            _logExportInProgress.value = true
            onAction(PockRefAction.LogExportStarted)

            // 30s watchdog: if build hangs (extremely unlikely — logcat
            // exec could theoretically block on a hostile device), reset
            // state and surface a timeout via onError.
            val watchdogJob = launch {
                kotlinx.coroutines.delay(30_000L)
                if (_logExportInProgress.value) {
                    Log.w(TAG, "Log export timed out")
                    _logExportInProgress.value = false
                    onError("timeout")
                    onAction(PockRefAction.LogExportFailed("timeout"))
                }
            }

            try {
                val lastCrash = com.pockref.app.data.CrashHandler.readLastCrash(getApplication())
                val prefsLines = listOf(
                    "Theme mode:           ${appPrefs.themeMode.name}",
                    "Auto-save:            ${if (appPrefs.autoSaveEnabled) "enabled (interval ${appPrefs.autoSaveIntervalMs / 1000}s)" else "disabled"}",
                    "S Pen mode:           ${appPrefs.spenMode.name}",
                    "Recent scenes count:  ${_uiState.value.recentScenes.size}",
                    *(
                        if (appPrefs.disabledGestureIds.isNotEmpty()) {
                            arrayOf("Disabled gestures:    ${appPrefs.disabledGestureIds.sorted()}")
                        } else emptyArray()
                        ),
                )
                val text = com.pockref.app.data.LogExporter.buildForContext(
                    context = getApplication(),
                    appLabel = appLabel,
                    recentErrors = snapshotRecentErrors(),
                    lastCrash = lastCrash,
                    prefsLines = prefsLines,
                )
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val name = "${appLabel}_日志导出_$ts.txt"
                watchdogJob.cancel()
                onReady(name, text)
                onAction(PockRefAction.LogExportFinished)
            } catch (t: Throwable) {
                watchdogJob.cancel()
                Log.e(TAG, "Log export failed", t)
                val msg = t.message ?: t::class.java.simpleName
                onError(msg)
                onAction(PockRefAction.LogExportFailed(msg))
            } finally {
                _logExportInProgress.value = false
            }
        }
    }

    internal class CachedSceneFile(val file: File) : java.io.Closeable {
        override fun close() {
            file.delete()
        }
    }

    companion object {
        private const val TAG = "PockRefViewModel"
        // V_NOW (2026-06-16): ring-buffer cap for the
        // log-export [recentErrors] snapshot.
        private const val MAX_RECENT_ERRORS = 50
    }
}
