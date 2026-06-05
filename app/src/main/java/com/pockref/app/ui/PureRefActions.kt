package com.pockref.app.ui

import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.AlignMode
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.CropAspect
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.CropRect
import com.pockref.app.model.SettingsTab

sealed interface PockRefAction {
    data object ResetView : PockRefAction
    data object FitToScreen : PockRefAction
    data object Undo : PockRefAction
    data object Redo : PockRefAction
    data object ToggleMultiSelectMode : PockRefAction
    data object BeginSceneGesture : PockRefAction
    data object EndSceneGesture : PockRefAction
    data class BeginImageDrag(val imageId: String) : PockRefAction
    data class UpdateImageDrag(val delta: Offset) : PockRefAction
    data object CommitImageDrag : PockRefAction
    data object CancelImageDrag : PockRefAction
    data object RotateLeft : PockRefAction
    data object RotateRight : PockRefAction
    data object FlipHorizontal : PockRefAction
    data object FlipVertical : PockRefAction
    data object BringToFront : PockRefAction
    data object SendToBack : PockRefAction
    data object DeleteSelected : PockRefAction
    data object DuplicateSelected : PockRefAction
    data object ToggleLockSelected : PockRefAction
    data class ShowContextMenu(val anchorScreen: Offset, val targetImageIds: Set<String>) : PockRefAction
    data object DismissContextMenu : PockRefAction
    data object BeginRenameScene : PockRefAction
    data class ConfirmRenameScene(val newName: String) : PockRefAction
    data object CancelRenameScene : PockRefAction
    /** Show the export chooser dialog (PNG / JPG). */
    data object ExportSelected : PockRefAction
    /** User picked a format and a save URI — render + write the image. */
    data class ConfirmExport(val uri: Uri, val format: com.pockref.app.model.ExportFormat) : PockRefAction
    /**
     * User picked a format and a destination FOLDER (an OpenDocumentTree
     * tree URI). The VM iterates the currently selected images and writes
     * each one as `<imageName>.<ext>` inside the tree, letting
     * DocumentFile.createFile handle name collisions (it appends " (N)"
     * to duplicates on the Storage Access Framework side). Triggered from
     * the export chooser dialog when ≥2 images are selected.
     */
    data class ConfirmExportBatch(
        val treeUri: Uri,
        val format: com.pockref.app.model.ExportFormat,
    ) : PockRefAction
    data object CancelExport : PockRefAction
    data object ExportCanvas : PockRefAction
    data class ConfirmExportCanvas(val uri: Uri) : PockRefAction
    data class UpdateCanvasViewport(val width: Float, val height: Float) : PockRefAction
    data class ImportImages(val uris: List<Uri>) : PockRefAction
    data class LoadScene(val uri: Uri) : PockRefAction
    data class SaveScene(val uri: Uri) : PockRefAction
    data class OpenRecentScene(val uri: String) : PockRefAction
    data class RemoveRecentScene(val uri: String) : PockRefAction
    data object CloseScene : PockRefAction
    data class SelectImage(val imageId: String?) : PockRefAction
    data object Deselect : PockRefAction
    data class MoveImage(val imageId: String, val delta: Offset) : PockRefAction
    data class CycleImageUnderPoint(val point: Offset) : PockRefAction
    data class BringImageToFront(val imageId: String) : PockRefAction
    data class PanCanvas(val delta: Offset) : PockRefAction
    data class ZoomCanvas(val scaleChange: Float, val focus: Offset) : PockRefAction
    /**
     * Programmatic canvas-offset set: the minimap composable dispatches this
     * when the user clicks a point in the minimap thumbnail. The [offset] is
     * the new scene-space → screen-space translation that centers the
     * click point in the main view. The view-model reducer clamps the
     * resulting viewport against the scene bbox so the click can never
     * pan the canvas into empty space.
     */
    data class SetCanvasOffsetFromMinimap(val offset: Offset) : PockRefAction
    data class TransformSelectedImage(
        val scaleChange: Float,
        val rotationChange: Float,
        val pan: Offset,
    ) : PockRefAction
    data class SetColorAdjust(
        val brightness: Float,
        val contrast: Float,
        val saturation: Float,
    ) : PockRefAction
    data class SetSceneName(val value: String) : PockRefAction
    data object EnterCropMode : PockRefAction
    data object CancelCrop : PockRefAction
    data object CommitCrop : PockRefAction
    data class UpdateCropRect(val cropRect: CropRect) : PockRefAction
    data class SetCropAspect(val aspect: CropAspect) : PockRefAction
    data class SetCropRotation(val degrees: Float) : PockRefAction
    data class FitImageToScreen(val imageId: String) : PockRefAction
    data object ScaleUp : PockRefAction
    data object ScaleDown : PockRefAction
    data object ResetTransform : PockRefAction
    data class BatchAlign(val mode: AlignMode) : PockRefAction
    data class BatchScale(val scaleFactor: Float) : PockRefAction
    data object BatchFlipHorizontal : PockRefAction
    data object ConvertToGrayscale : PockRefAction
    data class LassoSelect(val rect: RectF) : PockRefAction
    data object ToggleBoxSelectMode : PockRefAction
    data object BeginBoxSelect : PockRefAction
    data class UpdateBoxSelectRect(val rect: RectF) : PockRefAction
    data object CommitBoxSelect : PockRefAction
    data object ExitBoxSelectMode : PockRefAction
    /**
     * Set the selection interaction mode to one of the three
     * exclusive values (SINGLE / MULTI / BOX). Driven by the
     * S Pen button binding: hold on touchdown → BOX, release
     * mid-stroke → MULTI, double-tap empty canvas → SINGLE.
     * Idempotent — setting the same mode the state is already
     * in is a no-op so the S Pen logic doesn't need to check
     * the current state before dispatching.
     */
    data class SetSelectionMode(val mode: com.pockref.app.model.SelectionMode) : PockRefAction
    data object SaveSceneDirect : PockRefAction
    data object UnsavedDialogSave : PockRefAction
    data object UnsavedDialogDiscard : PockRefAction
    data object UnsavedDialogCancel : PockRefAction
    data object ClearSaveAsTrigger : PockRefAction
    data object ExecuteAutoLayout : PockRefAction
    data object UndoLastOptimization : PockRefAction
    data object ConfirmBatchOptimize : PockRefAction
    data object SkipBatchOptimize : PockRefAction
    data object CancelBatchOptimize : PockRefAction
    data object PauseBatchOptimize : PockRefAction
    data object ResumeBatchOptimize : PockRefAction
    data object RetryFailedOptimization : PockRefAction
    data class SetBackgroundPreset(val preset: BackgroundPreset) : PockRefAction
    data class SetBackgroundColor(val color: Color) : PockRefAction
    data class ShowBackgroundPicker(val show: Boolean) : PockRefAction
    data class SetThemeMode(val mode: ThemeMode) : PockRefAction
    /**
     * Acknowledge the most recent UiError after the Snackbar has been displayed.
     * The UI fires this when the snackbar is dismissed so the VM can clear
     * its lastError slot and a new error can replace it.
     */
    data object ConsumeError : PockRefAction

    data class SetHover(val imageId: String?, val position: Offset?) : PockRefAction

    data object ClearHover : PockRefAction

    data class SetHoverPreview(val imageId: String?) : PockRefAction

    data class SetDragOver(val isOver: Boolean) : PockRefAction

    data class DropImages(val uris: List<Uri>, val dragPermission: android.view.DragAndDropPermissions? = null) : PockRefAction

    data class SetTopToolbarExpanded(val expanded: Boolean) : PockRefAction

    /**
     * V_NOW: minimap position update. Dispatched continuously
     * during a long-press drag. Coords are in screen pixels,
     * top-left origin. The reducer persists the value to
     * [com.pockref.app.data.AppPreferences.minimapPositionX] /
     * [minimapPositionY] on every dispatch so a force-kill
     * mid-drag still remembers the last position.
     */
    data class SetMinimapPosition(val x: Float, val y: Float) : PockRefAction

    /**
     * V_NOW: minimap dock-edge update. [MinimapDockEdge.NONE] =
     * free-positioned (the position is in raw screen coords).
     * TOP / BOTTOM / LEFT / RIGHT = the minimap is collapsed to
     * an edge icon at that side. Persisted across launches via
     * [com.pockref.app.data.AppPreferences.minimapDockEdge].
     */
    data class SetMinimapDockEdge(val edge: com.pockref.app.model.MinimapDockEdge) : PockRefAction

    /**
     * V_NOW: minimap collapse toggle. Independent of dock-edge
     * in principle, but in practice the auto-collapse timer
     * flips this to true 3s after the last interaction (matching
     * the top toolbar's contract) and the user's click on the
     * docked edge icon flips it back to false.
     */
    data class SetMinimapCollapsed(val collapsed: Boolean) : PockRefAction

    /**
     * V_NOW: transient — true while the user is long-pressing
     * AND dragging the minimap to a new position. Dispatched
     * by the long-press pointerInput at the 500ms threshold
     * (true) and by the drag-end detector on release (false).
     * Not persisted.
     */
    data class SetMinimapMoving(val moving: Boolean) : PockRefAction

    /**
     * V_NOW: pinch-to-zoom scale for the full minimap panel.
     * Dispatched continuously during a two-finger pinch.
     * Clamped to [0.5f, 2.0f] in the reducer so callers
     * (e.g. the pointerInput's [detectTransformGestures]
     * handler) can pass through unclamped values from the
     * gesture stream — the reducer is the single source of
     * truth for valid ranges. Persisted to
     * [com.pockref.app.data.AppPreferences.minimapZoomScale]
     * on every dispatch.
     */
    data class SetMinimapZoomScale(val scale: Float) : PockRefAction

    /**
     * V_NOW: window-blur collapse. Dispatched by
     * [com.pockref.app.MainActivity.onWindowFocusChanged] when
     * the Activity loses focus (status-bar pull-down, system
     * dialog over the app, recent-apps switcher, another app
     * launching in split-screen, etc.). Reducer unifies three
     * collapses into a single state-copy:
     *   - [PockRefUiState.topToolbarExpanded] = false
     *     (top bar Surface hidden via AnimatedVisibility)
     *   - [PockRefUiState.bottomRightPanelExpanded] = false
     *     (bottom-right 4-item list hidden, FAB still visible)
     *   - [PockRefUiState.menuBarsCollapsed] = true
     *     (the SelectionFloatingToolbar — which has no
     *     dedicated state field, derived from `selectedCount` —
     *     early-returns from this flag; the PockRefApp-level
     *     recent-files `menuExpanded` local state is also
     *     force-reset via a LaunchedEffect keyed on this field)
     *
     * The "unified" single action keeps the spec's
     * "互斥:失焦后,任意时刻至多 1 个菜单栏活跃" guarantee
     * in a single reducer step (no race between the three
     * individual toggles in the same frame). The opposite —
     * focusing the window — is intentionally NOT a separate
     * action: the spec says "聚焦时不主动恢复菜单栏", so the
     * collapsed state persists until the user manually re-opens
     * each menu.
     */
    data object SetMenuBarsCollapsed : PockRefAction

    /**
     * V_NOW: auto-hide / show the selection floating toolbar.
     * Dispatched by the 5-second inactivity timer in
     * [com.pockref.app.ui.PureRefApp.SelectionFloatingToolbar]
     * (hidden = true) and by image selection / toolbar
     * interaction (hidden = false). Mirrors the
     * [SetMenuBarsCollapsed] pattern but for the selection
     * toolbar's own idle timeout rather than window blur.
     */
    data class SetSelectionToolbarHidden(val hidden: Boolean) : PockRefAction

    /**
     * V_NOW (focus-restore follow-up): window-focus restore
     * — the inverse of [SetMenuBarsCollapsed]. Dispatched by
     * [com.pockref.app.MainActivity.onWindowFocusChanged] when
     * `hasFocus == true` (the activity window regains focus
     * — app switcher return, notification shade dismiss,
     * system dialog dismiss, etc.). The reducer writes the
     * [PockRefUiState.preBlurTopToolbarExpanded] /
     * [PockRefUiState.preBlurBottomRightPanelExpanded]
     * snapshot back to the live fields and clears
     * [PockRefUiState.menuBarsCollapsed] so the three
     * Composables re-evaluate their natural visibility
     * (SelectionFloatingToolbar: `selectedCount > 0`,
     * top toolbar: [PockRefUiState.topToolbarExpanded],
     * bottom-right: [PockRefUiState.bottomRightPanelExpanded]).
     *
     * Spec correction: the original blur-collapse spec said
     * "聚焦时不主动恢复" (don't restore on focus), but the
     * user spec **as actually implemented** is the inverse:
     * "窗口重新聚焦后,菜单栏应恢复为失焦前用户设定的
     * 状态,而非默认全收起或默认全展开". This action is
     * the implementation of the corrected spec. The
     * snapshot fields are written by the collapse reducer
     * (BEFORE the collapse copy) so a double-blur (e.g.
     * notification shade pulled twice without focusing)
     * preserves the original snapshot — the focus restore
     * still pulls the pre-first-blur values, not the
     * "blurred" values.
     *
     * Idempotent: if [PockRefUiState.menuBarsCollapsed] is
     * already `false` (initial Activity onCreate fires
     * `onWindowFocusChanged(true)` with a fresh uiState),
     * the reducer no-ops.
     */
    data object RestoreMenuBarsOnFocus : PockRefAction

    /**
     * Expand / collapse the bottom-right action panel. True = the
     * panel grows upward from the always-visible FAB to show the
     * full action list (box-select / settings / import / save).
     * False = the panel is collapsed to the 44dp FAB only.
     * Auto-collapses 3s after the last interaction, same as
     * the top toolbar — see [com.pockref.app.ui.PockRefApp.kt].
     */
    data class SetBottomRightPanelExpanded(val expanded: Boolean) : PockRefAction

    /**
     * Collapse the right-bottom action panel without touching any
     * other state. Used to dismiss the panel when the user starts
     * interacting elsewhere (canvas tap, image selection, top
     * toolbar click) — the panel gets out of the way so the new
     * focus area (selection toolbar / top toolbar menu / canvas
     * itself) has clean visual room. Idempotent: dispatching
     * when the panel is already collapsed is a no-op. Inverse
     * of [SetBottomRightPanelExpanded] but always-collapsed.
     */
    data object DismissBottomRightPanel : PockRefAction

    /**
     * Open / close the in-app settings sheet
     * ([com.pockref.app.ui.SettingsPanel]). Hoisted to UI state
     * so multiple chrome entry points — the top toolbar's gear
     * button (when restored) and the bottom-right action panel's
     * "Settings" ListItem — can drive the same sheet instance.
     * Closing is also dispatched by the sheet's
     * [androidx.compose.material3.ModalBottomSheet] scrim /
     * onDismissRequest.
     */
    data class SetSettingsSheetOpen(val open: Boolean) : PockRefAction

    /**
     * Switch to a different tab in the in-app settings sheet
     * (V_NOW: tabbed settings — 通用 / 外观 / 手势). Only
     * meaningful while [SetSettingsSheetOpen] is true; the
     * [com.pockref.app.ui.SettingsPanel] reads the value via
     * the shared [com.pockref.app.model.PockRefUiState] to
     * drive the [androidx.compose.material3.TabRow] /
     * [androidx.compose.material3.NavigationRail]
     * selection. Idempotent — setting the same tab is a no-op.
     */
    data class SetSettingsTab(val tab: SettingsTab) : PockRefAction

    /**
     * A drag just ended and the final position was produced by snap (the
     * drag delta was adjusted by the snap engine). The renderer uses this to
     * fire a brief highlight overlay so the user gets visual confirmation
     * that snap fired. No-op when the drag didn't snap.
     */
    data class SnapJustCommitted(val highlight: com.pockref.app.model.SnapHighlight) : PockRefAction

    /**
     * Clears the post-commit snap highlight. Fired by a timer in the
     * ViewModel ~350ms after SnapJustCommitted, or whenever the next
     * gesture starts.
     */
    data object ClearSnapHighlight : PockRefAction

    /**
     * Toggle the edge-to-edge snap feature on/off. The setting is
     * persisted via AppPreferences so the user's last choice survives
     * app restart.
     */
    data class SetEdgeSnapEnabled(val enabled: Boolean) : PockRefAction

    /**
     * Toggle the rotation-angle-snap feature (0°/90°/180°/270° lock)
     * on/off. Independent of [SetEdgeSnapEnabled] — the user can opt
     * out of one without affecting the other. Persisted via
     * AppPreferences.
     */
    data class SetRotationSnapEnabled(val enabled: Boolean) : PockRefAction

    /**
     * Toggle one gesture in [com.pockref.app.model.PockRefUiState.gestureToggles].
     * The view-model handler inverts the boolean into
     * [com.pockref.app.data.AppPreferences.disabledGestureIds] (a
     * StringSet, NOT a per-key Boolean, so a new [GestureId] added
     * later needs zero migration). [id] must be one of the const
     * values in [GestureId]; unknown ids are silently ignored at
     * the dispatch site so a future gesture rename doesn't crash
     * older saved state.
     */
    data class SetGestureEnabled(val id: String, val enabled: Boolean) : PockRefAction

    /**
     * V_NOW: S Pen side-button gesture mode (see
     * [com.pockref.app.spen.SpenMode]). User picks in the
     * settings panel. Persisted to
     * [com.pockref.app.data.AppPreferences.spenMode] so the
     * choice survives restart; the MainActivity's
     * [com.pockref.app.spen.SpenButtonDetector] reads it
     * once on construction (no hot-swap — see the comment
     * on the detector for why).
     */
    data class SetSpenMode(val mode: com.pockref.app.spen.SpenMode) : PockRefAction

    /**
     * Set the auto-save interval, in milliseconds. V_NOW.
     * Dispatched by the settings-panel slider on every onValueChange
     * (i.e. live as the user drags), so the preference is updated
     * many times per gesture. SharedPreferences handles that fine —
     * each write is a `apply()` and coalesces to a single fsync.
     *
     * The auto-save coroutine in
     * [com.pockref.app.ui.PockRefViewModel.startAutoSaveLoop] reads
     * [com.pockref.app.data.AppPreferences.autoSaveIntervalMs] on
     * every iteration of its `delay(intervalMs)` cycle, so the
     * new value takes effect on the next cycle (no need to restart
     * the loop or restart the activity).
     *
     * The view-model handler clamps [intervalMs] to the slider's
     * range (60_000..3_600_000) defensively even though the slider
     * can't currently produce out-of-range values — this protects
     * against a future code path that dispatches this action with
     * a wider value.
     */
    data class SetAutoSaveIntervalMs(val intervalMs: Long) : PockRefAction

    /**
     * V_NOW: master switch for the auto-save feature. Dispatched by
     * the Switch in the settings sheet's auto-save section. The
     * view-model handler writes
     * [com.pockref.app.data.AppPreferences.autoSaveEnabled] (so the
     * choice survives process death) AND updates
     * [com.pockref.app.model.PockRefUiState.autoSaveEnabled] (so
     * the Switch + Slider dim reactively in the same
     * recomposition).
     *
     * The auto-save coroutine reads the pref on every cycle
     * (same pattern as [SetAutoSaveIntervalMs]), so the
     * "user flips the switch off → next tick stops doing
     * file I/O" semantics come for free — no coroutine
     * restart, no signal passing. See
     * [com.pockref.app.data.AutoSaveManager.shouldAutoSave] for
     * the read site.
     */
    data class SetAutoSaveEnabled(val enabled: Boolean) : PockRefAction
}

/**
 * String constants for the per-gesture on/off switches surfaced
 * in the settings panel under "手势". Each constant is the key
 * used both in [com.pockref.app.model.PockRefUiState.gestureToggles]
 * (Map<String, Boolean>) and in the persisted
 * [com.pockref.app.data.AppPreferences.disabledGestureIds]
 * (Set<String>). The 7 entries correspond to the 7 multi-touch
 * / compound gestures that have a UI toggle — single-finger drag
 * is the simplest, three-finger tap is the most complex
 * (the earlier "three-finger double-tap → reset transform"
 * toggle was removed in 2026-06).
 */
object GestureId {
    const val MOVE_IMAGE = "moveImage"
    const val PINCH_ZOOM = "pinchZoom"
    const val PINCH_ROTATE = "pinchRotate"
    const val SLIDE_ZOOM = "slideZoom"
    const val TWO_FINGER_TAP = "twoFingerTap"
    const val THREE_FINGER_TAP = "threeFingerTap"
    const val DOUBLE_TAP_EMPTY = "doubleTapEmpty"
    val ALL: List<String> = listOf(
        MOVE_IMAGE, PINCH_ZOOM, PINCH_ROTATE, SLIDE_ZOOM,
        TWO_FINGER_TAP, THREE_FINGER_TAP,
        DOUBLE_TAP_EMPTY,
    )

    /** Human-readable label for the settings panel. The 7 keys
     *  above each map to one of these. */
    fun label(id: String): String = when (id) {
        MOVE_IMAGE -> "单指拖动"
        PINCH_ZOOM -> "双指捏合缩放"
        PINCH_ROTATE -> "双指旋转"
        SLIDE_ZOOM -> "双指竖直滑动缩放"
        TWO_FINGER_TAP -> "双指点击(撤销)"
        THREE_FINGER_TAP -> "三指点击(重做)"
        DOUBLE_TAP_EMPTY -> "双击空白(适应窗口)"
        else -> id
    }
}
