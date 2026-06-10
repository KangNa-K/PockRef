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
import com.pockref.app.data.PurFileParser
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.data.SceneImageDecoder
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.SceneNaming
import com.pockref.app.model.SceneImage
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
    internal val imageOptimizer = ImageOptimizer(application)

    internal val undoStack: ArrayDeque<HistoryEntry> = ArrayDeque()
    internal val redoStack: ArrayDeque<HistoryEntry> = ArrayDeque()
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
            // The disabled-gestures set is the ONLY persistence for
            // gesture toggles (no per-key Boolean like the other
            // settings). A gesture is "enabled" by default, so
            // the in-memory map is built from the *complement* of
            // the disabled set: id → true for every id that is
            // NOT in [disabledGestureIds]. A missing key in the
            // resulting map still reads as enabled via
            // [com.pockref.app.ui.isGestureEnabled]'s `?: true`
            // fallback, so the absence of an entry = "default
            // on", which is what we want.
            gestureToggles = buildMap {
                com.pockref.app.ui.GestureId.ALL.forEach { id ->
                    put(id, id !in appPrefs.disabledGestureIds)
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
    // [PockRefUiState.practiceState]. The six
    // [PockRefAction.StartPractice] / [PracticeTick] /
    // PausePractice / ResumePractice / SkipRest /
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
                if (state.images.isEmpty()) continue
                val scene = state.toScene()
                if (autoSaveManager.shouldAutoSave(System.currentTimeMillis())) {
                    Log.d("AutoSave", "shouldAutoSave=true, calling autoSave()")
                    val saved = autoSaveManager.autoSave(scene, currentSceneUri, contentResolver)
                    Log.d("AutoSave", "autoSave() returned saved=$saved, uri=$currentSceneUri")
                    if (saved) {
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
                        _uiState.update {
                            it.copy(lastAutoSaveAt = System.currentTimeMillis())
                        }
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
        val dirty = hasUnsavedChanges && _uiState.value.images.isNotEmpty()
        val cur = _uiState.value
        if (cur.canDirectSave != directSave || cur.hasUnsavedChanges != dirty) {
            _uiState.update { it.copy(canDirectSave = directSave, hasUnsavedChanges = dirty) }
        }
    }

    val hasUnsavedChanges: Boolean
        get() {
            val scene = _uiState.value.toScene()
            if (scene.images.isEmpty()) return false
            val currentHash = computeQuickHash(scene)
            return currentHash != lastSaveSnapshotHash
        }

    val canDirectSave: Boolean
        get() = isImportedPurFile && currentSceneUri != null && hasUnsavedChanges

    fun onAction(action: PockRefAction) {
        when (action) {
            PockRefAction.ResetView -> batchController.resetView()
            PockRefAction.FitToScreen -> batchController.fitToScreen()
            PockRefAction.Undo -> transformController.undo()
            PockRefAction.Redo -> transformController.redo()
            PockRefAction.ToggleMultiSelectMode -> selectionController.toggleMultiSelectMode()
            is PockRefAction.ImportImages -> sceneController.importImages(action.uris)
            is PockRefAction.LoadScene -> sceneController.loadScene(action.uri)
            is PockRefAction.SaveScene -> sceneController.saveScene(action.uri)
            is PockRefAction.OpenRecentScene -> sceneController.loadScene(Uri.parse(action.uri))
            is PockRefAction.RemoveRecentScene -> removeRecentScene(action.uri)
            is PockRefAction.RenameRecentScene -> renameRecentScene(action.uri, action.newLabel)
            is PockRefAction.TogglePinRecentScene -> togglePinRecentScene(action.uri)
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
            is PockRefAction.SetAsCover -> _uiState.update { it.copy(coverImageId = action.imageId) }
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
 val maxStep =200f
 val dx = action.delta.x.coerceIn(-maxStep, maxStep)
 val dy = action.delta.y.coerceIn(-maxStep, maxStep)
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
 val newScale = action.scale.coerceIn(0.1f,10f)
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
            // V_NOW (2026-06-08 "global image lock" feature):
            // flip the [PockRefUiState.globalImageLock] flag
            // in-place AND mirror to prefs so a process kill /
            // fresh launch restores the user's last choice.
            // This is a view-mode toggle (not a scene change),
            // so it intentionally does NOT enter the undo
            // stack and does NOT clear redo — same policy as
            // [ToggleMultiSelectMode] / [ToggleBoxSelectMode].
            PockRefAction.ToggleGlobalImageLock -> {
                val newValue = !_uiState.value.globalImageLock
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
                        // V_NOW: auto-close box-select when locking
                        boxSelectMode = if (newValue) false else it.boxSelectMode,
                        boxSelectRect = if (newValue) null else it.boxSelectRect,
                        boxSelectPreviewIds = if (newValue) emptySet() else it.boxSelectPreviewIds,
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
 val nowFrozen = _uiState.value.lockCanvasFrozen
 val newFrozen = !nowFrozen
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
 // V_NOW: auto-close box-select when freezing
 boxSelectMode = if (newFrozen) false else it.boxSelectMode,
 boxSelectRect = if (newFrozen) null else it.boxSelectRect,
 boxSelectPreviewIds = if (newFrozen) emptySet() else it.boxSelectPreviewIds,
 )
 }
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
 _uiState.update {
 it.copy(
 exportCanvasMaxDim = action.maxDim?.coerceAtLeast(1),
 showExportCanvasDialog = false,
 )
 }
 }
 PockRefAction.CancelExportCanvas -> {
 _uiState.update { it.copy(showExportCanvasDialog = false) }
 }
 is PockRefAction.ConfirmExportCanvas -> {
                _uiState.update { it.copy(showExportCanvasDialog = false) }
                sceneController.exportCanvas(action.uri)
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
            is PockRefAction.PracticeTick -> practiceController.onTick(action.remainingMs)
            PockRefAction.PausePractice -> practiceController.pause()
            PockRefAction.ResumePractice -> practiceController.resume()
            PockRefAction.SkipRest -> practiceController.skipRest()
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
        // Persist as the DISABLED set: a missing entry = enabled
        // (the default), so we only store ids the user has
        // turned off. This keeps the prefs file small and means
        // "delete the prefs entry" = "reset to default = on".
        val currentDisabled = appPrefs.disabledGestureIds
        val newDisabled = if (enabled) currentDisabled - id else currentDisabled + id
        appPrefs.disabledGestureIds = newDisabled
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
            it.copy(
                angleSnapGuide = null,
            ).syncHistoryFlags(undoStackSize, redoStackSize)
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
                before.images != after.images
            ) {
                pushHistoryEntry(buildHistoryEntry(before, after, undoStack.size))
            }
        }
        // Clear the angle-snap guide on commit — the in-flight rotation
        // gesture that owned it is now over. Mirrors the snapHighlight
        // / snapGuide clearing in commitImageDrag.
        _uiState.update {
            it.copy(
                angleSnapGuide = null,
            ).normalizeSelection().syncHistoryFlags(undoStackSize, redoStackSize)
        }
    }

    internal fun mutateScene(transform: PockRefUiState.() -> PockRefUiState) {
        val before = _uiState.value
        val after = before.transform().normalizeSelection()
        if (before.sceneName != after.sceneName ||
            before.backgroundColor != after.backgroundColor ||
            before.images != after.images
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

    // ---------------------------------------------------------------------
    // Recent scenes persistence.
    // ---------------------------------------------------------------------

    internal fun rememberRecentScene(uri: Uri, label: String, source: String) {
        val filePath = queryDisplayNameSync(uri) ?: ""
        val fileSize = queryFileSizeSync(uri).coerceAtLeast(0L)
        val normalized = RecentSceneEntry(
            uri = uri.toString(),
            label = label,
            source = source,
            filePath = filePath,
            fileSize = fileSize,
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
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    // V_NOW (L2 polish 2026-06-10): rename a recent entry's
    // display label. Re-serializes the prefs entry so the
    // new label survives process death. Empty newLabel is
    // silently dropped.
    internal fun renameRecentScene(uri: String, newLabel: String) {
        if (newLabel.isBlank()) return
        if (_uiState.value.recentScenes.none { it.uri == uri }) return
        val updated = _uiState.value.recentScenes.map {
            if (it.uri == uri) it.copy(label = newLabel) else it
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
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    internal fun removeRecentScene(uri: String) {
        val updated = _uiState.value.recentScenes.filterNot { it.uri == uri }
        prefs.edit().putString(RECENT_SCENES_KEY, updated.joinToString("\n") {
            listOf(
                it.uri,
                it.label.replace("|", "/"),
                it.source,
                it.timestamp.toString(),
                it.filePath.replace("|", "/"),
                it.fileSize.toString(),
                if (it.pinned) "1" else "0",
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    private fun loadRecentScenes(): List<RecentSceneEntry> {
        return prefs.getString(RECENT_SCENES_KEY, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 3) RecentSceneEntry(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L,
                    parts.getOrElse(4) { "" },
                    parts.getOrElse(5) { "0" }.toLongOrNull() ?: 0L,
                    pinned = parts.getOrElse(6) { "0" } == "1",
                ) else null
            }
            ?.toList()
            .orEmpty()
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
            ).joinToString("|")
        }).apply()
        _uiState.update { it.copy(recentScenes = updated) }
    }

    // V_NOW (L2 empty-canvas revamp 2026-06-10): the new
    // single-launcher import path. Reads the URI's MIME
    // via ContentResolver and routes the URI list to
    // importImages for image MIME or to loadScene for the
    // PockRef scene MIME / octet-stream / unknown. Empty
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
        return contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
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
        try {
            for (candidate in sceneUriCandidates(uri)) {
                copySceneCandidate(candidate, tempFile, attempts)?.let { copiedBytes ->
                    if (copiedBytes > 0L) return CachedSceneFile(tempFile)
                }
            }
            android.util.Log.w(TAG, "All scene read attempts produced 0 bytes for $uri: ${attempts.joinToString("; ")}")
            error("读取到 0 字节；URI=$uri，提供方大小=${formatDiagnosticFileSize(expectedSize)}，已尝试 ${attempts.size} 条读取路径")
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

    private fun copySceneCandidate(uri: Uri, target: File, attempts: MutableList<String>): Long? {
        copyFromInputStream(uri, target, attempts)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        copyFromAssetFileDescriptor(uri, target, attempts)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        copyFromFileDescriptor(uri, target, attempts)?.let { copiedBytes ->
            if (copiedBytes > 0L) return copiedBytes
        }
        return null
    }

    private fun copyFromInputStream(uri: Uri, target: File, attempts: MutableList<String>): Long? {
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
                reportError("读取场景文件失败: ${error.message ?: error.javaClass.simpleName}", error)
                null
            },
        )
    }

    private fun copyFromAssetFileDescriptor(uri: Uri, target: File, attempts: MutableList<String>): Long? {
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
                reportError("读取场景文件失败: ${error.message ?: error.javaClass.simpleName}", error)
                null
            },
        )
    }

    private fun copyFromFileDescriptor(uri: Uri, target: File, attempts: MutableList<String>): Long? {
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
                reportError("读取场景文件失败: ${error.message ?: error.javaClass.simpleName}", error)
                null
            },
        )
    }

    internal fun takePersistablePermission(uri: Uri, read: Boolean, write: Boolean) {
        val flags = (if (read) android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (write) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags == 0) return
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun formatDiagnosticFileSize(size: Long): String {
        return if (size >= 0L) "$size bytes" else "unknown"
    }

    internal class CachedSceneFile(val file: File) : java.io.Closeable {
        override fun close() {
            file.delete()
        }
    }

    companion object {
        private const val TAG = "PockRefViewModel"
    }
}
