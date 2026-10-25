@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package com.pockref.app.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import android.util.Log
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
// LockOpen import: V_NOW the single-image-locked
// panel uses the OPEN padlock to convey "tap to
// unlock" — distinct from the multi-select panel
// which uses the closed padlock (active=blue when
// the whole batch is locked). The two states are
// visually distinguishable so the user can never be
// in doubt about whether tapping the button will
// lock or unlock.
// previous LockOpen usage was the all-locked sub-mode at
// the old line 1312; now that branch also uses Lock with
// active=allLocked.
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.Settings
import com.pockref.app.model.AlignMode
import com.pockref.app.R
import com.pockref.app.data.ImageDecodePolicy
import com.pockref.app.data.ImageTilePlanner
import com.pockref.app.data.ImageTilePolicy
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.ColorAdjust
import com.pockref.app.ui.theme.Dimens
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.groupContaining
import com.pockref.app.model.selectedGroups
import com.pockref.app.ui.logs.LogExportFab
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneNaming
import com.pockref.app.model.SelectionMode
import com.pockref.app.model.hasSceneContent
import com.pockref.app.model.isCanvasOpen
import com.pockref.app.model.isImageLocked
import com.pockref.app.model.Size2D
import com.pockref.app.ui.settings.SettingsPanel
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

private enum class ImageTransformMode { NONE, SCALE, ROTATE }

private data class RotationHandleDragState(
    val pivotScreen: Offset,
    val previousAngleRad: Float,
)

// V_NOW (user feedback "P1-B4 mode 切换 cooldown"):
// see [modeCommitTimeMs] in [PockRefCanvas]'s pointer
// loop. [MODE_COOLDOWN_MS] is the wall-clock window
// after a SCALE / ROTATE commit during which
// the transform-mode decision is short-circuited
// (we trust the already-committed mode). 500 ms is
// the current value; raise it if user reports
// mid-gesture mode flips, lower it if the user
// can't recover from a wrong-mode commit.
private const val MODE_COOLDOWN_MS = 500L
// V_NOW (user feedback "P1-B3 SCALE / ROTATE
// 阈值单位统一"): the two thresholds in this file
// (SCALE / ROTATE) use two DIFFERENT unit
// systems (dp, %, °) and a future reader / tuner will
// not know which constant is in which unit without
// grep'ing the call site. The renamed *_PERCENT /
// *_DEG / *_DP suffixes below are unit-tag aliases;
// the legacy un-tagged names are kept as a
// deprecation bridge to avoid breaking anything that
// might still reference them. (Verified no other file
// in the codebase references the un-tagged names —
// grep `SCALE_THRESHOLD\b` / `ROTATE_THRESHOLD\b`
// across app/ returns only this file.)
//
// Tuning guide:
//   SCALE_THRESHOLD_PERCENT : increase → require more
//                          zoom-out (or zoom-in) cumulative
//                          deviation from 1.0 before the
//                          pinch commits. 12% means a pinch
//                          that ends with fingers 12% closer
//                          or further apart than at down
//                          will commit. 5% is "tip of the
//                          fingers" precision; 20% is
//                          deliberate.
//   ROTATE_THRESHOLD_DEG : increase → require more
//                          cumulative rotation before the
//                          gesture commits. 10° is the
//                          current setting, calibrated to
//                          "I meant to rotate" (not
//                          "my fingers drifted").
private const val SCALE_THRESHOLD_PERCENT = 12f
private const val ROTATE_THRESHOLD_DEG = 10f
// Legacy un-tagged aliases (kept for any 3rd-party
// reading the file; can be removed once a
// code-search confirms no external reference).
@Suppress("unused")
private const val SCALE_THRESHOLD = SCALE_THRESHOLD_PERCENT
@Suppress("unused")
private const val ROTATE_THRESHOLD = ROTATE_THRESHOLD_DEG

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

// V_NOW (2026-06-10 user spec "启动页 pockref 字体要
// 圆润些"): use Google Fonts' Nunito via the Downloadable
// Fonts API instead of bundling TTF. Benefits:
//   - APK stays small (no 130 KB font file per weight)
//   - User gets the latest version of Nunito automatically
//   - No minSdk 26 requirement (Downloadable Fonts works
//     back to API 14 via GMS Core)
//   - Multiple weights (400, 700, 900) share the same
//     [FontFamily] declaration, so we can pick weight at
//     the call site via TextStyle.fontWeight
// The provider authority `com.google.android.gms.fonts`
// must be declared in AndroidManifest.xml <queries> on
// Android 11+ for the system to resolve the provider.
private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.pockref.app.R.array.com_google_android_gms_fonts_certs,
)
private val NunitoFont = GoogleFont("Nunito")
private val NunitoFontFamily = FontFamily(
    Font(googleFont = NunitoFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = NunitoFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = NunitoFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Black),
)

// V_NOW (user feedback "P0-A1 阈值按 density 缩放"):
// the previous thresholds were raw pixel constants, set
// when the project assumed ~160 dpi (24 px ≈ 3.8 mm).
// On the Galaxy Tab S10+ (340 dpi, 2.125× the 160 dpi
// baseline), the *old pixel values* are now too small
// in mm and trigger on natural hand jitter:
//
//   old value   old@160dpi  old@340dpi   new (dp)   new@340dpi
//   DRAG_TAP       4f         4 px         8 dp       17 px     ← scale UP: 0.3→0.5 mm (was a 6× undershoot)
//   TAP_MAX_DRAG   8f         8 px        12 dp       26 px     ← scale UP: 0.5→0.8 mm
//   GESTURE_MOVED  4f         4 px         8 dp       17 px     ← scale UP (multi-finger "any finger moved")
//   EDGE_SCROLL   20f        20 px        48 dp      102 px     ← scale UP: 0.6→1.4 mm (was way too easy to hit)
//   DOUBLE_TAP    20f        20 px        32 dp       68 px     ← scale UP: 0.6→1.0 mm
//   SCALE        12f        12 %         12 % (unchanged — already unit-agnostic)
//   ROTATE       10f        10°          10°  (unchanged)
//
// The constants below are dp values; the gesture handler
// at the call site multiplies by [LocalDensity.current.density]
// to get the actual pixel threshold. The dp numbers
// are calibrated to "human-perceived" millimetres on
// a 340 dpi tablet: 8 dp ≈ 0.5 mm hand jitter floor,
// 48 dp edge zone ≈ 1.4 mm "I'm at the edge" intent.
private const val DRAG_TAP_THRESHOLD_DP = 8f
private const val TAP_MAX_DRAG_DP = 12f
private const val EDGE_AUTO_SCROLL_THRESHOLD_DP = 48f
private const val GESTURE_MOVED_THRESHOLD_DP = 8f
private const val DOUBLE_TAP_MAX_DIST_DP = 32f

/** Min/max zoom range for the minimap slider (continuous, stepless). */
private const val ZOOM_MIN = 0.25f
private const val ZOOM_MAX = MAX_CANVAS_SCALE
private const val DOUBLE_TAP_MAX_INTERVAL_MS = 400L

internal fun isSingleFingerCanvasTap(
    peakPointerCount: Int,
    dragStarted: Boolean,
    transformStarted: Boolean,
    durationMs: Long,
    maxDistancePx: Float,
    tapMaxDragPx: Float,
): Boolean = peakPointerCount == 1 && !dragStarted && !transformStarted &&
    durationMs < 300L && maxDistancePx < tapMaxDragPx
// V_NOW: delay before showing the SelectionBottomSheet
// in single-select mode. Shorter than the full double-tap
// window so the sheet appears responsive on single taps,
// but long enough to catch most double-taps (typically
// completed within ~200 ms). If a slow double-tap fires
// after the sheet is already visible, the double-tap
// handler dismisses it.
private const val SINGLE_TAP_SHEET_DELAY_MS = 200L
// V_NOW (2026-06-08 "连续弹出两次" 修复 v2):
// this constant is RESIDUAL from v1 of the debounce
// (which lived in this pointerInput block). v1 only
// covered the single-finger-tap path and missed the
// two-finger-down path at L6287 which dispatches
// [TickSelectionSheet] directly. v2 moved the
// debounce to the [PockRefViewModel]
// [PockRefAction.TickSelectionSheet] reducer itself
// (see the [PockRefUiState.lastSheetShowMs] field
// and the reducer's `now - lastSheetShowMs < 800L`
// guard), so all four [TickSelectionSheet] dispatch
// sites are protected.
//
// The constant is kept here so the v1 pointerInput
// block below (which still wraps the
// [pendingSheetJob] scheduling) can stay consistent
// with the canonical 800ms window. Without this
// consistency, the pointerInput's own
// [lastSheetShowMs] check could let a job through that
// the viewmodel would then refuse — visible as the
// sheet staying closed for the user's first tap on
// an image they were sure they tapped.
private const val SHEET_SHOW_DEBOUNCE_MS = 800L

// --- Render-perf tuning ----------------------------------------------------
// Extra padding (in screen pixels) added on every side of the world-space
// viewport. Used both for the draw-loop cull and for the LaunchedEffect
// decode-priority filter so fast pans don't reveal blank edges or
// thrash the decode queue.
private const val VIEWPORT_BUFFER_PX = 200f

// A 1024px ARGB tile is about 4 MiB. Keeping at most 32 bounds the
// full-resolution overlay at roughly 128 MiB while the 4096px base LOD
// remains the fallback for uncached regions.
private const val MAX_TILE_CACHE_ENTRIES = 32

// Pixel count above which we treat an image as "large" and downsample it
// to a viewport-aware target. ~4 megapixels.
private const val LARGE_IMAGE_PIXELS = 4_000_000

// Upper bound for the downsampled width of a large image.
private const val LARGE_IMAGE_MAX_DECODE_PX = 2048

// CreateDocument's stock contract accepts only a file name, so it cannot
// honor the user's persisted folder. This small contract adds the SAF
// initial URI while preserving the same URI result shape.
private data class CreateDocumentInput(
    val mimeType: String,
    val fileName: String,
    val initialUri: Uri?,
)

private class CreateDocumentWithInitialUri : ActivityResultContract<CreateDocumentInput, Uri?>() {
    override fun createIntent(context: android.content.Context, input: CreateDocumentInput): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)
            .apply {
                input.initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

private fun treeDisplayName(contentResolver: ContentResolver, treeUri: Uri): String? {
    val documentUri = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }.getOrNull()
    val queriedName = documentUri?.let { uri ->
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
    if (!queriedName.isNullOrBlank()) return queriedName
    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        Uri.decode(documentId.substringAfter(':', documentId).substringAfterLast('/'))
            .takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun createDocumentInTree(
    contentResolver: ContentResolver,
    treeUri: Uri,
    mimeType: String,
    fileName: String,
): Uri? {
    return try {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocumentUri =
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        DocumentsContract.createDocument(
            contentResolver,
            parentDocumentUri,
            mimeType,
            fileName,
        )
    } catch (error: Throwable) {
        Log.e("PockRefSave", "createDocument failed for $treeUri", error)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PockRefApp(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    imageLazyLoader: com.pockref.app.data.PurFileParser? = null,
    imageDecoder: com.pockref.app.data.SceneImageDecoder? = null,
    errorsFlow: SharedFlow<UiError>? = null,
    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Wraps [PockRefViewModel.startLogExport] so the
    // [LogExportFab] (mounted inside the start-page branch)
    // can trigger the VM-side work without the composable
    // tree holding a direct viewModel reference. The actual
    // VM stays in MainActivity / ViewModelProvider; this
    // composable just routes the click through.
    onStartLogExport: (
        appLabel: String,
        onReady: (suggestedName: String, content: String) -> Unit,
        onError: (reason: String) -> Unit,
    ) -> Unit = { _, _, _ -> },
    exportInProgressFlow: StateFlow<Boolean> = MutableStateFlow(false),
    onEnterFloatingWindow: () -> Unit = {},
    showFloatingWindowButton: Boolean = true,
    updateController: com.pockref.app.update.AppUpdateController? = null,
) {
    val importLauncher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        onAction(PockRefAction.ImportImages(uris))
    }
    // Hoisted context: needed by the camera launcher to
    // resolve content URIs and clean up orphan temp files
    // when the user cancels the camera.
    val context = androidx.compose.ui.platform.LocalContext.current
    // V_NOW (2026-06-10 #2 import popup): gallery picker.
    // [PickMultipleVisualMedia] is the Android 13+
    // PhotoPicker API (auto-fallback to SAF on older
    // Android versions). Bounded at 32 items — matches
    // the multi-select workflow the existing
    // [OpenMultipleDocuments] launcher offers, so users
    // can pull in a whole reference sheet in one tap.
    // [PickVisualMediaRequest.ImageOnly] restricts the
    // picker to image MIME so we never receive a stray
    // video/PDF that the SAF list-mode picker might
    // surface.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        PickMultipleVisualMedia(maxItems = 32),
    ) { uris ->
        if (uris.isNotEmpty()) onAction(PockRefAction.ImportImages(uris))
    }

    // Camera capture launcher. Uses ACTION_IMAGE_CAPTURE with a
    // content URI pointing to a temp file in the cache dir. The
    // camera app writes the full-res JPEG to that URI; on
    // success we feed it to the existing [ImportImages] flow.
    //
    // V_NOW (2026-06-11): switched from TakePicture() to
    // StartActivityForResult() because TakePicture() does NOT
    // add FLAG_GRANT_WRITE_URI_PERMISSION or ClipData to the
    // intent — on Samsung devices the camera app then can't
    // write to the content URI and crashes, taking the host
    // app down with it. By building the intent manually we
    // ensure the camera receives both read + write grants via
    // ClipData + flags.
    val cameraPhotoUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraPhotoUri.value
        android.util.Log.d(
            "PockRefCamera",
            "TakePicture result: success=$success uri=$uri",
        )
        if (success && uri != null) {
            onAction(PockRefAction.ImportImages(listOf(uri)))
        }
        cameraPhotoUri.value = null
    }
    // V_NOW (2026-06-11): runtime CAMERA permission
    // request. Samsung OneUI 6 (Android 14) enforces
    // that the CALLING app holds CAMERA permission
    // before it will resolve ACTION_IMAGE_CAPTURE —
    // even though the system camera app is the one
    // actually accessing the hardware. Without this
    // runtime request the system throws
    // SecurityException "revoked permission
    // android.permission.CAMERA" and the camera
    // silently fails to launch.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = cameraPhotoUri.value
            if (uri != null) {
                cameraLauncher.launch(uri)
            }
        } else {
            cameraPhotoUri.value = null
            android.util.Log.w(
                "PockRefCamera",
                "CAMERA permission denied by user",
            )
        }
    }
// V_NOW (2026-06-10 user hand-modification from 23:31 APK, merged
    // with #2 chrome unify): the dropdown's "剪贴板" item dispatches
    // [PockRefAction.ImportFromClipboard]. The ViewModel handles the
    // heavy lifting in an IO coroutine — reads [ClipboardManager],
    // decodes the bytes via [ImageOptimizer], creates a [SceneImage],
    // and surfaces errors via the existing [errorsFlow] snackbar
    // (see [PureRefViewModelScene.importFromClipboard]). This is
    // strictly better than the earlier simplified lambda that
    // dispatched [ImportImages] with raw URIs because the
    // clipboard may hold URI strings or inline image data that
    // need byte-level decode + optimize before a [SceneImage]
    // can be constructed — exactly the case the full
    // [importFromClipboard] implementation handles.
    val onImportFromClipboard: () -> Unit = {
        onAction(PockRefAction.ImportFromClipboard)
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
    //   1. The unsaved-changes dialog is showing → the
    //      dialog's own [AlertDialog.onDismissRequest]
    //      handles back (fires [PockRefAction.UnsavedDialogCancel]).
    //      We disable our handler in that branch so we don't
    //      double-fire CloseScene on top of the dialog.
    //   2. A scene is "open" (has images OR can direct-save
    //      to a saved file) → fire [PockRefAction.CloseScene].
    //      The reducer's [onCloseScene] shows the 3-option
    //      "保存 / 不保存 / 取消" dialog when there are
    //      unsaved changes, so the user's "优先关闭文件
    //      而非整个软件" requirement is satisfied.
    //   3. None of the above (empty / main screen) → the
    //      handler is disabled, so back falls through to the
    //      system and the Activity finishes (Android: returns
    //      to the home screen, satisfying "无文件打开状态
    //      直接退出软件回到桌面").
    val blankCanvasActive = state.blankCanvasOpen && !state.hasSceneContent
    val sceneIsOpen = state.hasSceneContent || state.canDirectSave
    val unsavedDialogShowing = state.showUnsavedChangesDialog
    // V_NOW (2026-06-10, "启动页多选" back-press contract):
    // when the start page is in multi-select mode, the
    // system back button exits multi-select first. This
    // is a new top-priority branch in the back-press
    // chain that ranks ABOVE [sceneIsOpen] — pressing back
    // while in multi-select
    // is the standard Android "I'm done with this
    // ephemeral mode" gesture, and routing it to
    // [CloseScene] (which only makes sense for an open
    // scene) or to the system "finish activity" path
    // would surprise the user. The existing two
    // branches are preserved unchanged.
    //
    // Edge case: when [bulkDeleteConfirmOpen] is true,
    // the back press should close the confirmation
    // dialog FIRST, before exiting multi-select. This
    // matches the M3 [AlertDialog] back-press contract
    // (the dialog's own [onDismissRequest] would also
    // fire on a back press, but having two
    // simultaneous paths is the kind of redundancy
    // that breaks under refactor — so we route the
    // back press exclusively to the dialog dismiss
    // when both are open).
    val recentMultiSelectActive = state.recentMultiSelectMode && !sceneIsOpen
    BackHandler(
        enabled = state.pendingMarkupTextPosition != null ||
            state.markupModeActive ||
            state.importSourceMenuOpen ||
            recentMultiSelectActive ||
            state.bulkDeleteConfirmOpen ||
            blankCanvasActive ||
            (sceneIsOpen && !unsavedDialogShowing),
    ) {
        when {
            state.pendingMarkupTextPosition != null ->
                onAction(PockRefAction.CancelMarkupText)
            state.markupModeActive ->
                onAction(PockRefAction.CancelMarkupSession)
            state.bulkDeleteConfirmOpen ->
                onAction(PockRefAction.DismissBulkDeleteConfirm)
            recentMultiSelectActive ->
                onAction(PockRefAction.ExitRecentMultiSelect)
            state.importSourceMenuOpen ->
                onAction(PockRefAction.SetImportSourceMenuOpen(false))
            blankCanvasActive -> onAction(PockRefAction.CloseBlankCanvas)
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
    val isHoveringImage = state.hoverPreviewImageId != null && !state.markupModeActive
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
    val canvasBitmapWorkingSet = remember { CanvasBitmapWorkingSet() }
    val canvasTileCache = remember {
        mutableStateMapOf<com.pockref.app.data.ImageTileKey, ImageBitmap>()
    }
    val posterizeCache = rememberCoroutineScope().let { scope ->
        remember(scope) { PosterizeBitmapCache(scope) }
    }
    val gifFrameBitmapCache = remember { GifFrameBitmapCache() }
    DisposableEffect(Unit) {
        onDispose {
            posterizeCache.clear()
            gifFrameBitmapCache.clear()
        }
    }
    val gifPlaybackSources = remember { mutableStateMapOf<String, GifPlaybackSource>() }
    val gifPlaybackStates = remember { mutableStateMapOf<String, GifPlaybackState>() }
    val gifImagesForPlayback = remember(state.images) {
        state.images.filter { image ->
            isGifMimeType(image.mimeType) && image.cropRect == null
        }
    }
    val selectedGifPlaybackImageId = remember(
        state.images,
        state.selectedImageIds,
        state.selectedImageId,
    ) {
        selectedGifPlaybackImageIdFor(
            images = state.images,
            selectedImageIds = state.selectedImageIds,
            selectedImageId = state.selectedImageId,
        )
    }

    LaunchedEffect(
        gifImagesForPlayback.map { image ->
            listOf(image.id, image.imageBytes.size, image.imageByteOffset, image.imageByteLength)
        },
        imageDecoder,
        imageLazyLoader,
    ) {
        gifImagesForPlayback.forEach { image ->
            if (gifPlaybackSources[image.id] == null) {
                val source = withContext(Dispatchers.Default) {
                    val bytes = resolveGifBytes(image, imageDecoder, imageLazyLoader)
                        ?: return@withContext null
                    decodeGifPlaybackSource(bytes)
                } ?: return@forEach
                gifPlaybackSources[image.id] = source
            }
            if (gifPlaybackStates[image.id] == null) {
                gifPlaybackStates[image.id] = GifPlaybackState()
            }
        }
    }

    gifPlaybackSources.entries.toList().forEach { (imageId, source) ->
        val playback = gifPlaybackStates[imageId] ?: GifPlaybackState()
        LaunchedEffect(imageId, source, playback) {
            if (!playback.isPlaying) return@LaunchedEffect
            delay(source.timeline.durationForFrame(playback.frameIndex).toLong())
            val nextFrame = (playback.frameIndex + 1) % source.timeline.frameCount
            gifPlaybackStates[imageId] = playback.copy(frameIndex = nextFrame)
        }
    }
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
        posterizeCache.removeImagesNotIn(currentIds)
        gifFrameBitmapCache.removeImagesNotIn(currentIds)
        gifPlaybackSources.keys.filterNot { it in currentIds }.forEach { key ->
            gifPlaybackSources.remove(key)
        }
        gifPlaybackStates.keys.filterNot { it in currentIds }.forEach { key ->
            gifPlaybackStates.remove(key)
        }
        onDispose { }
    }
    // Invalidate cached bitmaps when an image's bytes or intrinsic
    // dimensions change (e.g. after crop or undo of crop). The
    // pipeline skips images already in the cache, so without this
    // eviction the old bitmap persists and the user sees the wrong
    // image (e.g. stretched after undo of a crop on a rotated image).
    // V_NOW: the previous logic only checked [imageBytes.size] and
    // required [newSize > 0], which missed two critical cases:
    //   1. Undo of crop: imageBytes goes from non-empty back to empty
    //      (the image reverts to the imageByteOffset path). newSize = 0
    //      failed the > 0 guard, so the cropped bitmap stayed cached.
    //   2. Intrinsic dimension change without byte-size change: rare
    //      but possible (e.g. same compressed size, different pixels).
    // The fix: track (imageBytes.size, intrinsicWidth, intrinsicHeight)
    // as the cache-invalidation key. Any change to any of these three
    // evicts the cached bitmap for that image.
    val imageCacheKeys = remember { mutableStateMapOf<String, Triple<Int, Int, Int>>() }
    DisposableEffect(state.images.map { it.id to Triple(it.imageBytes.size, it.intrinsicWidth, it.intrinsicHeight) }) {
        val newKeys = state.images.associate { it.id to Triple(it.imageBytes.size, it.intrinsicWidth, it.intrinsicHeight) }
        newKeys.forEach { (id, newKey) ->
            val oldKey = imageCacheKeys[id]
            if (oldKey != null && oldKey != newKey) {
                // Image bytes or intrinsic dimensions changed — evict stale cache
                canvasBitmapCache.remove(id)
                posterizeCache.removeImage(id)
                gifFrameBitmapCache.removeImage(id)
                gifPlaybackSources.remove(id)
                gifPlaybackStates.remove(id)
            }
            imageCacheKeys[id] = newKey
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
        uri?.let { onAction(PockRefAction.OpenSceneFromPicker(it)) }
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
    // V_NOW (2026-06-16): [ACTION_CREATE_DOCUMENT] + Downloads
    // returns a URI that ONLY has per-task write grant on Samsung
    // One UI 6 / Android 14 — DocumentsUI doesn't grant
    // [FLAG_GRANT_PERSISTABLE_URI_PERMISSION] for the Downloads
    // folder, so [takePersistableUriPermission] throws
    // "SecurityException: No persistable permission grants found
    // for UID ..." (verified via PockRefSave log on SM-X810).
    // Without a persistable grant the URI never enters the
    // grant table, so [ExternalStorageProvider.checkCallingUriPermission]
    // returns false and [openOutputStream] denies with
    // "Permission Denial ... requires android.permission.MANAGE_DOCUMENTS,
    // or grantUriPermission()".
    //
    // Fix: switch the Save flow to the two-step [OpenDocumentTree] +
    // [DocumentsContract.createDocument] pattern already used by
    // [exportOriginalsLauncher]. The user picks a folder first (opened at the previously selected folder
    // when the switch is enabled), then types a filename in
    // [SaveSceneNameDialog], then we takePersistable on the tree
    // and createDocument on it. OpenDocumentTree on a tree URI
    // does grant persistable (it's the whole point of the tree
    // contract), so the resulting document URI inherits a
    // working write grant.
    val selectedSaveTreeUri = state.defaultSaveDirectoryUri
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
    val preferredSaveTreeUri = selectedSaveTreeUri
        .takeIf { state.defaultSavePathEnabled }
    val saveSceneTreeLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        android.util.Log.i(
            "PockRefSave",
            "OpenDocumentTree callback: uri=$treeUri",
        )
        if (treeUri == null) {
            // user cancelled — leave pendingSaveSceneLaunch already
            // consumed (LaunchedEffect clears it on entry); nothing
            // else to do.
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // per-task grant still good for the lifetime of the
            // process — log but proceed (mirrors
            // [exportOriginalsLauncher] behavior).
            android.util.Log.w(
                "PockRefSave",
                "takePersistableUriPermission failed: ${e.message}",
            )
        }
        val filename = state.pendingSaveSceneName.ifBlank { "Untitled.pur" }
        // V_NOW (2026-06-20, 1.3.2): [DocumentsContract.createDocument]
        // requires a *document* URI (path ending in
        // `/document/<id>`), not the raw tree URI that
        // [OpenDocumentTree] returns (path ending in `/tree/<id>`).
        // Passing the tree URI causes
        // `IllegalArgumentException: Invalid URI:
        // content://.../tree/primary%3APictures` on Samsung One UI
        // 6 (SM-X810 confirmed via PockRefSave log), so the user's
        // "新建的 .pur 文件没法保存" report comes back as a silent
        // failure with no scene on disk. Convert tree → document
        // the same way [exportOriginalsToTree] does
        // (PureRefViewModelScene.kt exportOriginalsToTree()).
        val documentUri = createDocumentInTree(
            contentResolver = context.contentResolver,
            treeUri = treeUri,
            mimeType = com.pockref.app.MainActivity.PUR_SCENE_MIME,
            fileName = filename,
        )
        if (documentUri != null) {
            onAction(PockRefAction.SaveScene(documentUri))
        }
        // Clear the pending-launch flag now that we've either
        // dispatched SaveScene or failed silently — the
        // LaunchedEffect only fires when this flag transitions to
        // true, so flipping it false here is safe even if no
        // dialog confirm re-arms it.
        onAction(PockRefAction.ConsumePendingSaveSceneLaunch)
    }
    // Settings uses the same tree contract as scene save. The selected URI
    // is persisted by the ViewModel after this callback returns.
    val saveDirectoryPickerLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            android.util.Log.w(
                "PockRefSave",
                "save directory persistable permission failed: ${e.message}",
            )
        }
        val displayName = treeDisplayName(context.contentResolver, treeUri)
            ?: "已选择文件夹"
        onAction(
            PockRefAction.SetDefaultSaveDirectory(
                treeUri = treeUri,
                displayName = displayName,
            ),
        )
    }

    // Lossless selected-export launchers. The original-image path uses a
    // folder so every selected item can be copied with its own source MIME
    // and filename. The canvas path creates one PNG document.
    var pendingSelectedCanvasImageIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val exportSelectedCanvasLauncher = rememberLauncherForActivityResult(CreateDocumentWithInitialUri()) { uri ->
        uri?.let {
            val imageIds = pendingSelectedCanvasImageIds.toSet()
            pendingSelectedCanvasImageIds = emptyList()
            onAction(
                PockRefAction.ConfirmExportCanvas(
                    uri = it,
                    backgroundMode = state.exportCanvasBackground,
                    imageIds = imageIds,
                ),
            )
        }
    }
    // Canvas export from the right-bottom action panel: export the entire
    // scene and keep the existing background / size options.
    val exportCanvasLauncher = rememberLauncherForActivityResult(CreateDocumentWithInitialUri()) { uri ->
        uri?.let {
            onAction(
                PockRefAction.ConfirmExportCanvas(
                    uri = it,
                    backgroundMode = state.exportCanvasBackground,
                ),
            )
        }
    }
    // Original-image export: choose a folder, then copy each selected
    // source file verbatim. Keep the persisted grant when the provider
    // supports it; the current task grant is enough for providers that do not.
    val saveDirectoryInitialUri = preferredSaveTreeUri
    val exportOriginalsLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
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
                    "takePersistableUriPermission for original export failed: ${e.message}",
                )
            }
            onAction(PockRefAction.ConfirmExportOriginals(treeUri))
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
    // V_NOW (2026-06-16): Save now goes through [RequestSaveScene]
    // → [SaveSceneNameDialog]. For a new file, the confirmed name is
    // created directly in the enabled default directory; otherwise
    // [saveSceneTreeLauncher] lets the user choose a folder first.
    // The two-step picker path is kept for providers where a manual
    // folder grant is still required (see the launcher comments above).
    val onSaveScene: () -> Unit = {
        onAction(PockRefAction.RequestSaveScene)
    }

    LaunchedEffect(state.triggerSaveAsFromDialog) {
        if (state.triggerSaveAsFromDialog) {
            // Same dialog flow as the top-bar Save button — the
            // unsaved-changes dialog's "Save" path now also
            // triggers [RequestSaveScene], so the user gets the
            // same name-dialog flow whether they hit Save from the
            // toolbar or from the close confirmation. The configured
            // directory is used directly when the switch is enabled.
            onAction(PockRefAction.RequestSaveScene)
            onAction(PockRefAction.ClearSaveAsTrigger)
        }
    }

    LaunchedEffect(state.pendingSaveSceneLaunch) {
        if (state.pendingSaveSceneLaunch) {
            val treeUri = preferredSaveTreeUri
            if (treeUri == null) {
                // No active default directory: let the user choose one.
                saveSceneTreeLauncher.launch(null)
            } else {
                // The directory has already been authorized in Settings, so
                // create the named document directly and avoid reopening the
                // folder picker during the close-and-save flow.
                val documentUri = createDocumentInTree(
                    contentResolver = context.contentResolver,
                    treeUri = treeUri,
                    mimeType = com.pockref.app.MainActivity.PUR_SCENE_MIME,
                    fileName = state.pendingSaveSceneName.ifBlank { "Untitled.pur" },
                )
                if (documentUri != null) {
                    onAction(PockRefAction.SaveScene(documentUri))
                    onAction(PockRefAction.ConsumePendingSaveSceneLaunch)
                } else {
                    // A revoked provider grant or a provider-side failure
                    // falls back to the manual folder picker.
                    saveSceneTreeLauncher.launch(treeUri)
                }
            }
        }
    }

    // V_NOW (2026-06-10, "全局锁定/冻结模式只显示
    // 全局锁定按钮"): single source of truth for
    // "global lock or freeze is engaged" used by
    // every chrome element's render gate. Declared
    // at the [PockRefApp] function scope (NOT
    // inside [CanvasPanel]) because the
    // [TopToolbar] / [BottomRightActionPanel]
    // gates and the inner-CanvasPanel chrome
    // ([MinimapChrome] / [SelectionModeOverlay]
    // / [SelectionBottomSheet] / etc.) all need
    // to see the same boolean — declaring it
    // here keeps a single recomposition-driven
    // value consistent across every sibling
    // chrome layer. When the user is in either
    // of those modes they explicitly want the
    // canvas to be a read-only viewing surface
    // — the [GlobalLockButton] is the ONLY
    // chrome element that stays on top so the
    // user can click / long-press it to step
    // back out of the mode. Every other
    // element (top toolbar, bottom-right FAB +
    // 4-item panel, minimap + collapsed icon,
    // selection sheet, practice config sheet,
    // top-centre mode chip) hides itself via
    // `&& !isLockEngaged` so the screen is as
    // close to a pure canvas as possible.
    val isLockEngaged = state.globalImageLock || state.lockCanvasFrozen
    val chromePolicy = remember(state) { CanvasChromePolicy.resolve(state) }
    var topToolbarWidth by remember { mutableStateOf(224.dp) }
    var layerPanelExpanded by remember { mutableStateOf(false) }
    val chromeDensity = LocalDensity.current
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
                .fillMaxSize()
                // V_NOW (2026-06-10, "sheet 点击空白处关闭"):
                // a "tap outside to dismiss" detector on
                // the outer BoxWithConstraints. Active
                // ONLY when the start page is showing
                // AND the user is in multi-select mode
                // (the gate key is the AND of both
                // booleans — the pointerInput is
                // re-created when either flips).
                //
                // V_NOW: the detector uses a manual
                // [awaitEachGesture] loop with
                // [awaitFirstDown] + [awaitFirstUp],
                // NOT [detectTapGestures], because we
                // need to inspect the consumed state
                // of the up event to decide whether
                // a child has already claimed the
                // gesture. [detectTapGestures] only
                // fires its onTap when both the
                // down AND the up are unconsumed
                // (Compose's default for the
                // underlying loop), but it doesn't
                // expose that consumed state to the
                // caller — the manual loop is the
                // only way to check
                // `up.isConsumed` from outside.
                //
                // V_NOW: cooperative consumption model.
                //   - Empty area (no child consumed):
                //     `up.isConsumed = false` →
                //     fire onAction(ExitRecentMultiSelect)
                //   - Tap on thumbnail: thumbnail's
                //     [detectTapGestures] (multi-select
                //     mode branch in v3) consumes the
                //     up via its internal press
                //     handling → `up.isConsumed = true`
                //     → detector releases, no dismiss.
                //   - Tap on the "添加图片" + button:
                //     the button's [Modifier.clickable]
                //     consumes the up → no dismiss.
                //     (Tapping the + button in
                //     multi-select mode would also
                //     fire the SAF picker; we let
                //     that happen rather than
                //     dismissing multi-select too.)
                //   - Tap on the sheet itself
                //     (header / action buttons /
                //     drag handle): the [BottomSheetPanel]'s
                //     Surface consumes the up → no
                //     dismiss. The sheet's own
                //     swipe-down-to-dismiss and
                //     header-close-button-to-dismiss
                //     paths remain the primary
                //     dismissal affordances.
                //   - Tap on the bulk-delete confirm
                //     dialog: the [AlertDialog]'s
                //     content consumes the up → no
                //     dismiss. The dialog's
                //     onDismissRequest handles its own
                //     dismissal.
                //
                // V_NOW: NO scrim is added. The
                // tap-to-dismiss is a "natural"
                // Android affordance without a visual
                // hint — the user can discover it
                // just by tapping. A scrim would dim
                // the canvas (and the thumbnails
                // behind it) which contradicts the
                // non-modal design intent of the
                // sheet — the user should still be
                // able to see and interact with the
                // grid below the sheet. (The
                // existing [BottomSheetPanel] is
                // also non-modal: the kdoc on that
                // widget explicitly notes "Touches
                // that land OUTSIDE the sheet's
                // [Surface] pass through to the
                // underlying canvas".)
                .pointerInput(state.images.isEmpty() && state.recentMultiSelectMode) {
                    if (!state.recentMultiSelectMode) return@pointerInput
                    awaitEachGesture {
                        // V_NOW: [requireUnconsumed = true]
                        // is critical — without it, the
                        // outer detector would see the
                        // down even if a child
                        // immediately consumed it, and
                        // the loop would proceed to
                        // await the up. The up check
                        // would still catch the
                        // consumed-up case, but
                        // requiring unconsumed downs
                        // from the start avoids a
                        // useless gesture loop when a
                        // child claims the gesture
                        // instantly (e.g. the grid's
                        // scroll on a vertical drag).
                        val down = awaitFirstDown(requireUnconsumed = true)
                        // V_NOW: manual up-wait via
                        // [awaitPointerEvent] — the
                        // Compose 1.2.x baseline used
                        // here doesn't expose
                        // [awaitFirstUp] directly (that
                        // lands in 1.3+). The
                        // [awaitPointerEvent] loop
                        // below walks the change list
                        // and looks for the matching
                        // pointer going up; if it
                        // finds the up AND the up
                        // change is unconsumed, fire
                        // the dismiss.
                        var upConsumed = false
                        var didFireUp = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) {
                                upConsumed = change.isConsumed
                                didFireUp = true
                                break
                            }
                            // V_NOW: if the change is
                            // already consumed by a
                            // child (e.g. a vertical
                            // scroll on the grid), bail
                            // out without firing the
                            // dismiss — the child has
                            // claimed the gesture.
                            if (change.isConsumed && !change.pressed.not()) {
                                // No-op — we keep looping
                                // until the up so we
                                // can still read the
                                // consumed state. The
                                // dismiss decision is
                                // made on the up event.
                            }
                        }
                        if (didFireUp && !upConsumed) {
                            onAction(PockRefAction.ExitRecentMultiSelect)
                        }
                    }
                },
        ) {
            val actionPanelBottomInset = Dimens.CornerInset
            CanvasPanel(
                layerPanelBottomInset = actionPanelBottomInset,
                layerPanelExpanded = layerPanelExpanded,
                onLayerPanelExpandedChange = { layerPanelExpanded = it },
                state = state,
                onAction = onAction,
                onImport = onImport,
                onOpenScene = onOpenScene,
                onSaveScene = onSaveScene,
                // [BottomRightActionPanel] is rendered as a
                // separate sibling of this [BoxWithConstraints]
                // below, so [CanvasPanel] stays focused on canvas
                // content and gestures.
                 imageLazyLoader = imageLazyLoader,
                imageDecoder = imageDecoder,
                 gifPlaybackSources = gifPlaybackSources,
                 gifPlaybackStates = gifPlaybackStates,
                 selectedGifPlaybackImageId = selectedGifPlaybackImageId,
                 // V_NOW (2026-06-16): thread the export trigger.
                 exportInProgressFlow = exportInProgressFlow,
                 onStartLogExport = onStartLogExport,
                 modifier = Modifier
                     .fillMaxSize(),
                hoverProgress = hoverProgress,
                canvasBitmapCache = canvasBitmapCache,
                canvasBitmapWorkingSet = canvasBitmapWorkingSet,
                canvasTileCache = canvasTileCache,
                posterizeCache = posterizeCache,
                gifFrameBitmapCache = gifFrameBitmapCache,
            )

            // V_NOW (2026-06-19): lock-button visibility
            // toggle is now handled inside the canvas's
            // own pointerInput (see the tap-vs-drag
            // detection at the pointer-up path in
            // PockRefCanvas). The previous approach used
            // a separate transparent
            // [Box(matchParentSize)] overlay with its own
            // [pointerInput], but on the SM-X810 the
            // mere presence of a sibling
            // [matchParentSize] Box with a [pointerInput]
            // modifier (regardless of pass: Main or
            // Final) caused the canvas's pan/zoom to
            // stop working in global-lock mode. Moving
            // the toggle detection into the canvas's own
            // gesture loop avoids the sibling entirely.

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
            // V_NOW (2026-06-10 unified popup parity):
            // tap-outside scrim for the import popup.
            // The popup is an inline
            // [AnimatedVisibility] inside the toolbar
            // Surface, so it has no built-in
            // [Popup.onDismissRequest] — the scrim is
            // the only way to dismiss it via tap-
            // outside. The dispatch goes through
            // [PockRefAction.SetImportSourceMenuOpen]
            // to keep the visibility source of truth
            // in [PockRefUiState] (the parent
            // [TopToolbar] reads [state.importSourceMenuOpen]
            // via its [importSourceMenuOpen] param).
            if (state.importSourceMenuOpen) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) {
                            onAction(PockRefAction.SetImportSourceMenuOpen(false))
                        },
                )
            }
            // Keep this panel above both the canvas sheets and the
            // full-screen import/title dismiss scrims. Otherwise a
            // visible FAB tap dismisses the popup without running layout.
            // A newly-created blank canvas is still an open canvas and should
            // keep the bottom-right auto-layout/action panel available.
            if (chromePolicy.showActionPanel) {
                BottomRightActionPanel(
                    settingsOpen = state.settingsSheetOpen,
                    showLayerButton = chromePolicy.showLayerPanel,
                    onOpenLayers = { layerPanelExpanded = !layerPanelExpanded },
                    bottomInset = actionPanelBottomInset,
                    onAction = { action ->
                        if (state.importSourceMenuOpen) {
                            onAction(PockRefAction.SetImportSourceMenuOpen(false))
                        }
                        if (isEditingSceneName) dismissSceneNameEdit()
                        onAction(action)
                    },
                    onSaveScene = onSaveScene,
                    onExportCanvas = { onAction(PockRefAction.ExportCanvas) },
                )
            }
            //
            // V_NOW (2026-06-08 速写练习 focus-mode
            // revision): gate on
            // [state.practiceState == null] so the top
            // toolbar (recent-files menu, scene-name
            // editor, 3-dots menu, save button) is
            // skipped during practice. Same reasoning as
            // the FAB below — skipping the render
            // avoids the toolbar's internal
            // remember{ mutableStateOf(...) } slots
            // from staying allocated, and ensures no
            // menu state from a previous session
            // bleeds into the next.
            //
            // V_NOW (2026-06-08 启动页不要显示顶栏和全局锁):
            // also gate on [state.images.isNotEmpty()]
            // so the start screen (no images, just the
            // empty-canvas hero) doesn't show the top
            // toolbar OR the global-lock button. The
            // empty state has its own Import / Open .pur
            // buttons in [EmptyCanvasState] which cover
            // the same entry points — the toolbar's
            // recent-files menu, scene-name editor,
            // 3-dots menu, save button, and lock toggle
            // are all redundant (or meaningless, for the
            // lock) when there's no scene to act on. The
            // FAB ([PockRefAction.ImportImages]) is NOT
            // gated by this — it stays visible on the
            // start screen because it's the primary "add
            // your first image" affordance.
            // A temporary blank canvas has no lockable scene content, so its
            // add/open toolbar must remain reachable even if lock mode was
            // persisted from the previous scene.
            if (chromePolicy.showTopToolbar) {
                TopToolbar(
                onWidthChanged = { topToolbarWidth = with(chromeDensity) { it.toDp() } },
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
                // V_NOW (2026-06-10 unified popup parity):
                // import popup state hoisted to [PockRefApp]
                // pattern. Drives (a) the parent scrim
                // tap-outside dismiss and (b) the
                // [ToolbarIconButton.active] highlight on
                // the import button itself. State is sourced
                // from [PockRefUiState.importSourceMenuOpen]
                // (added when the import-source menu was
                // first introduced) so the visibility
                // survives recomposition and could be
                // driven by another chrome element in the
                // future if needed.
                importSourceMenuOpen = state.importSourceMenuOpen,
                onImportSourceMenuOpenChange = {
                    onAction(PockRefAction.SetImportSourceMenuOpen(it))
                },
                // V_NOW (2026-06-10 #2 import popup user spec
                // "顶栏 import 按钮 tap 后弹 3 选项 popup:
                // 相册 / 剪贴板 / 文件"): the [+] import
                // button now opens a transient in-toolbar
                // popup (sibling [AnimatedVisibility] in
                // the toolbar Surface, mirroring the
                // recent-files popup) instead of going
                // straight to the SAF picker. Three
                // options — gallery
                // ([photoPickerLauncher] = Android 13+
                // PhotoPicker, auto-fallback to SAF on
                // older), clipboard
                // ([onImportFromClipboard] =
                // ClipboardManager read), and files
                // ([onImport] = the existing SAF
                // [OpenMultipleDocuments] path). Empty-
                // canvas state still has its own
                // [EmptyCanvasState] import button that calls
                // [onImport] directly (intentionally NOT
                // routed through the dropdown — a popup
                // anchored to the top toolbar would jump to a
                // screen corner when the user taps a button
                // in the canvas center).
                onPickGallery = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        ),
                    )
                },
                onPickClipboard = onImportFromClipboard,
                onPickFiles = onImport,
                onOpenScene = onOpenScene,
                onEnterFloatingWindow = onEnterFloatingWindow,
                showFloatingWindowButton = showFloatingWindowButton,
                onTakePhoto = {
                    try {
                        val photoDir = context.getExternalFilesDir(
                            android.os.Environment.DIRECTORY_PICTURES,
                        ) ?: context.cacheDir
                        photoDir.mkdirs()
                        val photoFile = java.io.File.createTempFile(
                            "pockref_camera_", ".jpg", photoDir,
                        )
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile,
                        )
                        cameraPhotoUri.value = uri
                        // V_NOW (2026-06-11): Samsung OneUI 6
                        // requires the CALLING app to hold
                        // CAMERA runtime permission before
                        // resolving ACTION_IMAGE_CAPTURE. Check
                        // first; if not granted, request via
                        // the permission launcher and defer
                        // cameraLauncher.launch() to the
                        // permission callback.
                        val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(
                                android.Manifest.permission.CAMERA,
                            )
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e(
                            "PockRefCamera",
                            "takePhoto failed",
                            t,
                        )
                    }
                },
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
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.pockref.app.ui.settings.LocalAppUpdateController provides updateController,
            ) {
            SettingsPanel(
                topInset = statusBarTopDp + Dimens.CornerInset,
                bottomInset = actionPanelBottomInset,
                open = state.settingsSheetOpen,
                onDismiss = { onAction(PockRefAction.SetSettingsSheetOpen(false)) },
                state = state,
                onAction = onAction,
                onChooseDefaultSaveDirectory = {
                    saveDirectoryPickerLauncher.launch(selectedSaveTreeUri)
                },
            )
            }

            // V_NOW (2026-06-08 "global image lock" feature):
            // top-right lock toggle. Anchored at
            // [Alignment.TopEnd] with the SAME 24dp
            // [Dimens.CornerInset] the top toolbar uses on
            // its TOP edge and the bottom-right FAB uses
            // on its END edge — so the four corner-chrome
            // elements (top-left toolbar / top-right
            // global-lock / bottom-left minimap /
            // bottom-right FAB) all sit on the same
            // 24dp grid, no matter how the title row
            // grows or shrinks.
            //
            // Visual spec (M3 + matches the FAB):
            //   - Size: 48dp square (M3 WCAG 2.5.5 AAA
            //     minimum touch target, same as the FAB).
            //   - Shape: 12dp [Dimens.ChromeCornerRadius]
            //     rounded — same as the FAB and toolbar.
            //   - Fill: [surface] at 0.92 alpha (frosted
            //     glass), same as the FAB. The user
            //     feedback for the FAB
            //     ("right-bottom-corner 一键排版按钮为
            //     什么并不是和顶栏一个颜色") established
            //     the uniform chrome-colour policy, and
            //     this button follows it.
            //   - Elevation: 6dp [Dimens.FabElevation]
            //     so the active state has the same
            //     "primary action" depth as the FAB.
            //   - Icon: 24dp, same as the FAB. We use
            //     [Icons.Rounded.Lock] when the global
            //     lock is on, [Icons.Rounded.LockOpen]
            //     when off — both from the same icon
            //     family, same 24dp, same tint, same
            //     horizontal padding inside the box. The
            //     tint flips to [error] when the lock is
            //     active so a 1-glance visual says
            //     "this is doing something".
            //   - State layer: same 8% / 12% primary-tinted
            //     hover/press feedback as the FAB.
            //   - Click handler: dispatches
            //     [PockRefAction.ToggleGlobalImageLock],
            //     which the reducer mirrors to AppPreferences
            //     so the choice survives a process kill.
            //
            // V_NOW (2026-06-08 速写练习 focus-mode
            // revision): gate the whole lock toggle on
            // [state.practiceState == null] so it
            // genuinely doesn't render during practice.
            // Same reasoning as the FAB and toolbar —
            // skipping the render is cheaper and avoids
            // the lock-state remembered slot from staying
            // allocated mid-practice.
            //
            // V_NOW (2026-06-08 启动页不要显示全局锁定):
            // also gate on [state.images.isNotEmpty()] so
            // the global lock toggle doesn't render on
            // the empty-canvas start screen. The lock
            // only makes sense once there's at least one
            // image to lock, and the start screen already
            // has its own Import / Open .pur buttons in
            // [EmptyCanvasState] covering the entry
            // points. This `if` is independent of the
            // surrounding [TopToolbar] block — the lock
            // toggle lives at the top-right of the canvas
            // (not inside the toolbar's row), so it has
            // its own gate.
            //
            // V_NOW (2026-06-19 "全局锁定+冻结模式下点
            // 应用界面,锁定按钮隐藏" feature; same-day
            // follow-up "激活全局锁定后也要生效,不只是
            // 冻结模式"): also gate on
            // `!state.lockButtonHidden` so the user can
            // tap the canvas in any lock-engaged state
            // (soft or hard mode — see the
            // [LockVisibilityToggleOverlay] block above
            // for why both modes qualify) to hide the
            // lock button for a clean view, and tap
            // again to bring it back. The flag is
            // only toggled by the overlay itself, so
            // outside any lock-engaged state the
            // overlay is unmounted and the flag stays
            // `false` (the reducer also resets it on
            // every exit from lock mode, see the
            // [ToggleGlobalImageLock] and
            // [ToggleLockCanvasFrozen] reducers for
            // the two reset paths).
            if (state.practiceState == null && !state.markupModeActive &&
                !state.lockButtonHidden) {
                val hasLockButton = chromePolicy.showGlobalLock
                val hasMarkup = state.markupStrokes.any { it.isRenderable } ||
                    state.markupTexts.any { it.text.isNotBlank() }
                if (hasMarkup) {
                    MarkupVisibilityButton(
                        visible = state.markupVisible,
                        hasLockButton = hasLockButton,
                        onToggle = { onAction(PockRefAction.ToggleMarkupVisibility) },
                        statusBarTopDp = statusBarTopDp,
                    )
                }
                if (hasLockButton) {
            // V_NOW (2026-06-10): the global-lock button
            // is extracted to [GlobalLockButton] so the
            // [combinedClickable] opt-in lives at the
            // helper-function level (rather than ad-hoc on
            // a `val` declaration, which doesn't propagate
            // to nested lambda call-sites). The helper
            // carries the full visual / interaction spec;
            // see its doc-comment for the three-state
            // machine (OFF / SOFT / HARD) and the
            // click / long-press dispatch matrix.
            GlobalLockButton(
                state = state,
                onAction = onAction,
                statusBarTopDp = statusBarTopDp,
            )
                }
            }  // close if (state.practiceState == null) gating the lock toggle

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
                val selectedExportIds = if (state.selectedImageIds.isNotEmpty()) {
                    state.selectedImageIds
                } else {
                    state.selectedImageId?.let { setOf(it) }.orEmpty()
                }
                val selectedCount = selectedExportIds.size
                ExportDialogHost(
                    selectedCount = selectedCount,
                    onExportOriginals = {
                        onAction(PockRefAction.CancelExport)
                        exportOriginalsLauncher.launch(saveDirectoryInitialUri)
                    },
                    onExportCanvas = {
                        pendingSelectedCanvasImageIds = selectedExportIds.toList()
                        onAction(PockRefAction.CancelExport)
                        onAction(PockRefAction.ExportCanvas)
                    },
                    onDismiss = { onAction(PockRefAction.CancelExport) },
                )
            }
            // V_NOW (2026-06-16): the canvas-export options
            // dialog. Was previously a dead state — the
            // [PockRefUiState.showExportCanvasDialog] flag was
            // set in [PureRefViewModel] but no composable read
            // it, so the user saw the SAF picker pop up
            // directly with no options to pick. Now
            // [onExportCanvas] dispatches [PockRefAction.ExportCanvas]
            // (which sets this flag to true), and this
            // composable is the single place that consumes the
            // flag and renders [ExportCanvasDialogHost]. The
            // dialog owns the dismiss flow via
            // [PockRefAction.CancelExportCanvas] (back / scrim
            // tap) and the "确认导出" button (which
            // launches the SAF picker — the callback then
            // dispatches [PockRefAction.ConfirmExportCanvas]
            // with the URI + the current background mode from
            // state).
            if (state.showExportCanvasDialog) {
                ExportCanvasDialogHost(
                    sceneName = state.sceneName,
                    selectedImageIds = pendingSelectedCanvasImageIds.toSet().takeIf { it.isNotEmpty() },
                    maxDim = state.exportCanvasMaxDim,
                    backgroundMode = state.exportCanvasBackground,
                    onAction = onAction,
                    initialUri = preferredSaveTreeUri,
                    exportCanvasLauncher = exportCanvasLauncher,
                    exportSelectedCanvasLauncher = exportSelectedCanvasLauncher,
                )
            }
            LaunchedEffect(state.showExportCanvasDialog) {
                if (!state.showExportCanvasDialog) {
                    pendingSelectedCanvasImageIds = emptyList()
                }
            }
            // OpenDocumentTree. See [PockRefAction.RequestSaveScene]
            // and the comment block above [saveSceneTreeLauncher]
            // for why the Save flow no longer uses
            // ACTION_CREATE_DOCUMENT.
            if (state.showSaveSceneNameDialog) {
                SaveSceneNameDialog(
                    initialName = state.pendingSaveSceneName,
                    onConfirm = { typed ->
                        onAction(PockRefAction.ConfirmSaveSceneName(typed))
                    },
                    onDismiss = {
                        onAction(PockRefAction.DismissSaveSceneNameDialog)
                    },
                )
            }
            if (state.pendingMarkupTextPosition != null) {
                MarkupTextDialog(
                    value = state.pendingMarkupTextValue,
                    fontSize = state.pendingMarkupTextFontSize,
                    isEditing = state.editingMarkupTextId != null,
                    onValueChange = { onAction(PockRefAction.SetPendingMarkupText(it)) },
                    onFontSizeChange = { onAction(PockRefAction.SetPendingMarkupTextFontSize(it)) },
                    onConfirm = { onAction(PockRefAction.CommitMarkupText) },
                    onDismiss = { onAction(PockRefAction.CancelMarkupText) },
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
                posterizeCache = posterizeCache,
                progress = hoverProgress,
            )
        }
        updateController?.let {
            Box(Modifier.fillMaxSize()) {
            com.pockref.app.ui.settings.AppUpdateNotice(
                controller = it,
                compact = state.hasSceneContent || state.blankCanvasOpen || !showFloatingWindowButton,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            )
            }
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
    layerPanelBottomInset: Dp = Dimens.CornerInset,
    layerPanelExpanded: Boolean = false,
    onLayerPanelExpandedChange: (Boolean) -> Unit = {},
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
    canvasBitmapWorkingSet: CanvasBitmapWorkingSet,
    canvasTileCache: androidx.compose.runtime.snapshots.SnapshotStateMap<com.pockref.app.data.ImageTileKey, ImageBitmap>,
    posterizeCache: PosterizeBitmapCache,
    gifFrameBitmapCache: GifFrameBitmapCache,
    gifPlaybackSources: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackSource>,
    gifPlaybackStates: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackState>,
    selectedGifPlaybackImageId: String?,
    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Threaded down from [PockRefApp] so [LogExportFab]
    // (mounted on the start-page branch of this Box) can
    // observe the VM-side export-in-progress flag and
    // dispatch a build without [CanvasPanel] holding a
    // direct viewModel reference.
    exportInProgressFlow: StateFlow<Boolean> = MutableStateFlow(false),
    onStartLogExport: (
        appLabel: String,
        onReady: (suggestedName: String, content: String) -> Unit,
        onError: (reason: String) -> Unit,
    ) -> Unit = { _, _, _ -> },
) {

    // V_NOW (S-04 chip alignment): recompute statusBarTopDp here
    // so [SelectionModeOverlay] (a BoxScope extension called from
    // inside this Box) can use it without having to thread it through
    // the CanvasPanel parameter list. The cost is one extra
    // WindowInsets read per recomposition, which is negligible.
    val statusBarTopPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val statusBarTopDp = with(LocalDensity.current) { statusBarTopPx.toDp() }
    val chromePolicy = remember(state) { CanvasChromePolicy.resolve(state) }
    // V_NOW (2026-06-10, "全局锁定/冻结模式只显示
    // 全局锁定按钮"): re-derive the same
    // [isLockEngaged] boolean the outer [PockRefApp]
    // function uses, but locally inside [CanvasPanel]
    // so the inner chrome layers
    // ([MinimapChrome] /
    // [SelectionModeOverlay] / [SelectionBottomSheet]
    // / [PracticeConfigSheet] / [CropOverlay] gate)
    // can reference it without being threaded through
    // the parameter list. The boolean is a pure
    // function of [state] (no async I/O), so
    // recomputing it here matches the outer value
    // exactly on every recomposition and keeps the
    // signature of [CanvasPanel] short.
    val isLockEngaged = state.globalImageLock || state.lockCanvasFrozen
    Box(
        modifier
            .fillMaxSize()
    ) {
        PockRefCanvas(
            state = state,
            modifier = Modifier.fillMaxSize().glassBackdropSource(
                enabled = state.isCanvasOpen && state.practiceState == null,
            ),
            onAction = onAction,
            imageLazyLoader = imageLazyLoader,
            imageDecoder = imageDecoder,
            hoverProgress = hoverProgress,
            bitmapCache = canvasBitmapCache,
            bitmapWorkingSet = canvasBitmapWorkingSet,
            tileCache = canvasTileCache,
            posterizeCache = posterizeCache,
            gifFrameBitmapCache = gifFrameBitmapCache,
            gifPlaybackSources = gifPlaybackSources,
            gifPlaybackStates = gifPlaybackStates,
            selectedGifPlaybackImageId = selectedGifPlaybackImageId,
        )
        if (state.markupModeActive) {
            var showMarkupColors by rememberSaveable { mutableStateOf(false) }
            MarkupToolPalette(state, onAction, Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp).navigationBarsPadding().imePadding(),
                onColorRequested = { showMarkupColors = true })
            MarkupSelectionActions(state, onAction, Modifier.align(Alignment.TopEnd)
                .padding(top = statusBarTopDp + Dimens.CornerInset + 64.dp,
                    end = Dimens.CornerInset))
            if (showMarkupColors) {
                MarkupColorPicker(state.markupColor,
                    onColor = { onAction(PockRefAction.SetMarkupColor(it)) },
                    onDismiss = { showMarkupColors = false })
            }
            MarkupModeBar(
                onCancel = { onAction(PockRefAction.CancelMarkupSession) },
                onDone = { onAction(PockRefAction.SetMarkupModeActive(false)) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarTopDp + Dimens.CornerInset,
                        start = Dimens.CornerInset, end = Dimens.CornerInset),
            )
        }
        if (!state.hasSceneContent && !state.blankCanvasOpen) {
            // V_NOW (2026-06-10, "启动页多选 + 底部 Bulk
            // Action Bar 置顶"): the start-page multi-select
            // chrome. Three siblings of the centered
            // [EmptyCanvasState]:
            //   1. [RecentMultiSelectTopBar] (top-aligned) —
            //      only renders when [PockRefUiState.recentMultiSelectMode]
            //      is true. Shows the "已选 N 项" count + a
            //      close (X) button to exit the mode.
            //   2. [RecentMultiSelectBottomBar] (bottom-
            //      aligned) — only renders when in multi-
            //      select mode AND [recentScenes] is not
            //      empty (an empty list would mean no actions
            //      to do, so the bar is hidden). Hosts the
            //      全选/置顶/取消置顶/删除 bulk actions.
            //   3. [BulkDeleteConfirmDialog] — sibling
            //      overlay (not positioned; uses
            //      [AlertDialog]'s built-in window). Only
            //      renders when [PockRefUiState.bulkDeleteConfirmOpen]
            //      is true. Confirms the user's intent
            //      before the destructive [BulkDeleteRecentScenes]
            //      dispatch fires.
            // The three siblings are independent of the
            // [EmptyCanvasState] — they don't move or
            // re-layout it, the [Modifier.align] in
            // [EmptyCanvasState] keeps it centred while
            // the chrome slides in from the top and
            // bottom edges.
            //
            // V_NOW (2026-06-10, sheet 统一): the v1.0
            // dual top-bar + bottom-bar chrome has
            // been collapsed into a SINGLE bottom
            // sheet — [RecentMultiSelectSheet]. The
            // sheet itself owns the
            // "visible = recentMultiSelectMode &&
            // recentScenes.isNotEmpty()" gate, so
            // the call site just renders it
            // unconditionally; the sheet no-ops
            // when the gate is false. This matches
            // the [com.pockref.app.ui.PureRefApp.SelectionBottomSheet]
            // / crop overlay's [BottomSheetPanel]
            // usage — one sheet, one chrome element,
            // visual contract identical to every
            // other sheet in the app.
            //
            // V_NOW (BackHandler — see further down): the
            // system back button, when the start page is
            // in multi-select mode, exits multi-select
            // first (before the existing "close scene" /
            // "finish activity" priority chain). The chain
            // is updated so the recent-multi-select case
            // ranks ABOVE the scene-is-open case, which is
            // a no-op for the start page anyway (no
            // scene is open there — the [EmptyCanvasState]
            // is the canvas-empty branch). The sheet's
            // own back handler is disabled
            // ([dismissOnBackPress] = false) so the
            // PockRefApp-level chain is the single
            // back-press authority.
            RecentMultiSelectSheet(
                state = state,
                onAction = onAction,
            )
            BulkDeleteConfirmDialog(
                state = state,
                onAction = onAction,
            )
            // V_NOW (2026-06-16 user feedback "把导出日志按钮
            // 改成默认不显示,点 pockref 图标 7 次后才会显示"):
            // the FAB visibility is now gated by a local
            // remember flag (not in PockRefUiState, not in
            // AppPreferences). The flag starts false every
            // time the EmptyCanvasState composable enters the
            // composition (which is every fresh app launch —
            // matching the spec "应用每次打开启动页的导出
            // 日志按钮都不显示"). The 7-tap counter inside
            // [EmptyCanvasState] flips this flag to true via
            // [onLogExportFab]; once true it stays true for
            // the rest of the session (no auto-hide, no
            // re-lock on backgrounding — the spec doesn't
            // ask for either). The Settings → 外观 →
            // "启动页显示导出日志" toggle is removed
            // entirely (see SettingsAppearanceTab).
            val showLogExportFab = remember { mutableStateOf(false) }
            EmptyCanvasState(
                state = state,
                onAction = onAction,
                onOpenBlankCanvas = { onAction(PockRefAction.OpenBlankCanvas) },
                showLogExportFab = showLogExportFab.value,
                onLogExportFab = { showLogExportFab.value = true },
                modifier = Modifier.align(Alignment.Center),
            )
            // V_NOW (2026-06-16, "启动页导出运行日志" feature):
            // Bottom-right export FAB. Mounted ONLY on the start
            // page branch; once a scene is loaded the FAB
            // disappears, matching the "启动页" semantics the
            // feature was scoped to. The StateFlow + callback
            // come from PockRefApp's parameters so we don't pull
            // a viewModel reference into this 11k-line composable.
            //
            // V_NOW (2026-06-16 user feedback "把导出日志按钮
            // 改成默认不显示,点 pockref 图标 7 次后才会显示"):
            // the visibility flag is the local
            // [showLogExportFab] remember'd above, NOT a
            // PockRefUiState field. The 7-tap unlock is
            // session-only by design (per the user's spec
            // "应用每次打开启动页的导出日志按钮都不显示"),
            // so we don't pollute the persistent UI state
            // with a field that would always start false on
            // next launch anyway.
            if (showLogExportFab.value) {
                LogExportFab(
                    exportInProgress = exportInProgressFlow,
                    onStartLogExport = onStartLogExport,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.EdgeInset),
                )
            }
        } else if (state.hasSceneContent) {
            if (state.cropImageId != null) {
                // V_NOW (M3 sheet chrome, user feedback "裁切界面的视觉风格
                // 要跟现在选区弹出后整体窗口的展示风格保持一致"): the
                // previous 0.5-alpha black scrim is removed —
                // SelectionBottomSheet uses
                // `scrimColor = Color.Transparent`, and the
                // crop overlay now matches that. The previous
                // scrim was a separate [Box] (NOT the
                // [ModalBottomSheet]'s built-in scrim — the
                // crop never used ModalBottomSheet, it was a
                // plain [Column] inside a [BoxScope] with the
                // scrim drawn explicitly here). Removing it
                // means the main canvas is now fully visible
                // behind the floating crop layer, which is
                // what the user asked for ("整体窗口的展示风格
                // 保持一致").
                //
                // The crop overlay's bottom toolbar is a
                // CUSTOM [Surface] with M3 sheet chrome
                // (28dp top corners, surface color solid,
                // 0dp tonalElevation, 32x4dp drag handle,
                // 560dp max width) — visually identical to a
                // [ModalBottomSheet] but WITHOUT its scrim's
                // touch-interception, so the floating image
                // layer stays interactive (the user can drag
                // crop handles while the toolbar is up).
                // [ModalBottomSheet] would have been the
                // natural choice but its built-in scrim
                // intercepts ALL touches outside the sheet
                // content, blocking drag interactions on
                // the floating crop layer above.
                CropOverlay(
                    state = state,
                    onAction = onAction,
                    bitmapCache = canvasBitmapCache,
                    // V_NOW: try the decoder first (same
                    // [RandomAccessFile] handle the main
                    // canvas uses for streaming decode), then
                    // fall back to the lazy loader (used by
                    // the ViewModel for batch operations like
                    // [commitCrop]). This mirrors
                    // [PureRefViewModel.resolveImageBytes]'s
                    // precedence so the crop preview and the
                    // committed crop see the same bytes.
                    imageBytesResolver = { off, len ->
                        imageDecoder?.readImageBytes(off, len)
                            ?: imageLazyLoader?.readImageBytes(off, len)
                    },
                )
            } else if (
                chromePolicy.showSelectionPanel
            ) {
                // V_NOW (P2-C1): M3 ModalBottomSheet
                // replaces the legacy
                // SelectionFloatingToolbar. Visibility
                // is derived from
                // [state.selectedImageIds.size] — the
                // sheet raises whenever the user has any
                // selection and is dismissed via the
                // close ✕, drag handle, or scrim tap.
                // See the SelectionBottomSheet KDoc
                // for the full design contract.
                // V_NOW (2026-06-10, "全局锁定/冻结模式只
                // 显示全局锁定按钮"): also gate on
                // [!isLockEngaged] so the selection
                // sheet can't appear while the canvas
                // is in read-only lock / freeze mode
                // (the chrome is supposed to be
                // invisible at that point). The
                // reducer has its own guard
                // ([PureRefViewModel.setLocked] /
                // [PureRefViewModel.beginSceneGesture])
                // that prevents new gestures from
                // starting, but this gate is a UI-
                // level safety so an existing
                // selection left over from before
                // the user locked can't keep
                // resurfacing the sheet.
                SelectionBottomSheet(
                    state = state,
                    onAction = onAction,
                    gifPlaybackSources = gifPlaybackSources,
                    gifPlaybackStates = gifPlaybackStates,
                    selectedGifPlaybackImageId = selectedGifPlaybackImageId,
                )
                // V_NOW (2026-06-08 速写练习 commit 3):
                // full-screen practice view. Renders only
                // when [state.practiceState] is non-null
                // (= RUNNING / PAUSED / FINISHED). When
                // null, [PracticeScreen] early-returns
                // and the user sees the canvas + chrome
                // underneath. Mounted AFTER
                // [SelectionBottomSheet] and
                // [PracticeConfigSheet] so it overlays
                // both — these three are mutually exclusive
                // by the time they're rendered (the
                // SelectionBottomSheet auto-dismisses on
                // the "速写练习" click, the config sheet
                // dispatches DismissPracticeConfigSheet
                // on its "开始" tap, leaving the practice
                // screen alone on top).
                //
                // Why a [BoxScope] extension on
                // [PracticeScreen]: the canvas in the
                // parent Box is `Modifier.matchParentSize()`
                // and the practice screen needs the same
                // fill-max-size-with-overlay semantics.
                // Defining it as `BoxScope.PracticeScreen`
                // lets it `Modifier.fillMaxSize()` directly
                // without taking the parent Box as a
                // parameter.
                if (state.practiceState != null) {
                    GlassBackdropHost {
                        PracticeScreen(
                            state = state.practiceState,
                            images = state.images,
                            imageDecoder = imageDecoder,
                            imageLazyLoader = imageLazyLoader,
                            onAction = onAction,
                        )
                    }
                }
                // V_NOW (2026-06-08 速写练习 commit 2):
                // pre-flight config sheet. Lives next to
                // [SelectionBottomSheet] in the same
                // BoxScope so the two are mutually
                // exclusive on screen (SelectionBottomSheet
                // auto-dismisses when the user clicks the
                // "速写练习" entry; the user can re-show
                // it by tapping any selected image, per
                // the existing [selectionSheetShowTick]
                // re-raise contract).
                //
                // [lastConfig] is read directly from
                // [com.pockref.app.data.AppPreferences.lastPracticeConfig]
                // — the controller's StartPractice side
                // effect writes a fresh [PracticeConfig]
                // on every start, so this is always
                // current. Passing it as a parameter
                // (not reading prefs inside the sheet)
                // keeps the sheet testable in isolation
                // and avoids SharedPreferences I/O in
                // composition.
                //
                // V_NOW: appPrefs is instantiated here
                // from [LocalContext.current] (same
                // pattern as [SettingsAutoSaveSection])
                // — [PockRefApp] doesn't take an
                // [AppPreferences] parameter and
                // adding one would force a refactor of
                // every call site (MainActivity +
                // androidTest setUp). The instantiation
                // is cheap (just a SharedPreferences
                // handle) and the getter for
                // [lastPracticeConfig] is a
                // getString/getLong read — no I/O storm.
                val appPrefs = com.pockref.app.data.AppPreferences(
                    androidx.compose.ui.platform.LocalContext.current,
                )
                PracticeConfigSheet(
                    // V_NOW (2026-06-10, "全局锁定/
                    // 冻结模式只显示全局锁定按钮"):
                    // mask the practice config
                    // sheet with [!isLockEngaged] so
                    // it can't pop while the canvas
                    // is in read-only lock / freeze
                    // mode. The reducer already
                    // blocks [StartPractice] when
                    // [globalImageLock] is on, but
                    // this gate prevents the config
                    // sheet from even APPEARING in
                    // the first place — the user
                    // wouldn't be able to tap "开始"
                    // anyway, so hiding the sheet
                    // is the right user-facing
                    // behaviour.
                    visible = state.practiceConfigSheetOpen && !isLockEngaged,
                    selectedCount = state.selectedImageIds.size,
                    lastConfig = appPrefs.lastPracticeConfig,
                    onStart = { config ->
                        // StartPractice closes the config sheet and
                        // creates the practice state in one update, so
                        // the first image appears immediately without
                        // requiring a follow-up canvas tap.
                        onAction(
                            PockRefAction.StartPractice(
                                imageIds = state.selectedImageIds.toList(),
                                config = config,
                            )
                        )
                    },
                    onDismiss = {
                        onAction(PockRefAction.DismissPracticeConfigSheet)
                    },
                )
                // V_NOW (R-04): subtle "multi-select mode is
                // active" overlay above the canvas. Three
                // states:
                //   - SINGLE mode (default): no overlay
                //   - MULTI mode: 3dp primary border + chip
                //     "多选模式 · 点图片累加选中"
                //   - BOX mode: same border + chip
                //     "框选模式 · 拖动画出选区"
                // Border is a draw-only modifier (no
                // pointer-input impact) so canvas gestures
                // keep working normally.
                //
                // V_NOW (2026-06-08 速写练习 focus-mode
                // revision): gate on
                // [state.practiceState == null] so the
                // mode-border + mode-chip are not drawn
                // during practice. The overlay's purpose
                // is to confirm the user is in MULTI/BOX
                // select mode — irrelevant during the
                // practice session (the user is staring
                // at an image, not selecting).
                if (chromePolicy.showSelectionMode &&
                    // V_NOW (2026-06-10, "使用 sheet
                    // 时取消 chip 显示"): the top-
                    // centre mode chip and the
                    // bottom-centre
                    // [SelectionBottomSheet] carry
                    // the same conceptual payload
                    // (which mode the user is in),
                    // so showing BOTH at once is
                    // redundant. When the sheet is
                    // up the chip auto-hides, and
                    // when the sheet is dismissed
                    // (no selection) the chip
                    // returns. Same UI state
                    // ([PockRefUiState.selectedImageIds]
                    // is empty ↔ sheet hidden ↔
                    // chip visible).
                    state.selectedImageIds.isEmpty()) {
  SelectionModeOverlay(
  state = state,
  modifier = Modifier.padding(
  top = statusBarTopDp + Dimens.CornerInset +8.dp,
  ),
  )
 }
            }
            // Bottom-left layer navigator for the current .pur scene.
            if (chromePolicy.showLayerPanel) {
                // V_NOW: wrap in BoxWithConstraints so the
                // drag-clamping math below can read the
                // parent size (the call site itself is outside
                // the outer BoxWithConstraints at line 710, so
                // we need our own scope). A full BoxWithConstraints
                // is more expensive than `onSizeChanged`, but we
                // need the size at composition time (before the
                // pointerInput's coroutine starts) so the default
                // position can be computed without an async wait.
                BoxWithConstraints(modifier = Modifier
                    .fillMaxSize(),
                ) {
                    CanvasLayersChrome(
                        bottomInset = layerPanelBottomInset,
                        expandedOverride = layerPanelExpanded,
                        onExpandedChange = onLayerPanelExpandedChange,
                        showLauncher = false,
                        state = state,
                        onAction = onAction,
                        imageDecoder = imageDecoder,
                    )
                }
            }
            // Bottom-center selection status pill. Reads the
            // same [PockRefUiState.selectedImageIds] that the
            // floating toolbar above reads, so the two stay
            // in lock-step. Hidden entirely when nothing is
            // selected — the user shouldn't see a stale "0 张"
            // row. Sits at BottomCenter so it never collides
            // with the layer navigator (BottomStart) or the FAB
            // (BottomEnd, on screens where the user has
            // triggered auto-layout).
            // (底部 Pill 已删除：SelectionBottomSheet 是唯一的选中态
            // chrome；左下角图层入口在选中时向上避让。)
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
                    // P1 (2026-06-11 chrome unify): same
                    // tier as the floating chrome (toolbar /
                    // minimap / FAB) so the "drop here" hint
                    // reads as a brief overlay, not a new
                    // visual family.
                    shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
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

@Composable
private fun TopToolbar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    importSourceMenuOpen: Boolean,
    onImportSourceMenuOpenChange: (Boolean) -> Unit,
    onPickGallery: () -> Unit,
    onPickClipboard: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenScene: () -> Unit,
    onEnterFloatingWindow: () -> Unit,
    showFloatingWindowButton: Boolean,
    onTakePhoto: () -> Unit,
    parentMaxWidth: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
    onWidthChanged: (Int) -> Unit = {},
) {
    val toolbarModifier = if (parentMaxWidth != Dp.Infinity && parentMaxWidth > 0.dp) {
        modifier.widthIn(
            max = (parentMaxWidth - Dimens.CornerInset * 2).coerceAtLeast(0.dp),
        )
    } else {
        modifier
    }

    GlassSurface(
        modifier = toolbarModifier
            .onSizeChanged { onWidthChanged(it.width) }
            .testTag("canvas-top-toolbar"),
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(Dimens.TopToolbarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolbarIconButton(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    enabled = state.isCanvasOpen,
                    onClick = { onAction(PockRefAction.CloseScene) },
                )
                ToolbarIconButton(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "导入图像",
                    active = importSourceMenuOpen,
                    onClick = {
                        onImportSourceMenuOpenChange(!importSourceMenuOpen)
                    },
                )
                HighFreqActions(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onAction = onAction,
                )
                if (showFloatingWindowButton) {
                    ToolbarIconButton(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = "悬浮窗",
                        onClick = onEnterFloatingWindow,
                    )
                }
            }

            AnimatedVisibility(
                visible = importSourceMenuOpen,
                enter = expandVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                ),
            ) {
                Column(
                    modifier = Modifier.width(
                        if (showFloatingWindowButton) 224.dp else 180.dp,
                    ),
                ) {
                    ImportSourceMenuItem(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "从相册选择",
                    ) {
                        onImportSourceMenuOpenChange(false)
                        onPickGallery()
                    }
                    ImportSourceMenuItem(
                        icon = Icons.Rounded.ContentPaste,
                        label = "从剪贴板粘贴",
                    ) {
                        onImportSourceMenuOpenChange(false)
                        onPickClipboard()
                    }
                    ImportSourceMenuItem(
                        icon = Icons.Rounded.FileOpen,
                        label = "载入图片",
                    ) {
                        onImportSourceMenuOpenChange(false)
                        onPickFiles()
                    }
                    ImportSourceMenuItem(
                        icon = Icons.Rounded.FileOpen,
                        label = "打开文件",
                    ) {
                        onImportSourceMenuOpenChange(false)
                        onOpenScene()
                    }
                    ImportSourceMenuItem(
                        icon = Icons.Rounded.PhotoCamera,
                        label = "拍照",
                    ) {
                        onImportSourceMenuOpenChange(false)
                        onTakePhoto()
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSourceMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Selection-mode capsule above an icon-only floating action toolbar.
 * All actions remain inline and scroll horizontally in narrower windows.
 */
@Composable
internal fun SelectionBottomSheet(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    gifPlaybackSources: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackSource>,
    gifPlaybackStates: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackState>,
    selectedGifPlaybackImageId: String?,
    onChromeHeightChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    val selectedCount = state.selectedImageIds.size
    if (selectedCount == 0 && state.editingGroupId == null) return
    val selectedGroups = state.selectedGroups
    val selectedGroup = state.selectedGroups.singleOrNull()
    val editingGroup = state.editingGroupId?.let { id -> state.groups.firstOrNull { it.id == id } }
    val groupActionTarget = editingGroup ?: selectedGroup
    val canCreateGroup = !state.requiresGroupFlattening && state.editingGroupId == null &&
        selectedCount >= 1 && selectedGroups.isEmpty() &&
        state.selectedImageIds.none { id -> state.isImageLocked(id) }
    var showRenameGroup by remember { mutableStateOf(false) }
    var showGroupAppearance by remember { mutableStateOf(false) }
    var groupNameDraft by remember(groupActionTarget?.id) {
        mutableStateOf(groupActionTarget?.name.orEmpty())
    }
    var groupBackgroundColor by remember(groupActionTarget?.id) {
        mutableStateOf(groupActionTarget?.backgroundColor ?: Color.Black)
    }
    var groupBackgroundOpacity by remember(groupActionTarget?.id) {
        mutableFloatStateOf(groupActionTarget?.backgroundOpacity ?: 0f)
    }

    val posterizeControlsVisible =
        state.posterizeControlsVisible &&
            state.posterizeTargetImageIds == state.selectedImageIds &&
            state.selectedImageIds.isNotEmpty()

    val activeSelectedIds = state.selectedImageIds.ifEmpty {
        state.selectedImageId?.let(::setOf).orEmpty()
    }
    val allSelectedLocked = activeSelectedIds.isNotEmpty() &&
        activeSelectedIds.all { id -> state.isImageLocked(id) }
    val allGrayscale = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }
                ?.colorAdjust?.saturation?.let { it <= -0.9f } == true
        }
    val allFlippedH = state.selectedImageIds.isNotEmpty() &&
        state.selectedImageIds.all { id ->
            state.images.firstOrNull { it.id == id }?.flipX == true
        }
    val isLockedPanel = allSelectedLocked
    val selectedGifPlaybackSource = selectedGifPlaybackImageId
        ?.let { gifPlaybackSources[it] }
    val selectedGifPlaybackState = selectedGifPlaybackImageId
        ?.let { gifPlaybackStates[it] ?: GifPlaybackState() }

    val quickActions = listOf(
        SheetActionSpec(Icons.Rounded.Flip, "翻转", allFlippedH) {
            onAction(PockRefAction.FlipHorizontal)
        },
        SheetActionSpec(
            if (allSelectedLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            if (allSelectedLocked) "解锁" else "锁定",
            allSelectedLocked,
        ) {
            onAction(PockRefAction.ToggleLockSelected)
        },
        SheetActionSpec(Icons.Rounded.Contrast, "灰度", allGrayscale) {
            onAction(PockRefAction.ConvertToGrayscale)
        },
        SheetActionSpec(Icons.Rounded.Delete, "删除", false, tint = MaterialTheme.colorScheme.error) {
            onAction(PockRefAction.DeleteSelected)
        },
    )
    val row1 = if (state.multiSelectMode || state.boxSelectMode) {
        listOf(
            SheetActionSpec(
                icon = Icons.Rounded.Flip,
                label = "水平翻转",
                active = allFlippedH,
                onClick = { onAction(PockRefAction.FlipHorizontal) },
            ),
            SheetActionSpec(Icons.Rounded.VerticalAlignTop, "向上对齐", false) {
                onAction(PockRefAction.BatchAlign(AlignMode.ALIGN_TOP))
            },
            SheetActionSpec(Icons.Rounded.VerticalAlignBottom, "向下对齐", false) {
                onAction(PockRefAction.BatchAlign(AlignMode.ALIGN_BOTTOM))
            },
            SheetActionSpec(Icons.Rounded.AlignHorizontalLeft, "向左对齐", false) {
                onAction(PockRefAction.BatchAlign(AlignMode.ALIGN_LEFT))
            },
            SheetActionSpec(Icons.Rounded.AlignHorizontalRight, "向右对齐", false) {
                onAction(PockRefAction.BatchAlign(AlignMode.ALIGN_RIGHT))
            },
        )
    } else {
        listOf(
            SheetActionSpec(Icons.Rounded.Flip, "水平翻转", allFlippedH) {
                onAction(PockRefAction.FlipHorizontal)
            },
            SheetActionSpec(Icons.Rounded.Crop, "裁切", false) {
                onAction(PockRefAction.EnterCropMode)
            },
            SheetActionSpec(Icons.AutoMirrored.Rounded.RotateRight, "右旋 90°", false) {
                onAction(PockRefAction.RotateRight)
            },
            SheetActionSpec(Icons.Rounded.RestartAlt, "复原", false) {
                onAction(PockRefAction.ResetTransform)
            },

        )
    }
    val row2 = if (state.multiSelectMode || state.boxSelectMode) {
        listOf(
            SheetActionSpec(Icons.Rounded.ContentCopy, "复制", false) {
                onAction(PockRefAction.DuplicateSelected)
            },
            SheetActionSpec(
                icon = if (allSelectedLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                label = if (allSelectedLocked) "已锁定" else "锁定",
                active = allSelectedLocked,
            ) {
                onAction(PockRefAction.ToggleLockSelected)
            },
            SheetActionSpec(Icons.Rounded.Contrast, "灰度", allGrayscale) {
                onAction(PockRefAction.ConvertToGrayscale)
            },
            SheetActionSpec(Icons.Rounded.Timer, "速写练习", false) {
                onAction(PockRefAction.OpenPracticeConfigSheet)
            },
            SheetActionSpec(Icons.Rounded.Image, "导出", false) {
                onAction(PockRefAction.ExportSelected)
            },
            SheetActionSpec(Icons.Rounded.Delete, "删除", false, tint = MaterialTheme.colorScheme.error) {
                onAction(PockRefAction.DeleteSelected)
            },
        )
    } else {
        val currentSingleId = state.selectedImageId
        listOf(
            SheetActionSpec(Icons.Rounded.ContentCopy, "复制", false) {
                onAction(PockRefAction.DuplicateSelected)
            },
            SheetActionSpec(
                icon = if (allSelectedLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                label = if (allSelectedLocked) "已锁定" else "锁定",
                active = allSelectedLocked,
            ) {
                onAction(PockRefAction.ToggleLockSelected)
            },
            SheetActionSpec(Icons.Rounded.Contrast, "灰度", allGrayscale) {
                onAction(PockRefAction.ConvertToGrayscale)
            },
            SheetActionSpec(
                icon = Icons.Rounded.Star,
                label = "设为封面",
                active = currentSingleId != null &&
                    state.coverImageId == currentSingleId,
            ) {
                val id = currentSingleId
                if (id != null) {
                    val next = if (state.coverImageId == id) null else id
                    onAction(PockRefAction.SetAsCover(next))
                }
            },
            SheetActionSpec(Icons.Rounded.Image, "导出", false) {
                onAction(PockRefAction.ExportSelected)
            },
            SheetActionSpec(Icons.Rounded.Delete, "删除", false, tint = MaterialTheme.colorScheme.error) {
                onAction(PockRefAction.DeleteSelected)
            },
        )
    }
    val groupActions = buildList {
        if (!state.requiresGroupFlattening) {
            if (state.editingGroupId != null) {
                add(SheetActionSpec(Icons.Rounded.Close, "完成组内编辑", false) { onAction(PockRefAction.ExitGroup) })
                if (selectedCount > 0) add(SheetActionSpec(Icons.Rounded.Layers, "移出组", false) {
                    onAction(PockRefAction.RemoveSelectedFromGroup)
                })
            } else if (selectedGroup != null) {
                add(SheetActionSpec(Icons.Rounded.Close, "解组", false) { onAction(PockRefAction.UngroupSelected) })
                add(SheetActionSpec(Icons.Outlined.Edit, "重命名", false) {
                    groupNameDraft = selectedGroup.name
                    showRenameGroup = true
                })
                add(SheetActionSpec(Icons.Outlined.Palette, "外观", false) {
                    groupBackgroundColor = selectedGroup.backgroundColor
                    groupBackgroundOpacity = selectedGroup.backgroundOpacity
                    showGroupAppearance = true
                })
            } else if (selectedGroups.isNotEmpty()) {
                add(SheetActionSpec(Icons.Rounded.Close, "解组 ${selectedGroups.size} 个", false) {
                    onAction(PockRefAction.UngroupSelected)
                })
            } else if (canCreateGroup) {
                add(SheetActionSpec(Icons.Rounded.Add, "成组", false) { onAction(PockRefAction.CreateGroup) })
            }
        }
    }
    val exportAction = SheetActionSpec(Icons.Rounded.Image, "导出", false) { onAction(PockRefAction.ExportSelected) }
    val fullActions = groupActions + when {
        state.requiresGroupFlattening -> listOf(exportAction)
        isLockedPanel -> quickActions + exportAction
        else -> row1 + row2
    }
    val hasGifControls = selectedGifPlaybackImageId != null &&
        selectedGifPlaybackSource != null && selectedGifPlaybackState != null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Center tablet controls with equal clearance for both corner panels.
        val cornerClearance = Dimens.CornerInset + Dimens.BottomPanelChromeSize + 8.dp
        val centerToolbar = maxWidth >= 600.dp
        val startInset = if (centerToolbar) cornerClearance else Dimens.CornerInset
        val availableToolbarWidth = (maxWidth - startInset - cornerClearance).coerceAtLeast(0.dp)
        val posterizeWidth = 240.dp * LocalDensity.current.fontScale
        Column(
            modifier = Modifier
                .align(if (centerToolbar) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(
                    start = startInset,
                    end = cornerClearance,
                    top = Dimens.EdgeInset,
                    bottom = Dimens.CornerInset,
                )
                .widthIn(max = availableToolbarWidth)
                .onSizeChanged { size ->
                    onChromeHeightChanged(with(density) { size.height.toDp() })
                },
            horizontalAlignment = if (centerToolbar) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val gifImageId = selectedGifPlaybackImageId
            if (hasGifControls && gifImageId != null &&
                selectedGifPlaybackSource != null && selectedGifPlaybackState != null
            ) {
                GifFrameControlBar(
                    source = selectedGifPlaybackSource,
                    playback = selectedGifPlaybackState,
                    onPlaybackChange = { next -> gifPlaybackStates[gifImageId] = next },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (posterizeControlsVisible) {
                PosterizeLevelButtons(
                    modifier = Modifier
                        .width(posterizeWidth)
                        .testTag("selection-posterize-levels"),
                    selectedLevels = state.posterizeLevels,
                    onLevelClick = { levels ->
                        onAction(PockRefAction.TogglePosterizeLevel(levels))
                    },
                )
            }
            SheetModeToggle(
                singleSelected = !state.multiSelectMode,
                boxSelectMode = state.boxSelectMode,
                onSingle = {
                    if (state.boxSelectMode) onAction(PockRefAction.ToggleBoxSelectMode)
                    if (state.multiSelectMode) onAction(PockRefAction.ToggleMultiSelectMode)
                },
                onMulti = {
                    if (state.boxSelectMode) onAction(PockRefAction.ToggleBoxSelectMode)
                    if (!state.multiSelectMode) onAction(PockRefAction.ToggleMultiSelectMode)
                },
                onBox = { onAction(PockRefAction.ToggleBoxSelectMode) },
            )
            Column(
                modifier = Modifier
                    .align(if (centerToolbar) Alignment.CenterHorizontally else Alignment.Start)
                    .testTag("selection-action-toolbar")
                    .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
                    .glassMaterial(RoundedCornerShape(Dimens.ChromeCornerRadius))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(Dimens.ChromeCornerRadius),
                    ),
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .height(Dimens.BottomPanelChromeSize)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        fullActions.forEach { spec ->
                            SheetActionButton(
                                icon = spec.icon,
                                label = spec.label,
                                active = spec.active,
                                tint = spec.tint,
                                showLabel = false,
                                modifier = Modifier.size(Dimens.MinTouchTarget),
                                onClick = spec.onClick,
                                onLongClick = spec.onLongClick,
                            )
                        }

                    }
                }
            }
        }
    }
    if (showRenameGroup && groupActionTarget != null) {
        GlassAlertDialog(
            onDismissRequest = { showRenameGroup = false },
            title = { Text("重命名组") },
            text = {
                OutlinedTextField(
                    value = groupNameDraft,
                    onValueChange = { groupNameDraft = it },
                    singleLine = true,
                    label = { Text("组名") },
                )
            },
            confirmButton = {
                Button(onClick = {
                    onAction(PockRefAction.RenameSelectedGroup(groupNameDraft))
                    showRenameGroup = false
                }) { Text("保存") }
            },
            dismissButton = {
                Button(onClick = { showRenameGroup = false }) { Text("取消") }
            },
        )
    }
    if (showGroupAppearance && groupActionTarget != null) {
        val palette = listOf(Color.Black, Color.Red, Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFFFC107))
        GlassAlertDialog(
            onDismissRequest = { showGroupAppearance = false },
            title = { Text("组外观") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        palette.forEach { color ->
                            FilterChip(
                                selected = groupBackgroundColor == color,
                                onClick = { groupBackgroundColor = color },
                                label = { Text("色") },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.size(16.dp),
                                        shape = RoundedCornerShape(50),
                                        color = color,
                                    ) { }
                                },
                            )
                        }
                    }
                    Text("背景透明度 ${(groupBackgroundOpacity * 100).roundToInt()}%")
                    Slider(
                        value = groupBackgroundOpacity,
                        onValueChange = { groupBackgroundOpacity = it },
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAction(PockRefAction.SetSelectedGroupAppearance(groupBackgroundColor, groupBackgroundOpacity))
                    showGroupAppearance = false
                }) { Text("保存") }
            },
            dismissButton = {
                Button(onClick = { showGroupAppearance = false }) { Text("取消") }
            },
        )
    }
}


@Composable
private fun GifFrameControlBar(
    source: GifPlaybackSource,
    playback: GifPlaybackState,
    onPlaybackChange: (GifPlaybackState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeline = source.timeline
    val frameIndex = playback.frameIndex.coerceIn(0, timeline.lastFrameIndex)
    val frameCount = timeline.frameCount.coerceAtLeast(1)
    val previousFrame = if (frameIndex == 0) timeline.lastFrameIndex else frameIndex - 1
    val nextFrame = if (frameIndex >= timeline.lastFrameIndex) 0 else frameIndex + 1
    val sliderSteps = if (frameCount in 3..160) frameCount - 2 else 0

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.ChipCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "GIF",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = "第 ${frameIndex + 1}/$frameCount 帧",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onPlaybackChange(
                            playback.copy(
                                frameIndex = previousFrame,
                                isPlaying = false,
                            ),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一帧",
                    )
                }
                IconButton(
                    onClick = {
                        onPlaybackChange(
                            playback.copy(
                                frameIndex = frameIndex,
                                isPlaying = !playback.isPlaying,
                            ),
                        )
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playback.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(
                    onClick = {
                        onPlaybackChange(
                            playback.copy(
                                frameIndex = nextFrame,
                                isPlaying = false,
                            ),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一帧",
                    )
                }
            }
            Slider(
                value = frameIndex.toFloat(),
                onValueChange = { value ->
                    onPlaybackChange(
                        playback.copy(
                            frameIndex = value.roundToInt().coerceIn(0, timeline.lastFrameIndex),
                            isPlaying = false,
                        ),
                    )
                },
                valueRange = 0f..timeline.lastFrameIndex.toFloat().coerceAtLeast(1f),
                steps = sliderSteps,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "GIF 帧进度"
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = frameIndex.toFloat(),
                            range = 0f..timeline.lastFrameIndex.toFloat().coerceAtLeast(1f),
                            steps = sliderSteps,
                        )
                    },
            )
        }
    }

}


/**
 * V_NOW (A+按钮重分类): Helper data class so the two
 * action tiers (4 primary / 6 secondary) can be passed
 * to inline `forEach` blocks inside [SelectionBottomSheet]
 * without bloating the parent Composable with 10
 * separate lambdas. The old `SheetActionGrid` helper
 * (12 actions chunked into 3 rows of 4) is no longer
 * used since the 4+6 layout is rendered as two direct
 * `Row`s in the caller.
 */
private data class SheetActionSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val active: Boolean,
    val tint: Color? = null,
    // V_NOW (S-04): declared BEFORE [onClick] so the
    // trailing-lambda call sites
    // (`SheetActionSpec(icon, label, active) { ... }`)
    // resolve to [onClick] (the last function-type
    // parameter in the constructor). If [onLongClick]
    // were declared AFTER [onClick], every existing
    // call site would need to be rewritten to
    // `SheetActionSpec(..., onClick = ...) { ... }`
    // or similar — a 14-site mechanical churn that
    // adds no value. Today only the multi-select
    // "水平翻转" button passes `onLongClick = {...}`;
    // the other 9 buttons default to null.
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color? = null,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    // V_NOW (S-04): optional long-press handler. When
    // non-null, the button switches from `Modifier.clickable`
    // to `Modifier.combinedClickable` so the long-press
    // gesture is honoured alongside the regular tap.
    // See [SheetActionSpec.onLongClick] for the call site.
    onLongClick: (() -> Unit)? = null,
) {
    val resolvedTint = tint ?: if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            // V_NOW (S-03 / a11y heuristic "User control
            // and freedom"): enforce a 48dp minimum touch
            // target on the OUTER height of the button.
            // `defaultMinSize` (vs `heightIn(min =)`) lets
            // Compose honour the incoming `modifier` first
            // and only bumps the height when it's below
            // 48dp. The caller passes `Modifier.weight(1f)`
            // from the parent's Row, and we don't want to
            // fight that.
            //
            // The KDoc previously claimed "48dp minimum
            // touch target is enforced by
            // `minimumInteractiveComponentSize` in the
            // parent Box's modifier chain" — but no caller
            // actually wraps the Column in that helper.
            // Without it, the button's outer height lands
            // at ~40dp (4dp padding + 18dp icon + 2dp gap
            // + 11sp label + 4dp padding ≈ 39dp) which is
            // BELOW WCAG 2.5.5 / Apple HIG's 44pt minimum
            // and well below M3's 48dp recommendation.
            // Fingers wider than 16dp hit two buttons at
            // once; system font scale 1.3x / 1.5x pushes
            // the label to a second line and the button
            // grows asymmetrically.
            .defaultMinSize(minHeight = 48.dp)
            // P2 (2026-06-11 chrome unify): bumped from
            // 10dp outlier to [Dimens.ChromeCornerRadius]
            // (12dp) so the selection-toolbar item reads
            // as the same visual family as the chrome
            // surfaces (toolbar / minimap / FAB / lock).
            .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
            // V_NOW (S-04): combinedClickable when
            // onLongClick is provided, plain clickable
            // otherwise. combinedClickable is the
            // canonical M3 path for click + long-press;
            // using it keeps the implementation trivial.
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent,
            )
            // V_NOW (A+按钮重分类 — compact 40dp): was
            // 12dp vertical / 4dp horizontal (old 64dp
            // button). Now 4dp vertical / 2dp horizontal
            // so the total content height lands at ~40dp
            // (4+18+2+ 11sp label ≈ 35dp + 5dp slack).
            // Matches M3 `IconButtonDefaults.smallIconButtonSize`
            // (40dp) which is the toolbar icon button
            // standard. The 48dp minimum touch target is
            // enforced by `defaultMinSize(minHeight = 48.dp)`
            // ABOVE — the padding here is the CONTENT
            // padding inside that 48dp minimum, so the
            // outer clickable region is always ≥48dp tall.
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (showLabel) Arrangement.spacedBy(2.dp) else Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = resolvedTint,
            modifier = Modifier.size(18.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
 private fun SheetModeToggle(
    singleSelected: Boolean,
    boxSelectMode: Boolean,
    onSingle: () -> Unit,
    onMulti: () -> Unit,
    onBox: () -> Unit,
) {
    CompactSegmentedControl(
        options = listOf("单选", "多选", "框选"),
        modifier = Modifier.testTag("selection-mode-toggle"),
        selectedIndex = when {
            boxSelectMode -> 2
            singleSelected -> 0
            else -> 1
        },
        onOptionClick = { index ->
            when (index) {
                0 -> if (!singleSelected || boxSelectMode) onSingle()
                1 -> if (singleSelected || boxSelectMode) onMulti()
                2 -> if (!boxSelectMode) onBox()
            }
        },
    )
}


@Composable
private fun BoxScope.SelectionModeOverlay(
 state: PockRefUiState,
 modifier: Modifier = Modifier,
) {
 // V_NOW (S-04): render a chip in ALL three selection modes
 // (SINGLE / MULTI / BOX). SINGLE uses a muted surfaceVariant
 // background so it reads as "default state confirmation"
 // rather than the loud primary tint MULTI/BOX get. The
 //4-edge canvas border is MULTI/BOX-only (SINGLE is the
 // default, no border cue needed).
 val isMulti = state.multiSelectMode && !state.boxSelectMode
 val isBox = state.boxSelectMode
 val isSingle = !isMulti && !isBox
 val isBoxWindow = isBox &&
 state.boxSelectKind == com.pockref.app.model.BoxSelectionKind.WINDOW
 val isBoxCrossing = isBox &&
 state.boxSelectKind == com.pockref.app.model.BoxSelectionKind.CROSSING
  val isDragging = state.boxSelectRect != null
  // Chip visual: pick background / foreground / icon / label
  // based on current mode + kind + drag state. Direction arrow
 // is the visual cue for WINDOW vs CROSSING (AutoCAD /
 // Visio / Figma convention).
 //
 // Color scheme:3 modes use3 DISTINCT M3 roles so the chip
 // reads as a different colour per mode:
  // - All 4 modes (SINGLE / MULTI / BOX WINDOW / BOX CROSSING)
  // use the same pale-blue secondaryContainer chip with
  // onSecondaryContainer text. The user explicitly asked for
  // a UNIFORM chip style across modes (2026-06-08): the
  // visual difference between modes is communicated by the
  // LABEL TEXT (e.g. "单选" vs "多选" vs "框选 · 左→右") and
  // the DIRECTION ICON (←/→ for BOX, none for SINGLE/MULTI),
  // not by background color.
   val backgroundColor: Color
   val contentColor: Color
   val directionIcon: androidx.compose.ui.graphics.vector.ImageVector?
   val label: String
   when {
    isBoxCrossing -> {
     backgroundColor = MaterialTheme.colorScheme.secondaryContainer
     contentColor = MaterialTheme.colorScheme.onSecondaryContainer
     directionIcon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft
     label = if (isDragging) {
      val n = state.boxSelectPreviewIds.size
      "框选 · 右→左 (相交) · 圈了 $n 张"
     } else {
      "框选 · 右→左拖动 (相交)"
     }
    }
    isBoxWindow -> {
     backgroundColor = MaterialTheme.colorScheme.secondaryContainer
     contentColor = MaterialTheme.colorScheme.onSecondaryContainer
     directionIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight
     label = if (isDragging) {
      val n = state.boxSelectPreviewIds.size
      "框选 · 左→右 (全包含) · 圈了 $n 张"
     } else {
      "框选 · 左→右拖动 (全包含)"
     }
    }
    isMulti -> {
     backgroundColor = MaterialTheme.colorScheme.secondaryContainer
     contentColor = MaterialTheme.colorScheme.onSecondaryContainer
     directionIcon = null
     label = "多选 · 点击图片累加选中"
    }
    else -> {
     backgroundColor = MaterialTheme.colorScheme.secondaryContainer
     contentColor = MaterialTheme.colorScheme.onSecondaryContainer
     directionIcon = null
     label = "单选 · 点击图片切换选中"
    }
   }
 // Top-centre status chip. The caller passes [modifier] with
 // the top padding that centres the chip on the same line
 // as the top toolbar + global lock toggle (see call site
 // KDoc). Sits below the top toolbar's z-order.
 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(6.dp),
 modifier = modifier
 .align(Alignment.TopCenter)
  .clip(RoundedCornerShape(999.dp))
  .background(backgroundColor)
  .padding(horizontal =14.dp, vertical =6.dp),
 ) {
 if (directionIcon != null) {
 Icon(
 imageVector = directionIcon,
 contentDescription = null,
 tint = contentColor,
 modifier = Modifier.size(16.dp),
 )
 }
 Text(
 text = label,
 color = contentColor,
 style = MaterialTheme.typography.labelMedium,
 )
 }
}


@Composable
private fun BoxScope.CropOverlay(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    bitmapCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Int, ImageBitmap>>,
    // V_NOW: callback that reads the raw image bytes for a
    // disk-backed [SceneImage] (the [imageByteOffset] /
    // [imageByteLength] path). Returns null if the bytes
    // cannot be resolved for any reason. The previous
    // design only read [SceneImage.imageBytes] (which is
    // populated for newly-imported images but EMPTY for
    // images re-loaded from a saved `.pur` scene — those
    // go through the [SceneImageDecoder] / [PurFileParser]
    // path, leaving [imageBytes] = empty but
    // [imageByteOffset] / [imageByteLength] valid). The
    // caller (CanvasPanel) wires this to the same
    // resolver used by [commitCrop] in the ViewModel.
    imageBytesResolver: (imageByteOffset: Int, imageByteLength: Int) -> ByteArray?,
) {
    val cropImage = state.images.find { it.id == state.cropImageId } ?: return
    val viewport = state.canvasViewportSize

    // V_NOW: the floating crop layer used to render only a
    // neutral surface color (a gray rectangle) with the
    // selection chrome on top. The user reported "选定图片后
    // 使用裁切工具时，显示的界面是这样的" with a screenshot
    // showing a huge gray box that didn't visually correspond
    // to the actual selected image on the main canvas — the
    // gray box is the [MaterialTheme.colorScheme.surface]
    // background, with no bitmap ever drawn inside it. The
    // CropRect math was correct, but the user had no way to
    // tell what they were cropping. The fix: look up the
    // cached decoded bitmap for [cropImage.id] (same cache
    // the main canvas uses) and fall back to a one-shot
    // decode of [cropImage.imageBytes] if the cache entry
    // hasn't arrived yet (e.g. user just imported the image
    // and the ImageDecodePipeline hasn't streamed the
    // thumbnail). The bitmap is then drawn into the chrome
    // canvas as the first paint operation, so the selection
    // overlay, dim, border, grid, handles, and crop rect all
    // align on top of the actual image.
    val cached = bitmapCache[cropImage.id]?.second
    val fallback by produceState<ImageBitmap?>(
        initialValue = cached,
        key1 = cropImage.id,
    ) {
        if (value == null) {
            // V_NOW: prefer the resolver callback (handles
            // disk-backed .pur images) and only fall back
            // to inline [cropImage.imageBytes] if the
            // resolver returns null AND the image has
            // inline bytes. The previous code only checked
            // [cropImage.imageBytes.isNotEmpty()] which is
            // FALSE for disk-backed images — that's the
            // "image not rendered" bug the user reported.
            val resolved: ByteArray? = when {
                cropImage.imageByteOffset >= 0 && cropImage.imageByteLength > 0 ->
                    imageBytesResolver(cropImage.imageByteOffset, cropImage.imageByteLength)
                cropImage.imageBytes.isNotEmpty() -> cropImage.imageBytes
                else -> null
            }
            if (resolved != null && resolved.isNotEmpty()) {
                value = withContext(Dispatchers.IO) {
                    // V_NOW (2026-06-16 user feedback "为什么我的应用
                    // 会闪退"): the previous code did a full-resolution
                    // decode here, which for a disk-backed .pur image
                    // of e.g. 9317×9333 produced a 347MB ARGB_8888
                    // bitmap that the Compose `Image` couldn't draw to
                    // the [Canvas] (throwIfCannotDraw fires past
                    // ~200MB on most devices, including SM-X810's
                    // Adreno 730). The canvas-render path doesn't
                    // hit this because it goes through the
                    // ImageDecodePipeline LOD ladder; only the
                    // crop-fallback path was unguarded.
                    //
                    // [decodeSampledBitmap] mirrors the SceneImageDecoder
                    // downsample pattern: inJustDecodeBounds to get
                    // dimensions, power-of-two inSampleSize, precise
                    // createScaledBitmap to [CROP_FALLBACK_MAX_LONG_EDGE].
                    // 2048 matches the second-largest rung of the
                    // canvas LOD ladder (4096 is ultra-zoom; the
                    // crop UI is a floating layer at most ~half the
                    // screen, so 2048 is more than enough for the
                    // user to inspect the crop region).
                    decodeSampledBitmap(
                        resolved,
                        CROP_FALLBACK_MAX_LONG_EDGE,
                    )?.asImageBitmap()
                }
            }
        }
    }
    val displayBitmap = cached ?: fallback

    val density = LocalDensity.current
    val touchPadding = with(density) { 28.dp.toPx() }
    val handleRadius = with(density) { 10.dp.toPx() }

    // V_NOW (floating-layer crop UI per user spec
    // "裁切图片应以悬浮层形式显示"): the cropping picture
    // is no longer rendered as a fullscreen overlay that
    // follows the image's position on the main canvas. It's
    // now a dedicated floating layer anchored in the upper
    // half of the safe area (status bar to toolbar), with
    // the bottom toolbar (取消 / 应用 + aspect chips) pinned
    // to the safe-area bottom.
    //
    // V_NOW (user feedback "裁剪打开就是这个页面" + the
    // previous screenshot showed the bottom toolbar COMPLETELY
    // missing): the previous layout had the floating layer
    // and the toolbar as TWO SIBLINGS in a BoxScope:
    //   - Floating Box: Modifier.align(Alignment.Center)
    //   - Toolbar Column: Modifier.align(Alignment.BottomCenter)
    // BoxScope.Center is the CENTER of the parent Box
    // (= the WHOLE WINDOW, status bar + nav bar included
    // because [PockRefApp]'s outer Scaffold has
    // `contentWindowInsets = WindowInsets(0)`). The toolbar
    // pinned to the BOTTOM of the same Box. With maxLayerH
    // computed as `viewport.h - 200dp - 32dp` and
    // `viewport.h` = window full height (incl. ~80dp of
    // system bars on SM-X810), the floating layer's bottom
    // edge landed at ~window.h / 2 + 200dp = 600dp-ish, and
    // the toolbar's top edge sat at ~window.h - 130dp =
    // 700dp-ish. Result: the floating layer's bottom + its
    // 16dp shadow OVERLAPPED the toolbar's top by ~70dp
    // (covering the entire chips row + half the button
    // row) and the user saw "no toolbar at all" in the
    // screenshot.
    //
    // New layout: the entire CropOverlay is wrapped in a
    // single Column inside this BoxScope. The Column is
    // split into TWO rows:
    //   - Row 1: [Box(weight(1f))] — upper area, takes all
    //     remaining space ABOVE the toolbar, the floating
    //     layer is centered in this row with
    //     [contentAlignment = Alignment.Center]
    //   - Row 2: [Column] — bottom toolbar, fixed height
    //     ~130dp
    // The Column itself is given
    // `windowInsetsPadding(WindowInsets.systemBars)` so its
    // layout region is the SAFE AREA (status bar above,
    // nav bar below) — the floating layer can never
    // overlap the status bar / nav bar, and the toolbar
    // sits flush against the nav bar with its own nav-bar
    // padding (which becomes a no-op once the outer
    // padding has already applied it). The
    // [Modifier.windowInsetsPadding(WindowInsets.navigationBars)]
    // on the inner toolbar Column is kept as a belt-and-
    // suspenders in case a future caller drops the outer
    // safe-area padding.
    //
    // `maxLayerH` is now derived from the SAFE height
    // (window minus status bar minus nav bar) minus the
    // toolbar reserved minus safety margins. Combined with
    // the weight-row layout, the floating layer can
    // NEVER overlap the toolbar — they're in disjoint
    // parent rows.
    val safetyMarginPx = with(density) { 16.dp.toPx() }
    val sideMarginPx = with(density) { 16.dp.toPx() }
    // V_NOW: bumped from 200dp → 280dp (user feedback "完全
    // 无法使用" + screenshot showed the floating layer
    // dominating the screen, the toolbar compressed to a
    // single cramped row of chips + buttons, and the 8
    // crop handles stretching almost to the screen
    // edges). The previous 200dp reservation only
    // covered the [chip row ≈ 48dp] + [button row ≈
    // 64dp] = 112dp of toolbar chrome, leaving a mere
    // 88dp of "padding" for the visible gap between
    // floating-layer bottom and toolbar top. On a 1752px-
    // tall landscape device that gap shrunk to 0-20dp on
    // the user's screenshot, with the floating layer's
    // own 16dp shadow + 16dp safety margin further
    // crushing the toolbar's visible space. 280dp adds
    // 80dp of buffer so the floating layer has clear
    // breathing room above the toolbar regardless of
    // font-scale settings that might make the chip row
    // wrap to a taller multi-line layout.
    val toolbarReserved = 280.dp
    val minLayerSide = 120.dp
    val sideMargin = 16.dp
    val safetyMargin = 16.dp
    // V_NOW (user feedback "图片显示不全"): ROOT CAUSE
    // FIX — the previous code computed [layerW] / [layerH]
    // in PIXELS (mixing `viewport.width` from
    // [onSizeChanged] which reports px, with
    // `with(density) { X.dp.toPx() }` margins), then
    // passed those px values back into [Modifier.size] as
    // `with(density) { layerW.toDp() }`. The chain
    // [Float (px) → Float.toDp() → Dp.value = px →
    // Dp.toPx(density) = px * density] doubled the
    // effective size at @density 2.0. The Box the user
    // saw was 2× the intended layer size, so the
    // [Canvas] inside reported [size] that was also 2×
    // too large — its [fitScale = maxOf(...)] overshot,
    // the image's height projected to ~ 2× the canvas
    // height, and the top + bottom of the image were
    // clipped outside the canvas bounds, producing the
    // "图片显示不全" the user reported.
    //
    // Fix: drive the ENTIRE layer size chain in
    // [Dp] from the start — convert the [IntSize] from
    // [onSizeChanged] to dp ONCE, then size in dp, then
    // [Modifier.size] receives plain dp values (no more
    // `toDp()` round-trip). The math below is unitless
    // ratio math, but the inputs and outputs are dp.
    // The crop mode can be entered in the same frame in which the canvas
    // reports its size. Do not make the whole editor disappear just because
    // the state mirror is still 0x0; the composable itself already has a
    // valid window size to use as a fallback.
    val viewportWidthPx = viewport.width.takeIf { it > 0f }
        ?: with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val viewportHeightPx = viewport.height.takeIf { it > 0f }
        ?: with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val viewportDpW: Dp = with(density) { viewportWidthPx.toDp() }
    val viewportDpH: Dp = with(density) { viewportHeightPx.toDp() }
    val statusBarPx: Int = WindowInsets.statusBars.getTop(density)
    val navBarPx: Int = WindowInsets.navigationBars.getBottom(density)
    val statusBarDp: Dp = with(density) { statusBarPx.toFloat().toDp() }
    val navBarDp: Dp = with(density) { navBarPx.toFloat().toDp() }
    val safeHeight = (viewportDpH - statusBarDp - navBarDp).coerceAtLeast(minLayerSide)
    val maxLayerW = (viewportDpW - sideMargin * 2f).coerceAtLeast(minLayerSide)
    val maxLayerH = (safeHeight - toolbarReserved - safetyMargin * 2f).coerceAtLeast(minLayerSide)
    // V_NOW (user feedback "使用裁切工具裁切旋转过后的图片，
    // 裁切图片显示不全"): the previous code used the
    // image's INTRINSIC aspect (no rotation) to size the
    // layer. When the image is rotated on the canvas
    // (e.g. `cropImage.rotationDegrees` ≠ 0), the
    // canvas's visual AABB is taller OR wider than the
    // unrotated intrinsic — the layer was sized for the
    // unrotated image, but the [Canvas] below renders the
    // image ROTATED + cover-fit into that smaller layer.
    // Result: the rotated image's four corners poked
    // outside the layer, the layer's rounded-corner clip
    // cropped them, and the user saw a tilted image with
    // big empty "layer-coloured" wedges in the middle.
    //
    // Fix: size the layer to the image's ROTATED
    // AABB aspect. For an angle θ around the image
    // centre, the AABB is
    //   aabbW = |bmpW·cos θ| + |bmpH·sin θ|
    //   aabbH = |bmpW·sin θ| + |bmpH·cos θ|
    //   aabbAspect = aabbW / aabbH
    // The cover-fit's [fitScale = layerH / bmpH] is
    // unchanged (verified algebraically — see the
    // drawImage site comment), and the rotated AABB
    // lines up exactly with the layer rectangle, so
    // every pixel of the rotated image is visible.
    // Flips ([cropImage.flipX/Y]) don't change the
    // AABB, so we don't fold them into the aspect.
    val bmpW = cropImage.intrinsicWidth.toFloat()
    val bmpH = cropImage.intrinsicHeight.toFloat()
    // V_NOW (user feedback "裁切旋转过后的图片，裁切图片
    // 显示不全"): the rotation we render with in this
    // overlay is [state.cropRotation] — the CROP TOOL'S
    // INTERNAL rotation, NOT [cropImage.rotationDegrees].
    //
    // [cropImage.rotationDegrees] is the image's pose on
    // the main canvas (set by the user's pinch-rotate
    // gesture). [state.cropRotation] is a SEPARATE state
    // field that the crop tool owns: it starts at 0f
    // when [EnterCropMode] fires (PockRefViewModelScene
    // line 695: `cropRotation = 0f` in the entry reducer)
    // and is incremented by the user tapping the rotate
    // buttons INSIDE the crop overlay.
    //
    // The bug the user just hit: the previous code read
    // `cropImage.rotationDegrees` (always non-zero when
    // the image was rotated on the canvas) and sized
    // the layer to that AABB aspect, but then [drawImage]
    // ALSO rotated by `cropImage.rotationDegrees` — so
    // the layer was sized for a tilted AABB AND the
    // image was rendered tilted to match, producing the
    // "image covers the layer but the layer is too tall
    // to fit" mis-alignment the user reported.
    //
    // The crop overlay is meant to show the image as if
    // it were freshly loaded (rotation = 0 by default,
    // the user can rotate the crop overlay independently
    // if they want to crop a different orientation). The
    // canvas's existing rotation is preserved in
    // [cropImage.rotationDegrees] on the resulting
    // [SceneImage] — the commitCrop reducer adds the
    // tool's [cropRotation] to the canvas's
    // [rotationDegrees] so the user's on-canvas rotation
    // is never lost. So here in the overlay we only
    // care about the crop tool's internal rotation.
    val cropRotation = state.cropRotation
    val imgAspect: Float = if (cropRotation == 0f) {
        bmpW / bmpH
    } else {
        val rad = Math.toRadians(cropRotation.toDouble())
        val cosA = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
        val sinA = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
        val aabbW = bmpW * cosA + bmpH * sinA
        val aabbH = bmpW * sinA + bmpH * cosA
        aabbW / aabbH
    }
    // V_NOW (user feedback "图片显示不全"): the previous
    // formula used `coerceAtLeast(minLayerSide)` on the
    // *secondary* axis alone. When the image's aspect
    // was extreme (very tall OR very wide), the
    // floor on the secondary axis broke the panel's
    // aspect ratio — the panel no longer matched the
    // image's aspect, so the image's [fitScale] = min(...)
    // projection letterboxed inside the panel, leaving
    // blank "layer-coloured" bars around the picture.
    //
    // New formula: the panel's aspect ratio is STRICTLY
    // pinned to [imgAspect]. When the secondary axis
    // hits the [minLayerSide] floor, we RECOMPUTE
    // the binding axis to keep the ratio (the panel
    // may then slightly exceed [maxLayerH] / [maxLayerW]
    // — accepted because dropping below the floor would
    // make the panel unusably small for tap targets).
    val layerW: Dp
    val layerH: Dp
    if (maxLayerW / maxLayerH > imgAspect) {
        // Available rect wider than image — height is
        // the binding constraint. Width is derived.
        var w = maxLayerH * imgAspect
        if (w < minLayerSide) {
            // Floor forces W up — recompute H to keep aspect.
            w = minLayerSide
            val h = w / imgAspect
            layerW = w
            layerH = h.coerceAtMost(maxLayerH).coerceAtLeast(minLayerSide)
        } else {
            layerW = w
            layerH = maxLayerH
        }
    } else {
        // Available rect taller than image — width is the
        // binding constraint. Height is derived.
        var h = maxLayerW / imgAspect
        if (h < minLayerSide) {
            h = minLayerSide
            val w = h * imgAspect
            layerH = h
            layerW = w.coerceAtMost(maxLayerW).coerceAtLeast(minLayerSide)
        } else {
            layerW = maxLayerW
            layerH = h
        }
    }
    val currentLayerW by rememberUpdatedState(layerW)
    val currentLayerH by rememberUpdatedState(layerH)

    val currentCropImage by rememberUpdatedState(cropImage)
    val currentCropRect by rememberUpdatedState(state.cropRect)
    val currentCropAspect by rememberUpdatedState(state.cropAspect)
    val currentOnAction by rememberUpdatedState(onAction)

    var dragEdge by remember { mutableStateOf<String?>(null) }

    // V_NOW: [calcScreenCoords] now operates in FLOATING
    // LAYER coordinates instead of VIEWPORT coordinates.
    // The [CropRect] is still a 0..1 normalized selection
    // over the image, but the projection target changed
    // from "the image's position on the main canvas" to
    // "the floating layer's top-left = (0, 0)". The
    // returned tuple stays the same shape ([l, t, r, b,
    // layerW, layerH]) so the existing drag-handler math
    // (dx = dragAmount.x / imgW etc.) still works without
    // per-branch changes. The previous code read
    // [state.canvasScale] / [state.canvasOffset] / the
    // image's display size / position to project onto the
    // main canvas — those are intentionally discarded
    // because the floating layer is independent of the
    // canvas transform (the user can pan/zoom the main
    // canvas without moving the crop layer).
    fun calcScreenCoords(): FloatArray {
        val cr = currentCropRect
        // Convert Dp to px — pointerInput coordinates are in
        // pixels, so the crop rect must also be in pixels for
        // hit-testing and drag math to work correctly.
        val lw: Float = with(density) { currentLayerW.toPx() }
        val lh: Float = with(density) { currentLayerH.toPx() }
        return floatArrayOf(
            cr.left * lw,
            cr.top * lh,
            cr.right * lw,
            cr.bottom * lh,
            lw,
            lh,
        )
    }

    fun normAspect(aspect: CropAspect, img: com.pockref.app.model.SceneImage): Float {
        return aspect.ratioW / aspect.ratioH * (img.intrinsicHeight.toFloat() / img.intrinsicWidth.toFloat())
    }

    // 1. Outer Column — wraps the entire CropOverlay in
    //    the safe area (status bar above, nav bar below).
    //    Split into [weight(1f) upper area for the
    //    floating layer] + [fixed-height bottom toolbar].
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(state.backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // 1a. Upper area — `weight(1f)` consumes all
        //     remaining space above the toolbar. The
        //     floating Box is centered in this row via
        //     `contentAlignment = Alignment.Center` so the
        //     layer is visually centered in the upper
        //     half, with no risk of overlap with the
        //     toolbar below (different parent rows).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                // V_NOW: the pointerInput is on the OUTER Box
                // (the full upper area), NOT on the floating
                // layer Box. The previous code put
                // pointerInput on the floating layer Box
                // (size = layerW x layerH), which meant
                // touch events at the Box's right/bottom
                // edges (where the crop handles sit when
                // CropRect = (0,0,1,1)) were OUTSIDE the
                // Compose hit-testing range [0, width) x
                // [0, height) — the right and bottom edges
                // are exclusive. The user could SEE the
                // handles (drawn outside the Box via
                // graphicsLayer(clip=false)) but could NOT
                // TOUCH them. This is why the bottom two
                // corners were basically impossible to drag
                // (y = layerH is outside the Box) and the
                // top two were unreliable (x = layerW is
                // outside the Box).
                //
                // Moving pointerInput to the outer Box
                // (which fills the entire upper area) means
                // touch events are received for the full
                // area, including the space around the
                // floating layer where the handle circles
                // extend. We convert touch coordinates from
                // the outer Box's coordinate system to the
                // floating layer's coordinate system using
                // the centering offset:
                //   layerLocalX = touchX - offsetX
                //   layerLocalY = touchY - offsetY
                // where offsetX = (outerW - layerWpx) / 2
                //       offsetY = (outerH - layerHpx) / 2
                //
                // This mirrors how the SINGLE-mode corner
                // handle works: its pointerInput is on the
                // full-screen Canvas, so there are no
                // boundary issues.
                awaitEachGesture {
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
                    // Compute the floating layer's offset
                    // within this outer Box. The layer is
                    // centered (contentAlignment = Center), so
                    // offset = (outerSize - layerSize) / 2.
                    val outerW = size.width.toFloat()
                    val outerH = size.height.toFloat()
                    val lwPx = with(density) { currentLayerW.toPx() }
                    val lhPx = with(density) { currentLayerH.toPx() }
                    val offX = (outerW - lwPx) / 2f
                    val offY = (outerH - lhPx) / 2f
                    // Convert touch position to floating
                    // layer coordinates.
                    val startLayer = Offset(
                        down.position.x - offX,
                        down.position.y - offY,
                    )
                    val cStart = calcScreenCoords()
                    val cL0 = cStart[0]; val cT0 = cStart[1]
                    val cR0 = cStart[2]; val cB0 = cStart[3]
                    val imgW = cStart[4]; val imgH = cStart[5]
                    val tp = touchPadding
                    val cornerTp = touchPadding * 1.5f
                    val inCornerLT = startLayer.x in (cL0 - cornerTp)..(cL0 + cornerTp) &&
                            startLayer.y in (cT0 - cornerTp)..(cT0 + cornerTp)
                    val inCornerRT = startLayer.x in (cR0 - cornerTp)..(cR0 + cornerTp) &&
                            startLayer.y in (cT0 - cornerTp)..(cT0 + cornerTp)
                    val inCornerLB = startLayer.x in (cL0 - cornerTp)..(cL0 + cornerTp) &&
                            startLayer.y in (cB0 - cornerTp)..(cB0 + cornerTp)
                    val inCornerRB = startLayer.x in (cR0 - cornerTp)..(cR0 + cornerTp) &&
                            startLayer.y in (cB0 - cornerTp)..(cB0 + cornerTp)
                    val inside = startLayer.x > cL0 + tp && startLayer.x < cR0 - tp &&
                            startLayer.y > cT0 + tp && startLayer.y < cB0 - tp
                    val nearBox = startLayer.x > cL0 - tp && startLayer.x < cR0 + tp &&
                            startLayer.y > cT0 - tp && startLayer.y < cB0 + tp
                    val isFree = currentCropAspect == CropAspect.FREE
                    dragEdge = when {
                        inCornerLT -> if (isFree) "lt" else "lt_lock"
                        inCornerRT -> if (isFree) "rt" else "rt_lock"
                        inCornerLB -> if (isFree) "lb" else "lb_lock"
                        inCornerRB -> if (isFree) "rb" else "rb_lock"
                        inside -> "move"
                        nearBox -> "move"
                        else -> null
                    }
                    val edge = dragEdge ?: return@awaitEachGesture
                    // V_NOW: use ABSOLUTE positioning instead of
                    // incremental deltas. The previous code read
                    // [currentCropRect] on every move frame and
                    // added the per-frame dx/dy to it. But
                    // [currentCropRect] is a [rememberUpdatedState]
                    // wrapper that only reflects the ViewModel's
                    // new value after the NEXT recomposition — so
                    // if two move events arrive in the same frame
                    // (common on high-refresh-rate devices), the
                    // second event reads the STALE cropRect and
                    // the first event's delta is lost. The user
                    // perceives this as "not following the finger".
                    //
                    // The SINGLE-mode corner handle avoids this by
                    // computing the scale factor from the current
                    // pointer position relative to the initial
                    // anchor (absolute positioning). We use the
                    // same approach: capture the initial cropRect
                    // at drag start, and on every move frame
                    // compute the TOTAL displacement from the
                    // start position, then apply that total
                    // displacement to the initial cropRect.
                    val initialCropRect = currentCropRect
                    val startLayerX = startLayer.x
                    val startLayerY = startLayer.y
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        // Convert to layer coordinates
                        val curLayerX = change.position.x - offX
                        val curLayerY = change.position.y - offY
                        change.consume()
                        // Total displacement from drag start
                        val totalDxPx = curLayerX - startLayerX
                        val totalDyPx = curLayerY - startLayerY
                        val totalDx = totalDxPx / imgW
                        val totalDy = totalDyPx / imgH
                        val cr = initialCropRect
                        val minSize = 0.08f
                        val newRect = when (edge) {
                            "move" -> {
                                val nw = cr.cropWidth
                                val nh = cr.cropHeight
                                val nl = (cr.left + totalDx).coerceIn(0f, 1f - nw)
                                val nt = (cr.top + totalDy).coerceIn(0f, 1f - nh)
                                cr.copy(left = nl, top = nt, right = nl + nw, bottom = nt + nh)
                            }
                            "lt" -> cr.copy(
                                left = (cr.left + totalDx).coerceIn(0f, cr.right - minSize),
                                top = (cr.top + totalDy).coerceIn(0f, cr.bottom - minSize),
                            )
                            "rt" -> cr.copy(
                                right = (cr.right + totalDx).coerceIn(cr.left + minSize, 1f),
                                top = (cr.top + totalDy).coerceIn(0f, cr.bottom - minSize),
                            )
                            "lb" -> cr.copy(
                                left = (cr.left + totalDx).coerceIn(0f, cr.right - minSize),
                                bottom = (cr.bottom + totalDy).coerceIn(cr.top + minSize, 1f),
                            )
                            "rb" -> cr.copy(
                                right = (cr.right + totalDx).coerceIn(cr.left + minSize, 1f),
                                bottom = (cr.bottom + totalDy).coerceIn(cr.top + minSize, 1f),
                            )
                            "l" -> cr.copy(left = (cr.left + totalDx).coerceIn(0f, cr.right - minSize))
                            "r" -> cr.copy(right = (cr.right + totalDx).coerceIn(cr.left + minSize, 1f))
                            "t" -> cr.copy(top = (cr.top + totalDy).coerceIn(0f, cr.bottom - minSize))
                            "b" -> cr.copy(bottom = (cr.bottom + totalDy).coerceIn(cr.top + minSize, 1f))
                            "rb_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left; val aT = cr.top
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + totalDx) - aL).coerceIn(minSize, maxW)
                                var nh = nw / na
                                if (aT + nh > 1f) { nh = 1f - aT; nw = nh * na }
                                cr.copy(right = aL + nw, bottom = aT + nh)
                            }
                            "lt_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aR = cr.right; val aB = cr.bottom
                                val maxW = aR.coerceAtLeast(minSize)
                                var nw = (aR - (cr.left + totalDx)).coerceIn(minSize, maxW)
                                var nh = nw / na
                                var nT = aB - nh
                                if (nT < 0f) { nh = aB; nw = nh * na; nT = 0f }
                                cr.copy(left = aR - nw, top = nT)
                            }
                            "rt_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left; val aB = cr.bottom
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + totalDx) - aL).coerceIn(minSize, maxW)
                                var nh = nw / na
                                var nT = aB - nh
                                if (nT < 0f) { nh = aB; nw = nh * na; nT = 0f }
                                cr.copy(right = aL + nw, top = nT)
                            }
                            "lb_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aR = cr.right; val aT = cr.top
                                val maxW = aR.coerceAtLeast(minSize)
                                var nw = (aR - (cr.left + totalDx)).coerceIn(minSize, maxW)
                                var nh = nw / na
                                if (aT + nh > 1f) { nh = 1f - aT; nw = nh * na }
                                cr.copy(left = aR - nw, bottom = aT + nh)
                            }
                            "r_lock" -> {
                                val na = normAspect(currentCropAspect, currentCropImage)
                                val aL = cr.left
                                val maxW = (1f - aL).coerceAtLeast(minSize)
                                var nw = ((cr.right + totalDx) - aL).coerceIn(minSize, maxW)
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
                                var nw = (aR - (cr.left + totalDx)).coerceIn(minSize, maxW)
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
                                var nh = ((cr.bottom + totalDy) - aT).coerceIn(minSize, maxH)
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
                                var nh = (aB - (cr.top + totalDy)).coerceIn(minSize, maxH)
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
                    }
                    dragEdge = null
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            // 1b. Floating image layer — anchored in the
            //     center of the upper area. The
            //     [pointerInput] is on the OUTER Box
            //     (the full upper area), not here, so
            //     that touch events at the layer's edges
            //     (where crop handles sit) are received.
            //     The [shadow] / [background] / [clip]
            //     chain applies the iOS-PiP card
            //     silhouette. The 16dp shadow
            //     is decorative (no layout cost); with
            //     [clip = false] it would otherwise show
            //     rectangular corners outside the rounded
            //     clip, so we pass the matching shape
            //     explicitly.
            Box(
                modifier = Modifier
                    .size(
                        width = layerW,
                        height = layerH,
                    )
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                        clip = false,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                    ),
    ) {
        // V_NOW: render the actual cropped image as the
        // bottom layer inside the chrome canvas. The layer's
        // aspect is sized to match the image's intrinsic
        // aspect (see [imgAspect] / `layerW` / `layerH`
        // computation above), so a FIT draw of the decoded
        // bitmap fills the layer exactly. Rotation / flip
        // from the [SceneImage] transform fields are applied
        // with the same pivot as the main canvas's
        // [drawSceneImage] so the preview matches what the
        // user saw on the main canvas before tapping the
        // crop tool. The dim / border / grid / handles paint
        // on top of this image so the user can see both
        // the picture AND the crop overlay at the same time.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(clip = false)) {
            val c = calcScreenCoords()
            val cL = c[0]; val cT = c[1]; val cR = c[2]; val cB = c[3]
            val cW = cR - cL; val cH = cB - cT

            if (displayBitmap != null) {
                val bmpW = displayBitmap.width.toFloat()
                val bmpH = displayBitmap.height.toFloat()
                // V_NOW (user feedback "裁切旋转过后的图片，
                // 裁切图片显示不全"): for a rotated image,
                // the image's VISUAL footprint is its
                // rotated AABB — `(aabbW, aabbH)`, not the
                // intrinsic `(bmpW, bmpH)`. The cover-fit
                // has to compute [fitScale] from the AABB
                // dimensions, otherwise the post-rotation
                // image AABB overshoots the layer.
                //
                // Why: with intrinsic fit
                //   fitScale = max(layerW / bmpW, layerH / bmpH)
                // and a 9:16 portrait image (bmpW/bmpH =
                // 0.5625) rotated 15° around its centre, the
                // AABB becomes
                //   aabbW = 0.5625*cos 15 + 1*sin 15 ≈ 0.802
                //   aabbH = 0.5625*sin 15 + 1*cos 15 ≈ 1.112
                // (normalised to bmpH = 1). The layer, sized
                // to aabbW/aabbH ≈ 0.721, is e.g. 720×1000dp
                // instead of the original intrinsic 9:16
                // ratio. With the OLD intrinsic-based
                // [fitScale = max(720 / bmpW, 1000 / bmpH)] =
                // 1000 / bmpH, the bitmap draws at
                // bmpH*fitScale = 1000 (touches the layer
                // top + bottom) but bmpW*fitScale = 562.5
                // (well under the 720dp layer width). After
                // rotating 15° around the layer centre, the
                // rotated image's AABB = 1000*1.112 = 1112dp
                // tall and 562.5*1.112 ≈ 625dp wide, which
                // exceeds the 1000dp layer height. The 56dp
                // overshoot is clipped by the layer's
                // rounded-corner clip → the user sees
                // empty "layer-coloured" wedges in the
                // middle of the image.
                //
                // The CORRECT fitScale makes the rotated
                // image's AABB line up with the layer:
                //   fitScale = max(layerW / aabbW, layerH / aabbH)
                // Algebraically: layerW / aabbW = layerH /
                // aabbH (both equal to the layer / aabb
                // size ratio), so the max is just that one
                // value. With that scale, the rotated
                // image's AABB = (layerW, layerH) — the
                // image's four rotated corners land exactly
                // on the layer's four corners, every pixel
                // visible, no clipping, no wedges.
                //
                // Flips don't change the AABB so we keep
                // them out of the AABB math (they only
                // change which way the pixels face inside
                // the rotated AABB).
                val aabbW: Float
                val aabbH: Float
                if (cropRotation == 0f) {
                    aabbW = bmpW
                    aabbH = bmpH
                } else {
                    val rad = Math.toRadians(cropRotation.toDouble())
                    val cosA = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
                    val sinA = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
                    aabbW = bmpW * cosA + bmpH * sinA
                    aabbH = bmpW * sinA + bmpH * cosA
                }
                val fitScale = maxOf(size.width / aabbW, size.height / aabbH)
                val drawW = bmpW * fitScale
                val drawH = bmpH * fitScale
                val offX = (size.width - drawW) / 2f
                val offY = (size.height - drawH) / 2f
                rotate(
                    degrees = cropRotation,
                    pivot = Offset(size.width / 2f, size.height / 2f),
                ) {
                    scale(
                        scaleX = if (cropImage.flipX) -1f else 1f,
                        scaleY = if (cropImage.flipY) -1f else 1f,
                        pivot = Offset(size.width / 2f, size.height / 2f),
                    ) {
                        // V_NOW: clipPath the image draw to a
                        // rounded rect matching the floating
                        // layer's [ChromeCornerRadius], so the
                        // picture itself shows the iOS-PiP
                        // card silhouette WITHOUT clipping the
                        // whole Box (which would clip the
                        // corner handles too).
                        clipPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        left = 0f, top = 0f,
                                        right = size.width, bottom = size.height,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                            with(density) { Dimens.ChromeCornerRadius.toPx() }
                                        )
                                    )
                                )
                            }
                        ) {
                            drawImage(
                                image = displayBitmap,
                                dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                                dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)),
                            )
                        }
                    }
                }
            }

            drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, 0f), size = Size(cL, size.height))
            drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cR, 0f), size = Size(size.width - cR, size.height))
            drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cL, 0f), size = Size(cW, cT))
            drawRect(color = Color.Black.copy(alpha = 0.3f), topLeft = Offset(cL, cB), size = Size(cW, size.height - cB))
            // V_NOW (2026-06-11, user spec "裁切框 ... 蓝色边框"
            // + reference screenshot): crop selection border is
            // now M3 primary blue (was white) — matches the
            // blue inner ring of the corner handles (the
            // [Color(0xFF2196F3)] two lines below) so the
            // visual language is "blue = crop selection" across
            // the border + handles. Border width bumped from
            // 2f → 3f so it reads as a deliberate selection
            // (the 2f white border was previously nearly
            // invisible against the dimmed surrounding
            // image).
            drawRect(
                color = Color(0xFF2196F3),
                topLeft = Offset(cL, cT),
                size = Size(cW, cH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
            val gridColor = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                val fx = cL + cW * i / 3f
                drawLine(gridColor, Offset(fx, cT), Offset(fx, cB), strokeWidth = 1f)
                val fy = cT + cH * i / 3f
                drawLine(gridColor, Offset(cL, fy), Offset(cR, fy), strokeWidth = 1f)
            }
            // V_NOW (2026-06-11): only 4 corner handles — no
            // mid-edge handles. The corner circles sit ON the
            // crop rect corner (no diagonal offset) so the
            // entire circle is always visible, even when the
            // crop rect is at the image edge. The previous
            // 12dp diagonal offset pushed the circles into
            // the dimmed area, which made them half-hidden
            // when the crop rect touched the image border.
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
                Offset(cL, cT),
                Offset(cR, cT),
                Offset(cL, cB),
                Offset(cR, cB),
            )
            handlePositions.forEach { pos ->
                drawCircle(color = Color.White, radius = handleRadius, center = pos)
                drawCircle(color = Color(0xFF2196F3), radius = handleRadius - 3f, center = pos)
            }
        }
        }
    }

    // 1c. Bottom toolbar — second (and last) child of the
    //     outer safe-area Column above. Sits flush against
    //     the safe-area bottom (= just above the nav bar
    //     thanks to the outer `windowInsetsPadding(systemBars)`).
    //     The two rows are: the aspect chip row
    //     (1:1 / 4:3 / 3:2 / 16:9 / 9:16 / 自由) and the
    //     action button row (取消 / 应用).
    //
    // V_NOW (M3 sheet chrome, user feedback "裁切界面的视觉风格
    // 要跟现在选区弹出后整体窗口的展示风格保持一致"): the
    // toolbar is now a custom [Surface] styled to match
    // [SelectionBottomSheet]'s [ModalBottomSheet] chrome:
    //   - shape = RoundedCornerShape(topStart = 28.dp,
    //     topEnd = 28.dp) — M3 sheet standard top corners
    //     (was 12dp [ChromeCornerRadius], a "card" shape)
    //   - color = MaterialTheme.colorScheme.surface solid —
    //     no alpha tint (was 0.92-alpha, a frosted look
    //     that competed with the canvas image behind)
    //   - tonalElevation = 0.dp — M3 sheet default
    //     (was 3.dp, a "card" elevation)
    //   - widthIn(max = 560.dp) + [Alignment.CenterHorizontally] —
    //     M3 sheet caps its width on tablets so the panel
    //     doesn't span the full 1750dp landscape width
    //     (was fillMaxWidth, the toolbar stretched to the
    //     screen edges)
    //   - 32x4dp drag handle at the top — matches
    //     [SelectionBottomSheet]'s handle (M3 spec for
    //     large component bottom sheets)
    //   - bottom padding 12dp (was vertical 8dp, which
    //     compressed the action row into the chrome)
    //
    // V_NOW (M3 sheet vs ModalBottomSheet): the obvious
    // choice would be to swap this [Surface] for a real
    // [ModalBottomSheet] (the same component the selection
    // sheet uses), but [ModalBottomSheet]'s built-in scrim
    // intercepts ALL touches outside the sheet content
    // (even with `scrimColor = Color.Transparent` —
    // M3's Scrim composable still receives the touch and
    // dismisses on tap). For the SELECTION sheet this is
    // correct (canvas should be inert while the sheet is
    // up). For CROP the user MUST be able to drag the
    // floating image's crop handles WHILE the toolbar is
    // visible, so a touch-intercepting scrim is fatal.
    // The custom [Surface] reproduces the sheet chrome
    // (handle / corners / color / elevation / max width)
    // without the scrim, keeping the floating image
    // fully interactive. The redundant
    // `windowInsetsPadding(navigationBars)` is removed
    // (the parent Column already pads against
    // [WindowInsets.systemBars]).
    GlassSurface(
        modifier = Modifier
            .widthIn(max = Dimens.SheetMaxWidth)
            .fillMaxWidth()
            .align(Alignment.CenterHorizontally),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        // P0 (2026-06-11 chrome unify): route through
        // [Dimens.SheetCornerRadius] so this toolbar
        // stays in lockstep with [SelectionBottomSheet]
        // if the sheet corner tier ever changes.
        shape = RoundedCornerShape(
            topStart = Dimens.SheetCornerRadius,
            topEnd = Dimens.SheetCornerRadius,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // V_NOW: 32x4dp drag handle at top centre,
            // matching [SelectionBottomSheet]'s handle.
            // M3 1.2's default handle is slightly larger;
            // we keep the 32x4dp custom to match the
            // M3 spec for large component bottom sheets
            // (and the existing [SelectionBottomSheet]).
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFC1C7CE)),
                )
            }

            // V_NOW (user feedback "完全无法使用" + screenshot
            // showed the toolbar compressed to a single row):
            // wrap the chip row + button row in a fixed-height
            // [Column] with a 12dp [Spacer] between them so
            // the two rows are GUARANTEED to render as two
            // separate visual rows regardless of font scale
            // or chip-row wrap behaviour. The [Spacer] (vs
            // relying on the implicit 0dp gap between the two
            // [Row]s) ensures the layout doesn't degenerate to
            // a single line on a constrained-height parent.
            //
            // V_NOW (chip layout): [Arrangement.spacedBy(8.dp)]
            // replaces the previous [SpaceEvenly] so the
            // chips read as a visually-grouped "ratio picker"
            // (uniform 8dp gap between siblings) rather than
            // getting pushed to the screen edges by
            // SpaceEvenly's "distribute remaining space"
            // semantics. 8dp is M3's recommended chip-row
            // spacing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Spacer(modifier = Modifier.height(12.dp))

            // V_NOW (user feedback "检查下方取消和应用按钮
            // 是否符合 material3"): the M3 standard for
            // a "dismiss + confirm" action pair is
            //   - **Confirm (应用)** = [Button] / Filled
            //     (primary, the affirmative action)
            //   - **Dismiss (取消)** = [TextButton]
            //     (text-only, the lesser action)
            // with both buttons RIGHT-ALIGNED in the row
            // (the M3 dialog / bottom-sheet action bar
            // convention — the primary action sits at the
            // end of the visual flow so the thumb lands on
            // it after reading the content from left to
            // right).
            //
            // The previous layout had [FilledTonalButton]
            // for 取消 (= the same "primary filled" visual
            // weight as 应用), which broke the M3
            // primary-vs-secondary hierarchy AND used
            // [SpaceEvenly] which pushed the two buttons
            // to opposite screen edges with a useless
            // empty gap in the middle. The new layout:
            //   - 取消 → [TextButton] (text only, no fill)
            //   - 应用 → [Button] (kept; primary filled)
            //   - both right-aligned via
            //     [Spacer(Modifier.weight(1f))]
            //   - 8dp gap between the two buttons so the
            //     pair reads as a group
            //   - 16dp horizontal padding to leave breathing
            //     room from the screen edge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onAction(PockRefAction.CancelCrop) },
                ) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onAction(PockRefAction.CommitCrop) },
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("应用", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    }  // <-- closes the outer safe-area Column (L2504) wrapping
       //     the upper `Box(weight(1f))` (floating image) +
       //     the bottom toolbar [Surface]. The original
       //     block had this close at the end of the closing
       //     brace stack; the new block must include it to
       //     match the function's brace structure.
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
    val estimatedMinimapSize = with(density) { 200.dp.toPx() }
    // Auto-hide the minimap when the selection sheet would
    // visually overlap it. The sheet is BottomCenter-aligned
    // with widthIn(max = SheetMaxWidth); the minimap sits at
    // BottomStart with CornerInset padding. If the minimap's
    // right edge extends past the sheet's left edge, hide the
    // entire minimap chrome so the bottom row stays clean.
    // V_NOW (2026-06-11): converted to dp-based calculation
    // (matching the FAB overlap fix above) to avoid subtle
    // px/dp confusion. All values are in dp; [parentWidth]
    // (px from BoxWithConstraints) is converted once at the
    // top of the block.
    if (state.selectedImageIds.isNotEmpty()) {
        val parentWidthDp = with(density) { parentWidth.toDp() }
        val sheetWidthDp = minOf(parentWidthDp, Dimens.SheetMaxWidth)
        val sheetLeftDp = (parentWidthDp - sheetWidthDp) / 2
        val minimapRightDp = Dimens.CornerInset + 200.dp
        if (minimapRightDp > sheetLeftDp) return
    }
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

    // V_NOW: long-press zoom slider state shared by both
    // collapsed and expanded minimap branches.
    val zoomSliderActive = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnAction by rememberUpdatedState(onAction)
    val currentCanvasScale by rememberUpdatedState(state.canvasScale)

    // Auto-dismiss: slider disappears after 3 s of inactivity.
    // Any slider drag resets the timer.
    val sliderInteractionStamp = remember { mutableStateOf(0L) }
    LaunchedEffect(zoomSliderActive.value) {
        if (!zoomSliderActive.value) return@LaunchedEffect
        while (true) {
            delay(3000L)
            // If no interaction happened during the wait, dismiss.
            if (sliderInteractionStamp.value == 0L) {
                zoomSliderActive.value = false
                break
            }
            // An interaction occurred; reset stamp and wait again.
            sliderInteractionStamp.value = 0L
        }
    }

    // Morph progress: 0f = minimap, 1f = zoom slider.
    // V_NOW: M3 spec — 长按展开走 emphasized 曲线 250ms,
    // 松手回弹走 standard 曲线 150ms (旧值 300/200ms 是早期近似,
    // 现在对齐 M3 spec).
    val morphProgress by animateFloatAsState(
        targetValue = if (zoomSliderActive.value) 1f else 0f,
        animationSpec = if (zoomSliderActive.value) {
            tween(durationMillis = 250, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        } else {
            tween(durationMillis = 150, easing = FastOutSlowInEasing)
        },
        label = "minimapToSlider",
    )

    // V_NOW: slider orientation resolved from the user's
    // [minimapSliderOrientation] setting. Used by both the
    // long-press temporary slider and the persistent slider
    // so they always match. ADAPTIVE uses scene aspect:
    // portrait (height > width) → vertical, else horizontal.
    val sliderOrientation = when (state.minimapSliderOrientation) {
        com.pockref.app.model.MinimapSliderOrientation.HORIZONTAL -> Orientation.Horizontal
        com.pockref.app.model.MinimapSliderOrientation.VERTICAL -> Orientation.Vertical
        com.pockref.app.model.MinimapSliderOrientation.ADAPTIVE -> {
            val bbox = computeSceneBboxForMinimap(state.images)
            if (bbox.height > bbox.width) Orientation.Vertical else Orientation.Horizontal
        }
    }

    // V_NOW: when minimapDisplayMode == SLIDER, show a
    // persistent zoom slider instead of the minimap.
    // Orientation is resolved from the user's
    // [minimapSliderOrientation] setting (ADAPTIVE uses
    // scene aspect, same logic as the long-press slider).
    // Double-tap on the slider collapses it to the small
    // white bar (same as minimap double-tap behavior).
    if (state.minimapDisplayMode == com.pockref.app.model.MinimapDisplayMode.SLIDER) {
        if (state.minimapCollapsed) {
            // Slider collapsed: show the same small white bar
            // as the minimap collapsed state. Single-tap or
            // double-tap expands back to the slider.
            val edge = state.minimapDockEdge
            val isHorizontalEdge = edge == com.pockref.app.model.MinimapDockEdge.TOP ||
                edge == com.pockref.app.model.MinimapDockEdge.BOTTOM
            val touchWidthDp = if (isHorizontalEdge) Dimens.BottomPanelChromeSize else 32.dp
            val touchHeightDp = if (isHorizontalEdge) 32.dp else Dimens.BottomPanelChromeSize
            val touchWidthPx = with(density) { touchWidthDp.toPx() }
            val touchHeightPx = with(density) { touchHeightDp.toPx() }
            Box(
                modifier = Modifier
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
                    .pointerInput(state.minimapDockEdge) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!zoomSliderActive.value) {
                                    onAction(PockRefAction.SetMinimapCollapsed(false))
                                }
                            },
                        )
                    },
            ) {
                CollapsedMinimapIcon(
                    edge = state.minimapDockEdge,
                    onExpand = {
                        if (!zoomSliderActive.value) {
                            onAction(PockRefAction.SetMinimapCollapsed(false))
                        }
                    },
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        zoomSliderActive.value = true
                        sliderInteractionStamp.value = System.currentTimeMillis()
                    },
                    // V_NOW: do NOT dismiss on long-press end;
                    // slider stays for drag. Dismissed via
                    // onValueChangeFinished or tap-outside.
                    onLongPressEnd = {},
                    // V_NOW (2026-06-07): drag-after-long-
                    // press on the SLIDER-mode collapsed
                    // capsule is also dispatched as
                    // [SetCanvasZoom], same as the
                    // MAP-mode collapsed path above. The
                    // SLIDER mode and the MAP mode share
                    // the same minimap state machine so
                    // the user can press-and-drag either
                    // chrome variant to scale the
                    // canvas.
                    onDrag = { dragAmount ->
                        val scaleDelta = -dragAmount.y * 0.005f
                        val newScale = (currentCanvasScale * (1f + scaleDelta))
                            .coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
                        currentOnAction(PockRefAction.SetCanvasZoom(newScale))
                        sliderInteractionStamp.value = System.currentTimeMillis()
                    },
                )
            }
        } else {
            // Slider expanded: show the zoom slider with
            // double-tap to collapse.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Dimens.CornerInset, bottom = Dimens.CornerInset)
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
                MinimapZoomSlider(
                    orientation = sliderOrientation,
                    currentValue = state.canvasScale,
                    onValueChange = { newValue ->
                        onAction(PockRefAction.SetCanvasZoom(newValue))
                    },
                    onValueChangeFinished = {},
                )
            }
        }
        return
    }

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
                            if (!zoomSliderActive.value) {
                                onAction(PockRefAction.SetMinimapCollapsed(false))
                            }
                        },
                    )
                },
        ) {
                CollapsedMinimapIcon(
                    edge = state.minimapDockEdge,
                    onExpand = {
                        // V_NOW: guard — if the zoom slider is
                        // currently active (long-press triggered),
                        // don't expand to the full minimap. The
                        // slider overlay handles the interaction;
                        // expanding would dismiss the slider and
                        // show the full minimap instead of returning
                        // to the collapsed bar.
                        if (!zoomSliderActive.value) {
                            onAction(PockRefAction.SetMinimapCollapsed(false))
                        }
                    },
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        zoomSliderActive.value = true
                        sliderInteractionStamp.value = System.currentTimeMillis()
                    },
                    // V_NOW: do NOT set zoomSliderActive = false
                    // on long-press end. The slider should remain
                    // visible so the user can drag it. The slider
                    // is dismissed via onValueChangeFinished (drag
                    // complete) or by tapping outside the slider.
                    onLongPressEnd = {},
                    // V_NOW (2026-06-07): drag-after-long-
                    // press on the collapsed capsule is
                    // dispatched as a multiplicative
                    // [SetCanvasZoom]. Same gain (-0.005f
                    // on dragAmount.y) as the expanded
                    // minimap path so the two surfaces feel
                    // identical to scale. Horizontal drag
                    // deltas are ignored (dragAmount.x
                    // doesn't factor into scaleDelta) which
                    // matches the y-only semantics of the
                    // expanded panel.
                    onDrag = { dragAmount ->
                        val scaleDelta = -dragAmount.y * 0.005f
                        val newScale = (currentCanvasScale * (1f + scaleDelta))
                            .coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
                        currentOnAction(PockRefAction.SetCanvasZoom(newScale))
                        // Reset the 3s auto-dismiss timer
                        // on every drag tick — same as the
                        // expanded panel — so the slider
                        // overlay doesn't fade out from
                        // under the user's finger mid-drag.
                        sliderInteractionStamp.value = System.currentTimeMillis()
                    },
                )
        }
    } else {
        // Full minimap panel.
        // Double-tap collapses to dock bar.

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.CornerInset, bottom = Dimens.CornerInset)
                .onSizeChanged { panelActualSize.value = it }
                .offset {
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
                    shadowElevation = 0f
                }
                // V_NOW (gesture contract, revised
                // 2026-06-07 after user feedback): the
                // expanded minimap supports BOTH a
                // double-tap-to-collapse AND a
                // long-press-to-scale. They coexist
                // without conflict via TWO separate
                // [Modifier.pointerInput] blocks stacked
                // on the same Box:
                //
                //   1. [detectTapGestures(onDoubleTap=...)]
                //      — handles fast tap-tap within the
                //      double-tap timeout (default ~300ms).
                //      Fires [SetMinimapCollapsed(true)] to
                //      fold the minimap into the dock bar.
                //      This detector is the FIRST
                //      [pointerInput] so it sits behind the
                //      drag detector in the gesture-pipeline
                //      order (later detectors get first
                //      crack at the down event); but both
                //      detectors only consume the gesture
                //      after a 300-500ms timeout, so in
                //      practice a fast tap-tap flows
                //      through to BOTH detectors and the
                //      drag detector correctly sees it as
                //      "released before the long-press
                //      threshold" and stays quiet.
                //   2. [detectDragGesturesAfterLongPress(...)]
                //      — handles long-press-then-drag for
                //      direct scale manipulation. [onDragStart]
                //      fires at the 500ms threshold with
                //      haptic feedback. [onDrag] maps
                //      dragAmount.y to a multiplicative
                //      scale delta (向上拖放大, 向下拖
                //      缩小; 横向 drag 被忽略, 跟双指
                //      捏合的 y-only 语义一致). [onDragEnd]
                //      leaves the slider visible for the
                //      3s auto-dismiss; [onDragCancel]
                //      collapses immediately.
                //
                // Why this works without gesture-pipeline
                // conflicts:
                //   - Double-tap (fast tap-tap within
                //     ~300ms): both detectors see the down
                //     event, both see up before their own
                //     timeout. The tap detector fires
                //     [onDoubleTap]. The drag detector
                //     doesn't fire [onDragStart] (its 500ms
                //     threshold isn't met). No conflict.
                //   - Long-press (≥500ms hold): the drag
                //     detector commits to drag-mode at 500ms
                //     and consumes the gesture, so the tap
                //     detector never gets a chance to fire
                //     [onDoubleTap] (the second tap would
                //     arrive after the gesture is already
                //     consumed). No conflict.
                //   - Long-press + drag: same as long-press
                //     + drag fires [onDrag] on every move.
                //     Tap detector stays quiet.
                //   - Quick tap (no double-tap, no long
                //     press): neither detector fires.
                //     [clickable] on the [CanvasMinimap]
                //     inner is untouched (no change in
                //     semantics).
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            // Restore the double-tap-to-
                            // collapse UX (previously removed
                            // in 方案 1, restored in this
                            // revision per user request). The
                            // `drag detector will see
                            // <500ms hold and stay quiet, so
                            // the only response is the
                            // collapse.
                            zoomSliderActive.value = false
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
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            zoomSliderActive.value = true
                            sliderInteractionStamp.value = System.currentTimeMillis()
                        },
                        onDrag = { _, dragAmount ->
                            // dragAmount.y: negative when
                            // dragging UP. UP = ZOOM IN
                            // (matches pinch-zoom
                            // semantics). 0.005f gain
                            // matched to the canvas
                            // pinch-zoom baseline so a
                            // full screen-height drag of
                            // ~1700px covers a ~2.1x
                            // scale change.
                            val scaleDelta = -dragAmount.y * 0.005f
                            val newScale = (currentCanvasScale * (1f + scaleDelta))
                                .coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
                            currentOnAction(PockRefAction.SetCanvasZoom(newScale))
                            sliderInteractionStamp.value = System.currentTimeMillis()
                        },
                        onDragEnd = {
                            // Don't collapse here — the
                            // existing 3s auto-dismiss
                            // handles teardown.
                        },
                        onDragCancel = {
                            zoomSliderActive.value = false
                        },
                    )
                }
        ) {
            CanvasMinimap(
                state = state,
                onAction = onAction,
                modifier = Modifier.graphicsLayer(alpha = 1f - morphProgress),
                isCollapsed = false,
                onExpand = {},
            )
        }
    }

    // V_NOW: tap-outside dismiss — when the zoom slider is
    // active, a transparent full-size overlay catches taps
    // anywhere on the canvas and dismisses the slider. The
    // overlay is placed BEFORE the slider Box so the slider
    // (rendered on top) still consumes its own touch events;
    // only taps that fall through to the overlay (i.e. outside
    // the slider) trigger the dismiss.
    if (morphProgress > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        zoomSliderActive.value = false
                    }
                },
        )
    }

    // V_NOW: shared temporary zoom slider overlay, rendered
    // at the same position as the persistent slider
    // (BottomStart + CornerInset). Appears on long-press of
    // the collapsed bar or the expanded minimap, regardless
    // of display mode. Uses the same orientation as the
    // persistent slider (from minimapSliderOrientation setting).
    // Dismissed when: (1) slider drag completes
    // (onValueChangeFinished), or (2) user taps outside the
    // slider (overlay above).
    if (morphProgress > 0.01f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.CornerInset, bottom = Dimens.CornerInset)
                .graphicsLayer(alpha = morphProgress)
                // V_NOW: swipe-left to dismiss the slider.
                // Detects a quick horizontal swipe to the left
                // on the slider container and collapses it back
                // to the collapsed bar. Uses
                // [awaitEachGesture] so it doesn't interfere
                // with the Slider's own drag handling — the
                // Slider consumes events inside its bounds,
                // but a fast swipe that starts on the
                // container edge (outside the Slider track)
                // or a swipe that the Slider doesn't consume
                // will trigger the dismiss.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragX = 0f
                        do {
                            // Use [PointerEventPass.Initial] to observe
                            // drag events before the inner Slider
                            // consumes them. We only read, never
                            // consume — the Slider still works
                            // normally.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            totalDragX += change.positionChange().x
                        } while (event.type != PointerEventType.Release)
                        // Swipe left threshold: at least 60px
                        // of horizontal movement to the left
                        if (totalDragX < -60f) {
                            zoomSliderActive.value = false
                        }
                    }
                },
        ) {
            MinimapZoomSlider(
                orientation = sliderOrientation,
                currentValue = currentCanvasScale,
                onValueChange = { newValue ->
                    sliderInteractionStamp.value = System.currentTimeMillis()
                    currentOnAction(PockRefAction.SetCanvasZoom(newValue))
                },
                onValueChangeFinished = { zoomSliderActive.value = false },
            )
        }
    }
}

/**
 * M3 zoom slider that replaces the minimap on long-press
 * or persists when [MinimapDisplayMode.SLIDER] is active.
 * Self-adapts orientation to scene aspect:
 *   - portrait scene (aspect < 1): vertical bar 80×240
 *   - landscape scene (aspect ≥ 1): horizontal bar 240×80
 *   - square scene: defaults to horizontal
 *
 * V_NOW: continuous (stepless) bidirectional zoom slider.
 * M3 visual spec (matches top toolbar chrome tokens):
 * - Container: Surface with [Dimens.ToolbarElevation] tonal
 *   elevation, [Dimens.ChromeCornerRadius] shape,
 *   [Dimens.ChromeSurfaceAlpha] alpha — same as TopToolbar
 * - M3 Slider (continuous, no steps) with primary active
 *   track, surfaceVariant inactive track
 * - Range: 0.25x..4.0x, default 1.0x (current canvasScale)
 * - Zoom anchors on canvas visual center
 * - TalkBack: "缩放控制, 当前缩放级别 X 倍"
 * - Keyboard: ←/→ 0.01x step, Home/End 跳端点, PageUp/Down 0.5x
 *
 * Implementation note: M3 Slider is horizontal-only in
 * Compose 1.6 (orientation param lands in 1.7+). For
 * vertical we render the Slider in a 240×48 box, rotate
 * -90° via [graphicsLayer], then swap the layout dimensions
 * with [verticalLayoutSwap] so the parent sees 48×240.
 * Slider's internal gesture / a11y work unchanged because
 * the touch event pipeline re-projects through the rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimapZoomSlider(
    orientation: Orientation,
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVertical = orientation == Orientation.Vertical
    val colorScheme = MaterialTheme.colorScheme

    // The M3 Slider always renders horizontally. For vertical
    // we render it in a (4×48)×48 box and rotate -90°, then
    // [verticalLayoutSwap] reports 48×(4×48) to the parent so
    // positioning (BottomStart etc.) is correct.
    // - Horizontal: 240×48 (width×height), height matches
    //   the one-click layout FAB ([BottomPanelChromeSize]).
    // - Vertical: after rotation the visual size is
    //   48×(4×48) — width matches FAB, height matches the
    //   expanded right-bottom panel (4 items × 48dp each).
    val sliderLength = Dimens.BottomPanelChromeSize * 4
    Box(
        modifier = modifier
            .then(if (isVertical) Modifier.verticalLayoutSwap() else Modifier)
            .size(sliderLength, Dimens.BottomPanelChromeSize)
            .graphicsLayer {
                if (isVertical) rotationZ = -90f
            }
            // Clip before background so Slider thumb/track
            // never overflows the 48dp height (which becomes
            // the visual width after rotation for vertical).
            .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
            .glassMaterial(RoundedCornerShape(Dimens.ChromeCornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        val tooltipState = rememberTooltipState(isPersistent = true)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip { Text(String.format("%.2fx", currentValue)) }
            },
            state = tooltipState,
        ) {
            Slider(
                value = currentValue.coerceIn(ZOOM_MIN, ZOOM_MAX),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = ZOOM_MIN..ZOOM_MAX,
                colors = SliderDefaults.colors(
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    // Horizontal padding (12dp) = end caps for
                    // the track; after -90° rotation this becomes
                    // the vertical end caps of the vertical slider.
                    // Vertical padding (2dp) = top/bottom inset;
                    // after rotation this becomes the horizontal
                    // inset of the vertical slider, keeping the
                    // track visually close to the 48dp container
                    // edge (matching FAB width).
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .semantics {
                        contentDescription =
                            "缩放控制, 当前缩放级别 ${String.format("%.2f", currentValue)} 倍"
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = currentValue,
                            range = ZOOM_MIN..ZOOM_MAX,
                            steps = 0,
                        )
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val fineStep = 0.01f
                        val coarseStep = 0.5f
                        val newValue = when (event.key) {
                            Key.DirectionLeft, Key.DirectionUp ->
                                (currentValue - fineStep).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            Key.DirectionRight, Key.DirectionDown ->
                                (currentValue + fineStep).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            Key.MoveHome -> ZOOM_MIN
                            Key.MoveEnd -> ZOOM_MAX
                            Key.PageUp ->
                                (currentValue + coarseStep).coerceAtMost(ZOOM_MAX)
                            Key.PageDown ->
                                (currentValue - coarseStep).coerceAtLeast(ZOOM_MIN)
                            else -> return@onKeyEvent false
                        }
                        onValueChange(newValue)
                        true
                    },
            )
        }
    }
}

/**
 * Swaps the layout dimensions of the modified composable so a
 * 240×48 box (rotated -90° via [graphicsLayer]) reports 48×240
 * to its parent. The content is re-centered so the visual
 * center (unchanged by rotation) aligns with the reported
 * bounds' center.
 */
private fun Modifier.verticalLayoutSwap() = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyCanvasState(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onOpenBlankCanvas: () -> Unit,
    // V_NOW (2026-06-16 user feedback "把导出日志按钮改成默认
    // 不显示,点 pockref 图标 7 次后才会显示"): the start-page
    // log-export FAB is hidden by default and unlocked via
    // an easter-egg tap counter on the PockRef text title. The
    // tap counter is local (remember) — it resets on every
    // fresh app launch, matching the spec "应用每次打开启动页
    // 的导出日志按钮都不显示". [showLogExportFab] is the parent
    // (PockRefApp) state flag that gates the actual FAB
    // mount. [onLogExportFab] flips that flag to true and
    // sticks for the rest of this session (no reset, no
    // auto-hide timer — once unlocked, the FAB stays until
    // the activity is destroyed).
    showLogExportFab: Boolean = false,
    onLogExportFab: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 7-tap easter-egg counter on the PockRef title. local
    // remember: dies with the composable, so every fresh
    // start-page entry (which is what the spec actually
    // requires) starts from 0. Counter only fires the
    // callback on the 7th tap; taps 1-6 are silent. No
    // visual feedback (no snackbar / no toast) — the FAB
    // appearing IS the affordance, the spec doesn't ask
    // for an intermediate confirmation.
    val pockrefIconTapCount = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Surface(
        modifier = modifier.widthIn(max = 520.dp).glassBackdropSource(),
        // P1 (2026-06-11 chrome unify): mid-size card
        // tier (sits between 12dp chrome and 24dp dialog).
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
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
                // V_NOW (2026-06-16 user spec "把导出日志按钮
                // 改成默认不显示,点 pockref 图标 7 次后才
                // 会显示"): the title doubles as the
                // easter-egg activator. `clickable` on a Text
                // is the standard pattern (the Text itself has
                // no internal hit-rect, so a Modifier
                // extension is required). The `clip` /
                // `clickable` order: clip first, then clickable,
                // so the ripple is bounded to the text glyph
                // region (no overflow into the surrounding
                // card padding — which would visually look
                // like a "card is tappable" affordance and
                // confuse the user about the actual card
                // chrome). The counter is the local
                // `pockrefIconTapCount` remember'd in this
                // composable; on the 7th tap it calls
                // [onLogExportFab] which flips the parent's
                // [showLogExportFab] flag to true.
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val next = pockrefIconTapCount.intValue + 1
                        pockrefIconTapCount.intValue = next
                        if (next >= 7) onLogExportFab()
                    },
                // V_NOW (2026-06-10 user spec "启动页
                // pockref 字体"): use Nunito (a rounded
                // geometric sans) via Google Fonts
                // Downloadable Fonts API, sized and
                // weighted per Material 3 type scale.
                //
                //   - displaySmall (36sp): the M3 tier
                //     for prominent-but-secondary
                //     heroes (e.g. empty-state titles,
                //     splash logos). displayLarge
                //     (57sp) is reserved for "big
                //     marketing numbers" and would
                //     dwarf the FAB below; the
                //     previous 44sp was a one-off that
                //     doesn't map to any M3 role.
                //   - Bold (700): M3 default for
                //     display* is Normal (400); Bold
                //     gives the "logo" affordance just
                //     enough weight to read as a brand
                //     mark without the chunky
                //     solid-fill feel of Black (900)
                //     that the previous iteration
                //     had.
                //   - letterSpacing 0: M3 default
                //     for display* is tight; the
                //     previous 0.5sp tracking made
                //     "PockRef" feel like two words.
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            ExtendedFloatingActionButton(
                onClick = onOpenBlankCanvas,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                // P1 (2026-06-11 chrome unify): match the
                // chrome surface tier so the EFAB inside
                // the empty-state card reads as the same
                // visual family as the corner-anchored
                // chrome (toolbar / minimap / FAB / lock).
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "添加图片", modifier = Modifier.size(24.dp))
            }
            RecentFileGrid(
                entries = state.recentScenes,
                sortMode = state.recentSortMode,
                showPinnedOnly = state.showPinnedOnly,
                multiSelectMode = state.recentMultiSelectMode,
                selectedUris = state.recentSelection,
                onOpen = { onAction(PockRefAction.OpenRecentScene(it)) },
                onEnterMultiSelect = { uri -> onAction(PockRefAction.EnterRecentMultiSelect(uri)) },
                onToggleSelection = { uri -> onAction(PockRefAction.ToggleRecentSelection(uri)) },
                // V_NOW (2026-06-10, v1.1 swipe-to-pin):
                // the right-swipe on a recent tile
                // dispatches the existing
                // [PockRefAction.TogglePinRecentScene]
                // action. No new action is needed —
                // the swipe handler reuses the same
                // toggle-path that the canvas-side
                // "edit scene" code uses, so a swipe-
                // pin and a bulk-action pin produce
                // byte-identical on-disk state.
                onSwipePin = { uri -> onAction(PockRefAction.TogglePinRecentScene(uri)) },
                onSwipeDelete = { uri -> onAction(PockRefAction.RemoveRecentScene(uri)) },
                onRename = { uri, newLabel ->
                    onAction(PockRefAction.RenameRecentScene(uri, newLabel))
                },
                // V_NOW (2026-06-10 user spec "启动页去掉长按
                // 封面显示置顶和删除按钮，以及去除长按封面触发
                // 其他功能的相关代码"): the long-press overlay
                // (and its `onRemove` / `onTogglePin` callbacks)
                // is removed from [RecentFileGrid]. Pin / Delete
                // for a recent scene now live on the dedicated
                // scene-edit path (the canvas-side "..." menu /
                // scene-info sheet) — not in the start-page
                // peek. The two callback params and the
                // [PockRefAction.RemoveRecentScene] /
                // [PockRefAction.TogglePinRecentScene]
                // actions are intentionally left in place; they
                // are dispatched from the canvas-side scene-edit
                // path, just not from here.
            )
        }
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

/**
 * V_NOW (2026-06-09 chrome unify): 顶栏按钮的统一模板 — 替换
 * 之前混合的两套写法(40dp Box / 36dp IconButton),5 个按钮
 * 大小完全一致,符合 M3 "视觉一致触达" 的 chrome 规范.
 *
 *   - 40dp 触摸 Box (不走 IconButton 的 minimumInteractiveComponentSize,
 *     M3 默认会撑到 48dp — 跟 ToolbarIconButton 不一致).
 *   - 20dp icon (之前 chevron/import 是 18dp,统一成 20dp 跟
 *     撤销 / 重做 / 关闭对齐).
 *   - onSurfaceVariant tint,disabled 时 alpha=0.38 (M3 标准).
 *   - [active] 参数:popup 打开时按钮背景 tint 为
 *     [primaryContainer.copy(alpha = ChromeSurfaceAlpha)],
 *     跟 Lock 按钮激活态视觉一致. 禁用时 active 不生效.
 *
 * 5 个按钮 (chevron / 导入 / 撤销 / 重做 / 关闭) 全部走这个
 * 模板,顶栏内间距统一 8dp,见 [TopToolbar] 调用处.
 */
@Composable
private fun ToolbarIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
            // V_NOW: removed the [active] state highlight
            // (the [primaryContainer.copy(alpha = ChromeSurfaceAlpha)]
            // background). The user found the highlight
            // visually noisy — two adjacent buttons (chevron
            // + add) would read as a single "container" when
            // one was active, which they didn't want. The
            // [active] parameter is kept for source-compat
            // (callers still pass it for future re-enable)
            // but the renderer no longer paints a tint for
            // it.
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * High-frequency actions: Undo, Redo.
 * V_NOW (2026-06-09): 函数简化成透传 canUndo / canRedo 到
 * [ToolbarIconButton] 模板,统一 40dp + 20dp 视觉.
 * 之前是 36dp IconButton + minimumInteractiveComponentSize
 * (M3 默认撑到 48dp) — 跟 chevron/import 40dp Box 视觉不一致.
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

// V_NOW (2026-06-16 user feedback "为什么我的应用会闪退",
// logcat: java.lang.RuntimeException: Canvas: trying to draw
// too large(347892948bytes) bitmap.): the [CropOverlay] fallback
// path used to call [BitmapFactory.decodeByteArray] on the raw
// image bytes without any downsample. For a disk-backed .pur
// image whose original dimensions are e.g. 9317×9333, that
// produced a 347MB ARGB_8888 bitmap which the Compose `Image`
// couldn't draw to the [Canvas] (Android framework throws
// [BaseRecordingCanvas.throwIfCannotDraw] past the ~200MB
// single-bitmap limit on most devices, including SM-X810's
// Adreno 730). The canvas-render path (PockRefCanvas /
// ImageDecodePipeline) already downsample-decodes via the
// LOD ladder (128→256→512→1024→2048→4096), so the crash
// was crop-fallback-specific.
//
// This helper mirrors [SceneImageDecoder.decodeSampledBitmap]
// — inJustDecodeBounds to get the original dimensions,
// inSampleSize power-of-two downsample, then a precise
// [Bitmap.createScaledBitmap] pass to land on [maxLongEdge].
// It also recycles intermediate bitmaps so the 331MB
// intermediate is released the moment the scaled copy exists.
// Caller is responsible for recycling the returned bitmap.

// Target long-edge for the crop-fallback decoded bitmap.
// 2048px matches the second-largest rung of the canvas LOD
// ladder (DECODE_LADDER in [SceneImageDecoder]). The crop
// UI is a floating layer anchored in the upper half of the
// safe area, so its longest displayed edge is well under
// 2000px on SM-X810 (2800×1752 landscape, status bar +
// toolbar eat the top ~80dp, so the floating crop layer
// is roughly 1300-1500px on its long side). 2048 is enough
// for pixel-accurate crop-rect inspection without risking
// the 200MB Canvas bitmap limit.
private const val CROP_FALLBACK_MAX_LONG_EDGE = 2048
private fun decodeSampledBitmap(
    bytes: ByteArray,
    maxLongEdge: Int,
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Power-of-two inSampleSize: doubles the downsample each
    // step until we'd be below [maxLongEdge]. Matches the
    // Android docs "Power of 2" guidance — values other than
    // powers of two are rounded down to the nearest one.
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var inSampleSize = 1
    while (longSide / (inSampleSize * 2) >= maxLongEdge) {
        inSampleSize *= 2
    }

    val opts = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        inMutable = false
    }
    val decoded = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (oom: OutOfMemoryError) {
        // Retry with 2x downsample if the first pass OOMed.
        // (The bounds probe above only reads headers, so
        // outWidth/outHeight doesn't trigger the OOM; it
        // happens on the pixel-decode pass.)
        opts.inSampleSize = inSampleSize * 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } ?: return null

    val longSideDecoded = maxOf(decoded.width, decoded.height)
    return if (longSideDecoded > maxLongEdge) {
        val scale = maxLongEdge.toFloat() / longSideDecoded.toFloat()
        val newW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val newH = (decoded.height * scale).toInt().coerceAtLeast(1)
        android.graphics.Bitmap.createScaledBitmap(decoded, newW, newH, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else {
        decoded
    }
}

@Composable
private fun PockRefCanvas(
    state: PockRefUiState,
    modifier: Modifier = Modifier,
    onAction: (PockRefAction) -> Unit,
    imageLazyLoader: com.pockref.app.data.PurFileParser? = null,
    imageDecoder: com.pockref.app.data.SceneImageDecoder? = null,
    // V_NOW (user feedback "P0-A1 阈值按 density 缩放"):
    // [LocalDensity.current] is a @Composable getter, but the
    // gesture handler runs inside [pointerInput { awaitEachGesture }]
    // which is NOT a @Composable scope. We read density here
    // (composable scope) and forward it into the handler closure.
    // The `density` variable is then used by the threshold
    // conversion at the start of each gesture below.
    density: androidx.compose.ui.unit.Density = LocalDensity.current,
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
    bitmapWorkingSet: CanvasBitmapWorkingSet,
    tileCache: androidx.compose.runtime.snapshots.SnapshotStateMap<com.pockref.app.data.ImageTileKey, ImageBitmap>,
    posterizeCache: PosterizeBitmapCache,
    gifFrameBitmapCache: GifFrameBitmapCache,
    gifPlaybackSources: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackSource>,
    gifPlaybackStates: androidx.compose.runtime.snapshots.SnapshotStateMap<String, GifPlaybackState>,
    selectedGifPlaybackImageId: String?,
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
    var localCanvasSize by remember { mutableStateOf(Size.Zero) }
    val renderViewportSize = if (
        localCanvasSize.width > 0f && localCanvasSize.height > 0f
    ) {
        localCanvasSize
    } else {
        Size(
            state.canvasViewportSize.width,
            state.canvasViewportSize.height,
        )
    }
    // V_NOW: the Activity and floating overlay render this composable in
    // separate windows but share one ViewModel state. Decode/tile planning
    // must use this host's measured canvas size; the global state viewport
    // can still contain the full-screen Activity dimensions while the
    // overlay is showing.
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
    val tilePipeline = remember(imageDecoder) {
        imageDecoder?.let { decoder ->
            com.pockref.app.data.ImageTilePipeline<android.graphics.Bitmap>(
                decodeTile = { image, request -> decoder.decodeTile(image, request) },
            )
        }
    }

    val currentState by rememberUpdatedState(state)
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
            pipeline.close()
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)
    val panCoalescer = remember { CanvasPanFrameCoalescer() }
    val panFrameRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    var canvasPanPresentationOffset by remember { mutableStateOf<Offset?>(null) }
    var canvasPanLastCommittedOffset by remember { mutableStateOf<Offset?>(null) }
    fun flushQueuedCanvasPan() {
        splitCanvasPanDelta(panCoalescer.flush()).forEach { step ->
            currentOnAction(PockRefAction.PanCanvas(step))
        }
    }
    fun addCanvasPanPreview(delta: Offset) {
        canvasPanPresentationOffset =
            (canvasPanPresentationOffset ?: currentState.canvasOffset) + delta
    }
    fun commitCanvasPanPreview() {
        val presentationOffset = canvasPanPresentationOffset ?: return
        val delta = canvasPanCommitDelta(
            presentationOffset = presentationOffset,
            lastCommittedOffset = canvasPanLastCommittedOffset,
            stateOffset = currentState.canvasOffset,
        )
        splitCanvasPanDelta(delta).forEach { step ->
            currentOnAction(PockRefAction.PanCanvas(step))
        }
        canvasPanLastCommittedOffset = presentationOffset
    }
    fun enqueueCanvasPan(delta: Offset) {
        panCoalescer.enqueue(delta)
        panFrameRequests.trySend(Unit)
    }
    LaunchedEffect(
        state.canvasOffset,
        canvasPanPresentationOffset,
        canvasPanLastCommittedOffset,
    ) {
        val presentationOffset = canvasPanPresentationOffset
        if (
            presentationOffset != null &&
            canvasPanLastCommittedOffset != null &&
            (presentationOffset - state.canvasOffset).getDistance() <= 0.5f
        ) {
            canvasPanPresentationOffset = null
            canvasPanLastCommittedOffset = null
        }
    }
    LaunchedEffect(panFrameRequests) {
        for (ignored in panFrameRequests) {
            withFrameNanos {
                flushQueuedCanvasPan()
            }
        }
    }
    val currentLoaderRef by rememberUpdatedState(imageLazyLoader)
    val focusedHighResImageId = remember(
        state.fittedImageId,
        state.selectedImageIds,
        state.selectedImageId,
    ) {
        val fittedId = state.fittedImageId
        if (fittedId != null &&
            (state.selectedImageIds == setOf(fittedId) || state.selectedImageId == fittedId)
        ) {
            fittedId
        } else {
            null
        }
    }
    val tileImageId = remember(
        state.images,
        state.selectedImageIds,
        state.selectedImageId,
        state.globalImageLock,
        state.lockCanvasFrozen,
        state.lockRetainedHdImageId,
    ) {
        ImageTilePolicy.hdImageIdForDisplay(
            images = state.images,
            selectedImageIds = state.selectedImageIds,
            selectedImageId = state.selectedImageId,
            retainedImageId = state.lockRetainedHdImageId,
            retainWhileLocked = state.globalImageLock || state.lockCanvasFrozen,
        )
    }
    val currentFocusedHighResImageId by rememberUpdatedState(focusedHighResImageId)
    val currentTileImageId by rememberUpdatedState(tileImageId)
    val currentTilePipeline by rememberUpdatedState(tilePipeline)
    val panViewportUpdates = remember {
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    var tileCacheImageId by remember { mutableStateOf<String?>(null) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    // V_NOW: track the image ID of the last tap so that
    // double-tap detection does NOT depend on
    // [curState.selectedImageIds]. The first tap dispatches
    // [CycleImageUnderPoint] which selects the image in the
    // ViewModel, but [curState] is read via
    // [rememberUpdatedState] which only refreshes on
    // recomposition — the state may not have propagated by
    // the time the second tap fires (< 300 ms later), so
    // checking [tappedId in curState.selectedImageIds]
    // would always be false on the second tap.
    var lastTapImageId by remember { mutableStateOf("") }
    // V_NOW: in single-select mode, the first tap of a
    // potential double-tap calls
    // [CycleImageUnderPoint(showSheet = false)] to select
    // the image without immediately showing the
    // SelectionBottomSheet. A delayed [TickSelectionSheet]
    // is scheduled — if the second tap comes within the
    // double-tap window, this job is cancelled and the
    // focus/unfocus action fires instead, preventing the
    // sheet from flashing open then closed.
    var pendingSheetJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // V_NOW (2026-06-08 "连续弹出两次" 修复): wall-
    // clock stamp of the LAST [TickSelectionSheet]
    // dispatch (single-select path). The scheduling
    // logic below debounces against this so a confused
    // "tap-tap" within [SHEET_SHOW_DEBOUNCE_MS] of a
    // previous tap doesn't dispatch twice. See the
    // [SHEET_SHOW_DEBOUNCE_MS] constant for the full
    // rationale.
    var lastSheetShowMs by remember { mutableLongStateOf(0L) }
    val tapScope = rememberCoroutineScope()
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
        tileCache.keys.filterNot { it.imageId in currentIds }.forEach { key ->
            tileCache.remove(key)
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

    // Decode only the viewport and its prefetch ring; distant images wait
    // until a pan brings them close enough to render.
    LaunchedEffect(
        state.images.map {
            listOf(
                it.id,
                it.imageBytes.size,
                it.imageByteOffset,
                it.imageByteLength,
                it.mimeType,
            )
        },
        renderViewportSize,
        focusedHighResImageId,
    ) {
        val s = currentState
        val viewport = worldViewportRect(
            canvasOffset = s.canvasOffset,
            canvasScale = s.canvasScale,
            viewportWidth = renderViewportSize.width,
            viewportHeight = renderViewportSize.height,
        )
        pipeline.onSceneChanged(
            images = s.images,
            viewportRect = android.graphics.RectF(viewport.left, viewport.top, viewport.right, viewport.bottom),
            cachedImageIds = bitmapCache.keys.toSet(),
        ) { image ->
            ImageDecodePolicy.requestFor(
                image = image,
                canvasScale = s.canvasScale,
                isFocusedImage = image.id == focusedHighResImageId,
            )
        }
    }

    suspend fun requestViewportDecode() {
        val s = currentState
        val viewport = worldViewportRect(
            canvasOffset = s.canvasOffset,
            canvasScale = s.canvasScale,
            viewportWidth = renderViewportSize.width,
            viewportHeight = renderViewportSize.height,
        )
        if (viewport.width <= 0f || viewport.height <= 0f) return
        evictCanvasBitmaps(
            bitmapCache = bitmapCache,
            workingSet = bitmapWorkingSet,
            state = s,
            viewportSize = renderViewportSize,
        )
        val cachedLevels = bitmapCache.mapValues { it.value.first }
        pipeline.onViewportChanged(
            images = s.images,
            viewportRect = android.graphics.RectF(viewport.left, viewport.top, viewport.right, viewport.bottom),
            cachedImageIds = bitmapCache.keys.toSet(),
            cachedLevels = cachedLevels,
        ) { image ->
            ImageDecodePolicy.requestFor(
                image = image,
                canvasScale = s.canvasScale,
                isFocusedImage = image.id == currentFocusedHighResImageId,
            )
        }
    }

    suspend fun requestVisibleTiles() {
        if (tileCacheImageId != currentTileImageId) {
            tileCache.clear()
            tileCacheImageId = currentTileImageId
        }

        val activeImage = currentTileImageId?.let { id ->
            currentState.images.firstOrNull { it.id == id }
        }
        val tilePipelineSnapshot = currentTilePipeline
        if (tilePipelineSnapshot == null || activeImage == null ||
            !ImageTilePolicy.shouldRequestTiles(
                image = activeImage,
                canvasScale = currentState.canvasScale,
                isHdDisplayActive = true,
            )
        ) {
            tileCache.clear()
            return
        }

        val s = currentState
        val viewport = worldViewportRect(
            canvasOffset = s.canvasOffset,
            canvasScale = s.canvasScale,
            viewportWidth = renderViewportSize.width,
            viewportHeight = renderViewportSize.height,
            bufferPx = 0f,
        )
        val requests = ImageTilePlanner.visibleTiles(activeImage, viewport)
        val keepKeys = requests
            .take(MAX_TILE_CACHE_ENTRIES)
            .mapTo(mutableSetOf()) { it.key }
        tileCache.keys
            .filter { it !in keepKeys }
            .forEach { tileCache.remove(it) }
        val missing = requests.filter { it.key in keepKeys && it.key !in tileCache }
        tilePipelineSnapshot.decode(activeImage, missing) { decoded ->
            if (decoded.request.key in keepKeys) {
                tileCache[decoded.request.key] = decoded.bitmap.asImageBitmap()
            }
        }
    }

    LaunchedEffect(
        state.canvasScale,
        renderViewportSize,
        state.images,
        focusedHighResImageId,
        tileImageId,
        state.globalImageLock,
        state.lockCanvasFrozen,
        state.lockRetainedHdImageId,
        tilePipeline,
    ) {
        requestViewportDecode()
        requestVisibleTiles()
    }

    LaunchedEffect(state.canvasOffset) {
        panViewportUpdates.tryEmit(Unit)
    }

    LaunchedEffect(panViewportUpdates) {
        panViewportUpdates
            .debounceCanvasPanViewportUpdates()
            .collectLatest {
                requestViewportDecode()
                requestVisibleTiles()
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

    // V_NOW (P2-C3): drive the selection-flash alpha
    // off a Compose State so the Canvas re-draws every
    // ~16ms while the 200ms flash is active. We can't
    // compute alpha from `System.currentTimeMillis()`
    // inside [DrawScope] alone — the draw lambda is
    // only re-invoked when a state it READS changes,
    // and `now()` is not a state read. The pattern
    // below uses [produceState] keyed on
    // [state.selectionFlash] so a brand-new flash
    // (or a null → non-null transition) launches a
    // coroutine that updates `flashAlphaNow` for
    // 200ms, then leaves it at 0f.
    //
    // The flash alpha curve is 0.1 → 0.5 over the
    // first 100ms, 0.5 → 0.0 over the next 100ms,
    // then 0 (see also [drawSceneImage] which
    // re-derives the curve from the same elapsed-ms
    // computation; the state is read inside the draw
    // lambda so the Canvas re-invalidates each tick).
    val flashAlphaNow by produceState(
        initialValue = 0f,
        key1 = state.selectionFlash,
    ) {
        val flash = state.selectionFlash
        if (flash == null) {
            value = 0f
            return@produceState
        }
        val startMs = flash.atMs
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed >= 200L) {
                value = 0f
                break
            }
            value = if (elapsed < 100L) {
                0.1f + 0.4f * (elapsed / 100f)
            } else {
                0.5f * (1f - (elapsed - 100L) / 100f)
            }
            kotlinx.coroutines.delay(16L)
        }
    }

    val controlDensity = LocalDensity.current
    var selectionHdRotationActive by remember { mutableStateOf(false) }
    val controlWidthPx = with(controlDensity) { 52.dp.toPx() }
    val controlHeightPx = with(controlDensity) { 44.dp.toPx() }
    val controlInsetPx = with(controlDensity) { 8.dp.toPx() }
    val selectedImagesForHd = remember(
        state.images,
        state.selectedImageIds,
        state.selectedImageId,
    ) {
        val selectedIds = state.selectedImageIds.ifEmpty {
            state.selectedImageId?.let(::setOf).orEmpty()
        }
        state.images.filter { it.id in selectedIds }
    }
    val selectionHdPlacement = remember(
        selectedImagesForHd,
        state.canvasOffset,
        state.canvasScale,
        renderViewportSize,
        state.transientDragImageIds,
        state.transientDragDelta,
        controlWidthPx,
        controlHeightPx,
        controlInsetPx,
    ) {
        val viewport = renderViewportSize
        val displayImages = selectedImagesForHd.map { image ->
            val dragOffset = if (image.id in state.transientDragImageIds) {
                state.transientDragDelta
            } else {
                Offset.Zero
            }
            if (dragOffset == Offset.Zero) image else image.copy(center = image.center + dragOffset)
        }
        if (displayImages.isEmpty() ||
            viewport.width <= 0f ||
            viewport.height <= 0f ||
            displayImages.any { image -> isGifMimeType(image.mimeType) }
        ) {
            null
        } else if (displayImages.size == 1) {
            val image = displayImages.single()
            val canvasCenter = Offset(
                x = viewport.width / 2f + state.canvasOffset.x,
                y = viewport.height / 2f + state.canvasOffset.y,
            )
            val imageCenter = Offset(
                x = canvasCenter.x + image.center.x * state.canvasScale,
                y = canvasCenter.y + image.center.y * state.canvasScale,
            )
            val imageWidth = image.displaySize.width * image.scale * state.canvasScale
            val imageHeight = image.displaySize.height * image.scale * state.canvasScale
            OriginalResolutionControlPlacement(
                center = projectOriginalResolutionControlCenter(
                    imageCenter = imageCenter,
                    imageWidthPx = imageWidth,
                    imageHeightPx = imageHeight,
                    rotationDegrees = image.rotationDegrees,
                    flipX = image.flipX,
                    flipY = image.flipY,
                    controlWidthPx = controlWidthPx,
                    controlHeightPx = controlHeightPx,
                    insetPx = controlInsetPx,
                ),
                rotationDegrees = image.rotationDegrees,
                controlWidthPx = controlWidthPx,
                controlHeightPx = controlHeightPx,
            )
        } else {
            val selectionFrame = projectSelectionFrameToScreen(
                worldFrame = groupSelectionAabb(displayImages).rect,
                canvasOffset = state.canvasOffset,
                canvasScale = state.canvasScale,
                viewportSize = viewport,
            )
            val controlCenter = originalResolutionControlPosition(
                frame = selectionFrame,
                controlWidthPx = controlWidthPx,
                controlHeightPx = controlHeightPx,
                insetPx = controlInsetPx,
            )
            OriginalResolutionControlPlacement(
                center = controlCenter,
                rotationDegrees = 0f,
                controlWidthPx = controlWidthPx,
                controlHeightPx = controlHeightPx,
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
            .fillMaxSize()
            .background(state.backgroundColor)
            .onSizeChanged { canvasSize ->
                localCanvasSize = Size(
                    canvasSize.width.toFloat(),
                    canvasSize.height.toFloat(),
                )
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
                    // V_NOW (2026-06-10 user spec "启动页的最近
                    // 文件左右滑动都没有反应"): the unconditional
                    // [down.consume()] below was eating EVERY
                    // pointer-down on the canvas, including
                    // pointer-downs that land on the start-page
                    // [EmptyCanvasState] / [RecentFileGrid]
                    // overlay. The grid's [LazyVerticalGrid]
                    // vertical scroll and the tile's
                    // [awaitEachGesture] swipe detector both
                    // need an unconsumed down to start their
                    // gesture loops — a consumed down means
                    // they never see the gesture start, so
                    // both vertical scroll and horizontal
                    // swipe-to-pin silently no-op'd.
                    //
                    // Fix: skip the consume (and the entire
                    // canvas gesture loop) when the canvas
                    // is empty (start-page state). The
                    // [EmptyCanvasState] overlay sits on top
                    // of the canvas in the z-order and
                    // handles its own gestures; the canvas
                    // pointerInput has nothing useful to do
                    // when there are no images to drag /
                    // select / transform.
                    if (!currentState.hasSceneContent && !currentState.markupModeActive) return@awaitEachGesture
                    down.consume()
                    // If the crop overlay is active, skip all
                    // canvas gesture processing so touches on
                    // the dimmed mask area don't accidentally
                    // select / drag images behind the overlay.
                    if (currentState.cropImageId != null) return@awaitEachGesture
                    val downPos = down.position
                    val downTime = System.currentTimeMillis()
                    val s = currentState
                    // V_NOW (user feedback "P0-A1 阈值按 density
                    // 缩放"): the file-scope *DP constants (e.g.
                    // [DRAG_TAP_THRESHOLD_DP]) are interpreted in
                    // device-independent pixels and need to be
                    // multiplied by the current screen density.
                    val dragTapThresholdPx = with(density) { DRAG_TAP_THRESHOLD_DP.dp.toPx() }
                    val tapMaxDragPx = with(density) { TAP_MAX_DRAG_DP.dp.toPx() }
                    val gestureMovedThresholdPx = with(density) { GESTURE_MOVED_THRESHOLD_DP.dp.toPx() }
                    val doubleTapMaxDistPx = with(density) { DOUBLE_TAP_MAX_DIST_DP.dp.toPx() }
                    val edgeAutoScrollThresholdPx = with(density) { EDGE_AUTO_SCROLL_THRESHOLD_DP.dp.toPx() }
                    if (s.markupModeActive) {
                        fun screenToMarkupWorld(point: Offset, snapshot: PockRefUiState): Offset {
                            val scale = snapshot.canvasScale.coerceAtLeast(0.0001f)
                            return Offset(
                                x = (point.x - size.width.toFloat() / 2f - snapshot.canvasOffset.x) / scale,
                                y = (point.y - size.height.toFloat() / 2f - snapshot.canvasOffset.y) / scale,
                            )
                        }
                        var transformStarted = false
                        var previousDistance = 0f
                        var previousPanCentroid = Offset.Zero
                        fun handleMarkupCanvasTransform(changes: List<PointerInputChange>) {
                            val activeChanges = changes.filter { it.pressed }
                            if (activeChanges.size < 2) return
                            val p0 = activeChanges[0].position
                            val p1 = activeChanges[1].position
                            val centroid = (p0 + p1) / 2f
                            val distance = (p0 - p1).getDistance()
                            if (!transformStarted) {
                                previousDistance = distance
                                previousPanCentroid = centroid
                                transformStarted = true
                                return
                            }
                            val distanceDelta = if (previousDistance > 0f) distance / previousDistance else 1f
                            // Markup reserves one finger for editing; two fingers always navigate,
                            // independently of the normal canvas single-finger pan preference.
                            val panDelta = canvasPanDeltaForPointerCount(
                                pointerCount = activeChanges.size,
                                previousCentroid = previousPanCentroid,
                                centroid = centroid,
                                singleFingerPanEnabled = false,
                            )
                            if (panDelta != Offset.Zero) {
                                enqueueCanvasPan(panDelta)
                            }
                            if (
                                distanceDelta != 1f &&
                                isGestureEnabled(currentState.gestureToggles, GestureId.PINCH_ZOOM)
                            ) {
                                currentOnAction(PockRefAction.ZoomCanvas(distanceDelta, centroid))
                            }
                            previousDistance = distance
                            previousPanCentroid = centroid
                        }
                        val stylusGesture = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (!stylusGesture && firstEvent.changes.count { it.pressed } >= 2) {
                            handleMarkupCanvasTransform(firstEvent.changes)
                            firstEvent.changes.forEach { it.consume() }
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val changes = event.changes
                                if (changes.count { it.pressed } >= 2) {
                                    handleMarkupCanvasTransform(changes)
                                }
                                changes.forEach { it.consume() }
                                if (changes.none { it.pressed }) break
                            }
                            flushQueuedCanvasPan()
                            return@awaitEachGesture
                        }
                        currentOnAction(PockRefAction.BeginMarkupStroke(screenToMarkupWorld(downPos, s)))
                        var cancelled = false
                        var navigatingCanvas = false
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val changes = event.changes
                                if (navigatingCanvas) {
                                    if (changes.count { it.pressed } >= 2) {
                                        handleMarkupCanvasTransform(changes)
                                    }
                                    changes.forEach { it.consume() }
                                    if (changes.none { it.pressed }) break
                                    continue
                                }
                                if (!stylusGesture && changes.count { it.pressed } >= 2) {
                                    cancelled = true
                                    navigatingCanvas = true
                                    currentOnAction(PockRefAction.CancelMarkupStroke)
                                    handleMarkupCanvasTransform(changes)
                                    changes.forEach { it.consume() }
                                    if (changes.none { it.pressed }) break
                                    continue
                                }
                                val change = changes.firstOrNull { it.id == down.id }
                                // Compose delivers ACTION_CANCEL as an already-consumed release.
                                if (change == null || change.isConsumed) {
                                    cancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    currentOnAction(PockRefAction.UpdateMarkupStroke(
                                        screenToMarkupWorld(change.position, currentState)))
                                    change.consume()
                                    break
                                }
                                currentOnAction(
                                    PockRefAction.UpdateMarkupStroke(
                                        screenToMarkupWorld(change.position, currentState),
                                    ),
                                )
                                change.consume()
                            }
                            if (!navigatingCanvas) {
                                currentOnAction(
                                    if (cancelled) PockRefAction.CancelMarkupStroke
                                    else PockRefAction.CommitMarkupStroke,
                                )
                            }
                            flushQueuedCanvasPan()
                            completed = true
                        } finally {
                            if (!completed) currentOnAction(PockRefAction.CancelMarkupStroke)
                        }
                        return@awaitEachGesture
                    }
                    if (s.markupVisible) {
                        val hitText = hitTestMarkupText(
                            texts = s.markupTexts,
                            point = downPos,
                            centerOffset = Offset(size.width.toFloat() / 2f, size.height.toFloat() / 2f),
                            canvasOffset = s.canvasOffset,
                            canvasScale = s.canvasScale,
                        )
                        if (hitText != null) {
                            var draggingText = false
                            var previousTextDragPosition = downPos
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val changes = event.changes
                                if (changes.count { it.pressed } >= 2) {
                                    if (draggingText) currentOnAction(PockRefAction.EndSceneGesture)
                                    changes.forEach { it.consume() }
                                    return@awaitEachGesture
                                }
                                val change = changes.firstOrNull { it.id == down.id } ?: changes.firstOrNull()
                                if (change == null) {
                                    if (draggingText) currentOnAction(PockRefAction.EndSceneGesture)
                                    return@awaitEachGesture
                                }
                                if (!change.pressed) {
                                    change.consume()
                                    if (draggingText) {
                                        currentOnAction(PockRefAction.EndSceneGesture)
                                    } else {
                                        currentOnAction(PockRefAction.EditMarkupText(hitText.id))
                                    }
                                    return@awaitEachGesture
                                }
                                val totalDrag = change.position - downPos
                                if (!draggingText && totalDrag.getDistance() >= dragTapThresholdPx) {
                                    draggingText = true
                                    currentOnAction(PockRefAction.BeginSceneGesture)
                                }
                                if (draggingText) {
                                    val scale = currentState.canvasScale.coerceAtLeast(0.0001f)
                                    val deltaScreen = change.position - previousTextDragPosition
                                    previousTextDragPosition = change.position
                                    currentOnAction(
                                        PockRefAction.MoveMarkupText(
                                            textId = hitText.id,
                                            delta = Offset(deltaScreen.x / scale, deltaScreen.y / scale),
                                        ),
                                    )
                                    change.consume()
                                }
                            }
                        }
                    }
                    val hitId = hitTestImage(s, downPos, Size(size.width.toFloat(), size.height.toFloat()))

                    var activeDragImageId: String? = null
                    // Separate the "image the user tapped"
                    // identity from [activeDragImageId] so
                    // locked/global-lock images can still
                    // participate in tap and double-tap focus
                    // without arming image drag.
                    var tapTargetImageId: String? = null
                    var lassoActive = false
                    var lassoStart: Offset? = null
                    var boxSelectStart: Offset? = null
                    var boxSelectDragging = false
                    // V_NOW (4-corner scale handles):
                    // SINGLE-mode per-gesture state.
                    // Captured at down time when the
                    // finger lands inside
                    // [cornerHitRadiusPx] of one of the
                    // SINGLE selected image's 4 projected
                    // V_NOW (2026-06-10 single-image
                    // corner-handle v3): SINGLE and
                    // GROUP corner drags share the
                    // same [GroupCornerDragState]
                    // (1-element "group" for SINGLE,
                    // N-element for GROUP), so the
                    // separate [cornerDrag] variable
                    // was removed. The unified
                    // 1-element-group geometry
                    // (per-AABB scaling) is the only
                    // path that remains.
                    // V_NOW (2026-06-10 #3 corner-handle
                    // undo/redo atomicity): tracks whether
                    // the current down-to-up gesture opened
                    // a [BeginSceneGesture] transaction so
                    // the up handler can close it with
                    // [EndSceneGesture]. Independent of
                    // [groupCornerDrag]
                    // because the user might cancel the
                    // drag mid-gesture (e.g. drag off the
                    // canvas); in that case we still want
                    // to close the gesture so the
                    // [updateSceneForGesture] reducer
                    // doesn't leave [sceneGestureActive]
                    // stuck at `true` for the next
                    // gesture.
                    var cornerDragActive = false
                    // V_NOW (4-corner scale handles,
                    // SINGLE+GROUP unified path):
                    // per-gesture state for the corner
                    // drag. Captured at down time when
                    // the finger lands on one of the 4
                    // corners — of the image's rotated
                    // footprint AABB (SINGLE) or of
                    // the unioned AABB enclosing every
                    // selected image (GROUP). Move loop
                    // dispatches
                    // [PockRefAction.SetSelectedImagesScale]
                    // on every frame while non-null.
                    var groupCornerDrag: GroupCornerDragState? = null
                    val cornerHitRadiusPx = with(density) { CORNER_HIT_RADIUS_DP.dp.toPx() }
                    var rotationHandleDrag: RotationHandleDragState? = null
                    var rotationHandleDragActive = false
                    val rotationHandleOffsetPx = with(density) { ROTATION_HANDLE_OFFSET_DP.dp.toPx() }
                    val rotationHandleHitRadiusPx = with(density) { ROTATION_HANDLE_HIT_RADIUS_DP.dp.toPx() }
                    // V_NOW: long-press on a selected image in
                    // multi-select mode triggers the
                    // SelectionBottomSheet. Track whether the
                    // long-press has already fired for this
                    // gesture so it doesn't re-fire on
                    // subsequent move events.
                    var longPressFired = false

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

                    // V_NOW (4-corner scale handles, SINGLE
                    // + MULTI/BOX paths): if the finger lands
                    // inside [cornerHitRadiusPx] of one of the
                    // 4 projected corners of the SINGLE
                    // selected image (SINGLE mode) OR the 4
                    // corners of the AABB enclosing every
                    // selected image (MULTI/BOX mode), start a
                    // corner drag and skip every other down
                    // branch below (image drag, lasso,
                    // box-select, canvas pan, S Pen button
                    // binding). The corner drag is
                    // intentionally a single-finger gesture
                    // ONLY — a two-finger pinch in the same
                    // area still goes through the rotation
                    // path on release. Gating:
                    //   * exactly one finger down
                    //   * stylus button NOT held (S Pen
                    //     side button drives BOX/MULTI)
                    //   * a selection exists
                    //   * canvas mode is SINGLE for the
                    //     per-image path; MULTI/BOX for the
                    //     group-AABB path
                    //   * the target image(s) are NOT locked
                    // The [hits] list is computed ONCE here;
                    // the move loop uses
                    // [initialMoveScreen] /
                    // [initialAnchorScreen] from the
                    // captured state, not the projected
                    // corner set (which would re-derive
                    // from the already-mutated image each
                    // frame and accumulate drift).
                    if (firstEvent.changes.size == 1 && !stylusButtonDown) {
                        val cs2 = currentState
                        val sIds = cs2.selectedImageIds
                        val sId = cs2.selectedImageId
                        val hasGroupSelection = sIds.size >= 2
                        val hasSingleSelection = sId != null && sIds.size <= 1
                        // V_NOW (group corner-handle
                        // anchor fix v2): the screen-to-
                        // world conversion for the
                        // group-corner move corner's
                        // down-time world position.
                        val cs2Center = Offset(
                            size.width.toFloat() / 2f + cs2.canvasOffset.x,
                            size.height.toFloat() / 2f + cs2.canvasOffset.y,
                        )
                        // V_NOW (2026-06-10 single-image
                        // corner-handle v3): SINGLE and
                        // GROUP corner drags now share
                        // the same per-AABB geometry
                        // (see the [MULTI/BOX path]
                        // comment below + the
                        // [PockRefViewModelBatch.setSelectedImagesScale]
                        // reducer). The previous SINGLE
                        // path called a separate
                        // [computeCornerDragResult]
                        // helper that operated on the
                        // image's intrinsic width/height
                        // and rotation, but it gave
                        // incorrect results in practice
                        // (the dragged corner
                        // overshooted the finger and the
                        // diagonal anchor drifted on
                        // every frame, especially when
                        // the image was rotated). The
                        // fix is to use the same code
                        // path the multi-select uses:
                        // build a 1-element "group"
                        // whose AABB IS the image's
                        // rotated footprint AABB, and
                        // let the reducer apply
                        //   newAabb = pivot
                        //     + (initialAabb - pivot) * factor
                        // uniformly. The drag handler
                        // below captures the 1-element
                        // [GroupCornerDragState]; the
                        // existing move-handler branch
                        // `if (gcorner != null)` does
                        // the rest. Result: the SINGLE
                        // and GROUP paths behave
                        // identically (same per-AABB
                        // math), and the diagonal
                        // anchor stays pinned at the
                        // opposite corner.
                        val hasAnySelection =
                            sId != null || sIds.isNotEmpty()
                        // -------- SINGLE/GROUP shared
                        // path: per-image 4 corners for
                        // SINGLE, group AABB 4 corners
                        // for GROUP. Unified through
                        // [GroupCornerDragState] below.
                        if (hasAnySelection) {
                            // Build the per-frame image
                            // list and AABB. For SINGLE,
                            // it's a 1-element "group";
                            // for GROUP, the full
                            // multi-select union.
                            val cornerImages: List<com.pockref.app.model.SceneImage> =
                                if (hasSingleSelection) {
                                    cs2.images.filter { it.id == sId }
                                } else {
                                    cs2.images.filter { it.id in sIds }
                                }
                            // Refuse if EVERY member is
                            // locked. (SINGLE: that
                            // means the only selected
                            // image is locked. GROUP: no
                            // unlocked members — the
                            // group can't move / scale
                            // at all; the drag would
                            // no-op.)
                            val anyUnlocked =
                                cornerImages.any {
                                    !cs2.isImageLocked(it.id)
                                }
                            if (anyUnlocked) {
                                val rotationGeometry = if (hasSingleSelection) {
                                    val ct = cornerImages.first()
                                    projectImageRotationHandleToScreen(
                                        imageCenter = ct.center,
                                        imageWidth = ct.displaySize.width.toFloat() * ct.scale,
                                        imageHeight = ct.displaySize.height.toFloat() * ct.scale,
                                        rotationDegrees = ct.rotationDegrees,
                                        handleOffsetPx = rotationHandleOffsetPx,
                                        canvasScale = cs2.canvasScale,
                                        canvasOffset = cs2.canvasOffset,
                                        viewportWidth = size.width.toFloat(),
                                        viewportHeight = size.height.toFloat(),
                                    )
                                } else {
                                    projectGroupRotationHandleToScreen(
                                        groupAabb = groupSelectionAabb(cornerImages),
                                        handleOffsetPx = rotationHandleOffsetPx,
                                        canvasScale = cs2.canvasScale,
                                        canvasOffset = cs2.canvasOffset,
                                        viewportWidth = size.width.toFloat(),
                                        viewportHeight = size.height.toFloat(),
                                    )
                                }
                                if (hitTestRotationHandle(rotationGeometry, downPos, rotationHandleHitRadiusPx)) {
                                    val pivotWorld = if (hasSingleSelection) {
                                        cornerImages.first().center
                                    } else {
                                        groupSelectionAabb(cornerImages).center
                                    }
                                    val pivotScreen = Offset(
                                        x = cs2Center.x + pivotWorld.x * cs2.canvasScale,
                                        y = cs2Center.y + pivotWorld.y * cs2.canvasScale,
                                    )
                                    rotationHandleDrag = RotationHandleDragState(
                                        pivotScreen = pivotScreen,
                                        previousAngleRad = atan2(
                                            downPos.y - pivotScreen.y,
                                            downPos.x - pivotScreen.x,
                                        ),
                                    )
                                    currentOnAction(PockRefAction.BeginSceneGesture)
                                    rotationHandleDragActive = true
                                    selectionHdRotationActive = true
                                }

                                // V_NOW (2026-06-11 "旋转图片
                                // 无法用角点缩放" fix):
                                // for SINGLE, project the
                                // IMAGE's actual 4 rotated
                                // corners (not the AABB
                                // corners). The corner handles
                                // are rendered at the image's
                                // rotated corners (inside the
                                // translate+rotate block in
                                // [drawSceneImage]), so the
                                // hit-test must match those
                                // positions. The previous code
                                // used [rotatedFootprintAabb]
                                // + [projectGroupAabbCornersToScreen]
                                // which projects the AXIS-ALIGNED
                                // bounding box corners — those
                                // only coincide with the image's
                                // actual corners when
                                // rotationDegrees == 0.
                                // MULTI/BOX still uses the
                                // group AABB (the group chrome
                                // IS the AABB box).
                                if (rotationHandleDrag == null) {
                                    val gHitIdx: Int
                                    val gProjected: List<Offset>
                                    val cornerAabb: Rect
                                    if (hasSingleSelection) {
                                        val ct = cornerImages.first()
                                        val imgW = ct.displaySize.width.toFloat() * ct.scale
                                        val imgH = ct.displaySize.height.toFloat() * ct.scale
                                        gProjected = projectImageCornersToScreen(
                                            imageCenter = ct.center,
                                            imageWidth = imgW,
                                            imageHeight = imgH,
                                            rotationDegrees = ct.rotationDegrees,
                                            canvasScale = cs2.canvasScale,
                                            canvasOffset = cs2.canvasOffset,
                                            viewportWidth = size.width.toFloat(),
                                            viewportHeight = size.height.toFloat(),
                                        )
                                        gHitIdx = hitTestCornerHandle(
                                            projectedCorners = gProjected,
                                            screenPoint = downPos,
                                            hitRadiusPx = cornerHitRadiusPx,
                                        )
                                        cornerAabb = rotatedFootprintAabb(
                                            center = ct.center,
                                            displayW = ct.displaySize.width.toFloat(),
                                            displayH = ct.displaySize.height.toFloat(),
                                            scale = ct.scale,
                                            rotationDegrees = ct.rotationDegrees,
                                        )
                                    } else {
                                        cornerAabb = groupSelectionAabb(cornerImages).rect
                                        val gaabbObj = GroupAabb(
                                            rect = cornerAabb,
                                            center = Offset(
                                                (cornerAabb.left + cornerAabb.right) / 2f,
                                                (cornerAabb.top + cornerAabb.bottom) / 2f,
                                            ),
                                            width = cornerAabb.width,
                                            height = cornerAabb.height,
                                        )
                                        gProjected = projectGroupAabbCornersToScreen(
                                            groupAabb = gaabbObj,
                                            canvasScale = cs2.canvasScale,
                                            canvasOffset = cs2.canvasOffset,
                                            viewportWidth = size.width.toFloat(),
                                            viewportHeight = size.height.toFloat(),
                                        )
                                        gHitIdx = hitTestCornerHandle(
                                            projectedCorners = gProjected,
                                            screenPoint = downPos,
                                            hitRadiusPx = cornerHitRadiusPx,
                                        )
                                    }
                                    if (gHitIdx >= 0 && cornerAabb.width > 1f && cornerAabb.height > 1f) {
                                    val gAnchorScreen = gProjected[3 - gHitIdx]
                                    val gDiag = kotlin.math.hypot(
                                        gProjected[gHitIdx].x - gAnchorScreen.x,
                                        gProjected[gHitIdx].y - gAnchorScreen.y,
                                    )
                                    if (gDiag > 1f) {
                                        val gRect = cornerAabb
                                        val gPivotX = when (gHitIdx) {
                                            0, 2 -> gRect.right
                                            else -> gRect.left
                                        }
                                        val gPivotY = when (gHitIdx) {
                                            0, 1 -> gRect.bottom
                                            else -> gRect.top
                                        }
                                        val gImageAabbs =
                                            cornerImages
                                                .associate { img ->
                                                    img.id to
                                                        rotatedFootprintAabb(
                                                            center = img.center,
                                                            displayW = img.displaySize.width.toFloat(),
                                                            displayH = img.displaySize.height.toFloat(),
                                                            scale = img.scale,
                                                            rotationDegrees = img.rotationDegrees,
                                                        )
                                                }
                                        val gAnchorWorld = Offset(gPivotX, gPivotY)
                                        val gMoveWorld = Offset(
                                            (gProjected[gHitIdx].x - cs2Center.x) / cs2.canvasScale,
                                            (gProjected[gHitIdx].y - cs2Center.y) / cs2.canvasScale,
                                        )
                                        val gGroupCenterWorld = Offset(
                                            (gRect.left + gRect.right) / 2f,
                                            (gRect.top + gRect.bottom) / 2f,
                                        )
                                        // V_NOW (2026-06-11 "旋转图片角点缩放" fix):
                                        // for SINGLE, compute the image's ACTUAL
                                        // anchor corner world position (not the
                                        // AABB corner). The anchor is the opposite
                                        // corner from the one the user grabbed.
                                        val singleAnchor: Offset?
                                        val singleMove: Offset?
                                        val singleRot: Float
                                        val singleW: Float
                                        val singleH: Float
                                        val singleInitScale: Float
                                        val singleASignX: Int
                                        val singleASignY: Int
                                        if (hasSingleSelection) {
                                            val ct = cornerImages.first()
                                            singleRot = ct.rotationDegrees
                                            singleW = ct.displaySize.width.toFloat() * ct.scale
                                            singleH = ct.displaySize.height.toFloat() * ct.scale
                                            singleInitScale = ct.scale
                                            // gProjected contains the image's actual
                                            // rotated corners (from projectImageCornersToScreen).
                                            // Anchor is the opposite corner.
                                            singleAnchor = Offset(
                                                (gProjected[3 - gHitIdx].x - cs2Center.x) / cs2.canvasScale,
                                                (gProjected[3 - gHitIdx].y - cs2Center.y) / cs2.canvasScale,
                                            )
                                            singleMove = Offset(
                                                (gProjected[gHitIdx].x - cs2Center.x) / cs2.canvasScale,
                                                (gProjected[gHitIdx].y - cs2Center.y) / cs2.canvasScale,
                                            )
                                            // Anchor corner's offset-from-center sign
                                            // in local image space:
                                            //   TL(0)→(-1,-1), TR(1)→(+1,-1),
                                            //   BL(2)→(-1,+1), BR(3)→(+1,+1)
                                            val anchorIdx = 3 - gHitIdx
                                            singleASignX = if (anchorIdx == 1 || anchorIdx == 3) 1 else -1
                                            singleASignY = if (anchorIdx == 2 || anchorIdx == 3) 1 else -1
                                        } else {
                                            singleAnchor = null
                                            singleMove = null
                                            singleRot = 0f
                                            singleW = 0f
                                            singleH = 0f
                                            singleInitScale = 0f
                                            singleASignX = 1
                                            singleASignY = 1
                                        }
                                        groupCornerDrag = GroupCornerDragState(
                                            imageIds = cornerImages.map { it.id }.toSet(),
                                            initialScales = cornerImages
                                                .associate { it.id to it.scale },
                                            initialGroupDiagonalScreen = gDiag,
                                            initialMoveScreen = gProjected[gHitIdx],
                                            initialFingerOffsetFromHandle = downPos - gProjected[gHitIdx],
                                            initialAnchorScreen = gAnchorScreen,
                                            initialGroupCenterWorld = gGroupCenterWorld,
                                            initialCenters = cornerImages
                                                .associate { it.id to it.center },
                                            initialGroupAabb = gRect,
                                            initialImageAabbs = gImageAabbs,
                                            initialAnchorWorld = gAnchorWorld,
                                            initialMoveWorld = gMoveWorld,
                                            pivotX = gPivotX,
                                            pivotY = gPivotY,
                                            singleAnchorWorld = singleAnchor,
                                            singleMoveWorld = singleMove,
                                            singleRotationDegrees = singleRot,
                                            singleWorldWidth = singleW,
                                            singleWorldHeight = singleH,
                                            singleInitialScale = singleInitScale,
                                            singleAnchorSignX = singleASignX,
                                            singleAnchorSignY = singleASignY,
                                        )
                                        currentOnAction(PockRefAction.BeginSceneGesture)
                                        cornerDragActive = true
                                    }
                                }
                                }
                            }
                        }
                    }

                    if (rotationHandleDrag != null) {
                        // Rotation handle owns this gesture; the move loop below
                        // turns pointer angle deltas into TransformSelectedImage.
                    } else if (stylusButtonDown && groupCornerDrag == null) {
                        // Force BOX mode for this gesture. The down
                        // might have hit an image — we ignore the hit
                        // because the S Pen button is for selection,
                        // not image drag.
                        if (!s.boxSelectMode) {
                            currentOnAction(PockRefAction.SetSelectionMode(SelectionMode.BOX))
                        }
                        currentOnAction(PockRefAction.BeginBoxSelectAt(downPos))
                        boxSelectStart = downPos
                        boxSelectDragging = true
                    } else if (hitId != null) {
                        // V_NOW (2026-06-11 "全局锁定/冻结模式
                        // 双击聚焦" fix): the gate on
                        // [!s.globalImageLock] was removed.
                        // The previous guard skipped this
                        // entire branch when global lock was
                        // on, which meant [activeDragImageId]
                        // was never set — and the double-tap
                        // detection at pointer-up time
                        // (`if (activeDragImageId != null)`)
                        // was unreachable, so double-tap
                        // focus/unfocus silently no-op'd in
                        // global lock mode. Now we always
                        // enter this branch when the user
                        // tapped on an image, but inside we
                        // check [s.globalImageLock] to decide
                        // whether to arm the drag or just
                        // record the hit for double-tap.
                        if (s.globalImageLock) {
                            // Keep tap / double-tap focus on
                            // globally locked images, but do
                            // not arm single-finger canvas pan.
                            tapTargetImageId = hitId
                        } else if (s.boxSelectMode && hitId !in s.selectedImageIds && groupCornerDrag == null) {
                            currentOnAction(PockRefAction.BeginBoxSelectAt(downPos))
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
                            val moveEnabled = isGestureEnabled(s.gestureToggles, GestureId.MOVE_IMAGE)
                            val imageLocked = hitImage?.let { s.isImageLocked(it.id) } == true
                            when {
                                !imageLocked && moveEnabled -> {
                                    activeDragImageId = hitId
                                }
                            }
                        }
                    } else if (s.boxSelectMode && groupCornerDrag == null) {
                        // V_NOW: skip starting a new box-select
                        // when a corner drag is already active
                        // (the user grabbed a group corner handle
                        // which is on empty space). Without this
                        // guard, BeginBoxSelectAt clears
                        // selectedImageIds, destroying the group
                        // that the corner drag is operating on.
                        currentOnAction(PockRefAction.BeginBoxSelectAt(downPos))
                        boxSelectStart = downPos
                        boxSelectDragging = true
                    } else if (s.multiSelectMode && hitId != null) {
                        // V_NOW: only start lasso if the user
                        // touched an image in multi-select mode.
                        // Touching empty space should pan the
                        // canvas (same as single-select mode),
                        // not start a lasso — the user expects
                        // to be able to navigate the canvas in
                        // multi-select mode.
                        lassoActive = true
                        lassoStart = downPos
                    }

                    var transformMode = ImageTransformMode.NONE
                    // V_NOW (user feedback "P1-B4 mode 切换 cooldown"):
                    // once a two-finger gesture commits to
                    // SCALE / ROTATE, don't let it
                    // flip-flop to a different mode in the
                    // next [MODE_COOLDOWN_MS] ms. The
                    // motivation: a user is in the middle
                    // of a pinch-scale (committed) and their
                    // fingers naturally drift a few extra
                    // millimetres of vertical travel on frame
                    // 5-6. Without the cooldown the
                    // mode would flip mid-way
                    // and the user would see "the image I
                    // was scaling suddenly started rotating"
                    // with no clean way
                    // to recover. With the cooldown the
                    // committed mode sticks for the full
                    // window of the gesture. The value
                    // 500 ms was picked because the
                    // typical two-finger gesture on a
                    // tablet runs 800-1500 ms end-to-end, so
                    // 500 ms of "lock" leaves plenty of room
                    // for the user to finish the gesture
                    // before the next gesture starts.
                    var modeCommitTimeMs: Long = 0L
                    var previousDistance = 0f
                    var previousAngle = 0f
                    var previousPanCentroid = Offset.Zero
                    var initialZoom = 1f
                    var initialRotation = 0f
                    var transformStarted = false
                    var lockedCanvasNavigationStarted = false
                    var lockedCanvasInitialDistance = 0f
                    var lockedCanvasPinchZoomActive = false
                    var dragStarted = false
                    var totalDrag = Offset.Zero
                    var maxSingleFingerDistancePx = 0f
                    // Drag-to-edge auto-scroll handoff. The
                    // move loop is the authoritative source
                    // for the pointer position during this
                    // gesture; the auto-scroll worker (a
                    // [LaunchedEffect] at the
                    // [PockRefCanvas] level) just reads
                    // the latest value. Eligible only for
                    // image drag / box-select / lasso. Canvas
                    // pan is two-finger only and stays outside
                    // this single-pointer edge auto-scroll path.
                    lastPointerPosState.value = downPos
                    autoScrollEligibleState.value = false
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
                                (change.position - change.previousPosition).getDistance() > gestureMovedThresholdPx
                            }
                        }
                        if (peakPointerCount == 1) {
                            val distance = changes.firstOrNull { it.id == down.id }
                                ?.let { (it.position - downPos).getDistance() } ?: 0f
                            maxSingleFingerDistancePx = maxOf(maxSingleFingerDistancePx, distance)
                        }

                        if (
                            shouldSkipFrozenCanvasPointerWork(
                                canvasFrozen = currentState.lockCanvasFrozen,
                                hasPressedPointers = changes.any { it.pressed },
                            )
                        ) {
                            changes.forEach { it.consume() }
                            continue
                        }

                        val singleFingerPanEnabled = isGestureEnabled(
                            currentState.gestureToggles,
                            GestureId.SINGLE_FINGER_PAN,
                        )
                        if (
                            pointerCount == 1 &&
                            shouldUseLocalSoftLockedSingleFingerPan(
                                globalImageLock = currentState.globalImageLock,
                                canvasFrozen = currentState.lockCanvasFrozen,
                                singleFingerPanEnabled = singleFingerPanEnabled,
                            )
                        ) {
                            val change = changes.firstOrNull { it.id == down.id } ?: changes.firstOrNull()
                            if (change?.pressed == true) {
                                val panDelta = change.position - change.previousPosition
                                if (panDelta != Offset.Zero) {
                                    addCanvasPanPreview(panDelta)
                                    totalDrag += panDelta
                                    lastPointerPosState.value = change.position
                                    autoScrollEligibleState.value = false
                                    changes.forEach { it.consume() }
                                    continue
                                }
                            }
                        }

                        if (
                            pointerCount >= 2 &&
                            !lockedCanvasNavigationStarted &&
                            canvasPanPresentationOffset != null
                        ) {
                            commitCanvasPanPreview()
                        }

                        if (pointerCount >= 2) {
                            if (rotationHandleDragActive) {
                                currentOnAction(PockRefAction.EndSceneGesture)
                                rotationHandleDragActive = false
                                rotationHandleDrag = null
                                selectionHdRotationActive = false
                            }
                            if (activeDragImageId != null && dragStarted) {
                                currentOnAction(PockRefAction.CommitImageDrag)
                            } else if (activeDragImageId != null) {
                                currentOnAction(PockRefAction.CancelImageDrag)
                            }
                            activeDragImageId = null
                            tapTargetImageId = null
                            lassoActive = false

                            if (
                                pointerCount == 2 &&
                                shouldUseSoftLockedTwoFingerCanvasNavigation(
                                    globalImageLock = currentState.globalImageLock,
                                    canvasFrozen = currentState.lockCanvasFrozen,
                                )
                            ) {
                                val p0 = changes[0].position
                                val p1 = changes[1].position
                                val centroid = (p0 + p1) / 2f
                                val distance = (p0 - p1).getDistance()

                                if (!lockedCanvasNavigationStarted) {
                                    previousDistance = distance
                                    previousPanCentroid = centroid
                                    lockedCanvasInitialDistance = distance
                                    lockedCanvasNavigationStarted = true
                                    changes.forEach { it.consume() }
                                    continue
                                }

                                val panDelta = canvasPanDeltaForPointerCount(
                                    pointerCount = pointerCount,
                                    previousCentroid = previousPanCentroid,
                                    centroid = centroid,
                                    singleFingerPanEnabled = isGestureEnabled(
                                        currentState.gestureToggles,
                                        GestureId.SINGLE_FINGER_PAN,
                                    ),
                                )
                                if (
                                    !lockedCanvasPinchZoomActive &&
                                    hasCrossedCanvasPinchZoomThreshold(
                                        initialDistance = lockedCanvasInitialDistance,
                                        currentDistance = distance,
                                    )
                                ) {
                                    commitCanvasPanPreview()
                                    canvasPanPresentationOffset = null
                                    canvasPanLastCommittedOffset = null
                                    lockedCanvasPinchZoomActive = true
                                }
                                if (panDelta != Offset.Zero) {
                                    if (
                                        shouldUseLocalTwoFingerCanvasPan(
                                            canvasFrozen = currentState.lockCanvasFrozen,
                                            transformCommitted = lockedCanvasPinchZoomActive,
                                        )
                                    ) {
                                        addCanvasPanPreview(panDelta)
                                    } else {
                                        enqueueCanvasPan(panDelta)
                                    }
                                }
                                val distanceDelta = if (previousDistance > 0f) {
                                    distance / previousDistance
                                } else {
                                    1f
                                }
                                if (
                                    lockedCanvasPinchZoomActive &&
                                    distanceDelta != 1f &&
                                    isGestureEnabled(
                                        currentState.gestureToggles,
                                        GestureId.PINCH_ZOOM,
                                    )
                                ) {
                                    currentOnAction(PockRefAction.ZoomCanvas(distanceDelta, centroid))
                                }

                                previousDistance = distance
                                previousPanCentroid = centroid
                                changes.forEach { it.consume() }
                                continue
                            }
                            lockedCanvasNavigationStarted = false
                            lockedCanvasPinchZoomActive = false

                            // V_NOW (user spec "双指缩放选中图片"):
                            // Hit-test ONLY at the start of the
                            // two-finger gesture (before
                            // [transformStarted]). Once the gesture
                            // has committed to a mode (SCALE /
                            // ROTATE), we must NOT
                            // re-evaluate — otherwise a canvas
                            // zoom that pushes an image under a
                            // finger would hijack the gesture
                            // mid-stroke, switching from "zoom
                            // canvas" to "zoom image" without the
                            // user's intent.
                            if (
                                changes.size >= 2 &&
                                !transformStarted &&
                                shouldSelectImageForTwoFingerCanvasGesture(
                                    globalImageLock = currentState.globalImageLock,
                                )
                            ) {
                                val viewportSize = Size(
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                                val cs = currentState
                                // V_NOW (P1-B2): a fingertip on a
                                // touch screen has a 6-12 dp
                                // contact patch. When two
                                // fingers come down and a
                                // fingertip lands *just* on
                                // the visual edge of an
                                // image, the user almost
                                // never means "grab this
                                // image" — they mean "I'm
                                // pinching in the area
                                // around it". Shrink the
                                // multi-touch hit rectangle
                                // by 8 dp on every side
                                // (~0.5 mm on 340 dpi) so
                                // the edge zone falls into
                                // the empty area and
                                // doesn't accidentally
                                // replace the selection
                                // with the just-edged image.
                                val twoFingerHitInsetPx = with(density) { 8.dp.toPx() }
                                val hitP0 = hitTestImage(cs, changes[0].position, viewportSize, twoFingerHitInsetPx)
                                val hitP1 = hitTestImage(cs, changes[1].position, viewportSize, twoFingerHitInsetPx)
                                // Two fingers on different images → ignore
                                if (hitP0 != null && hitP1 != null && hitP0 != hitP1) {
                                    changes.forEach { it.consume() }
                                    continue
                                }
                                val nonSelectedHit = listOfNotNull(hitP0, hitP1)
                                    .firstOrNull { it !in cs.selectedImageIds }
                                if (nonSelectedHit != null) {
                                    currentOnAction(PockRefAction.SelectImage(nonSelectedHit))
                                    // V_NOW (SelectionBottomSheet
                                    // "弹不出来" 修复): also bump
                                    // the show-tick. The two-
                                    // finger down SelectImage
                                    // path is a meaningful
                                    // "user wants the sheet"
                                    // event, same as a single-
                                    // finger tap. The reducer
                                    // for SelectImage doesn't
                                    // bump the tick by itself
                                    // (we keep the bump
                                    // explicit at the
                                    // dispatch site so non-
                                    // user-action SelectImage
                                    // calls — e.g. scene
                                    // load — don't accidentally
                                    // re-raise the sheet).
                                    currentOnAction(PockRefAction.TickSelectionSheet)
                                    // V_NOW (P2-C3): flash the
                                    // newly-selected image so
                                    // the user gets visual
                                    // confirmation that the
                                    // two-finger-down hit
                                    // actually replaced the
                                    // selection. The flash
                                    // expires after 200ms
                                    // (renderer reads
                                    // `now - flash.atMs`
                                    // per frame).
                                    currentOnAction(PockRefAction.FlashSelection(nonSelectedHit))
                                }
                            }

                            val p0 = changes[0].position
                            val p1 = changes[1].position
                            val centroid = (p0 + p1) / 2f
                            val distance = (p0 - p1).getDistance()
                            val angle = atan2(p1.y - p0.y, p1.x - p0.x)

                            // V_NOW (2026-06-10 #4 frozen
                            // mode gesture dead-weight): when
                            // [lockCanvasFrozen] is on, the
                            // canvas refuses every canvas
                            // transform (the reducer guards
                            // on PanCanvas / ZoomCanvas /
                            // SetCanvasOffsetFromMinimap /
                            // SetCanvasZoom — see
                            // [PureRefViewModel] L590-654).
                            // The two-finger gesture still
                            // ran the full transform math
                            // here (centroid / distance /
                            // distanceDelta / angleDelta /
                            // mode commit / worldPivot),
                            // dispatched actions the reducer
                            // would reject, then looped for
                            // the next move event. The wasted
                            // work is small per-frame but
                            // continuous — on a SM-X810
                            // (340dpi, 120Hz pointer) the
                            // user feels the gesture "trying
                            // to do something" while the
                            // canvas visibly doesn't move,
                            // which reads as lag. Early-skip
                            // the entire transform block
                            // (including the mode-commit
                            // flip) when frozen — the
                            // two-finger hit-test / image
                            // select above (L7230-7299)
                            // already ran, so the user can
                            // still pick images while the
                            // canvas is frozen; we just
                            // don't burn CPU on a transform
                            // the reducer will throw away.
                            if (currentState.lockCanvasFrozen) {
                                previousDistance = distance
                                previousAngle = angle
                                previousPanCentroid = centroid
                                changes.forEach { it.consume() }
                                continue
                            }

                            if (!transformStarted) {
                                previousDistance = distance
                                previousAngle = angle
                                previousPanCentroid = centroid
                                initialZoom = 1f
                                initialRotation = 0f
                                transformStarted = true
                                transformMode = ImageTransformMode.NONE
                                currentOnAction(PockRefAction.BeginSceneGesture)
                                changes.forEach { it.consume() }
                                continue
                            }

                            val distanceDelta = if (previousDistance > 0f) distance / previousDistance else 1f
                            val angleDelta = angle - previousAngle
                            val panDelta = canvasPanDeltaForPointerCount(
                                pointerCount = pointerCount,
                                previousCentroid = previousPanCentroid,
                                centroid = centroid,
                                singleFingerPanEnabled = isGestureEnabled(
                                    currentState.gestureToggles,
                                    GestureId.SINGLE_FINGER_PAN,
                                ),
                            )
                            val normalizedAngleDelta = if (angleDelta > PI.toFloat()) angleDelta - 2f * PI.toFloat()
                                else if (angleDelta < -PI.toFloat()) angleDelta + 2f * PI.toFloat()
                                else angleDelta

                            initialZoom *= distanceDelta
                            initialRotation += normalizedAngleDelta

                            // Hoist [hasSelected] ABOVE the
                            // mode-decision block so the
                            // selection-aware routing can run on the
                            // same frame.
                            val hasSelected = currentState.selectedImageId != null

                            if (transformMode == ImageTransformMode.NONE) {
                                // V_NOW (P1-B4): skip the decision
                                // block entirely while inside the
                                // post-commit cooldown window.
                                val nowMs = System.currentTimeMillis()
                                val inCooldown = (nowMs - modeCommitTimeMs) < MODE_COOLDOWN_MS
                                if (!inCooldown) {
                                    val zoomDelta = abs(initialZoom - 1f) * 100f
                                    val rotationDelta = abs(initialRotation) * 180f / PI.toFloat()

                                    if (hasSelected) {
                                        // A2 path: SCALE / ROTATE
                                        // only.
                                        if (zoomDelta >= SCALE_THRESHOLD && zoomDelta >= rotationDelta) {
                                            transformMode = ImageTransformMode.SCALE
                                            modeCommitTimeMs = nowMs
                                        } else if (rotationDelta >= ROTATE_THRESHOLD) {
                                            transformMode = ImageTransformMode.ROTATE
                                            modeCommitTimeMs = nowMs
                                        }
                                    } else {
                                        // No-selection path: SCALE /
                                        // ROTATE priority chain.
                                        if (zoomDelta >= SCALE_THRESHOLD && zoomDelta >= rotationDelta) {
                                            transformMode = ImageTransformMode.SCALE
                                            modeCommitTimeMs = nowMs
                                        } else if (rotationDelta >= ROTATE_THRESHOLD) {
                                            transformMode = ImageTransformMode.ROTATE
                                            modeCommitTimeMs = nowMs
                                        }
                                    }
                                }
                            }

                            if (hasSelected && transformMode == ImageTransformMode.ROTATE) {
                                selectionHdRotationActive = true
                            }

                            val canvasOffsetForTransform = renderCanvasOffsetForLocalPan(
                                stateOffset = currentState.canvasOffset,
                                presentationOffset = canvasPanPresentationOffset,
                            )
                            if (
                                transformMode != ImageTransformMode.NONE &&
                                canvasPanPresentationOffset != null
                            ) {
                                commitCanvasPanPreview()
                                canvasPanPresentationOffset = null
                                canvasPanLastCommittedOffset = null
                            }
                            if (panDelta != Offset.Zero) {
                                if (
                                    shouldUseLocalTwoFingerCanvasPan(
                                        canvasFrozen = currentState.lockCanvasFrozen,
                                        transformCommitted = transformMode != ImageTransformMode.NONE,
                                    )
                                ) {
                                    addCanvasPanPreview(panDelta)
                                } else {
                                    enqueueCanvasPan(panDelta)
                                }
                            }

                            // V_NOW (user spec "双指缩放选中图片"):
                            // spec rule 4 — the two-finger CENTROID
                            // is the scale anchor (in scene / world
                            // coordinates). We convert the screen-
                            // space centroid [centroid] to world
                            // coordinates using the same formula
                            // [drawSceneImage] uses in reverse
                            // (`worldX = (screenX - centerOffset.x -
                            // canvasOffset.x) / canvasScale`). The
                            // reducer's "AABB fallback" handles the
                            // case where the centroid is so far from
                            // the selection that anchoring it would
                            // visibly snap the group.
                            val worldPivot: Offset? = if (hasSelected && (transformMode == ImageTransformMode.SCALE || transformMode == ImageTransformMode.ROTATE)) {
                                val cs = currentState
                                val vpSize = autoScrollViewportSize.value.let {
                                    if (it.width > 0f && it.height > 0f) it
                                    else Size(size.width.toFloat(), size.height.toFloat())
                                }
                                val centerOffset = Offset(vpSize.width / 2f, vpSize.height / 2f)
                                Offset(
                                    x = (centroid.x - centerOffset.x - canvasOffsetForTransform.x) / cs.canvasScale,
                                    y = (centroid.y - centerOffset.y - canvasOffsetForTransform.y) / cs.canvasScale,
                                )
                            } else null

                             when (transformMode) {
                                ImageTransformMode.SCALE -> {
                                    // V_NOW (2026-06-10 user spec
                                    // "双指缩放画布"): the
                                    // SCALE branch fires on
                                    // `distanceDelta != 1f`
                                    // (i.e. a pinch). The image
                                    // scale path is intentionally
                                    // a no-op (4 corner handles
                                    // are the only way to scale
                                    // an image); the canvas is
                                    // zoomed via
                                    // [PockRefAction.ZoomCanvas].
                                    //
                                    // The [hasSelected] gate was
                                    // removed because it
                                    // contradicted the user spec
                                    // "双指在碰到图片时无法缩放
                                    // 画布" — when the user has
                                    // a selection but starts a
                                    // pinch whose centroid lands
                                    // ON the selected image, the
                                    // gesture should still zoom
                                    // the canvas. Previously the
                                    // gate silently dropped the
                                    // gesture (no canvas zoom,
                                    // no image scale), which
                                    // read as "pinch doesn't
                                    // work" exactly in the
                                    // case the user wanted it
                                    // most. The image is still
                                    // safe (4-corner handles are
                                    // the only way to scale
                                    // it).
                                    val pinchZoomEnabled = isGestureEnabled(
                                        currentState.gestureToggles,
                                        GestureId.PINCH_ZOOM,
                                    )
                                    if (distanceDelta != 1f && pinchZoomEnabled) {
                                        currentOnAction(
                                            PockRefAction.ZoomCanvas(
                                                distanceDelta,
                                                centroid,
                                            ),
                                        )
                                    }
                                }
                                ImageTransformMode.ROTATE -> {
                                    // Two-finger ROTATE is a
                                    // per-image gesture ONLY
                                    // (canvas rotation is not
                                    // supported — canvas
                                    // orientation is fixed in
                                    // PockRef). When no image is
                                    // selected the gesture is a
                                    // no-op: the user spec says
                                    // "双指捏 → 画布缩放;图片
                                    // 自身不响应双指" which means
                                    // a no-selection pinch is
                                    // zoom-only, not rotate-only.
                                    val pinchRotateEnabled = isGestureEnabled(currentState.gestureToggles, GestureId.PINCH_ROTATE)
                                    if (hasSelected) {
                                        if (normalizedAngleDelta != 0f && pinchRotateEnabled) {
                                            val rotationDeg = (normalizedAngleDelta * 180f / PI).toFloat()
                                            // V_NOW: rotation shares the
                                            // world pivot so scale +
                                            // rotate in the same gesture
                                            // feel anchored to the same
                                            // point (the centroid). The
                                            // reducer still uses the AABB
                                            // for rotation math; the
                                            // [worldPivot] only affects
                                            // the scale component's
                                            // post-pan correction.
                                            currentOnAction(PockRefAction.TransformSelectedImage(1f, rotationDeg, Offset.Zero, worldPivot))
                                        }
                                    }
                                }
                                else -> { }
                            }

                            previousDistance = distance
                            previousAngle = angle
                            previousPanCentroid = centroid
                            changes.forEach { it.consume() }
                        } else if (pointerCount == 1) {
                            if (transformStarted) {
                                currentOnAction(PockRefAction.EndSceneGesture)
                                transformStarted = false
                                transformMode = ImageTransformMode.NONE
                                initialZoom = 1f
                                initialRotation = 0f
                                selectionHdRotationActive = false
                            }

                            val change = changes[0]

                            // V_NOW (4-corner scale handles,
                            // SINGLE+GROUP shared path): both
                            // modes now route through the
                            // 1-element "group" [GroupCornerDragState]
                            // (the SINGLE-path down handler
                            // builds a 1-element group; the
                            // GROUP-path down handler builds a
                            // N-element group; both use the
                            // same per-AABB
                            // [SetSelectedImagesScale] reducer
                            // in the move handler below).
                            // The legacy [cornerDrag] /
                            // [SetSelectedImageScale] /
                            // [computeCornerDragResult] path
                            // was deleted because the
                            // separate SINGLE geometry drifted
                            // the diagonal anchor (the moved
                            // corner overshooted the finger
                            // and the anchor "walked" on every
                            // frame, especially when the image
                            // was rotated). The unified path
                            // keeps the opposite corner pinned
                            // for SINGLE the same way it does
                            // for GROUP.
                            val rotationDrag = rotationHandleDrag
                            if (rotationDrag != null) {
                                val currentAngle = atan2(
                                    change.position.y - rotationDrag.pivotScreen.y,
                                    change.position.x - rotationDrag.pivotScreen.x,
                                )
                                val angleDelta = shortestAngleDelta(rotationDrag.previousAngleRad, currentAngle)
                                if (angleDelta != 0f) {
                                    currentOnAction(
                                        PockRefAction.TransformSelectedImage(
                                            scaleChange = 1f,
                                            rotationChange = (angleDelta * 180f / PI).toFloat(),
                                            pan = Offset.Zero,
                                            worldPivot = null,
                                        ),
                                    )
                                    rotationHandleDrag = rotationDrag.copy(previousAngleRad = currentAngle)
                                }
                                change.consume()
                                lastPointerPosState.value = change.position
                                autoScrollEligibleState.value = false
                                continue
                            }

                            val gcorner = groupCornerDrag
                            if (gcorner != null) {
                                val cs2 = currentState
                                val cs2Center = Offset(
                                    size.width.toFloat() / 2f + cs2.canvasOffset.x,
                                    size.height.toFloat() / 2f + cs2.canvasOffset.y,
                                )
                                val currentMoveScreen = cornerHandleMovePointForFinger(
                                    fingerPosition = change.position,
                                    initialFingerOffsetFromHandle = gcorner.initialFingerOffsetFromHandle,
                                )
                                val currentMoveWorld = Offset(
                                    (currentMoveScreen.x - cs2Center.x) / cs2.canvasScale,
                                    (currentMoveScreen.y - cs2Center.y) / cs2.canvasScale,
                                )
                                // V_NOW (2026-06-11 "旋转图片角点缩放" fix):
                                // for SINGLE, use [computeCornerDragResult]
                                // which inverts the rotation transform to
                                // pin the image's ACTUAL anchor corner.
                                // The AABB-scaling path only pins the AABB
                                // corner, which diverges from the image's
                                // actual corner when rotationDegrees != 0.
                                val singleAnchor = gcorner.singleAnchorWorld
                                if (singleAnchor != null && gcorner.singleMoveWorld != null) {
                                    val (scaleFactor, newCenterWorld) = computeCornerDragResult(
                                        initialMoveWorld = gcorner.singleMoveWorld,
                                        initialAnchorWorld = singleAnchor,
                                        currentMoveWorld = currentMoveWorld,
                                        worldWidth = gcorner.singleWorldWidth,
                                        worldHeight = gcorner.singleWorldHeight,
                                        rotationDegrees = gcorner.singleRotationDegrees,
                                        anchorSignX = gcorner.singleAnchorSignX,
                                        anchorSignY = gcorner.singleAnchorSignY,
                                        minScaleFactor = 0.1f / gcorner.singleInitialScale,
                                        maxScaleFactor = 8.0f / gcorner.singleInitialScale,
                                    )
                                    val newScale = (gcorner.singleInitialScale * scaleFactor)
                                        .coerceIn(0.1f, 8.0f)
                                    currentOnAction(
                                        PockRefAction.SetSingleImageCornerScale(
                                            imageId = gcorner.imageIds.first(),
                                            newScale = newScale,
                                            newCenter = newCenterWorld,
                                        ),
                                    )
                                } else {
                                    // MULTI/BOX: AABB-scaling path
                                    val anchorWorld = gcorner.initialAnchorWorld
                                    val moveWorld = gcorner.initialMoveWorld
                                    val initialDiagWorld = kotlin.math.hypot(
                                        moveWorld.x - anchorWorld.x,
                                        moveWorld.y - anchorWorld.y,
                                    )
                                    val newDiagWorld = kotlin.math.hypot(
                                        currentMoveWorld.x - anchorWorld.x,
                                        currentMoveWorld.y - anchorWorld.y,
                                    )
                                    val rawRatio = if (initialDiagWorld > 0.0001f) {
                                        newDiagWorld / initialDiagWorld
                                    } else {
                                        1f
                                    }
                                    val factor = if (kotlin.math.abs(rawRatio - 1f) <= 0.03f) {
                                        1f
                                    } else {
                                        rawRatio.coerceIn(0.1f, 8.0f)
                                    }
                                    currentOnAction(
                                        PockRefAction.SetSelectedImagesScale(
                                            factor = factor,
                                            initialGroupAabb = gcorner.initialGroupAabb,
                                            initialImageAabbs = gcorner.initialImageAabbs,
                                            initialScales = gcorner.initialScales,
                                            pivotX = gcorner.pivotX,
                                            pivotY = gcorner.pivotY,
                                        ),
                                    )
                                }
                                change.consume()
                                lastPointerPosState.value = change.position
                                autoScrollEligibleState.value = false
                                continue
                            }

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

                            // V_NOW: long-press on a selected image
                            // in multi-select mode triggers the
                            // SelectionBottomSheet. The user holds
                            // still on an already-selected image for
                            // ≥ 500ms without dragging → pop the
                            // sheet. Long-pressing an unselected
                            // image does NOT trigger the sheet.
                            if (!longPressFired && !dragStarted && !transformStarted &&
                                hitId != null && hitId in currentState.selectedImageIds &&
                                (currentState.multiSelectMode || currentState.boxSelectMode) &&
                                totalDrag.getDistance() < tapMaxDragPx &&
                                (System.currentTimeMillis() - downTime) >= 500L
                            ) {
                                longPressFired = true
                                currentOnAction(PockRefAction.TickSelectionSheet)
                            }

                            val dragId = activeDragImageId
                            if (dragId != null) {
                                if (!dragStarted && maxSingleFingerDistancePx >= tapMaxDragPx) {
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
                                currentOnAction(PockRefAction.UpdateBoxSelectRect(RectF(left, top, right, bottom), cur))
                            } else if (lassoActive) {
                                lassoStart = lassoStart?.let { ls ->
                                    Offset(
                                        minOf(ls.x, change.position.x),
                                        minOf(ls.y, change.position.y),
                                    )
                                } ?: change.position
                            } else {
                                val panDelta = canvasPanDeltaForPointerCount(
                                    pointerCount = 1,
                                    previousCentroid = change.previousPosition,
                                    centroid = change.position,
                                    singleFingerPanEnabled = isGestureEnabled(
                                        currentState.gestureToggles,
                                        GestureId.SINGLE_FINGER_PAN,
                                    ),
                                )
                                if (panDelta != Offset.Zero) {
                                    enqueueCanvasPan(panDelta)
                                }
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
                    commitCanvasPanPreview()
                    flushQueuedCanvasPan()

                    if (transformStarted) {
                        currentOnAction(PockRefAction.EndSceneGesture)
                        transformStarted = false
                        transformMode = ImageTransformMode.NONE
                        initialZoom = 1f
                        initialRotation = 0f
                        selectionHdRotationActive = false
                    }

                    // V_NOW (2026-06-10 #3 corner-handle
                    // undo/redo atomicity): close the
                    // corner-drag scene gesture opened in
                    // the down handler so the reducer's
                    // [endSceneGesture] flushes ONE
                    // before-vs-final diff to the undo
                    // stack (see [beginSceneGesture] /
                    // [updateSceneForGesture]). Reset
                    // [cornerDrag] / [groupCornerDrag] so
                    // the next gesture starts clean. Guard
                    // on [cornerDragActive] rather than
                    // [cornerDrag] / [groupCornerDrag]
                    // because the gesture may have been
                    // cancelled mid-stroke (finger lifted
                    // off the canvas before the move loop
                    // could run); we still want to close
                    // the gesture so [sceneGestureActive]
                    // doesn't stay stuck at `true` for the
                    // next down.
                    if (cornerDragActive) {
                        currentOnAction(PockRefAction.EndSceneGesture)
                        cornerDragActive = false
                        groupCornerDrag = null
                    }

                    if (rotationHandleDragActive) {
                        currentOnAction(PockRefAction.EndSceneGesture)
                        rotationHandleDragActive = false
                        rotationHandleDrag = null
                        selectionHdRotationActive = false
                    }

                    // V_NOW (2026-06-10 #3 corner-handle
                    // anchor pinning user spec "单指拖 corner
                    // handle 后 pin 释放, canvas zoom 没回正"):
                    // when the user is in [fittedImageId]
                    // state (entered by double-tap → canvas is
                    // zoomed-in to fill the viewport with this
                    // single image) and then drags a corner
                    // handle to resize, the corner drag mutates
                    // the image's natural extent but leaves the
                    // canvas in the zoomed-in state. The user
                    // expects the canvas to unfocus (restore
                    // [preFitCanvasScale]) as soon as the corner
                    // drag releases — "pin 释放, canvas zoom
                    // 回正" — instead of requiring a second
                    // double-tap to manually unfocus.
                    //
                    // We re-dispatch
                    // [PockRefAction.FitImageToScreen] for the
                    // same [imageId] the corner drag was
                    // operating on. The reducer at
                    // [com.pockref.app.ui.PureRefViewModelTransform.fitImageToScreen]
                    // detects the matching [fittedImageId] and
                    // restores [preFitCanvasScale] /
                    // [preFitCanvasOffset] instead of zooming
                    // in again — see L1856-1864 there for the
                    // toggle path.
                    //
                    // Only the SINGLE corner-drag path is
                    // unwrapped here. The MULTI/BOX
                    // [groupCornerDrag] path can't reach this
                    // state — [FitImageToScreen] is per-image,
                    // and the reducer refuses to enter
                    // fittedImageId when there's more than one
                    // selected image (the focus gesture in
                    // [PockRefApp] L7692-7707 short-circuits on
                    // [multiSelectMode] / [boxSelectMode]).
                    // V_NOW (2026-06-10 single-image
                    // corner-handle v3): SINGLE now
                    // uses the same 1-element
                    // [GroupCornerDragState] as GROUP;
                    // we read the SINGLE image id from
                    // [groupCornerDrag.imageIds] (it's
                    // a 1-element set for SINGLE) so the
                    // auto-unzoom on release still
                    // works.
                    val cornerDragImageId = groupCornerDrag?.imageIds?.firstOrNull()
                    val fittedId = currentState.fittedImageId
                    if (cornerDragImageId != null && fittedId == cornerDragImageId) {
                        currentOnAction(PockRefAction.FitImageToScreen(cornerDragImageId))
                    }

                    val upTime = System.currentTimeMillis()
                    val isTap = isSingleFingerCanvasTap(
                        peakPointerCount = peakPointerCount,
                        dragStarted = dragStarted,
                        transformStarted = transformStarted,
                        durationMs = upTime - downTime,
                        maxDistancePx = maxSingleFingerDistancePx,
                        tapMaxDragPx = tapMaxDragPx,
                    )
                    if (hitId != null && !isTap && !dragStarted && peakPointerCount == 1) {
                        Log.d(
                            "PockRefTouch",
                            "image tap rejected duration=${upTime - downTime}ms " +
                                "maxDistanceDp=${maxSingleFingerDistancePx / density} " +
                                "transform=$transformStarted",
                        )
                    }

                    // V_NOW (2026-06-19 user spec "开启全局锁定
                    // 和冻结模式后,点一下应用界面,右上角锁定
                    // 按钮也会隐藏起来,再点一下会显示出来"):
                    // when the user taps the canvas in any
                    // lock-engaged state (soft or hard mode),
                    // toggle the lock button's visibility.
                    // This was previously implemented as a
                    // separate [Box(matchParentSize)] overlay
                    // with its own [pointerInput], but that
                    // approach broke canvas pan/zoom on the
                    // SM-X810 (the sibling pointerInput
                    // interfered with the canvas's gesture
                    // loop regardless of PointerEventPass).
                    // Moving the toggle into the canvas's own
                    // gesture loop avoids the sibling entirely.
                    // The toggle fires for ANY tap on the
                    // canvas (empty space or on an image) —
                    // the user's mental model is "tap
                    // anywhere to toggle the button".
                    if (isTap && (currentState.globalImageLock || currentState.lockCanvasFrozen) && currentState.images.isNotEmpty()) {
                        currentOnAction(PockRefAction.ToggleLockButtonVisibility)
                    }

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

                    // V_NOW (2026-06-11): combine
                    // [activeDragImageId] (the normal
                    // "image drag" tap) and
                    // [tapTargetImageId] (the global-lock
                    // "image is view-only but tap should
                    // still double-tap focus" tap) into a
                    // single [tappedId] so the
                    // double-tap-detection / fit-on-screen
                    // block below runs for both cases.
                    val effectiveTapId: String? = when {
                        activeDragImageId != null -> activeDragImageId
                        tapTargetImageId != null -> tapTargetImageId
                        else -> null
                    }
                    if (effectiveTapId != null) {
                        if (activeDragImageId != null && dragStarted) {
                            val draggedIds = currentState.transientDragImageIds
                            val targetGroup = groupDropTargetAt(
                                state = currentState,
                                screenPoint = downPos + totalDrag,
                                viewportSize = Size(size.width.toFloat(), size.height.toFloat()),
                                draggingImageIds = draggedIds,
                            )
                            if (targetGroup != null) {
                                currentOnAction(
                                    PockRefAction.AddImagesToGroupAfterDrag(
                                        groupId = targetGroup.id,
                                        imageIds = draggedIds,
                                    ),
                                )
                            } else {
                                currentOnAction(PockRefAction.CommitImageDrag)
                            }
                        } else if (isTap) {
                            val tappedId = effectiveTapId
                            // V_NOW: use [lastTapImageId] instead
                            // of [curState.selectedImageIds] for
                            // double-tap detection. The first tap
                            // dispatches [CycleImageUnderPoint]
                            // which selects the image in the VM,
                            // but [curState] is read via
                            // [rememberUpdatedState] which only
                            // refreshes on recomposition — the
                            // state may not have propagated by
                            // the time the second tap fires
                            // (< 300 ms later), so the old check
                            // [tappedId in curState.selectedImageIds]
                            // was always false on the second tap.
                            val isDoubleTapOnImage = tappedId == lastTapImageId &&
                                (upTime - lastTapTime) < DOUBLE_TAP_MAX_INTERVAL_MS &&
                                (lastTapPos - downPos).getDistance() < doubleTapMaxDistPx
                            // V_NOW: in multi-select / box-select
                            // mode, if the tapped image is already
                            // selected, [CycleImageUnderPoint] would
                            // toggle it (remove from selection),
                            // which is wrong when the user is about
                            // to double-tap to focus. Skip the
                            // cycle and just record the tap as a
                            // double-tap candidate.
                            val alreadySelected = tappedId in currentState.selectedImageIds
                            val isMultiSelect = currentState.multiSelectMode || currentState.boxSelectMode
                            if (isDoubleTapOnImage) {
                                // V_NOW: cancel the pending sheet
                                // show — the user double-tapped,
                                // so the intent is focus/unfocus,
                                // not sheet interaction.
                                pendingSheetJob?.cancel()
                                pendingSheetJob = null
                                Log.d(
                                    "ExportCanvas",
                                    "DT_FIT id=$tappedId " +
                                        "lastTapId=$lastTapImageId " +
                                        "dt=${upTime - lastTapTime}ms " +
                                        "currentFittedId=${currentState.fittedImageId}",
                                )
                                val group = if (currentState.editingGroupId == null) {
                                    currentState.groupContaining(tappedId)
                                } else null
                                if (group != null) {
                                    currentOnAction(PockRefAction.EnterGroup(group.id, tappedId))
                                } else {
                                    currentOnAction(PockRefAction.FitImageToScreen(tappedId))
                                }
                                currentOnAction(PockRefAction.DismissSelectionSheet)
                                lastTapTime = 0L
                                lastTapPos = Offset.Zero
                                lastTapImageId = ""
                            } else if (isMultiSelect && alreadySelected) {
                                // Don't toggle — just record for
                                // double-tap detection.
                                lastTapTime = upTime
                                lastTapPos = downPos
                                lastTapImageId = tappedId
                            } else {
                                // V_NOW: in single-select mode, call
                                // [CycleImageUnderPoint] with
                                // [showSheet = false] to select the
                                // image without immediately showing
                                // the sheet. Schedule a delayed
                                // [TickSelectionSheet] — if a
                                // second tap (double-tap) comes
                                // within the window, the job is
                                // cancelled and the focus action
                                // fires instead.
                                val isSingleSelect = !isMultiSelect
                                currentOnAction(PockRefAction.CycleImageUnderPoint(
                                    downPos,
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    showSheet = !isSingleSelect,
                                ))
                                if (isSingleSelect) {
                                    // V_NOW (2026-06-08 "连续弹出
                                    // 两次" 修复): debounce against
                                    // [lastSheetShowMs] so a
                                    // confused "tap-tap" within
                                    // [SHEET_SHOW_DEBOUNCE_MS] of
                                    // a previous tap doesn't
                                    // dispatch a second
                                    // [TickSelectionSheet]. See
                                    // [SHEET_SHOW_DEBOUNCE_MS]
                                    // for the full rationale.
                                    val now = System.currentTimeMillis()
                                    if (now - lastSheetShowMs >= SHEET_SHOW_DEBOUNCE_MS) {
                                        pendingSheetJob?.cancel()
                                        pendingSheetJob = tapScope.launch {
                                            kotlinx.coroutines.delay(SINGLE_TAP_SHEET_DELAY_MS)
                                            currentOnAction(PockRefAction.TickSelectionSheet)
                                            // Stamp AFTER the
                                            // dispatch so the
                                            // 800ms window
                                            // measures "time
                                            // since sheet was
                                            // last shown", not
                                            // "time since job
                                            // was scheduled".
                                            lastSheetShowMs = System.currentTimeMillis()
                                        }
                                    } else {
                                        // Within the debounce
                                        // window — the user
                                        // either saw the sheet
                                        // pop on the first tap
                                        // (and this second tap
                                        // was a mistaken
                                        // "didn't register"
                                        // reaction) or this is
                                        // a slow second-tap of
                                        // a double-tap (which
                                        // the [isDoubleTapOnImage]
                                        // branch above would
                                        // have caught). Either
                                        // way, no further
                                        // [TickSelectionSheet]
                                        // needed. We still
                                        // cancel the prior job
                                        // because it might
                                        // still be pending
                                        // (e.g. debounce
                                        // triggered on a
                                        // previous tap that
                                        // was outside the
                                        // window but the job
                                        // hadn't fired yet).
                                        pendingSheetJob?.cancel()
                                        pendingSheetJob = null
                                    }
                                }
                                lastTapTime = upTime
                                lastTapPos = downPos
                                lastTapImageId = tappedId
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
                        // V_NOW: same fix as the unlocked
                        // branch — use [lastTapImageId]
                        // instead of
                        // [curState.selectedImageIds] to
                        // avoid the state-propagation race.
                        val isDoubleTapOnImage = tappedId == lastTapImageId &&
                            (upTime - lastTapTime) < DOUBLE_TAP_MAX_INTERVAL_MS &&
                            (lastTapPos - downPos).getDistance() < doubleTapMaxDistPx
                        // V_NOW: same as the unlocked branch —
                        // in multi-select / box-select, if the
                        // tapped image is already selected, skip
                        // the cycle to avoid toggling it off.
                        val alreadySelected = tappedId in currentState.selectedImageIds
                        val isMultiSelect = currentState.multiSelectMode || currentState.boxSelectMode
                        if (isDoubleTapOnImage) {
                            // V_NOW: cancel pending sheet —
                            // double-tap means focus/unfocus.
                            pendingSheetJob?.cancel()
                            pendingSheetJob = null
                            val group = if (currentState.editingGroupId == null) {
                                currentState.groupContaining(tappedId)
                            } else null
                            if (group != null) {
                                currentOnAction(PockRefAction.EnterGroup(group.id, tappedId))
                            } else {
                                currentOnAction(PockRefAction.FitImageToScreen(tappedId))
                            }
                            currentOnAction(PockRefAction.DismissSelectionSheet)
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                            lastTapImageId = ""
                        } else if (isMultiSelect && alreadySelected) {
                            // Don't toggle — just record for
                            // double-tap detection.
                            lastTapTime = upTime
                            lastTapPos = downPos
                            lastTapImageId = tappedId
                        } else {
                            // V_NOW: same as the unlocked branch —
                            // in single-select mode, defer the
                            // sheet so a second tap (double-tap)
                            // can cancel it and focus instead.
                            val isSingleSelect = !isMultiSelect
                            if (isSingleSelect) {
                                currentOnAction(PockRefAction.CycleImageUnderPoint(
                                    downPos,
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    showSheet = false,
                                ))
                            } else {
                                currentOnAction(PockRefAction.CycleImageUnderPoint(
                                    downPos,
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    showSheet = true,
                                ))
                            }
                            if (isSingleSelect) {
                                        // V_NOW (2026-06-08): same
                                        // debounce as the
                                        // unlocked-image path
                                        // (L6656-6720). The
                                        // locked-image branch
                                        // (which goes through
                                        // [hitId] instead of
                                        // [activeDragImageId])
                                        // would otherwise be
                                        // susceptible to the
                                        // same "tap-tap" double-
                                        // pop bug.
                                        val now = System.currentTimeMillis()
                                        if (now - lastSheetShowMs >= SHEET_SHOW_DEBOUNCE_MS) {
                                            pendingSheetJob?.cancel()
                                            pendingSheetJob = tapScope.launch {
                                                kotlinx.coroutines.delay(SINGLE_TAP_SHEET_DELAY_MS)
                                                currentOnAction(PockRefAction.TickSelectionSheet)
                                                lastSheetShowMs = System.currentTimeMillis()
                                            }
                                        } else {
                                            pendingSheetJob?.cancel()
                                            pendingSheetJob = null
                                        }
                                    }
                            lastTapTime = upTime
                            lastTapPos = downPos
                            lastTapImageId = tappedId
                        }
                    } else if (isTap && !lassoActive && !boxSelectDragging) {
                        val editingGroupId = currentState.editingGroupId
                        if (editingGroupId != null) {
                            val group = currentState.groups.firstOrNull { it.id == editingGroupId }
                            val members = currentState.images.filter { image ->
                                group?.imageIds?.contains(image.id) == true
                            }
                            val scale = currentState.canvasScale.coerceAtLeast(0.001f)
                            val canvasPoint = Offset(
                                x = (downPos.x - size.width / 2f - currentState.canvasOffset.x) / scale,
                                y = (downPos.y - size.height / 2f - currentState.canvasOffset.y) / scale,
                            )
                            val bounds = group?.canvasBounds(
                                currentState.images,
                                GROUP_CANVAS_BOUNDS_PADDING,
                            )
                            val insideGroup = bounds?.contains(canvasPoint.x, canvasPoint.y) == true
                            currentOnAction(
                                if (insideGroup) PockRefAction.ClearGroupSelection
                                else PockRefAction.ExitGroup,
                            )
                            lastTapTime = 0L
                            lastTapPos = Offset.Zero
                            lastEmptyTapTime = 0L
                            lastEmptyTapPos = Offset.Zero
                        } else {
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
                                (lastEmptyTapPos - downPos).getDistance() < doubleTapMaxDistPx
                            if (isDoubleTapOnEmpty && isGestureEnabled(currentState.gestureToggles, GestureId.DOUBLE_TAP_EMPTY)) {
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
                        peakPointerCount >= 2 &&
                        shouldAllowMultiFingerHistoryGesture(
                            globalImageLock = currentState.globalImageLock,
                            canvasFrozen = currentState.lockCanvasFrozen,
                        )
                    ) {
                        when (peakPointerCount) {
                            2 -> {
                                // 2-finger tap = undo. Use the
                                // same 400ms / 20px gap as the
                                // other double-tap paths so the
                                // cadence feels consistent.
                                if (isGestureEnabled(currentState.gestureToggles, GestureId.TWO_FINGER_TAP)) {
                                    currentOnAction(PockRefAction.Undo)
                                }
                            }
                            3 -> {
                                // 3-finger tap = redo. (The
                                // previous "3-finger double-tap
                                // → reset transform" behaviour
                                // was removed in 2026-06; use
                                // the toolbar "复原" button on
                                // a single image instead.)
                                if (isGestureEnabled(currentState.gestureToggles, GestureId.THREE_FINGER_TAP)) {
                                    currentOnAction(PockRefAction.Redo)
                                }
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
        val renderCanvasOffset = renderCanvasOffsetForLocalPan(
            stateOffset = state.canvasOffset,
            presentationOffset = canvasPanPresentationOffset,
        )

        // Viewport cull: compute the world-space visible rect once per draw
        // pass, then skip images whose AABB does not intersect it. The
        // 200px screen-space buffer (see [VIEWPORT_BUFFER_PX]) prevents
        // edge flicker on fast pans. Items that are being dragged (transient
        // drag), selected, or being box-select-previewed are always drawn
        // even if they would otherwise be culled — otherwise the selection
        // chrome and the box-select feedback could pop out at the edges.
        val viewRect = worldViewportRect(
            canvasOffset = renderCanvasOffset,
            canvasScale = state.canvasScale,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat(),
        )

        val groupByFirstMember = state.groups.mapNotNull { group ->
            val members = state.images.filter { it.id in group.imageIds }
            members.minByOrNull { it.zIndex }?.id?.let { it to group }
        }.toMap()

        sortedImages.forEach { image ->
            groupByFirstMember[image.id]?.let { group ->
                val members = state.images.filter { it.id in group.imageIds }
                drawSceneGroupContainer(
                    group = group,
                    images = members,
                    isSelected = false,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = renderCanvasOffset,
                    transientDragIds = state.transientDragImageIds,
                    transientDragDelta = state.transientDragDelta,
                    drawBackground = true,
                    drawOutline = false,
                )
            }
            if (image.id !in forcedDrawIds) {
                val aabb = imageWorldAabb(image)
                if (!rectsIntersect(aabb, viewRect)) return@forEach
            }
            val gifSource = gifPlaybackSources[image.id]
            val gifState = if (gifSource != null) {
                gifPlaybackStates[image.id] ?: GifPlaybackState()
            } else {
                null
            }
            val cached = bitmapCache[image.id]?.second
            val tiles = tileCache.filterKeys { it.imageId == image.id }
            if (cached != null || gifSource != null || tiles.isNotEmpty()) {
                drawSceneImage(
                    image = image,
                    bitmap = cached,
                    tiles = tiles,
                    gifPlaybackSource = gifSource,
                    gifPlaybackState = gifState,
                    isSelected = image.id in state.selectedImageIds,
                    isBoxSelectPreview = image.id in state.boxSelectPreviewIds,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = renderCanvasOffset,
                    transientDragIds = state.transientDragImageIds,
                    transientDragDelta = state.transientDragDelta,
                    posterizeLevels = state.posterizeLevelsFor(image.id),
                    posterizeCache = posterizeCache,
                    posterizeSourceToken = posterizeSourceToken(image),
                    bitmapDecodeLevel = bitmapCache[image.id]?.first ?: 0,
                    gifFrameBitmapCache = gifFrameBitmapCache,
                    // V_NOW (4-corner scale handles):
                    // per-image handles are only drawn
                    // in SINGLE mode. In MULTI/BOX the
                    // group-AABB chrome is drawn
                    // separately below the
                    // [sortedImages] loop.
                    selectedCount = state.selectedImageIds.size,
                    // V_NOW (P2-C3): pass the one-shot
                    // flash marker + the live alpha
                    // (pre-computed by [produceState]
                    // above so the Canvas re-draws
                    // every ~16ms while the 200ms
                    // flash is active). The draw
                    // lambda reads `flashAlphaNow` so
                    // each tick re-invalidates the
                    // draw and the curve plays out
                    // smoothly.
                    selectionFlash = state.selectionFlash,
                    flashAlpha = flashAlphaNow,
                )
            } else {
                drawImagePlaceholder(
                    image = image,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = renderCanvasOffset,
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
                    canvasOffset = renderCanvasOffset,
                )
            }
        }

        // Draw outlines after all members so the group affordance remains
        // visible even when a member covers its own edge.
        state.groups.forEach { group ->
            val members = state.images.filter { it.id in group.imageIds }
            if (members.isNotEmpty()) {
                drawSceneGroupContainer(
                    group = group,
                    images = members,
                    isSelected = group.id in state.selectedGroupIds ||
                        group.id == state.editingGroupId || group in state.selectedGroups,
                    centerOffset = centerOffset,
                    canvasScale = state.canvasScale,
                    canvasOffset = renderCanvasOffset,
                    transientDragIds = state.transientDragImageIds,
                    transientDragDelta = state.transientDragDelta,
                    drawBackground = false,
                    drawOutline = true,
                )
            }
        }

        // V_NOW (4-corner scale handles, MULTI/BOX
        // path): when 2+ images are selected, render
        // a single group-AABB chrome (one box + 4
        // corners) INSTEAD of per-image corners.
        // Drawn AFTER all images so the box /
        // corners sit on top of the member
        // bitmaps, and BEFORE the snap guides so a
        // snap guide triggered mid-drag still wins.
        if (state.selectedImageIds.size >= 2) {
            val groupImages = state.images.filter { it.id in state.selectedImageIds }
            if (groupImages.isNotEmpty()) {
                drawGroupSelectionChrome(
                    selectedImages = groupImages,
                    canvasScale = state.canvasScale,
                    canvasOffset = renderCanvasOffset,
                    centerOffset = centerOffset,
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
                canvasOffset = renderCanvasOffset,
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
                    canvasOffset = renderCanvasOffset,
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
                        canvasOffset = renderCanvasOffset,
                        imageCenter = target.center,
                    )
                }
            }
        }

        if (state.markupVisible) {
            val strokes = state.markupStrokes.associateBy { it.id }
            val texts = state.markupTexts.associateBy { it.id }
            (state.markupStrokes.map { it.zOrder to it.id } + state.markupTexts.map { it.zOrder to it.id })
                .sortedBy { it.first }.forEach { (_, id) ->
                    strokes[id]?.let { drawMarkupStroke(it,centerOffset,state.canvasScale,renderCanvasOffset) }
                    texts[id]?.let { drawMarkupText(it,centerOffset,state.canvasScale,renderCanvasOffset) }
                }
            state.draftMarkupStroke?.let { drawMarkupStroke(it,centerOffset,state.canvasScale,renderCanvasOffset) }
            if (state.markupModeActive) markupSelectionBounds(state)?.let { bounds ->
                drawRect(Color(0xFF2196F3),
                    Offset(worldXToScreen(bounds.left,centerOffset,renderCanvasOffset,state.canvasScale), worldYToScreen(bounds.top,centerOffset,renderCanvasOffset,state.canvasScale)),
                    Size(bounds.width.coerceAtLeast(1f)*state.canvasScale,bounds.height.coerceAtLeast(1f)*state.canvasScale),
                    style = Stroke(width=2f))
                if (state.markupTool == com.pockref.app.model.CanvasMarkupTool.SELECT) {
                    markupHandlePositions(state).forEach { (handle, point) ->
                        val screen = centerOffset + renderCanvasOffset + point*state.canvasScale
                        if (handle == MarkupHandle.ROTATE) drawLine(Color(0xFF2196F3),
                            centerOffset + renderCanvasOffset + Offset(bounds.center.x,bounds.top)*state.canvasScale, screen, 2f)
                        drawCircle(Color.White, 7f, screen)
                        drawCircle(Color(0xFF2196F3), 7f, screen, style=Stroke(width=2f))
                    }
                }
            }
            if (state.markupLassoPoints.size > 1) {
                val points = state.markupLassoPoints + state.markupLassoPoints.first()
                points.zipWithNext().forEach { (a,b) ->
                    drawLine(Color(0xFF2196F3),
                        centerOffset + renderCanvasOffset + a*state.canvasScale,
                        centerOffset + renderCanvasOffset + b*state.canvasScale, strokeWidth = 2f)
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

        if (
            selectedImagesForHd.isNotEmpty() &&
            selectionHdPlacement != null &&
            shouldShowOriginalResolutionControl(
                rotationActive = selectionHdRotationActive,
                containsAnimatedGif = selectedImagesForHd.any { image -> isGifMimeType(image.mimeType) },
            )
        ) {
            SelectionHdControl(
                state = originalResolutionStateFor(selectedImagesForHd.map { it.showsOriginalResolution }),
                onClick = {
                    onAction(PockRefAction.ToggleOriginalDisplaySelected)
                },
                rotationDegrees = selectionHdPlacement.rotationDegrees,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (selectionHdPlacement.center.x - selectionHdPlacement.controlWidthPx / 2f).roundToInt(),
                        y = (selectionHdPlacement.center.y - selectionHdPlacement.controlHeightPx / 2f).roundToInt(),
                    )
                },
            )
        }
    }
}

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

private fun DrawScope.drawMarkupStroke(
    stroke: CanvasMarkupStroke, centerOffset: Offset, canvasScale: Float, canvasOffset: Offset,
) {
    if (!stroke.isRenderable) return
    drawIntoCanvas { target ->
        val canvas = target.nativeCanvas
        val save = canvas.save()
        try {
            canvas.translate(centerOffset.x+canvasOffset.x, centerOffset.y+canvasOffset.y)
            canvas.scale(canvasScale,canvasScale)
            SharedMarkupRenderer.drawStroke(canvas,stroke)
        } finally { canvas.restoreToCount(save) }
    }
}

private fun DrawScope.drawMarkupText(
    item: CanvasMarkupText, centerOffset: Offset, canvasScale: Float, canvasOffset: Offset,
) {
    drawIntoCanvas { target ->
        val canvas = target.nativeCanvas
        val save = canvas.save()
        try {
            canvas.translate(centerOffset.x+canvasOffset.x, centerOffset.y+canvasOffset.y)
            canvas.scale(canvasScale,canvasScale)
            SharedMarkupRenderer.drawText(canvas,item)
        } finally { canvas.restoreToCount(save) }
    }
}

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

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path):
 * per-gesture state for a group-AABB corner drag.
 * Captured in the down handler when the finger lands
 * inside [CORNER_HIT_RADIUS_DP] of one of the 4
 * corners of the AABB enclosing every selected
 * image. The move handler reads this on every
 * pointer move to drive
 * [PockRefAction.SetSelectedImagesScale].
 *
 * The "members" snapshot ([initialScales]) is
 * captured at down time so the reducer can apply
 * `image.scale = initialScale * newScaleFactor`
 * for each image — the absolute form has no drift
 * over a long group drag.
 *
 * [initialGroupDiagonalScreen] is the screen-space
 * diagonal length of the group AABB at down time.
 * [initialMoveScreen] and [initialAnchorScreen]
 * are the two opposite corners that define the
 * diagonal (same convention as [CornerDragState]
 * for the SINGLE case). [initialGroupCenterWorld]
 * is the group AABB center in world coordinates at
 * down time, used as the pivot for re-centering
 * each member as the group scales.
 */
private data class GroupCornerDragState(
    val imageIds: Set<String>,
    val initialScales: Map<String, Float>,
    val initialGroupDiagonalScreen: Float,
    val initialMoveScreen: Offset,
    val initialFingerOffsetFromHandle: Offset,
    val initialAnchorScreen: Offset,
    val initialGroupCenterWorld: Offset,
    val initialCenters: Map<String, Offset>,
    // V_NOW (2026-06-10 group corner-handle anchor
    // fix v2): the down-time group AABB in WORLD
    // coords. The reducer applies
    //   newGroupAabb = pivot + (initialGroupAabb - pivot) * factor
    //   newImageAabb = pivot + (initialImageAabb - pivot) * factor
    // so the rendered group AABB (which is the
    // union of the per-image new AABBs) is
    // automatically pinned at the anchor corner
    // A = (pivotX, pivotY).
    val initialGroupAabb: Rect,
    // V_NOW: per-image rotated footprint AABBs at
    // down time, in WORLD coords. Keyed by image
    // id. These are the per-image "input boxes"
    // the new geometry scales by [factor] around
    // the pivot. Each one is a Rect(left, top,
    // right, bottom) covering the union of the
    // image's 4 rotated corners.
    val initialImageAabbs: Map<String, Rect>,
    // V_NOW: the world-space positions of the
    // anchor (A) and move (M) corners of the
    // group AABB at down time. The move handler
    // computes [factor] from these:
    //   factor = |currentMove_world - A| / |M - A|
    // — both in world space so the canvas scale
    // doesn't enter the ratio. (The old geometry
    // used screen-space diagonals + a screen-to-
    // world conversion on the resulting center;
    // the new geometry factors out the canvas
    // transform entirely by computing the ratio
    // in world space directly.)
    val initialAnchorWorld: Offset,
    val initialMoveWorld: Offset,
    // V_NOW: the (X, Y) pivot for the per-AABB
    // scaling. = (initialGroupAabb.left or
    // initialGroupAabb.right, initialGroupAabb.top
    // or initialGroupAabb.bottom) depending on
    // which corner the user grabbed. The caller
    // decides this from the hit-test
    // [gHitIdx] in the down handler.
    val pivotX: Float,
    val pivotY: Float,
    // V_NOW (2026-06-11 "旋转图片角点缩放" fix):
    // for SINGLE mode, the image's ACTUAL
    // anchor corner in world coords (not the
    // AABB corner). When non-null, the move
    // handler uses [computeCornerDragResult]
    // which inverts the rotation transform to
    // pin the actual corner, instead of the
    // AABB-scaling path. Null for MULTI/BOX
    // (group AABB chrome IS the AABB).
    val singleAnchorWorld: Offset? = null,
    val singleMoveWorld: Offset? = null,
    val singleRotationDegrees: Float = 0f,
    val singleWorldWidth: Float = 0f,
    val singleWorldHeight: Float = 0f,
    val singleInitialScale: Float = 0f,
    // V_NOW (2026-06-11 "旋转图片角点缩放跳位置" fix):
    // the anchor corner's offset-from-center sign
    // in local image space. TL→(-1,-1), TR→(+1,-1),
    // BL→(-1,+1), BR→(+1,+1). Passed to
    // [computeCornerDragResult] so the center formula
    // works for all 4 corners, not just BR.
    val singleAnchorSignX: Int = 1,
    val singleAnchorSignY: Int = 1,
)

/**
 * V_NOW (4-corner scale handles): the hit-test
 * radius for grabbing a corner handle. Picked at
 * 32dp so a fingertip landing near the white dot
 * still grabs the corner before image dragging wins.
 * Bigger than the visual handle (the white circle
 * is ~10dp radius = 20dp diameter), small enough
 * that a corner grab doesn't accidentally swallow
 * taps on the image interior (the 4 corners of a
 * 1000x1500dp image are > 800dp apart, so the hit
 * circles never overlap).
 */
private const val CORNER_HIT_RADIUS_DP = 32f
private const val ROTATION_HANDLE_OFFSET_DP = 32f
private const val ROTATION_HANDLE_HIT_RADIUS_DP = 24f

private fun DrawScope.drawRotationHandleChrome(
    geometry: RotationHandleGeometry,
    canvasScale: Float,
) {
    val strokePx = max(3f, canvasScale * 3f)
    val handleRadius = max(10f, canvasScale * 5f)
    val handleInnerRadius = max(4f, canvasScale * 2f)
    val handleColor = Color(0xFF6AD6FF)
    drawLine(
        color = Color(0x556AD6FF),
        start = geometry.anchor,
        end = geometry.handleCenter,
        strokeWidth = max(8f, canvasScale * 8f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = handleColor,
        start = geometry.anchor,
        end = geometry.handleCenter,
        strokeWidth = strokePx,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = Color.White,
        radius = handleRadius,
        center = geometry.handleCenter,
    )
    drawCircle(
        color = handleColor,
        radius = handleInnerRadius,
        center = geometry.handleCenter,
    )
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

private fun DrawScope.drawSceneGroupContainer(
    group: SceneGroup,
    images: List<SceneImage>,
    isSelected: Boolean,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    transientDragIds: Set<String> = emptySet(),
    transientDragDelta: Offset = Offset.Zero,
    drawBackground: Boolean = true,
    drawOutline: Boolean = true,
) {
    // Group bounds must follow the live drag presentation just like the
    // member images. The gesture does not write the final positions into
    // state until it ends, so using the committed positions here makes the
    // frame appear to lag behind a dragged group.
    val displayedImages = if (transientDragIds.isEmpty() || transientDragDelta == Offset.Zero) {
        images
    } else {
        images.map { image ->
            if (image.id in transientDragIds) {
                image.copy(center = image.center + transientDragDelta)
            } else {
                image
            }
        }
    }
    val bounds = group.canvasBounds(displayedImages, GROUP_CANVAS_BOUNDS_PADDING) ?: return
    val left = centerOffset.x + canvasOffset.x + bounds.left * canvasScale
    val top = centerOffset.y + canvasOffset.y + bounds.top * canvasScale
    val width = bounds.width() * canvasScale
    val height = bounds.height() * canvasScale
    // Keep the group frame square to match the iOS canvas affordance.
    val corner = CornerRadius.Zero
    val frameSize = Size(width, height)
    if (drawBackground && group.backgroundOpacity > 0f) {
        drawRoundRect(
            color = group.backgroundColor.copy(alpha = group.backgroundOpacity),
            topLeft = Offset(left, top),
            size = frameSize,
            cornerRadius = corner,
        )
    }
    if (drawOutline) {
        drawRoundRect(
            color = if (isSelected) Color(0xFFBDEBFF) else Color(0xFF90A4AE).copy(alpha = 0.72f),
            topLeft = Offset(left, top),
            size = frameSize,
            cornerRadius = corner,
            style = Stroke(width = if (isSelected) max(2.5f, canvasScale * 2.5f) else max(1.5f, canvasScale * 1.5f)),
        )

        // Keep the group discoverable even after the selection is cleared.
        // iOS uses the same always-present name affordance; a small light
        // capsule keeps the label legible over both light and dark boards.
        val name = group.name.trim()
        if (name.isNotEmpty()) {
            val textSize = 12.dp.toPx()
            val horizontalPadding = 6.dp.toPx()
            val verticalPadding = 3.dp.toPx()
            val gap = 4.dp.toPx()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color(0xFF23313D).toArgb()
                this.textSize = textSize
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val labelWidth = textPaint.measureText(name) + horizontalPadding * 2f
            val labelHeight = textSize + verticalPadding * 2f
            val labelTop = if (top >= labelHeight + gap) {
                top - labelHeight - gap
            } else {
                top + gap
            }
            val labelLeft = left.coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
            val labelRect = RectF(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight)
            drawRoundRect(
                color = Color.White.copy(alpha = if (isSelected) 0.92f else 0.78f),
                topLeft = Offset(labelRect.left, labelRect.top),
                size = Size(labelRect.width(), labelRect.height()),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    name,
                    labelRect.left + horizontalPadding,
                    labelRect.top + verticalPadding + textSize,
                    textPaint,
                )
            }
        }
    }
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
    bitmap: ImageBitmap?,
    tiles: Map<com.pockref.app.data.ImageTileKey, ImageBitmap>,
    gifPlaybackSource: GifPlaybackSource? = null,
    gifPlaybackState: GifPlaybackState? = null,
    isSelected: Boolean,
    isBoxSelectPreview: Boolean = false,
    centerOffset: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    transientDragIds: Set<String>,
    transientDragDelta: Offset,
    posterizeLevels: Int? = null,
    posterizeCache: PosterizeBitmapCache? = null,
    posterizeSourceToken: String = com.pockref.app.ui.posterizeSourceToken(image),
    bitmapDecodeLevel: Int = 0,
    gifFrameBitmapCache: GifFrameBitmapCache? = null,
    /**
     * V_NOW (4-corner scale handles): total number
     * of currently selected images. When 1, the
     * per-image 4-corner handles are drawn. When
     * 2+, per-image handles are SKIPPED — the
     * MULTI/BOX path draws a single group-AABB
     * chrome (see [drawGroupSelectionChrome]) so
     * 4N corner circles don't pile up when the
     * user has 5 images selected.
     */
    selectedCount: Int = 0,
    /**
     * V_NOW (P2-C3): if non-null and matching
     * [image.id], the renderer renders an extra
     * highlight rect with alpha = [flashAlpha]
     * (fade-in 0.1 → 0.5 over the first 100ms,
     * fade-out 0.5 → 0.0 over the next 100ms,
     * then 0 — curve computed by the caller via
     * [produceState] so the draw lambda
     * re-invalidates every ~16ms while the flash
     * is active). Null or non-matching means no
     * flash. The flash is applied AFTER the
     * selected-image strokes so it sits on top
     * of (and visually "punches through") the
     * existing chrome. See
     * [PockRefUiState.selectionFlash] for the
     * full rationale.
     */
    selectionFlash: com.pockref.app.model.SelectionFlash? = null,
    flashAlpha: Float = 0f,
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
                // Non-destructive crop: when cropRect is set,
                // only draw the cropped portion of the bitmap.
                // The display size has already been adjusted
                // to the crop's aspect ratio (see commitCrop),
                // so the cropped region fills the display
                // rect exactly.
                val crop = image.cropRect
                if (gifPlaybackSource != null && gifPlaybackState != null && crop == null) {
                    drawGifMovieFrame(
                        source = gifPlaybackSource,
                        playback = gifPlaybackState,
                        width = width,
                        height = height,
                        colorAdjust = image.colorAdjust,
                        imageId = image.id,
                        sourceToken = posterizeSourceToken,
                        posterizeLevels = posterizeLevels,
                        posterizeCache = posterizeCache,
                        frameBitmapCache = gifFrameBitmapCache,
                    )
                } else if (bitmap != null && crop != null) {
                    val srcW = bitmap.width
                    val srcH = bitmap.height
                    val srcOffset = androidx.compose.ui.unit.IntOffset(
                        (crop.left * srcW).toInt().coerceIn(0, srcW - 1),
                        (crop.top * srcH).toInt().coerceIn(0, srcH - 1),
                    )
                    val srcSize = androidx.compose.ui.unit.IntSize(
                        ((crop.right - crop.left) * srcW).toInt().coerceIn(1, srcW - srcOffset.x),
                        ((crop.bottom - crop.top) * srcH).toInt().coerceIn(1, srcH - srcOffset.y),
                    )
                    val sourceRect = android.graphics.Rect(
                        srcOffset.x,
                        srcOffset.y,
                        srcOffset.x + srcSize.width,
                        srcOffset.y + srcSize.height,
                    )
                    val destinationRect = android.graphics.RectF(0f, 0f, width, height)
                    val drawn = if (posterizeLevels != null && posterizeCache != null) {
                        val source = bitmap.asAndroidBitmap()
                        val request = PosterizeRequest(posterizeLevels, image.colorAdjust)
                        val key = PosterizeCacheKey(
                            imageId = image.id,
                            sourceToken = posterizeSourceToken,
                            decodeLevel = bitmapDecodeLevel,
                            tileToken = null,
                            levels = posterizeLevels,
                            brightnessBits = image.colorAdjust.brightness.toBits(),
                            contrastBits = image.colorAdjust.contrast.toBits(),
                            saturationBits = image.colorAdjust.saturation.toBits(),
                        )
                        val fallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            posterizeCache.getOrRequest(key, source, request)
                        } else null
                        var result = false
                        drawIntoCanvas { canvas ->
                            result = PosterizeRenderer.drawBitmap(
                                canvas = canvas.nativeCanvas,
                                source = source,
                                sourceRect = sourceRect,
                                destinationRect = destinationRect,
                                request = request,
                                cpuFallback = fallback,
                            )
                        }
                        result
                    } else false
                    if (!drawn) {
                        drawImage(
                            image = bitmap,
                            srcOffset = srcOffset,
                            srcSize = srcSize,
                            dstSize = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
                            colorFilter = buildColorFilter(image.colorAdjust),
                        )
                    }
                } else if (bitmap != null) {
                    val drawn = if (posterizeLevels != null && posterizeCache != null) {
                        val source = bitmap.asAndroidBitmap()
                        val request = PosterizeRequest(posterizeLevels, image.colorAdjust)
                        val sourceRect = android.graphics.Rect(0, 0, source.width, source.height)
                        val key = PosterizeCacheKey(
                            imageId = image.id,
                            sourceToken = posterizeSourceToken,
                            decodeLevel = bitmapDecodeLevel,
                            tileToken = null,
                            levels = posterizeLevels,
                            brightnessBits = image.colorAdjust.brightness.toBits(),
                            contrastBits = image.colorAdjust.contrast.toBits(),
                            saturationBits = image.colorAdjust.saturation.toBits(),
                        )
                        val fallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            posterizeCache.getOrRequest(key, source, request)
                        } else null
                        var result = false
                        drawIntoCanvas { canvas ->
                            result = PosterizeRenderer.drawBitmap(
                                canvas = canvas.nativeCanvas,
                                source = source,
                                sourceRect = sourceRect,
                                destinationRect = android.graphics.RectF(0f, 0f, width, height),
                                request = request,
                                cpuFallback = fallback,
                            )
                        }
                        result
                    } else false
                    if (!drawn) {
                        drawImage(
                            image = bitmap,
                            dstSize = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
                            colorFilter = buildColorFilter(image.colorAdjust),
                        )
                    }
                }
                if (tiles.isNotEmpty()) {
                    drawImageTiles(
                        image = image,
                        tiles = tiles,
                        width = width,
                        height = height,
                        posterizeLevels = posterizeLevels,
                        posterizeCache = posterizeCache,
                        posterizeSourceToken = posterizeSourceToken,
                    )
                }
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
                    // V_NOW (4-corner scale handles,
                    // SINGLE path): white circle +
                    // primary-tint center dot at
                    // each of the 4 image corners.
                    // Only drawn in SINGLE mode
                    // (`selectedCount < 2`); the
                    // MULTI/BOX path renders a
                    // single group-AABB box with
                    // its own 4 corner handles.
                    if (selectedCount < 2) {
                        val handleRadius = max(10f, canvasScale * 5f)
                        val handleInnerRadius = max(4f, canvasScale * 2f)
                        val handleCenterColor = Color(0xFF6AD6FF)
                        val cornerPositions = listOf(
                            Offset(0f, 0f),
                            Offset(width, 0f),
                            Offset(0f, height),
                            Offset(width, height),
                        )
                        cornerPositions.forEach { pos ->
                            drawCircle(
                                color = Color.White,
                                radius = handleRadius,
                                center = pos,
                            )
                            drawCircle(
                                color = handleCenterColor,
                                radius = handleInnerRadius,
                                center = pos,
                            )
                        }
                    }
                }
                // V_NOW (P2-C3): render the one-shot
                // selection-change flash on top of the
                // existing chrome. The alpha is
                // pre-computed by the caller (a
                // [produceState] in [PockRefCanvas])
                // and re-fed on every ~16ms tick so
                // the curve 0.1 → 0.5 → 0.0 plays
                // out smoothly over 200ms. The flash
                // overlays the [isSelected] stroke so
                // the user sees a clear visual
                // "pulse" when the selection was
                // just replaced.
                if (selectionFlash != null && selectionFlash.imageId == image.id && flashAlpha > 0f) {
                    val flashStroke = max(4f, canvasScale * 4f)
                    val flashGlow = max(12f, canvasScale * 12f)
                    drawRect(
                        color = Color(0xFF6AD6FF).copy(alpha = flashAlpha * 0.45f),
                        size = Size(width, height),
                        style = Stroke(width = flashGlow),
                    )
                    drawRect(
                        color = Color(0xFF6AD6FF).copy(alpha = flashAlpha),
                        size = Size(width, height),
                        style = Stroke(width = flashStroke),
                    )
	                }
	            }
	        }
	    }
    if (isSelected && selectedCount < 2) {
        drawRotationHandleChrome(
            geometry = projectImageRotationHandleToScreen(
                imageCenter = image.center + dragOffset,
                imageWidth = image.displaySize.width * image.scale,
                imageHeight = image.displaySize.height * image.scale,
                rotationDegrees = image.rotationDegrees,
                handleOffsetPx = ROTATION_HANDLE_OFFSET_DP.dp.toPx(),
                canvasScale = canvasScale,
                canvasOffset = canvasOffset,
                viewportWidth = size.width,
                viewportHeight = size.height,
            ),
            canvasScale = canvasScale,
        )
    }
}

private fun DrawScope.drawImageTiles(
    image: SceneImage,
    tiles: Map<com.pockref.app.data.ImageTileKey, ImageBitmap>,
    width: Float,
    height: Float,
    posterizeLevels: Int? = null,
    posterizeCache: PosterizeBitmapCache? = null,
    posterizeSourceToken: String = com.pockref.app.ui.posterizeSourceToken(image),
) {
    val crop = image.cropRect ?: CropRect()
    val imageWidth = image.intrinsicWidth.toFloat()
    val imageHeight = image.intrinsicHeight.toFloat()
    if (imageWidth <= 0f || imageHeight <= 0f ||
        crop.cropWidth <= 0f || crop.cropHeight <= 0f
    ) {
        return
    }

    val cropLeft = crop.left * imageWidth
    val cropTop = crop.top * imageHeight
    val cropRight = crop.right * imageWidth
    val cropBottom = crop.bottom * imageHeight
    val cropWidth = cropRight - cropLeft
    val cropHeight = cropBottom - cropTop

    tiles.forEach { (key, tile) ->
        val tileLeft = key.column * ImageTilePlanner.TILE_EDGE_PX.toFloat()
        val tileTop = key.row * ImageTilePlanner.TILE_EDGE_PX.toFloat()
        val tileRight = tileLeft + tile.width
        val tileBottom = tileTop + tile.height
        val sourceLeft = maxOf(tileLeft, cropLeft)
        val sourceTop = maxOf(tileTop, cropTop)
        val sourceRight = minOf(tileRight, cropRight)
        val sourceBottom = minOf(tileBottom, cropBottom)
        if (sourceRight <= sourceLeft || sourceBottom <= sourceTop) return@forEach

        val srcOffsetX = (sourceLeft - tileLeft).toInt().coerceIn(0, tile.width - 1)
        val srcOffsetY = (sourceTop - tileTop).toInt().coerceIn(0, tile.height - 1)
        val srcWidth = (sourceRight - sourceLeft).toInt().coerceIn(1, tile.width - srcOffsetX)
        val srcHeight = (sourceBottom - sourceTop).toInt().coerceIn(1, tile.height - srcOffsetY)
        val dstLeft = ((sourceLeft - cropLeft) / cropWidth * width).toInt()
        val dstTop = ((sourceTop - cropTop) / cropHeight * height).toInt()
        val dstWidth = ((sourceRight - sourceLeft) / cropWidth * width).toInt().coerceAtLeast(1)
        val dstHeight = ((sourceBottom - sourceTop) / cropHeight * height).toInt().coerceAtLeast(1)

        val drawn = if (posterizeLevels != null && posterizeCache != null) {
            val source = tile.asAndroidBitmap()
            val request = PosterizeRequest(posterizeLevels, image.colorAdjust)
            val key = PosterizeCacheKey(
                imageId = image.id,
                sourceToken = posterizeSourceToken,
                decodeLevel = 0,
                tileToken = "${key.column}:${key.row}",
                levels = posterizeLevels,
                brightnessBits = image.colorAdjust.brightness.toBits(),
                contrastBits = image.colorAdjust.contrast.toBits(),
                saturationBits = image.colorAdjust.saturation.toBits(),
            )
            val fallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                posterizeCache.getOrRequest(key, source, request)
            } else null
            var result = false
            drawIntoCanvas { canvas ->
                result = PosterizeRenderer.drawBitmap(
                    canvas = canvas.nativeCanvas,
                    source = source,
                    sourceRect = android.graphics.Rect(
                        srcOffsetX,
                        srcOffsetY,
                        srcOffsetX + srcWidth,
                        srcOffsetY + srcHeight,
                    ),
                    destinationRect = android.graphics.RectF(
                        dstLeft.toFloat(),
                        dstTop.toFloat(),
                        (dstLeft + dstWidth).toFloat(),
                        (dstTop + dstHeight).toFloat(),
                    ),
                    request = request,
                    cpuFallback = fallback,
                )
            }
            result
        } else false
        if (!drawn) {
            drawImage(
                image = tile,
                srcOffset = androidx.compose.ui.unit.IntOffset(srcOffsetX, srcOffsetY),
                srcSize = androidx.compose.ui.unit.IntSize(srcWidth, srcHeight),
                dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft, dstTop),
                dstSize = androidx.compose.ui.unit.IntSize(dstWidth, dstHeight),
                colorFilter = buildColorFilter(image.colorAdjust),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun DrawScope.drawGifMovieFrame(
    source: GifPlaybackSource,
    playback: GifPlaybackState,
    width: Float,
    height: Float,
    colorAdjust: ColorAdjust,
    imageId: String,
    sourceToken: String,
    posterizeLevels: Int? = null,
    posterizeCache: PosterizeBitmapCache? = null,
    frameBitmapCache: GifFrameBitmapCache? = null,
) {
    val movie = source.movie
    val movieWidth = movie.width()
    val movieHeight = movie.height()
    if (movieWidth <= 0 || movieHeight <= 0 || width <= 0f || height <= 0f) return

    movie.setTime(source.timeline.timeForFrame(playback.frameIndex))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = buildColorMatrix(colorAdjust)?.let { ColorMatrixColorFilter(it) }
    }
    if (posterizeLevels != null && posterizeCache != null && frameBitmapCache != null) {
        posterizeCache.retainGifFrame(imageId, playback.frameIndex)
        val frame = frameBitmapCache.obtain(imageId, movieWidth, movieHeight)
        val frameCanvas = android.graphics.Canvas(frame)
        frameCanvas.drawColor(
            android.graphics.Color.TRANSPARENT,
            android.graphics.PorterDuff.Mode.CLEAR,
        )
        movie.draw(frameCanvas, 0f, 0f, paint)
        val request = PosterizeRequest(posterizeLevels, colorAdjust)
        val key = PosterizeCacheKey(
            imageId = imageId,
            sourceToken = sourceToken,
            decodeLevel = 0,
            tileToken = "gif-frame:${playback.frameIndex}",
            levels = posterizeLevels,
            brightnessBits = colorAdjust.brightness.toBits(),
            contrastBits = colorAdjust.contrast.toBits(),
            saturationBits = colorAdjust.saturation.toBits(),
        )
        val fallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            posterizeCache.getOrRequest(key, frame, request, snapshotSource = true)
        } else null
        var drawn = false
        drawIntoCanvas { canvas ->
            drawn = PosterizeRenderer.drawBitmap(
                canvas = canvas.nativeCanvas,
                source = frame,
                sourceRect = android.graphics.Rect(0, 0, movieWidth, movieHeight),
                destinationRect = android.graphics.RectF(0f, 0f, width, height),
                request = request,
                cpuFallback = fallback,
            )
            if (!drawn) {
                val checkpoint = canvas.nativeCanvas.save()
                try {
                    canvas.nativeCanvas.scale(width / movieWidth.toFloat(), height / movieHeight.toFloat())
                    canvas.nativeCanvas.drawBitmap(frame, 0f, 0f, null)
                } finally {
                    canvas.nativeCanvas.restoreToCount(checkpoint)
                }
            }
        }
        return
    }
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val checkpoint = nativeCanvas.save()
        try {
            nativeCanvas.scale(width / movieWidth.toFloat(), height / movieHeight.toFloat())
            movie.draw(nativeCanvas, 0f, 0f, paint)
        } finally {
            nativeCanvas.restoreToCount(checkpoint)
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
    posterizeCache: PosterizeBitmapCache,
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
            posterizeLevels = state.posterizeLevelsFor(hoveredImage.id),
            posterizeCache = posterizeCache,
            posterizeSourceToken = posterizeSourceToken(hoveredImage),
            bitmapDecodeLevel = bitmapCache[hoveredImage.id]?.first ?: 0,
        )
    }
}

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path):
 * render the group-selection chrome — one
 * axis-aligned box around the unioned AABB of
 * every selected image, plus 4 white-with-primary-
 * dot corner handles at the box's 4 corners. Used
 * in place of the per-image chrome when
 * `selectedImageIds.size >= 2` so 4N corner
 * circles don't pile up on top of each other for
 * large selections.
 *
 * Geometry: the AABB is computed in world
 * (scene) coordinates by [groupSelectionAabb]
 * (each member's rotated footprint unioned into
 * a single rect), then projected to screen
 * pixels and drawn directly. The box stays
 * axis-aligned on screen even when individual
 * members are rotated — by design, since the
 * box is a SELECTION affordance (not a per-image
 * thing), the 4 corners need to be at stable,
 * easy-to-hit points for the corner-drag
 * gesture. Members can rotate freely inside the
 * box; the box re-unions on the next frame and
 * the corners follow.
 *
 * Visual: the box uses the SAME blue palette as
 * the SINGLE mode per-image selection
 * (`0xFF6AD6FF` stroke + `0x556AD6FF` glow), so
 * the two selection modes feel like the same
 * design system at different granularities.
 * Stroke widths match the per-image path
 * (`max(3f, canvasScale * 3f)` etc.) so a
 * 1-px-wide border and an N-image-wide border
 * look the same on screen at the same zoom.
 */
private fun DrawScope.drawGroupSelectionChrome(
    selectedImages: List<SceneImage>,
    canvasScale: Float,
    canvasOffset: Offset,
    centerOffset: Offset,
) {
    val aabb = groupSelectionAabb(selectedImages)
    if (aabb.width <= 0f || aabb.height <= 0f) return

    val centerX = centerOffset.x + canvasOffset.x
    val centerY = centerOffset.y + canvasOffset.y
    val left = centerX + aabb.rect.left * canvasScale
    val top = centerY + aabb.rect.top * canvasScale
    val right = centerX + aabb.rect.right * canvasScale
    val bottom = centerY + aabb.rect.bottom * canvasScale
    val widthPx = (right - left).coerceAtLeast(0f)
    val heightPx = (bottom - top).coerceAtLeast(0f)

    val strokePx = max(3f, canvasScale * 3f)
    val glow = max(8f, canvasScale * 8f)
    drawRect(
        color = Color(0x556AD6FF),
        topLeft = Offset(left, top),
        size = Size(widthPx, heightPx),
        style = Stroke(width = glow),
    )
    drawRect(
        color = Color(0xFF6AD6FF),
        topLeft = Offset(left, top),
        size = Size(widthPx, heightPx),
        style = Stroke(width = strokePx),
    )

    // 4 corner handles — same M3 pattern as
    // the per-image chrome (white outer +
    // primary-tint inner dot). Sizing is also
    // the same so a user moving between SINGLE
    // and MULTI modes gets identical-looking
    // handles.
    val handleRadius = max(10f, canvasScale * 5f)
    val handleInnerRadius = max(4f, canvasScale * 2f)
    val handleCenterColor = Color(0xFF6AD6FF)
    val cornerPositions = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom),
    )
    cornerPositions.forEach { pos ->
        drawCircle(
            color = Color.White,
            radius = handleRadius,
            center = pos,
        )
        drawCircle(
            color = handleCenterColor,
            radius = handleInnerRadius,
            center = pos,
        )
    }
    drawRotationHandleChrome(
        geometry = projectGroupRotationHandleToScreen(
            groupAabb = aabb,
            handleOffsetPx = ROTATION_HANDLE_OFFSET_DP.dp.toPx(),
            canvasScale = canvasScale,
            canvasOffset = canvasOffset,
            viewportWidth = size.width,
            viewportHeight = size.height,
        ),
        canvasScale = canvasScale,
    )
}

private fun DrawScope.drawHoverMagnifier(
    image: SceneImage,
    bitmap: ImageBitmap,
    imageScreenCenter: Offset,
    imageScreenWidth: Float,
    imageScreenHeight: Float,
    progress: Float,
    posterizeLevels: Int? = null,
    posterizeCache: PosterizeBitmapCache? = null,
    posterizeSourceToken: String = com.pockref.app.ui.posterizeSourceToken(image),
    bitmapDecodeLevel: Int = 0,
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
                    val drawn = if (posterizeLevels != null && posterizeCache != null) {
                        val source = bitmap.asAndroidBitmap()
                        val request = PosterizeRequest(posterizeLevels, image.colorAdjust)
                        val key = PosterizeCacheKey(
                            imageId = image.id,
                            sourceToken = posterizeSourceToken,
                            decodeLevel = bitmapDecodeLevel,
                            tileToken = "magnifier",
                            levels = posterizeLevels,
                            brightnessBits = image.colorAdjust.brightness.toBits(),
                            contrastBits = image.colorAdjust.contrast.toBits(),
                            saturationBits = image.colorAdjust.saturation.toBits(),
                        )
                        val fallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            posterizeCache.getOrRequest(key, source, request)
                        } else null
                        var result = false
                        drawIntoCanvas { canvas ->
                            result = PosterizeRenderer.drawBitmap(
                                canvas = canvas.nativeCanvas,
                                source = source,
                                sourceRect = android.graphics.Rect(0, 0, source.width, source.height),
                                destinationRect = android.graphics.RectF(
                                    0f,
                                    0f,
                                    currentW,
                                    currentH,
                                ),
                                request = request,
                                cpuFallback = fallback,
                            )
                        }
                        result
                    } else false
                    if (!drawn) {
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
}

private fun hitTestImage(
    state: PockRefUiState,
    point: Offset,
    viewportSize: Size,
    // V_NOW (user feedback "P1-B2 双指 down hit-test 用
    // 8dp 半径收紧"): when two fingers come down on a
    // multi-touch gesture, each finger tip's contact
    // patch is 6-12 dp wide on a finger-driven touch,
    // and a fingertip that lands on the EDGE of an
    // image (just past the visual boundary) is more
    // often "the user meant the empty area beyond"
    // than "the user meant to grab this image". A
    // single-finger down keeps [edgeInsetPx = 0]
    // (legacy behaviour, no inset) because a single
    // fingertip usually means the user is reaching
    // for what they see. A multi-finger down call
    // (e.g. the [PockRefCanvas] pointer loop's
    // `if (changes.size >= 2)` branch) passes a
    // positive [edgeInsetPx] — typically `8.dp.toPx()`
    // — to shrink the hit rectangle by that many
    // pixels on every side. The pixel value is
    // multiplied by [LocalDensity.current] at the
    // call site so the inset scales with the device.
    edgeInsetPx: Float = 0f,
): String? {
    return hitTestImagesAt(state, point, viewportSize, edgeInsetPx).firstOrNull()
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

internal fun canvasPanDeltaForPointerCount(
    pointerCount: Int,
    previousCentroid: Offset,
    centroid: Offset,
    singleFingerPanEnabled: Boolean,
): Offset {
    val allowedPointerCount = if (singleFingerPanEnabled) 1 else 2
    return if (pointerCount == allowedPointerCount) centroid - previousCentroid else Offset.Zero
}

internal fun shouldSkipFrozenCanvasPointerWork(
    canvasFrozen: Boolean,
    hasPressedPointers: Boolean,
): Boolean = canvasFrozen && hasPressedPointers

internal fun shouldAllowMultiFingerHistoryGesture(
    globalImageLock: Boolean,
    canvasFrozen: Boolean,
): Boolean = !globalImageLock && !canvasFrozen

internal fun shouldSelectImageForTwoFingerCanvasGesture(
    globalImageLock: Boolean,
): Boolean = !globalImageLock

internal fun shouldUseSoftLockedTwoFingerCanvasNavigation(
    globalImageLock: Boolean,
    canvasFrozen: Boolean,
): Boolean = globalImageLock && !canvasFrozen

internal fun shouldUseLocalSoftLockedSingleFingerPan(
    globalImageLock: Boolean,
    canvasFrozen: Boolean,
    singleFingerPanEnabled: Boolean,
): Boolean = globalImageLock && !canvasFrozen && singleFingerPanEnabled

internal fun shouldUseLocalTwoFingerCanvasPan(
    canvasFrozen: Boolean,
    transformCommitted: Boolean,
): Boolean = !canvasFrozen && !transformCommitted

internal fun renderCanvasOffsetForLocalPan(
    stateOffset: Offset,
    presentationOffset: Offset?,
): Offset = presentationOffset ?: stateOffset

internal fun canvasPanCommitDelta(
    presentationOffset: Offset,
    lastCommittedOffset: Offset?,
    stateOffset: Offset,
): Offset = presentationOffset - (lastCommittedOffset ?: stateOffset)

internal fun hasCrossedCanvasPinchZoomThreshold(
    initialDistance: Float,
    currentDistance: Float,
): Boolean {
    if (initialDistance <= 0f) return false
    return abs(currentDistance / initialDistance - 1f) * 100f >= SCALE_THRESHOLD_PERCENT
}

private fun hitTestImagesAt(
    state: PockRefUiState,
    point: Offset,
    viewportSize: Size,
    // V_NOW (P1-B2): see the note on [hitTestImage]. The
    // [edgeInsetPx] is subtracted from the hit-test
    // rectangle on every side; if the image is so
    // small that [edgeInsetPx >= halfWidth] the image
    // never matches — that's intentional (a sub-16dp
    // image shouldn't be a hit target for a two-finger
    // gesture anyway; the user can still single-tap it).
    edgeInsetPx: Float = 0f,
): List<String> {
    return state.images.sortedByDescending { it.zIndex }.mapNotNull { image ->
        if (state.editingGroupId != null &&
            state.groupContaining(image.id)?.id != state.editingGroupId
        ) return@mapNotNull null
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
        val effectiveHalfW = (halfWidth - edgeInsetPx).coerceAtLeast(0f)
        val effectiveHalfH = (halfHeight - edgeInsetPx).coerceAtLeast(0f)
        if (effectiveHalfW <= 0f || effectiveHalfH <= 0f) {
            null
        } else if (pts[0] in -effectiveHalfW..effectiveHalfW && pts[1] in -effectiveHalfH..effectiveHalfH) {
            image.id
        } else {
            null
        }
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
 * World-space AABB of the visible viewport. The default [bufferPx]
 * (=[VIEWPORT_BUFFER_PX], 200 screen pixels) inflates the rect on all
 * four sides so the draw-loop cull and the LaunchedEffect decode-priority
 * filter don't prematurely drop images sitting just past the visible
 * edge during fast pans. The buffer is converted to world units via
 * `1 / canvasScale` so its screen-space size stays constant regardless
 * of zoom level.
 *
 * A scale of 0 or a viewport of 0×0 yields an empty rect at (0,0,0,0) so
 * the cull rejects everything until the first onSizeChanged fires.
 *
 * V_NOW: [bufferPx] is now an explicit parameter (default still
 * [VIEWPORT_BUFFER_PX]) so callers that need the **strict** visible
 * rect — without the cull's "提前看 200px" inflation — can pass 0f.
 * The minimap's viewport indicator is one such caller: the indicator
 * must show the user EXACTLY the region currently visible on the main
 * canvas. Reusing the buffered rect for the indicator meant the
 * indicator was inflated by 200px / canvasScale on every side, which
 * (a) made it look bigger than the actual visible region and (b) at
 * low zoom levels (canvasScale ≈ 0.3) pushed the indicator's right
 * and bottom edges OUTSIDE the minimap's content rect, exposing the
 * gap between the indicator and the panel border ("蓝色框向上偏移,
 * minimap 下边露出一条小白边" per the user's screenshot).
 */
internal fun worldViewportRect(
    canvasOffset: Offset,
    canvasScale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    bufferPx: Float = VIEWPORT_BUFFER_PX,
): Rect {
    if (canvasScale <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
        return Rect(0f, 0f, 0f, 0f)
    }
    val bufferWorld = bufferPx / canvasScale
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
private const val GROUP_CANVAS_BOUNDS_PADDING = 12f

private fun groupDropTargetAt(
    state: PockRefUiState,
    screenPoint: Offset,
    viewportSize: Size,
    draggingImageIds: Set<String>,
): SceneGroup? {
    if (
        state.editingGroupId != null ||
            state.requiresGroupFlattening ||
            draggingImageIds.isEmpty() ||
            draggingImageIds.any { state.groupContaining(it) != null }
    ) return null

    val scale = state.canvasScale.coerceAtLeast(0.0001f)
    val canvasPoint = Offset(
        x = (screenPoint.x - viewportSize.width / 2f - state.canvasOffset.x) / scale,
        y = (screenPoint.y - viewportSize.height / 2f - state.canvasOffset.y) / scale,
    )
    return state.groups.mapIndexedNotNull { index, group ->
        val members = state.images.filter { it.id in group.imageIds }
        val bounds = group.canvasBounds(state.images, GROUP_CANVAS_BOUNDS_PADDING)
        if (
            group.positionLocked ||
                members.isEmpty() ||
                members.any { it.locked } ||
                bounds == null ||
                !bounds.contains(canvasPoint.x, canvasPoint.y)
        ) {
            null
        } else {
            Triple(group, members.maxOfOrNull { it.zIndex } ?: Int.MIN_VALUE, index)
        }
    }.maxWithOrNull(compareBy<Triple<SceneGroup, Int, Int>> { it.second }.thenBy { it.third })?.first
}

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

private fun evictCanvasBitmaps(
    bitmapCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Int, ImageBitmap>>,
    workingSet: CanvasBitmapWorkingSet,
    state: PockRefUiState,
    viewportSize: Size,
) {
    val viewport = worldViewportRect(
        canvasOffset = state.canvasOffset,
        canvasScale = state.canvasScale,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
        // Match the draw cull buffer and include the prefetch ring. An image
        // that is about to enter the viewport must not be evicted while the
        // pan presentation offset is settling.
        bufferPx = VIEWPORT_BUFFER_PX,
    )
    val protectedIds = buildSet {
        addAll(state.selectedImageIds)
        addAll(state.boxSelectPreviewIds)
        addAll(state.transientDragImageIds)
        if (viewport.width > 0f && viewport.height > 0f) {
            state.images.forEach { image ->
                val bounds = imageWorldAabb(image)
                if (
                    bounds.left < viewport.right &&
                    bounds.right > viewport.left &&
                    bounds.top < viewport.bottom &&
                    bounds.bottom > viewport.top
                ) {
                    add(image.id)
                }
            }
        }
    }
    val evicted = workingSet.update(
        cached = bitmapCache.mapValues { (_, entry) ->
            CanvasBitmapWorkingSet.BitmapInfo(
                level = entry.first,
                width = entry.second.width,
                height = entry.second.height,
            )
        },
        protectedIds = protectedIds,
    )
    evicted.forEach(bitmapCache::remove)
}

/**
 * V_NOW (2026-06-08 关闭智能规避): 1.1.0 release 把
 * SelectionBottomSheet 的"自动平移/缩放画布"功能移除。
 * 之前 sheet 弹了会调 [computeCanvasAdjustmentForSheet]
 * 让选中图避开 sheet 区域,sheet 收时再还原。user 反馈
 * 这是干扰性 UX — 画布状态应该完全 user 控制, sheet
 * 是纯"工具面板"语义,出现时**不**动画布, 收时也
 * **不**还原。函数已删除。
 */

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

private fun resolveGifBytes(
    image: SceneImage,
    imageDecoder: com.pockref.app.data.SceneImageDecoder?,
    imageLazyLoader: com.pockref.app.data.PurFileParser?,
): ByteArray? {
    if (!isGifMimeType(image.mimeType)) return null
    if (image.imageBytes.isNotEmpty()) return image.imageBytes
    return if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
        imageDecoder?.readImageBytes(image.imageByteOffset, image.imageByteLength)
            ?: imageLazyLoader?.readImageBytes(image.imageByteOffset, image.imageByteLength)
    } else {
        null
    }
}

private fun buildColorFilter(adjust: ColorAdjust): ColorFilter? {
    val matrix = buildColorMatrix(adjust) ?: return null
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
private fun UnsavedChangesDialog(isSaving: Boolean, onAction: (PockRefAction) -> Unit) {
    GlassAlertDialog(
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
                // V_NOW (2026-06-08 user request): removed
                // the auto-backup guarantee callout
                // (Surface-wrapped Row with the lock icon
                // + the "保留 7 天..." body text) — the
                // dialog now only asks the question. The
                // backup behavior is unchanged; it just
                // doesn't get a dedicated callout in this
                // dialog anymore. Drop the now-unused
                // Surface / Row / Icon imports below if
                // they're no longer referenced elsewhere.
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
                    // R-03: renamed "不保存" → "放弃更改"
                    // (M3 destructive-action wording
                    // convention). "不保存" reads ambiguously
                    // ("don't save the FILE?"); "放弃更改"
                    // is unambiguous.
                    Text(
                        text = "放弃更改",
                        color = if (isSaving) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error,
                    )
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
    GlassAlertDialog(
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
            // P1 (2026-06-11 chrome unify): mid-size
            // card tier.
            shape = RoundedCornerShape(Dimens.CardCornerRadius),
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
    GlassAlertDialog(
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
    GlassAlertDialog(
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
 * V_NOW (2026-06-16): Save-scene name dialog. Shown when the user
 * taps Save (or hits "Save" in the unsaved-changes dialog).
 *
 * The dialog does NOT launch the system save-as picker — it only
 * collects a filename. After confirm, [PockRefAction.ConfirmSaveSceneName]
 * stores the name and flips [PockRefUiState.pendingSaveSceneLaunch],
 * which a [LaunchedEffect] in [PockRefApp] reacts to by launching
 * the [OpenDocumentTree] picker. The picker is the second step
 * (folder choice). The reason we can't fuse them into a single
 * [CreateDocument] dialog is the Samsung One UI 6 / Android 14
 * DocumentsUI bug: it doesn't grant
 * [FLAG_GRANT_PERSISTABLE_URI_PERMISSION] on URIs returned for the
 * Downloads folder, so [openOutputStream] denies with
 * "Permission Denial ... requires android.permission.MANAGE_DOCUMENTS,
 * or grantUriPermission()". The tree-picker path sidesteps that
 * because [OpenDocumentTree]'s persistable grant succeeds.
 *
 * The dialog is a Compose [rememberSaveable] field so the typed
 * name survives configuration changes while the dialog is open.
 */
@Composable
private fun SaveSceneNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialName) { mutableStateOf(initialName) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存场景") },
        text = {
            Column {
                Text(
                    text = "输入文件名,接下来选择保存位置。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim().ifBlank { "Untitled" }) },
            ) {
                Text("选择文件夹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MarkupTextDialog(
    value: String,
    fontSize: Float,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑文本" else "添加文本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("文本") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "字号 ${fontSize.roundToInt()}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = minOf(1f, fontSize)..maxOf(2000f, fontSize),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.trim().isNotEmpty(),
            ) {
                Text(if (isEditing) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MarkupModeBar(
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlassSurface(shape = RoundedCornerShape(Dimens.ChromeCornerRadius)) {
            ToolbarIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = "不保存退出标记",
                onClick = onCancel,
            )
        }
        GlassSurface(shape = RoundedCornerShape(Dimens.ChromeCornerRadius)) {
            ToolbarIconButton(
                imageVector = Icons.Rounded.Check,
                contentDescription = "完成标记",
                onClick = onDone,
            )
        }
    }
}

/**
 * Lossless export-mode dialog for the current image selection. Both
 * choices avoid a JPEG re-encode: one copies each source file verbatim,
 * the other renders only the selected images into one PNG canvas.
 */
@Composable
private fun ExportDialogHost(
    selectedCount: Int,
    onExportOriginals: () -> Unit,
    onExportCanvas: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titleText = if (selectedCount == 1) {
        "导出选中的图片"
    } else {
        "导出选中的 $selectedCount 张图片"
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(Dimens.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "仅导出当前选中的图片 · 两种方式均为无损导出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onExportOriginals,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Image,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text("逐张导出原始图像")
                        Text(
                            "保留原始格式和文件内容,选择一个目标文件夹",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Button(
                    onClick = onExportCanvas,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Layers,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text("合并导出为一张画布")
                        Text(
                            "仅包含选中的图片,PNG 无损;可选手动高画质/原图优先",
                            style = MaterialTheme.typography.bodySmall,
                        )
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

/**
 * V_NOW (2026-06-16 user spec "导出画布新增背景选项"):
 * the canvas-export options dialog. Renders when
 * [PockRefUiState.showExportCanvasDialog] is true. Two
 * segmented-button rows:
 *   1. **Max dim** (existing 2026-06-09 spec "导出画布
 *      选项 — 无尺寸上限"): the user picks a longest-edge
 *      cap. Default = 8192px; the alternative is "no cap"
 *      which the dialog warns can OOM on small-heap
 *      devices.
 *   2. **Background** (new 2026-06-16 spec): TRANSPARENT
 *      (default, preserves the pre-feature behaviour of
 *      [com.pockref.app.ui.PureRefViewModelScene.exportCanvas])
 *      or CANVAS_BACKGROUND (fills the export bitmap with
 *      [PockRefUiState.backgroundColor] before Pass 2
 *      draws the images).
 *
 * The "确认导出" button is the single point that dismisses
 * the dialog AND launches the SAF picker. Chip clicks only
 * write to state — they don't dismiss — so the user can
 * flip both rows before committing. The previous reducer
 * (pre-1.3.0) had [com.pockref.app.ui.PockRefAction.SetExportCanvasMaxDim]
 * dismiss the dialog, which would have closed it on every
 * max-dim chip click; the new design removes that dismiss
 * and the "确认导出" button owns the dismiss+launch flow.
 *
 * Forced to PNG regardless of background mode: JPEG
 * either ignores alpha (filling opaque) or contradicts
 * the user's CANVAS_BACKGROUND pick when alpha is set.
 * The dialog's helper text explains this when the user
 * picks TRANSPARENT.
 */
@Composable
private fun ExportCanvasDialogHost(
    sceneName: String,
    selectedImageIds: Set<String>?,
    maxDim: Int?,
    backgroundMode: com.pockref.app.model.ExportCanvasBackgroundMode,
    onAction: (PockRefAction) -> Unit,
    initialUri: Uri?,
    exportCanvasLauncher: ActivityResultLauncher<CreateDocumentInput>,
    exportSelectedCanvasLauncher: ActivityResultLauncher<CreateDocumentInput>,
) {
    val isSelectionExport = !selectedImageIds.isNullOrEmpty()
    val selectedCount = selectedImageIds?.size ?: 0
    val effectiveSceneName = sceneName.ifBlank {
        if (isSelectionExport) "selected-images" else "canvas"
    }
    GlassAlertDialog(
        onDismissRequest = { onAction(PockRefAction.CancelExportCanvas) },
        title = { Text("导出画布") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Scope chip — mirror the [ExportDialogHost] R-05
                // chip so the user knows exactly which export path
                // this dialog controls (vs. the per-image
                // [ExportDialogHost] which exports the selection).
                Surface(
                    shape = RoundedCornerShape(Dimens.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (isSelectionExport) {
                                "仅导出选中的 $selectedCount 张图片,合并为一张 PNG"
                            } else {
                                "导出当前画布的全部图片,合并为一张 PNG"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // ── Max-dim segmented control ──
                Text(
                    text = if (isSelectionExport) "画质与尺寸" else "尺寸上限",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = maxDim == 8192,
                        onClick = { onAction(PockRefAction.SetExportCanvasMaxDim(8192)) },
                        label = {
                            Text(
                                if (isSelectionExport) "高画质\n(设备可承受)" else "标准\n(最长 8192px)",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = maxDim == null,
                        onClick = { onAction(PockRefAction.SetExportCanvasMaxDim(null)) },
                        label = {
                            Text(
                                if (isSelectionExport) "尽量原图\n(自动防 OOM)" else "原始画质\n(无上限)",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // V_NOW (2026-06-09): the "no cap" path can OOM on
                // small-heap devices (canvas sizes to actual bbox,
                // e.g. 20000×15000 ≈ 1.2GB). Inline hint to set
                // expectations before the user picks.
                if (maxDim == null) {
                    Text(
                        text = if (isSelectionExport) {
                            "超大原图会按分块读取,画布尺寸仍会根据设备内存自动保护"
                        } else {
                            "⚠ 画布按实际内容尺寸输出,可能非常大,小内存设备可能 OOM"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelectionExport) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                } else if (isSelectionExport) {
                    Text(
                        text = "PNG 无损编码,超大图片按分块读取以尽量保留原图细节",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // ── Background segmented control ──
                Text(
                    text = "背景",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = backgroundMode == com.pockref.app.model.ExportCanvasBackgroundMode.TRANSPARENT,
                        onClick = {
                            onAction(
                                PockRefAction.SetExportCanvasBackground(
                                    com.pockref.app.model.ExportCanvasBackgroundMode.TRANSPARENT,
                                ),
                            )
                        },
                        label = { Text("透明背景") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = backgroundMode == com.pockref.app.model.ExportCanvasBackgroundMode.CANVAS_BACKGROUND,
                        onClick = {
                            onAction(
                                PockRefAction.SetExportCanvasBackground(
                                    com.pockref.app.model.ExportCanvasBackgroundMode.CANVAS_BACKGROUND,
                                ),
                            )
                        },
                        label = { Text("画布背景色") },
                        modifier = Modifier.weight(1f),
                    )
                }
                // V_NOW (2026-06-16): transparent + JPEG would
                // contradict each other (JPEG forces opaque fill
                // in most encoders). The export itself is forced
                // to PNG, so we surface a one-liner explaining
                // why JPG doesn't appear in this dialog — same
                // pattern as [ExportDialogHost] for the per-image
                // path, but inlined here because the format choice
                // is implicit ("always PNG" for export-canvas).
                if (backgroundMode == com.pockref.app.model.ExportCanvasBackgroundMode.TRANSPARENT) {
                    Text(
                        text = "PNG 格式,无背景填充(适合后续编辑/合成)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "PNG 格式,导出图背景色 = 画布当前背景色",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val launcher = if (isSelectionExport) {
                        exportSelectedCanvasLauncher
                    } else {
                        exportCanvasLauncher
                    }
                    launcher.launch(
                        CreateDocumentInput(
                            mimeType = "image/png",
                            fileName = if (isSelectionExport) {
                                "${effectiveSceneName}_selected.png"
                            } else {
                                "$effectiveSceneName.png"
                            },
                            initialUri = initialUri,
                        ),
                    )
                },
            ) {
                Text("确认导出")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(PockRefAction.CancelExportCanvas) }) {
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


/**
 * Always-expanded bottom-right action panel. All actions remain available
 * while the canvas chrome is visible; idle time and long presses do not
 * collapse the panel. Tapping the bottom cell still runs auto-layout.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.BottomRightActionPanel(
    onAction: (PockRefAction) -> Unit,
    onSaveScene: () -> Unit,
    onExportCanvas: () -> Unit,
    bottomInset: Dp = Dimens.CornerInset,
    settingsOpen: Boolean = false,
    showLayerButton: Boolean = false,
    onOpenLayers: () -> Unit = {},
) {
    GlassSurface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = Dimens.CornerInset, bottom = bottomInset),
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        // V_NOW (2026-06-09 chrome unify): tonalElevation 从
        // ToolbarElevation (3.dp) 改成 0.dp,跟顶栏统一 — 三个
        // chrome 容器(顶栏 / FAB / 全局锁定)统一纯 surface @0.92,
        // 没有 M3 surfaceTint overlay (light theme 下叠 8% primary
        // tint 让容器偏浅青). FAB 嵌在这个 Surface 内,统一 0.dp
        // 后 FAB 自身的 surface@0.92 直接渲染色 = canvas 透出色,
        // 跟 Lock 按钮 (Box 直接在主 Box 上画) 同色.
        tonalElevation = 0.dp,
        // V_NOW (2026-06-08): 1dp outlineVariant@40% border
        // matching the bottom-sheet chrome so the bottom-right
        // expanded panel reads as the same "outlined chrome
        // surface" family. Without the border, the panel is a
        // flat surface tint (ChromeSurfaceAlpha) on the canvas
        // — the panel and its individual list-item buttons
        // visually merge into the canvas background on light
        // presets (the user reported this same issue for the
        // top toolbar / global-lock / FAB and asked for the
        // 1dp border to be added everywhere consistently).
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
                .copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End,
        ) {
            Column {
                BottomPanelListItem(
                    icon = Icons.Rounded.Brush,
                    contentDescription = "标记",
                    onClick = {
                        onAction(PockRefAction.SetMarkupModeActive(true))
                    },
                )
                if (showLayerButton) {
                    BottomPanelListItem(
                        icon = Icons.Rounded.Layers,
                        contentDescription = "打开图层窗口",
                        onClick = onOpenLayers,
                    )
                }
                // The same settings button opens and closes the non-modal popup.
                BottomPanelListItem(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    onClick = {
                        onAction(PockRefAction.SetSettingsSheetOpen(!settingsOpen))
                    },
                )
                // 4. Export canvas — export the entire
                //    canvas as a single PNG with
                //    transparent background.
                BottomPanelListItem(
                    icon = Icons.Rounded.Image,
                    contentDescription = "Export canvas",
                    onClick = {
                        onExportCanvas()
                    },
                )
                // 5. Save — same handler as the top
                //    toolbar's Save button.
                BottomPanelListItem(
                    icon = Icons.Rounded.Save,
                    contentDescription = "Save scene",
                    onClick = {
                        onSaveScene()
                    },
                )
            }
            // Auto-layout is the panel's permanent bottom cell. Long presses
            // have no expansion/collapse behavior.
            // V_NOW (user feedback "右下角的一键排版按钮为什
            // 么并不是和顶栏一个颜色"): the previous design
            // used [primaryContainer] (the M3 "filled FAB"
            // colour role, a light teal in light theme) so
            // the FAB visually "popped" against the toolbar's
            // neutral surface. The user's spec instead wants
            // a UNIFORM chrome colour across the app's
            // floating surfaces — top toolbar and FAB both
            // wear the same [surface] tint so the eye reads
            // them as a coordinated "chrome layer" rather
            // than the FAB being a single colored accent
            // against a neutral surround. The FAB still
            // pops via the 6dp [FabElevation] (vs the
            // toolbar's 3dp [ToolbarElevation]) — the M3
            // elevation system is what communicates
            // "primary action" depth, not the colour role.
            //
            // Icon colour: [onSurface] (the same dark icon
            // colour the top toolbar's icon buttons use) so
            // the icon-tint matches across both floating
            // surfaces.
            //
            // Hover / press state layer — [primary]-tinted
            // (the brand's chrome accent) at 8% / 12% alpha,
            // composited on top of the surface fill. The
            // brand colour is the M3 "primary" tint, not the
            // chrome surface colour, so the visual feedback
            // for "you're touching this" is a recognisable
            // brand pulse rather than a grey wash — the
            // uniform surface fill is for the IDLE state,
            // and the transient state layer is the
            // affordance layer.
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
                    // V_NOW (2026-06-11 user feedback "右下角
                    // 一键排版展开后为什么一键排版按钮还会有
                    // 一个浅灰色的边框"): the FAB's own 1dp
                    // outlineVariant@40% border is removed.
                    // The outer [Surface] (rendered by
                    // [BottomRightActionPanel] above) already
                    // draws the single 1dp outlineVariant@40%
                    // ring around the WHOLE 5-cell column, so
                    // the FAB painting a second inner border
                    // makes it look "boxed out" of the panel
                    // (an extra horizontal line at the FAB's
                    // top edge) instead of being the bottom
                    // cell of the same column. This matches
                    // the per-item-border removal on the 4
                    // [BottomPanelListItem]s above (see
                    // V_NOW 2026-06-08 comment at
                    // [BottomPanelListItem]) — "only the
                    // OUTER ring" on the whole panel, no
                    // internal grid lines.
                    .background(Color.Transparent, RoundedCornerShape(Dimens.ChromeCornerRadius))
                    .background(
                        color = fabStateLayer,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                    )
                    .hoverable(interactionSource = fabInteractionSource)
                    .clickable(
                        interactionSource = fabInteractionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            onAction(PockRefAction.ExecuteAutoLayout)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = "一键排版",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Single icon-only cell for [BottomRightActionPanel], sized
 * [Dimens.BottomPanelChromeSize] × [Dimens.BottomPanelChromeSize]
 * (V_NOW 2026-06-10: was incorrectly documented as
 * [Dimens.TopToolbarHeight] — that token is for the top toolbar
 * itself; the bottom-right cells use a different token. With
 * TopToolbarHeight bumped to 48dp to match BottomPanelChromeSize
 * the two values now happen to be equal, but they remain
 * distinct semantic tokens).
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
            // V_NOW (2026-06-08): the per-item border added
            // in the previous round is removed. The user
            // explicitly wanted "only the OUTER ring" on
            // the expanded FAB panel — i.e. the parent
            // [Surface]'s single 1dp outlineVariant@40%
            // border around the whole panel, with the 4
            // inner ListItem cells reading as flat Icon
            // tappable regions (no internal grid lines).
            // The outer Surface border is rendered by
            // [BottomRightActionPanel] on its own Surface.
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


// V_NOW (2026-06-10 user spec "全局锁定按钮长按触发
// 冻结模式,按钮变红色;点击或长按红色全局锁定按钮
// 解锁"): the global-lock toggle is now a TWO-AXIS
// [combinedClickable] rather than a single tap, and
// its visual branches on a three-state machine.
//
// State machine — single source of truth
// ([state.lockCanvasFrozen] / [state.globalImageLock]):
//   - OFF  : [LockOpen] icon, frosted surface,
//            [onSurface] tint — the same look the
//            button had pre-this-change.
//   - SOFT : [Lock] icon, [primaryContainer] bg +
//            [onPrimaryContainer] tint — the M3
//            filled-toggle pair already in place
//            since the "激活改成是和其他按钮一样是蓝色"
//            revision. Reads as "engaged feature".
//   - HARD : [Lock] icon, [errorContainer] bg +
//            [onErrorContainer] tint — the M3
//            error-color filled-toggle pair,
//            deliberately distinct from SOFT's cyan
//            so the user can tell at a glance "this
//            is the panic-stop mode, not the normal
//            lock".
//
// Click behaviour in each state:
//   - OFF  → click engages SOFT (existing reducer).
//   - SOFT → click disengages back to OFF.
//   - HARD → click exits HARD but preserves SOFT
//            (so the user steps from frozen-back-to-
//            soft in one tap — per the user's
//            "点击红色按钮解锁到蓝色" spec).
//
// Long-press behaviour:
//   - OFF  → long-press engages HARD directly (and
//            HARD's reducer also engages SOFT, so the
//            button ends up in HARD state).
//   - SOFT → long-press escalates to HARD (reducer
//            already couples frozen-on ↔ soft-on).
//   - HARD → long-press exits to OFF entirely
//            (per the user's "长按红色按钮解锁" spec).
//            We dispatch [ToggleGlobalImageLock] AFTER
//            [ToggleLockCanvasFrozen] because the
//            frozen-OFF branch of the reducer
//            intentionally does NOT touch
//            [globalImageLock] (so a user who wants
//            "soft lock after stepping out of hard
//            mode" isn't silently reset). For the
//            explicit "stop everything" gesture, we
//            want both flags off.
//
// Chrome tokens — identical to the original
// single-tap button, only the dispatch path
// changes:
//   - 48dp square [Dimens.BottomPanelChromeSize]
//   - 12dp [Dimens.ChromeCornerRadius] rounded
//   - 1dp outlineVariant@40% border (or error@40%
//     in HARD state)
//   - [surface]@0.92 frosted base
//   - 6dp [Dimens.FabElevation] (inherited from
//     Box context; not asserted here — the
//     surrounding chrome system applies the
//     elevation via the host layout, same as the
//     pre-refactor version).
//
// The @OptIn(ExperimentalFoundationApi::class) is
// hoisted to the function declaration so it covers
// [combinedClickable] inside the [Modifier] chain
// without needing per-call suppression.
@Composable
private fun BoxScope.MarkupVisibilityButton(
    visible: Boolean,
    hasLockButton: Boolean,
    onToggle: () -> Unit,
    statusBarTopDp: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val stateLayer = when {
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = statusBarTopDp + Dimens.CornerInset + if (hasLockButton) {
                    Dimens.BottomPanelChromeSize + 8.dp
                } else {
                    0.dp
                },
                end = Dimens.CornerInset,
            )
            .size(Dimens.BottomPanelChromeSize)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .glassMaterial(RoundedCornerShape(Dimens.ChromeCornerRadius))
            .background(
                color = stateLayer,
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (visible) "隐藏标记" else "显示标记",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BoxScope.GlobalLockButton(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    statusBarTopDp: androidx.compose.ui.unit.Dp,
) {
    val lockInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isLockHovered by lockInteractionSource.collectIsHoveredAsState()
    val isLockPressed by lockInteractionSource.collectIsPressedAsState()
    // V_NOW (2026-06-10 #1 long-press freeze not firing):
    // wrap [state] in a `rememberUpdatedState` so the
    // long-lived [pointerInput(Unit)] lambda body reads
    // the *current* `state` on every tap / long-press,
    // not the value captured at first composition. Without
    // this wrapper, the lambda is only created once
    // (key = Unit) and the `state.lockCanvasFrozen` /
    // `state.globalImageLock` branches it consults never
    // update — so the second time the user toggles the
    // button, the dispatcher can read a stale "not
    // frozen" and behave as if it were the first press.
    val currentStateRef = rememberUpdatedState(state)
    val lockStateLayer = when {
        isLockPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isLockHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(
                top = statusBarTopDp + Dimens.CornerInset,
                end = Dimens.CornerInset,
            )
            .size(Dimens.BottomPanelChromeSize)
            .border(
                width = 1.dp,
                color = if (state.lockCanvasFrozen) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .glassMaterial(RoundedCornerShape(Dimens.ChromeCornerRadius))
            .background(
                color = when {
                    state.lockCanvasFrozen ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    state.globalImageLock ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .background(
                color = lockStateLayer,
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            // V_NOW: replaced combinedClickable with
            // clickable + pointerInput(detectTapGestures).
            // combinedClickable's onLongClick was not
            // firing reliably on device — the 500ms
            // threshold was being consumed by the
            // parent BoxWithConstraints' gesture system
            // or by the underlying PockRefCanvas
            // pointerInput. detectTapGestures runs
            // directly in the pointer event stream and
            // is more reliable for long-press detection.
            .clickable(
                interactionSource = lockInteractionSource,
                indication = null,
            ) {
                // no-op: tap is handled by detectTapGestures
                // below. This clickable exists only to
                // provide hover/press interaction state
                // and accessibility semantics.
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        val s = currentStateRef.value
                        if (s.lockCanvasFrozen) {
                            onAction(PockRefAction.ToggleLockCanvasFrozen)
                            onAction(PockRefAction.ToggleGlobalImageLock)
                        } else {
                            onAction(PockRefAction.ToggleLockCanvasFrozen)
                        }
                    },
                    onTap = {
                        val s = currentStateRef.value
                        if (s.lockCanvasFrozen) {
                            onAction(PockRefAction.ToggleLockCanvasFrozen)
                        } else {
                            onAction(PockRefAction.ToggleGlobalImageLock)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (state.globalImageLock || state.lockCanvasFrozen) {
                androidx.compose.material.icons.Icons.Rounded.Lock
            } else {
                androidx.compose.material.icons.Icons.Rounded.LockOpen
            },
            contentDescription = when {
                state.lockCanvasFrozen ->
                    "画布已冻结,点按退到软锁,长按完全解锁"
                state.globalImageLock ->
                    "全局图片已锁定,点击解锁"
                else ->
                    "全局图片未锁定,点击锁定全部图片,长按进入冻结模式"
            },
            modifier = Modifier.size(24.dp),
            // Icon tint follows the same three-state
            // branch as the background so WCAG contrast
            // pairs stay intact:
            //   - OFF → onSurface on frosted
            //   - SOFT → onPrimaryContainer
            //   - HARD → onErrorContainer
            tint = when {
                state.lockCanvasFrozen ->
                    MaterialTheme.colorScheme.onErrorContainer
                state.globalImageLock ->
                    MaterialTheme.colorScheme.onPrimaryContainer
                else ->
                    MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
