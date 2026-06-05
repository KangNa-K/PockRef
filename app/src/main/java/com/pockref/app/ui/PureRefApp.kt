package com.pockref.app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
// LockOpen import removed: V_NOW unified the lock button to
// always use the closed padlock with active=blue. The only
// previous LockOpen usage was the all-locked sub-mode at
// the old line 1312; now that branch also uses Lock with
// active=allLocked.
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomInMap
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.Settings
import com.pockref.app.model.AlignMode
import com.pockref.app.R
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.ColorAdjust
import com.pockref.app.ui.theme.Dimens
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneNaming
import com.pockref.app.model.SelectionMode
import com.pockref.app.model.Size2D
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ImageTransformMode { NONE, SLIDE, SCALE, ROTATE }
private const val SCALE_THRESHOLD = 12f
private const val ROTATE_THRESHOLD = 10f

// V_NOW: locked-image affordance colors, hoisted to file
// scope. The locked indicator (the red border + halo around
// a locked image) and the minimap's locked-stroke indicator
// (CanvasMinimap.MinimapColors.lockedStroke) both want the
// same semantic red, so they share a single pair of file-
// level constants instead of redeclaring in two places.
// Deliberately NOT theme-derived: "this image is
// protected" is a system-level affordance, not a UI
// decoration, and would lose its meaning if it shifted
// with the user's light/dark choice.
private val LockedStrokeColor: Color = Color(0xFFE57373)
private val LockedGlowColor: Color = Color(0x55E57373)

// How long a single touch must stay stationary before it counts as
// a long-press. Material default is 500ms; a touch artist would
// expect a bit less so the menu feels responsive, so we use 400ms.
// Must be long enough that a quick tap or a slow drag never
// accidentally fires the menu.
private const val LONG_PRESS_THRESHOLD_MS = 400L
// Max pixel travel within the long-press window that still counts
// as "stationary". A finger drift of 1–2 px is normal; anything
// more means the user is dragging, not holding. 8px mirrors the
// existing tap-tolerance constant used elsewhere in this file.
private const val LONG_PRESS_SLOP_PX = 8f
// Pixels of vertical centroid travel before a two-finger gesture
// commits to SLIDE mode (canvas zoom via two-finger vertical slide).
// Picked above the typical touch jitter floor so a stationary
// two-finger touch doesn't accidentally commit, and well below a
// deliberate stroke (a few tens of px is already a clear intent).
private const val SLIDE_THRESHOLD = 24f
// Per-pixel sensitivity for the slide-to-zoom mapping. A 100px upward
// slide = 1.10x (10% zoom in); a 1000px upward slide = 2.0x. Tuned so a
// casual swipe gives a meaningful but not jarring zoom step.
// V6 SLIDE sensitivity: 1f/2000f means 200px of vertical
// centroid movement → 0.1x step (factor 1±0.1). Reverse
// precision at this step is 0.01x (1.0 → 1.1 → 0.99),
// at the user spec's "<0.01x" threshold. If precision
// becomes a complaint, lower the divisor (e.g. 1f/3000f
// gives 0.0044x error) — at the cost of larger gestures
// for the same zoom amount.
private const val SLIDE_ZOOM_PER_PX = 1f / 2000f

// --- Render-perf tuning ----------------------------------------------------
// Extra padding (in screen pixels) added on every side of the world-space
// viewport. Used both for the draw-loop cull and for the LaunchedEffect
// decode-priority filter so fast pans don't reveal blank edges or
// thrash the decode queue.
private const val VIEWPORT_BUFFER_PX = 200f

// Pixel count above which we treat an image as "large" and downsample it
// to a viewport-aware target. ~4 megapixels.
private const val LARGE_IMAGE_PIXELS = 4_000_000

// Upper bound for the downsampled width of a large image.
private const val LARGE_IMAGE_MAX_DECODE_PX = 2048

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PockRefApp(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    imageLazyLoader: com.pockref.app.data.PurFileParser? = null,
    imageDecoder: com.pockref.app.data.SceneImageDecoder? = null,
    errorsFlow: SharedFlow<UiError>? = null,
) {
    val importLauncher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        onAction(PockRefAction.ImportImages(uris))
    }
    // Inline scene-name editor state. Lifted out of [TopToolbar]
    // so the tap-outside-cancel scrim below can read the same
    // boolean and dismiss the editor when the user taps on the
    // canvas. The state is otherwise identical to what was
    // previously local: a TextFieldValue for the in-progress
    // edit, a [FocusRequester] for auto-focus, and a session
    // start timestamp for the 200ms focus-settling race window.
    var isEditingSceneName by remember { mutableStateOf(false) }
    val editingSceneValue = remember { mutableStateOf(TextFieldValue("")) }
    val titleFocusRequester = remember { FocusRequester() }
    val editSessionStartMs = remember { mutableLongStateOf(0L) }
    // V6: top toolbar's recent-files menu state. Lifted out
    // of [TopToolbar] for the same reason as
    // [isEditingSceneName] — the parent Box renders a
    // tap-outside scrim that dismisses the menu. The menu is
    // now an inline child of the toolbar's chrome Surface
    // (no longer a separate Popup window), so we have to
    // dismiss it manually on outside taps; the scrim lives
    // at this scope because it needs to cover the rest of
    // the screen, which the toolbar's align(TopStart) can't.
    var menuExpanded by remember { mutableStateOf(false) }
    // V_NOW: window-blur snapshot + restore for the
    // recent-files dropdown. [menuExpanded] is a local
    // Composable state (not in PockRefUiState — there's
    // no other reader and no reason to push it to the VM
    // just for this), so the blur / focus reducers can't
    // reach it directly. The blur reducer DOES flip
    // [PockRefUiState.topToolbarExpanded] to false, which
    // hides the toolbar Surface and the dropdown along
    // with it — but the dropdown's `true` state would
    // leak through to the next time the user re-expands
    // the toolbar (because the local state is persisted
    // in the remember).
    //
    // V_NOW (focus-restore follow-up): the user spec is
    // "窗口重新聚焦后,菜单栏应恢复为失焦前用户设定的
    // 状态" — so on focus we must RESTORE [menuExpanded]
    // to its pre-blur value, not just leave it closed.
    // We use a second [remember] [savedMenuExpanded] to
    // hold the value-at-time-of-blur, then the same
    // LaunchedEffect handles both directions of the
    // [state.menuBarsCollapsed] transition:
    //
    //   false→true (blur):  save [menuExpanded] to
    //                       [savedMenuExpanded],
    //                       force [menuExpanded]=false
    //   true →false (focus): restore [menuExpanded] from
    //                        [savedMenuExpanded] (could
    //                        be true or false depending
    //                        on what the user had open)
    //
    // Initial composition: [state.menuBarsCollapsed] is
    // false → the effect runs once with the "focus" branch
    // and copies [savedMenuExpanded] (=false) to
    // [menuExpanded] (=false) — a no-op.
    //
    // Double-blur guard: a second blur while
    // [state.menuBarsCollapsed] is already true won't
    // re-fire this effect (LaunchedEffect keys on the
    // state value, and a true→true transition doesn't
    // re-launch), so [savedMenuExpanded] preserves its
    // original first-blur value. On focus restore we
    // pull the pre-first-blur value, not a stale
    // post-blur value.
    var savedMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.menuBarsCollapsed) {
        if (state.menuBarsCollapsed) {
            savedMenuExpanded = menuExpanded
            menuExpanded = false
        } else {
            menuExpanded = savedMenuExpanded
        }
    }
    // Back-press handling.
    //
    // V6: dismissed the recent-files menu (replaces the
    // Popup's built-in back-press handling, which is gone
    // now that the menu is a Surface child instead of a
    // separate window).
    //
    // V_NOW (file-close contract): extended the same handler
    // to also drive the "close current file" path. Pressing
    // the system back button (or the gesture / a hardware
    // back key) follows this priority chain:
    //
    //   1. The recent-files menu is open → close the menu
    //      first (it's a transient overlay on top of the
    //      toolbar; closing the scene underneath would be
    //      surprising).
    //   2. The unsaved-changes dialog is showing → the
    //      dialog's own [AlertDialog.onDismissRequest]
    //      handles back (fires [PockRefAction.UnsavedDialogCancel]).
    //      We disable our handler in that branch so we don't
    //      double-fire CloseScene on top of the dialog.
    //   3. A scene is "open" (has images OR can direct-save
    //      to a saved file) → fire [PockRefAction.CloseScene].
    //      The reducer's [onCloseScene] shows the 3-option
    //      "保存 / 不保存 / 取消" dialog when there are
    //      unsaved changes, so the user's "优先关闭文件
    //      而非整个软件" requirement is satisfied.
    //   4. None of the above (empty / main screen) → the
    //      handler is disabled, so back falls through to the
    //      system and the Activity finishes (Android: returns
    //      to the home screen, satisfying "无文件打开状态
    //      直接退出软件回到桌面").
    val sceneIsOpen = state.images.isNotEmpty() || state.canDirectSave
    val unsavedDialogShowing = state.showUnsavedChangesDialog
    BackHandler(
        enabled = menuExpanded || (sceneIsOpen && !unsavedDialogShowing),
    ) {
        when {
            menuExpanded -> menuExpanded = false
            else -> onAction(PockRefAction.CloseScene)
        }
    }
    val dismissSceneNameEdit: () -> Unit = {
        isEditingSceneName = false
        // No save: tap-outside is a cancel, not a commit.
    }
    // Hover-magnifier progress animation. Lifted out of
    // [PockRefCanvas] (where it was originally computed)
    // because V2's z-order fix moves the magnifier drawing
    // OUT of the main canvas — into a sibling Composable
    // that's the last child of the outer Box, so it sits
    // above the top toolbar / minimap / FAB. Both the
    // canvas (in case the in-canvas draw path is still
    // hit) and the overlay read the same state.
    val isHoveringImage = state.hoverPreviewImageId != null
    val hoverProgress by animateFloatAsState(
        targetValue = if (isHoveringImage) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = { t -> 1f - (1f - t) * (1f - t) }),
        label = "hover-magnifier",
    )
    // V2: bitmap cache lifted from [PockRefCanvas] so the
    // sibling-overlay hover magnifier can read the decoded
    // bitmaps without re-decoding. The cache is
    // mutableStateMapOf so writes from PockRefCanvas's
    // decode pipeline (LaunchedEffects) flow through to
    // the overlay's read on the next recomposition.
    val canvasBitmapCache = remember { mutableStateMapOf<String, Pair<Int, ImageBitmap>>() }
    // Cache-eviction DisposableEffect: when the set of
    // images shrinks (e.g. user deleted one), drop the
    // corresponding cache entries. Lifted with the cache
    // — reads [state.images] which is also in the
    // PockRefApp scope, so this works without any other
    // plumbing.
    DisposableEffect(state.images) {
        val currentIds = state.images.map { it.id }.toSet()
        canvasBitmapCache.keys.filterNot { it in currentIds }.forEach { key ->
            canvasBitmapCache.remove(key)
        }
        onDispose { }
    }
    // V_NOW: open-scene picker accepts the dedicated
    // .pur MIME plus the legacy `application/octet-stream`
    // (so files saved by older builds, which wrote
    // octet-stream, are still pickable). The `*/*` catch-all
    // is omitted intentionally — Android's OpenDocument
    // picker renders the `*/*` case as a flat list of every
    // file on the device, which is unusable for "open a
    // scene" UX. Users with .pur files tagged as the new
    // MIME get a clean "PockRef Scene" filter group in the
    // picker; users with old octet-stream .pur files get the
    // "binary blob" filter (which is where most file
    // managers put them anyway).
    val openSceneLauncher = rememberLauncherForActivityResult(
        OpenDocument(),
    ) { uri ->
        uri?.let { onAction(PockRefAction.LoadScene(it)) }
    }
    // V_NOW: save .pur with [com.pockref.app.MainActivity.PUR_SCENE_MIME]
    // ("application/x-pockref-scene") so the resulting file is
    // tagged with our dedicated MIME. This makes the system
    // show PockRef as a direct handler in the file manager's
    // "Open with" picker (and, after the user picks it once,
    // remembers it as the default for this MIME). The
    // `application/octet-stream` MIME used in the previous
    // build is still matchable by the
    // `application/octet-stream + .pur path` intent filter in
    // AndroidManifest.xml, so old .pur files saved by older
    // versions still open in PockRef via that filter.
    val saveSceneLauncher = rememberLauncherForActivityResult(
        CreateDocument(com.pockref.app.MainActivity.PUR_SCENE_MIME),
    ) { uri ->
        uri?.let { onAction(PockRefAction.SaveScene(it)) }
    }
    // Export launchers are declared inside [if
    // (state.showExportDialog)] (where they're used) so the
    // closure capture of function-scope locals is trivial. Two
    // separate launchers because the document picker is bound
    // to a MIME type.
    val exportPngLauncher = rememberLauncherForActivityResult(CreateDocument("image/png")) { uri ->
        uri?.let { onAction(PockRefAction.ConfirmExport(it, com.pockref.app.model.ExportFormat.PNG)) }
    }
    val exportJpgLauncher = rememberLauncherForActivityResult(CreateDocument("image/jpeg")) { uri ->
        uri?.let { onAction(PockRefAction.ConfirmExport(it, com.pockref.app.model.ExportFormat.JPEG)) }
    }
    // Canvas export: export the entire canvas as a single PNG
    val exportCanvasLauncher = rememberLauncherForActivityResult(CreateDocument("image/png")) { uri ->
        uri?.let { onAction(PockRefAction.ConfirmExportCanvas(it)) }
    }
    // Multi-export: pick a FOLDER, then write every selected image
    // as a sibling file inside it. The same format launches both
    // tree-picker branches; the dialog uses one of the two based on
    // how many images are selected.
    //
    // V2 fix: the OpenDocumentTree contract grants per-task write
    // permission, which is enough for [DocumentsContract.createDocument]
    // to work *while* the activity is alive — but a few OEM SAF
    // forks (and some cloud providers) reject createDocument with
    // "无法使用此文件夹" unless we ALSO call
    // [ContentResolver.takePersistableUriPermission]. The persistable
    // grant survives process death, so a background batch export
    // also resumes cleanly. SecurityException is swallowed because
    // some providers don't support persistable grants at all — the
    // per-task grant is still good for the lifetime of the process,
    // which is enough for an in-session batch export.
    val context = androidx.compose.ui.platform.LocalContext.current
    // Build an initial URI pointing at the Download folder so the
    // system folder picker opens there instead of the storage root.
    // On Samsung devices the root is read-only; opening Download
    // directly avoids the "无法在此文件夹创建文件" error.
    val exportBatchInitialUri = remember {
        // content://com.android.externalstorage.documents/root/primary%3ADownload
        Uri.parse(
            "content://com.android.externalstorage.documents/root/primary%3A" +
                Environment.DIRECTORY_DOWNLOADS,
        )
    }
    val exportBatchPngLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        // V3 diagnostic: log the launcher result so the
        // user can see in logcat whether the picker
        // returned a tree URI at all (and what authority
        // it was). The previous version silently dropped
        // `uri == null` (cancelled), which was fine for
        // UX but invisible when debugging. The user's
        // "无法使用此文件夹" report now also gets a
        // distinct log line so logcat can confirm
        // whether the picker returned null or returned
        // a tree URI that later failed createDocument.
        android.util.Log.i(
            "PockRefExport",
            "OpenDocumentTree callback: uri=$uri",
        )
        uri?.let { treeUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                android.util.Log.w(
                    "PockRefExport",
                    "takePersistableUriPermission failed: ${e.message}",
                )
                // non-fatal: per-task grant is still active
            }
            onAction(PockRefAction.ConfirmExportBatch(treeUri, com.pockref.app.model.ExportFormat.PNG))
        }
    }
    val exportBatchJpgLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        android.util.Log.i(
            "PockRefExport",
            "OpenDocumentTree callback (JPG): uri=$uri",
        )
        uri?.let { treeUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                android.util.Log.w(
                    "PockRefExport",
                    "takePersistableUriPermission (JPG) failed: ${e.message}",
                )
            }
            onAction(PockRefAction.ConfirmExportBatch(treeUri, com.pockref.app.model.ExportFormat.JPEG))
        }
    }
    // Top toolbar used to share these launchers with
    // [CanvasPanel] / [EmptyCanvasState] for the "Import" /
    // "Open" buttons. V_NOW: 顶栏只保留 标题 / 撤回 / 重做 /
    // 关闭 — Save / Import / 3-dots / BoxSelectToggle 都下线。
    // [onImport] + [onOpenScene] 仍然供 [CanvasPanel] →
    // [EmptyCanvasState] 使用(空画布上的"导入图片" /
    // "打开 pur" 入口),[onSaveScene] 整体下线(顶栏 Save
    // 按钮取消;保存动作只走 [LaunchedEffect] →
    // [saveSceneLauncher] 路径,即 unsaved-changes-dialog
    // 的 "Save As" 流程)。
    val onImport: () -> Unit = { importLauncher.launch(arrayOf("image/*")) }
    // V_NOW: open-scene picker accepts the dedicated .pur
    // MIME plus the legacy `application/octet-stream` (so
    // files saved by older builds, which wrote octet-stream,
    // are still pickable). The `*/*` catch-all is omitted
    // intentionally — Android's OpenDocument picker renders
    // the `*/*` case as a flat list of every file on the
    // device, which is unusable for "open a scene" UX. Users
    // with .pur files tagged as the new MIME get a clean
    // "PockRef Scene" filter group; users with old
    // octet-stream .pur files get the "binary blob" filter
    // (where most file managers put them anyway).
    val onOpenScene: () -> Unit = {
        openSceneLauncher.launch(
            arrayOf(
                com.pockref.app.MainActivity.PUR_SCENE_MIME,
                "application/octet-stream",
            ),
        )
    }
    // V_NOW: bottom-right action panel's "Save" item. Always goes
    // through the system save-as picker (matching the existing
    // unsaved-dialog-save path at line 560 below). When the user
    // is editing an existing .pur file they can hit Save, confirm
    // the same filename, and the picker will overwrite in place.
    val onSaveScene: () -> Unit = {
        val name = if (state.sceneName.endsWith(".pur")) state.sceneName else "${state.sceneName}.pur"
        saveSceneLauncher.launch(name)
    }

    LaunchedEffect(state.triggerSaveAsFromDialog) {
        if (state.triggerSaveAsFromDialog) {
            val name = if (state.sceneName.endsWith(".pur")) state.sceneName else "${state.sceneName}.pur"
            saveSceneLauncher.launch(name)
            onAction(PockRefAction.ClearSaveAsTrigger)
        }
    }

    // Snackbar surface for runtime errors emitted by the ViewModel. We use a
    // remember-durable host and pump the flow inside a LaunchedEffect that
    // also calls PockRefAction.ConsumeError on dismiss so the VM can clear
    // its lastError slot. If no errorsFlow is provided (e.g. preview / tests),
    // the host is still created but no events will arrive.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorsFlow) {
        if (errorsFlow == null) return@LaunchedEffect
        errorsFlow.collectLatest { error ->
            snackbarHostState.showSnackbar(
                message = error.message,
                duration = SnackbarDuration.Short,
            )
            onAction(PockRefAction.ConsumeError)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize(),
        ) {
            CanvasPanel(
                state = state,
                onAction = onAction,
                onImport = onImport,
                onOpenScene = onOpenScene,
                onSaveScene = onSaveScene,
                // [BottomRightActionPanel] is now rendered as a
                // separate sibling of this [BoxWithConstraints]
                // (below), so the chrome-mutex dismiss callback is
                // wired there directly. [CanvasPanel] no longer
                // needs the [onMenuDismiss] param.
                 imageLazyLoader = imageLazyLoader,
                 imageDecoder = imageDecoder,
                 modifier = Modifier
                     .fillMaxSize(),
                 hoverProgress = hoverProgress,
                 canvasBitmapCache = canvasBitmapCache,
             )

            val statusBarTopPx = WindowInsets.statusBars.getTop(LocalDensity.current)
            val statusBarTopDp = with(LocalDensity.current) { statusBarTopPx.toDp() }
            // Tap-outside-cancel scrim for the inline scene-name
            // editor. Drawn BEFORE [TopToolbar] below so the
            // TopToolbar sibling renders on top in the Box z-order
            // — otherwise the scrim would sit on top of the title
            // and the user's long-press / tap would never reach the
            // title (we hit this regression in commit bd17dba).
            // Same effect is achieved by the scrim's
            // [matchParentSize] + the toolbar's own content area;
            // taps on the toolbar still go to the toolbar.
            if (isEditingSceneName) {
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusManager.clearFocus()
                            dismissSceneNameEdit()
                        },
                )
            }
            // V6: tap-outside scrim for the top toolbar's
            // recent-files menu. Same pattern as the
            // scene-name editor scrim above — the menu is
            // now an inline Surface child (no longer a
            // separate Popup window), so we dismiss it
            // manually on taps below the toolbar. Drawn
            // BEFORE [TopToolbar] below so the toolbar
            // sibling renders on top in the Box z-order.
            // Without that ordering, the scrim would sit
            // on top of the title and the user's tap on
            // "PockRef Import" would never reach the
            // chevron's clickable.
            if (menuExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) {
                            menuExpanded = false
                        },
                )
            }
            // V_NOW (bug fix — "menu open 时点击 FAB 不收起 / 不执行自动排版"):
            // the [BottomRightActionPanel] used to live inside
            // [CanvasPanel] (which is the first child of this
            // [BoxWithConstraints]). The menu scrim above is a
            // full-screen clickable drawn AFTER [CanvasPanel], so it
            // covers the FAB's hit area and intercepts every FAB /
            // list-item tap — the FAB's [combinedClickable.onClick] /
            // [onLongClick] never fire, [onMenuDismiss] never runs
            // from the FAB side, and [PockRefAction.ExecuteAutoLayout]
            // / [SetBottomRightPanelExpanded] never dispatch. The
            // visible "menu closes" came from the scrim itself,
            // masking the broken click path.
            //
            // Fix: render [BottomRightActionPanel] here, AFTER the
            // scrims but BEFORE [TopToolbar], so it sits visually on
            // top of the menu scrim. The FAB + 4 list items become
            // the topmost hit target at the bottom-right corner, so
            // their [onMenuDismiss] + business-action paths both
            // run. The render condition is the same as before
            // (`state.images.isNotEmpty() && state.cropImageId ==
            // null`) — the panel's [Surface] uses
            // [BoxScope.align(Alignment.BottomEnd)] to anchor itself
            // to this [BoxWithConstraints]'s bottom-end, same visual
            // position as the old inside-CanvasPanel placement.
            if (state.images.isNotEmpty() && state.cropImageId == null) {
                BottomRightActionPanel(
                    state = state,
                    onAction = onAction,
                    onImport = onImport,
                    onOpenScene = onOpenScene,
                    onSaveScene = onSaveScene,
                    onExportCanvas = {
                        val sceneName = state.sceneName.ifBlank { "canvas" }
                        exportCanvasLauncher.launch("$sceneName.png")
                    },
                    // V_NOW: chrome-mutex — any click in the
                    // right-bottom panel (FAB tap / long-press
                    // or any of the 4 list items) collapses
                    // the top toolbar's 3-dots menu so it
                    // stops competing for visual room with
                    // whatever the user is now tapping.
                    // Idempotent setter (already-collapsed is
                    // a no-op).
                    onMenuDismiss = { menuExpanded = false },
                )
            }
            TopToolbar(
                state = state,
                // V_NOW: any click on the top toolbar
                // collapses the right-bottom action panel
                // (the panel's "you're done here" signal).
                // Wrapping at the call site (rather than in
                // every TopToolbar button handler) keeps the
                // chrome-mutex contract local to the parent:
                // the toolbar itself stays oblivious. The
                // panel's own items (Import, Save, etc.) are
                // NOT wrapped, so clicking inside the panel
                // doesn't dismiss itself. The
                // SelectionFloatingToolbar's click handlers
                // are also unwrapped, since by the time the
                // selection toolbar is up the right-bottom
                // panel is already collapsed.
                onAction = { action ->
                    onAction(action)
                    onAction(PockRefAction.DismissBottomRightPanel)
                },
                isEditingSceneName = isEditingSceneName,
                editingSceneValueVar = editingSceneValue,
                titleFocusRequester = titleFocusRequester,
                editSessionStartMsVar = editSessionStartMs,
                onEditModeChanged = { editing, value ->
                    isEditingSceneName = editing
                    editingSceneValue.value = value
                },
                onSceneNameCommitted = { newName ->
                    isEditingSceneName = false
                    if (newName.isNotBlank() && newName != state.sceneName) {
                        onAction(PockRefAction.ConfirmRenameScene(newName))
                    }
                },
                onEditDismiss = dismissSceneNameEdit,
                // V6: menu state hoisted so the parent
                // scrim can dismiss it. The toolbar itself
                // only opens/closes the menu and never owns
                // the state.
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                // V_NOW: parent container's max width, captured by
                // the [BoxWithConstraints] wrapper at the call site
                // (was [Box] before). The toolbar uses this to cap
                // its own width so the right edge can never sit
                // closer than [Dimens.CornerInset] (24dp) from the
                // window's right edge — even if the title + actions
                // would otherwise force the toolbar surface to grow
                // past the safe area or wrap to a second line. The
                // three corner-chrome elements (top toolbar,
                // bottom-left minimap, bottom-right action panel)
                // all share the 24dp CornerInset token so their
                // adjacent edges line up regardless of content size.
                parentMaxWidth = maxWidth,
                // V_NOW: bumped from [Dimens.EdgeInset] (16dp) to
                // [Dimens.CornerInset] (24dp) for both top + start
                // — the three corner chrome elements now share the
                // 24dp value so the toolbar's top + left sit on the
                // same grid as the minimap's left + bottom and the
                // bottom-right action panel's right + bottom. The
                // right edge is enforced by the [widthIn] cap inside
                // [TopToolbar] using [parentMaxWidth].
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = statusBarTopDp + Dimens.CornerInset,
                        start = Dimens.CornerInset,
                    ),
            )


            if (state.isBusy && !state.batchOptimizeState.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            if (state.showBatchOptimizeConfirm) {
                BatchOptimizeConfirmDialog(
                    optimizableCount = state.batchOptimizeState.total,
                    totalCount = state.images.size,
                    onConfirm = { onAction(PockRefAction.ConfirmBatchOptimize) },
                    onSkip = { onAction(PockRefAction.SkipBatchOptimize) },
                )
            }

            if (state.batchOptimizeState.isRunning) {
                BatchOptimizeProgressOverlay(
                    state = state.batchOptimizeState,
                    onPause = { onAction(PockRefAction.PauseBatchOptimize) },
                    onResume = { onAction(PockRefAction.ResumeBatchOptimize) },
                    onCancel = { onAction(PockRefAction.CancelBatchOptimize) },
                )
            }

            if (state.batchOptimizeState.status == com.pockref.app.model.BatchOptimizeStatus.COMPLETED && state.batchOptimizeState.hasFailures) {
                BatchOptimizeFailureDialog(
                    failedCount = state.batchOptimizeState.failed,
                    onRetry = { onAction(PockRefAction.RetryFailedOptimization) },
                    onDismiss = { },
                )
            }
            if (state.showExportDialog) {
                // Multi-export kicks in when the user has ≥2
                // selected images. Single-select keeps the old
                // per-image "give it a name" flow; multi-select
                // switches to the folder-pick flow (each image
                // gets its own file in the chosen folder).
                val multiCount = state.selectedImageIds.size
                val isMulti = multiCount >= 2
                ExportDialogHost(
                    selectedName = state.images.find { it.id == state.selectedImageId }?.name ?: "untitled",
                    isMulti = isMulti,
                    selectedCount = if (isMulti) multiCount else 1,
                    exportBatchInitialUri = exportBatchInitialUri,
                    exportPngLauncher = exportPngLauncher,
                    exportJpgLauncher = exportJpgLauncher,
                    exportBatchPngLauncher = exportBatchPngLauncher,
                    exportBatchJpgLauncher = exportBatchJpgLauncher,
                    onDismiss = { onAction(PockRefAction.CancelExport) },
                )
            }
        }
        // V4: hover-magnifier overlay. LAST child of the
        // OUTER Box (was previously inside [CanvasPanel]'s
        // inner Box, where it was the last child of that
        // Box but the WHOLE CanvasPanel is a sibling
        // drawn BEFORE the TopToolbar / minimap / FAB
        // — so anything inside CanvasPanel was always
        // under those siblings in the outer Box's
        // z-order, regardless of where it sat inside
        // CanvasPanel). The user's reported bug was
        // that the magnifier was under the top-left
        // toolbar specifically. Now the overlay is a
        // direct child of the outer Box and drawn after
        // every other chrome element, so the magnifier
        // image sits ON TOP of all of them. The overlay
        // is a no-op when there's no active hover
        // (progress == 0), so this layer doesn't sit
        // permanently over the chrome.
        // Wrapped in a Box because HoverMagnifierOverlay
        // is a BoxScope extension that uses matchParentSize
        // — we need a BoxScope context. The wrapping Box
        // has fillMaxSize so the overlay matches the
        // outer Box.
        Box(modifier = Modifier.fillMaxSize()) {
            HoverMagnifierOverlay(
                state = state,
                bitmapCache = canvasBitmapCache,
                progress = hoverProgress,
            )
        }
    }
}

@Composable
    private fun CanvasPanel(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onImport: () -> Unit,
    onOpenScene: () -> Unit,
    // V_NOW: bottom-right action panel's "Save" item needs a
    // callback that fires the system save-as picker. Defined
    // in [PockRefApp] (where [saveSceneLauncher] lives) and
    // threaded through to [BottomRightActionPanel].
    onSaveScene: () -> Unit,
    modifier: Modifier = Modifier,
    imageLazyLoader: com.pockref.app.data.PurFileParser? = null,
    imageDecoder: com.pockref.app.data.SceneImageDecoder? = null,
    // V2: hover-magnifier state lifted from
    // [PockRefCanvas] to the [PockRefApp] scope. See
    // the comments there for why. Both values are
    // threaded through this Box's children — the
    // [PockRefCanvas] (for the in-canvas draw path,
    // which is now empty; the actual draw happens in
    // the [HoverMagnifierOverlay] sibling) and the
    // overlay (which is the LAST child of this Box
    // so the magnifier image sits ABOVE the chrome).
    hoverProgress: Float = 0f,
    canvasBitmapCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Int, ImageBitmap>>,
) {

    Box(
        modifier
            .fillMaxSize()
    ) {
        PockRefCanvas(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onAction = onAction,
            imageLazyLoader = imageLazyLoader,
            imageDecoder = imageDecoder,
            hoverProgress = hoverProgress,
            bitmapCache = canvasBitmapCache,
        )
        if (state.images.isEmpty()) {
            EmptyCanvasState(
                state = state,
                onAction = onAction,
                onImport = onImport,
                onOpenScene = onOpenScene,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            if (state.cropImageId != null) {
                CropOverlay(
                    state = state,
                    onAction = onAction,
                )
            } else if (state.selectedImageIds.isNotEmpty()) {
                // Unified selection-mode floating toolbar.
                // Renders the single-select icon set when
                // exactly 1 image is selected, the multi-
                // select icon set (collapsed / expanded)
                // when 2+ are selected, and nothing when
                // nothing is selected. The kind is computed
                // inside the composable from
                // [PockRefUiState.selectedImageIds] — no
                // separate mode flag is needed and the
                // toolbar re-renders automatically as the
                // count crosses the 1 / 2 boundary.
                SelectionFloatingToolbar(
                    state = state,
                    onAction = onAction,
                )
            }
            // Bottom-left minimap. The composable itself
            // returns early when state.canvasScale ≤ 1, so we
            // don't gate it here.
            //
            // V_NOW (re-applied): the user wants minimap to
            // support free dragging (Android Picture-in-Picture
            // / floating-window style). Behavior:
            //   1. Long-press the panel → enter "moving" mode
            //      (500ms threshold, default Compose
            //      [ViewConfiguration.LongPressTimeout]).
            //   2. Drag → position is updated continuously and
            //      persisted to
            //      [com.pockref.app.data.AppPreferences.minimapPositionX]
            //      / [Y] on every frame.
            //   3. On release: if the panel is within 48dp of
            //      a screen edge, dock to that edge and collapse
            //      to the [CollapsedMinimapIcon] (the user
            //      wanted the "auto-hide to edge" behavior).
            //   4. Tap or hover the docked edge icon → expand
            //      back to the full panel at the last persisted
            //      position.
            //   5. 3s of no interaction on the full panel →
            //      collapse to the last dock edge (or BOTTOM
            //      if not yet docked), matching the top
            //      toolbar's auto-collapse contract.
            //
            // Coexistence with the existing canvas-pan-by-
            // minimap-drag: the existing [CanvasMinimap]
            // inner [pointerInput] uses [awaitEachGesture]
            // with [awaitFirstDown] + a manual move loop, so
            // a quick tap-and-drag fires its drag handler
            // (canvas pan). Our OUTER [pointerInput] used to
            // use [detectDragGesturesAfterLongPress] which
            // only fires after a 500ms hold WITHOUT movement
            // — that long-press handler was REMOVED by user
            // spec "minimap 去掉长按功能". The panel now has
            // zero touch interactivity of its own (it's a
            // static [Alignment.BottomStart] surface), so
            // every pointer event flows through to the inner
            // [CanvasMinimap] gesture untouched. The inner
            // canvas-pan is the only minimap-related drag
            // handler that exists.
            // interactions are mutually exclusive by
            // construction.
            //
            // V_NOW (mobile UX): hide the minimap during crop
            // mode. The crop overlay's bottom row (aspect ratio
            // chips + rotate ±90° + 取消 / 应用) sits at
            // BottomStart just like the minimap, so the
            // minimap's Box occludes the leftmost controls (on
            // narrow / portrait screens, the 取消 button is
            // directly underneath the minimap — the user
            // can't tap it without first moving the minimap,
            // which isn't a documented affordance). The
            // minimap is purely a navigation aid; during crop
            // the user is editing a single image and doesn't
            // need the global overview, so the right move is
            // to hide it and let the crop controls own the
            // bottom of the screen. The [CanvasMinimap]
            // composable already returns early when there are
            // no images; we add a parallel early-return for
            // crop mode here at the call site so the
            // composable's own API surface stays unchanged.
            if (state.cropImageId == null) {
                // V_NOW: wrap in BoxWithConstraints so the
                // drag-clamping math below can read the
                // parent size (the call site itself is outside
                // the outer BoxWithConstraints at line 710, so
                // we need our own scope). A full BoxWithConstraints
                // is more expensive than `onSizeChanged`, but we
                // need the size at composition time (before the
                // pointerInput's coroutine starts) so the default
                // position can be computed without an async wait.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    MinimapChrome(
                        state = state,
                        onAction = onAction,
                        parentWidth = constraints.maxWidth.toFloat(),
                        parentHeight = constraints.maxHeight.toFloat(),
                    )
                }
            }
            // Bottom-center selection status pill. Reads the
            // same [PockRefUiState.selectedImageIds] that the
            // floating toolbar above reads, so the two stay
            // in lock-step. Hidden entirely when nothing is
            // selected — the user shouldn't see a stale "0 张"
            // row. Sits at BottomCenter so it never collides
            // with the minimap (BottomStart) or the FAB
            // (BottomEnd, on screens where the user has
            // triggered auto-layout).
            // (底部 Pill 已删除:SelectionStatusBar 整体下线,只保留
            //  TopCenter 浮动 SelectionFloatingToolbar 作为唯一
            //  选中态 chrome。minimap 也不再因为 pill 占用底部
            //  居中空间而需要折叠到 24dp 图标 — 永远完整展示。)
        }
        if (state.showUnsavedChangesDialog) {
            UnsavedChangesDialog(
                isSaving = state.isSaving,
                onAction = onAction,
            )
        }
        // V_NOW (re-applied): the standalone RGB slider
        // background-picker dialog (8d9d74e had this as a
        // separate modal triggered by the inline palette
        // button) is removed. The HSV picker is now inline
        // inside [SettingsPanel] under section 4
        // ("背景颜色") and only shows when the user picks
        // the "Custom" preset chip. The
        // [showBackgroundPicker] state field is still in
        // the view-model (cheap idempotent action handler
        // is harmless if never set to true by the UI now).
        state.renameDialogValue?.let { initial ->
            RenameSceneDialog(
                initialValue = initial,
                onConfirm = { newName -> onAction(PockRefAction.ConfirmRenameScene(newName)) },
                onDismiss = { onAction(PockRefAction.CancelRenameScene) },
            )
        }
        state.contextMenu?.let { menu ->
            SelectionContextMenu(
                state = state,
                menu = menu,
                onAction = onAction,
                onDismiss = { onAction(PockRefAction.DismissContextMenu) },
            )
        }
        if (state.isDragOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = Color(0x8042A5F5),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .background(Color(0x1542A5F5)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDD111111),
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color(0xFF42A5F5),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "松开以导入图片",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Long-press context menu shown after the user holds a finger on a
 * selected image. Six icon-only buttons in the order requested by
 * the user: rotate left, rotate right, duplicate, bring to front,
 * send to back, toggle lock, delete. The menu surfaces the lock
 * state by showing a closed-vs-open padlock icon, and the button
 * row is wide enough to host all 7 entries without scrolling.
 *
 * Anchored at the long-press touch position. The exact pixel is
 * passed in via [menu].anchorScreen]; we center the menu horizontally
 * on it and push down enough that the row sits under the finger
 * rather than over it (finger occlusion of the button row is
 * worse than a small offset). Screen edges are clamped so the menu
 * never bleeds outside the device.
 *
 * Each action dispatches its [PockRefAction] and then dismisses
 * the menu via [onDismiss]. The dismissal is separate from the
 * action dispatch so the same handler can be reused for keyboard /
 * accessibility activations that don't auto-dismiss.
 */
@Composable
private fun BoxScope.SelectionContextMenu(
    state: PockRefUiState,
    menu: com.pockref.app.model.ContextMenuState,
    onAction: (PockRefAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportW = state.canvasViewportSize.width
    val viewportH = state.canvasViewportSize.height
    // 7 icons × 48dp each + 6×8dp gaps + 24dp horizontal padding =
    // ~408dp wide. Pre-compute it once so the edge-clamp below
    // uses a real number, not a Layout-intrinsic measurement
    // (which would only be available inside Modifier.layout).
    val menuWidthDp = 7 * 48 + 6 * 8 + 24
    val menuHeightDp = 48 + 16 // 48 button + 16 padding
    val menuWidthPx = with(density) { menuWidthDp.dp.toPx() }
    val menuHeightPx = with(density) { menuHeightDp.dp.toPx() }
    // Clamp inside the screen so the menu never bleeds off the
    // right or bottom edges. The down position itself might be
    // near an edge (long-pressing on an image at the right side of
    // a portrait canvas), and we'd rather shift the menu left/up
    // than let it clip.
    val centeredX = (menu.anchorScreen.x - menuWidthPx / 2f)
        .coerceIn(8f, (viewportW - menuWidthPx - 8f).coerceAtLeast(8f))
    val belowFingerY = (menu.anchorScreen.y + 24f)
        .coerceIn(8f, (viewportH - menuHeightPx - 8f).coerceAtLeast(8f))

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(
                x = with(density) { centeredX.toDp() },
                y = with(density) { belowFingerY.toDp() },
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xEE1A1A1A),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 1. rotate left
                ContextMenuIcon(
                    icon = Icons.AutoMirrored.Rounded.RotateLeft,
                    contentDescription = "左旋",
                ) {
                    onAction(PockRefAction.RotateLeft)
                    onDismiss()
                }
                // 2. rotate right
                ContextMenuIcon(
                    icon = Icons.AutoMirrored.Rounded.RotateRight,
                    contentDescription = "右旋",
                ) {
                    onAction(PockRefAction.RotateRight)
                    onDismiss()
                }
                // 3. duplicate
                ContextMenuIcon(
                    icon = Icons.Rounded.ContentCopy,
                    contentDescription = "复制",
                ) {
                    onAction(PockRefAction.DuplicateSelected)
                    onDismiss()
                }
                // 4. bring to front
                ContextMenuIcon(
                    icon = Icons.Rounded.FlipToFront,
                    contentDescription = "置顶",
                ) {
                    onAction(PockRefAction.BringToFront)
                    onDismiss()
                }
                // 5. send to back
                ContextMenuIcon(
                    icon = Icons.Rounded.FlipToBack,
                    contentDescription = "置底",
                ) {
                    onAction(PockRefAction.SendToBack)
                    onDismiss()
                }
                // 6. lock / unlock
                // V_NOW: padlock icon stays as the closed
                // [Icons.Rounded.Lock] regardless of state
                // (no more Lock↔LockOpen swap), matching the
                // floating selection toolbar's policy. The
                // click target's purpose ("toggle the lock
                // state of the long-pressed image") is
                // unchanged — only the resulting state
                // differs, and the surrounding UI (e.g. the
                // red locked-indicator on the canvas) carries
                // that affordance.
                ContextMenuIcon(
                    icon = Icons.Rounded.Lock,
                    contentDescription = "锁定",
                ) {
                    onAction(PockRefAction.ToggleLockSelected)
                    onDismiss()
                }
                // 7. delete
                ContextMenuIcon(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除",
                ) {
                    onAction(PockRefAction.DeleteSelected)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ContextMenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Unified selection-mode floating toolbar.
 *
 * The user's spec for this iteration collapses the previous
 * three-mode model (SINGLE / MULTI / BOX) into a single
 * "image selection" mode whose content is driven entirely
 * by the current selection size:
 *
 * - 0 selected  → toolbar is hidden (no Surface drawn at all)
 * - 1 selected  → single-image actions: Flip / Crop / Reset /
 *                 Grayscale / Export / Delete
 * - 2+ selected → multi-image actions (collapsed by default):
 *                 SwapHoriz / Grayscale / Delete / Expand;
 *                 expanded adds SwapVert / Lock / Reset /
 *                 Duplicate / 4-way Align / Export / Delete /
 *                 Collapse.
 *
 * The kind is computed inline from
 * [PockRefUiState.selectedImageIds] — no separate mode
 * flag, no action needed to "enter" multi-select. The user
 * just selects more images and the toolbar re-renders.
 */
@Composable
private fun BoxScope.SelectionFloatingToolbar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
) {
    val selectedCount = state.selectedImageIds.size
    if (selectedCount == 0) return
    // V_NOW: window-blur collapse. The other two chrome
    // surfaces (top toolbar / bottom-right action panel)
    // own dedicated boolean state fields that the blur
    // reducer flips to false, so their visibility tracks
    // [state.topToolbarExpanded] / [state.bottomRightPanelExpanded]
    // directly. The SelectionFloatingToolbar is special:
    // its visibility is *derived* from [selectedCount],
    // not a standalone state field, so it has nothing
    // for the blur reducer to flip. Instead we gate
    // composition on a dedicated flag, [menuBarsCollapsed],
    // that the blur reducer sets to true and that the
    // focus side deliberately does NOT clear (per the
    // "不主动恢复" spec — the user must re-select the
    // image to bring the toolbar back). The 3 modes
    // (single / multi / box) all share this early-return
    // because they all live inside this composable.
    if (state.menuBarsCollapsed) return
    // V_NOW: auto-hide after 5s of inactivity. Same
    // early-return pattern as [menuBarsCollapsed] above.
    // The user taps an image to bring the toolbar back
    // (selectImage resets selectionToolbarHidden = false).
    if (state.selectionToolbarHidden) return

    val density = LocalDensity.current
    val edgePaddingPx = with(density) { 8.dp.toPx() }
    val fallbackWidth = with(density) { 44.dp.toPx() }
    // Multi-select expanded panel is ~12 buttons tall; the
    // fallback needs to be tall enough that the offset
    // clamp picks a sensible initial position. Single-
    // select is 6 buttons, the single fallback of 280dp
    // worked for the original toolbar; for the merged
    // toolbar we use a midpoint that covers both shapes.
    val fallbackHeight = with(density) { 320.dp.toPx() }
    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val anchor = selectionToolbarAnchor(state) ?: return
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val viewportWidth = state.canvasViewportSize.width
    val viewportHeight = state.canvasViewportSize.height
    val targetOffset = clampSelectionToolbarOffset(
        anchor = anchor,
        gapPx = with(density) { 14.dp.toPx() },
        toolbarWidth = if (toolbarSize.width > 0) toolbarSize.width.toFloat() else fallbackWidth,
        toolbarHeight = if (toolbarSize.height > 0) toolbarSize.height.toFloat() else fallbackHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        topInset = statusBarTopPx,
        bottomInset = navBarBottomPx,
        edgePadding = edgePaddingPx,
    )
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = if (selectedCount >= 2) "multiselect-toolbar-offset" else "selection-toolbar-offset",
    )

    // V_NOW (single-select collapse/expand): the toolbar's
    // expanded state is now hoisted to the function scope
    // (was previously INSIDE the multi-select branch, so it
    // reset every time the count crossed the 1↔2 boundary).
    // The [key(selectedCount)] below forces a reset on every
    // count change, so the user starts collapsed in whichever
    // mode they just entered. Hoisting also lets the single-
    // select branch share the same expand/collapse machinery
    // (was a 6-button flat list before this commit, no
    // collapse affordance at all — user feedback was "the
    // previous single-select collapsed floating menu bar is
    // missing, restore it").
    // V_NOW: "每次点击图片都是默认显示折叠后的单选悬浮菜单栏"
    // (用户要求)。原 `remember(selectedCount) { mutableStateOf(false) }`
    // 只在 [selectedCount] 变时 reset — 单选切另一张图时
    // (selectedCount 保持 1,只 [selectedImageId] 变) 不会重置,
    // 用户从 expanded 视图点另一张图会看到 toolbar 保持展开,
    // 跟"折叠默认"语义不符。
    //
    // 改成: 单纯 `remember { false }` + `LaunchedEffect` 在
    // [selectedImageId] 或 [selectedCount] 任意一个变时 force-reset。
    // 两个 key 一起 cover:
    //   - 0→1 / 1→0 / 1→2 / 2→1 等 count 变化 (老逻辑就有)
    //   - 1→1 单选切另一张图 (selectedImageId 变) — 新加
    //   - 2→1 多选切回单选 (count 变)
    //   - 任意状态下 toggle 多选模式(此时通常 count 也不变
    //     因为 retainedPrimary 兜底,不会误 reset)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedImageId, selectedCount) {
        expanded = false
    }

    // V_NOW: 5-second auto-hide timer. When the toolbar
    // is visible and no button has been pressed for 5s,
    // dispatch [SetSelectionToolbarHidden(true)] so the
    // toolbar disappears. Tapping an image (selectImage)
    // resets selectionToolbarHidden = false, bringing it
    // back. Toolbar button presses reset this timer by
    // updating [lastToolbarInteractionMs] (see the
    // wrapper below). Uses the same polling pattern as
    // the top-toolbar's 3s auto-collapse to avoid
    // LaunchedEffect key churn.
    var lastToolbarInteractionMs by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(selectedCount) {
        if (selectedCount > 0) {
            lastToolbarInteractionMs = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - lastToolbarInteractionMs
                if (elapsed >= 5000L) {
                    onAction(PockRefAction.SetSelectionToolbarHidden(true))
                    break
                }
                val waitMs = (5000L - elapsed).coerceIn(100L, 1000L)
                delay(waitMs)
            }
        }
    }

    // Group-state derived fields for the multi-select
    // toggle buttons. Single-select (selectedCount == 1)
    // only reads the single image so these are unused
    // there; the call sites are inside the multi branch.
    val allGrayscale = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }
                ?.colorAdjust?.saturation?.let { it <= -0.9f } == true
        }
    val allLocked = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }?.locked == true
        }
    val allFlippedV = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }?.flipY == true
        }
    // V_NOW: horizontal-flip active-state check. Mirrors
    // [allFlippedV] but for the X axis — used by the
    // horizontal-flip button (now using [Icons.Rounded.Flip]
    // per user spec) to show its "every selected image is
    // already flipped" state in primary tint.
    val allFlippedH = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }?.flipX == true
        }
    // V_NOW: locked-panel gate. When EVERY selected image is
    // locked the toolbar swaps to a fixed 4-button panel (灰度 /
    // 解锁 / 导出图片 / 删除) with NO expand/collapse — crop / pinch /
    // rotate / flip / box-select / transform are all no-ops on
    // locked images, so exposing those controls would be
    // misleading. Grayscale + export + delete remain meaningful
    // (don't touch the image's pose, or are explicit destructive
    // acts), and unlock is the single most important control in
    // this mode — the whole reason the user is here.
    //
    // activeSelectedIds uses the legacy [selectedImageId]
    // fallback so a single-image selection (where
    // [selectedImageIds] is empty) is recognised as locked when
    // the lone [selectedImageId] points at a locked image.
    // Without this, single-locked selections stay on the
    // unlocked panel and the user can still tap "裁切" /
    // "左旋" etc. — the locked mode never kicks in.
    val activeSelectedIds = state.selectedImageIds.ifEmpty {
        state.selectedImageId?.let(::setOf).orEmpty()
    }
    val allSelectedLocked = activeSelectedIds.isNotEmpty() &&
        activeSelectedIds.all { id ->
            state.images.firstOrNull { it.id == id }?.locked == true
        }

    // V_NOW: wrap onAction so every toolbar button press
    // resets the 5s auto-hide timer. Without this, pressing
    // a button wouldn't refresh [lastToolbarInteractionMs]
    // and the toolbar could auto-hide mid-interaction.
    val toolbarOnAction: (PockRefAction) -> Unit = { action ->
        lastToolbarInteractionMs = System.currentTimeMillis()
        onAction(action)
    }

    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                IntOffset(
                    x = animatedOffset.x.roundToInt(),
                    y = animatedOffset.y.roundToInt(),
                )
            }
            .widthIn(min = 40.dp)
            .onSizeChanged { toolbarSize = it },
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
        tonalElevation = Dimens.ToolbarElevation,
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // V_NOW (user bug report): the locked panel ONLY
            // applies when the user has selected exactly ONE
            // image and that image is locked. When 2+ images
            // are selected and they're all locked, fall
            // through to the multi-select toolbar — the
            // multi-select toolbar's 锁定 button can flip the
            // whole batch's lock state (it's already
            // `active = allLocked` in the collapsed multi-
            // select view, so the user sees the same "all
            // locked, tap to unlock" affordance in either
            // place). The locked panel is reserved for the
            // "I'm looking at a single locked image, help me
            // unlock it" scenario — the 4-button no-expand
            // shape there makes that intent obvious.
            if (allSelectedLocked && selectedCount == 1) {
                // ===== V_NOW locked panel — exactly 4 buttons, no
                // expand / collapse, no rotation / crop / box-
                // select / transform controls (all no-ops on
                // locked images). Order per user spec: 水平反转 /
                // 灰度 / 解锁 / 删除. The red double-stroke
                // indicator on every locked image (see
                // [PockRefCanvas]'s draw loop) carries the
                // "this image is locked" affordance.
                // =====
                // 1. 水平反转 (horizontal flip) — locked images
                //    still respond to flip (it doesn't change
                //    pose in the locked sense, just mirroring
                //    the pixels). Pre-8d9d74e the toolbar used
                //    horizontal flip too; the user wants the
                //    Flip icon to match.
                ToolbarActionItem(
                    icon = Icons.Rounded.Flip,
                    contentDescription = "水平反转",
                    active = allFlippedH,
                ) { toolbarOnAction(PockRefAction.FlipHorizontal) }
                // 2. Grayscale (still meaningful on locked images;
                //    doesn't touch the image's pose).
                ToolbarActionItem(
                    icon = Icons.Rounded.Layers,
                    contentDescription = "灰度",
                    active = allGrayscale,
                    onClick = { toolbarOnAction(PockRefAction.ConvertToGrayscale) },
                )
                // 3. V_NOW: Lock — closed padlock, active=blue.
                //    The user spec is "无论激活没激活都是直显示
                //    🔒的图标,激活锁定时按钮就变蓝": the lock
                //    button's icon is the closed padlock in
                //    BOTH single-select and multi-select (and
                //    here in the all-locked sub-mode), and the
                //    state is conveyed by the active tint, not
                //    by swapping Lock↔LockOpen. The previous
                //    LockOpen in this branch was the one
                //    remaining inconsistency with the rest of
                //    the project. [allLocked] is true by
                //    construction in this branch (the if-guard
                //    above checks it), so [active = allLocked]
                //    always evaluates to true here and the
                //    button renders in primary/blue. Click
                //    toggles the lock (which, in this branch,
                //    means unlocking — but the icon/tint
                //    policy is now uniform across the project).
                ToolbarActionItem(
                    icon = Icons.Rounded.Lock,
                    contentDescription = "锁定",
                    active = allLocked,
                ) { toolbarOnAction(PockRefAction.ToggleLockSelected) }
                // 4. Delete — fixed slot, no colour hint, same
                //    chrome token as the rest of the locked
                //    panel. Trash icon + fixed slot position
                //    carry the "danger" semantics.
                ToolbarActionItem(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "删除",
                ) { toolbarOnAction(PockRefAction.DeleteSelected) }
            } else if (selectedCount == 1) {
                // V_NOW: single-select now mirrors the
                // 4+8 expandable panel the project used
                // pre-8d9d74e. The user's spec for the
                // expanded set is (in order):
                //   1. 镜像   (FlipVertical)
                //   2. 裁切   (Crop)
                //   3. 灰度   (Grayscale)
                //   4. 锁定   (Lock / Unlock)
                //   5. 向左旋转 90 度 (RotateLeft)
                //   6. 向右旋转 90 度 (RotateRight)
                //   7. 复原图片 (ResetTransform)
                //   8. 复制   (Duplicate)
                //   9. 导出图片 (Export)
                //  10. 删除   (Delete)
                //  11. 收起   (Collapse)
                // The collapsed set keeps the 4 most
                // common (mirror / crop / grayscale /
                // delete) + the Expand toggle, matching
                // the old 4+8 shape the user is
                // remembering. Same 200ms
                // expandVertically / shrinkVertically
                // tween as the multi branch so the two
                // panels feel like one motion when the
                // count crosses 1 ↔ 2.
                val singleImage = state.images
                    .firstOrNull { it.id == state.selectedImageId }
                val isGrayscale = singleImage
                    ?.colorAdjust?.saturation?.let { it <= -0.9f } == true
                val isLocked = singleImage?.locked == true
                AnimatedVisibility(
                    visible = !expanded,
                    enter = expandVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // V_NOW: 多选模式开关 — 单选 toolbar 第一个按钮。
                        // 单选模式下,点图就重置选择(只有当前图被选);
                        // 想累积选多张必须先打开这个 toggle,
                        // 之后每点一张图都会 add 到 selection 而不是 replace。
                        // Icon swap 跟之前的 BoxSelectToggleButton 一致:
                        // 开 = CheckBox 蓝,关 = CheckBoxOutlineBlank 灰。
                        // dispatch 走 [ToggleMultiSelectMode],reducer
                        // 会保留当前 selectedImageId / selectedImageIds
                        // (见 PockRefViewModelSelection.toggleMultiSelectMode
                        // 的 retainedPrimary 逻辑)。
                        ToolbarActionItem(
                            icon = if (state.multiSelectMode) {
                                Icons.Rounded.CheckBox
                            } else {
                                Icons.Rounded.CheckBoxOutlineBlank
                            },
                            contentDescription = "多选模式",
                            active = state.multiSelectMode,
                        ) { toolbarOnAction(PockRefAction.ToggleMultiSelectMode) }
                        ToolbarActionItem(
                            icon = Icons.Rounded.Flip,
                            contentDescription = "水平反转",
                            active = singleImage?.flipX == true,
                        ) { toolbarOnAction(PockRefAction.FlipHorizontal) }
                        ToolbarActionItem(Icons.Rounded.Crop, "裁剪") {
                            toolbarOnAction(PockRefAction.EnterCropMode)
                        }
                        ToolbarActionItem(
                            icon = Icons.Rounded.Layers,
                            contentDescription = "灰度",
                            active = isGrayscale,
                            onClick = { toolbarOnAction(PockRefAction.ConvertToGrayscale) },
                        )
                        ToolbarActionItem(Icons.Rounded.Delete, "删除") {
                            toolbarOnAction(PockRefAction.DeleteSelected)
                        }
                        ToolbarActionItem(
                            icon = Icons.Rounded.ExpandMore,
                            contentDescription = "展开",
                        ) { expanded = true }
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 0. V_NOW: 多选模式开关 — 跟 collapsed 同款,
                        // 提到 水平反转 之前(变成 expanded 的 #1)。
                        ToolbarActionItem(
                            icon = if (state.multiSelectMode) {
                                Icons.Rounded.CheckBox
                            } else {
                                Icons.Rounded.CheckBoxOutlineBlank
                            },
                            contentDescription = "多选模式",
                            active = state.multiSelectMode,
                        ) { toolbarOnAction(PockRefAction.ToggleMultiSelectMode) }
                        // 1. 水平反转 (horizontal flip)
                        ToolbarActionItem(
                            icon = Icons.Rounded.Flip,
                            contentDescription = "水平反转",
                            active = singleImage?.flipX == true,
                        ) { toolbarOnAction(PockRefAction.FlipHorizontal) }
                        // 2. 裁切
                        ToolbarActionItem(Icons.Rounded.Crop, "裁剪") {
                            toolbarOnAction(PockRefAction.EnterCropMode)
                        }
                        // 3. 灰度
                        ToolbarActionItem(
                            icon = Icons.Rounded.Layers,
                            contentDescription = "灰度",
                            active = isGrayscale,
                            onClick = { toolbarOnAction(PockRefAction.ConvertToGrayscale) },
                        )
                        // 4. 锁定 (lock toggle) — V_NOW: icon
                        //    stays as the closed padlock regardless
                        //    of state, the "active" tint (primary /
                        //    blue) carries the "this image is
                        //    currently locked" affordance. The
                        //    previous Lock↔LockOpen swap was
                        //    confusing because the click target's
                        //    purpose ("lock the image") is
                        //    unchanged — only the resulting state
                        //    differs. Now: padlock + blue = locked,
                        //    padlock + chrome = unlocked.
                        ToolbarActionItem(
                            icon = Icons.Rounded.Lock,
                            contentDescription = "锁定",
                            active = isLocked,
                        ) { toolbarOnAction(PockRefAction.ToggleLockSelected) }
                        // 5. 向左旋转 90 度
                        ToolbarActionItem(
                            icon = Icons.AutoMirrored.Rounded.RotateLeft,
                            contentDescription = "向左旋转 90 度",
                        ) { toolbarOnAction(PockRefAction.RotateLeft) }
                        // 6. 向右旋转 90 度
                        ToolbarActionItem(
                            icon = Icons.AutoMirrored.Rounded.RotateRight,
                            contentDescription = "向右旋转 90 度",
                        ) { toolbarOnAction(PockRefAction.RotateRight) }
                        // 7. 复原图片
                        ToolbarActionItem(Icons.Rounded.RestartAlt, "复原图片") {
                            toolbarOnAction(PockRefAction.ResetTransform)
                        }
                        // 8. 复制
                        ToolbarActionItem(Icons.Rounded.ContentCopy, "复制") {
                            toolbarOnAction(PockRefAction.DuplicateSelected)
                        }
                        // 9. 导出图片
                        ToolbarActionItem(
                            icon = Icons.Rounded.Save,
                            contentDescription = "导出图片",
                            active = false,
                            onClick = { toolbarOnAction(PockRefAction.ExportSelected) },
                        )
                        // 10. 删除
                        ToolbarActionItem(Icons.Rounded.Delete, "删除") {
                            toolbarOnAction(PockRefAction.DeleteSelected)
                        }
                        // 11. 收起
                        ToolbarActionItem(
                            icon = Icons.Rounded.ExpandLess,
                            contentDescription = "收起",
                        ) { expanded = false }
                    }
                }
            } else {
                // Multi-select (selectedCount >= 2): collapsed /
                // expanded panel via [expanded] local state.
                // Collapsed = 4 buttons (SwapHoriz / Grayscale /
                // Delete / Expand); expanded = 12 buttons (see
                // the [MultiSelectFloatingToolbar] history for
                // the full set + the swap-out / mirror /
                // align / lock etc. rationale).
                // V_NOW: [expanded] is now hoisted to the
                // function scope (above the if/else), keyed on
                // [selectedCount] so it auto-resets when the
                // count crosses the 1↔2 boundary — entering
                // single mode starts collapsed, same for
                // multi mode.
                AnimatedVisibility(
                    visible = !expanded,
                    enter = expandVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // V_NOW: 4 buttons per user spec —
                        // 水平反转 / 灰度 / 锁定 / 删除. Save +
                        // ExpandMore dropped from the collapsed
                        // state; lock added so users can flip
                        // the whole batch's lock state without
                        // expanding. Horizontal-flip icon uses
                        // the pre-8d9d74e [Icons.Rounded.Flip]
                        // (the "previous style" the user asked
                        // to bring back), not [SwapHoriz].
                        ToolbarActionItem(
                            icon = Icons.Rounded.Flip,
                            contentDescription = "水平反转",
                            active = allFlippedH,
                        ) { toolbarOnAction(PockRefAction.FlipHorizontal) }
                        ToolbarActionItem(
                            icon = Icons.Rounded.Layers,
                            contentDescription = "灰度",
                            active = allGrayscale,
                            onClick = { toolbarOnAction(PockRefAction.ConvertToGrayscale) },
                        )
                        // V_NOW: 锁定 — closed padlock always,
                        // active=blue when every selected is
                        // already locked. No more Lock↔LockOpen
                        // swap; the click target's purpose
                        // ("toggle the lock state of the
                        // selection") is unchanged.
                        ToolbarActionItem(
                            icon = Icons.Rounded.Lock,
                            contentDescription = "锁定",
                            active = allLocked,
                        ) { toolbarOnAction(PockRefAction.ToggleLockSelected) }
                        ToolbarActionItem(
                            icon = Icons.Rounded.Delete,
                            contentDescription = "删除",
                        ) { toolbarOnAction(PockRefAction.DeleteSelected) }
                        // V_NOW: 5th button — 展开. Tapping
                        // flips [expanded] to true, which
                        // triggers the AnimatedVisibility swap
                        // into the 12-button expanded panel
                        // (see below). Mirrors the single-
                        // select collapsed layout (which has
                        // had the 5th ExpandMore since 8c3244b
                        // restored it per user feedback).
                        ToolbarActionItem(
                            icon = Icons.Rounded.ExpandMore,
                            contentDescription = "展开",
                        ) { expanded = true }
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // V_NOW: horizontal-flip (was vertical).
                            // Same per-user-spec swap as the single-
                            // select and collapsed-multi toolbars —
                            // pre-8d9d74e project used horizontal
                            // flip in the toolbar, vertical flip was
                            // a post-merge accident.
                            ToolbarActionItem(
                                icon = Icons.Rounded.Flip,
                                contentDescription = "水平反转",
                                active = allFlippedH,
                                onClick = { toolbarOnAction(PockRefAction.FlipHorizontal) },
                            )
                            ToolbarActionItem(
                                icon = Icons.Rounded.Layers,
                                contentDescription = "灰度",
                                active = allGrayscale,
                                onClick = { toolbarOnAction(PockRefAction.ConvertToGrayscale) },
                            )
                            // V_NOW: 锁定 — closed padlock always,
                            // active=blue when every selected image
                            // is already locked. Lock↔LockOpen swap
                            // dropped per user spec (the click
                            // target's purpose is unchanged, the
                            // state is conveyed by the active tint).
                            ToolbarActionItem(
                                icon = Icons.Rounded.Lock,
                                contentDescription = "锁定",
                                active = allLocked,
                            ) { toolbarOnAction(PockRefAction.ToggleLockSelected) }
                            ToolbarActionItem(
                                Icons.Rounded.RestartAlt,
                                "复原",
                            ) { toolbarOnAction(PockRefAction.ResetTransform) }
                            ToolbarActionItem(
                                Icons.Rounded.ContentCopy,
                                "复制",
                            ) { toolbarOnAction(PockRefAction.DuplicateSelected) }
                            ToolbarActionItem(
                                Icons.Rounded.VerticalAlignTop,
                                "向上对齐",
                            ) { toolbarOnAction(PockRefAction.BatchAlign(AlignMode.ALIGN_TOP)) }
                            ToolbarActionItem(
                                Icons.Rounded.VerticalAlignBottom,
                                "向下对齐",
                            ) { toolbarOnAction(PockRefAction.BatchAlign(AlignMode.ALIGN_BOTTOM)) }
                            ToolbarActionItem(
                                Icons.Rounded.AlignHorizontalLeft,
                                "向左对齐",
                            ) { toolbarOnAction(PockRefAction.BatchAlign(AlignMode.ALIGN_LEFT)) }
                            ToolbarActionItem(
                                Icons.Rounded.AlignHorizontalRight,
                                "向右对齐",
                            ) { toolbarOnAction(PockRefAction.BatchAlign(AlignMode.ALIGN_RIGHT)) }
                            ToolbarActionItem(
                                icon = Icons.Rounded.Save,
                                contentDescription = "导出选中图片",
                                active = false,
                                onClick = { toolbarOnAction(PockRefAction.ExportSelected) },
                            )
                            ToolbarActionItem(
                                icon = Icons.Rounded.Delete,
                                contentDescription = "删除",
                            ) { toolbarOnAction(PockRefAction.DeleteSelected) }
                            ToolbarActionItem(
                                icon = Icons.Rounded.ExpandLess,
                                contentDescription = "收起",
                            ) { expanded = false }
                        }
                    }
            }
        }
    }
}


@Composable
private fun ToolbarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = false,
    // V11: caller-supplied tint wins (used for the red delete
    // button). When null, falls back to the active/inactive
    // primary-vs-onSurfaceVariant logic below. Default
    // `null` keeps every existing call site source-
    // compatible — only the multi-select toolbar's delete
    // buttons pass an explicit red tint.
    //
    // V12a: applied via [IconButton]'s `colors.contentColor`
    // instead of the child [Icon]'s `tint` param. The
    // long-press context menu's [ContextMenuIcon] uses
    // `Icon(tint = ...)` and renders red correctly; the
    // multi-select toolbar's [ToolbarActionItem] was using
    // the same `Icon(tint = ...)` shape but the user's
    // device showed black — likely a tint-render-order
    // quirk specific to [IconButton]'s [LocalContentColor]
    // provider (which wraps the Icon in a
    // [CompositionLocalProvider] that wins over the Icon's
    // own `tint` in some build of the M3 ripple). Setting
    // the color at the [IconButton] level — which is what
    // M3 actually applies as the Icon's default `tint`
    // via the local — sidesteps the override entirely.
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val resolvedTint = tint ?: if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize(),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = resolvedTint,
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BoxScope.CropOverlay(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
) {
    val cropImage = state.images.find { it.id == state.cropImageId } ?: return
    val viewport = state.canvasViewportSize
    if (viewport.width <= 0f || viewport.height <= 0f) return

    val density = LocalDensity.current
    val touchPadding = with(density) { 28.dp.toPx() }
    val handleRadius = with(density) { 10.dp.toPx() }

    val currentCropImage by rememberUpdatedState(cropImage)
    val currentCanvasScale by rememberUpdatedState(state.canvasScale)
    val currentCanvasOffset by rememberUpdatedState(state.canvasOffset)
    val currentViewport by rememberUpdatedState(viewport)
    val currentCropRect by rememberUpdatedState(state.cropRect)
    val currentCropAspect by rememberUpdatedState(state.cropAspect)
    val currentOnAction by rememberUpdatedState(onAction)

    var dragEdge by remember { mutableStateOf<String?>(null) }

    fun calcScreenCoords(): FloatArray {
        val img = currentCropImage
        val vp = currentViewport
        val scale = currentCanvasScale
        val offset = currentCanvasOffset
        val cr = currentCropRect
        val cxOff = vp.width / 2f + offset.x
        val cyOff = vp.height / 2f + offset.y
        val imgW = img.displaySize.width * img.scale * scale
        val imgH = img.displaySize.height * img.scale * scale
        val imgL = cxOff + img.center.x * scale - imgW / 2f
        val imgT = cyOff + img.center.y * scale - imgH / 2f
        return floatArrayOf(
            imgL + cr.left * imgW,
            imgT + cr.top * imgH,
            imgL + cr.right * imgW,
            imgT + cr.bottom * imgH,
            imgW,
            imgH,
        )
    }

    fun normAspect(aspect: CropAspect, img: com.pockref.app.model.SceneImage): Float {
        return aspect.ratioW / aspect.ratioH * (img.intrinsicHeight.toFloat() / img.intrinsicWidth.toFloat())
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val c = calcScreenCoords()
                        val cL = c[0]; val cT = c[1]; val cR = c[2]; val cB = c[3]
                        val tp = touchPadding
                        val inLeft = start.x in (cL - tp)..(cL + tp)
                        val inRight = start.x in (cR - tp)..(cR + tp)
                        val inTop = start.y in (cT - tp)..(cT + tp)
                        val inBottom = start.y in (cB - tp)..(cB + tp)
                        val inside = start.x > cL + tp && start.x < cR - tp &&
                                start.y > cT + tp && start.y < cB - tp
                        val nearBox = start.x > cL - tp && start.x < cR + tp &&
                                start.y > cT - tp && start.y < cB + tp
                        val isFree = currentCropAspect == CropAspect.FREE

                        dragEdge = when {
                            inside -> "move"
                            inLeft && inTop -> if (isFree) "lt" else "lt_lock"
                            inRight && inTop -> if (isFree) "rt" else "rt_lock"
                            inLeft && inBottom -> if (isFree) "lb" else "lb_lock"
                            inRight && inBottom -> if (isFree) "rb" else "rb_lock"
                            inLeft -> if (isFree) "l" else "l_lock"
                            inRight -> if (isFree) "r" else "r_lock"
                            inTop -> if (isFree) "t" else "t_lock"
                            inBottom -> if (isFree) "b" else "b_lock"
                            nearBox -> "move"
                            else -> null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val edge = dragEdge ?: return@detectDragGestures
                        val coords = calcScreenCoords()
                        val imgW = coords[4]
                        val imgH = coords[5]
                        val dx = dragAmount.x / imgW
                        val dy = dragAmount.y / imgH
                        val cr = currentCropRect
                        val minSize = 0.08f
                        val newRect = when (edge) {
                            "move" -> {
                                val nw = cr.cropWidth
                                val nh = cr.cropHeight
                                val nl = (cr.left + dx).coerceIn(0f, 1f - nw)
                                val nt = (cr.top + dy).coerceIn(0f, 1f - nh)
                                cr.copy(left = nl, top = nt, right = nl + nw, bottom = nt + nh)
                            }
                            "lt" -> cr.copy(
                                left = (cr.left + dx).coerceIn(0f, cr.right - minSize),
                                top = (cr.top + dy).coerceIn(0f, cr.bottom - minSize),
                            )
                            "rt" -> cr.copy(
                                right = (cr.right + dx).coerceIn(cr.left + minSize, 1f),
                                top = (cr.top + dy).coerceIn(0f, cr.bottom - minSize),
                            )
                            "lb" -> cr.copy(
                                left = (cr.left + dx).coerceIn(0f, cr.right - minSize),
                                bottom = (cr.bottom + dy).coerceIn(cr.top + minSize, 1f),
                            )
                            "rb" -> cr.copy(
                                right = (cr.right + dx).coerceIn(cr.left + minSize, 1f),
                                bottom = (cr.bottom + dy).coerceIn(cr.top + minSize, 1f),
                            )
                            "l" -> cr.copy(left = (cr.left + dx).coerceIn(0f, cr.right - minSize))
                            "r" -> cr.copy(right = (cr.right + dx).coerceIn(cr.left + minSize, 1f))
                            "t" -> cr.copy(top = (cr.top + dy).coerceIn(0f, cr.bottom - minSize))
                            "b" -> cr.copy(bottom = (cr.bottom + dy).coerceIn(cr.top + minSize, 1f))
                            "rb_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left; val aT = cr.top
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + dx) - aL).coerceIn(minSize, maxW)
                                var nh = nw / na
                                if (aT + nh > 1f) { nh = 1f - aT; nw = nh * na }
                                cr.copy(right = aL + nw, bottom = aT + nh)
                            }
                            "lt_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aR = cr.right; val aB = cr.bottom
                                val maxW = aR.coerceAtLeast(minSize)
                                var nw = (aR - (cr.left + dx)).coerceIn(minSize, maxW)
                                var nh = nw / na
                                var nT = aB - nh
                                if (nT < 0f) { nh = aB; nw = nh * na; nT = 0f }
                                cr.copy(left = aR - nw, top = nT)
                            }
                            "rt_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left; val aB = cr.bottom
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + dx) - aL).coerceIn(minSize, maxW)
                                var nh = nw / na
                                var nT = aB - nh
                                if (nT < 0f) { nh = aB; nw = nh * na; nT = 0f }
                                cr.copy(right = aL + nw, top = nT)
                            }
                            "lb_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aR = cr.right; val aT = cr.top
                                val maxW = aR.coerceAtLeast(minSize)
                                var nw = (aR - (cr.left + dx)).coerceIn(minSize, maxW)
                                var nh = nw / na
                                if (aT + nh > 1f) { nh = 1f - aT; nw = nh * na }
                                cr.copy(left = aR - nw, bottom = aT + nh)
                            }
                            "r_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + dx) - aL).coerceIn(minSize, maxW)
                                var nh = nw / na
                                val cy = (cr.top + cr.bottom) / 2f
                                var nT = cy - nh / 2f
                                var nB = cy + nh / 2f
                                if (nT < 0f) { nT = 0f; nB = nh; nh = nB - nT; nw = nh * na }
                                if (nB > 1f) { nB = 1f; nh = nB - nT; nw = nh * na }
                                cr.copy(right = aL + nw, top = nT, bottom = nB)
                            }
                            "l_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aR = cr.right
                                val maxW = aR.coerceAtLeast(minSize)
                                var nw = (aR - (cr.left + dx)).coerceIn(minSize, maxW)
                                var nh = nw / na
                                val cy = (cr.top + cr.bottom) / 2f
                                var nT = cy - nh / 2f
                                var nB = cy + nh / 2f
                                if (nT < 0f) { nT = 0f; nB = nh; nh = nB - nT; nw = nh * na }
                                if (nB > 1f) { nB = 1f; nh = nB - nT; nw = nh * na }
                                cr.copy(left = aR - nw, top = nT, bottom = nB)
                            }
                            "b_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aT = cr.top
                                val maxH = (1f - aT).coerceAtLeast(minSize)
                                var nh = ((cr.bottom + dy) - aT).coerceIn(minSize, maxH)
                                var nw = nh * na
                                val cx = (cr.left + cr.right) / 2f
                                var nL = cx - nw / 2f
                                var nR = cx + nw / 2f
                                if (nL < 0f) { nL = 0f; nR = nw; nw = nR - nL; nh = nw / na }
                                if (nR > 1f) { nR = 1f; nw = nR - nL; nh = nw / na }
                                cr.copy(left = nL, right = nR, bottom = aT + nh)
                            }
                            "t_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aB = cr.bottom
                                val maxH = aB.coerceAtLeast(minSize)
                                var nh = (aB - (cr.top + dy)).coerceIn(minSize, maxH)
                                var nw = nh * na
                                val cx = (cr.left + cr.right) / 2f
                                var nL = cx - nw / 2f
                                var nR = cx + nw / 2f
                                if (nL < 0f) { nL = 0f; nR = nw; nw = nR - nL; nh = nw / na }
                                if (nR > 1f) { nR = 1f; nw = nR - nL; nh = nw / na }
                                cr.copy(left = nL, right = nR, top = aB - nh)
                            }
                            else -> cr
                        }
                        currentOnAction(PockRefAction.UpdateCropRect(newRect))
                    },
                    onDragEnd = { dragEdge = null },
                    onDragCancel = { dragEdge = null },
                )
            },
    ) {
        val c = calcScreenCoords()
        val cL = c[0]; val cT = c[1]; val cR = c[2]; val cB = c[3]
        val cW = cR - cL; val cH = cB - cT

        drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, 0f), size = Size(cL, size.height))
        drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cR, 0f), size = Size(size.width - cR, size.height))
        drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cL, 0f), size = Size(cW, cT))
        drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cL, cB), size = Size(cW, size.height - cB))
        drawRect(
            color = Color.White,
            topLeft = Offset(cL, cT),
            size = Size(cW, cH),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
        val gridColor = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val fx = cL + cW * i / 3f
            drawLine(gridColor, Offset(fx, cT), Offset(fx, cB), strokeWidth = 1f)
            val fy = cT + cH * i / 3f
            drawLine(gridColor, Offset(cL, fy), Offset(cR, fy), strokeWidth = 1f)
        }
        val cornerLen = 28f
        val cornerW = 4f
        val cornerColor = Color.White
        val cornerPairs = listOf(
            Offset(cL, cT) to listOf(Offset(cornerLen, 0f), Offset(0f, cornerLen)),
            Offset(cR, cT) to listOf(Offset(-cornerLen, 0f), Offset(0f, cornerLen)),
            Offset(cL, cB) to listOf(Offset(cornerLen, 0f), Offset(0f, -cornerLen)),
            Offset(cR, cB) to listOf(Offset(-cornerLen, 0f), Offset(0f, -cornerLen)),
        )
        cornerPairs.forEach { (corner, dirs) ->
            dirs.forEach { dir ->
                drawLine(cornerColor, corner, corner + dir, strokeWidth = cornerW)
            }
        }
        val handlePositions = listOf(
            Offset(cL, cT), Offset(cR, cT), Offset(cL, cB), Offset(cR, cB),
            Offset((cL + cR) / 2f, cT), Offset((cL + cR) / 2f, cB),
            Offset(cL, (cT + cB) / 2f), Offset(cR, (cT + cB) / 2f),
        )
        handlePositions.forEach { pos ->
            drawCircle(color = Color.White, radius = handleRadius, center = pos)
            drawCircle(color = Color(0xFF2196F3), radius = handleRadius - 3f, center = pos)
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CropAspect.entries.forEach { aspect ->
                FilterChip(
                    selected = state.cropAspect == aspect,
                    onClick = { onAction(PockRefAction.SetCropAspect(aspect)) },
                    label = { Text(aspect.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // V_NOW (user request): rotation row removed. The
        // [向左旋转 90°] / [向右旋转 90°] / [0°] display
        // controls are gone from the crop interface; the
        // user wants a leaner crop UI that just crops (no
        // embedded rotation). The underlying
        // [PockRefAction.SetCropRotation] / [PockRefUiState.cropRotation]
        // / [SceneController.setCropRotation] infrastructure
        // is left intact (in case rotation is restored later
        // via a different affordance); only the UI rendering
        // and the user-facing controls are removed.

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilledTonalButton(
                onClick = { onAction(PockRefAction.CancelCrop) },
                modifier = Modifier.size(width = 120.dp, height = 48.dp),
            ) {
                Text("取消")
            }
            Button(
                onClick = { onAction(PockRefAction.CommitCrop) },
                modifier = Modifier.size(width = 120.dp, height = 48.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("应用", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * V_NOW: minimap rendering with free-drag + edge-dock + auto-
 * collapse. Wraps the existing [CanvasMinimap] (which keeps
 * its inner canvas-pan-by-drag gesture) and adds a 3-state
 * machine on top:
 *
 *   **Expanded** (`minimapCollapsed == false`): full panel
 *     rendered at the persisted screen-space position
 *     (default: bottom-left 24dp inset). A 500ms long-press
 *     enters "moving" mode, after which the user's drag
 *     updates the position. On release, if the position is
 *     within [DOCK_THRESHOLD_DP] of any screen edge, the
 *     minimap auto-docks to that edge and collapses.
 *
 *   **Collapsed** (`minimapCollapsed == true`): the small
 *     [CollapsedMinimapIcon] is rendered at the
 *     [PockRefUiState.minimapDockEdge] anchor (top center,
 *     bottom center, center start, or center end depending
 *     on which edge). Tap or hover expands it back to
 *     Expanded. The [hoverable] modifier covers stylus / mouse
 *     hover; [clickable] covers touch.
 *
 * The 3s auto-collapse matches the top toolbar's contract:
 * the timer starts after the last REAL interaction (Press /
 * Move-with-pressed / Release), reads the wall-clock against
 * [minimapLastInteractionMs], and polls every [100ms, 1s] so
 * a hover noise burst can't keep resetting the timer
 * (same pattern as the top toolbar — see the long comment
 * block at [TopToolbar]'s LaunchedEffect).
 *
 * V_NOW: long-press drag is REMOVED. The previous
 * 3-state model (Expanded / Collapsed / Moving) collapsed
 * to a 2-state model (Expanded / Collapsed). The user spec
 * is "minimap 去掉长按功能" — the panel stays anchored
 * at its [Alignment.BottomStart] default position for
 * the whole session. The [Moving] flag, the
 * [SetMinimapMoving] dispatch, the
 * [detectDragGesturesAfterLongPress] pointerInput, the
 * 1.05× lift-scale animation, and the long-press auto-dock
 * on release are all gone. Pinch-to-zoom is also gone
 * (see the [graphicsLayer] block + the dedicated
 * V_NOW comment for the zoom-removal rationale).
 *
 * Coexistence with [CanvasMinimap]'s inner gesture: the
 * outer panel has no pointerInput now, so all pointer
 * events flow through to the inner [CanvasMinimap]'s
 * canvas-pan-by-drag untouched. No gesture conflict
 * to reason about. The "outer long-press + inner
 * drag" coexistence dance that the previous comment
 * described is no longer relevant.
 */
@Composable
private fun BoxScope.MinimapChrome(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    parentWidth: Float,
    parentHeight: Float,
) {
    // Nothing to render if there are no images — the inner
    // [CanvasMinimap] would early-return anyway, but checking
    // here lets us skip the auto-collapse timer too (no
    // point in timing a panel that's invisible).
    if (state.images.isEmpty()) return

    val density = LocalDensity.current
    val cornerInsetPx = with(density) { Dimens.CornerInset.toPx() }
    // Rough size estimate used ONLY for boundary clamping on
    // drag. The actual minimap panel size is computed inside
    // [CanvasMinimap] from the scene's bbox + aspect and
    // varies per scene; we pick 200dp as a reasonable
    // upper bound for the clamp math so the minimap can't
    // drift completely off-screen even for very small /
    // very large scenes.
    val estimatedMinimapSize = with(density) { 200.dp.toPx() }
    val dockThresholdPx = with(density) { 48.dp.toPx() }

    // Effective position with bounds clamping. The persisted
    // [PockRefUiState.minimapPosition] is the top-left corner
    // of the panel in raw pixels. [Offset.Unspecified] =
    // "use the default bottom-left 24dp-inset position".
    // Clamping allows up to half the panel to drift off-
    // screen so the user can still see the other half to
    // drag it back.
    // V_NOW (alignment fix): the default position
    // places the element's top-left at the bottom-left
    // corner with [CornerInset] padding. The Y coordinate
    // is the top of the collapsed bar (44dp tall) so the
    // bar's bottom edge lands at [parentHeight - CornerInset],
    // flush with the FAB. The expanded minimap uses
    // align+padding (offset = Zero at default) and is
    // unaffected by this value.
    val defaultPosition = Offset(
        x = cornerInsetPx,
        y = parentHeight - with(density) { Dimens.BottomPanelChromeSize.toPx() } - cornerInsetPx,
    )
    val effectivePosition = if (state.minimapPosition == Offset.Unspecified) {
        defaultPosition
    } else {
        Offset(
            x = state.minimapPosition.x.coerceIn(
                minimumValue = -estimatedMinimapSize * 0.5f,
                maximumValue = parentWidth - estimatedMinimapSize * 0.5f,
            ),
            y = state.minimapPosition.y.coerceIn(
                minimumValue = -estimatedMinimapSize * 0.5f,
                maximumValue = parentHeight - estimatedMinimapSize * 0.5f,
            ),
        )
    }

    // V_NOW (user feedback "minimap 取消 3s 自动收起"): the
    // 3s auto-collapse (matching the top toolbar's contract)
    // is REMOVED. The user's stated rule is "minimap 只在
    // 被拖到屏幕左/右/上/下任一边缘时才自动隐藏,否则
    // 一直显示". So the auto-collapse timer no longer fires
    // on idle — the minimap stays expanded at its current
    // position until the user explicitly drags it to an
    // edge (the [onDragEnd] dock-detection block at the
    // pointerInput is the single path that flips
    // [minimapCollapsed] = true). The
    // [minimapLastInteractionMs] state and the updates to
    // it on tap / drag are kept (in case a future feature
    // wants to read "last interaction time" — e.g. for
    // analytics, or a user-toggleable "auto-collapse" opt-in
    // setting), but nothing reads it for the collapse decision
    // anymore. The whole
    // "matching the top toolbar's auto-collapse contract"
    // framing that justified the previous timer is gone:
    // the minimap's auto-collapse is now a USER ACTION
    // (drag-to-edge), not a passive timer.

    // V_NOW (user feedback "拖动 minimap 粘滞"): the previous
    // design read [effectivePosition] (derived from the
    // [state.minimapPosition] StateFlow value) on every drag
    // frame, and wrote the new position back to the StateFlow
    // on every frame. That's: drag event → dispatch action →
    // ViewModel.setMinimapPosition → appPrefs.putFloat.apply() +
    // _uiState.update { copy() } → StateFlow emission →
    // recomposition → [effectivePosition] recomputed →
    // [currentEffectivePos] updated → next-frame offset { }
    // reads it. On a 60Hz device that's 16-32ms of latency per
    // frame, which the user reported as "粘滞" (sticky / lagging
    // behind the finger). The Compose recomposition + offset
    // readback is the bottleneck, not the actual drag math.
    //
    // Fix: use a LOCAL [mutableStateOf] as the source of truth
    // during drag. The drag handler writes to it directly
    // (one mutable-state write → next frame's offset reads the
    // new value, no StateFlow roundtrip). The ViewModel /
    // SharedPreferences write happens only ON DRAG END so a
    // single [putFloat().apply()] covers the whole drag, not
    // 60 per second. For app-kill-during-drag safety we also
    // write to the ViewModel on every drag frame in the
    // background (cheap, async, doesn't block the UI).
    //
    // [localPosition] is initialized from [effectivePosition]
    // and is the SINGLE source of truth for the rendered
    // [offset { }]. The [LaunchedEffect] below keeps it in
    // sync with [state.minimapPosition] when the state changes
    // from outside our drag handler (e.g. user restored from
    // pref, or some other code path moves the minimap).
    val localPosition = remember { mutableStateOf(effectivePosition) }
    // V_NOW (alignment fix — minimap bottom = FAB bottom):
    // the actual measured size of the panel Box (varies with
    // scene bbox aspect ratio, clamped to [60dp, 200dp] on
    // each dim). The default `effectivePosition` uses
    // [estimatedMinimapSize] = 200dp as a placeholder for
    // the math (so the user can't drag a non-existent box
    // off the screen), but when the actual panel size is
    // < 200dp on a tall scene, the panel's bottom edge
    // ended up at [H - 24 - 200 + actualH] instead of
    // [H - 24] — visibly higher than the FAB. We read the
    // post-layout [IntSize] via [Modifier.onSizeChanged] and
    // use it in the expanded-branch [Modifier.offset]
    // math so the box's bottom edge is always at exactly
    // [parentHeight - 24] when the user is at the default
    // position. The collapsed (小白条) branch already
    // anchors its touch target's bottom to [H - 24]
    // unconditionally (the touch target is a fixed
    // 32dp / 64dp box whose bottom is at
    // [parentHeight - touchHeight - cornerInset] = H - 56,
    // so its bottom is H - 56 + 32 = H - 24 — already
    // aligned with the FAB; the only fix needed is the
    // expanded branch, which has variable panel size).
    val panelActualSize = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // V_NOW (user feedback "minimap 取消 3s 自动收起"): the
    // [minimapLastInteractionMs] timestamp and its
    // tap/hover/drag updates were removed along with the
    // 3s auto-collapse timer (see the comment block above
    // the empty block where the LaunchedEffect used to be).
    // Nothing reads the timestamp anymore, so tracking it
    // would be dead writes. A future "auto-collapse" opt-in
    // setting would re-introduce both the var and the
    // writes in one go.
    androidx.compose.runtime.LaunchedEffect(state.minimapPosition) {
        if (state.minimapPosition != Offset.Unspecified) {
            localPosition.value = Offset(
                x = state.minimapPosition.x.coerceIn(
                    minimumValue = -estimatedMinimapSize * 0.5f,
                    maximumValue = parentWidth - estimatedMinimapSize * 0.5f,
                ),
                y = state.minimapPosition.y.coerceIn(
                    minimumValue = -estimatedMinimapSize * 0.5f,
                    maximumValue = parentHeight - estimatedMinimapSize * 0.5f,
                ),
            )
        }
    }

    // V_NOW: pinch-to-zoom state removed (see
    // [graphicsLayer] block below + pinch
    // pointerInput removal above). The
    // [liveZoom] mutableFloatState, the
    // StateFlow→local sync LaunchedEffect,
    // and the 180ms debounced
    // [SetMinimapZoomScale] dispatch are
    // all gone. [state.minimapZoomScale] is
    // still read at first composition to
    // seed nothing (the field is unused
    // here now), and the data layer
    // (PockRefAction / AppPreferences /
    // ViewModel reducer) is intentionally
    // preserved untouched.

    // RememberUpdatedState so the pointerInput coroutine
    // (which is keyed on Unit and never re-collects) sees
    // the latest parent size / dock threshold / local
    // position on every drag frame. The local position is
    // already a mutableStateOf so we can read its .value
    // directly without rememberUpdatedState.
    val currentParentWidth by rememberUpdatedState(parentWidth)
    val currentParentHeight by rememberUpdatedState(parentHeight)
    val currentMinimapSize by rememberUpdatedState(estimatedMinimapSize)
    val currentDockThreshold by rememberUpdatedState(dockThresholdPx)

    // V_NOW (user feedback "minimap 取消 3s 自动收起"): the
    // 3s auto-collapse (matching the top toolbar's contract)
    // is REMOVED. The user's stated rule is "minimap 只在
    // 被拖到屏幕左/右/上/下任一边缘时才自动隐藏,否则
    // 一直显示". So the auto-collapse timer no longer fires
    // on idle — the minimap stays expanded at its current
    // position until the user explicitly drags it to an
    // edge (the [onDragEnd] dock-detection block at the
    // pointerInput is the single path that flips
    // [minimapCollapsed] = true). The
    // [minimapLastInteractionMs] state and the updates to
    // it on tap / drag are kept (in case a future feature
    // wants to read "last interaction time" — e.g. for
    // analytics, or a user-toggleable "auto-collapse" opt-in
    // setting), but nothing reads it for the collapse decision
    // anymore. The whole
    // "matching the top toolbar's auto-collapse contract"
    // framing that justified the previous timer is gone:
    // the minimap's auto-collapse is now a USER ACTION
    // (drag-to-edge), not a passive timer.
    //
    // The 1-frame latency the `minimapLastInteractionMs`
    // `mutableLongStateOf` had been paying for is also
    // recovered — the only `remember { ... }` left for the
    // timestamp is the call-site update, not the polling
    // loop reading it.

    if (state.minimapCollapsed) {
        // V_NOW (iOS PiP-style minimap — position
        // preservation): the bar is positioned via
        // [Modifier.offset] keyed on [localPosition]
        // (the same draggable state the expanded
        // panel uses), NOT via [Modifier.align] +
        // [Modifier.padding]. The previous
        // [Alignment.CenterEnd] / [Alignment.BottomCenter]
        // approach anchored the bar to the screen-edge
        // MIDDLE (vertically for left/right, horizontally
        // for top/bottom) and DISCARDED the user's
        // drag-position — when the user dragged the
        // minimap to e.g. the upper-right area, then
        // released near the right edge, the bar
        // collapsed but jumped to the vertical center
        // of the right edge, losing the user's
        // intentional Y position. The new design:
        //   - X is anchored to the docked screen edge
        //     (left/right) with [Dimens.CornerInset]
        //     padding
        //   - X (or Y) along the edge is taken from
        //     [localPosition.value] so the bar lands
        //     where the user dropped it
        //   - Clamped to the visible viewport so the
        //     bar can't sit half off-screen
        // The bar's touch target still owns the iOS
        // PiP capsule + hover-grow + click-to-expand
        // (see [CollapsedMinimapIcon]); this Box is
        // the position + double-tap-toggle shell.
        val edge = state.minimapDockEdge
        // V_NOW (0-frame lag fix): the bar's
        // screen-edge axis math is done INSIDE
        // the [graphicsLayer] block (DRAW phase)
        // below, NOT in a separate composition-
        // time snapshot. Pre-computing the
        // position into a local `val` (the
        // previous design) made it a 1-frame
        // snapshot of [localPosition]: when the
        // drag handler wrote [localPosition.value],
        // the next frame's composition
        // re-evaluated the [if] branch and
        // re-ran [barOffset], but the actual
        // offset was applied with that 1-frame
        // lag. The new design lets the graphics
        // layer block read [localPosition.value]
        // DIRECTLY in the draw phase, applying
        // the new value on the same frame the
        // write happens.
        //
        // Touch target dimensions (64dp × 32dp
        // for TOP/BOTTOM, 32dp × 64dp for
        // LEFT/RIGHT) are pre-composed at the
        // top of the function so the graphics
        // layer block only does the per-frame
        // axis-clamp math.
        // V_NOW: touch target matches [CollapsedMinimapIcon]
        // dimensions — 44dp along the bar's long axis
        // (same as the auto-layout FAB height for
        // horizontal alignment), 32dp along the short
        // axis. These vary by edge direction.
        val isHorizontalEdge = edge == com.pockref.app.model.MinimapDockEdge.TOP ||
            edge == com.pockref.app.model.MinimapDockEdge.BOTTOM
        val touchWidthDp = if (isHorizontalEdge) Dimens.BottomPanelChromeSize else 32.dp
        val touchHeightDp = if (isHorizontalEdge) 32.dp else Dimens.BottomPanelChromeSize
        val touchWidthPx = with(density) { touchWidthDp.toPx() }
        val touchHeightPx = with(density) { touchHeightDp.toPx() }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                // V_NOW (lag fix — REVISED, same
                // rationale as the expanded
                // branch above): the previous
                // 0-frame attempt used
                // [Modifier.graphicsLayer { translationX = ... }]
                // for the bar, but
                // [graphicsLayer] did not
                // move the hit region in
                // this Compose version —
                // the visual bar appeared
                // at the new position but
                // the touch hit region
                // stayed at the Box's
                // layout position, so
                // double-tap to expand
                // the bar from a
                // non-default Y position
                // (the whole point of the
                // position-preserving
                // redesign) failed
                // silently.
                //
                // Reverted to
                // [Modifier.offset { ... }]
                // (layout phase, 1-frame
                // latency) so the hit
                // region follows the bar.
                // Accept the 1-frame lag
                // — the bar isn't being
                // dragged while collapsed
                // (only the expanded
                // panel supports drag),
                // so the lag is only
                // visible on the
                // expand/collapse
                // transition, not on
                // continuous interaction.
                // [Modifier.offset] reads
                // [localPosition.value]
                // in the layout phase
                // and DOES move both
                // the visual position
                // AND the hit region
                // — which is what we
                // need.
                .offset {
                    when (edge) {
                        com.pockref.app.model.MinimapDockEdge.TOP,
                        com.pockref.app.model.MinimapDockEdge.BOTTOM -> {
                            val clampedX = localPosition.value.x.coerceIn(
                                minimumValue = cornerInsetPx,
                                maximumValue = currentParentWidth - touchWidthPx - cornerInsetPx,
                            )
                            val yEdgePx = if (edge == com.pockref.app.model.MinimapDockEdge.TOP) {
                                cornerInsetPx
                            } else {
                                currentParentHeight - touchHeightPx - cornerInsetPx
                            }
                            IntOffset(x = clampedX.toInt(), y = yEdgePx.toInt())
                        }
                        com.pockref.app.model.MinimapDockEdge.LEFT,
                        com.pockref.app.model.MinimapDockEdge.RIGHT -> {
                            val clampedY = localPosition.value.y.coerceIn(
                                minimumValue = cornerInsetPx,
                                maximumValue = currentParentHeight - touchHeightPx - cornerInsetPx,
                            )
                            val xEdgePx = if (edge == com.pockref.app.model.MinimapDockEdge.LEFT) {
                                cornerInsetPx
                            } else {
                                currentParentWidth - touchWidthPx - cornerInsetPx
                            }
                            IntOffset(x = xEdgePx.toInt(), y = clampedY.toInt())
                        }
                        com.pockref.app.model.MinimapDockEdge.NONE -> {
                            IntOffset(
                                x = cornerInsetPx.toInt(),
                                y = (currentParentHeight - touchHeightPx - cornerInsetPx).toInt(),
                            )
                        }
                    }
                }
                // V_NOW (iOS PiP-style): double-tap
                // on the bar toggles to the expanded
                // panel. This is a complement to
                // single-tap (handled inside
                // [CollapsedMinimapIcon]) and to
                // hover-to-expand (same): a user
                // with no hover capability (touch
                // screen, no stylus) can still
                // expand the bar with a deliberate
                // double-tap, mirroring the iOS
                // PiP-window double-tap-to-toggle
                // grammar.
                .pointerInput(state.minimapDockEdge) {
                    detectTapGestures(
                        onDoubleTap = {
                            onAction(PockRefAction.SetMinimapCollapsed(false))
                        },
                    )
                },
        ) {
            CollapsedMinimapIcon(
                edge = state.minimapDockEdge,
                onExpand = {
                    onAction(PockRefAction.SetMinimapCollapsed(false))
                },
            )
        }
    } else {
        // Full minimap panel. Outer Box carries the
        // V_NOW: long-press-drag-to-move AND
        // two-finger pinch-to-zoom are BOTH DISABLED
        // by user spec "minimap 去掉长按功能" +
        // "minimap 禁止自由调整大小". The panel
        // is anchored at its [Alignment.BottomStart]
        // default position (24dp inset, 24dp above
        // bottom edge) for the entire session —
        // no drag, no zoom, no lift animation. The
        // [graphicsLayer] block is a no-op identity
        // transform; the [onSizeChanged] still
        // captures panel size for the [offset]
        // alignment math but no [localPosition]
        // mutation ever happens.
        //
        // The inner is the existing [CanvasMinimap]
        // (which keeps its own canvas-pan-by-drag
        // gesture for panning the MAIN view, NOT
        // moving the minimap panel — see the
        // coexistence note at the call site).
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                // V_NOW (alignment fix — minimap
                // bottom = FAB bottom): the box
                // is anchored to
                // [Alignment.BottomStart] with
                // 24dp inset (via [Modifier.padding]),
                // so the box's bottom edge is
                // ALWAYS at
                // [parentHeight - 24dp] regardless
                // of the box's measured size. The
                // previous design used a
                // fixed [estimatedMinimapSize] =
                // 200dp placeholder in the
                // default-position math
                // (top-left = (24,
                // parentHeight - 200 - 24)),
                // which left the box's bottom
                // edge at
                // [parentHeight - 24 - 200 + actualH]
                // when the actual panel was
                // smaller than 200dp — visibly
                // higher than the FAB. The new
                // design reads the post-layout
                // [IntSize] via [Modifier.onSizeChanged]
                // and converts [localPosition]
                // (screen-coord top-left) into
                // an offset RELATIVE to the
                // aligned position, so the
                // box's screen-coord top-left
                // is still exactly
                // [localPosition.value]. Drag
                // math, persistence, dock
                // detection — all unchanged.
                .align(Alignment.BottomStart)
                .padding(start = Dimens.CornerInset, bottom = Dimens.CornerInset)
                .onSizeChanged { panelActualSize.value = it }
                .offset {
                    // V_NOW (alignment fix v2 — Box size
                    // includes padding): the previous
                    // version of this expression was
                    //
                    //   alignedY = parentHeight - 24 - panelActualSize.value.height
                    //
                    // which over-subtracted 24dp. The
                    // reason: [Modifier.padding(start,
                    // bottom)] wraps the box's INNER
                    // content; the box's outer size
                    // is [child + padding] =
                    // [child + 24 in both width and
                    // height], so
                    // [panelActualSize.value.height]
                    // reports [childHeight + 24] —
                    // NOT [childHeight]. Subtracting
                    // 24 again double-counted the
                    // bottom padding and the child
                    // ended up 24dp short of
                    // [parentHeight - 24]. The
                    // corrected formula
                    //
                    //   alignedY = parentHeight - panelActualSize.value.height
                    //
                    // yields [parentHeight - childHeight
                    // - 24] = exactly the child
                    // top-left Y at the
                    // bottom-aligned default, so
                    // [localPosition.value] ==
                    // (24, parentHeight - childHeight
                    // - 24) at the default and the
                    // child bottom lands at exactly
                    // [parentHeight - 24], aligned
                    // flush with the FAB.
                    // V_NOW (alignment fix): when the
                    // minimap is at its default position
                    // (Unspecified = never dragged), the
                    // align+padding already positions the
                    // box correctly at BottomStart with
                    // 24dp inset. The offset should be
                    // zero so the box stays at that
                    // aligned position. When the user
                    // has dragged the minimap, we
                    // convert the screen-coord
                    // localPosition to an offset
                    // relative to the aligned position.
                    if (state.minimapPosition == Offset.Unspecified) {
                        IntOffset.Zero
                    } else {
                        val alignedX = cornerInsetPx
                        val alignedY = currentParentHeight - panelActualSize.value.height
                        IntOffset(
                            x = (localPosition.value.x - alignedX).toInt(),
                            y = (localPosition.value.y - alignedY).toInt(),
                        )
                    }
                }
                .graphicsLayer {
                    // V_NOW: identity transform — the
                    // panel is a static
                    // [Alignment.BottomStart] surface
                    // that draws with its raw size +
                    // a fixed 2dp shadow (matching the
                    // previous idle-state shadow).
                    // Pinch-zoom + long-press-lift
                    // scale animations are removed by
                    // user spec. No [Modifier.scaleX/Y]
                    // write, no [shadowElevation] lerp.
                    shadowElevation = 2.dp.toPx()
                }
                // V_NOW: long-press drag handler is
                // REMOVED by user spec "minimap 去掉
                // 长按功能". The panel stays anchored
                // at its [Alignment.BottomStart]
                // default position. No pointerInput
                // is attached to the outer panel
                // Box anymore — the panel has zero
                // touch interactivity of its own
                // (the inner [CanvasMinimap] keeps
                // its own canvas-pan-by-drag for
                // panning the MAIN view, which
                // doesn't conflict with this
                // "panel stays still" rule).
                // V_NOW: two-finger pinch-to-zoom is
                // DISABLED by user spec "minimap 禁止
                // 自由调整大小". The panel size is
                // fixed by the scene-bbox math in
                // [CanvasMinimap] (60dp..200dp).
                // The previous [detectTransformGestures]
                // pointerInput, [liveZoom] state, and
                // [SetMinimapZoomScale] dispatch are
                // removed from this UI layer. The
                // data layer (PockRefAction /
                // AppPreferences / ViewModel reducer)
                // is preserved so a future re-enable
                // is a UI-only restoration, and
                // existing users' persisted
                // `minimap_zoom_scale` SharedPreferences
                // key is harmlessly ignored.
                //
                // V_NOW (iOS PiP-style): double-tap on
                // the expanded panel collapses to the
                // bar at the current position. The
                // user spec is "在面板上双击就收回
                // 小白条", matching the iOS PiP
                // double-tap-to-toggle behavior. The
                // dock-edge detection on long-press
                // drag is preserved for the
                // drag-to-edge case; double-tap is
                // the complement (panel still in the
                // middle of the screen, just want to
                // hide it without moving it). On
                // double-tap, [minimapCollapsed]
                // flips to true and
                // [minimapDockEdge] is set to the
                // nearest edge so the bar appears
                // docked.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            val pos = localPosition.value
                            val nearestEdge = when {
                                pos.x < currentParentWidth / 2f ->
                                    com.pockref.app.model.MinimapDockEdge.LEFT
                                else ->
                                    com.pockref.app.model.MinimapDockEdge.RIGHT
                            }
                            onAction(PockRefAction.SetMinimapDockEdge(nearestEdge))
                            onAction(PockRefAction.SetMinimapCollapsed(true))
                        },
                    )
                },
        ) {
            CanvasMinimap(
                state = state,
                onAction = onAction,
                modifier = Modifier,
                isCollapsed = false,
                onExpand = {},
                // V_NOW (0-frame pinch lag fix): the
                // panel SIZE is no longer
                // parameter-driven. Pinch zoom
                // is applied by the outer
                // [Modifier.graphicsLayer.scaleX/Y]
                // in the [MinimapChrome]
                // wrapper, which scales the
                // panel's DRAW output without
                // re-running layout. The panel
                // base size here is the
                // scene-bbox-derived [60dp,
                // 200dp] — draw-time scaling
                // produces the visual 0.5×
                // – 2.0× range the user
                // pinches for, with 0-frame
                // latency.
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyCanvasState(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onImport: () -> Unit,
    onOpenScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recentImports = state.recentScenes.filter { it.source == "Saved" }.take(10)
    val recentOpens = state.recentScenes.filter { it.source == "Opened" }.take(10)

    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PockRef",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                FilledTonalButton(
                    onClick = onOpenScene,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }

            if (recentImports.isNotEmpty() || recentOpens.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (recentImports.isNotEmpty()) {
                        RecentFileSection(
                            title = "最近导入",
                            entries = recentImports,
                            onOpen = { onAction(PockRefAction.OpenRecentScene(it)) },
                            onRemove = { onAction(PockRefAction.RemoveRecentScene(it)) },
                        )
                    }
                    if (recentOpens.isNotEmpty()) {
                        RecentFileSection(
                            title = "最近打开",
                            entries = recentOpens,
                            onOpen = { onAction(PockRefAction.OpenRecentScene(it)) },
                            onRemove = { onAction(PockRefAction.RemoveRecentScene(it)) },
                            showFileSize = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentFileSection(
    title: String,
    entries: List<RecentSceneEntry>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    showFileSize: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            // V8: chrome-match the recent-files list
            // surface. `surfaceVariant` carries a subtle
            // primary-tinted blue cast; switching to the
            // chrome surface token (neutral gray) makes the
            // list visually sibling to the top toolbar.
            color = MaterialTheme.colorScheme.surface
                .copy(alpha = Dimens.ChromeSurfaceAlpha),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(entries, key = { it.uri }) { entry ->
                    RecentFileItem(
                        entry = entry,
                        showFileSize = showFileSize,
                        onOpen = { onOpen(entry.uri) },
                        onRemove = { onRemove(entry.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFileItem(
    entry: RecentSceneEntry,
    showFileSize: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val bgColor = if (hovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.filePath.ifBlank { entry.uri },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showFileSize && entry.fileSize > 0L) {
                    Text(
                        formatFileSize(entry.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onOpen,
            modifier = Modifier
                .size(28.dp)
                .minimumInteractiveComponentSize(),
        ) {
            Icon(
                Icons.Rounded.FileOpen,
                contentDescription = "打开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(28.dp)
                .minimumInteractiveComponentSize(),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
         else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
    }
}

/**
 * V4: scene-name title that shrinks to fit the
 * available width instead of being ellipsized.
 *
 * The previous cap of 480dp wasn't enough for users'
 * actual scene names (a 30-char Chinese+ASCII mix
 * still overflowed). Compose 1.7's [BasicText] has
 * no `autoSize` parameter (that lands in 1.8) so
 * this is a hand-rolled shrink-to-fit: it lays out
 * the text at each candidate size, finds the largest
 * one whose [TextLayoutResult.size.width] fits the
 * available width, and renders at that size. Sizes
 * in the candidate pool are tuned for [titleMedium]
 * typography on a 340dpi tablet — starting at
 * 22sp (the natural titleMedium) and stepping down
 * to 16sp (one notch below [bodyLarge]). Below 16sp
 * the title is no longer readable so we fall back
 * to the 16sp version with `maxLines = 1` and
 * `TextOverflow.Ellipsis` — the user then sees an
 * ellipsis at the cap, which is a clear signal
 * "your title doesn't fit at any readable size,
 * shorten it" rather than a silent truncation.
 *
 * Performance: 7 candidate sizes × 1 layout pass
 * each = 7 layouts per recomposition. Cheap because
 * [TextLayoutResult] is a Compose state that's
 * already being computed for hit-testing in the
 * outer Row. The cost is one extra layout pass
 * when the title changes; for a typical user who
 * renames the scene once per session, that's a
 * one-time hit.
 */
@Composable
private fun AutoShrinkTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    // Font-size pool, descending. [titleMedium] =
    // 16sp on Material 3; we use 22/20/18/17/16.5/16/15sp
    // which covers the practical range from "20 chars
    // fits" to "8 chars fits" before going to ellipsis.
    val candidates = listOf(22.sp, 20.sp, 18.sp, 17.sp, 16.sp, 15.sp, 14.sp)
    var chosenSize by remember(text) { mutableStateOf(candidates.first()) }
    val color = MaterialTheme.colorScheme.onSurface
    val decoration = TextDecoration.None
    // Layout probe: render at each candidate size and
    // remember the largest whose intrinsic width fits
    // the available space. [BoxWithConstraints] gives
    // the available width the parent gave us (the
    // .weight(1f, fill = false) modifier).
    BoxWithConstraints(modifier = modifier) {
        val available = constraints.maxWidth
        if (available > 0) {
            // Quick pick by intrinsic width. We don't
            // do a full Layout pass per candidate — that
            // would re-layout 7 times per recomposition.
            // Instead we measure once at each size with
            // [TextMeasurer] (Compose 1.7+) which is
            // cheap because it doesn't go through Layout.
            val measurer = rememberTextMeasurer()
            var best = candidates.last()  // smallest fallback
            for (size in candidates.reversed()) {
                val result = measurer.measure(
                    text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = size,
                    ),
                    maxLines = 2,
                )
                if (result.size.width <= available) {
                    best = size
                    break
                }
            }
            chosenSize = best
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = chosenSize,
            ),
            color = color,
            textDecoration = decoration,
            // V5: maxLines raised from 1 to 2. The
            // user's V5 spec says "增加高度限制以完
            // 整显示标题内容,避免文字被截断" — if a
            // name is so long that even the smallest
            // candidate (14sp) doesn't fit on one
            // line, wrap to 2 lines instead of
            // ellipsizing. The toolbar's height
            // auto-grows to fit (we removed the .height
            // pin in V5) so 2 lines look natural. If
            // 2 lines still don't fit, the Text's
            // default overflow kicks in (ellipsize at
            // the second line's end).
            maxLines = 2,
            // If even the smallest candidate overflows
            // 2 lines, ellipsize. The user sees a
            // clear "this doesn't fit" signal instead
            // of an awkwardly small font.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopToolbar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    isEditingSceneName: Boolean,
    editingSceneValueVar: androidx.compose.runtime.MutableState<TextFieldValue>,
    titleFocusRequester: FocusRequester,
    editSessionStartMsVar: androidx.compose.runtime.MutableLongState,
    onEditModeChanged: (Boolean, TextFieldValue) -> Unit,
    onSceneNameCommitted: (String) -> Unit,
    onEditDismiss: () -> Unit,
    // V6: menu state hoisted to [PockRefApp] so the parent
    // Box can render a tap-outside scrim. The toolbar only
    // owns the open/close transition (the [AnimatedVisibility]
    // for the popup content); the actual state lives in
    // [PockRefApp.menuExpanded] and is mutated via
    // [onMenuExpandedChange].
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    // V_NOW: parent container's max width, captured by the
    // [BoxWithConstraints] wrapper at the [PockRefApp] call
    // site. The toolbar uses this to cap its Surface width
    // so its right edge can never sit closer than
    // [Dimens.CornerInset] (24dp) from the window's right
    // edge — even if the title + action row would otherwise
    // force the toolbar to grow past the safe area or wrap
    // to a second line. Default [Dp.Infinity] means "no cap"
    // (preview / standalone tests).
    parentMaxWidth: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
) {
    // V6: [menuExpanded] is now a parameter (hoisted to
    // [PockRefApp]) so the parent can render a tap-outside
    // scrim. The toolbar's chrome Surface itself no longer
    // needs popup-position bookkeeping ([toolbarSizePx],
    // [toolbarBoundsInWindow], [titleBarBounds],
    // [titleTextLeftPx], [titleTextWidthPx] all removed —
    // they only existed to anchor the now-deleted Popup to
    // the right pixel).
    // V_NOW: [moreExpanded] (3-dots MoreMenuButton 的 state)
    // 已下线 — 整个 MoreMenuButton 删除,顶栏不再有
    // "更多" 入口(主题切换 + 导入 pur 都下线)。顶栏 sub-panel
    // 现在只有 [menuExpanded] (最近文件下拉) 和
    // [settingsSheetOpen] (gear → settings 面板)。
    // V_NOW (user bug report — "top bar doesn't auto-collapse
    // even after 3s"): the old [isInteracting] boolean-based
    // design had the auto-collapse timer re-launching on
    // every Enter/Exit hover event. S Pen crossing the
    // toolbar boundary (extremely common while drawing on
    // a tablet canvas) fired Enter (isInteracting = true,
    // LaunchedEffect re-launched → in-flight 3s delay
    // cancelled) then Exit (isInteracting = false,
    // LaunchedEffect re-launched → 3s delay restarted from
    // 0). As long as the user kept crossing the toolbar
    // boundary every <3s — exactly the natural rhythm of
    // drawing — the timer never accumulated, and the
    // toolbar stayed open until they left the canvas idle
    // for >3s of continuous no-crossing. Most users hit
    // this bug at least once per session.
    //
    // New design: track the wall-clock timestamp of the
    // last REAL interaction (Press / Move-with-pressed /
    // drag-out Exit-with-pressed / Release-no-pressed —
    // NOT Enter / not Exit-with-no-pressed, which is the
    // "stylus just flew over the toolbar" case). The
    // LaunchedEffect polls this timestamp instead of being
    // re-launched on it, so hover noise can't keep
    // resetting the countdown.
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // V7 (re-applied): live width of the toolbar Row,
    // captured via [onSizeChanged]. The popup Column
    // below reads this and sets its own width to match —
    // so the popup tracks the toolbar's current "displayed
    // width" instead of filling the Surface. When the
    // toolbar is collapsed (title only) the popup is
    // narrow; when expanded (title + action icons) the
    // popup widens. Starts at 0 because the Row has not
    // been laid out yet on the first composition; the
    // very next layout pass writes the real value (the
    // toolbar is always visible, so this happens before
    // the user can open the menu).
    var toolbarRowWidth by remember { mutableStateOf(0) }

    // V_NOW: SoftwareKeyboardController for explicit IM show
    // when entering the title's rename mode. Compose's TextField
    // usually pops the keyboard on focus, but on the SM-X810
    // (Samsung fork) the auto-show is suppressed under certain
    // input-method states — having the handle lets us force
    // `show()` after `requestFocus()` in the LaunchedEffect
    // below.
    val keyboardController = LocalSoftwareKeyboardController.current

    val hasOpenSubPanel = menuExpanded || state.settingsSheetOpen
    LaunchedEffect(state.topToolbarExpanded, hasOpenSubPanel) {
        if (state.topToolbarExpanded && !hasOpenSubPanel) {
            // V_NOW (user bug report — "top bar doesn't
            // auto-collapse even after 3s"): the previous
            // design used `isInteracting` as a LaunchedEffect
            // key, so any pointer event that flipped the
            // boolean (hover Enter/Exit from a stylus
            // crossing the toolbar) cancelled the in-flight
            // `delay(3000)`. S Pen crossing the toolbar at
            // the natural pace of drawing (<3s per crossing)
            // kept the timer reset indefinitely.
            //
            // New design: poll the wall clock against
            // [lastInteractionMs] (which is ONLY updated by
            // real interactions — see the pointerInput
            // above for the exact filter). The
            // LaunchedEffect's key is just
            // `(state.topToolbarExpanded, hasOpenSubPanel)`
            // so it doesn't re-launch on every pointer
            // event; the loop reads the latest
            // [lastInteractionMs] via snapshot each
            // iteration. Worst-case collapse time is
            // 3000ms + 100ms (one poll period) of the latest
            // [lastInteractionMs] write, regardless of how
            // much hover noise the toolbar is receiving.
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionMs
                if (elapsed >= 3000L) {
                    onAction(PockRefAction.SetTopToolbarExpanded(false))
                    break
                }
                val waitMs = (3000L - elapsed).coerceIn(100L, 1000L)
                delay(waitMs)
            }
        }
    }

    // V_NOW (user request): when the recent-files menu is
    // open and the user has been idle for a while, auto-
    // close the menu. Without this, opening the chevron to
    // peek at the list (then not picking anything) leaves
    // the popup lingering on top of the canvas until the
    // user manually taps a row or tap-outside — visually
    // noisy and blocks the top-toolbar auto-collapse below
    // (which sees [hasOpenSubPanel = true] and waits
    // forever).
    //
    // Reset semantics: shares the same [lastInteractionMs]
    // state the top-toolbar 3s auto-collapse reads from.
    // The user scrolling through a long list keeps
    // [lastInteractionMs] continuously fresh (Press +
    // Move-with-pressed keep updating it) and the popup
    // stays open for as long as they hold and move. The
    // 5s timer only starts ticking after the user
    // releases AND the menu is still open. Matches the
    // top-toolbar's 3s collapse by feeling (similar order
    // of magnitude) but gives the user a bit more time
    // to scan a longer list of recent scenes on a
    // tablet-sized screen.
    //
    // The polling loop reads [lastInteractionMs] via
    // snapshot, so hover noise that doesn't touch the
    // timestamp can't keep resetting the timer (same
    // fix as the 3s auto-collapse above).
    LaunchedEffect(menuExpanded) {
        if (menuExpanded) {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionMs
                if (elapsed >= 5000L) {
                    onMenuExpandedChange(false)
                    break
                }
                val waitMs = (5000L - elapsed).coerceIn(100L, 1000L)
                delay(waitMs)
            }
        }
    }

    // When the title enters edit mode, focus the text field and
    // request the soft keyboard. Triggered on the [isEditingSceneName]
    // edge: re-arms every time the user re-enters edit mode.
    // [editSessionStartMs] is the wall-clock at which the latest
    // edit session began; the focus-loss commit path ignores
    // any focus loss within the first 200ms of the session,
    // because Compose's focus system has a brief settling
    // period after the TextField is first composed where the
    // focus state can flicker false→true→false. Without the
    // grace window, the TextField pops up for one frame and
    // then disappears as the focus-loss callback fires
    // synchronously with the initial focus request.
    LaunchedEffect(isEditingSceneName) {
        if (isEditingSceneName) {
            editSessionStartMsVar.longValue = System.currentTimeMillis()
            // V_NOW (user bug report): 长按顶栏标题进入重命名后,软键盘
            // 不会自动弹出。`requestFocus()` 立即调用时 OutlinedTextField
            // 的焦点 modifier 可能还没完全挂上(在 `if/else` 分支切换
            // 同一帧内刚刚被首次 compose),焦点请求会被静默丢弃,IM 也
            // 就收不到 `showSoftInput` 信号。
            //
            // 修法:
            //   1. 等一帧再 requestFocus — 等焦点 modifier attach 完毕。
            //   2. 拿到 `LocalSoftwareKeyboardController` 显式 `show()` —
            //      Compose 的 TextField 焦点逻辑在某些 Samsung fork
            //      (SM-X810) 上不会自动弹 IM,需要显式触发。
            withFrameNanos { }
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = modifier
            // V_NOW: cap the toolbar's max width so its right
            // edge stays [Dimens.CornerInset] (24dp) from the
            // window right at all times, even when the title +
            // action row would otherwise overflow. Available
            // width = parentMaxWidth - 2·CornerInset (one
            // CornerInset for the left start-padding applied at
            // the call site, one for the right margin the cap
            // enforces here). When [parentMaxWidth] is
            // [Dp.Infinity] (preview / standalone tests) the
            // cap is skipped so the toolbar is free to size
            // to its content.
            .let { base ->
                if (parentMaxWidth != Dp.Infinity && parentMaxWidth > 0.dp) {
                    base.widthIn(max = (parentMaxWidth - Dimens.CornerInset * 2).coerceAtLeast(0.dp))
                } else {
                    base
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // V_NOW: only REAL interactions refresh
                        // [lastInteractionMs]. The hover-only
                        // events (Enter with no buttons, Exit
                        // with no buttons) used to flip
                        // [isInteracting] which re-launched the
                        // auto-collapse effect and cancelled
                        // the in-flight delay. S Pen crossing
                        // the toolbar boundary while drawing
                        // on the canvas was the dominant
                        // trigger. The new wall-clock polling
                        // pattern survives those flickers
                        // because the timer reads
                        // [lastInteractionMs] instead of
                        // re-launching on every change.
                        when (event.type) {
                            PointerEventType.Press -> {
                                lastInteractionMs = System.currentTimeMillis()
                            }
                            PointerEventType.Move -> {
                                if (event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                            PointerEventType.Exit -> {
                                // Pointer left the toolbar
                                // bounds. If they were
                                // pressing (drag in progress,
                                // e.g. dragging a chip from
                                // the toolbar onto the
                                // canvas), keep the countdown
                                // paused so the toolbar
                                // doesn't collapse mid-drag.
                                // If they were NOT pressing
                                // (just an unhover), this is
                                // hover noise — ignore.
                                if (event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                            else -> {
                                // Release and any other
                                // event. If all pointers are
                                // up, the user has finished
                                // their gesture — mark the
                                // start of the idle countdown
                                // so the polling loop sees a
                                // fresh "now".
                                if (!event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
            }
            // V6: removed the [onSizeChanged] / [onGloballyPositioned]
            // pair that captured toolbar / title bounds for the
            // (now-deleted) Popup anchor math. The popup is an
            // inline Surface child instead of a separate window,
            // so it doesn't need window-coordinate bounds — it
            // just lays out below the toolbar Row inside the
            // shared Surface.
            ,
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
        tonalElevation = Dimens.ToolbarElevation,
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
    ) {
        // V6: wrap the toolbar Row + the (now-inline) popup
        // AnimatedVisibility in a single Column. The Surface
        // gets to keep its single set of rounded outer
        // corners, and the popup expands downward from the
        // toolbar's bottom edge with no shadow seam between
        // the two — the user's "连续区块" requirement.
        Column {
            Row(
                modifier = Modifier
                    // V7: capture the toolbar Row's actual
                    // width so the popup below can match it.
                    // The popup is supposed to follow the
                    // toolbar's "displayed width" (narrow when
                    // the toolbar is collapsed — title only;
                    // wider when expanded — title + action
                    // icons), not the Surface's max width.
                    // [onSizeChanged] fires after every layout
                    // pass that changes the Row's size, so
                    // state.topToolbarExpanded toggles
                    // (which animates the action icons in/out
                    // via the inner [AnimatedVisibility])
                    // naturally propagate to the popup's
                    // width through this state.
                    .onSizeChanged { toolbarRowWidth = it.width }
                    .height(Dimens.TopToolbarHeight)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // Title-as-menu: replaces the old hamburger IconButton.
            // The user sees the scene name; tapping it shows the
            // recent files dropdown (or expands the toolbar if it's
            // currently collapsed, mirroring the previous button's
            // contract). Long-press swaps the title in place for
            // a borderless text field with the name pre-selected —
            // the user can just start typing to replace it. The
            // Box wrapper is now a no-op (its only previous use
            // was the [onGloballyPositioned] that captured
            // `titleBarBounds` for the deleted Popup anchor math)
            // but is kept here as a stable layout slot so the
            // if/else below still aligns with the rest of the
            // toolbar Row.
            Box {
                val displaySceneName = remember(state.sceneName, state.hasUnsavedChanges) {
                    SceneNaming.displayName(state.sceneName, isDirty = state.hasUnsavedChanges)
                }
                if (isEditingSceneName) {
                    // Inline edit: BORDERLESS text input that
                    // replaces the title in place. Same width
                    // cap and rounded shape as the title row so
                    // the visual transition is invisible — the
                    // title "becomes" a text field, no popup.
                    //
                    // V_NOW (bug fix v3 — "typed text STILL
                    // invisible after three rounds of
                    // OutlinedTextField color pin"):
                    //
                    // The first three rounds stayed inside M3's
                    // [OutlinedTextField]:
                    //
                    //   v1: relied on default `colors.textColor`
                    //       resolution (M3 1.2.1 token chain)
                    //   v2: pinned `textStyle.color` +
                    //       `colors.{focused,unfocused}TextColor`
                    //       to `onSurface`
                    //   v3: pinned everything to hardcoded
                    //       `Color.Black` + wrapped in a
                    //       [CompositionLocalProvider] setting
                    //       [LocalContentColor] = Black + dropped
                    //       the translucent container
                    //
                    // v3 should have worked, but the user
                    // re-tested on SM-X810 and reported "title
                    // still can't see the input characters" with
                    // a fresh screenshot showing the field at
                    // top-left as a translucent white block with
                    // only a faint cursor mark visible. Whatever
                    // M3 1.2.1's [OutlinedTextField] does
                    // internally — possibly the `BasicTextField`
                    // inside, possibly some
                    // [Surface]/[Box] z-order interaction with
                    // the parent [Surface] — is either ignoring
                    // the color pins or layering the text
                    // somewhere it doesn't render.
                    //
                    // v4: stop using [OutlinedTextField] entirely.
                    // Drop straight to
                    // [androidx.compose.foundation.text.BasicTextField]
                    // — the foundation primitive that
                    // [OutlinedTextField] is a thin M3 wrapper
                    // around. [BasicTextField] takes a
                    // [TextStyle] (with explicit color) and a
                    // [Brush] for the cursor, and that's it —
                    // no decoration box, no indicator line, no
                    // container Surface, no token chain, no
                    // MaterialTheme indirection. We add the
                    // rounded background + clickable hit area +
                    // selection highlight manually.
                    //
                    // Visual contract: same as the old
                    // [OutlinedTextField] for the user — a
                    // rounded rectangle at the title's position,
                    // typed glyphs in black on the toolbar's
                    // [surface]. No M3 chrome to fight with.
                    //
                    // The text color is hardcoded [Color.Black]
                    // to match the non-edit-mode
                    // [AutoShrinkTitle]'s black text in the
                    // user's screenshots (line ~2633) — we
                    // know THAT path renders, and we know
                    // black-on-light is readable. The
                    // toolbar's `surface` is light in the
                    // user's light-mode theme, so this works.
                    // (Dark mode would be a separate fix; the
                    // user is testing in light.)
                    Box(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .height(36.dp)
                            .background(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .focusRequester(titleFocusRequester)
                            .onFocusChanged { focusState ->
                                // Tap on canvas / focus loss = CANCEL.
                                // The [state.sceneName] stays as-is, so
                                // any unsaved typing is discarded when
                                // the editor closes. The 200ms grace
                                // window after session start absorbs
                                // the focus-settling race in the
                                // requestFocus path — without it, the
                                // first focus-change callback (reported
                                // as lost-focus before the request
                                // completes) would immediately close
                                // the editor on a fresh long-press.
                                //
                                // Note: [editSessionStartMs] is still
                                // local-state at the call site here,
                                // but reads through [Long] parameter
                                // — passes the wall-clock correctly.
                                if (!focusState.isFocused && isEditingSceneName) {
                                    val sessionAge = System.currentTimeMillis() - editSessionStartMsVar.longValue
                                    if (sessionAge >= 200L) {
                                        onEditDismiss()
                                    }
                                }
                            },
                    ) {
                        BasicTextField(
                            value = editingSceneValueVar.value,
                            onValueChange = { editingSceneValueVar.value = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = Color.Black,
                            ),
                            cursorBrush = SolidColor(Color.Black),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onSceneNameCommitted(editingSceneValueVar.value.text.trim())
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    } else {
                        // [rememberUpdatedState] is critical here. The
                        // [pointerInput] block is keyed on [state.sceneName],
                        // so it only re-launches when the name changes.
                        // The toolbar's auto-collapse timer flips
                        // [state.topToolbarExpanded] without changing the
                        // name — without [rememberUpdatedState], the
                        // lambda below would see a stale
                        // [topToolbarExpanded] = true and always go to
                        // the "show recent files" branch, never the
                        // "expand toolbar" branch. (This was the bug the
                        // user reported — click title when collapsed
                        // showed the dropdown instead of expanding.)
                        val currentState by rememberUpdatedState(state)
                        val currentOnAction by rememberUpdatedState(onAction)
                        // V2: the title's clickable is now on
                        // the WHOLE Row (not just the Text),
                        // and the chevron is wrapped in a
                        // 40×40dp touch box (not the raw
                        // 18dp icon). The user's reported
                        // issue was that tapping the padded
                        // area next to the title text was a
                        // dead zone (the Row has padding
                        // 10/6 but the clickable only
                        // covered the Text's content box),
                        // and the chevron's hit target was
                        // 18dp = ~25px on the SM-X810 — too
                        // small to reliably tap. Putting the
                        // combinedClickable on the Row and
                        // bumping the chevron's touch box to
                        // 40dp brings both up to the
                        // standard Material tap-target size.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                // V5: removed the widthIn cap.
                                // The user's V4 spec was "make
                                // the title fit", and the 480dp
                                // cap did that for most names,
                                // but the user's V5 spec is
                                // stricter: "宽度不固定,根据标题
                                // 文字内容长度自动适配,确保标题
                                // 完整显示" — width is not fixed,
                                // auto-fits to content. With the
                                // cap removed, the title group
                                // Row takes its natural width
                                // (the AutoShrinkTitle inside
                                // already shrinks the font to
                                // fit whatever width the parent
                                // gives it via BoxWithConstraints).
                                // For very long names, the
                                // shrink-to-fit cascades: shrink
                                // the font first, then if the
                                // smallest size still doesn't
                                // fit the available space, wrap
                                // to 2 lines (maxLines = 2 on
                                // the AutoShrinkTitle's Text).
                                // "高度调整:增加高度限制以完整显
                                // 示标题内容" — the toolbar Row
                                // is no longer pinned to 44dp
                                // (that was already removed in
                                // the rename-blank fix above), so
                                // a 2-line title naturally
                                // makes the toolbar taller.
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (!currentState.topToolbarExpanded) {
                                            currentOnAction(
                                                PockRefAction.SetTopToolbarExpanded(true)
                                            )
                                        }
                                        // Else: no-op (toolbar
                                        // already expanded; user
                                        // uses chevron to open the
                                        // dropdown).
                                    },
                                    onLongClick = {
                                        // Set the session-start
                                        // timestamp synchronously
                                        // here, BEFORE the state
                                        // change, so the first
                                        // onFocusChanged callback
                                        // (which fires before any
                                        // LaunchedEffect coroutine
                                        // runs) sees a non-zero
                                        // sessionAge. Without this,
                                        // sessionAge = currentTime
                                        // - 0 is huge and the
                                        // editor's 200ms grace
                                        // window check instantly
                                        // closes it.
                                        editSessionStartMsVar.longValue =
                                            System.currentTimeMillis()
                                        onEditModeChanged(
                                            true,
                                            TextFieldValue(
                                                text = state.sceneName,
                                                selection = TextRange(
                                                    0,
                                                    state.sceneName.length,
                                                ),
                                            ),
                                        )
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            // [Title text] — visual only now;
                            // the Row's combinedClickable above
                            // handles tap/long-press for the
                            // whole title region (text +
                            // padding). Before V2 this Text
                            // carried its own clickable, which
                            // left a dead zone in the Row's
                            // padding.
                            //
                            // V4: replaced the plain Text with
                            // [AutoShrinkTitle] (defined later in
                            // this file) because a 480dp cap
                            // wasn't enough for users' actual
                            // scene names. "Brand identity 2024"
                            // / "PockRef Import" fit, but
                            // "双击空白 = reset zoom 逻辑?
                            // PockRef Import" (a 30-char
                            // Chinese+ASCII mix) was still being
                            // ellipsized. Compose 1.7's BasicText
                            // doesn't expose autoSize (that's a
                            // 1.8 feature), so we implement
                            // shrink-to-fit by sampling a few
                            // font sizes in [16, 18, 20, 22, 24]
                            // and picking the largest that fits
                            // one line. Falls back to ellipsis at
                            // 16sp if nothing smaller fits (the
                            // user then knows the title is too
                            // long to fully display, instead of
                            // silently seeing a one-line version
                            // that doesn't match what they typed).
                            AutoShrinkTitle(
                                text = displaySceneName,
                                modifier = Modifier
                                    .weight(1f, fill = false),
                            )
                            // [Chevron] — separate clickable so the
                            // title text and the chevron are two
                            // independent hit targets. Tap = show
                            // recent files. The dropdown is independent
                            // of the toolbar's expanded/collapsed state
                            // (per user request): tapping the chevron
                            // always opens the menu, regardless of
                            // whether the toolbar is expanded.
                            // V6: [menuExpanded] state lives
                            // in [PockRefApp], not in this
                            // composable. The chevron's tap
                            // just dispatches a "set true" via
                            // [onMenuExpandedChange]; the
                            // back press and tap-outside
                            // scrim in [PockRefApp] own the
                            // "set false" path.
                            //
                            // V2: the clickable is now on a 40dp
                            // Box (not the raw 18dp icon). The
                            // 18dp icon was the hit target
                            // before, which on the SM-X810
                            // (340dpi) is only ~25px — too
                            // small to reliably tap. Bumping to
                            // 40dp (~136px) brings it up to the
                            // standard Material tap-target
                            // minimum.
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable(
                                        interactionSource = remember {
                                            androidx.compose.foundation.interaction.MutableInteractionSource()
                                        },
                                        indication = null,
                                    ) {
                                        // V6: state is hoisted to
                                        // [PockRefApp.menuExpanded];
                                        // this just requests the
                                        // open transition. The
                                        // BackHandler / scrim in
                                        // PockRefApp own the close
                                        // path.
                                        onMenuExpandedChange(true)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "最近文件",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
            )
        }
        // V_NOW: settings sheet rendered at [PockRefApp] level
        // (was inside [TopToolbar] before). Reads
        // [PockRefUiState.settingsSheetOpen] so multiple chrome
        // entry points can drive the same sheet — the top
        // toolbar's gear button (when restored) AND the bottom-
        // right action panel's "Settings" ListItem both
        // dispatch [PockRefAction.SetSettingsSheetOpen]. The
        // sheet's onDismissRequest also dispatches `false` to
        // close. [ModalBottomSheet] is its own window so it
        // doesn't need a BoxScope — placing it here as a
        // sibling of [BoxWithConstraints] inside the [Scaffold]
        // content lambda is the cleanest spot.
        SettingsPanel(
            open = state.settingsSheetOpen,
            onDismiss = { onAction(PockRefAction.SetSettingsSheetOpen(false)) },
            state = state,
            onAction = onAction,
        )
    }
}
            }
            AnimatedVisibility(
                visible = state.topToolbarExpanded,
                enter = expandHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = shrinkHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // V_NOW: 顶栏只保留 标题 / 撤回 / 重做 / 关闭
                    // 这几个按钮(用户决定)。Save / Import /
                    // gear(Settings) / 3-dots(MoreMenuButton) /
                    // BoxSelectToggleButton 全部下线。
                    //
                    // 撤回 / 重做:这是历史最强健的 IconButton
                    // 写法 ([HighFreqActions] 现在只剩这两个)。
                    // 关闭:从原 [SecondaryActions] 内联过来,
                    // [hasImages] 仍控制 enabled (空画布时
                    // 不能"关闭"一个不存在的 scene)。
                    HighFreqActions(
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        onAction = onAction,
                    )
                    IconButton(
                        onClick = { onAction(PockRefAction.CloseScene) },
                        enabled = state.images.isNotEmpty(),
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }
            // V6: popup content inline, sharing the toolbar
            // Surface. The AnimatedVisibility expands the
            // Surface's height downward when the user opens
            // the menu; the Surface's rounded bottom corners
            // now wrap both the toolbar Row and the popup
            // (read as one continuous block).
            //
            // The 200ms tween mirrors the action-icons
            // AnimatedVisibility above so the two expand /
            // collapse in the same time feel — no jarring
            // asymmetry when the user opens the recent-files
            // menu while the action icons are also expanding.
            AnimatedVisibility(
                visible = menuExpanded,
                enter = expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            ) {
                // V7: bind the popup's width to the toolbar
                // Row's live width (captured via
                // [onSizeChanged] above). The user spec is
                // "popup width adapts to the toolbar's
                // expanded/collapsed displayed width" — narrow
                // when only the title is shown, wider when the
                // action icons slide in. [toolbarRowWidth]
                // starts at 0 and is written on the first
                // layout pass (the toolbar is always visible,
                // so this is well before the user can open
                // the menu). The [with] conversion is recomputed
                // each time the state changes — cheap, since
                // [LocalDensity.current] doesn't change at
                // runtime.
                val popupWidthDp = with(LocalDensity.current) {
                    toolbarRowWidth.toDp()
                }
                Column(
                    modifier = Modifier.width(popupWidthDp),
                ) {
                    // V11 (item 2): section header. The
                    // "最近" label sits above the list as a
                    // non-interactive anchor — M3 menus with
                    // grouped content (Drive, Files) all do
                    // this. Padded so it has visual breathing
                    // room from the items below and the
                    // menu's top edge.
                    Text(
                        text = "最近",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 4.dp,
                        ),
                    )
                    if (state.recentScenes.isEmpty()) {
                        Text(
                            text = "无最近文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 16.dp,
                            ),
                        )
                    } else {
                        state.recentScenes.forEach { entry ->
                            // V12: removed the trailing X
                            // IconButton (per user spec —
                            // "don't need the X on each
                            // item"). The row is just leading
                            // icon + 2-line text, clickable
                            // for open.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // V11 (item 5): tighter
                                    // contentPadding. M3
                                    // default is 16h/8v — we
                                    // tighten to 12h/10v for
                                    // a denser feel that
                                    // matches the chrome
                                    // rhythm.
                                    .clickable(
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        },
                                        indication = null,
                                    ) {
                                        // V6: state is
                                        // hoisted; the
                                        // click on an item
                                        // both dispatches
                                        // the open action
                                        // and asks the
                                        // parent to close
                                        // the menu.
                                        onMenuExpandedChange(false)
                                        onAction(
                                            PockRefAction.OpenRecentScene(entry.uri)
                                        )
                                    }
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Leading icon (item 1):
                                // file / image affordance.
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                // Text column: label
                                // (bodyMedium) + timestamp
                                // (labelSmall). No
                                // .weight(1f) needed since
                                // there's no trailing close
                                // button — the text
                                // naturally fills the row's
                                // remaining horizontal
                                // space.
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatTimestamp(entry.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                }
            }
        }
    }

    // V_NOW: settings sheet rendering moved to [PockRefApp]
    // (was previously rendered here). The sheet now reads
    // [PockRefUiState.settingsSheetOpen] directly so any
    // chrome element that has [onAction] can open it without
    // threading a separate `settingsSheetOpen` remember
    // through the call graph. See the [SettingsPanel] call
    // in [PockRefApp].
}
            }
        }
    }
}

/**
 * High-frequency actions: Undo, Redo.
 * V_NOW: Save 按钮下线(顶栏简化)。函数仍然套着,因为
 * 两个 IconButton 配 [canUndo]/[canRedo] enabled 逻辑
 * 后续还可能扩展(例如加上 复制 / 粘贴)。
 * Always left-most after the leading title block so they get
 * the thumb-friendly start of the toolbar.
 */
@Composable
private fun HighFreqActions(
    canUndo: Boolean,
    canRedo: Boolean,
    onAction: (PockRefAction) -> Unit,
) {
    IconButton(
        onClick = { onAction(PockRefAction.Undo) },
        enabled = canUndo,
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize(),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Undo,
            contentDescription = "撤销",
            modifier = Modifier.size(20.dp),
        )
    }
    IconButton(
        onClick = { onAction(PockRefAction.Redo) },
        enabled = canRedo,
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize(),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Redo,
            contentDescription = "重做",
            modifier = Modifier.size(20.dp),
        )
    }
}


private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

/**
 * V_NOW (follow-up after "auto-save silent" bug): a
 * coarser-grained "X 分钟前" / "X 小时前" formatter for the
 * SettingsAutoSaveSection's "last saved" line. Absolute
 * timestamps (e.g. "2026-06-05 17:31") are exact but feel
 * cluttered inside a settings row and don't communicate
 * "this is recent" at a glance. Relative age answers the
 * only question the user actually has — "how long ago did
 * this happen?" — and makes a fresh save visibly
 * different from a stale one.
 *
 * Bucketing is 60-second granularity up to 1 hour, then
 * hours, then days, then falls back to the absolute
 * timestamp for anything older than 30 days. Negative
 * diffs (clock skew, the auto-save's `System.currentTimeMillis()`
 * is ahead of the renderer's) clamp to "刚刚" rather
 * than emitting a negative number.
 */
private fun formatRelativeAge(thenMs: Long, nowMs: Long): String {
    val diffMs = (nowMs - thenMs).coerceAtLeast(0L)
    val seconds = diffMs / 1000L
    return when {
        seconds < 5L -> "刚刚"
        seconds < 60L -> "${seconds}秒前"
        seconds < 3600L -> "${seconds / 60L}分钟前"
        seconds < 86_400L -> "${seconds / 3600L}小时前"
        seconds < 30L * 86_400L -> "${seconds / 86_400L}天前"
        else -> formatTimestamp(thenMs)
    }
}

/**
 * Content-addressed cache key for decoded bitmaps.
 *
 * The decoded bitmap depends ONLY on the raw image bytes (and a target decode
 * size which is fixed by the viewport-aware logic in [PockRefCanvas], not by
 * the [SceneImage]). Render-time transforms — `scale`, `rotationDegrees`,
 * `flipX/Y`, and `colorAdjust` — are applied in [drawSceneImage] via `dstSize`,
 * `rotate`, `scale`, and `colorFilter` and do NOT require a re-decode.
 *
 * Including any of those transform fields in the key would invalidate the
 * cache for purely visual changes (e.g. `executeAutoLayout` rewrites every
 * `image.scale` to fit a grid, which previously caused all images to fall
 * back to [drawImagePlaceholder] until each one re-decoded).
 *
 * `imageId` alone is insufficient: when `imageBytes` is replaced (e.g. after a
 * crop or re-import) the `SceneImage.id` stays the same, so an id-only cache
 * would return the stale decoded bitmap. We therefore hash the raw bytes so
 * a different payload invalidates the cache entry automatically.
 */
private fun imageFingerprint(image: SceneImage): Pair<Int, String> {
    return image.imageBytes.size to image.mimeType
}

@Composable
private fun PockRefCanvas(
    state: PockRefUiState,
    modifier: Modifier = Modifier,
    onAction: (PockRefAction) -> Unit,
    imageLazyLoader: com.pockref.app.data.PurFileParser? = null,
    imageDecoder: com.pockref.app.data.SceneImageDecoder? = null,
    // V2: hover-magnifier progress, computed at the
    // [PockRefApp] level so the same value is read by
    // both the in-canvas magnifier and the
    // sibling-overlay magnifier. 0f = hidden, 1f = fully
    // shown, in-between = fade animation in progress.
    hoverProgress: Float = 0f,
    // V2: bitmap cache lifted from this function to
    // [PockRefApp] so the sibling [HoverMagnifierOverlay]
    // can read decoded bitmaps. See the cache definition
    // in [PockRefApp] for the cache-eviction effect that
    // used to live here.
    bitmapCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Int, ImageBitmap>>,
) {
    // V2: bitmapCache is now passed in from the [PockRefApp]
    // scope (see the [PockRefApp] declaration of the same
    // variable). It used to be local to this function; lifting
    // it out lets the sibling [HoverMagnifierOverlay]
    // Composable read the decoded bitmaps from the same
    // cache so the overlay doesn't have to re-decode the
    // image (and so the cache eviction DisposableEffect
    // continues to fire from one owner).
    //
    // V_NOW: Cache is now imageId → (decodeLevel, ImageBitmap).
    // decodeLevel is an index into SceneImageDecoder.DECODE_LADDER.
    // Higher level = higher quality. The draw loop always uses
    // the highest-quality cached entry for each imageId.
    @Suppress("NAME_SHADOWING")
    val bitmapCache = bitmapCache
    // Capture the screen density once per recomposition. The draw scope's
    // `size` is in raw pixels; multiplying by density keeps the hover-
    // magnifier budget consistent across DPI buckets (see the DPI note
    // in [drawHoverMagnifier]).
    val density = LocalDensity.current.density
    // Drag-to-edge auto-scroll state. The pointerInput's
    // awaitEachGesture block runs inside Compose's
    // @RestrictsSuspension AwaitPointerEventScope, which only
    // allows calling member/extension suspending functions
    // (i.e. awaitPointerEvent). Spawning a child coroutine
    // there via coroutineScope { launch { ... } } is a
    // compile error. So the auto-scroll worker lives in a
    // [LaunchedEffect] at this level (the unrestricted
    // composable scope) and reads the latest pointer position
    // + eligible flag via two mutableStateOf-backed slots
    // that the gesture loop writes into on each event. The
    // worker is "always running" but is a no-op when the
    // flag is false, so the cost is one State read per
    // 16ms tick.
    //
    // V2: 64dp zone / 18dp-per-tick cap = ~215px / 60px
    // per tick at 340dpi. The tick-cap is the per-16ms
    // scroll delta; the per-tick speed × tick rate gives
    // the max continuous scroll speed (~3600 px/s).
    val autoScrollEdgeZoneDp = 64.dp
    val autoScrollMaxSpeedDp = 18.dp
    val autoScrollTickMs = 16L
    val autoScrollEdgeZonePx = with(LocalDensity.current) { autoScrollEdgeZoneDp.toPx() }
    val autoScrollMaxSpeedPx = with(LocalDensity.current) { autoScrollMaxSpeedDp.toPx() }
    val lastPointerPosState = remember { mutableStateOf(Offset.Zero) }
    val autoScrollEligibleState = remember { mutableStateOf(false) }
    val autoScrollViewportSize = remember { mutableStateOf(Size.Zero) }
    // V3: auto-scroll worker also feeds the active drag.
    // The user's reported bug was that the canvas panned
    // under a stationary image when the finger sat in
    // the edge zone — the canvas moved but the image
    // stayed at the screen position it was first
    // dragged to. The fix: when the auto-scroll worker
    // dispatches PanCanvas(scroll), it ALSO dispatches
    // UpdateImageDrag(scroll) for every currently-dragged
    // image. The image's center moves by `scroll` in
    // world units (= screen units / canvasScale, but
    // UpdateImageDrag takes SCREEN units and the
    // reducer multiplies by 1/canvasScale — so passing
    // `scroll` as-is is correct). Net effect: the
    // image keeps its screen-relative position, and
    // the new scene area scrolls into view. The user
    // can keep dragging the image into the new area
    // without it being "left behind".
    LaunchedEffect(Unit) {
        while (true) {
            if (autoScrollEligibleState.value) {
                val pos = lastPointerPosState.value
                val viewport = autoScrollViewportSize.value
                if (viewport.width > 0f && viewport.height > 0f) {
                    val scroll = computeEdgeAutoScroll(
                        pos = pos,
                        viewport = viewport,
                        edgeZonePx = autoScrollEdgeZonePx,
                        maxSpeedPx = autoScrollMaxSpeedPx,
                    )
                    if (scroll != Offset.Zero) {
                        onAction(PockRefAction.PanCanvas(scroll))
                        // V3: also nudge every actively-
                        // dragged image by the SAME amount
                        // so it follows the canvas. We read
                        // [state.transientDragImageIds]
                        // (set by the move loop via
                        // BeginImageDrag) — when the gesture
                        // is a box-select or lasso the set
                        // is empty so this is a no-op and
                        // only the canvas pans, which is
                        // the right behavior (you wouldn't
                        // expect the box-select rectangle
                        // to follow a canvas scroll).
                        val draggedIds = state.transientDragImageIds
                        for (id in draggedIds) {
                            onAction(
                                PockRefAction.UpdateImageDrag(scroll),
                            )
                        }
                    }
                }
            }
            delay(autoScrollTickMs)
        }
    }
    // ── Image decode pipeline (replaces old LRU + LaunchedEffect) ──
    //
    // The old pipeline used BitmapCacheKey(imageId, DecodeLevel, fingerprint)
    // with a LinkedHashMap LRU and two duplicate LaunchedEffects.
    // The new pipeline uses ImageDecodePipeline with SceneImageDecoder:
    // - No temp files (BitmapRegionDecoder)
    // - Disk cache for thumbnails (WebP, 64MB)
    // - Zoom-aware decode ladder (128→256→512→1024→2048→4096)
    // - Streaming callbacks (no awaitAll blocking)
    val pipeline = remember(imageDecoder) {
        val decoder = imageDecoder ?: com.pockref.app.data.SceneImageDecoder(null)
        com.pockref.app.data.ImageDecodePipeline(decoder)
    }

    DisposableEffect(pipeline) {
        pipeline.onBitmapReady = { imageId, level, bitmap ->
            val current = bitmapCache[imageId]
            // Only replace if level is higher (never downgrade)
            if (current == null || level > current.first) {
                bitmapCache[imageId] = level to bitmap.asImageBitmap()
            }
        }
        onDispose {
            pipeline.onBitmapReady = null
            pipeline.onBatchComplete = null
        }
    }

    // Cache eviction: when over 300 entries, evict lowest-level first
    DisposableEffect(bitmapCache.size) {
        if (bitmapCache.size > 300) {
            val toRemove = bitmapCache.entries
                .sortedBy { it.value.first }
                .take(bitmapCache.size - 250)
                .map { it.key }
            toRemove.forEach { bitmapCache.remove(it) }
        }
        onDispose { }
    }

    val currentState by rememberUpdatedState(state)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentLoaderRef by rememberUpdatedState(imageLazyLoader)
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    // Track empty-canvas taps separately from the existing
    // lastTap* (which is used to detect double-tap-to-fit on a
    // selected image). Resetting on every tap means an empty
    // double-tap and an image double-tap share no state — a
    // user who taps image, empty, empty still gets the reset
    // zoom on the second empty. Same 400ms / 20px tolerances.
    var lastEmptyTapTime by remember { mutableLongStateOf(0L) }
    var lastEmptyTapPos by remember { mutableStateOf(Offset.Zero) }

    // V2: hoverProgress is now passed in as a parameter
    // from the [PockRefApp] scope (see the function
    // signature). It used to be computed here, but
    // lifting it out lets the sibling-overlay magnifier
    // read the same value so the two paths animate in
    // lockstep. The label has been renamed from
    // "spen-hover-magnifier" (an earlier name) to
    // "hover-magnifier" since the same value now drives
    // both the in-canvas draw and the overlay.

    DisposableEffect(state.images) {
        val currentIds = state.images.map { it.id }.toSet()
        bitmapCache.keys.filterNot { it in currentIds }.forEach { key ->
            bitmapCache.remove(key)
        }
        onDispose {}
    }

    // ── Decode effects (replaces old loadingImages + decodedFingerprints + two LaunchedEffects) ──

    // Stable sort of the scene's images by z-index, reused across draw
    // frames while [state.images] is reference-equal. Recomputes only when
    // an image is added/removed/reordered (e.g. bringToFront / sendToBack
    // / import), not on every state change.
    val sortedImages = remember(state.images) { state.images.sortedBy { it.zIndex } }

    // Union of "ids we must draw even if off-viewport": selected, in-progress
    // box-select previews, and transient-drag targets.
    val forcedDrawIds = remember(
        state.selectedImageIds,
        state.boxSelectPreviewIds,
        state.transientDragImageIds,
    ) {
        buildSet {
            addAll(state.selectedImageIds)
            addAll(state.boxSelectPreviewIds)
            addAll(state.transientDragImageIds)
        }
    }

    // Scene-changed effect: load all images that aren't cached
    LaunchedEffect(state.images.map { Triple(it.id, it.imageBytes.size, it.mimeType) }) {
        val s = currentState
        val viewport = worldViewportRect(
            canvasOffset = s.canvasOffset,
            canvasScale = s.canvasScale,
            viewportWidth = s.canvasViewportSize.width,
            viewportHeight = s.canvasViewportSize.height,
        )
        pipeline.onSceneChanged(
            images = s.images,
            viewportRect = android.graphics.RectF(viewport.left, viewport.top, viewport.right, viewport.bottom),
            canvasScale = s.canvasScale,
            cachedImageIds = bitmapCache.keys.toSet(),
        ) { image ->
            val displayW = (image.displaySize.width * s.canvasScale).toInt().coerceAtLeast(1)
            val displayH = (image.displaySize.height * s.canvasScale).toInt().coerceAtLeast(1)
            displayW to displayH
        }
    }

    // Viewport-changed effect: decode newly-visible images + upgrade zoom levels
    LaunchedEffect(state.canvasOffset, state.canvasScale, state.images) {
        val s = currentState
        val viewport = worldViewportRect(
            canvasOffset = s.canvasOffset,
            canvasScale = s.canvasScale,
            viewportWidth = s.canvasViewportSize.width,
            viewportHeight = s.canvasViewportSize.height,
        )
        if (viewport.width <= 0f || viewport.height <= 0f) return@LaunchedEffect
        val cachedLevels = bitmapCache.mapValues { it.value.first }
        pipeline.onViewportChanged(
            images = s.images,
            viewportRect = android.graphics.RectF(viewport.left, viewport.top, viewport.right, viewport.bottom),
            canvasScale = s.canvasScale,
            cachedImageIds = bitmapCache.keys.toSet(),
            cachedLevels = cachedLevels,
        ) { image ->
            val displayW = (image.displaySize.width * s.canvasScale).toInt().coerceAtLeast(1)
            val displayH = (image.displaySize.height * s.canvasScale).toInt().coerceAtLeast(1)
            displayW to displayH
        }
    }

    // Auto-clear the post-commit snap highlight after its fade window.
    // Keyed on the highlight's startTimeMs so a fresh highlight restarts
    // the timer, and clearing the highlight (e.g. by starting a new
    // gesture via beginImageDrag) cancels the pending dispatch.
    val snapHighlightStart = state.snapHighlight?.startTimeMs
    val localView = androidx.compose.ui.platform.LocalView.current
    // V3 S Pen debug: install a raw-MotionEvent listener on
    // the underlying View so logcat captures the raw
    // buttonState regardless of whether Compose's
    // PointerEvent.buttons is dropping the bit. The
    // listener returns false so it never consumes —
    // Compose's pointerInput still receives every event
    // for its own handling. When this DisposableEffect
    // leaves the composition (or PockRefCanvas
    // recomposes) the original touch listener is
    // restored, so we never leak a hook on the root
    // view.
    androidx.compose.runtime.DisposableEffect(localView) {
        val previous = localView.setOnTouchListener { _, motionEvent ->
            val toolType = motionEvent.getToolType(0)
            val buttonState = motionEvent.buttonState
            val isStylus = toolType == android.view.MotionEvent.TOOL_TYPE_STYLUS
            if (isStylus && (motionEvent.actionMasked == android.view.MotionEvent.ACTION_DOWN ||
                    motionEvent.actionMasked == android.view.MotionEvent.ACTION_MOVE)
            ) {
                android.util.Log.i(
                    "PockRefSpen",
                    "raw action=${android.view.MotionEvent.actionToString(motionEvent.actionMasked)} " +
                        "toolType=$toolType " +
                        "buttonState=$buttonState " +
                        "isStylus=$isStylus " +
                        "primary=${(buttonState and android.view.MotionEvent.BUTTON_PRIMARY) != 0} " +
                        "secondary=${(buttonState and android.view.MotionEvent.BUTTON_SECONDARY) != 0} " +
                        "stylusPrimary=${(buttonState and android.view.MotionEvent.BUTTON_STYLUS_PRIMARY) != 0} " +
                        "stylusSecondary=${(buttonState and android.view.MotionEvent.BUTTON_STYLUS_SECONDARY) != 0} " +
                        "tertiary=${(buttonState and android.view.MotionEvent.BUTTON_TERTIARY) != 0}",
                )
            }
            // Return false → don't consume; let Compose
            // handle the event normally.
            false
        }
        onDispose {
            // Restore the previous listener (or null if
            // there was none). setOnTouchListener accepts
            // null to clear, which is what we want on
            // dispose.
            localView.setOnTouchListener(previous?.let { { _, _ -> false } })
        }
    }
    LaunchedEffect(snapHighlightStart) {
        if (snapHighlightStart != null) {
            // V5: small haptic "thunk" when a snap actually commits
            // — gives the user a tactile "yeah that locked in"
            // confirmation. Uses a light constant (not the heavy
            // long-press) so it doesn't feel like an error. This
            // fires on every commit, not only corner snaps, because
            // the user spec asks for "吸附成功后提供轻微的吸附阻
            // 力感反馈" (a "阻力感" = resistance feel after snap
            // success) and we want consistency: every successful
            // snap gets the same confirmation.
            try {
                @Suppress("DEPRECATION")
                localView?.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                )
            } catch (_: Throwable) {
                // Some devices / configurations throw on haptic
                // calls; treat as best-effort.
            }
            kotlinx.coroutines.delay(SNAP_HIGHLIGHT_DURATION_MS + 50L)
            currentOnAction(PockRefAction.ClearSnapHighlight)
        }
    }

    // V_NOW: pre-compute the locked-image indicator colours in the
    // Composable scope so the DrawScope block below can reference
    // them by closure capture. Two layers, matching the
    // selection-outline style (an outer semi-transparent glow
    // stroke + an inner solid stroke) so the locked indicator
    // reads as the same family of affordance as the selection
    // rectangle — just a different colour and always-on vs
    // on-select.
    //
    // The inner stroke is the [selection] blue's counterpart: a
    // red that lives in [Color] hex (not a theme token) so the
    // "warning / danger" semantic is consistent across light and
    // dark themes — swapping to a [colorScheme.error] variant
    // would make the locked indicator hue depend on the user's
    // theme, which we explicitly do NOT want here (the lock
    // affordance is "this image is protected", not "this is an
    // error in the UI").
    val lockedStrokeColor = LockedStrokeColor
    val lockedGlowColor = LockedGlowColor

    Canvas(
        modifier = modifier
            .background(state.backgroundColor)
            .onSizeChanged { canvasSize ->
                onAction(PockRefAction.UpdateCanvasViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat()))
                // Mirror the viewport into a local
                // mutableStateOf so the drag-to-edge
                // auto-scroll worker (a sibling
                // [LaunchedEffect]) can read the latest
                // size without depending on the
                // Reducer round-trip — important when
                // the screen rotates mid-gesture and the
                // worker's cached viewport would
                // otherwise be one frame stale.
                autoScrollViewportSize.value = Size(
                    canvasSize.width.toFloat(),
                    canvasSize.height.toFloat(),
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Use awaitPointerEvent (not awaitFirstDown) so
                    // we can read [PointerEvent.buttons] for the S
                    // Pen side-button state. Compose 1.7's
                    // PointerInputChange doesn't expose a `buttons`
                    // field; the bitmask lives on the event itself
                    // (see compose-ui PointerEvent.android.kt).
                    //
                    // Loop until we see an event that contains a
                    // real down transition (mirrors [awaitFirstDown]'s
                    // semantics so a stale move event from a
                    // previous gesture doesn't get treated as a
                    // fresh down).
                    var firstEvent: PointerEvent
                    var down: PointerInputChange
                    while (true) {
                        firstEvent = awaitPointerEvent(PointerEventPass.Main)
                        val downChange = firstEvent.changes.firstOrNull {
                            it.changedToDownIgnoreConsumed()
                        }
                        if (downChange != null) {
                            down = downChange
                            break
                        }
                    }
                    down.consume()
                    val downPos = down.position
                    val downTime = System.currentTimeMillis()
                    val s = currentState
                    val hitId = hitTestImage(s, downPos, Size(size.width.toFloat(), size.height.toFloat()))

                    var activeDragImageId: String? = null
                    var dragCanvas = false
                    var lassoActive = false
                    var lassoStart: Offset? = null
                    var boxSelectStart: Offset? = null
                    var boxSelectDragging = false

                    // S Pen button binding. The side button on the
                    // Galaxy Tab S Pen reports as `PointerType.Stylus`
                    // with `event.buttons.isSecondaryPressed == true`
                    // while held. Mapping (per the user's spec):
                    //   * held on touchdown         → BOX (drag a rectangle)
                    //   * released mid-stroke       → MULTI (lasso)
                    //   * double-tap empty canvas   → SINGLE (exit)
                    // The `stylusButtonDown` flag latches the press
                    // and the move loop watches for the release via
                    // the event's `buttons.isSecondaryPressed` going
                    // false.
                    val downIsStylus = down.type == PointerType.Stylus
                    // V3: try multiple detection paths. The
                    // SM-X810 (Samsung fork) may not
                    // propagate the side-button state to
                    // Compose's PointerButtons in some
                    // Android versions. Compose's
                    // PointerEvent has `motionEvent: MotionEvent?`
                    // but it's `internal` (not accessible
                    // from app code), so we can't read the
                    // raw `buttonState` directly. We can
                    // only check the exposed `event.buttons`
                    // extension getters. We try:
                    //   1. event.buttons.isSecondaryPressed
                    //      (standard stylus side button)
                    //   2. event.buttons.isTertiaryPressed
                    //      (some Samsungs use TERTIARY for
                    //      stylus side button — empirical)
                    // If neither fires, the raw button
                    // state isn't reaching Compose and we
                    // need a different input pipeline (see
                    // android-compose.md memory entry on
                    // a11y onMotionEvent for the
                    // alternatives).
                    val stylusButtonDown = downIsStylus && (
                        firstEvent.buttons.isSecondaryPressed ||
                            firstEvent.buttons.isTertiaryPressed
                    )
                    // V2 debug: the user reported the S Pen
                    // side button "doesn't fire" — i.e. the
                    // BOX/MULTI/SINGLE transition doesn't
                    // happen when the button is pressed.
                    // Log every down event with the
                    // relevant fields so a logcat capture
                    // can confirm which detection path is
                    // (or isn't) firing.
                    android.util.Log.i(
                        "PockRefSpen",
                        "down type=${down.type} " +
                            "primary=${firstEvent.buttons.isPrimaryPressed} " +
                            "secondary=${firstEvent.buttons.isSecondaryPressed} " +
                            "tertiary=${firstEvent.buttons.isTertiaryPressed} " +
                            "isStylus=$downIsStylus " +
                            "buttonDown=$stylusButtonDown",
                    )

                    if (stylusButtonDown) {
                        // Force BOX mode for this gesture. The down
                        // might have hit an image — we ignore the hit
                        // because the S Pen button is for selection,
                        // not image drag.
                        if (!s.boxSelectMode) {
                            currentOnAction(PockRefAction.SetSelectionMode(SelectionMode.BOX))
                        }
                        currentOnAction(PockRefAction.BeginBoxSelect)
                        boxSelectStart = downPos
                        boxSelectDragging = true
                    } else if (hitId != null) {
                        if (s.boxSelectMode && hitId !in s.selectedImageIds) {
                            currentOnAction(PockRefAction.BeginBoxSelect)
                            boxSelectStart = downPos
                            boxSelectDragging = true
                        } else {
                            // V10: skip the drag for locked images.
                            // [SceneImage.locked] is supposed to make
                            // the image "ignore drag / pinch / rotate
                            // / lasso input" (see the field comment in
                            // [SceneImage]), but the previous code
                            // set [activeDragImageId] unconditionally,
                            // so the user's report was that locking
                            // an image had no effect — tapping on it
                            // still started a drag, still moved the
                            // image, still committed the new position
                            // on lift. The state changed, but the
                            // behavior didn't. We still let the tap
                            // through (so the user can select a
                            // locked image to inspect / unlock it),
                            // we just don't arm the drag for it.
                            //
                            // Box-select case: the locked image is
                            // included in the selection when the
                            // user's drag rectangle covers it; the
                            // lock is about *interaction*, not
                            // *visibility*, so it's still pickable.
                            // Only direct drag is blocked.
                            val hitImage = s.images.firstOrNull { it.id == hitId }
                            if (hitImage?.locked != true) {
                                activeDragImageId = hitId
                            }
                        }
                    } else if (s.boxSelectMode) {
                        currentOnAction(PockRefAction.BeginBoxSelect)
                        boxSelectStart = downPos
                        boxSelectDragging = true
                    } else if (s.multiSelectMode) {
                        lassoActive = true
                        lassoStart = downPos
                    } else {
                        dragCanvas = true
                    }

                    var transformMode = ImageTransformMode.NONE
                    var previousDistance = 0f
                    var previousAngle = 0f
                    var initialZoom = 1f
                    var initialRotation = 0f
                    // Slide-mode tracking. initialCentroidY is the y of
                    // the two-finger centroid at gesture start, so we
                    // can compute per-frame deltas in the same way the
                    // existing pinch/rotate path computes cumulative
                    // zoom/rotation. totalSlideY is the running
                    // accumulator used to decide whether the gesture
                    // has committed to SLIDE yet (see [SLIDE_THRESHOLD]).
                    var initialCentroidY = 0f
                    var totalSlideY = 0f
                    var previousCentroidY = 0f
                    var transformStarted = false
                    var dragStarted = false
                    var totalDrag = Offset.Zero
                    // Drag-to-edge auto-scroll handoff. The
                    // move loop is the authoritative source
                    // for the pointer position during this
                    // gesture; the auto-scroll worker (a
                    // [LaunchedEffect] at the
                    // [PockRefCanvas] level) just reads
                    // the latest value. Eligible only for
                    // image drag / box-select / lasso —
                    // not for canvas pan ([dragCanvas]),
                    // since the user is already driving
                    // the canvas with their finger and an
                    // auto-scroll on top would double the
                    // pan speed.
                    lastPointerPosState.value = downPos
                    autoScrollEligibleState.value = false
                    // Long-press detection. While the finger is
                    // stationary and the gesture hasn't otherwise
                    // committed (drag, transform, lasso, etc.), we
                    // arm a timer; if it fires we pop the context
                    // menu for the image under the down position.
                    // [lastMoveTimeMs] is the timestamp of the most
                    // recent event where the finger moved more than
                    // [LONG_PRESS_SLOP_PX] — "stationary" means
                    // (now - lastMoveTimeMs) > threshold. [longPressFired]
                    // is a one-shot latch so a single hold triggers at
                    // most one menu.
                    var lastMoveTimeMs = downTime
                    var longPressFired = false
                    // Multi-finger tap detection. [peakPointerCount]
                    // is the max simultaneous fingers during this
                    // gesture (used to distinguish 2-finger tap
                    // from 3-finger tap on release). [gestureMoved]
                    // is the "any finger crossed touch slop" flag —
                    // once true the gesture is a transform / drag,
                    // not a tap. [gestureAnyPressed] is needed
                    // because the do-while loop also drives the
                    // release path; it mirrors the existing
                    // `changes.any { it.pressed }` exit check.
                    var peakPointerCount = 1
                    var gestureMoved = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val changes = event.changes
                        val pointerCount = changes.size

                        // Update multi-finger-tap tracking. We
                        // also need to detect movement in events
                        // that don't go through the single-finger
                        // path (e.g. a 2-finger down event has
                        // changes.size == 2 with per-change
                        // previousPosition == position, so it's
                        // stationary by itself). The dominant-axis
                        // threshold mirrors the 8px used by
                        // [isTap] for single-finger — anything
                        // past that is a transform / drag, not a
                        // tap.
                        if (pointerCount > peakPointerCount) {
                            peakPointerCount = pointerCount
                        }
                        if (!gestureMoved) {
                            gestureMoved = changes.any { change ->
                                (change.position - change.previousPosition).getDistance() > 4f
                            }
                        }

                        if (pointerCount >= 2) {
                            if (activeDragImageId != null && dragStarted) {
                                currentOnAction(PockRefAction.CommitImageDrag)
                            } else if (activeDragImageId != null) {
                                currentOnAction(PockRefAction.CancelImageDrag)
                            }
                            activeDragImageId = null
                            dragCanvas = false
                            lassoActive = false

                            val p0 = changes[0].position
                            val p1 = changes[1].position
                            val centroid = (p0 + p1) / 2f
                            val distance = (p0 - p1).getDistance()
                            val angle = atan2(p1.y - p0.y, p1.x - p0.x)

                            if (!transformStarted) {
                                previousDistance = distance
                                previousAngle = angle
                                initialZoom = 1f
                                initialRotation = 0f
                                initialCentroidY = centroid.y
                                previousCentroidY = centroid.y
                                totalSlideY = 0f
                                transformStarted = true
                                transformMode = ImageTransformMode.NONE
                                currentOnAction(PockRefAction.BeginSceneGesture)
                                changes.forEach { it.consume() }
                                continue
                            }

                            val distanceDelta = if (previousDistance > 0f) distance / previousDistance else 1f
                            val angleDelta = angle - previousAngle
                            val normalizedAngleDelta = if (angleDelta > PI.toFloat()) angleDelta - 2f * PI.toFloat()
                                else if (angleDelta < -PI.toFloat()) angleDelta + 2f * PI.toFloat()
                                else angleDelta
                            // Per-frame centroid Y delta — used by the
                            // SLIDE (canvas-zoom) path. Signed: negative =
                            // fingers slid up, positive = slid down.
                            val centroidDeltaY = centroid.y - previousCentroidY

                            initialZoom *= distanceDelta
                            initialRotation += normalizedAngleDelta
                            totalSlideY += centroidDeltaY

                            if (transformMode == ImageTransformMode.NONE) {
                                val zoomDelta = abs(initialZoom - 1f) * 100f
                                val rotationDelta = abs(initialRotation) * 180f / PI.toFloat()
                                val slideDelta = abs(totalSlideY)

                                // SLIDE wins when the cumulative vertical
                                // centroid travel is the dominant motion
                                // and clear of the threshold. It has to
                                // beat both the scale and rotation
                                // accumulators in magnitude, otherwise
                                // we are still in a pinch/rotate attempt
                                // and the SLIDE branch would steal a
                                // gesture that was about to become one of
                                // those.
                                if (slideDelta >= SLIDE_THRESHOLD &&
                                    slideDelta >= zoomDelta &&
                                    slideDelta >= rotationDelta
                                ) {
                                    transformMode = ImageTransformMode.SLIDE
                                } else if (zoomDelta >= SCALE_THRESHOLD && zoomDelta >= rotationDelta) {
                                    transformMode = ImageTransformMode.SCALE
                                } else if (rotationDelta >= ROTATE_THRESHOLD) {
                                    transformMode = ImageTransformMode.ROTATE
                                }
                            }

                            val hasSelected = currentState.selectedImageId != null

                            when (transformMode) {
                                ImageTransformMode.SLIDE -> {
                                    // Two-finger vertical slide = canvas
                                    // zoom. Centroid moves up = zoom in,
                                    // down = zoom out. The sensitivity is
                                    // tuned by [SLIDE_ZOOM_PER_PX]. Note
                                    // that this dispatches regardless of
                                    // selection state — the whole point of
                                    // SLIDE is to give the user a way to
                                    // zoom the canvas even when an image
                                    // is selected (the existing
                                    // pinch/rotate handlers then route to
                                    // the image instead).
                                    //
                                    // V6: focus on the canvas geometric
                                    // center (not the gesture centroid),
                                    // per user spec — keeps the canvas
                                    // center pixel fixed regardless of
                                    // where the fingers are on screen.
                                    // `autoScrollViewportSize` is mirrored
                                    // from `onSizeChanged` so we always
                                    // have the latest viewport dimensions
                                    // without a Reducer round-trip.
                                    if (centroidDeltaY != 0f) {
                                        val vp = autoScrollViewportSize.value
                                        // Guard against first-frame zero
                                        // size — fall back to centroid
                                        // so the zoom still feels right
                                        // (degrades gracefully, no crash).
                                        val focus = if (vp.width > 0f && vp.height > 0f) {
                                            Offset(vp.width / 2f, vp.height / 2f)
                                        } else {
                                            centroid
                                        }
                                        val scaleFactor = 1f - centroidDeltaY * SLIDE_ZOOM_PER_PX
                                        currentOnAction(PockRefAction.ZoomCanvas(scaleFactor, focus))
                                    }
                                }
                                ImageTransformMode.SCALE -> {
                                    if (hasSelected) {
                                        if (distanceDelta != 1f) {
                                            currentOnAction(PockRefAction.TransformSelectedImage(distanceDelta, 0f, Offset.Zero))
                                        }
                                    } else {
                                        if (distanceDelta != 1f) {
                                            currentOnAction(PockRefAction.ZoomCanvas(distanceDelta, centroid))
                                        }
                                    }
                                }
                                ImageTransformMode.ROTATE -> {
                                    if (hasSelected) {
                                        if (normalizedAngleDelta != 0f) {
                                            val rotationDeg = (normalizedAngleDelta * 180f / PI).toFloat()
                                            currentOnAction(PockRefAction.TransformSelectedImage(1f, rotationDeg, Offset.Zero))
                                        }
                                    } else {
                                        if (distanceDelta != 1f) {
                                            currentOnAction(PockRefAction.ZoomCanvas(distanceDelta, centroid))
                                        }
                                    }
                                }
                                else -> { }
                            }

                            previousDistance = distance
                            previousAngle = angle
                            previousCentroidY = centroid.y
                            changes.forEach { it.consume() }
                        } else if (pointerCount == 1) {
                            if (transformStarted) {
                                currentOnAction(PockRefAction.EndSceneGesture)
                                transformStarted = false
                                transformMode = ImageTransformMode.NONE
                                initialZoom = 1f
                                initialRotation = 0f
                                totalSlideY = 0f
                                previousCentroidY = 0f
                            }

                            val change = changes[0]
                            val dragAmount = change.position - change.previousPosition
                            change.consume()

                            totalDrag += dragAmount

                            // S Pen button release mid-stroke. If the
                            // gesture was started with the button held
                            // (so we're in box-select) and the button
                            // is now released, switch to lasso for the
                            // rest of the gesture. The local
                            // [boxSelectDragging] / [lassoActive]
                            // flags are toggled immediately so the
                            // move-handler branches below pick up
                            // the new state on the SAME frame; the
                            // dispatched SetSelectionMode(MULTI)
                            // updates the canonical state for any
                            // other consumer (e.g. the toolbar
                            // button's selected indicator).
                            if (stylusButtonDown && boxSelectDragging) {
                                // V3: same multi-path button-still-down
                                // check as the down handler. Compose's
                                // raw MotionEvent is `internal` so we
                                // can't read buttonState directly; we
                                // OR the two extension getters.
                                val buttonStillDown = event.buttons.isSecondaryPressed ||
                                    event.buttons.isTertiaryPressed
                                if (!buttonStillDown) {
                                    currentOnAction(
                                        PockRefAction.SetSelectionMode(SelectionMode.MULTI),
                                    )
                                    boxSelectDragging = false
                                    lassoActive = true
                                    lassoStart = change.position
                                }
                            }

                            // Long-press watchdog. While the user
                            // is holding a finger still, we count
                            // time-since-last-meaningful-move. If it
                            // crosses the threshold and we haven't
                            // already latched a menu, dispatch
                            // [ShowContextMenu] for the image under
                            // the original touch (not the current
                            // finger position, since they may have
                            // drifted a couple px). Cancelled
                            // implicitly by anything that commits
                            // the gesture (drag, transform, lasso)
                            // — once one of those is true the
                            // context-menu won't fire.
                            if (!longPressFired) {
                                val nowMs = change.uptimeMillis
                                val movedThisFrame = dragAmount.getDistance() > LONG_PRESS_SLOP_PX
                                if (movedThisFrame) {
                                    lastMoveTimeMs = nowMs
                                } else if (
                                    nowMs - lastMoveTimeMs >= LONG_PRESS_THRESHOLD_MS &&
                                    !dragStarted && !lassoActive && !boxSelectDragging
                                ) {
                                    longPressFired = true
                                    val s = currentState
                                    val tapId = hitTestImage(
                                        s,
                                        downPos,
                                        Size(size.width.toFloat(), size.height.toFloat()),
                                    )
                                    val target = if (tapId != null) {
                                        // If the long-pressed image is
                                        // not part of the existing
                                        // selection, switch selection to
                                        // just this image (mirrors tap-
                                        // to-select behavior).
                                        if (s.multiSelectMode && tapId in s.selectedImageIds) {
                                            s.selectedImageIds
                                        } else {
                                            setOf(tapId)
                                        }
                                    } else {
                                        s.selectedImageIds
                                    }
                                    if (target.isNotEmpty()) {
                                        currentOnAction(
                                            PockRefAction.ShowContextMenu(downPos, target)
                                        )
                                    }
                                }
                            }

                            val dragId = activeDragImageId
                            if (dragId != null) {
                                if (!dragStarted && totalDrag.getDistance() > 4f) {
                                    val alreadySelected = dragId in currentState.selectedImageIds
                                    if (!alreadySelected) {
                                        currentOnAction(PockRefAction.SelectImage(dragId))
                                    }
                                    currentOnAction(PockRefAction.BeginImageDrag(dragId))
                                    dragStarted = true
                                }
                                if (dragStarted) {
                                    currentOnAction(PockRefAction.UpdateImageDrag(Offset(dragAmount.x, dragAmount.y)))
                                }
                            } else if (boxSelectDragging && boxSelectStart != null) {
                                val start = boxSelectStart!!
                                val cur = change.position
                                val left = minOf(start.x, cur.x)
                                val top = minOf(start.y, cur.y)
                                val right = maxOf(start.x, cur.x)
                                val bottom = maxOf(start.y, cur.y)
                                currentOnAction(PockRefAction.UpdateBoxSelectRect(RectF(left, top, right, bottom)))
                            } else if (lassoActive) {
                                lassoStart = lassoStart?.let { ls ->
                                    Offset(
                                        minOf(ls.x, change.position.x),
                                        minOf(ls.y, change.position.y),
                                    )
                                } ?: change.position
                            } else if (dragCanvas) {
                                currentOnAction(PockRefAction.PanCanvas(Offset(dragAmount.x, dragAmount.y)))
                            }
                            // Drag-to-edge auto-scroll handoff.
                            // Update the shared state with the
                            // latest pointer position and the
                            // current eligibility. The worker
                            // (LaunchedEffect at PockRefCanvas
                            // level) polls these every 16ms and
                            // dispatches PanCanvas when the
                            // pointer is in the edge zone.
                            lastPointerPosState.value = change.position
                            autoScrollEligibleState.value =
                                dragStarted || lassoActive || boxSelectDragging
                        }
                    } while (changes.any { it.pressed })

                    // Gesture ended — drop the auto-scroll
                    // flag so the worker idles even if the
                    // pointer happens to still be in the edge
                    // zone (e.g. user dragged into the
                    // corner, released, and then tapped again
                    // with the same position).
                    autoScrollEligibleState.value = false

                    if (transformStarted) {
                        currentOnAction(PockRefAction.EndSceneGesture)
                        transformStarted = false
                        transformMode = ImageTransformMode.NONE
                        initialZoom = 1f
                        initialRotation = 0f
                        totalSlideY = 0f
                        previousCentroidY = 0f
                    }
                    // If a long-press fired mid-gesture, dismiss
                    // the context menu now. Doing it on gesture end
                    // (rather than immediately on show) gives the
                    // user a frame to see the menu pop in.
                    if (longPressFired && currentState.contextMenu != null) {
                        currentOnAction(PockRefAction.DismissContextMenu)
                    }

                    val upTime = System.currentTimeMillis()
                    val isTap = !dragStarted && !transformStarted && (upTime - downTime) < 300L && totalDrag.getDistance() < 8f

                    // V_NOW (user request): in multi-select mode,
                    // tapping the empty canvas should deselect all
                    // images. The previous behavior was a silent
                    // no-op because the down handler in
                    // multiSelectMode sets lassoActive = true on
                    // pointer-down (so the upcoming lasso is ready
                    // if the user does drag), and the empty-tap
                    // branch below has a `!lassoActive` gate that
                    // then skips the Deselect dispatch. The lasso
                    // commit at the bottom of this handler runs
                    // `LassoSelect(0×0 rect)`, but the reducer
                    // computes `hitIds` as the images whose centers
                    // fall inside that point-sized rect — always
                    // empty — so the selection is unchanged. Net
                    // effect: the user's tap disappears into the
                    // void.
                    //
                    // Fix: when [isTap] and [lassoActive] are both
                    // true, abandon the lasso and let the empty-tap
                    // branch fire. Box-select mode does not need
                    // this fix because its own down handler
                    // dispatches `BeginBoxSelect` (which clears
                    // selection) AND the up-time
                    // `CommitBoxSelect` (which also clears when the
                    // rect is empty) — two redundant paths both
                    // result in deselect.
                    if (isTap && lassoActive) {
                        lassoActive = false
                        lassoStart = null
                    }

                    if (activeDragImageId != null) {
                        if (dragStarted) {
                            currentOnAction(PockRefAction.CommitImageDrag)
                        } else if (isTap) {
                            val tappedId = activeDragImageId
                            val curState = currentState
                            val isDoubleTapOnSelected = tappedId in curState.selectedImageIds &&
                                (upTime - lastTapTime) < 400L &&
                                (lastTapPos - downPos).getDistance() < 20f
                            if (isDoubleTapOnSelected) {
                                currentOnAction(PockRefAction.FitImageToScreen(tappedId))
                                lastTapTime = 0L
                                lastTapPos = Offset.Zero
                            } else {
                                currentOnAction(PockRefAction.CycleImageUnderPoint(downPos))
                                lastTapTime = upTime
                                lastTapPos = downPos
                            }
                        } else {
                            currentOnAction(PockRefAction.CancelImageDrag)
                        }
                    } else if (isTap && hitId != null && !lassoActive && !boxSelectDragging) {
                        // V_NOW (user bug report): tap on a LOCKED
                        // image must still select it, otherwise
                        // the user can never unlock it (the
                        // unlock button lives on the selection
                        // toolbar, and the toolbar only appears
                        // when at least one image is selected).
                        //
                        // The drag was correctly skipped for the
                        // locked image at down time (line 4468),
                        // so [activeDragImageId] is null for
                        // this gesture — but [hitId] still holds
                        // the hit-test result from the down
                        // event. We branch on [hitId] != null
                        // BEFORE the empty-canvas-tap branch
                        // below (which only fires when the
                        // pointer hit nothing), and run the
                        // same select / double-tap-fit-to-screen
                        // logic the unlocked path uses, just
                        // keyed off [hitId] instead of
                        // [activeDragImageId].
                        val tappedId = hitId
                        val curState = currentState
                        val isDoubleTapOnSelected = tappedId in curState.selectedImageIds &&
                            (upTime - lastTapTime) < 400L &&
                            (lastTapPos - downPos).getDistance() < 20f
                        if (isDoubleTapOnSelected) {
                            currentOnAction(PockRefAction.FitImageToScreen(tappedId))
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                        } else {
                            currentOnAction(PockRefAction.CycleImageUnderPoint(downPos))
                            lastTapTime = upTime
                            lastTapPos = downPos
                        }
                    } else if (isTap && !lassoActive && !boxSelectDragging) {
                        // Tap on empty canvas. Two cases:
                        //   - single tap: clear the current selection
                        //     via the dedicated [Deselect] action
                        //     (rather than SelectImage(null) — the
                        //     intent is explicit and the reducer
                        //     doesn't need a "if (imageId == null)..."
                        //     branch).
                        //   - double tap: reset the canvas to
                        //     100% zoom / 0 offset (the user is
                        //     saying "I'm lost, take me home"). The
                        //     second tap ALSO deselects so the user
                        //     doesn't end up with a selection that
                        //     "stuck" through the reset.
                        val isDoubleTapOnEmpty = (upTime - lastEmptyTapTime) < 400L &&
                            (lastEmptyTapPos - downPos).getDistance() < 20f
                        if (isDoubleTapOnEmpty) {
                            // V5: was ResetView (force 100%
                            // zoom / 0 offset — "go home"). User
                            // asked for double-tap-empty to be
                            // the smart fit-to-screen behavior
                            // instead: compute the optimal scale
                            // from the content AABB + viewport
                            // size, scale down if content is
                            // bigger than the window, keep 1.0
                            // if content is smaller (don't
                            // upscale tiny content to fill), and
                            // center the result. The 2f canvas
                            // edge padding matches the
                            // auto-layout's edgePadding so the
                            // two flows look identical.
                            currentOnAction(PockRefAction.FitToScreen)
                            currentOnAction(PockRefAction.Deselect)
                            // S Pen double-tap exit: if the canvas
                            // is currently in BOX or MULTI mode,
                            // the user's "double-tap on empty" is
                            // also a clear signal to go back to
                            // SINGLE. (FitToScreen + Deselect
                            // handle the canvas and selection;
                            // this handles the interaction mode.)
                            if (currentState.boxSelectMode || currentState.multiSelectMode) {
                                currentOnAction(
                                    PockRefAction.SetSelectionMode(SelectionMode.SINGLE),
                                )
                            }
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                            lastEmptyTapTime = 0L
                            lastEmptyTapPos = Offset.Zero
                        } else {
                            currentOnAction(PockRefAction.Deselect)
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                            lastEmptyTapTime = upTime
                            lastEmptyTapPos = downPos
                        }
                    }
                    // Multi-finger tap detection. Distinct from
                    // [isTap] (which only fires for single-finger
                    // gestures). Conditions for a 2 / 3-finger
                    // tap:
                    //   - peak simultaneous pointer count was 2 or 3
                    //   - no finger moved past 4px (transform
                    //     threshold) — so the gesture was a
                    //     touchdown + lift, not a pinch / slide
                    //   - total duration under 300ms
                    //   - the existing single-finger drag flag
                    //     never set (a 2-finger tap preceded by a
                    //     1-finger touchdown is a different gesture
                    //     class; we don't want to undo on a
                    //     drag-then-second-finger)
                    if (
                        !dragStarted && !transformStarted &&
                        !gestureMoved &&
                        (upTime - downTime) < 300L &&
                        peakPointerCount >= 2
                    ) {
                        when (peakPointerCount) {
                            2 -> {
                                // 2-finger tap = undo. Use the
                                // same 400ms / 20px gap as the
                                // other double-tap paths so the
                                // cadence feels consistent.
                                currentOnAction(PockRefAction.Undo)
                            }
                            3 -> {
                                // 3-finger tap = redo. (The
                                // previous "3-finger double-tap
                                // → reset transform" behaviour
                                // was removed in 2026-06; use
                                // the toolbar "复原" button on
                                // a single image instead.)
                                currentOnAction(PockRefAction.Redo)
                            }
                        }
                    }
                    if (boxSelectDragging && boxSelectStart != null) {
                        currentOnAction(PockRefAction.CommitBoxSelect)
                        boxSelectDragging = false
                        boxSelectStart = null
                    }
                    if (lassoActive && lassoStart != null) {
                        val end = lassoStart!!
                        val left = minOf(lassoStart!!.x, end.x)
                        val top = minOf(lassoStart!!.y, end.y)
                        val right = maxOf(lassoStart!!.x, end.x)
                        val bottom = maxOf(lassoStart!!.y, end.y)
                        currentOnAction(PockRefAction.LassoSelect(RectF(left, top, right, bottom)))
                    }
                }
            }
            .pointerInput(Unit) {
                var lastHoverPos = Offset.Zero
                var lastHoverTime = 0L
                var hoverCandidateId: String? = null
                var hoverCandidateStartTime = 0L
                var lastTargetSwitchTime = 0L
                var lastTargetId: String? = null
                var previewActiveId: String? = null

                val triggerDelayMs = 300L
                val speedThresholdPxPerSec = 500f
                val edgeThresholdPx = 20f
                val targetSwitchCooldownMs = 500L

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val hoverChange = event.changes.firstOrNull { change ->
                            (change.type == PointerType.Stylus ||
                                change.type == PointerType.Mouse) && !change.pressed
                        } ?: continue

                        when (event.type) {
                            PointerEventType.Enter,
                            PointerEventType.Move -> {
                                val pos = hoverChange.position
                                val now = System.currentTimeMillis()
                                val s = currentState
                                val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                                val hitId = hitTestImage(s, pos, viewportSize)

                                currentOnAction(PockRefAction.SetHover(hitId, pos))

                                val dt = (now - lastHoverTime).coerceAtLeast(1)
                                val dx = pos.x - lastHoverPos.x
                                val dy = pos.y - lastHoverPos.y
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                val speed = distance / dt * 1000f

                                lastHoverPos = pos
                                lastHoverTime = now

                                if (hitId == null) {
                                    hoverCandidateId = null
                                    hoverCandidateStartTime = 0L
                                    if (previewActiveId != null) {
                                        previewActiveId = null
                                        currentOnAction(PockRefAction.SetHoverPreview(null))
                                    }
                                } else {
                                    if (hitId != lastTargetId) {
                                        if (lastTargetId != null && (now - lastTargetSwitchTime) < targetSwitchCooldownMs) {
                                            hoverCandidateId = null
                                            hoverCandidateStartTime = 0L
                                            if (previewActiveId != null) {
                                                previewActiveId = null
                                                currentOnAction(PockRefAction.SetHoverPreview(null))
                                            }
                                        }
                                        lastTargetId = hitId
                                        lastTargetSwitchTime = now
                                    }

                                    if (speed > speedThresholdPxPerSec) {
                                        hoverCandidateId = null
                                        hoverCandidateStartTime = 0L
                                        if (previewActiveId != null) {
                                            previewActiveId = null
                                            currentOnAction(PockRefAction.SetHoverPreview(null))
                                        }
                                    } else {
                                        val image = s.images.firstOrNull { it.id == hitId }
                                        val edgeOk = image != null && !isNearImageEdge(s, pos, image, viewportSize, edgeThresholdPx)

                                        if (!edgeOk) {
                                            hoverCandidateId = null
                                            hoverCandidateStartTime = 0L
                                            if (previewActiveId != null) {
                                                previewActiveId = null
                                                currentOnAction(PockRefAction.SetHoverPreview(null))
                                            }
                                        } else {
                                            if (hoverCandidateId != hitId) {
                                                hoverCandidateId = hitId
                                                hoverCandidateStartTime = now
                                            }
                                            if (hoverCandidateId == hitId && (now - hoverCandidateStartTime) >= triggerDelayMs) {
                                                if (previewActiveId != hitId) {
                                                    previewActiveId = hitId
                                                    currentOnAction(PockRefAction.SetHoverPreview(hitId))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            PointerEventType.Exit -> {
                                currentOnAction(PockRefAction.ClearHover)
                                hoverCandidateId = null
                                hoverCandidateStartTime = 0L
                                lastTargetId = null
                                if (previewActiveId != null) {
                                    previewActiveId = null
                                    currentOnAction(PockRefAction.SetHoverPreview(null))
                                }
                            }
                            else -> { }
                        }
                    }
                }
            },
    ) {
        val centerOffset = Offset(size.width / 2f, size.height / 2f)

        // Viewport cull: compute the world-space visible rect once per draw
        // pass, then skip images whose AABB does not intersect it. The
        // 200px screen-space buffer (see [VIEWPORT_BUFFER_PX]) prevents
        // edge flicker on fast pans. Items that are being dragged (transient
        // drag), selected, or being box-select-previewed are always drawn
        // even if they would otherwise be culled — otherwise the selection
        // chrome and the box-select feedback could pop out at the edges.
        val viewRect = worldViewportRect(
            canvasOffset = state.canvasOffset,
            canvasScale = state.canvasScale,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat(),
        )

        sortedImages.forEach { image ->
            if (image.id !in forcedDrawIds) {
                val aabb = imageWorldAabb(image)
                if (!rectsIntersect(aabb, viewRect)) return@forEach
            }
            val cached = bitmapCache[image.id]?.second
            if (cached != null) {
                drawSceneImage(
                    image = image,
                    bitmap = cached,
                    isSelected = image.id in state.selectedImageIds,
                    isBoxSelectPreview = image.id in state.boxSelectPreviewIds,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = state.canvasOffset,
                    transientDragIds = state.transientDragImageIds,
                    transientDragDelta = state.transientDragDelta,
                )
            } else {
                drawImagePlaceholder(
                    image = image,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = state.canvasOffset,
                )
            }
            // V_NOW: locked-image red border, drawn
            // IMMEDIATELY after the image's bitmap /
            // placeholder. The next iteration of the
            // [sortedImages] forEach (a higher-z image)
            // draws on top of BOTH the bitmap AND this
            // border, so the upper image correctly
            // occludes the lower locked image's red
            // border along with the image itself. The
            // previous design drew the locked border in a
            // separate forEach AFTER all images, which
            // meant the border was painted on top of
            // every image — locked or not — and the
            // border was visible THROUGH any upper
            // image that overlapped the locked one. The
            // user reported this as "the upper image
            // should hide the lower locked image's red
            // border, but it doesn't".
            //
            // The drawLockedIndicator transform
            // (translate + rotate + scale) mirrors
            // [drawSceneImage]'s exactly so the border
            // follows the image's actual post-rotation
            // footprint, not its axis-aligned bbox.
            if (image.locked) {
                drawLockedIndicator(
                    image = image,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = state.canvasOffset,
                )
            }
        }

        // Edge-to-edge snap guide lines. Drawn AFTER all images so they
        // sit on top, but BEFORE the hover magnifier so a magnifier
        // triggered mid-drag still wins. We deliberately render guides on
        // the same draw scope as the rest of the canvas — no extra
        // overlay layer — to keep the rendering order simple and avoid
        // z-fighting with selection chrome.
        state.snapGuide?.let { guide ->
            // snapGuideAlpha is the V4 TRACKING fade-in factor: it ramps
            // 0 → 1 over the 300ms settle window so the user sees the
            // guide appearing rather than popping on. After engage the
            // VM holds alpha at 1f until disengage / commit.
            //
            // isCornerSnap (V5) makes the guide stroke thicker so the
            // user can tell "I hit the corner" apart from "I hit an
            // edge mid-line".
            drawSnapGuide(
                guide = guide,
                alpha = state.snapGuideAlpha.coerceIn(0f, 1f),
                isCornerSnap = guide.isCornerSnap,
                centerOffset = centerOffset,
                canvasScale = state.canvasScale,
                canvasOffset = state.canvasOffset,
            )
        }

        // Post-commit highlight. The fade-out alpha is driven by
        // [state.snapHighlight.startTimeMs] vs the system clock; we
        // snapshot the value once per frame so a 16ms-stale render
        // doesn't dip below zero and clamp to "frozen at end".
        state.snapHighlight?.let { highlight ->
            val elapsed = System.currentTimeMillis() - highlight.startTimeMs
            val rawAlpha = 1f - (elapsed.toFloat() / SNAP_HIGHLIGHT_DURATION_MS.toFloat())
            val alpha = rawAlpha.coerceIn(0f, 1f)
            if (alpha > 0.01f) {
                drawSnapHighlight(
                    guide = highlight.guide,
                    alpha = alpha,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = state.canvasOffset,
                    allImages = state.images,
                )
            }
        }

        // Rotation angle-snap guide. Drawn when a rotation gesture is
        // currently locked to a 0°/90°/180°/270° multiple. We find the
        // first selected image and draw a small "angle alignment"
        // overlay at its center — a thin circle ring + the snapped
        // angle text — so the user sees the snap engage visually.
        state.angleSnapGuide?.let { guide ->
            val targetId = state.selectedImageId
                ?: state.selectedImageIds.firstOrNull()
            if (targetId != null) {
                val target = state.images.firstOrNull { it.id == targetId }
                if (target != null) {
                    drawAngleSnapGuide(
                        snappedDegrees = guide.snappedDegrees,
                        centerOffset = centerOffset,
                        canvasScale = state.canvasScale,
                        canvasOffset = state.canvasOffset,
                        imageCenter = target.center,
                    )
                }
            }
        }

        // V2: hover-magnifier draw moved OUT of this
        // canvas into the [HoverMagnifierOverlay]
        // Composable, which is the last child of the
        // outer Box. The in-canvas draw used to sit here
        // (and would render below the top toolbar,
        // minimap, and FAB in the Box z-order — the
        // user's reported bug before V2). The overlay
        // reads the same [hoverProgress] + [bitmapCache]
        // and renders to its own Canvas that's a sibling
        // of (drawn AFTER) all the chrome, so the
        // magnifier image is on top of everything.
        state.boxSelectRect?.let { rect ->
            drawRect(
                color = Color(0x334FC3F7),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width(), rect.height()),
            )
            drawRect(
                color = Color(0xFF4FC3F7),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width(), rect.height()),
                style = Stroke(width = 2f),
            )
        }
    }
}

private data class SelectionToolbarAnchor(
    val imageLeftScreenX: Float,
    val imageRightScreenX: Float,
    val imageCenterScreenY: Float,
)

private fun selectionToolbarAnchor(state: PockRefUiState): SelectionToolbarAnchor? {
    val selected = state.images.filter { it.id in state.selectedImageIds }
    if (selected.isEmpty()) return null
    val viewport = state.canvasViewportSize
    if (viewport.width <= 0f || viewport.height <= 0f) return null
    val dragIds = state.transientDragImageIds
    val dragDelta = state.transientDragDelta

    val leftWorldX = selected.minOf {
        val offsetX = if (it.id in dragIds) dragDelta.x else 0f
        it.center.x + offsetX - (it.displaySize.width * it.scale) / 2f
    }
    val rightWorldX = selected.maxOf {
        val offsetX = if (it.id in dragIds) dragDelta.x else 0f
        it.center.x + offsetX + (it.displaySize.width * it.scale) / 2f
    }
    val topWorldY = selected.minOf {
        val offsetY = if (it.id in dragIds) dragDelta.y else 0f
        it.center.y + offsetY - (it.displaySize.height * it.scale) / 2f
    }
    val bottomWorldY = selected.maxOf {
        val offsetY = if (it.id in dragIds) dragDelta.y else 0f
        it.center.y + offsetY + (it.displaySize.height * it.scale) / 2f
    }

    val halfW = viewport.width / 2f
    val halfH = viewport.height / 2f
    val imageLeftScreenX = halfW + state.canvasOffset.x + leftWorldX * state.canvasScale
    val imageRightScreenX = halfW + state.canvasOffset.x + rightWorldX * state.canvasScale
    val imageCenterScreenY = halfH + state.canvasOffset.y + (topWorldY + bottomWorldY) / 2f * state.canvasScale
    return SelectionToolbarAnchor(
        imageLeftScreenX = imageLeftScreenX,
        imageRightScreenX = imageRightScreenX,
        imageCenterScreenY = imageCenterScreenY,
    )
}

private fun clampSelectionToolbarOffset(
    anchor: SelectionToolbarAnchor,
    gapPx: Float,
    toolbarWidth: Float,
    toolbarHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    topInset: Float,
    bottomInset: Float,
    edgePadding: Float,
): Offset {
    val effectiveWidth = toolbarWidth.coerceAtLeast(1f)
    val effectiveHeight = toolbarHeight.coerceAtLeast(1f)

    val rightCandidateLeft = anchor.imageRightScreenX + gapPx
    val leftCandidateLeft = anchor.imageLeftScreenX - gapPx - effectiveWidth
    val rightFits = (rightCandidateLeft + effectiveWidth) <= (viewportWidth - edgePadding) &&
        rightCandidateLeft >= edgePadding
    val leftFits = leftCandidateLeft >= edgePadding &&
        (leftCandidateLeft + effectiveWidth) <= (viewportWidth - edgePadding)

    val toolbarLeftX = when {
        rightFits -> rightCandidateLeft
        leftFits -> leftCandidateLeft
        else -> {
            val rightRoom = (viewportWidth - edgePadding) - rightCandidateLeft
            val leftRoom = (leftCandidateLeft + effectiveWidth) - edgePadding
            if (rightRoom >= leftRoom) {
                rightCandidateLeft.coerceIn(edgePadding, viewportWidth - effectiveWidth - edgePadding)
            } else {
                leftCandidateLeft.coerceIn(edgePadding, viewportWidth - effectiveWidth - edgePadding)
            }
        }
    }

    val minY = topInset + edgePadding
    val maxY = viewportHeight - effectiveHeight - bottomInset - edgePadding
    val centerY = anchor.imageCenterScreenY
    val idealY = centerY - effectiveHeight / 2f
    val finalY = if (maxY < minY) {
        (viewportHeight - effectiveHeight) / 2f
    } else {
        idealY.coerceIn(minY, maxY)
    }

    return Offset(toolbarLeftX, finalY)
}

/**
 * Brand color used for snap guides and the post-commit highlight. Magenta/
 * purple is the conventional "alignment" color in pro design tools
 * (Figma, Sketch, Adobe XD) — it sits well on both light and dark
 * backgrounds and is visually distinct from the cyan selection chrome.
 */
private val SnapGuideColor = Color(0xFFCE5BEB)
private val SnapHighlightColor = Color(0xFFAB47BC)
private val SnapGuideStrokePx = 2.5f
private val SnapHighlightStrokePx = 3f
private val SnapTargetEdgeMarkerPx = 8f

/**
 * Convert a world-space X coordinate to screen pixels using the current
 * canvas transform. Centralized here because the snap render path uses
 * it four times; inlining it would be both verbose and error-prone.
 */
private fun DrawScope.worldXToScreen(worldX: Float, centerOffset: Offset, canvasOffset: Offset, canvasScale: Float): Float =
    centerOffset.x + canvasOffset.x + worldX * canvasScale

private fun DrawScope.worldYToScreen(worldY: Float, centerOffset: Offset, canvasOffset: Offset, canvasScale: Float): Float =
    centerOffset.y + canvasOffset.y + worldY * canvasScale

/**
 * Draw edge-to-edge snap guide lines for the active drag. One line per
 * axis (horizontal / vertical) where snap fired, sized to cover the
 * dragged group's AABB on the cross axis. Lines are 2 screen pixels
 * thick for a regular edge snap; bumped to 3px for a corner snap
 * (V5: stronger visual feedback when the user lands on a corner).
 *
 * [alpha] is the V4 TRACKING fade-in factor: 0 right when the cursor
 * enters the 15px trigger range, ramping to 1 over 300ms as the user
 * settles. We apply it to the line color's alpha channel so the guide
 * visibly appears during the settle window rather than popping on.
 */
private fun DrawScope.drawSnapGuide(
    guide: com.pockref.app.model.SnapGuide,
    alpha: Float,
    isCornerSnap: Boolean,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
) {
    if (alpha <= 0f) return
    val gb = guide.groupBounds
    val color = SnapGuideColor.copy(alpha = alpha)
    val strokePx = if (isCornerSnap) SnapGuideStrokePx + 2f else SnapGuideStrokePx

    guide.axisX?.let { ax ->
        val x = worldXToScreen(ax.targetEdgeWorld, centerOffset, canvasOffset, canvasScale)
        val yTop = worldYToScreen(gb.top, centerOffset, canvasOffset, canvasScale)
        val yBot = worldYToScreen(gb.bottom, centerOffset, canvasOffset, canvasScale)
        drawLine(
            color = color,
            start = Offset(x, yTop),
            end = Offset(x, yBot),
            strokeWidth = strokePx,
        )
    }
    guide.axisY?.let { ay ->
        val y = worldYToScreen(ay.targetEdgeWorld, centerOffset, canvasOffset, canvasScale)
        val xLeft = worldXToScreen(gb.left, centerOffset, canvasOffset, canvasScale)
        val xRight = worldXToScreen(gb.right, centerOffset, canvasOffset, canvasScale)
        drawLine(
            color = color,
            start = Offset(xLeft, y),
            end = Offset(xRight, y),
            strokeWidth = strokePx,
        )
    }
}

/**
 * Draw the post-commit highlight: a magenta outline around the snapped
 * group + a small filled marker on the target's snapped edge. The
 * [alpha] is animated externally from 1 → 0 over
 * [SNAP_HIGHLIGHT_DURATION_MS].
 *
 * Looks up the target image by id so we can mark the right edge with
 * the marker dot. If the target is missing (e.g. deleted between commit
 * and render) we just skip the marker — the group outline alone is
 * enough to communicate the snap result.
 */
private fun DrawScope.drawSnapHighlight(
    guide: com.pockref.app.model.SnapGuide,
    alpha: Float,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    allImages: List<SceneImage>,
) {
    val gb = guide.groupBounds
    val baseColor = SnapHighlightColor

    val left = worldXToScreen(gb.left, centerOffset, canvasOffset, canvasScale)
    val top = worldYToScreen(gb.top, centerOffset, canvasOffset, canvasScale)
    val right = worldXToScreen(gb.right, centerOffset, canvasOffset, canvasScale)
    val bottom = worldYToScreen(gb.bottom, centerOffset, canvasOffset, canvasScale)
    val w = (right - left).coerceAtLeast(0f)
    val h = (bottom - top).coerceAtLeast(0f)

    val outlineColor = baseColor.copy(alpha = (alpha * 0.85f).coerceIn(0f, 1f))
    val fillColor = baseColor.copy(alpha = (alpha * 0.18f).coerceIn(0f, 1f))

    drawRect(
        color = fillColor,
        topLeft = Offset(left, top),
        size = Size(w, h),
    )
    drawRect(
        color = outlineColor,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = SnapHighlightStrokePx),
    )

    // Target edge marker. We draw a small filled square straddling the
    // snapped edge on the target image so the user can see exactly which
    // target edge they locked onto. Two cases per axis: marker is
    // horizontal (X snap) or vertical (Y snap).
    val targets = listOfNotNull(
        guide.axisX?.targetImageId,
        guide.axisY?.targetImageId,
    ).distinct()

    targets.forEach { targetId ->
        val target = allImages.firstOrNull { it.id == targetId } ?: return@forEach
        val halfW = (target.displaySize.width * target.scale) / 2f
        val halfH = (target.displaySize.height * target.scale) / 2f
        val tLeft = target.center.x - halfW
        val tRight = target.center.x + halfW
        val tTop = target.center.y - halfH
        val tBottom = target.center.y + halfH

        guide.axisX?.let { ax ->
            if (ax.targetImageId == targetId) {
                val ex = worldXToScreen(ax.targetEdgeWorld, centerOffset, canvasOffset, canvasScale)
                val eyTop = worldYToScreen(tTop, centerOffset, canvasOffset, canvasScale)
                val eyBot = worldYToScreen(tBottom, centerOffset, canvasOffset, canvasScale)
                val markerHalf = SnapTargetEdgeMarkerPx / 2f
                val markerColor = baseColor.copy(alpha = alpha.coerceIn(0f, 1f))
                // Crossbar across the target's snapped vertical edge so
                // the user can tell the snap fired on the LEFT or RIGHT
                // of the target, not somewhere in the middle.
                drawLine(
                    color = markerColor,
                    start = Offset(ex - markerHalf, eyTop),
                    end = Offset(ex - markerHalf, eyBot),
                    strokeWidth = SnapHighlightStrokePx,
                )
                drawLine(
                    color = markerColor,
                    start = Offset(ex + markerHalf, eyTop),
                    end = Offset(ex + markerHalf, eyBot),
                    strokeWidth = SnapHighlightStrokePx,
                )
            }
        }
        guide.axisY?.let { ay ->
            if (ay.targetImageId == targetId) {
                val ey = worldYToScreen(ay.targetEdgeWorld, centerOffset, canvasOffset, canvasScale)
                val exLeft = worldXToScreen(tLeft, centerOffset, canvasOffset, canvasScale)
                val exRight = worldXToScreen(tRight, centerOffset, canvasOffset, canvasScale)
                val markerHalf = SnapTargetEdgeMarkerPx / 2f
                val markerColor = baseColor.copy(alpha = alpha.coerceIn(0f, 1f))
                drawLine(
                    color = markerColor,
                    start = Offset(exLeft, ey - markerHalf),
                    end = Offset(exRight, ey - markerHalf),
                    strokeWidth = SnapHighlightStrokePx,
                )
                drawLine(
                    color = markerColor,
                    start = Offset(exLeft, ey + markerHalf),
                    end = Offset(exRight, ey + markerHalf),
                    strokeWidth = SnapHighlightStrokePx,
                )
            }
        }
    }
}

/**
 * Draw the rotation angle-snap engagement indicator on top of the
 * rotated image. A thin magenta ring straddling the image's
 * un-rotated AABB (so the ring is always axis-aligned, not rotated
 * with the image) plus a small label above the image showing the
 * snapped angle as "X°". Visual treatment mirrors [drawSnapGuide]'s
 * magenta-on-everything palette so the two snap systems feel like
 * siblings rather than unrelated UI.
 *
 * The ring radius is derived from the image's bounding box diagonal
 * (min side / 2) so it always fits inside the image and looks
 * proportional regardless of the image's aspect ratio.
 */
private fun DrawScope.drawAngleSnapGuide(
    snappedDegrees: Float,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    imageCenter: Offset,
) {
    val cx = worldXToScreen(imageCenter.x, centerOffset, canvasOffset, canvasScale)
    val cy = worldYToScreen(imageCenter.y, centerOffset, canvasOffset, canvasScale)
    // The ring follows the canvas scale so it grows with zoom — at
    // 100% it's a comfortable ~28dp radius; at 8x it grows with the
    // image. (The user has already zoomed; if the ring becomes too
    // small to read it's because the image itself is small, in which
    // case the label still anchors correctly via the .offset below.)
    val ringRadiusPx = 14f * canvasScale.coerceAtLeast(0.4f)
    val ringStrokePx = 2f
    val ringColor = SnapGuideColor

    drawCircle(
        color = ringColor.copy(alpha = 0.18f),
        radius = ringRadiusPx,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = ringColor,
        radius = ringRadiusPx,
        center = Offset(cx, cy),
        style = Stroke(width = ringStrokePx),
    )

    // Small crosshair through the image center (rotated 45° vs the
    // ring) — a quick visual cue that "this image is locked to an
    // angle". Drawn as two short line segments crossing at the center.
    val crossHalf = ringRadiusPx * 0.55f
    val cos45 = 0.70710677f
    val crossDx = crossHalf * cos45
    val crossDy = crossHalf * cos45
    drawLine(
        color = ringColor.copy(alpha = 0.6f),
        start = Offset(cx - crossDx, cy - crossDy),
        end = Offset(cx + crossDx, cy + crossDy),
        strokeWidth = ringStrokePx,
    )
    drawLine(
        color = ringColor.copy(alpha = 0.6f),
        start = Offset(cx - crossDx, cy + crossDy),
        end = Offset(cx + crossDx, cy - crossDy),
        strokeWidth = ringStrokePx,
    )

    // Angle label: a small filled pill above the ring with the
    // snapped angle as "0°" / "90°" / "180°" / "270°". The pill is
    // sized to the text and pinned just outside the ring so the
    // number doesn't fight the ring for attention. Uses the system
    // font through [drawIntoCanvas] so we don't have to set up a
    // TextStyle just for one number per snap event.
    val labelText = "${snappedDegrees.toInt()}°"
    val labelWidth = labelText.length * 8f + 14f
    val labelHeight = 18f
    val labelTopLeft = Offset(cx - labelWidth / 2f, cy - ringRadiusPx - labelHeight - 6f)
    val labelCornerRadius = 4f
    drawRoundRect(
        color = ringColor,
        topLeft = labelTopLeft,
        size = Size(labelWidth, labelHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(labelCornerRadius, labelCornerRadius),
    )
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 13f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textY = labelTopLeft.y + labelHeight / 2f + 4f
        nativeCanvas.drawText(labelText, cx, textY, paint)
    }
}

private fun DrawScope.drawImagePlaceholder(
    image: SceneImage,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
) {
    val canvasCenter = Offset(
        x = centerOffset.x + canvasOffset.x + image.center.x * canvasScale,
        y = centerOffset.y + canvasOffset.y + image.center.y * canvasScale,
    )
    val width = image.displaySize.width * image.scale * canvasScale
    val height = image.displaySize.height * image.scale * canvasScale
    val topLeft = Offset(canvasCenter.x - width / 2f, canvasCenter.y - height / 2f)
    drawRect(
        color = Color(0x22FFFFFF),
        topLeft = topLeft,
        size = Size(width, height),
    )
    drawRect(
        color = Color(0x44FFFFFF),
        topLeft = topLeft,
        size = Size(width, height),
        style = Stroke(width = 1f),
    )
}

/**
 * V_NOW: draws the locked-image red border in the SAME
 * transform frame the image itself was drawn in (translate
 * to top-left, rotate around image center, scale for
 * flipX/flipY). Extracted out of the main draw scope so
 * the canvas can call it from inside the [sortedImages]
 * forEach — immediately after the image's [drawSceneImage]
 * or [drawImagePlaceholder] call — so the locked border
 * shares the image's z-order slot. The next image in the
 * forEach (higher z-index) draws on top of both the image
 * AND its border, which is the correct occlusion behavior
 * the user asked for: a locked image's red border
 * covered by an upper image is invisible, only the
 * non-covered portion of the border remains.
 *
 * Style: double-stroke rectangle (outer glow + inner
 * solid) in the locked-affordance red, with scale-aware
 * widths so a 10% zoom-out doesn't shrink the indicator
 * to a hairline and a 5x zoom-in doesn't bloat it to a
 * chunky border. The translate+rotate+scale transform
 * mirrors [drawSceneImage]'s exactly so the locked
 * indicator and the selection outline use the same
 * geometry — they overlap cleanly when an image is both
 * selected and locked (rare, but the math stays
 * consistent).
 */
private fun DrawScope.drawLockedIndicator(
    image: SceneImage,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
) {
    val canvasCenter = Offset(
        x = centerOffset.x + canvasOffset.x + image.center.x * canvasScale,
        y = centerOffset.y + canvasOffset.y + image.center.y * canvasScale,
    )
    val width = image.displaySize.width * image.scale * canvasScale
    val height = image.displaySize.height * image.scale * canvasScale
    val topLeft = Offset(canvasCenter.x - width / 2f, canvasCenter.y - height / 2f)
    val strokePx = max(3f, canvasScale * 3f)
    val glowPx = max(8f, canvasScale * 8f)
    translate(topLeft.x, topLeft.y) {
        rotate(image.rotationDegrees, pivot = Offset(width / 2f, height / 2f)) {
            scale(
                scaleX = if (image.flipX) -1f else 1f,
                scaleY = if (image.flipY) -1f else 1f,
                pivot = Offset(width / 2f, height / 2f),
            ) {
                drawRect(
                    color = LockedGlowColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height),
                    style = Stroke(width = glowPx),
                )
                drawRect(
                    color = LockedStrokeColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

private fun DrawScope.drawSceneImage(
    image: SceneImage,
    bitmap: ImageBitmap,
    isSelected: Boolean,
    isBoxSelectPreview: Boolean = false,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    transientDragIds: Set<String>,
    transientDragDelta: Offset,
) {
    val dragOffset = if (image.id in transientDragIds) transientDragDelta else Offset.Zero
    val canvasCenter = Offset(
        x = centerOffset.x + canvasOffset.x + (image.center.x + dragOffset.x) * canvasScale,
        y = centerOffset.y + canvasOffset.y + (image.center.y + dragOffset.y) * canvasScale,
    )
    val width = image.displaySize.width * image.scale * canvasScale
    val height = image.displaySize.height * image.scale * canvasScale
    val topLeft = Offset(canvasCenter.x - width / 2f, canvasCenter.y - height / 2f)

    translate(topLeft.x, topLeft.y) {
        rotate(image.rotationDegrees, pivot = Offset(width / 2f, height / 2f)) {
            scale(
                scaleX = if (image.flipX) -1f else 1f,
                scaleY = if (image.flipY) -1f else 1f,
                pivot = Offset(width / 2f, height / 2f),
            ) {
                drawImage(
                    image = bitmap,
                    dstSize = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
                    colorFilter = buildColorFilter(image.colorAdjust),
                )
                if (isBoxSelectPreview && !isSelected) {
                    drawRect(
                        color = Color(0x554FC3F7),
                        size = Size(width, height),
                    )
                    drawRect(
                        color = Color(0xFF4FC3F7),
                        size = Size(width, height),
                        style = Stroke(width = max(2f, canvasScale * 2f)),
                    )
                }
                if (isSelected) {
                    val strokePx = max(3f, canvasScale * 3f)
                    val glow = max(8f, canvasScale * 8f)
                    drawRect(
                        color = Color(0x556AD6FF),
                        size = Size(width, height),
                        style = Stroke(width = glow),
                    )
                    drawRect(
                        color = Color(0xFF6AD6FF),
                        size = Size(width, height),
                        style = Stroke(width = strokePx),
                    )
                }
            }
        }
    }
}

/**
 * V2: hover-magnifier overlay. Renders the magnifier
 * preview image as a separate Canvas that's the last
 * child of the outer [Box], so the magnifier image
 * sits ABOVE the top toolbar, minimap, and FAB. The
 * in-canvas version (which used to live inside
 * [PockRefCanvas]) was hidden behind those siblings in
 * the Box z-order; the user's reported bug.
 *
 * Same source as the in-canvas draw — same `state`
 * fields, same [bitmapCache] lookup, same
 * [drawHoverMagnifier] helper. Just lifted to its own
 * Composable that uses [Modifier.matchParentSize] so
 * the [DrawScope] sees the same `size` it did when
 * nested inside [PockRefCanvas]. The overlay is a
 * no-op (returns early) when [progress] is 0 so it
 * doesn't add a permanent transparent layer on top of
 * the chrome.
 */
@Composable
private fun BoxScope.HoverMagnifierOverlay(
    state: PockRefUiState,
    bitmapCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Int, ImageBitmap>>,
    progress: Float,
) {
    if (progress <= 0f) return
    val hoveredId = state.hoverPreviewImageId
    if (hoveredId == null) return
    val hoveredImage = state.images.firstOrNull { it.id == hoveredId } ?: return
    val cachedBitmap = bitmapCache[hoveredImage.id]?.second ?: return
    Canvas(
        modifier = Modifier.matchParentSize(),
    ) {
        // centerOffset for the overlay is just size/2 —
        // the magnifier math is screen-space and the
        // overlay covers the same area as the main
        // canvas did.
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val imageScreenCenter = Offset(
            x = centerOffset.x + state.canvasOffset.x + hoveredImage.center.x * state.canvasScale,
            y = centerOffset.y + state.canvasOffset.y + hoveredImage.center.y * state.canvasScale,
        )
        val imageScreenW = hoveredImage.displaySize.width * hoveredImage.scale * state.canvasScale
        val imageScreenH = hoveredImage.displaySize.height * hoveredImage.scale * state.canvasScale
        drawHoverMagnifier(
            image = hoveredImage,
            bitmap = cachedBitmap,
            imageScreenCenter = imageScreenCenter,
            imageScreenWidth = imageScreenW,
            imageScreenHeight = imageScreenH,
            progress = progress,
        )
    }
}

private fun DrawScope.drawHoverMagnifier(
    image: SceneImage,
    bitmap: ImageBitmap,
    imageScreenCenter: Offset,
    imageScreenWidth: Float,
    imageScreenHeight: Float,
    progress: Float,
) {
    if (progress <= 0f) return
    val vpW = size.width
    val vpH = size.height
    if (vpW <= 0f || vpH <= 0f) return

    val eased = progress.coerceIn(0f, 1f)

    if (eased > 0f) {
        drawRect(
            color = Color((0x99 * eased).toInt().coerceIn(0, 0xFF) shl 24),
            topLeft = Offset.Zero,
            size = Size(vpW, vpH),
        )
    }

    // Viewport budget: stay within 90% of the window. [vpW] / [vpH] and the
    // image's [intrinsicWidth] / [intrinsicHeight] are both in pixels, so the
    // budget stays dimensionally consistent without needing to factor in
    // [density]. (Earlier we multiplied by [density] here, but on a 2x DPI
    // panel that made the budget 1.8× the viewport and the magnifier
    // overflowed the window. The [density] parameter is still threaded
    // through to the signature for future use but is not part of the budget.)
    val maxW = vpW * 0.9f
    val maxH = vpH * 0.9f
    val insetW = (vpW - maxW) / 2f
    val insetH = (vpH - maxH) / 2f

    // Smart adaptive zoom: aim to fill at least [minFillRatio] of the
    // viewport (so the user always sees a recognisable image, never a
    // postage-stamp), but never downscale (clamp to 1.0) and never
    // upscale beyond [maxZoom]. The final size is the zoomed image size,
    // then scaled-to-fit if it would still exceed the window. We derive
    // [idealW] / [idealH] from the *viewport* (not [maxW]) so the
    // [minFillRatio] is "X% of what the user can see", independent of the
    // 0.9 inset.
    val intrinsicW = image.intrinsicWidth.toFloat().coerceAtLeast(1f)
    val intrinsicH = image.intrinsicHeight.toFloat().coerceAtLeast(1f)
    val maxZoom = 3.5f
    val minFillRatio = 0.5f
    val idealW = vpW * minFillRatio
    val idealH = vpH * minFillRatio
    val zoomToFillW = idealW / intrinsicW
    val zoomToFillH = idealH / intrinsicH
    val requiredZoom = maxOf(zoomToFillW, zoomToFillH)
    val appliedZoom = requiredZoom.coerceIn(1.0f, maxZoom)
    val zoomedW = intrinsicW * appliedZoom
    val zoomedH = intrinsicH * appliedZoom
    val scaleToFit = minOf(maxW / zoomedW, maxH / zoomedH, 1.0f)
    val baseTargetW = (zoomedW * scaleToFit).coerceAtLeast(1f)
    val baseTargetH = (zoomedH * scaleToFit).coerceAtLeast(1f)

    // A rotated rectangle's axis-aligned bounding box is larger than the
    // rectangle itself by a factor up to (|cos θ| + |sin θ|), which reaches
    // √2 ≈ 1.414 at 45°. The [scaleToFit] above only bounds the unrotated
    // image; we have to shrink further so the *rotated* image still fits the
    // viewport. Otherwise the rotated corners spill out of the white
    // magnifier box (and past the window edge).
    val rotRad = image.rotationDegrees.toDouble() * (PI / 180.0)
    val absCos = abs(cos(rotRad).toFloat())
    val absSin = abs(sin(rotRad).toFloat())
    val rotBboxW = baseTargetW * absCos + baseTargetH * absSin
    val rotBboxH = baseTargetW * absSin + baseTargetH * absCos
    val rotScale = minOf(
        if (rotBboxW > maxW) maxW / rotBboxW else 1f,
        if (rotBboxH > maxH) maxH / rotBboxH else 1f,
        1f,
    )
    val targetW = (baseTargetW * rotScale).coerceAtLeast(1f)
    val targetH = (baseTargetH * rotScale).coerceAtLeast(1f)

    // The white box needs to contain the *rotated* image, not the unrotated
    // image. The image is drawn at (targetW, targetH), then rotated; the
    // rotated AABB is (targetW*|cos| + targetH*|sin|, targetW*|sin| +
    // targetH*|cos|). Make the box exactly that size and center the image
    // inside it — the rotated corners now land on the box edges, not past
    // them.
    val boxW = targetW * absCos + targetH * absSin
    val boxH = targetW * absSin + targetH * absCos

    val originW = imageScreenWidth.coerceAtLeast(1f)
    val originH = imageScreenHeight.coerceAtLeast(1f)

    val currentW = originW + (targetW - originW) * eased
    val currentH = originH + (targetH - originH) * eased
    val currentBoxW = boxW * eased
    val currentBoxH = boxH * eased

    var centerX = imageScreenCenter.x
    var centerY = imageScreenCenter.y

    val leftBound = currentBoxW / 2f + insetW
    val rightBound = vpW - currentBoxW / 2f - insetW
    val topBound = currentBoxH / 2f + insetH
    val bottomBound = vpH - currentBoxH / 2f - insetH
    // Floating-point: when [currentBoxW] lands a few ULPs above [maxW] the
    // bounds can invert (leftBound > rightBound), and Kotlin's [coerceIn]
    // throws on an inverted range. Swap defensively so the clamp is always
    // well-defined.
    centerX = centerX.coerceIn(minOf(leftBound, rightBound), maxOf(leftBound, rightBound))
    centerY = centerY.coerceIn(minOf(topBound, bottomBound), maxOf(topBound, bottomBound))

    val magnifierLeft = centerX - currentBoxW / 2f
    val magnifierTop = centerY - currentBoxH / 2f

    // Center the image inside the (rotated-AABB-sized) box.
    val imgOffsetX = (currentBoxW - currentW) / 2f
    val imgOffsetY = (currentBoxH - currentH) / 2f

    translate(magnifierLeft, magnifierTop) {
        translate(imgOffsetX, imgOffsetY) {
            rotate(image.rotationDegrees, pivot = Offset(currentW / 2f, currentH / 2f)) {
                scale(
                    scaleX = if (image.flipX) -1f else 1f,
                    scaleY = if (image.flipY) -1f else 1f,
                    pivot = Offset(currentW / 2f, currentH / 2f),
                ) {
                    drawImage(
                        image = bitmap,
                        dstSize = androidx.compose.ui.unit.IntSize(
                            currentW.toInt().coerceAtLeast(1),
                            currentH.toInt().coerceAtLeast(1),
                        ),
                        colorFilter = buildColorFilter(image.colorAdjust),
                    )
                }
            }
        }
    }
}

private fun hitTestImage(state: PockRefUiState, point: Offset, viewportSize: Size): String? {
    return hitTestImagesAt(state, point, viewportSize).firstOrNull()
}

/**
 * Drag-to-edge auto-scroll. Returns a screen-space PanCanvas
 * delta to apply this tick when [pos] is inside the
 * [edgeZonePx] strip of any edge of [viewport], or
 * [Offset.Zero] when the pointer sits in the safe center.
 *
 * Sign convention (matches the existing PanCanvas reducer
 * where `canvasOffset += delta`):
 *   - pointer at LEFT edge → dx POSITIVE  (canvasOffset
 *     increases → content moves right in screen coords →
 *     the LEFT side of the scene comes into view; the
 *     user is dragging an image further off the left edge)
 *   - pointer at RIGHT edge → dx NEGATIVE  (content moves
 *     left in screen → RIGHT side of scene comes into view)
 *   - pointer at TOP edge  → dy POSITIVE
 *   - pointer at BOTTOM   → dy NEGATIVE
 *
 * V2: the original convention was inverted; see the
 * [EdgeAutoScrollTest] regression coverage for the exact
 * per-edge expected sign at the zone boundary, mid-zone,
 * and edge.
 */
internal fun computeEdgeAutoScroll(
    pos: Offset,
    viewport: Size,
    edgeZonePx: Float,
    maxSpeedPx: Float,
): Offset {
    if (edgeZonePx <= 0f || maxSpeedPx <= 0f) return Offset.Zero
    var dx = 0f
    var dy = 0f
    val distLeft = pos.x
    val distRight = viewport.width - pos.x
    val distTop = pos.y
    val distBottom = viewport.height - pos.y
    if (distLeft in 0f..edgeZonePx) {
        val t = 1f - (distLeft / edgeZonePx)
        dx = maxSpeedPx * t * t
    } else if (distRight in 0f..edgeZonePx) {
        val t = 1f - (distRight / edgeZonePx)
        dx = -maxSpeedPx * t * t
    }
    if (distTop in 0f..edgeZonePx) {
        val t = 1f - (distTop / edgeZonePx)
        dy = maxSpeedPx * t * t
    } else if (distBottom in 0f..edgeZonePx) {
        val t = 1f - (distBottom / edgeZonePx)
        dy = -maxSpeedPx * t * t
    }
    return Offset(dx, dy)
}

private fun hitTestImagesAt(state: PockRefUiState, point: Offset, viewportSize: Size): List<String> {
    return state.images.sortedByDescending { it.zIndex }.mapNotNull { image ->
        val mappedCenter = Offset(
            point.x - (viewportSize.width / 2f) - state.canvasOffset.x,
            point.y - (viewportSize.height / 2f) - state.canvasOffset.y,
        )
        val localPoint = Offset(
            x = (mappedCenter.x / state.canvasScale) - image.center.x,
            y = (mappedCenter.y / state.canvasScale) - image.center.y,
        )
        val matrix = Matrix().apply {
            postRotate(-image.rotationDegrees)
            postScale(if (image.flipX) -1f else 1f, if (image.flipY) -1f else 1f)
        }
        val pts = floatArrayOf(localPoint.x, localPoint.y)
        matrix.mapPoints(pts)
        val halfWidth = (image.displaySize.width * image.scale) / 2f
        val halfHeight = (image.displaySize.height * image.scale) / 2f
        if (pts[0] in -halfWidth..halfWidth && pts[1] in -halfHeight..halfHeight) image.id else null
    }
}

private fun isNearImageEdge(
    state: PockRefUiState,
    point: Offset,
    image: SceneImage,
    viewportSize: Size,
    thresholdPx: Float,
): Boolean {
    val mappedCenter = Offset(
        point.x - (viewportSize.width / 2f) - state.canvasOffset.x,
        point.y - (viewportSize.height / 2f) - state.canvasOffset.y,
    )
    val localPoint = Offset(
        x = (mappedCenter.x / state.canvasScale) - image.center.x,
        y = (mappedCenter.y / state.canvasScale) - image.center.y,
    )
    val matrix = Matrix().apply {
        postRotate(-image.rotationDegrees)
        postScale(if (image.flipX) -1f else 1f, if (image.flipY) -1f else 1f)
    }
    val pts = floatArrayOf(localPoint.x, localPoint.y)
    matrix.mapPoints(pts)
    val halfWidth = (image.displaySize.width * image.scale) / 2f
    val halfHeight = (image.displaySize.height * image.scale) / 2f
    val thresholdWorld = thresholdPx / state.canvasScale
    val distLeft = pts[0] - (-halfWidth)
    val distRight = halfWidth - pts[0]
    val distTop = pts[1] - (-halfHeight)
    val distBottom = halfHeight - pts[1]
    return distLeft < thresholdWorld || distRight < thresholdWorld ||
        distTop < thresholdWorld || distBottom < thresholdWorld
}

/**
 * World-space AABB of the visible viewport, padded by [VIEWPORT_BUFFER_PX] in
 * screen pixels (converted to world units via canvasScale). Used by both the
 * draw-loop cull and the LaunchedEffect decode-priority filter. A scale of 0
 * or a viewport of 0×0 yields an empty rect at (0,0,0,0) so the cull rejects
 * everything until the first onSizeChanged fires.
 */
internal fun worldViewportRect(
    canvasOffset: Offset,
    canvasScale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Rect {
    if (canvasScale <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
        return Rect(0f, 0f, 0f, 0f)
    }
    val bufferWorld = VIEWPORT_BUFFER_PX / canvasScale
    val halfW = viewportWidth / 2f
    val halfH = viewportHeight / 2f
    val left = (-canvasOffset.x - halfW) / canvasScale - bufferWorld
    val right = (-canvasOffset.x + halfW) / canvasScale + bufferWorld
    val top = (-canvasOffset.y - halfH) / canvasScale - bufferWorld
    val bottom = (-canvasOffset.y + halfH) / canvasScale + bufferWorld
    return Rect(left, top, right, bottom)
}

/**
 * Axis-aligned world-space bounding box of an image, computed from the
 * image's center + (displaySize * scale) / 2. This is the un-rotated AABB —
 * a conservative (i.e. larger) approximation of the rotated bounds, which is
 * fine for culling because it never rejects a visible image. We avoid the
 * extra trig of a rotated AABB on the hot draw path.
 */
internal fun imageWorldAabb(image: SceneImage): Rect {
    val halfW = (image.displaySize.width * image.scale) / 2f
    val halfH = (image.displaySize.height * image.scale) / 2f
    return Rect(
        left = image.center.x - halfW,
        top = image.center.y - halfH,
        right = image.center.x + halfW,
        bottom = image.center.y + halfH,
    )
}

/**
 * World-space axis-aligned bounding box of an image AFTER its rotation
 * is applied. Used by the minimap (and the scene-bbox calculation that
 * drives the minimap panel shape) so that a 45° / arbitrary-rotation
 * image shows up in the minimap with the same footprint it has in the
 * main canvas — not as a tall portrait rectangle that no longer
 * corresponds to the rotated rendering the user is actually looking at.
 *
 * Math: an unrotated rectangle of half-extents (halfW, halfH) rotated
 * by angle θ has an AABB with half-extents
 *   aabbHalfW = |cos θ| · halfW + |sin θ| · halfH
 *   aabbHalfH = |sin θ| · halfW + |cos θ| · halfH
 * (sum of the rotated unit-axis projections). Flips don't change the
 * AABB since they only mirror the content, not its extent.
 *
 * This is more expensive than [imageWorldAabb] (one sin + cos + a few
 * multiplies per image). The minimap only draws a handful of small
 * rectangles per frame, so the cost is negligible. The main canvas's
 * hot draw path keeps using the axis-aligned [imageWorldAabb] for
 * culling, where the slight over-inclusion of a rotated image is fine
 * and skipping the trig is worth it.
 */
internal fun imageRotatedAabb(image: SceneImage): Rect {
    val halfW = (image.displaySize.width * image.scale) / 2f
    val halfH = (image.displaySize.height * image.scale) / 2f
    val theta = Math.toRadians(image.rotationDegrees.toDouble())
    val absCos = kotlin.math.abs(kotlin.math.cos(theta)).toFloat()
    val absSin = kotlin.math.abs(kotlin.math.sin(theta)).toFloat()
    val aabbHalfW = absCos * halfW + absSin * halfH
    val aabbHalfH = absSin * halfW + absCos * halfH
    return Rect(
        left = image.center.x - aabbHalfW,
        top = image.center.y - aabbHalfH,
        right = image.center.x + aabbHalfW,
        bottom = image.center.y + aabbHalfH,
    )
}

private fun rectsIntersect(a: Rect, b: Rect): Boolean =
    a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

private fun buildColorFilter(adjust: ColorAdjust): ColorFilter? {
    val matrix = buildColorMatrix(adjust) ?: return null
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
private fun UnsavedChangesDialog(isSaving: Boolean, onAction: (PockRefAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onAction(PockRefAction.UnsavedDialogCancel) },
        title = { Text("未保存的更改") },
        text = {
            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("保存中...")
                }
            } else {
                Text("此文件有未保存的更改。关闭前是否保存？")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(PockRefAction.UnsavedDialogSave) },
                enabled = !isSaving,
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onAction(PockRefAction.UnsavedDialogDiscard) },
                    enabled = !isSaving,
                ) {
                    Text("不保存", color = if (isSaving) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = { onAction(PockRefAction.UnsavedDialogCancel) },
                    enabled = !isSaving,
                ) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun BatchOptimizeConfirmDialog(
    optimizableCount: Int,
    totalCount: Int,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("图像优化") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("发现 $optimizableCount/$totalCount 张可优化图像")
                Text(
                    "优化参数：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "• 质量压缩至 70%-80%\n• PNG 转换为 WebP（保留透明度）\n• 小于 50KB 跳过\n• 大于 500KB 优先压缩",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("优化并保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("直接保存")
            }
        },
    )
}

@Composable
private fun BatchOptimizeProgressOverlay(
    state: com.pockref.app.model.BatchOptimizeState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (state.isPaused) "已暂停" else "图像优化中",
                    style = MaterialTheme.typography.titleMedium,
                )

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "${state.completed + state.failed + state.skipped}/${state.total}",
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (state.currentImageName.isNotEmpty() && !state.isPaused) {
                    Text(
                        state.currentImageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val savedMB = "%.1f".format(state.savedBytes.toFloat() / (1024 * 1024))
                    Text(
                        "已节省 $savedMB MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.failed > 0) {
                        Text(
                            "${state.failed} 张失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isPaused) {
                        TextButton(onClick = onResume) {
                            Text("继续")
                        }
                    } else {
                        TextButton(onClick = onPause) {
                            Text("暂停")
                        }
                    }
                    TextButton(onClick = onCancel) {
                        Text("取消", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchOptimizeFailureDialog(
    failedCount: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("优化完成") },
        text = {
            Text("图像优化完成，但 $failedCount 张图像处理失败。你可以重试失败的图像，或忽略继续。")
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("重试失败项")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * Scene-name rename dialog. Shown when the user long-presses the
 * scene-name Text in the top toolbar. The initial value comes from
 * [PockRefUiState.renameDialogValue] (set by the reducer to the
 * sanitized stored name); user input is dispatched as
 * [PockRefAction.ConfirmRenameScene] which goes back through
 * [SceneNaming.sanitizeForStorage] before persisting. Cancel
 * simply clears the dialog field. The dialog does not auto-close
 * on background because we don't have a separate "saving" indicator
 * — the unsaved-changes marker updates on the next reducer tick.
 */
@Composable
private fun RenameSceneDialog(
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名场景") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("场景名") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * Export dialog rendered when [PockRefUiState.showExportDialog]
 * is true. Pulled out into its own @Composable so the
 * [PockRefApp] function body can pass the launchers as
 * parameters (rather than capturing them through deeply nested
 * lambdas — see the [PockRefApp] note for why we did this
 * refactor).
 *
 * [exportPngLauncher] / [exportJpgLauncher] are the document
 * pickers for image/png and image/jpeg respectively. Each
 * button is labeled in plain language (lossless / smaller) and
 * the file extension so the user can make an informed pick even
 * if they don't know what PNG / JPEG are.
 *
 * In multi-select mode ([isMulti] = true), the format buttons
 * launch [exportBatchPngLauncher] / [exportBatchJpgLauncher]
 * instead — those are OpenDocumentTree pickers, and the result
 * is a folder URI. The export logic then writes one file per
 * selected image as a sibling of that folder, using the SAF
 * filename-collision API.
 */
@Composable
private fun ExportDialogHost(
    selectedName: String,
    isMulti: Boolean,
    selectedCount: Int,
    exportBatchInitialUri: Uri?,
    exportPngLauncher: ActivityResultLauncher<String>,
    exportJpgLauncher: ActivityResultLauncher<String>,
    exportBatchPngLauncher: ActivityResultLauncher<Uri?>,
    exportBatchJpgLauncher: ActivityResultLauncher<Uri?>,
    onDismiss: () -> Unit,
) {
    val titleText = if (isMulti) "导出 $selectedCount 张图片" else "导出当前选中的图"
    val helperText = if (isMulti) {
        "选择导出格式,再选一个目标文件夹。每张图会以它自己的" +
            "名字写入(同名会自动加 \"(1)\"、\"(2)\" 区分)。"
    } else {
        "选择导出格式。PNG 无损(适合透明背景或后续编辑)," +
            "JPG 体积更小(适合照片类内容,不支持透明)。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    helperText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (isMulti) {
                                exportBatchPngLauncher.launch(exportBatchInitialUri)
                            } else {
                                exportPngLauncher.launch("$selectedName.png")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("PNG\n(无损)", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            if (isMulti) {
                                exportBatchJpgLauncher.launch(exportBatchInitialUri)
                            } else {
                                exportJpgLauncher.launch("$selectedName.jpg")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("JPG\n(更小)", textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

// V_NOW (re-applied): settings panel + HSV color wheel +
// gesture toggles. The user's pre-merge spec (commits
// 78bee94 + a701a31 + a100580) had these as a 5-section
// ModalBottomSheet. 8d9d74e replaced the toolbar (and
// with it the gear entry + sheet) with its inline
// toolbar; this commit brings the sheet back.

// ─── SettingsPanel ──────────────────────────────────────
/**
 * 5-section settings sheet. Sections in order:
 *   1. 边缘吸附 (edge snap — M3 Switch)
 *   2. 旋转角度吸附 (rotation snap — M3 Switch)
 *   3. 主题 (theme — SingleChoiceSegmentedButtonRow:
 *      SYSTEM / LIGHT / DARK)
 *   4. 背景颜色 (background color — BackgroundColorPanel
 *      preset swatches, plus the HSV wheel inline when the
 *      active preset is [BackgroundPreset.Custom])
 *   5. 手势 (7 per-gesture toggles, one per [GestureId] in
 *      the spec's order: single-finger drag → compound
 *      double-tap)
 *
 * The inner [Column] is wrapped in [verticalScroll] because
 * the 5 sections + HSV wheel + 7 toggle rows overflow the
 * default partially-expanded sheet height on portrait
 * phones. Without scroll the bottom gesture
 * (双击空白) gets clipped. The user can drag the
 * sheet up to fully expand, or scroll within the content.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    open: Boolean,
    onDismiss: () -> Unit,
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // V_NOW (tabbed settings): the sheet content switches
        // between TabRow (phone) and NavigationRail (tablet)
        // based on the current window width. Below 600dp wide
        // (typical phone portrait, e.g. 360x780), a TabRow on
        // top keeps the sheet compact and phone-thumb reachable;
        // at >= 600dp (tablet landscape, e.g. 1280x800), a
        // left-side NavigationRail frees the right pane for the
        // content and matches the M3 large-screen idiom.
        val configuration = LocalConfiguration.current
        val isWide = configuration.screenWidthDp >= 600
        if (isWide) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                SettingsNavigationRail(
                    selectedTab = state.settingsTab,
                    onTabSelected = { tab ->
                        onAction(PockRefAction.SetSettingsTab(tab))
                    },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SettingsTabContent(
                        state = state,
                        onAction = onAction,
                        selectedTab = state.settingsTab,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, top = 16.dp),
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                        )
                    }
                }
                SettingsTabRow(
                    selectedTab = state.settingsTab,
                    onTabSelected = { tab ->
                        onAction(PockRefAction.SetSettingsTab(tab))
                    },
                )
                HorizontalDivider()
                SettingsTabContent(
                    state = state,
                    onAction = onAction,
                    selectedTab = state.settingsTab,
                )
            }
        }
    }
}

/**
 * A single row in the settings sheet — title on top,
 * helper text below, Switch on the right. Mirrors the M3
 * list-item pattern with a small vertical padding gap so
 * the rows feel separated without using dividers.
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
// ─── CustomColorWheel (HSV) ──────────────────────────────
/**
 * HSV color picker. Top → bottom:
 *   1. Preview swatch + RGB readout
 *   2. Circular picker: outer ring = hue wheel (0–360°
 *      sweep, Brush.sweepGradient); inner disc = radial
 *      gradient (white at centre → current hue at edge) so
 *      radial position picks saturation. The user's touch
 *      point's angle picks hue, distance from centre picks
 *      saturation. The white-fill / black-edge dot marks
 *      the current (hue, sat) so the user sees where they
 *      are before lifting their finger.
 *   3. Value (V) slider for brightness.
 *
 * Used only when the active preset is
 * [BackgroundPreset.Custom]. The parent [SettingsPanel]
 * only renders this composable under that condition.
 */
@Composable
private fun CustomColorWheel(
    currentColor: Color,
    onConfirm: (Color) -> Unit,
    onCancel: () -> Unit,
) {
    // Local preview state (V_NOW: preview/commit pattern). Dragging
    // the wheel or moving any of the 3 HSV sliders only mutates
    // these [hue] / [sat] / [value] remember-state variables — the
    // global [PockRefUiState.backgroundColor] is NOT touched until
    // the user taps "确定". This matches the user's explicit spec
    // "在色环或 HSV 滑块上的所有操作均**仅作为实时预览**,不立即
    // 应用为选定颜色".
    //
    // [remember(currentColor)] re-initialises the preview to the
    // just-saved (or still-original) colour each time the parent
    // re-passes [currentColor] — e.g. when the user reopens the
    // picker after a prior "确定"/"取消" cycle.
    val initialHsv = remember(currentColor) { rgbToHsv(currentColor) }
    var hue by remember(currentColor) { mutableStateOf(initialHsv[0]) }
    var sat by remember(currentColor) { mutableStateOf(initialHsv[1]) }
    var value by remember(currentColor) { mutableStateOf(initialHsv[2]) }
    val previewColor = Color.hsv(hue, sat, value)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Preview + Original swatch row.
        // [previewColor] is the live HSV-composed colour (left,
        // 48dp, the bigger one — this is the swatch the user is
        // adjusting). [currentColor] is the colour that was active
        // when the picker opened, i.e. the colour the user will
        // get back to if they tap "取消" (right, 32dp, smaller).
        // Both are bordered so the contrast against the sheet's
        // translucent surface is obvious regardless of which
        // underlying hue/S/V the user lands on.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(previewColor)
                    .border(1.dp, Color(0x44000000), RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(currentColor)
                    .border(1.dp, Color(0x44000000), RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "预览 / 调整前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "R ${(previewColor.red * 255).toInt()}  " +
                        "G ${(previewColor.green * 255).toInt()}  " +
                        "B ${(previewColor.blue * 255).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 2. Hue ring (outer) + triangle S/V picker (inner).
        // The ring is unchanged from the previous design —
        // angle picks hue (0–360°), V-dependent sweep colours
        // so the ring dims as V drops. The inner area is now
        // an equilateral triangle (white at top-left, black at
        // bottom-left, full hue at right) replacing the
        // previous radial SV disc. Triangle vertices are
        // inscribed in innerRadius (the ring's inner edge
        // minus a 4dp gap). Per-row horizontal scanline fills
        // the triangle — left side is a white-to-black vertical
        // gradient (constant in y), right side is the full
        // hue; mixing them along the row gives the standard
        // 2D HSV interpolation.
        //
        // Touch region split:
        //   - distance >= innerRadius: ring → H
        //   - distance <  innerRadius: triangle → S+V
        // The two regions are independent: a drag that started
        // on the ring keeps updating H even if the finger
        // crosses into the inner area, and vice versa. The
        // user can lift + re-touch to switch regions.
        //
        // Markers:
        //   - Ring marker (white + black border, ~10dp
        //     radius) sits on the ring's mid-radius at the
        //     hue angle.
        //   - Triangle marker (white + black border, ~8dp
        //     radius) sits inside the triangle at the
        //     position computed from (sat, value) via the
        //     inverse barycentric formula:
        //       Px = cx + R·(3s − 1) / 2
        //       Py = cy + (R·√3/2)·(1 + s − 2v)
        Box(
             modifier = Modifier
                 .fillMaxWidth()
                 .height(200.dp)
                 .pointerInput(Unit) {
                     // V_NOW (user bug report — "ring doesn't
                     // support touch drag, can only tap"):
                     // [detectDragGestures] gates both
                     // [onDragStart] and [onDrag] on the
                     // Compose touch-slop (≈8dp) — the
                     // gesture doesn't fire until the
                     // user's first move crosses that
                     // threshold, which made the ring
                     // feel "dead" for slow drags and
                     // small movements (the finger had to
                     // twitch by ~8dp before the marker
                     // snapped to the touch position).
                     // We replace it with
                     // [awaitEachGesture] + a direct
                     // [awaitPointerEvent] loop, which:
                     //   - dispatches on the INITIAL
                     //     down (no slop gate) so a tap
                     //     immediately positions the
                     //     marker
                     //   - dispatches on every subsequent
                     //     move event (no slop gate) so
                     //     slow drags track the finger
                     //     smoothly
                     //   - exits cleanly on release (the
                     //     `change.pressed == false` break
                     //     drops out of the while loop and
                     //     the awaitEachGesture re-arms
                     //     for the next touch)
                     // The dispatch is the same
                     // [updateHueFromRingTouch] +
                     // [updateSvFromTriangleTouch] pair,
                     // which already ignores touches
                     // outside its region (ring /
                     // triangle respectively), so the
                     // region split is preserved.
                     awaitEachGesture {
                         val down = awaitFirstDown(requireUnconsumed = false)
                         val downPos = down.position
                         updateHueFromRingTouch(downPos) { h ->
                             hue = h
                         }
                         updateSvFromTriangleTouch(downPos) { s, v ->
                             sat = s
                             value = v
                         }
                         down.consume()
                         while (true) {
                             val event = awaitPointerEvent()
                             val change = event.changes
                                 .firstOrNull { it.id == down.id }
                                 ?: break
                             if (!change.pressed) break
                             change.consume()
                             val pos = change.position
                             updateHueFromRingTouch(pos) { h ->
                                 hue = h
                             }
                             updateSvFromTriangleTouch(pos) { s, v ->
                                 sat = s
                                 value = v
                             }
                         }
                     }
                 }
                .drawBehind {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val outerRadius = minOf(centerX, centerY) - 2f
                    val ringThickness = outerRadius * 0.18f
                    val innerRadius = outerRadius - ringThickness - 4f
                    val sqrt3 = kotlin.math.sqrt(3f)
                    val triHalfH = innerRadius * sqrt3 / 2f
                    val triR = innerRadius

                    // Hue ring (outer). V-dependent sweep so
                    // the ring dims as V drops.
                    val ringColorsFullBrightness = listOf(
                        Color.hsv(0f, 1f, 1f),
                        Color.hsv(60f, 1f, 1f),
                        Color.hsv(120f, 1f, 1f),
                        Color.hsv(180f, 1f, 1f),
                        Color.hsv(240f, 1f, 1f),
                        Color.hsv(300f, 1f, 1f),
                        Color.hsv(360f, 1f, 1f),
                    )
                    rotate(degrees = -90f, pivot = Offset(centerX, centerY)) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = ringColorsFullBrightness,
                                center = Offset(centerX, centerY),
                            ),
                            radius = outerRadius - ringThickness / 2f,
                            style = Stroke(width = ringThickness),
                        )
                    }

                    // Triangle (inner). Per-row horizontal
                    // gradient from (white-to-black left mix)
                    // to (full hue right). 1px step keeps the
                    // gradient smooth on a 200dp box (~400
                    // rows on a 2x screen).
                    val fullColor = Color.hsv(hue, 1f, 1f)
                    val triangleLeft = centerX - triR / 2f
                    val triangleTop = centerY - triHalfH
                    val triangleBottom = centerY + triHalfH
                    val pixelStep = 1f
                    var y = triangleTop
                    while (y <= triangleBottom) {
                        // Left color: white at top (t=1) to
                        // black at bottom (t=0).
                        val t = (centerY + triHalfH - y) / (2f * triHalfH)
                        val leftColor = androidx.compose.ui.graphics.lerp(Color.Black, Color.White, t)
                        // Right boundary: AB edge for top half
                        // (y < centerY), AC edge for bottom
                        // half (y >= centerY).
                        val xRight = if (y < centerY) {
                            centerX + triR * (1f + 1.5f * (y - centerY) / triHalfH)
                        } else {
                            centerX + triR * (1f - 1.5f * (y - centerY) / triHalfH)
                        }
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(leftColor, fullColor),
                                startX = triangleLeft,
                                endX = xRight,
                            ),
                            topLeft = Offset(triangleLeft, y),
                            size = Size(xRight - triangleLeft, pixelStep),
                        )
                        y += pixelStep
                    }

                    // Ring marker (H). Sits on the ring's
                    // mid-radius at the hue angle.
                    //
                    // V_NOW (user bug report — "ring marker
                    // doesn't match the selected hue"): the
                    // ring gradient is drawn inside
                    // `rotate(-90f)` so that hue=0° (red)
                    // sits at the top (12 o'clock) and the
                    // sweep goes clockwise. The touch
                    // handler computes hue from
                    // `atan2(dy, dx) + 90°` — also the
                    // "0° at top, clockwise" convention.
                    // The marker MUST use the same
                    // convention or it lands 180° away
                    // from the actual hue. The previous
                    // formula `cos(hue+90°), sin(hue+90°)`
                    // was the standard math convention
                    // (0° at right, counter-clockwise) and
                    // put hue=0° at the BOTTOM, so the
                    // user saw a marker at 7-8 o'clock
                    // when the slider said 61° (which
                    // should be 1-2 o'clock). The
                    // "0° at top, clockwise" position
                    // formula in screen coords (y+ is
                    // down) is:
                    //   x = cx + r · sin(θ)
                    //   y = cy − r · cos(θ)
                    // which matches `atan2(dy, dx) + 90°`
                    // one-for-one (verified by walking
                    // through θ = 0° / 60° / 180° / 270°).
                    val ringMarkerR = ringThickness * 0.55f
                    val hueRadians = hue.toDouble() * Math.PI / 180.0
                    val ringMarkerRadius = outerRadius - ringThickness / 2f
                    val ringMarkerX = centerX + ringMarkerRadius * sin(hueRadians).toFloat()
                    val ringMarkerY = centerY - ringMarkerRadius * cos(hueRadians).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = ringMarkerR,
                        center = Offset(ringMarkerX, ringMarkerY),
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = ringMarkerR,
                        center = Offset(ringMarkerX, ringMarkerY),
                        style = Stroke(width = 1.5f),
                    )

                    // Triangle marker (S, V). Sits inside the
                    // triangle at the (s, v) position via the
                    // inverse barycentric formula. S is always
                    // ≤ V (the sliders enforce this), so no
                    // clamping is needed.
                    val triMarkerR = ringThickness * 0.45f
                    val triMarkerX = centerX + triR * (3f * sat - 1f) / 2f
                    val triMarkerY = centerY + triHalfH * (1f + sat - 2f * value)
                    drawCircle(
                        color = Color.White,
                        radius = triMarkerR,
                        center = Offset(triMarkerX, triMarkerY),
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = triMarkerR,
                        center = Offset(triMarkerX, triMarkerY),
                        style = Stroke(width = 1.5f),
                    )
                },
        )

        // 3. H slider. [hue] is 0-360° internally; the M3 Slider
        // is normalised 0-1 so we multiply/divide at the boundary.
        // The label shows the integer degree value the user is
        // seeing on the ring marker.
        SliderRow(
            label = "H",
            valueText = "${hue.toInt()}°",
            value = hue / 360f,
            onValueChange = { hue = it * 360f },
        )
        // 4. S slider. [sat] is 0-1 internally, displayed as 0-100%.
        // When S exceeds V the triangle can't represent it (the
        // triangle only covers V ≥ S), so we raise V to match S.
        // This keeps the marker inside the triangle and ensures
        // it actually moves when the user drags S.
        SliderRow(
            label = "S",
            valueText = "${(sat * 100).toInt()}%",
            value = sat,
            onValueChange = { newS ->
                sat = newS
                if (newS > value) value = newS
            },
        )
        // 5. V slider. Same shape as S, label "V" / 0-100%.
        // When V drops below S the triangle can't represent it,
        // so we lower S to match V.
        SliderRow(
            label = "V",
            valueText = "${(value * 100).toInt()}%",
            value = value,
            onValueChange = { newV ->
                value = newV
                if (newV < sat) sat = newV
            },
        )

        // 6. Confirm / Cancel buttons. The user spec requires
        // explicit commit: drag/slider changes are preview-only
        // until "确定" is pressed. "取消" discards the preview
        // (just closes the picker — preview state is local and
        // goes away with the composable). "确定" calls the
        // parent's onConfirm with the composed preview colour;
        // the parent dispatches SetBackgroundColor which both
        // applies the colour AND closes the picker (the
        // existing setBackgroundColor reducer already does the
        // close, so the caller can dispatch one action and be
        // done).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            androidx.compose.material3.Button(
                onClick = { onConfirm(previewColor) },
                modifier = Modifier.weight(1f),
            ) {
                Text("确定")
            }
        }
    }
}

/**
 * Single labelled slider row: "H  240°" / slider on its own line.
 * M3's built-in [Slider] doesn't render a value indicator, so the
 * label + value pair sits on a row above the track. Width is
 * `fillMaxWidth` so each row spans the full sheet content area.
 */
@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
        )
    }
}

/**
 * Pure helper — convert an sRGB [Color] to HSV triple
 * (H in [0, 360), S in [0, 1], V in [0, 1]). Used to
 * initialise the HSV state of [CustomColorWheel] from the
 * colour the user picked via the preset swatch row.
 */
private fun rgbToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val v = max
    val s = if (max == 0f) 0f else (max - min) / max
    val h = when {
        max == min -> 0f
        max == r -> ((g - b) / (max - min)) * 60f
        max == g -> ((b - r) / (max - min) + 2) * 60f
        else -> ((r - g) / (max - min) + 4) * 60f
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, s, v)
}

/**
 * 7-stop colour stops for the hue ring. Standard HSV
 * sweep: red → yellow → green → cyan → blue → magenta → red.
 * Each segment is one third of the circle, so the gradient
 * has no visual banding at the colour boundaries.
 */
private val hsvRingColors: List<Color> = listOf(
    Color(0xFFFF0000), // 0   red
    Color(0xFFFFFF00), // 60  yellow
    Color(0xFF00FF00), // 120 green
    Color(0xFF00FFFF), // 180 cyan
    Color(0xFF0000FF), // 240 blue
    Color(0xFFFF00FF), // 300 magenta
    Color(0xFFFF0000), // 360 back to red
)

/**
 * Touch handler for the hue ring in [CustomColorWheel].
 * Computes hue (0–360°) from the touch offset's angle
 * relative to the centre, then calls [apply] with the
 * new hue. Touches inside the inner area (distance <
 * innerRadius) are ignored — those belong to the
 * triangle picker. Reads the picker size from the
 * surrounding [PointerInputScope] so callers don't have
 * to thread it through manually.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.ui.input.pointer.PointerInputScope.updateHueFromRingTouch(
    offset: Offset,
    apply: (hue: Float) -> Unit,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outerRadius = minOf(centerX, centerY) - 2f
    val ringThickness = outerRadius * 0.18f
    val innerRadius = outerRadius - ringThickness - 4f
    val dx = offset.x - centerX
    val dy = offset.y - centerY
    val distance = kotlin.math.hypot(dx, dy)
    if (distance < innerRadius) return  // in inner area; triangle picker handles this
    if (distance > outerRadius) return
        // V_NOW (user bug report — "page-scroll swipe
        // through the picker area changes the hue"): the
        // Box around the ring is `fillMaxWidth`.
        // height(200.dp)` — the ring is centred, but the
        // 4 box corners (and the strips above / below the
        // ring) extend well past `outerRadius`. Without
        // this guard, a vertical drag that starts in a
        // blank corner was still classified as a ring
        // touch and re-aimed the hue marker. Now only
        // touches in the ring annulus
        // (`innerRadius ≤ distance ≤ outerRadius`) move
        // the marker; everything else is ignored (the
        // pointerInput modifier still receives the event
        // for the Box, but no setter is called, so the
        // hue stays put and the gesture doesn't hijack
        // vertical scrolling).
    // Angle in degrees, 0 = top, clockwise.
    val angleDeg = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0) + 360.0) % 360.0
    apply(angleDeg.toFloat())
}

/**
 * Touch handler for the inner triangle S/V picker in
 * [CustomColorWheel]. Equilateral triangle inscribed in
 * a circle of radius innerRadius. Vertices in the
 * box-centred coordinate system:
 *   A = (cx + R, cy)         right   = full hue    = (s=1, v=1)
 *   B = (cx − R/2, cy − h)   top    = white       = (s=0, v=1)
 *   C = (cx − R/2, cy + h)   bottom = black       = (s=0, v=0)
 * where h = R·√3/2. A point P inside the triangle has
 * barycentric coordinates (α, β, γ) for A, B, C:
 *   α = (2·dx + R) / (3·R)
 *   β = (R − dx) / (3·R) − dy / (R·√3)
 *   γ = (R − dx) / (3·R) + dy / (R·√3)
 * S = α, V = α + β. Touches outside the triangle
 * (distance ≥ innerRadius, or any barycentric
 * coordinate < 0) are clamped to the nearest point on
 * the triangle's boundary (clamp α, β, γ to [0, 1] then
 * renormalise so they sum to 1).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.ui.input.pointer.PointerInputScope.updateSvFromTriangleTouch(
    offset: Offset,
    apply: (saturation: Float, value: Float) -> Unit,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outerRadius = minOf(centerX, centerY) - 2f
    val ringThickness = outerRadius * 0.18f
    val innerRadius = outerRadius - ringThickness - 4f
    val dx = offset.x - centerX
    val dy = offset.y - centerY
    val distance = kotlin.math.hypot(dx, dy)
    if (distance >= innerRadius) return  // in ring area; hue ring handles this
    val R = innerRadius
    val sqrt3 = kotlin.math.sqrt(3f)
    val alpha = (2f * dx + R) / (3f * R)
    val beta = (R - dx) / (3f * R) - dy / (R * sqrt3)
    val gamma = (R - dx) / (3f * R) + dy / (R * sqrt3)
    val a = alpha.coerceIn(0f, 1f)
    val b = beta.coerceIn(0f, 1f)
    val g = gamma.coerceIn(0f, 1f)
    val sum = a + b + g
    if (sum <= 0f) return
    val an = a / sum
    val bn = b / sum
    apply(an, an + bn)
}
// ─── BackgroundColorPanel (preset swatches) ──────────────
/**
 * Horizontal row of preset colour chips. The 7 fixed
 * presets are color swatches; the 8th chip ("Custom")
 * opens the inline HSV picker (via the parent
 * [SettingsPanel] conditionally rendering
 * [CustomColorWheel] under it).
 */
@Composable
private fun BackgroundColorPanel(
    currentColor: Color,
    onPreset: (BackgroundPreset) -> Unit,
) {
    val activePreset = BackgroundPreset.fromColor(currentColor)
    val customDisplayColor = if (activePreset == BackgroundPreset.Custom) currentColor else Color(0xFF808080)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackgroundPreset.values().forEach { preset ->
            if (preset == BackgroundPreset.Custom) {
                BackgroundColorChip(
                    color = customDisplayColor,
                    label = androidx.compose.ui.res.stringResource(preset.labelRes),
                    isSelected = activePreset == BackgroundPreset.Custom,
                    isCustom = true,
                    onClick = { onPreset(preset) },
                )
            } else {
                BackgroundColorChip(
                    color = preset.color,
                    label = androidx.compose.ui.res.stringResource(preset.labelRes),
                    isSelected = activePreset == preset,
                    isCustom = false,
                    onClick = { onPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun BackgroundColorChip(
    color: Color,
    label: String,
    isSelected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x44000000)
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(
                color = if (isCustom) Color.Transparent else color,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isCustom) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                color,
                                Color(0xFFE53935),
                                Color(0xFFFB8C00),
                                Color(0xFFFDD835),
                                Color(0xFF43A047),
                                Color(0xFF1E88E5),
                                Color(0xFF8E24AA),
                            ),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
        }
        Text(
            text = label,
            color = if (isCustom) Color.Transparent else pickChipLabelColor(color),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/**
 * Pick a black/white label color based on the swatch
 * luminance so labels stay readable on both light and
 * dark preset colors.
 */
private fun pickChipLabelColor(swatch: Color): Color {
    val argb = swatch.toArgb()
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance > 0.6f) Color(0xFF111111) else Color(0xFFFFFFFF)
}

/**
 * Bottom-right action panel — replaces the previous single-FAB
 * (just auto-layout). Now a unified Surface that holds a
 * vertical list of actions, matching the top toolbar's "always
 * visible anchor + on-demand action set" pattern.
 *
 * Visual states:
 *   - **Collapsed** (default): 44dp FAB at the bottom showing the
 *     auto-layout icon. Tap = run [PockRefAction.ExecuteAutoLayout];
 *     long-press = expand the panel to reveal the other 4 actions.
 *   - **Expanded**: 4 additional 44dp icon cells (box-select /
 *     settings / import / save) grow upward from the FAB via
 *     [expandVertically]. Every cell is the same size as the FAB
 *     so the panel reads as a 44dp-wide column of 5 squares
 *     stacked vertically. The FAB still runs auto-layout on tap;
 *     long-press now collapses the panel back to the 44dp FAB
 *     state.
 *
 * Auto-collapse: 3 seconds after the last pointer Enter/Press
 * inside the panel, fires [PockRefAction.SetBottomRightPanelExpanded(false)].
 * Same contract as the top toolbar (see [TopToolbar]) but with
 * no sub-panels to gate on — the timer only depends on
 * [isInteracting] and the current [state.bottomRightPanelExpanded].
 *
 * The whole panel sits at [Alignment.BottomEnd] with
 * [Dimens.CornerInset] (24dp) from the window edges, so the
 * bottom edge of the FAB sits at the same baseline as the
 * bottom-left minimap's bottom edge — the two bottom-corner
 * elements share the same 24dp inset by design.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.BottomRightActionPanel(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onImport: () -> Unit,
    onOpenScene: () -> Unit,
    onSaveScene: () -> Unit,
    onExportCanvas: () -> Unit,
    // V_NOW: chrome-mutex inverse counterpart of
    // [TopToolbar]'s onAction wrap at the call site.
    // When the top toolbar's 3-dots menu ([menuExpanded])
    // is open and the user starts interacting with the
    // right-bottom panel (FAB tap / long-press / any
    // list item), the top toolbar's menu must collapse
    // so the user can see what they're tapping. The
    // parent passes a setter for the local [menuExpanded]
    // state (the toolbar deliberately doesn't own this
    // state — see [TopToolbar]'s KDoc). Idempotent:
    // calling when the menu is already closed is a no-op.
    onMenuDismiss: () -> Unit,
) {
    // V_NOW: same wall-clock polling pattern as the top toolbar.
    // [lastInteractionMs] tracks the wall-clock timestamp of the
    // last REAL interaction (Press / Move-with-pressed / Exit-
    // with-pressed / Release-with-no-pressed). The hover-only
    // events (Enter with no buttons, Exit with no buttons) DO
    // NOT refresh the timestamp — that's the bug the old
    // [isInteracting] pattern had: an S Pen just flying over
    // the panel re-launched the auto-collapse effect on every
    // Enter/Exit, cancelling the in-flight 3s delay so the
    // panel never collapsed while the user was drawing. The
    // polling loop reads [lastInteractionMs] via snapshot,
    // so hover noise can't keep resetting the timer.
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // V_NOW: import 弹窗的状态。点击聚合 Import 按钮 flip 到
    // true,ModalBottomSheet 弹出;关闭时(选项点击 / 外部 dismiss
    // / drag down)回到 false。
    var showImportSheet by remember { mutableStateOf(false) }
    // V_NOW: 当 sheet 打开时 pause auto-collapse(否则 3s 后
    // panel 会收起,导入按钮消失,用户来不及点选项)。
    // 重新 key 上 showImportSheet 之后,LaunchedEffect 在
    // sheet 关闭时会重新启动,自然续接。
    LaunchedEffect(state.bottomRightPanelExpanded, showImportSheet) {
        if (state.bottomRightPanelExpanded && !showImportSheet) {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionMs
                if (elapsed >= 3000L) {
                    onAction(PockRefAction.SetBottomRightPanelExpanded(false))
                    break
                }
                val waitMs = (3000L - elapsed).coerceIn(100L, 1000L)
                delay(waitMs)
            }
        }
    }
    // V_NOW: sheet 关闭时 refresh 计时器(sheet 内的 pointer
    // 事件不经过 panel 的 pointerInput,所以 lastInteractionMs
    // 不会被自动更新;否则 sheet 一关 panel 立刻检测到 elapsed
    // >= 3000L 直接收起)。把这个 effect 单独出来,只在
    // showImportSheet 翻转时跑一次。
    LaunchedEffect(showImportSheet) {
        if (!showImportSheet) {
            lastInteractionMs = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = Dimens.CornerInset, bottom = Dimens.CornerInset)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Press -> {
                                lastInteractionMs = System.currentTimeMillis()
                            }
                            PointerEventType.Move -> {
                                if (event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                            PointerEventType.Exit -> {
                                // Pointer left the panel bounds.
                                // If they were pressing (drag in
                                // progress, e.g. dragging from a
                                // list item to the canvas), keep
                                // the countdown paused. If they
                                // were NOT pressing (just an
                                // unhover), ignore.
                                if (event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                            else -> {
                                // Release / others. If all
                                // pointers are up, the gesture
                                // ended — mark the start of the
                                // idle countdown so the polling
                                // loop sees a fresh "now".
                                if (!event.changes.any { it.pressed }) {
                                    lastInteractionMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
            },
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        tonalElevation = Dimens.ToolbarElevation,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = state.bottomRightPanelExpanded,
                enter = expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                ),
            ) {
                Column {
                    // 1. Box-select mode — always shows
                    //    [PhotoSizeSelectLarge] (the canonical
                    //    "size / region select" Material icon —
                    //    a rectangle with arrows on the corners,
                    //    clearly says "drag a box"). The on/off
                    //    state is conveyed purely by the cyan
                    //    tint: on = primary/cyan, off = default
                    //    onSurfaceVariant. Swapping the icon
                    //    between on/off (e.g. CheckBoxOutlineBlank
                    //    ↔ PhotoSizeSelectLarge) was confusing —
                    //    the click target's purpose ("toggle box
                    //    select") is unchanged, only the resulting
                    //    state differs, so the icon should stay
                    //    the same and the tint carry the state.
                    BottomPanelListItem(
                        icon = Icons.Rounded.PhotoSizeSelectLarge,
                        contentDescription = "Box-select mode",
                        tint = if (state.boxSelectMode) Color(0xFF4FC3F7) else null,
                        onClick = {
                            // V_NOW: chrome-mutex — right-bottom
                            // item tap closes the top toolbar's
                            // 3-dots menu if it's open.
                            onMenuDismiss()
                            onAction(PockRefAction.ToggleBoxSelectMode)
                        },
                    )
                    // 2. Settings — dispatches
                    //    [PockRefAction.SetSettingsSheetOpen(true)]
                    //    which the reducer applies to
                    //    [PockRefUiState.settingsSheetOpen]. The
                    //    [SettingsPanel] ModalBottomSheet (rendered
                    //    at the [PockRefApp] level) reads that field
                    //    and shows itself. This is the same state
                    //    the top toolbar's gear button (when
                    //    restored) will drive, so both entry points
                    //    open the same sheet instance.
                    BottomPanelListItem(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        onClick = {
                            // V_NOW: chrome-mutex.
                            onMenuDismiss()
                            onAction(PockRefAction.SetSettingsSheetOpen(true))
                        },
                    )
                    // 3. Aggregate Import (V_NOW redesign):
                    //    one button that surfaces BOTH
                    //    "导入图片" (OpenMultipleDocuments,
                    //    image/*) AND "打开场景文件"
                    //    (OpenDocument, .pur MIME) via an M3
                    //    [ModalBottomSheet] (the V_NOW
                    //    redesign — first try was a
                    //    [DropdownMenu] but the user found the
                    //    dropdown felt too "weightless": the
                    //    menu flash-disappeared the moment
                    //    auto-collapse fired, and the dropdown
                    //    anchor was visually invisible relative
                    //    to the panel chrome). The bottom sheet
                    //    is rendered at the [BottomRightActionPanel]
                    //    level (sibling of the [Surface]) so it's
                    //    a real popup window independent of the
                    //    panel's auto-collapse lifecycle — see
                    //    [showImportSheet] + the pause-collapse
                    //    [LaunchedEffect] above for the
                    //    coordination.
                    //
                    //    Icon: [Icons.Rounded.FileOpen] — the
                    //    universal "open file" semantic that
                    //    works for both image AND scene file.
                    //    Considered [Input] / [MoveToInbox] /
                    //    [LibraryAdd] / [PostAdd] but FileOpen
                    //    has the strongest cross-format
                    //    recognition (Google Drive uses it for
                    //    "Open with") and matches the other
                    //    chrome icons' visual weight.
                    BottomPanelListItem(
                        icon = Icons.Rounded.FileOpen,
                        contentDescription = "Import",
                        onClick = {
                            // V_NOW: chrome-mutex.
                            onMenuDismiss()
                            showImportSheet = true
                        },
                    )
                    // 4. Export canvas — export the entire
                    //    canvas as a single PNG with
                    //    transparent background.
                    BottomPanelListItem(
                        icon = Icons.Rounded.Image,
                        contentDescription = "Export canvas",
                        onClick = {
                            onMenuDismiss()
                            onExportCanvas()
                        },
                    )
                    // 5. Save — same handler as the top
                    //    toolbar's Save button.
                    BottomPanelListItem(
                        icon = Icons.Rounded.Save,
                        contentDescription = "Save scene",
                        onClick = {
                            // V_NOW: chrome-mutex.
                            onMenuDismiss()
                            onSaveScene()
                        },
                    )
                }
            }
            // Always-visible FAB — the panel's anchor.
            // Gesture contract:
            //   - **Tap** = always run auto-layout (the FAB's
            //     primary action, same in both states).
            //   - **Long-press** = toggle the panel's
            //     expanded state.
            //
            // Size: pinned to [Dimens.BottomPanelChromeSize]
            // (48dp) so the FAB satisfies the M3 minimum touch
            // target (WCAG 2.5.5 Level AAA). The 4 expandable
            // ListItems above also use [Dimens.BottomPanelChromeSize]
            // so the column still reads as 5 identical squares
            // stacked vertically (just one notch larger than the
            // pre-P0-fix 44dp).
            //
            // M3 styling: primaryContainer fill (the M3
            // "primary floating action" colour role) +
            // onPrimaryContainer icon. Distinct from the 4
            // transparent list items above it, so the eye reads
            // the bottom cell as "the primary action" — matches
            // M3's "primary surface = most important affordance"
            // convention (same as Material's "filled button >
            // tonal button > outlined button > text button"
            // emphasis ladder).
            //
            // State layer: [interactionSource] is observed via
            // [collectIsHoveredAsState] / [collectIsPressedAsState]
            // and a translucent primary-tinted layer is composited
            // on top of the primaryContainer fill (8% on hover,
            // 12% on press, per M3 spec). The M3 ripple
            // (default [LocalIndication]) runs on top of that
            // for the transient tap-highlight.
            val fabInteractionSource = remember { MutableInteractionSource() }
            val isFabHovered by fabInteractionSource.collectIsHoveredAsState()
            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabStateLayer = when {
                isFabPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                isFabHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .size(Dimens.BottomPanelChromeSize)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                    )
                    .background(
                        color = fabStateLayer,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                    )
                    .hoverable(interactionSource = fabInteractionSource)
                    .combinedClickable(
                        interactionSource = fabInteractionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            // V_NOW: chrome-mutex — both FAB
                            // tap (run auto-layout) and long-
                            // press (toggle the panel) close
                            // the top toolbar's 3-dots menu.
                            onMenuDismiss()
                            onAction(PockRefAction.ExecuteAutoLayout)
                        },
                        onLongClick = {
                            onMenuDismiss()
                            onAction(
                                PockRefAction.SetBottomRightPanelExpanded(
                                    !state.bottomRightPanelExpanded,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = "一键排版",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    // V_NOW: import 弹窗。放在 [Surface] 外面、BottomRightActionPanel
    // 函数的最后,作为整个 panel 的"兄弟"节点。理由:
    //   1. **独立 window**: ModalBottomSheet 在 Compose
    //      [Popup] 里渲染,有独立的 WindowToken,跟 panel
    //      的 [Surface] 不共享 surface。这意味着 panel 收
    //      起时 sheet 不会被一起擦掉(虽然我们也 pause 了
    //      auto-collapse,见上面的 LaunchedEffect,但双保险)。
    //   2. **位置**: ModalBottomSheet 默认从底部弹起,
    //      跨过 panel 上方,看起来像"从面板里拽出来一个
    //      选择器"。
    //   3. **a11y + 键盘导航 + drag-down dismiss**:
    //      M3 ModalBottomSheet 全免费。
    //   4. **选项 click 立刻关 sheet + 触发 launcher**:
    //      onClick 同步 showImportSheet=false(避免 sheet
    //      还在动画时 launcher 已经在跑),再调 onImport 或
    //      onOpenScene。launcher 走的是 ActivityResult
    //      Contracts,本身异步,不会跟 sheet 动画打架。
    if (showImportSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            sheetState = sheetState,
        ) {
            ListItem(
                headlineContent = { Text("导入图片") },
                leadingContent = {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showImportSheet = false
                    onImport()
                },
            )
            ListItem(
                headlineContent = { Text("打开文件") },
                leadingContent = {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showImportSheet = false
                    onOpenScene()
                },
            )
        }
    }
}

/**
 * Single icon-only cell for [BottomRightActionPanel], sized
 * [Dimens.TopToolbarHeight] × [Dimens.TopToolbarHeight] so the
 * column reads as 5 identical squares stacked vertically (4
 * list items + 1 FAB). Plain Box with [Modifier.clickable]
 * (NOT M3 [ListItem] which reserves horizontal space for the
 * headlineContent slot even when empty, breaking the "5
 * identical squares" look).
 */
@Composable
private fun BottomPanelListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    Box(
        modifier = Modifier
            .size(Dimens.BottomPanelChromeSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


// ─── Settings tabs (V_NOW: tabbed settings) ───────────────

/**
 * Visual metadata for the 3 settings tabs. Centralised so the
 * TabRow (phones), the NavigationRail (tablets), and any future
 * deep-link / quick-jump all read the same icon + label, and
 * adding a 4th tab is a one-line change here.
 */
private data class SettingsTabSpec(
    val tab: com.pockref.app.model.SettingsTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val settingsTabSpecs: List<SettingsTabSpec> = listOf(
    SettingsTabSpec(
        tab = com.pockref.app.model.SettingsTab.GENERAL,
        label = "通用",
        icon = Icons.Rounded.Tune,
    ),
    SettingsTabSpec(
        tab = com.pockref.app.model.SettingsTab.APPEARANCE,
        label = "外观",
        icon = Icons.Rounded.Palette,
    ),
    SettingsTabSpec(
        tab = com.pockref.app.model.SettingsTab.GESTURES,
        label = "手势",
        icon = Icons.Rounded.SwipeRight,
    ),
)

/**
 * Phone-width tab bar across the top of the sheet. 3 fixed
 * tabs (M3 [TabRow]); selected tab inherits
 * [com.pockref.app.model.PockRefUiState.settingsTab] so the
 * choice survives sheet close/reopen within the same app
 * session.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabRow(
    selectedTab: com.pockref.app.model.SettingsTab,
    onTabSelected: (com.pockref.app.model.SettingsTab) -> Unit,
) {
    androidx.compose.material3.TabRow(
        selectedTabIndex = settingsTabSpecs.indexOfFirst { it.tab == selectedTab }
            .coerceAtLeast(0),
    ) {
        settingsTabSpecs.forEach { spec ->
            androidx.compose.material3.Tab(
                selected = selectedTab == spec.tab,
                onClick = { onTabSelected(spec.tab) },
                text = { Text(spec.label) },
                icon = { Icon(spec.icon, contentDescription = spec.label) },
            )
        }
    }
}

/**
 * Tablet-width vertical rail on the left of the sheet. Same
 * 3 tabs as [SettingsTabRow] but laid out as M3
 * [NavigationRailItem]s. The [selected] state and the icon
 * tint come straight from the same `settingsTabSpecs` list
 * so the two layouts never drift apart.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsNavigationRail(
    selectedTab: com.pockref.app.model.SettingsTab,
    onTabSelected: (com.pockref.app.model.SettingsTab) -> Unit,
) {
    NavigationRail {
        settingsTabSpecs.forEach { spec ->
            NavigationRailItem(
                selected = selectedTab == spec.tab,
                onClick = { onTabSelected(spec.tab) },
                icon = { Icon(spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
                colors = NavigationRailItemDefaults.colors(),
            )
        }
    }
}

/**
 * Dispatcher: renders the active tab's composable inside a
 * [LazyColumn] so off-screen content is not composed. Each
 * tab composable is responsible for its own `items { ... }`
 * blocks — this wrapper just picks the right one and gives
 * it the shared [state] / [onAction] / [paddingValues] (the
 * last for any inner padding the sheet applies, currently
 * unused but plumbed in for future edge-to-edge work).
 */
@Composable
private fun SettingsTabContent(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
    selectedTab: com.pockref.app.model.SettingsTab,
) {
    when (selectedTab) {
        com.pockref.app.model.SettingsTab.GENERAL ->
            SettingsGeneralTab(state = state, onAction = onAction)
        com.pockref.app.model.SettingsTab.APPEARANCE ->
            SettingsAppearanceTab(state = state, onAction = onAction)
        com.pockref.app.model.SettingsTab.GESTURES ->
            SettingsGesturesTab(state = state, onAction = onAction)
    }
}

/**
 * "通用" tab — device-input behaviors and persistence:
 *   - 边缘吸附 (edge snap to other images)
 *   - 旋转角度吸附 (rotation snap to 0/90/180/270°)
 *   - 自动保存 (interval slider, 1 min ~ 1 h)
 *   - S Pen 按钮 (PRESS_AND_HOLD vs TOGGLE)
 *
 * Uses [LazyColumn] so the long auto-save + S Pen sections
 * compose lazily on phone (portrait, ~700dp tall — the
 * content is ~900dp and used to clip the bottom 2 rows under
 * the old [verticalScroll] approach).
 */
@Composable
private fun SettingsGeneralTab(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsToggleRow(
                title = "吸附图像",
                subtitle = if (state.edgeSnapEnabled) "拖动图片时贴近其他图片的边/中线" else "已关闭",
                checked = state.edgeSnapEnabled,
                onCheckedChange = { checked ->
                    onAction(com.pockref.app.ui.PockRefAction.SetEdgeSnapEnabled(checked))
                },
            )
        }
        item {
            SettingsToggleRow(
                title = "吸附旋转",
                subtitle = if (state.rotationSnapEnabled) "双指旋转时自动吸附 0/90/180/270°" else "已关闭",
                checked = state.rotationSnapEnabled,
                onCheckedChange = { checked ->
                    onAction(com.pockref.app.ui.PockRefAction.SetRotationSnapEnabled(checked))
                },
            )
        }
        item {
            SettingsAutoSaveSection(state = state, onAction = onAction)
        }
        item {
            SettingsSpenSection(state = state, onAction = onAction)
        }
    }
}


/**
 * "外观" tab — visual look-and-feel:
 *   - 主题 (system / light / dark segmented row)
 *   - 背景颜色 (7 preset swatches + inline HSV wheel on
 *     the "Custom" chip)
 *
 * The HSV wheel is the only section that may scroll off
 * the bottom of a portrait phone — [LazyColumn] keeps
 * the rest of the tab cheap and the wheel's draggable
 * canvas uses its own internal height (180dp) so it
 * doesn't fight the LazyColumn for vertical space.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAppearanceTab(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 主题
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.titleSmall,
                )
                val themeOptions = listOf(
                    com.pockref.app.data.ThemeMode.SYSTEM to "系统",
                    com.pockref.app.data.ThemeMode.LIGHT to "浅色",
                    com.pockref.app.data.ThemeMode.DARK to "深色",
                )
                SingleChoiceSegmentedButtonRow {
                    themeOptions.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { onAction(com.pockref.app.ui.PockRefAction.SetThemeMode(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = themeOptions.size,
                            ),
                            modifier = Modifier.heightIn(min = 56.dp),
                            icon = { },
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        // 背景颜色
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "背景颜色",
                    style = MaterialTheme.typography.titleSmall,
                )
                BackgroundColorPanel(
                    currentColor = state.backgroundColor,
                    onPreset = { preset ->
                        onAction(com.pockref.app.ui.PockRefAction.SetBackgroundPreset(preset))
                    },
                )
                if (state.showBackgroundPicker) {
                    CustomColorWheel(
                        currentColor = state.backgroundColor,
                        // "确定" (V_NOW: preview/commit pattern) —
                        // apply the composed preview colour. The
                        // [setBackgroundColor] reducer both writes
                        // [state.backgroundColor] AND sets
                        // [showBackgroundPicker = false] in the
                        // same update, so the picker closes on
                        // commit (and the next time the user
                        // taps Custom, the picker reopens with
                        // the just-saved colour as the new
                        // starting point).
                        onConfirm = { color ->
                            onAction(com.pockref.app.ui.PockRefAction.SetBackgroundColor(color))
                        },
                        // "取消" (V_NOW: preview/commit pattern) —
                        // discard the preview (it's local state
                        // inside [CustomColorWheel] and goes away
                        // with the composable) and close the
                        // picker without touching
                        // [state.backgroundColor]. Uses the
                        // pre-existing [ShowBackgroundPicker]
                        // action — no new reducer needed.
                        onCancel = {
                            onAction(com.pockref.app.ui.PockRefAction.ShowBackgroundPicker(false))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Static grouping of the 7 [com.pockref.app.ui.GestureId]s
 * into 3 sub-categories (V_NOW: gesture tab visual
 * hierarchy). Mirrors Procreate's "Gestures → [Zoom /
 * Rotate / Edit]" 3-bucket layout so users coming from
 * Procreate find the toggle they want without scanning a
 * flat list of 7.
 */
private data class GestureGroupSpec(
    val title: String,
    val ids: List<String>,
)

private val gestureGroupSpecs: List<GestureGroupSpec> = listOf(
    GestureGroupSpec(
        title = "缩放",
        ids = listOf(
            com.pockref.app.ui.GestureId.PINCH_ZOOM,
            com.pockref.app.ui.GestureId.SLIDE_ZOOM,
        ),
    ),
    GestureGroupSpec(
        title = "旋转",
        ids = listOf(
            com.pockref.app.ui.GestureId.PINCH_ROTATE,
        ),
    ),
    GestureGroupSpec(
        title = "编辑",
        ids = listOf(
            com.pockref.app.ui.GestureId.MOVE_IMAGE,
            com.pockref.app.ui.GestureId.TWO_FINGER_TAP,
            com.pockref.app.ui.GestureId.THREE_FINGER_TAP,
            com.pockref.app.ui.GestureId.DOUBLE_TAP_EMPTY,
        ),
    ),
)

/**
 * "手势" tab — 7 per-gesture on/off switches grouped into
 * 缩放 / 旋转 / 编辑 sub-categories. Each group has a
 * small section header (titleSmall, onSurfaceVariant) so
 * the visual rhythm reads as "3 buckets of 1-4 toggles"
 * rather than a single flat list of 7. Uses [LazyColumn]
 * for lazy composition.
 */
@Composable
private fun SettingsGesturesTab(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gestureGroupSpecs.forEach { group ->
            item(key = "group_${group.title}") {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = group.ids,
                key = { id -> "gesture_$id" },
            ) { id ->
                val isOn = state.gestureToggles[id] ?: true
                val title = com.pockref.app.ui.GestureId.label(id)
                val subtitle = gestureSubtitle(id, isOn)
                SettingsToggleRow(
                    title = title,
                    subtitle = subtitle,
                    checked = isOn,
                    onCheckedChange = { checked ->
                        onAction(com.pockref.app.ui.PockRefAction.SetGestureEnabled(id, checked))
                    },
                )
            }
        }
    }
}

/**
 * Per-gesture helper subtitle for [SettingsGesturesTab].
 * Kept as a top-level private fun so the LazyColumn
 * `items` lambda stays a one-liner. Mirrors the original
 * `when` block that used to live inline in
 * [SettingsPanel].
 */
private fun gestureSubtitle(id: String, isOn: Boolean): String = when (id) {
    com.pockref.app.ui.GestureId.MOVE_IMAGE -> if (isOn) "单指拖动选中的图片" else "已关闭"
    com.pockref.app.ui.GestureId.PINCH_ZOOM -> if (isOn) "双指捏合调整缩放" else "已关闭"
    com.pockref.app.ui.GestureId.PINCH_ROTATE -> if (isOn) "双指旋转(吸附 0/90/180/270°)" else "已关闭"
    com.pockref.app.ui.GestureId.SLIDE_ZOOM -> if (isOn) "双指竖直滑动缩放画布" else "已关闭"
    com.pockref.app.ui.GestureId.TWO_FINGER_TAP -> if (isOn) "撤销" else "已关闭"
    com.pockref.app.ui.GestureId.THREE_FINGER_TAP -> if (isOn) "重做" else "已关闭"
    com.pockref.app.ui.GestureId.DOUBLE_TAP_EMPTY -> if (isOn) "适应窗口" else "已关闭"
    else -> if (isOn) "已启用" else "已关闭"
}

/**
 * Auto-save master switch + interval slider, extracted
 * from [SettingsPanel] so [SettingsGeneralTab] (and the
 * navigation-rail variant on tablet) can render it
 * uniformly. V_NOW layout:
 *
 *   [Switch row] "启用自动保存"  /  subtitle changes
 *               "已开启" ↔ "已关闭" so the user can tell
 *               at a glance whether the slider below is
 *               live or inert.
 *
 *   [Title] "自动保存"
 *   [Body ] "定时把画布快照写到磁盘..."
 *   [Label] "当前:X 分钟"  (greyed out when switch off)
 *   [Slider] discrete stops, 1 / 5 / 10 / 15 / ... / 60 min
 *            (13 entries, see
 *            [com.pockref.app.data.AutoSaveManager.INTERVAL_STOPS_MINUTES])
 *            `enabled = state.autoSaveEnabled` so flipping
 *            the switch off also visually dims the slider
 *            + refuses touch input.
 *
 * The slider uses an *index-based* range
 * (`0f..(stops.size - 1).toFloat()`, `steps = stops.size - 2`)
 * rather than a millisecond-based range. This is the design
 * the existing
 * [com.pockref.app.data.AutoSaveManager.INTERVAL_STOPS_MINUTES]
 * KDoc explicitly anticipates ("uses an `index → value`
 * indirection: `valueRange = 0f..(size-1).toFloat()` +
 * `steps = size-2`"). A millisecond-based range with
 * `steps` would produce linearly-spaced stops (1, 6.4, 11.7,
 * …, 60) — not the desired 1 / 5 / 10 / 15 / … / 60 pattern,
 * because Compose's Slider `steps` parameter spaces stops
 * uniformly. Going index-based + reading
 * [com.pockref.app.data.AutoSaveManager.INTERVAL_STOPS_MINUTES]
 * keeps the auto-save manager as the single source of truth
 * for the available intervals (a future "add 90 min / 120 min
 * stop" edit is one line in that file).
 *
 * [state] is read for both the Switch position
 * ([PockRefUiState.autoSaveEnabled]) and the dim/disabled
 * gating of the slider. The switch's `onCheckedChange`
 * dispatches [PockRefAction.SetAutoSaveEnabled]; the
 * reducer in [PockRefViewModel.setAutoSaveEnabled] writes
 * the pref AND updates the uiState in the same call, so a
 * tap on the switch is reflected in the slider's
 * `enabled` value in the next frame (no `mutableStateOf`
 * shadow, no one-frame desync).
 */
@Composable
private fun SettingsAutoSaveSection(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
) {
    val stops = com.pockref.app.data.AutoSaveManager.INTERVAL_STOPS_MINUTES
    val appPrefs = com.pockref.app.data.AppPreferences(
        androidx.compose.ui.platform.LocalContext.current,
    )
    // V_NOW (follow-up after auto-save-switch task): source
    // of truth for the slider's *position* is now the
    // [uiState] field [autoSaveIntervalMs] (mirrored from the
    // pref by the reducer). The previous version read
    // [appPrefs.autoSaveIntervalMs] directly, which is a
    // plain Kotlin getter over SharedPreferences — *not* a
    // Compose State — so dragging the slider wrote the pref
    // but didn't trigger recomposition, leaving the slider
    // visually stuck at the old position. The auto-save
    // coroutine itself still reads the pref directly on
    // every cycle, so slider position == the value the next
    // save will use.
    val currentAutoSaveMs = state.autoSaveIntervalMs
    // Snap the persisted ms value to the nearest stop's
    // index. If a future code path writes an
    // out-of-stops value (e.g. a debug "save every 5s"
    // toggle), the slider rounds to the closest stop
    // rather than throwing or showing a position outside
    // its [valueRange].
    val currentIndex = com.pockref.app.data.AutoSaveManager.INTERVAL_STOPS_MINUTES
        .mapIndexed { index, minutes -> index to minutes * 60_000L }
        .minByOrNull { kotlin.math.abs(it.second - currentAutoSaveMs) }
        ?.first
        ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // V_NOW: master switch on top so it's the first
        // thing the user sees (matches the project's
        // other settings — 吸附图像 / 吸附旋转 — which
        // all put the Switch on the same row as the
        // title via [SettingsToggleRow]).
        SettingsToggleRow(
            title = "自动保存",
            subtitle = if (state.autoSaveEnabled) "已开启,定时把画布快照写到磁盘" else "已关闭,画布不会自动保存",
            checked = state.autoSaveEnabled,
            onCheckedChange = { checked ->
                onAction(com.pockref.app.ui.PockRefAction.SetAutoSaveEnabled(checked))
            },
        )
        // V_NOW: dim the value label together with the
        // slider so the user sees the whole interval
        // control "go dark" when they flip the master
        // switch off. `MaterialTheme.colorScheme.onSurface`
        // at 0.38 alpha is the standard M3 disabled
        // affordance (matches what the system Slider
        // uses internally for its disabled state).
        Text(
            text = "当前:${stops[currentIndex]} 分钟",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.autoSaveEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        androidx.compose.material3.Slider(
            value = currentIndex.toFloat(),
            onValueChange = { newValue ->
                val snappedIndex = newValue.toInt().coerceIn(0, stops.size - 1)
                val snappedMs = stops[snappedIndex] * 60_000L
                onAction(
                    com.pockref.app.ui.PockRefAction.SetAutoSaveIntervalMs(snappedMs),
                )
            },
            valueRange = 0f..(stops.size - 1).toFloat(),
            // 13 stops → 11 internal divisions.
            // `steps = size - 2` matches the
            // INTERVAL_STOPS_MINUTES KDoc contract.
            steps = stops.size - 2,
            enabled = state.autoSaveEnabled,
        )
        // V_NOW (follow-up after "auto-save silent" bug):
        // show the user when the most recent auto-save
        // ran, so they can tell at a glance whether the
        // feature is actually doing its job. Renders as
        // "上次自动保存:[N秒前 / N分钟前 / N小时前 / 刚刚 / 从未]"
        // depending on age. Hidden (null renders empty
        // Text) when the master switch is off — the
        // "disabled" message in the Switch's subtitle
        // already covers that case, showing a stale
        // "last saved" timestamp would be misleading.
        if (state.autoSaveEnabled) {
            val lastSaved = state.lastAutoSaveAt
            Text(
                text = if (lastSaved == null) {
                    "上次自动保存:从未(画布有改动时,会在 ${stops[currentIndex]} 分钟后自动写盘)"
                } else {
                    "上次自动保存:" + formatRelativeAge(
                        lastSaved,
                        System.currentTimeMillis(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * S Pen side-button mode radio. Two options (PRESS_AND_HOLD
 * vs TOGGLE) with their human descriptions. Extracted
 * from the old [SettingsPanel] so [SettingsGeneralTab] can
 * include it without inlining the radio-list boilerplate.
 */
@Composable
private fun SettingsSpenSection(
    state: com.pockref.app.model.PockRefUiState,
    onAction: (com.pockref.app.ui.PockRefAction) -> Unit,
) {
    val currentSpenMode = state.spenMode
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "S Pen 按钮", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "选择笔身按钮的触发方式,需 S Pen 实体按钮支持(部分 Samsung 机型支持)",
            style = MaterialTheme.typography.bodySmall,
        )
        com.pockref.app.spen.SpenMode.values().forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (currentSpenMode == mode),
                        onClick = { onAction(com.pockref.app.ui.PockRefAction.SetSpenMode(mode)) },
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(
                    selected = (currentSpenMode == mode),
                    onClick = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (mode) {
                            com.pockref.app.spen.SpenMode.PRESS_AND_HOLD -> "按住开启,松开关闭"
                            com.pockref.app.spen.SpenMode.TOGGLE -> "按一下开,再按一下关"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = when (mode) {
                            com.pockref.app.spen.SpenMode.PRESS_AND_HOLD ->
                                "按住笔身按钮进入框选,松开退出。松手时如果有 2+ 张图被选中则切到多选,否则单选。"
                            com.pockref.app.spen.SpenMode.TOGGLE ->
                                "点按笔身按钮进入框选,再次点按退出。关闭时如果有 2+ 张图被选中则切到多选,否则单选。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
