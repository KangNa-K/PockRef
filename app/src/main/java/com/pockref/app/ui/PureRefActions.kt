package com.pockref.app.ui

import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.AlignMode
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.CropAspect
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.CropRect
import com.pockref.app.model.PracticeConfig
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
    // V_NOW (2026-06-08 "global image lock" feature):
    // Flip the app-wide [PockRefUiState.globalImageLock]
    // flag, persisted via AppPreferences so a fresh
    // launch restores the user's last choice. The
    // flag is a VIEW-MODE switch (like
    // [ToggleMultiSelectMode] / [ToggleBoxSelectMode]),
    // not a scene change — toggling it does NOT enter
    // the undo stack, and the existing canvas state is
    // untouched. Selection-chrome is updated in-place
    // to reflect the new lock state (e.g. an image
    // that's currently selected and shown in the
    // single-select action panel will, on the next
    // image-tap event, be filtered out by
    // [PockRefUiState.isImageLocked]).
    data object ToggleGlobalImageLock : PockRefAction
 // V_NOW (2026-06-09 user spec "长按全局锁定时画布不可移动
 // 和旋转,同时按钮会变红"): long-press handler for the
 // global-lock toggle button. Toggles
 // [com.pockref.app.model.PockRefUiState.lockCanvasFrozen]
 // (the "hard mode" / canvas-frozen flag). Independent
 // of [ToggleGlobalImageLock] (which is the soft mode
 // toggle for the same button's onClick) — the user can
 // be in soft mode (images locked, canvas movable) or
 // hard mode (both, button red), and the actions are
 // independent toggles for the two flags. Persisted via
 // [com.pockref.app.data.AppPreferences.lockCanvasFrozen].
 data object ToggleLockCanvasFrozen : PockRefAction
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
 /**
 * V_NOW (2026-06-09 user spec "导出画布选项 — 无尺寸上限"):
 * persist the user's chosen longest-edge cap for the
 * **export-canvas** path. Dispatched by the
 * [com.pockref.app.ui.ExportCanvasDialogHost] when the
 * user picks one of the two options ("标准 (最长8192px)"
 * or "原始画质 (无上限)"). The reducer stores the value
 * on [com.pockref.app.model.PockRefUiState.exportCanvasMaxDim]
 * AND dismisses the canvas-export dialog so the
 * follow-up [PockRefAction.ConfirmExportCanvas] can
 * observe the new value in state when [com.pockref.app.ui.PureRefViewModelScene.exportCanvas]
 * reads it.
 *
 * [maxDim] is the cap in pixels; `null` means "no cap"
 * (canvas sizes to actual bbox regardless of size). The
 * reducer clamps to >=1 defensively so a malformed
 * dispatch can't produce a0-pixel canvas.
 */
  data class SetExportCanvasMaxDim(val maxDim: Int?) : PockRefAction
  // V_NOW (2026-06-16): the KDoc above (saying "1) stores the chosen
  // cap AND 2) dismisses the dialog") is OBSOLETE — the dismiss
  // path was removed in commit 1.3.0 (see
  // [com.pockref.app.ui.PureRefViewModel] reducer for
  // [SetExportCanvasMaxDim]). The dialog is now a two-step
  // "pick options → confirm" flow: chip clicks write the
  // value into state without dismissing, and the dialog's
  // "确认导出" button is the single point that dismisses +
  // launches the SAF picker. The pre-feature comment about
  // "dismiss + SAF picker flow" was a half-implementation
  // design (the dialog composable was never written) that
  // didn't match the now-shipped dialog shape.
  /**
  * V_NOW (2026-06-16 user spec "导出画布新增背景选项"):
  * the canvas-export dialog's "透明背景" / "画布背景色"
  * segmented button dispatches this. The reducer writes the
  * mode to [PockRefUiState.exportCanvasBackground] (default
  * [com.pockref.app.model.ExportCanvasBackgroundMode.TRANSPARENT]).
  * Like [SetExportCanvasMaxDim], this is per-export (not
  * persisted) — the user picks the mode every time they
  * open the dialog.
  */
  data class SetExportCanvasBackground(val mode: com.pockref.app.model.ExportCanvasBackgroundMode) : PockRefAction
  /**
  * V_NOW (2026-06-09): dismiss the canvas-export options
  * dialog without changing the cap. Dispatched by the
  * dialog's [androidx.compose.material3.AlertDialog]
  * `onDismissRequest` (system back, scrim tap). Idempotent —
  * setting the flag to false when it's already false is a
  * no-op. Does NOT alter [PockRefUiState.exportCanvasMaxDim]
  * — the next [PockRefAction.SetExportCanvasMaxDim] or the
  * default8192 fallback will be used.
  */
  data object CancelExportCanvas : PockRefAction
  /**
  * User picked a save URI from the SAF picker AND the dialog's
  * background mode is now in [PockRefUiState.exportCanvasBackground].
  * The [backgroundMode] is also passed explicitly so
  * [com.pockref.app.ui.PureRefViewModelScene.exportCanvas] can
  * pin to the value the user confirmed (instead of re-reading
  * state that may have been mutated between the dialog's
  * confirm-click and the SAF picker's return — the dialog and
  * the picker both go through Compose recomposition, so
  * passing the value explicitly avoids any re-entrancy on
  * the [exportCanvasBackground] field).
  */
  data class ConfirmExportCanvas(
      val uri: Uri,
      val backgroundMode: com.pockref.app.model.ExportCanvasBackgroundMode,
  ) : PockRefAction
    data class UpdateCanvasViewport(val width: Float, val height: Float) : PockRefAction
    data class ImportImages(val uris: List<Uri>) : PockRefAction
    /**
     * Import an image from the system clipboard. The ViewModel
     * reads [ClipboardManager] in an IO coroutine, decodes the
     * image bytes, and adds a new [SceneImage] to the scene.
     * Error cases ("剪贴板中没有可用的图片" /
     * "从剪贴板导入失败") are surfaced via the existing
     * [errorsFlow] snackbar. Distinct from [ImportImages]
     * (which takes pre-resolved content URIs from the SAF
     * picker) because the clipboard path needs its own
     * byte-level decode — the clipboard may hold a URI
     * string or inline image data that must be read and
     * decoded before a [SceneImage] can be constructed.
     */
    data object ImportFromClipboard : PockRefAction
    /**
     * V_NOW (L2 empty-canvas revamp 2026-06-10): the new
     * single "添加图片" launcher payload. Replaces the
     * previous [ImportImages] + [LoadScene] two-button
     * layout on the empty canvas. The reducer reads the
     * URI's MIME via ContentResolver and dispatches to
     * [ImportImages] for image MIME or [LoadScene] for
     * the PockRef scene MIME / octet-stream / unknown.
     */
    data class ImportMixed(val uris: List<Uri>) : PockRefAction
    data class LoadScene(val uri: Uri) : PockRefAction
    data class SaveScene(val uri: Uri) : PockRefAction
    data class OpenRecentScene(val uri: String) : PockRefAction
    data class RemoveRecentScene(val uri: String) : PockRefAction

    /**
     * V_NOW (L2 polish 2026-06-10): rename a recent file
     * entry's display label. Dispatched by the long-press
     * rename dialog in [com.pockref.app.ui.RecentFileGrid].
     * The reducer writes the new label back to the matching
     * RecentSceneEntry and re-serializes the prefs entry.
     * An empty [newLabel] is a no-op.
     */
    data class RenameRecentScene(val uri: String, val newLabel: String) : PockRefAction

    /**
     * V_NOW (L2 empty-canvas revamp 2026-06-10): toggle the
     * `pinned` flag on a [RecentSceneEntry]. Dispatched by
     * the swipe-to-dismiss "star" button in
     * [com.pockref.app.ui.RecentFileGrid]. The reducer
     * updates the in-memory list and re-serializes the
     * pinned bit as the 7th `|` field of the prefs entry.
     */
    data class TogglePinRecentScene(val uri: String) : PockRefAction

    // ── Start-page multi-select (V_NOW 2026-06-10) ─────────────────
    // The eight actions below drive the new
    // [com.pockref.app.ui.RecentMultiSelectTopBar] /
    // [com.pockref.app.ui.RecentMultiSelectBottomBar] chrome
    // that pairs with the existing
    // [com.pockref.app.model.PockRefUiState.recentMultiSelectMode]
    // / [recentSelection] state. Long-press on a recent tile
    // dispatches [EnterRecentMultiSelect] (with the long-pressed
    // URI auto-selected); subsequent taps on tiles dispatch
    // [ToggleRecentSelection]. The bulk pin / delete buttons
    // dispatch [BulkSetRecentPin] / [RequestBulkDeleteConfirm] →
    // [BulkDeleteRecentScenes].
    //
    // Rationale: the user spec "启动页长按文件 = 多选" sets up
    // long-press as the multi-select entry (NOT for showing
    // a context menu, which the v5/v6/v7 overlay did before
    // it was removed in 2026-06-10 per the
    // RecentFileGrid.kt V_NOW comment). Long-press → multi-
    // select → bulk action bar is the standard M3 / Gmail /
    // Google Photos / Files-by-Google pattern.

    /**
     * Enter multi-select mode on the start-page recent files
     * grid. The reducer flips
     * [com.pockref.app.model.PockRefUiState.recentMultiSelectMode]
     * to true and seeds [recentSelection] with [uri] (if
     * non-null) so the user has a visible target on entry.
     * Dispatched by the recent-tile long-press handler in
     * [com.pockref.app.ui.RecentFileGrid]. Idempotent —
     * dispatching when mode is already true is a no-op
     * (the selection set is left untouched in that case so
     * a stray re-dispatch doesn't drop the user's picks).
     */
    data class EnterRecentMultiSelect(val uri: String? = null) : PockRefAction

    /**
     * Exit multi-select mode. Clears [recentSelection] AND
     * flips [recentMultiSelectMode] to false. Dispatched by
     * the top app bar's close (X) button and the system back
     * gesture (handled in
     * [com.pockref.app.ui.PureRefApp.BackHandler]).
     */
    data object ExitRecentMultiSelect : PockRefAction

    /**
     * Add or remove [uri] from
     * [com.pockref.app.model.PockRefUiState.recentSelection].
     * No-op if multi-select mode is false. Dispatched by
     * the tile tap handler when
     * [com.pockref.app.model.PockRefUiState.recentMultiSelectMode]
     * is true.
     */
    data class ToggleRecentSelection(val uri: String) : PockRefAction

    /**
     * Select every [com.pockref.app.model.RecentSceneEntry]
     * in the current [recentScenes] list. Replaces the
     * existing selection set (Gmail-style absolute toggle).
     */
    data object SelectAllRecentScenes : PockRefAction

    /**
     * Clear [com.pockref.app.model.PockRefUiState.recentSelection]
     * WITHOUT exiting multi-select mode. Paired with
     * [SelectAllRecentScenes] on the bottom bar.
     */
    data object ClearRecentSelection : PockRefAction

    /**
     * Bulk-set the [com.pockref.app.model.RecentSceneEntry.pinned]
     * flag to [pinned] for every URI in
     * [com.pockref.app.model.PockRefUiState.recentSelection].
     * No-op if [recentSelection] is empty. The UI computes
     * the smart "all pinned → show 取消置顶" mapping and
     * dispatches the matching boolean.
     */
    data class BulkSetRecentPin(val pinned: Boolean) : PockRefAction

    /**
     * Open the bulk-delete confirmation dialog. Distinct
     * from [BulkDeleteRecentScenes] (the actual destructive
     * step) because the dialog has its own confirm/cancel
     * lifecycle.
     */
    data object RequestBulkDeleteConfirm : PockRefAction

    /**
     * Close the bulk-delete confirmation dialog WITHOUT
     * deleting. Dispatched by the dialog's cancel button,
     * `onDismissRequest` (system back, scrim tap), and
     * [BulkDeleteRecentScenes] handler (so the dialog
     * auto-closes after a successful delete).
     */
    data object DismissBulkDeleteConfirm : PockRefAction

    /**
     * Delete every [com.pockref.app.model.RecentSceneEntry]
     * whose URI is in [uris]. After the delete, the
     * selection set is filtered to keep only survivors. If
     * the delete leaves [recentScenes] empty, multi-select
     * mode is auto-exited. Dispatched by the bulk-delete
     * dialog's confirm button.
     */
    data class BulkDeleteRecentScenes(val uris: List<String>) : PockRefAction

    data object CloseScene : PockRefAction
    data class SelectImage(val imageId: String?) : PockRefAction
    data object Deselect : PockRefAction
    /**
     * V_NOW (P2-C3): one-shot "selection just changed" flash
     * marker. The two-finger-down hit-test handler dispatches
     * this in the SAME call as
     * [PockRefAction.SelectImage] (only when
     * `SelectImage(nonSelectedHit)` was the previous
     * dispatch — i.e. the selection was actually replaced
     * by a different image, not just a no-op re-tap of
     * the currently-selected one). The reducer writes
     * [com.pockref.app.model.PockRefUiState.selectionFlash]
     * = `SelectionFlash(imageId, now)` and the renderer
     * fades the matching image in/out over 200ms. See
     * the kdoc on [com.pockref.app.model.PockRefUiState.selectionFlash]
     * for the full rationale and the no-renderer-effect
     * guarantee.
     */
    data class FlashSelection(val imageId: String) : PockRefAction
    /**
     * V_NOW (SelectionBottomSheet "弹不出来" 修复): the
     * pointerInput dispatches this alongside any
     * "the user just did a meaningful image tap" event
     * (single-tap [CycleImageUnderPoint], double-tap
     * [FitImageToScreen], and the locked-image
     * branches of the same). The reducer bumps
     * [com.pockref.app.model.PockRefUiState.selectionSheetShowTick]
     * so the [SelectionBottomSheet]'s
     * [kotlinx.coroutines.LaunchedEffect] keyed on
     * the tick value re-fires and calls
     * `sheetState.show()` — re-raising the sheet
     * after a previous dismissal. See the kdoc on
     * [com.pockref.app.model.PockRefUiState.selectionSheetShowTick]
     * for the full M3 1.2 ModalBottomSheet
     * rememberSaveable rationale.
     */
    data object TickSelectionSheet : PockRefAction
    // V_NOW: programmatically dismiss the SelectionBottomSheet.
    // Used when a double-tap focus/unfocus gesture fires — the
    // user's intent is to zoom the image, not to interact with
    // the sheet. The first tap of the double-tap may have already
    // triggered [TickSelectionSheet] via [CycleImageUnderPoint],
    // so this action bumps [PockRefUiState.selectionSheetDismissTick]
    // to force the sheet to hide.
    data object DismissSelectionSheet : PockRefAction
    data class MoveImage(val imageId: String, val delta: Offset) : PockRefAction
    data class CycleImageUnderPoint(val point: Offset, val showSheet: Boolean = true) : PockRefAction
    data class BringImageToFront(val imageId: String) : PockRefAction
    /**
     * V_NOW (2026-06-10 user spec "单选 sheet 第一行按钮 = 水平翻转 /
     * 裁切 / 右旋 90° / 复原 / 设为封面"): mark an image id as
     * the scene's cover. `null` clears the cover (resets to the
     * "no cover" state, so the export-thumbnail path falls back
     * to the first image in the scene, same as before this
     * field existed). Same single-id semantics as the
     * [PockRefUiState.coverImageId] it writes — setting a new
     * id simply replaces the previous; there is no
     * "multiple covers" / "no cover" toggle in the spec.
     *
     * V_NOW (2026-06-10 revert repair): the action and its
     * [com.pockref.app.ui.PureRefViewModel] handler were
     * silently removed by commit `720078f` ("fix(L2 v6
     * revert): restore v5 EmptyCanvasState+ExtendedFAB; add
     * v6 coverImageId + Pin button visual state") along with
     * the "设为封面" entry in the single-select sheet row1.
     * The `PockRefUiState.coverImageId` field itself survived
     * the revert (it's a passive state field), so reading it
     * from the UI still worked, but dispatching the action
     * was a no-op and the [com.pockref.app.ui.PureRefApp]
     * SheetActionSpec had been replaced with the previous
     * "左旋 90°" entry. This re-add is the [934b80f] commit
     * re-applied.
     */
    data class SetAsCover(val imageId: String?) : PockRefAction
    data class PanCanvas(val delta: Offset) : PockRefAction
    data class ZoomCanvas(val scaleChange: Float, val focus: Offset) : PockRefAction
    /**
     * Set the canvas zoom to an absolute scale value.
     * Used by the minimap long-press zoom slider to set
     * the zoom level directly. The zoom anchors on
     * the canvas visual center (same as [ZoomCanvas]).
     */
    data class SetCanvasZoom(val scale: Float) : PockRefAction
    /**
     * Programmatic canvas-offset set: the minimap composable dispatches this
     * when the user clicks a point in the minimap thumbnail. The [offset] is
     * the new scene-space → screen-space translation that centers the
     * click point in the main view. The view-model reducer clamps the
     * resulting viewport against the scene bbox so the click can never
     * pan the canvas into empty space.
     */
    data class SetCanvasOffsetFromMinimap(val offset: Offset) : PockRefAction
    /**
     * Atomically set both canvas scale and offset in a
     * single state update. Used by the SelectionBottomSheet
     * auto-adjust feature so the canvas pan + zoom land on
     * the same frame (no 1-frame tear between scale and
     * offset when dispatched as two separate actions).
     */
    data class SetCanvasScaleAndOffset(val scale: Float, val offset: Offset) : PockRefAction
    data class TransformSelectedImage(
        val scaleChange: Float,
        val rotationChange: Float,
        val pan: Offset,
        // V_NOW (user spec "双指缩放选中图片"): when
        // [worldPivot] is non-null, it overrides the
        // default AABB-centre pivot for the scale
        // component. The reducer uses it as the
        // scale anchor instead of
        // [calculateTransformPivot], so the image
        // scales around the two-finger centroid
        // (in scene/world coordinates) rather than
        // its own centre. Rotation still uses the
        // AABB pivot for stability. Pass `null` to
        // keep the legacy AABB-centre behaviour
        // (used by one-shot [ScaleUp] / [ScaleDown]
        // toolbar buttons that have no focal point).
        val worldPivot: Offset? = null,
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
    /**
     * V_NOW (4-corner scale handles): ABSOLUTE scale setter
     * for the selected image. The reducer applies
     * [newScale] as the new value of
     * [com.pockref.app.model.SceneImage.scale] (clamped to
     * the same [0.1, 8.0] safety range the per-frame
     * `TransformSelectedImage` path uses) and moves the
     * image so the drag's anchor corner stays fixed in
     * world space. The pointer handler dispatches this
     * on every move event during a corner drag, deriving
     * [newScale] from the current screen-space diagonal
     * length divided by the down-time diagonal length,
     * multiplied by the image's scale at the down event.
     *
     * Distinct from `TransformSelectedImage` (which is a
     * RELATIVE multiplier that the existing two-finger
     * pinch path stacks frame-over-frame) because the
     * corner drag has a single fixed reference frame: the
     * down-time diagonal length. Using the relative form
     * here would let numerical drift accumulate over a
     * long drag; the absolute form is always a one-line
     * recompute against the down-time baseline.
     *
     * [worldPivot] is the drag's anchor corner in WORLD
     * (scene) coordinates. The reducer pins it in place
     * when applying the new scale, so the image "grows
     * away from" the anchor as the user pulls the move-
     * handle. [worldCenter] is the new image center in
     * world coordinates (also computed by the pointer
     * handler from the new screen-space midpoint of
     * (move-handle, anchor) and converted back to world).
     *
     * Affects the SINGLE selected image only (the corner
     * drag is a single-image gesture; multi-select
     * batch-scale lives behind the existing
     * [BatchScale] action, which is NOT dispatched from
     * the corner-drag path).
     */
    /**
     * V_NOW (4-corner scale handles, SINGLE+GROUP
     * unified path): per-AABB scale dispatch for
     * the union of every selected image. The
     * SINGLE case is a 1-element "group" — the
     * SINGLE down handler builds a [Rect] from
     * the image's rotated footprint AABB and
     * passes it as `initialImageAabbs[id]`. The
     * reducer applies
     *   newImageAabb = pivot + (initialAabb - pivot) * factor
     * for each member, so the rendered group AABB
     * (the union of per-image new AABBs) is
     * automatically pinned at [pivotX], [pivotY]
     * — which is the opposite (anchor) corner
     * from the one the user grabbed. This is the
     * same code path the multi-select uses, so
     * SINGLE and GROUP behave identically.
     */
    data class SetSelectedImagesScale(
        // V_NOW (2026-06-10 group corner-handle
        // anchor fix v2): replaced
        // `absoluteScales + worldCenter +
        //  initialGroupCenter` with a per-AABB
        // geometry payload. The reducer now
        // applies
        //   newImageAabb = pivot
        //     + (initialImageAabb - pivot) * factor
        //   newImageCenter = center(newImageAabb)
        //   newImageScale = initialScale * factor
        // for each member, so the union (group
        // AABB chrome) is automatically pinned at
        // [pivotX, pivotY] with the rest scaled by
        // [factor]. This works correctly for
        // rotated members (the old geometry was
        // only correct for axis-aligned members
        // whose center matched the group AABB
        // center).
        val factor: Float,
        val initialGroupAabb: Rect,
        val initialImageAabbs: Map<String, Rect>,
        val initialScales: Map<String, Float>,
        val pivotX: Float,
        val pivotY: Float,
    ) : PockRefAction
    data class BatchAlign(val mode: AlignMode) : PockRefAction
    data class BatchScale(val scaleFactor: Float) : PockRefAction
    data object BatchFlipHorizontal : PockRefAction
    data object ConvertToGrayscale : PockRefAction
    /**
     * V_NOW (2026-06-11 "旋转图片角点缩放" fix):
     * single-image corner-drag that uses the
     * CORRECT rotated geometry. The anchor
     * corner's world position is inverted
     * through the image's rotation transform to
     * derive the new center, so the anchor
     * stays pinned for ANY rotation angle.
     * [SetSelectedImagesScale] uses AABB
     * scaling which only pins the AABB corner,
     * not the image's actual rotated corner —
     * for rotated images the two diverge.
     */
    data class SetSingleImageCornerScale(
        val imageId: String,
        val newScale: Float,
        val newCenter: Offset,
    ) : PockRefAction
    data class LassoSelect(val rect: RectF) : PockRefAction
    data object ToggleBoxSelectMode : PockRefAction
 data object BeginBoxSelect : PockRefAction
 /**
 * V_NOW (CAD-style box select): start a box-select drag with
 * the given screen-space start point. The reducer stores the
 * start so the first [UpdateBoxSelectRect] dispatch can decide
 * the drag direction (WINDOW vs CROSSING) by comparing the
 * pointer's current X against this start. Replaces the older
 * [BeginBoxSelect] for the drag entry paths; the legacy
 * no-arg form is kept so any other dispatch site that doesn't
 * care about direction (e.g. tests) still works.
 */
 data class BeginBoxSelectAt(val start: Offset) : PockRefAction
 /**
 * V_NOW (CAD-style box select): update the live drag rectangle.
 * [rect] is the screen-space normalised rect (left ≤ right,
 * top ≤ bottom). [current] is the live pointer position — the
 * reducer compares [current].x against the stored
 * [PockRefUiState.boxSelectStart].x to decide whether this
 * drag is WINDOW (left → right) or CROSSING (right → left).
 * Direction is locked on the first call and read on every
 * subsequent one (so a mid-drag wobble doesn't flip the
 * preview).
 */
 data class UpdateBoxSelectRect(val rect: RectF, val current: Offset) : PockRefAction
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
     * V_NOW: minimap display mode. MINIMAP = the traditional
     * thumbnail overview; SLIDER = a persistent zoom slider.
     * Persisted via [com.pockref.app.data.AppPreferences.minimapDisplayMode].
     */
    data class SetMinimapDisplayMode(val mode: com.pockref.app.model.MinimapDisplayMode) : PockRefAction

    /**
     * V_NOW: slider orientation when display mode is SLIDER.
     * ADAPTIVE picks horizontal or vertical based on scene
     * aspect ratio. Persisted via
     * [com.pockref.app.data.AppPreferences.minimapSliderOrientation].
     */
    data class SetMinimapSliderOrientation(val orientation: com.pockref.app.model.MinimapSliderOrientation) : PockRefAction

    // V_NOW (2026-06-16 user feedback "去掉设置里的启动页
    // 显示导出日志的开关"): the [SetShowStartPageLogExportFab]
    // action that lived here (added in 880a345) is removed.
    // The FAB visibility no longer has a state field to write
    // to, no Settings toggle to drive, and no AppPreferences
    // key to persist to — see
    // [com.pockref.app.model.PockRefUiState] KDoc for the full
    // chain of removals (action + field + Settings row +
    // AppPreferences key + reducer branch).

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
     * V_NOW (2026-06-10 #2 import popup): open/close the
     * import-source picker dropdown under the top toolbar's
     * [+] import button. Independent of [SetSettingsSheetOpen]
     * — those are different popups anchored at different
     * chrome elements. The [PockRefUiState.importSourceMenuOpen]
     * mirror flips on dispatch; the dropdown's
     * onDismissRequest dispatches `false` to close.
     */
    data class SetImportSourceMenuOpen(val open: Boolean) : PockRefAction

    /**
     * User tapped the "拍照" (camera) option in the import
     * dropdown menu. The UI layer should launch the system
     * camera intent (ACTION_IMAGE_CAPTURE) with a temp URI
     * for the full-res output. On success the resulting URI
     * is fed to [ImportImages].
     */
    data object TakePhoto : PockRefAction

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

    // ── 速写练习 (V_NOW 2026-06-08) ─────────────────────────────────
    // The six practice variants below are dispatched by the
    // pre-flight config sheet (Start), the practice screen
    // ticker (Tick), and the practice screen chrome (Pause /
    // Resume / SkipRest / Exit). All six route through
    // [com.pockref.app.ui.PockRefViewModelPractice], which wraps
    // the pure [com.pockref.app.ui.PracticeReducer] and applies
    // the result to [com.pockref.app.model.PockRefUiState.practiceState].
    // See [com.pockref.app.model.PracticeState] for the schema
    // and [com.pockref.app.ui.PracticeReducer] for the transition
    // rules.

    /**
     * Begin a new practice session. [imageIds] is the ordered
     * list of [com.pockref.app.model.SceneImage.id]s the user
     * wants to draw (typically read from
     * [com.pockref.app.model.PockRefUiState.selectedImageIds]).
     * [config] is the user-edited [PracticeConfig] from the
     * pre-flight sheet (the controller persists it to
     * [com.pockref.app.data.AppPreferences.lastPracticeConfig]
     * for next session's defaults).
     *
     * Rejected (silently no-ops) by the controller if the list
     * is empty or a session is already in progress — the
     * controller reports an [com.pockref.app.ui.UiError] for
     * the empty case so the user knows why nothing happened.
     */
    data class StartPractice(
        val imageIds: List<String>,
        val config: PracticeConfig,
    ) : PockRefAction

    /**
     * Frame-by-frame countdown update. Dispatched by the
     * practice screen's `LaunchedEffect` (added in commit 3)
     * roughly every 16ms. The reducer treats [remainingMs] as
     * a "time left in the current phase" reading, not an
     * elapsed-time delta — this keeps the ticker drift-free
     * (the screen computes `remainingMs = phaseEndMs - now`
     * using [System.nanoTime] deltas, not `delay()` accumulation).
     * Values `<= 0L` trigger a phase transition.
     *
     * No-op when the session is not RUNNING (PAUSED / FINISHED
     * / IDLE all reject the event), so a stale frame from
     * before a pause can't accidentally advance the state.
     */
    data class PracticeTick(val remainingMs: Long) : PockRefAction

    /** Freeze the current phase's `remainingMs` and switch to
     *  PAUSED. No-op when the session is not RUNNING. */
    data object PausePractice : PockRefAction

    /** Un-freeze and switch back to RUNNING. No-op when the
     *  session is not PAUSED — the [PracticeState.remainingMs]
     *  value is preserved across the pause/resume pair, so
     *  the ticker resumes from exactly where it stopped. */
    data object ResumePractice : PockRefAction

    /** User chose to skip the current rest period. Advances
     *  to the next image (or FINISHED if this was the last
     *  rest). No-op when the session is not in the REST phase
     *  (defense-in-depth — the skip button only renders during
     *  REST, so a misclick dispatch would only happen via a
     *  debug hook). */
    data object SkipRest : PockRefAction

    /** Tear down the session. Sets
     *  [com.pockref.app.model.PockRefUiState.practiceState]
     *  back to `null` (not the [PracticeState.Idle] sentinel
     *  — the `null` shape is what the rest of the app reads
     *  via `state.practiceState != null` for "is the practice
     *  screen visible?"). No-op when the session is already
     *  IDLE. */
    data object ExitPractice : PockRefAction

    // ── Pre-flight config sheet (commit 2) ─────────────────────────
    // The two actions below toggle the
    // [com.pockref.app.model.PockRefUiState.practiceConfigSheetOpen]
    // flag that drives the pre-flight
    // [com.pockref.app.ui.PracticeConfigSheet]
    // [androidx.compose.material3.ModalBottomSheet]. The flag
    // is independent of the actual
    // [com.pockref.app.model.PockRefUiState.practiceState]
    // (the in-session reducer) — opening the config sheet
    // does NOT start a practice session; only the sheet's
    // "开始" button dispatches [StartPractice].

    /**
     * Show the pre-flight config sheet. Dispatched by the
     * "速写练习" entry in the
     * [com.pockref.app.ui.PureRefApp.SelectionBottomSheet]'s
     * row1. Idempotent: setting the flag to `true` when
     * it's already `true` is a no-op. The SelectionBottomSheet
     * itself auto-dismisses (its `LaunchedEffect` reads
     * [com.pockref.app.model.PockRefUiState.selectionSheetShowTick]
     * and re-shows on every user action — clicking a button
     * that takes the user to a different sheet counts as
     * "the user moved on" so the SelectionBottomSheet goes
     * away on its own).
     */
    data object OpenPracticeConfigSheet : PockRefAction

    /**
     * Hide the pre-flight config sheet. Dispatched by the
     * sheet's [androidx.compose.material3.ModalBottomSheet]
     * `onDismissRequest` (system back, scrim tap, swipe-down
     * on the handle). Idempotent.
     */
    data object DismissPracticeConfigSheet : PockRefAction

    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Lifecycle of a log-export click. The Composable dispatches
    // [RequestLogExport]; the ViewModel emits [LogExportStarted]
    // when it enters its IO coroutine, [LogExportFinished] on
    // successful text assembly, and [LogExportFailed] on any
    // thrown exception. The actual SAF call lives in the
    // Composable (it owns the launcher) and uses a callback
    // closure to receive the assembled text — the text never
    // enters StateFlow because it can be ~100KB.
    data object RequestLogExport : PockRefAction
    data object LogExportStarted : PockRefAction
    data object LogExportFinished : PockRefAction
    data class  LogExportFailed(val reason: String) : PockRefAction
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
    const val TWO_FINGER_TAP = "twoFingerTap"
    const val THREE_FINGER_TAP = "threeFingerTap"
    const val DOUBLE_TAP_EMPTY = "doubleTapEmpty"
    val ALL: List<String> = listOf(
        MOVE_IMAGE, PINCH_ZOOM, PINCH_ROTATE,
        TWO_FINGER_TAP, THREE_FINGER_TAP,
        DOUBLE_TAP_EMPTY,
    )

    /** Human-readable label for the settings panel. */
    fun label(id: String): String = when (id) {
        MOVE_IMAGE -> "单指拖动"
        PINCH_ZOOM -> "双指捏合缩放"
        PINCH_ROTATE -> "双指旋转"
        TWO_FINGER_TAP -> "双指点击(撤销)"
        THREE_FINGER_TAP -> "三指点击(重做)"
        DOUBLE_TAP_EMPTY -> "双击空白(适应窗口)"
        else -> id
    }
}

/**
 * Returns `true` if the gesture identified by [id] is enabled
 * in the current [gestureToggles] map. Missing keys default to
 * `true` (so a freshly deserialised state that lacks the field
 * still allows every gesture).
 */
fun isGestureEnabled(gestureToggles: Map<String, Boolean>, id: String): Boolean =
    gestureToggles[id] ?: true
