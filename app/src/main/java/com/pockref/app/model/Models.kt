package com.pockref.app.model

import android.graphics.RectF
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pockref.app.R
import java.util.UUID

data class SceneImage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val imageBytes: ByteArray = ByteArray(0),
    val imageByteOffset: Int = -1,
    val imageByteLength: Int = 0,
    val intrinsicWidth: Int,
    val intrinsicHeight: Int,
    val center: Offset,
    val displaySize: Size2D,
    val colorAdjust: ColorAdjust = ColorAdjust(),
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val zIndex: Int = 0,
    val initialCenter: Offset = center,
    val initialDisplaySize: Size2D = displaySize,
    // When true, the image ignores drag / pinch / rotate / lasso input
    // and the long-press context menu. It still renders normally and
    // can be tapped to select (so the user can still lock / unlock it
    // or inspect it). Toggled via the long-press menu's lock button.
    val locked: Boolean = false,
)

data class Size2D(
    val width: Float,
    val height: Float,
)

/**
 * Image format for the export action. Drives the document
 * launcher's MIME type and the [android.graphics.Bitmap.CompressFormat]
 * the reducer uses to encode the rendered bitmap. PNG is
 * lossless and supports alpha; JPEG is much smaller for
 * photographic content but is opaque. Both choices are
 * user-facing in the export chooser dialog — there's no
 * "default" in code, the user picks every time.
 */
enum class ExportFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");
}

/**
 * Single source of truth for scene display names. All paths that
 * set [PockRefUiState.sceneName] go through this object so the
 * convention stays consistent (and the "•" unsaved indicator is
 * the only string the UI ever has to strip / re-apply when
 * computing the visible name).
 *
 * Conventions:
 *  - Newly imported images on a fresh scene → "Untitled N"
 *  - Loaded from a .pur file with no user rename → file name
 *    minus its extension
 *  - User has unsaved changes (per [PockRefViewModel.hasUnsavedChanges])
 *    → append the [DIRTY_MARKER] for the visible name. The stored
 *    [PockRefUiState.sceneName] never contains the marker; the
 *    UI calls [displayName] to layer it on.
 *  - User can rename to anything; [sanitizeForStorage] strips a
 *    trailing marker so the stored name is always clean.
 */
object SceneNaming {
    /** Suffix shown in the UI when the scene has unsaved changes. */
    const val DIRTY_MARKER = " •"

    private const val UNTITLED_PREFIX = "Untitled"
    private val URDF_SUFFIXES = listOf(".pur", ".PUR")

    /**
     * Name to show to the user. [stored] is [PockRefUiState.sceneName]
     * (no marker); [isDirty] comes from
     * [PockRefViewModel.hasUnsavedChanges]. Returns a freshly-built
     * String so the caller can compare by identity safely.
     */
    fun displayName(stored: String, isDirty: Boolean): String =
        if (isDirty) stored + DIRTY_MARKER else stored

    /**
     * Inverse of [displayName]: strip the dirty marker so the user
     * can pass the result to [PockRefAction.SetSceneName] without
     * us having to re-strip it in the reducer. Idempotent: calling
     * it on a name that has no marker is a no-op.
     */
    fun sanitizeForStorage(name: String): String =
        if (name.endsWith(DIRTY_MARKER)) name.dropLast(DIRTY_MARKER.length) else name

    /**
     * Default name for a brand-new scene (no .pur context, first
     * import or reset). [counter] is the running session-wide
     * import count so the user gets Untitled 1, Untitled 2, ...
     * even if they import into the same scene multiple times.
     */
    fun nextUntitled(counter: Int): String = "$UNTITLED_PREFIX $counter"

    /**
     * Derive a display name from a .pur file's display name
     * (the part after the last `/` minus the file extension).
     * Strips the dirty marker defensively so a previously-edited
     * name doesn't show "name.pur •" after a reload.
     */
    fun fromPurFileName(pathOrName: String): String {
        val trimmed = pathOrName.substringAfterLast('/')
        val noExt = URDF_SUFFIXES
            .firstOrNull { trimmed.endsWith(it) }
            ?.let { trimmed.dropLast(it.length) }
            ?: trimmed
        return sanitizeForStorage(noExt)
    }
}

/**
 * Axis-aligned world-space bounds (in scene units). Used by the snap engine
 * to reason about the dragged group as a single rectangle and to draw snap
 * guides / highlight overlays. Rotated images are still represented by their
 * un-rotated AABB — a deliberate simplification. Edge-to-edge snapping
 * (which is what the user asked for) is meaningful even when the image is
 * rotated, since users want to align the visual outline, not the underlying
 * bitmapped rectangle.
 */
data class ImageBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Which edge of an image / group the snap engine is reasoning about.
 */
enum class SnapEdge {
    LEFT, RIGHT, TOP, BOTTOM;
}

/**
 * One horizontal-axis snap pair: the [movingEdge] of the dragged group is
 * being aligned to the [targetEdge] of the image with id [targetImageId].
 * Both edge positions are in world coordinates.
 *
 * [sourceImageId] identifies which specific dragged image produced this
 * snap — important for group drags, where the engine picks the single
 * sub-image whose edge is closest to [targetEdgeWorld] (instead of using
 * the group's outer AABB). The render layer uses this to focus the
 * highlight on the relevant sub-image; the apply-delta path uses it to
 * compute the originalEdge that produced the snap. For a single-image
 * drag this is just the dragged image's id.
 */
data class SnapAxisX(
    val movingEdge: SnapEdge,         // LEFT or RIGHT
    val movingEdgeWorld: Float,
    val sourceImageId: String,        // which dragged image's edge fired this snap
    val targetImageId: String,
    val targetEdge: SnapEdge,         // LEFT or RIGHT
    val targetEdgeWorld: Float,
    val distanceWorld: Float,         // 0 when fully snapped, > 0 when within range but not snapped
    /**
     * Whether this snap aligns an EDGE of the dragged group onto a
     * target edge, or a CENTER axis of the group onto the target's
     * CENTER axis (powerpoint-style "align to middle"). V5 added the
     * CENTER path as a "边缘中点" candidate; the renderer uses this
     * field to pick a slightly different visual treatment.
     */
    val axisKind: AxisKind = AxisKind.EDGE,
)

/**
 * Vertical-axis counterpart of [SnapAxisX].
 */
data class SnapAxisY(
    val movingEdge: SnapEdge,         // TOP or BOTTOM
    val movingEdgeWorld: Float,
    val sourceImageId: String,
    val targetImageId: String,
    val targetEdge: SnapEdge,         // TOP or BOTTOM
    val targetEdgeWorld: Float,
    val distanceWorld: Float,
    val axisKind: AxisKind = AxisKind.EDGE,
)

/**
 * What kind of alignment the snap axis represents.
 *
 * - [EDGE]: the dragged sub-image's LEFT/RIGHT (or TOP/BOTTOM) edge
 *   is being aligned to the target image's matching edge. The original
 *   V4 behavior.
 * - [CENTER]: the dragged group's CENTRE X (or Y) is being aligned
 *   to the target image's CENTRE X (or Y) — "align to middle" of the
 *   target. V5 added this as a separate candidate so users can snap
 *   to a target's mid-line even when none of the edges line up.
 */
enum class AxisKind { EDGE, CENTER }

/**
 * Live snap state published by the ViewModel while a drag is in progress.
 * Each axis is independent; both can fire on the same frame (e.g. an image
 * snapping to a target's top-left corner). [groupBounds] is the dragged
 * group's AABB in world coordinates AFTER snap adjustment, which the
 * renderer uses to size the guide lines.
 *
 * V5: [isCornerSnap] is true when both axes are EDGE candidates that
 * hit the same corner of the same target image — the strongest snap
 * case the user spec calls out (corner > mid > edge). The renderer
 * reflects this in a stronger guide treatment.
 */
data class SnapGuide(
    val axisX: SnapAxisX?,
    val axisY: SnapAxisY?,
    val groupBounds: ImageBounds,
    val isCornerSnap: Boolean = false,
)

/**
 * Post-commit highlight state. When a drag ends and the final position was
 * produced by snap, the renderer briefly highlights the snapped edges with
 * a fading overlay (350ms) so the user gets a clear visual confirmation
 * that snap fired.
 */
data class SnapHighlight(
    val guide: SnapGuide,
    val startTimeMs: Long,
)

/**
 * Live angle-snap state published by the ViewModel while a rotation gesture
 * is in progress. Non-null means the rotated image is currently in the
 * snap zone (within ±5° of a 45°-multiple — 0/45/90/135/180/225/270/315).
 * The renderer uses the [snappedDegrees] field to draw a small
 * "angle alignment" overlay (a circle + the snapped angle text) on top
 * of the selected image so the user sees the snap engage.
 *
 * Three states correspond to the three values of [isEngaged] /
 * [activeSnapAngle]:
 *
 * - **ZONE (waiting)** — the gesture is in the snap zone but the user
 *   has not yet paused for 200ms. [isEngaged] = false,
 *   [activeSnapAngle] = null, [angleEnteredSnapZoneMs] is the timestamp
 *   the angle first entered the zone. The renderer may show a "near"
 *   indicator so the user knows snap is about to fire if they hold.
 *
 * - **ENGAGED (locked)** — the 200ms pause has elapsed and the image is
 *   locked to [activeSnapAngle]. [isEngaged] = true, [activeSnapAngle]
 *   is the canonical 8-angle the image is locked to. The renderer
 *   shows a stronger "locked" indicator. This state is held until the
 *   user rotates past [com.pockref.app.ui.ROTATION_SNAP_CONTINUE_THRESHOLD_DEG]
 *   (10°) from [activeSnapAngle], at which point the snap is released.
 *
 * - **CANCELLED (transient)** — same shape as ZONE; [activeSnapAngle]
 *   has just been nulled because the user moved past the continue
 *   threshold. If the new candidate is itself in a snap zone, the
 *   state transitions to ZONE with a fresh [angleEnteredSnapZoneMs].
 *
 * [lastAngleChangeMs] is the timestamp of the most recent non-zero
 * rotation update. Useful for the state machine and for debugging
 * "why didn't snap fire?" — the field exposes the raw input timing
 * without requiring the state machine to keep a separate timestamp.
 *
 * [startTimeMs] is the system-time millis when the snap-engaged
 * transition happened; the renderer can use it to time a fade-in
 * animation. Cleared (via `angleSnapGuide = null` on the UI state)
 * on commit / cancel / next gesture begin.
 */
data class AngleSnapGuide(
    val snappedDegrees: Float,
    val isEngaged: Boolean,
    val startTimeMs: Long,
    /**
     * Timestamp (epoch ms) of the most recent non-zero rotation
     * update. The state machine uses this to time the 200ms pause
     * detector (well — `now - angleEnteredSnapZoneMs`, but having the
     * last-change timestamp on the guide makes debugging easier).
     * Defaults to 0L when the guide is constructed fresh so the
     * default constructor is still usable in tests.
     */
    val lastAngleChangeMs: Long = 0L,
    /**
     * The canonical 8-angle the image is currently locked to. Null
     * when the snap is not yet engaged (waiting for the 200ms pause)
     * or has been cancelled (user rotated past the 10° continue
     * threshold). The renderer should hide the "locked" indicator
     * when this is null.
     */
    val activeSnapAngle: Float? = null,
    /**
     * Timestamp (epoch ms) when the angle first entered the ±5°
     * snap zone for the current zone visit. Reset to `null` (or to
     * `now` on a fresh entry after a cancel) every time the gesture
     * leaves the zone. Used by the state machine to compute
     * `now - angleEnteredSnapZoneMs >= 200ms` for the engage check.
     */
    val angleEnteredSnapZoneMs: Long? = null,
)

/**
 * One sample of the user's drag speed over a short window. The transform
 * layer records one of these per `updateImageDrag` invocation and
 * smooths across a 100ms window to decide whether the user is "settling"
 * (slow → trigger snap) or "continuing to move" (fast → cancel snap).
 *
 * Pure JVM data type so the speed-window logic can be unit-tested
 * without an Android runtime.
 */
data class SpeedSample(
    val timestampMs: Long,
    val pxPerSec: Float,
)

data class ColorAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
)

data class RecentSceneEntry(
    val uri: String,
    val label: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String = "",
    val fileSize: Long = 0L,
)

data class PockRefScene(
    val version: Int = 1,
    val name: String = "Scene",
    val backgroundColor: Color = Color.White,
    val images: List<SceneImage> = emptyList(),
    /**
     * Default horizontal / vertical spacing (in scene units) used by
     * auto-layout. The desktop .pur format does not standardize a
     * "spacing" field; this is a PockRef-Android extension stored in the
     * scene-level metadata block written by [writeLegacyStreaming] /
     * [readLegacyPurScene]. Defaults are 8 scene units.
     */
    val defaultSpacing: Size2D = Size2D(8f, 8f),
    /**
     * Default reference-point for image placement. CENTER (the historical
     * Android behavior — [SceneImage.center] is the image's center) is
     * the default so existing scenes reload unchanged. The field is
     * persisted alongside [defaultSpacing] in the scene-level metadata
     * block; desktop .pur readers that don't know about it ignore the
     * block cleanly.
     */
    val defaultAlignment: ImageAlignment = ImageAlignment.CENTER,
)

/**
 * Reference point used to interpret [SceneImage.center] when drawing.
 * CENTER (the default and historical Android behavior) treats
 * [SceneImage.center] as the geometric center of the image. Other
 * values shift the rendered position so [SceneImage.center] is instead
 * the image's top-left, top-right, etc. corner.
 */
enum class ImageAlignment(val label: String) {
    TOP_LEFT("左上"),
    TOP_CENTER("顶部居中"),
    TOP_RIGHT("右上"),
    CENTER_LEFT("左居中"),
    CENTER("居中"),
    CENTER_RIGHT("右居中"),
    BOTTOM_LEFT("左下"),
    BOTTOM_CENTER("底部居中"),
    BOTTOM_RIGHT("右下"),
}

enum class CropAspect(val label: String, val ratioW: Float, val ratioH: Float) {
    FREE("自由", 0f, 0f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_3_2("3:2", 3f, 2f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_9_16("9:16", 9f, 16f),
}

enum class AlignMode(val label: String) {
    ALIGN_TOP("向上对齐"),
    ALIGN_BOTTOM("向下对齐"),
    ALIGN_LEFT("向左对齐"),
    ALIGN_RIGHT("向右对齐"),
    ALIGN_WIDTH("等宽对齐"),
    ALIGN_HEIGHT("等高对齐"),
    ALIGN_MAX("最大对齐"),
    ALIGN_MIN("最小对齐"),
}

/**
 * The canvas's selection interaction mode. The internal state
 * ([PockRefUiState.boxSelectMode] / [PockRefUiState.multiSelectMode])
 * is a pair of booleans because the BOX case implies MULTI
 * (box-select is a multi-image gesture that drags a rectangle
 * rather than a freeform lasso), but the user-facing mode is
 * one of three exclusive values:
 *
 *   SINGLE  — tap-to-select one image at a time. No lasso, no
 *             box-select. This is the default after first launch.
 *   MULTI   — lasso-select. The user drags a freeform path; the
 *             released lasso path encloses all images whose
 *             centers fall inside.
 *   BOX     — box-select. The user drags a rectangle; on release
 *             all images whose centers fall inside are selected.
 *             BOX implies MULTI in the state because the same
 *             "I'm holding multiple images" intent is active.
 *
 * The S Pen button binding drives [SetSelectionMode] directly:
 *   - hold button on touchdown → BOX
 *   - release button mid-stroke → MULTI
 *   - double-tap empty canvas → SINGLE
 * The toolbar buttons still dispatch [ToggleMultiSelectMode] /
 * [ToggleBoxSelectMode] for parity with the existing UX.
 */
enum class SelectionMode {
    SINGLE,
    MULTI,
    BOX,
}

/**
 * Predefined background colors. The `Custom` entry does not represent a fixed
 * color — it is a sentinel that means "user picked a custom color via the
 * picker"; the actual color is stored on [PockRefUiState.backgroundCustomColor]
 * (or in the [PockRefScene.backgroundColor] field once persisted).
 */
enum class BackgroundPreset(@StringRes val labelRes: Int, val color: Color) {
    DarkGrey(R.string.bg_dark_grey, Color(0xFF111111)),
    Black(R.string.bg_black, Color(0xFF000000)),
    White(R.string.bg_white, Color(0xFFFFFFFF)),
    LightGrey(R.string.bg_light_grey, Color(0xFFEEEEEE)),
    Canvas(R.string.bg_canvas, Color(0xFFE8DCC4)),
    Mint(R.string.bg_mint, Color(0xFFB0E0D0)),
    Sky(R.string.bg_sky, Color(0xFFB0D8E8)),
    Custom(R.string.bg_custom, Color(0x00000000));

    companion object {
        /**
         * Returns the matching preset if [color] equals one of the fixed preset
         * colors (within a tiny epsilon to ignore float precision noise), or
         * [Custom] otherwise.
         */
        fun fromColor(color: Color): BackgroundPreset {
            values().forEach { preset ->
                if (preset == Custom) return@forEach
                if (preset.color.toArgb() == color.toArgb()) return preset
            }
            return Custom
        }

        val DefaultPreset: BackgroundPreset = White
    }
}


/**
 * The three top-level tabs of the in-app settings sheet.
 *
 * V_NOW: settings content grew past one phone screen, so
 * [com.pockref.app.ui.SettingsPanel] now pages into 3 tabs
 * (with a [androidx.compose.material3.TabRow] on phones, a
 * [androidx.compose.material3.NavigationRail] on tablets).
 * Persisted to [PockRefUiState.settingsTab] so the user's
 * last tab is restored the next time the sheet opens.
 *
 * Tab content (see [com.pockref.app.ui.SettingsPanel] for
 * the actual extraction):
 *   - [GENERAL]: 吸附图像 / 吸附旋转 — the two
 *     "snap-to-other-images" toggles
 *   - [APPEARANCE]: 主题 (system/light/dark) / 背景颜色 —
 *     6-7 preset swatches + HSV custom wheel
 *   - [GESTURES]: 7 per-gesture on/off switches, grouped
 *     into 缩放 / 旋转 / 编辑 sub-categories
 *
 * Order matters: the enum ordinal is the visual index of the
 * tab/rail item, so adding a new tab in the middle would
 * shuffle everyone's last-active tab on upgrade. Append-only.
 */
enum class SettingsTab(val label: String) {
    GENERAL("通用"),
    APPEARANCE("外观"),
    GESTURES("手势"),
}

/**
 * V_NOW: which screen edge the minimap is docked to. Used by
 * [PockRefUiState.minimapDockEdge] (and persisted via
 * [com.pockref.app.data.AppPreferences.minimapDockEdge]) to
 * remember the user's last edge-dock choice. [NONE] = the
 * minimap is free-positioned (full panel). The other four values
 * mean the minimap is collapsed to a small icon at that edge.
 *
 * Naming uses cardinal directions rather than "TOP_START" /
 * "BOTTOM_END" because the minimap can be docked at any of the
 * four cardinal edges, not just the two bottom corners the
 * FAB / minimap originally occupied.
 */
enum class MinimapDockEdge { NONE, TOP, BOTTOM, LEFT, RIGHT }

/**
 * V_NOW: how the minimap area is displayed. MINIMAP = the
 * traditional thumbnail overview; SLIDER = a persistent zoom
 * slider (the same slider that appears on long-press in MINIMAP
 * mode, but always visible). Persisted via
 * [com.pockref.app.data.AppPreferences.minimapDisplayMode].
 */
enum class MinimapDisplayMode { MINIMAP, SLIDER }

/**
 * V_NOW: orientation of the minimap slider when
 * [MinimapDisplayMode.SLIDER] is active. ADAPTIVE picks
 * horizontal or vertical based on scene aspect ratio.
 * Persisted via
 * [com.pockref.app.data.AppPreferences.minimapSliderOrientation].
 */
enum class MinimapSliderOrientation { ADAPTIVE, HORIZONTAL, VERTICAL }



data class CropRect(
    val left: Float = 0.05f,
    val top: Float = 0.05f,
    val right: Float = 0.95f,
    val bottom: Float = 0.95f,
) {
    val cropWidth: Float get() = right - left
    val cropHeight: Float get() = bottom - top

    fun isValid(): Boolean = cropWidth > 0.05f && cropHeight > 0.05f

    fun withAspect(aspect: CropAspect, imgW: Int, imgH: Int): CropRect {
        if (aspect == CropAspect.FREE) return this
        val imgAspect = imgW.toFloat() / imgH.toFloat()
        val targetAspect = aspect.ratioW / aspect.ratioH
        val newW: Float
        val newH: Float
        if (imgAspect > targetAspect) {
            newH = cropHeight
            newW = newH * targetAspect * (imgH.toFloat() / imgW.toFloat())
        } else {
            newW = cropWidth
            newH = newW / targetAspect * (imgW.toFloat() / imgH.toFloat())
        }
        if (newW > 1f || newH > 1f) {
            val s = minOf(1f / newW, 1f / newH)
            return CropRect(
                left = (1f - newW * s) / 2f,
                top = (1f - newH * s) / 2f,
                right = (1f + newW * s) / 2f,
                bottom = (1f + newH * s) / 2f,
            )
        }
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return CropRect(
            left = (cx - newW / 2f).coerceIn(0f, 1f - newW),
            top = (cy - newH / 2f).coerceIn(0f, 1f - newH),
            right = (cx + newW / 2f).coerceIn(newW, 1f),
            bottom = (cy + newH / 2f).coerceIn(newH, 1f),
        )
    }
}

data class PockRefUiState(
    val sceneName: String = "Untitled",
    val backgroundColor: Color = BackgroundPreset.White.color,
    val backgroundCustomColor: Color = Color(0xFF808080),
    val showBackgroundPicker: Boolean = false,
    val themeMode: com.pockref.app.data.ThemeMode = com.pockref.app.data.ThemeMode.LIGHT,
    /**
     * V_NOW: S Pen side-button gesture mode. Mirrors
     * [com.pockref.app.data.AppPreferences.spenMode] so the
     * settings sheet's RadioButton can read it on every
     * recomposition. The detector itself reads the pref
     * directly on construction (MainActivity.onCreate), so
     * this state field is UI-only — no view-model action
     * mutates it; the action writes to the pref, the pref
     * value flows back through the next uiState snapshot.
     */
    val spenMode: com.pockref.app.spen.SpenMode =
        com.pockref.app.spen.SpenMode.DOUBLE_TAP,
    val images: List<SceneImage> = emptyList(),
    /**
     * Scene-level default spacing (horizontal / vertical, in scene
     * units) used by auto-layout. Persisted as part of the scene-level
     * metadata block by [writeLegacyStreaming] / [readLegacyPurScene].
     */
    val sceneDefaultSpacing: Size2D = Size2D(8f, 8f),
    /**
     * Scene-level default reference-point for image placement. See
     * [ImageAlignment]. Persisted as part of the scene-level metadata
     * block.
     */
    val sceneDefaultAlignment: ImageAlignment = ImageAlignment.CENTER,
    val selectedImageId: String? = null,
    val selectedImageIds: Set<String> = emptySet(),
    val multiSelectMode: Boolean = false,
    val boxSelectMode: Boolean = false,
    val boxSelectRect: RectF? = null,
    val boxSelectPreviewIds: Set<String> = emptySet(),
    // V_NOW (2026-06-08): global image lock. When `true`,
    // every image on the canvas is treated as locked
    // for the purposes of move / rotate / scale /
    // select / delete / duplicate / etc. — the
    // "effective lock" of an image is
    // `image.locked || state.globalImageLock`, queried
    // via the [isImageLocked] extension below.
    //
    // This is a VIEW-MODE flag (like multiSelectMode /
    // boxSelectMode), not a scene change: toggling it
    // does NOT enter the undo stack — the same way
    // the single-image `lock` field isn't a scene
    // change either (and the per-image `lock` is
    // toggled via the SelectionBottomSheet's "锁定"
    // button without going through undo either, in
    // the existing design).
    //
    // Persisted via [com.pockref.app.data.AppPreferences.globalImageLock]
    // so a user who locks the canvas can restart the
    // app and find it still locked. Default `false`
    // — a fresh install must NOT come up with all
    // images locked, that would be hostile to anyone
    // who imported a few pictures.
    val globalImageLock: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val recentScenes: List<RecentSceneEntry> = emptyList(),
    val canvasScale: Float = 1f,
    val canvasOffset: Offset = Offset.Zero,
    val canvasViewportSize: Size2D = Size2D(0f, 0f),
    val transientDragImageIds: Set<String> = emptySet(),
    val transientDragDelta: Offset = Offset.Zero,
    val isBusy: Boolean = false,
    val cropImageId: String? = null,
    val cropRect: CropRect = CropRect(),
    val cropAspect: CropAspect = CropAspect.FREE,
    val cropRotation: Float = 0f,
    val canDirectSave: Boolean = false,
    /**
     * True when the in-memory scene differs from the last saved /
     * auto-saved snapshot. Computed in [PockRefViewModel.syncDirectSaveState]
     * and re-pushed to state on every change so the UI can render
     * the dirty marker (e.g. "Untitled 1 •") without recomputing
     * the hash on every recomposition. False when the scene is
     * empty (a fresh app start with no images doesn't need an
     * unsaved marker, even though the snapshot hash is "blank").
     */
    val hasUnsavedChanges: Boolean = false,
    /**
     * The "rename scene" dialog. Non-null = dialog is visible with
     * that initial text. The reducer path takes a fresh string
     * from [PockRefAction.ConfirmRenameScene] and pipes it through
     * [SceneNaming.sanitizeForStorage] so a user-typed trailing
     * marker gets stripped before persistence.
     */
    val renameDialogValue: String? = null,
    /**
     * The "export as PNG / JPG" chooser dialog. Non-null = dialog
     * is visible. The dialog is shown by [PockRefAction.ExportSelected]
     * and dismissed by either the user's format choice (which
     * triggers the [PockRefAction.ConfirmExport] path) or by
     * [PockRefAction.CancelExport].
     */
    val showExportDialog: Boolean = false,
    val showExportCanvasDialog: Boolean = false,
    val showUnsavedChangesDialog: Boolean = false,
    /**
     * Settings sheet visibility. True = the [com.pockref.app.ui.SettingsPanel]
     * ModalBottomSheet is showing. Toggled by [PockRefAction.SetSettingsSheetOpen].
     * Hoisted into UI state (was previously local `remember` in [TopToolbar])
     * so multiple chrome entry points — the top toolbar's gear button (when
     * it's restored) and the bottom-right action panel's "Settings" ListItem
     * — can all open the same sheet instance.
     */
    val settingsSheetOpen: Boolean = false,
    /**
     * V_NOW: minimap custom position (screen-space top-left of the minimap
     * panel). [Offset.Unspecified] = "use the default BottomStart 24dp
     * corner". The user can long-press the minimap to enter "moving" mode
     * and drag it anywhere on screen; on release the position is
     * persisted to [com.pockref.app.data.AppPreferences.minimapPositionX]
     * / [minimapPositionY] and hydrated back on next launch.
     *
     * Coords are in raw pixels, top-left origin. The renderer applies
     * [minimapDockEdge] on top of this — if docked, the position is
     * interpreted as the "anchor" along the edge (e.g. for
     * [MinimapDockEdge.LEFT], the position.y is the vertical anchor
     * from the top, position.x is ignored). This lets the user
     * remember their preferred vertical position on the side even
     * after a re-dock.
     */
    val minimapPosition: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Unspecified,
    /**
     * V_NOW: which screen edge the minimap is currently docked to.
     * [MinimapDockEdge.NONE] = free-positioned (showing the full
     * panel at [minimapPosition]). [TOP] / [BOTTOM] / [LEFT] / [RIGHT]
     * = collapsed to an edge icon at the indicated side, with the
     * vertical (for top/bottom) or vertical (for left/right) anchor
     * from [minimapPosition]. The user drags the minimap near an
     * edge to dock; hovers/clicks the docked icon to expand.
     */
    val minimapDockEdge: MinimapDockEdge = MinimapDockEdge.NONE,
    /**
     * V_NOW: whether the minimap is currently showing only its edge
     * icon ([CollapsedMinimapIcon]) vs the full panel. Tied to
     * [minimapDockEdge] — if dockEdge != NONE, this is effectively
     * always true (the icon is the visible state). Tracked as a
     * separate field so the 3s auto-collapse (matching the top
     * toolbar's contract) can collapse to the icon WITHOUT changing
     * the dock edge — i.e. an expanded free-positioned minimap
     * collapses to whichever edge the user last docked to, rather
     * than a fresh "no edge" state.
     */
    val minimapCollapsed: Boolean = false,
    /**
     * V_NOW: pinch-to-zoom scale for the full minimap panel.
     * 1.0f = default size (computed from scene bbox in
     * [CanvasMinimap]); 0.5f = half-size (more overview); 2.0f
     * = double-size (more detail). Two-finger pinch updates
     * this in real time; the value is persisted to
     * [com.pockref.app.data.AppPreferences.minimapZoomScale]
     * on every change (mid-pinch writes are cheap — just a
     * single [putFloat().apply()] — and we want crash-survivable
     * memory). Clamped to [0.5f, 2.0f] in
     * [SceneController.setMinimapZoomScale] / the pointerInput
     * handler. The bar (collapsed) view ignores this value;
     * only the expanded panel uses it.
     */
    val minimapZoomScale: Float = 1f,
    /**
     * V_NOW: how the minimap area is displayed. MINIMAP = the
     * traditional thumbnail overview; SLIDER = a persistent zoom
     * slider. Persisted via
     * [com.pockref.app.data.AppPreferences.minimapDisplayMode].
     * Default SLIDER shows the zoom slider on first
     * launch.
     */
    val minimapDisplayMode: MinimapDisplayMode = MinimapDisplayMode.SLIDER,
    /**
     * V_NOW: orientation of the minimap slider when
     * [minimapDisplayMode] is [MinimapDisplayMode.SLIDER].
     * ADAPTIVE picks horizontal or vertical based on scene
     * aspect ratio. Persisted via
     * [com.pockref.app.data.AppPreferences.minimapSliderOrientation].
     */
    val minimapSliderOrientation: MinimapSliderOrientation = MinimapSliderOrientation.VERTICAL,
    /**
     * Active settings tab (V_NOW: tabbed settings sheet).
     * Persists across sheet open/close so the user comes
     * back to whichever tab they were on. Default is
     * [SettingsTab.GENERAL] — the most common tab and the
     * natural landing tab for the 2 most-used toggles
     * (吸附图像 / 吸附旋转).
     */
    val settingsTab: SettingsTab = SettingsTab.GENERAL,
    val isSaving: Boolean = false,
    val triggerSaveAsFromDialog: Boolean = false,
    val pendingCloseAfterSave: Boolean = false,
    val fittedImageId: String? = null,
    val preFitCanvasScale: Float = 1f,
    val preFitCanvasOffset: Offset = Offset.Zero,
    val canUndoOptimization: Boolean = false,
    val batchOptimizeState: BatchOptimizeState = BatchOptimizeState(),
    val showBatchOptimizeConfirm: Boolean = false,
    val hoveredImageId: String? = null,
    val hoverPointScreen: Offset? = null,
    val hoverPreviewImageId: String? = null,
    val isDragOver: Boolean = false,
    val topToolbarExpanded: Boolean = true,
    /**
     * Bottom-right action panel state. True = the panel is
     * expanded (4 ListItem rows visible above the FAB:
     * box-select / settings / import / save). False = collapsed
     * to the 44dp FAB (auto-layout only). Auto-collapses 3s
     * after the last interaction, same auto-collapse contract as
     * [topToolbarExpanded] but no sub-panels to gate on.
     */
    val bottomRightPanelExpanded: Boolean = false,
    /**
     * Active snap state while a drag is in progress. Non-null means the
     * dragged group is within snap range of at least one other image. The
     * renderer draws guide lines from this. Cleared on commit / cancel.
     */
    val snapGuide: SnapGuide? = null,
    /**
     * Brief post-commit highlight so the user sees that the final position
     * was produced by snap. Auto-clears via a timed action after ~350ms.
     */
    val snapHighlight: SnapHighlight? = null,
    /**
     * User preference: whether the edge-to-edge snap engine runs on every
     * drag. Persisted to SharedPreferences via [com.pockref.app.data.AppPreferences].
     * Default `true` so existing users get snap immediately after upgrade.
     * The setting is gated in [com.pockref.app.ui.PockRefViewModelTransform.updateImageDrag]
     * so disabling it short-circuits the engine without any per-frame cost.
     */
    val edgeSnapEnabled: Boolean = true,
    /**
     * Per-gesture on/off switches exposed in the settings panel
     * under "手势". Each key is one of the [com.pockref.app.ui.GestureId]
     * const strings; missing keys default to `true` (so a freshly
     * deserialised state that lacks the field still allows every
     * gesture). [com.pockref.app.ui.PockRefAction.SetGestureEnabled]
     * toggles individual entries via map-set; the rest of the
     * app reads [com.pockref.app.ui.isGestureEnabled] to gate
     * each handler.
     */
    val gestureToggles: Map<String, Boolean> = emptyMap(),
    /**
     * Intent-detection state machine timer. Set to `now` (ms) the moment
     * the cursor enters the 15px trigger range of any target. After
     * [SNAP_ENGAGE_DELAY_MS] of low-speed motion, the engine transitions
     * to ENGAGED and starts producing snap guides.
     *
     * Cleared (= null) when the user moves out past 30px (主动移出) or
     * resumes fast motion (≥ 100 px/s, continue-moving intent). Also
     * cleared on commit / cancel / begin.
     */
    val snapInRangeSinceMs: Long? = null,
    /**
     * The image id the snap engine is currently ENGAGED on. Non-null
     * means we've passed the 300ms + low-speed gate for THIS target.
     * Cleared on disengage (move-out, fast move, commit, cancel).
     *
     * When the user enters the 15px range of a DIFFERENT target while
     * already engaged, this field swaps to the new target — the snap
     * guide follows the new alignment rather than the old one.
     */
    val snapEngagedTargetId: String? = null,
    /**
     * Sliding window of recent drag speed samples, used to compute a
     * smoothed average speed for the engage / disengage gates. The
     * transform layer keeps only samples within the last 100ms; older
     * ones are pruned on each updateImageDrag.
     */
    val snapRecentSpeeds: List<SpeedSample> = emptyList(),
    /**
     * Timestamp (ms) of the most recent `updateImageDrag` invocation.
     * Used to compute the true delta-time between motion events for
     * the speed sample — a 16ms fixed estimate would jitter badly
     * under batched or throttled touch events.
     */
    val snapLastSampleTimeMs: Long? = null,
    /**
     * 0..1 alpha multiplier for [snapGuide]'s reference lines. Used to
     * fade the guide IN over the 300ms TRACKING window so the user sees
     * the "engaging" feedback rather than the guide popping on
     * instantly. Once ENGAGED, alpha stays at 1f. On disengage, the
     * renderer should hide the guide entirely (set [snapGuide] to null)
     * so the alpha becomes irrelevant.
     */
    val snapGuideAlpha: Float = 1f,
    /**
     * User preference: whether rotation snaps to 0°/90°/180°/270°
     * during a transform gesture. Persisted via
     * [com.pockref.app.data.AppPreferences] so the choice survives
     * restart. Default `true` — most users want image angles to "click"
     * to the cardinal orientations when they're close enough.
     *
     * Independent of [edgeSnapEnabled] so the user can opt out of one
     * without the other.
     */
    val rotationSnapEnabled: Boolean = true,
    /**
     * Live angle-snap state. Non-null while a rotation gesture is locked
     * to a 0°/90°/180°/270° multiple. The renderer draws a small overlay
     * on the selected image so the user can see the snap engaged.
     * Cleared on commit / cancel / begin.
     */
    val angleSnapGuide: AngleSnapGuide? = null,
    /**
     * V_NOW (window-blur collapse): unified "all chrome
     * was collapsed by the blur reducer" flag. The blur
     * reducer flips this AND the two surface-level flags
     * ([topToolbarExpanded], [bottomRightPanelExpanded])
     * in a single `state.copy(...)`, and a third chrome
     * surface — the selection floating toolbar — has no
     * dedicated state field of its own (its visibility
     * is derived from `selectedImageIds.size > 0`).
     * It reads this flag and early-returns when it's
     * `true` so the toolbar goes away on blur even
     * though the selection is non-empty.
     *
     * Default `false` so a fresh process start (where
     * the blur reducer has never fired) shows the
     * toolbar normally when the user has a selection.
     * The opposite edge (focus regained) deliberately
     * does NOT clear this flag — see the "不主动恢复"
     * spec note in
     * [com.pockref.app.ui.PockRefViewModel.collapseMenuBarsOnWindowBlur].
     */
    val menuBarsCollapsed: Boolean = false,
    /**
     * V_NOW: auto-hide the selection floating toolbar after
     * 5 seconds of inactivity. When true, the
     * [com.pockref.app.ui.PureRefApp.SelectionFloatingToolbar]
     * early-returns (same pattern as [menuBarsCollapsed]).
     * Reset to false when the user taps an image (re-selects)
     * or interacts with the toolbar (button press resets the
     * 5s timer). This keeps the canvas clean when the user
     * is focused on drawing rather than toolbar operations.
     */
    val selectionToolbarHidden: Boolean = false,
    /**
     * V_NOW (focus-restore follow-up): snapshot of the top
     * toolbar's expanded flag captured the moment
     * [com.pockref.app.ui.PockRefViewModel.collapseMenuBarsOnWindowBlur]
     * runs (i.e. the value the user had set immediately
     * before the window blurred). The restore reducer
     * [com.pockref.app.ui.PockRefViewModel.restoreMenuBarsOnWindowFocus]
     * writes this value back to [topToolbarExpanded] when the
     * window regains focus, so the user returns to the exact
     * chrome configuration they had — not a forced "all
     * collapsed" or "all expanded" default.
     *
     * Only mutated by the collapse reducer (snapshot write
     * BEFORE the collapse copy), never by UI code. Default
     * `true` matches [topToolbarExpanded]'s default so a
     * never-blurred UiState reads naturally.
     *
     * Guard against double-blur: the collapse reducer checks
     * `if (current.menuBarsCollapsed) return current` first,
     * so this snapshot is only written on the FIRST blur
     * edge of a blur/focus cycle. A second consecutive blur
     * (e.g. user yanks notification shade twice without
     * focusing) leaves the snapshot untouched, so the focus
     * restore later pulls the original pre-first-blur value.
     */
    val preBlurTopToolbarExpanded: Boolean = true,
    /**
     * V_NOW: see [preBlurTopToolbarExpanded] for the contract.
     * Same snapshot pattern for the bottom-right action panel.
     * Default `false` matches [bottomRightPanelExpanded]'s
     * default.
     */
    val preBlurBottomRightPanelExpanded: Boolean = false,
    /**
     * V_NOW: master switch for the auto-save feature. Mirrors
     * [com.pockref.app.data.AppPreferences.autoSaveEnabled] so the
     * settings sheet's Switch reads the current value on every
     * recomposition. When `false`, the auto-save coroutine in
     * [com.pockref.app.data.AutoSaveManager.shouldAutoSave]
     * short-circuits (loop still ticks, no file I/O) and the
     * interval slider in the settings sheet is dimmed
     * (visual cue that the value is currently inert). Default
     * `true` preserves prior behavior for users who never touch
     * the new switch — see [com.pockref.app.data.AppPreferences.autoSaveEnabled]
     * for the persistence side.
     */
    val autoSaveEnabled: Boolean = true,
    /**
     * V_NOW (follow-up after "auto-save didn't fire when I set 1 min
     * and waited 1 min" bug): wall-clock timestamp of the most
     * recent successful auto-save write, in millis since epoch.
     * `null` means "the auto-save coroutine has not yet written a
     * backup" (fresh install, fresh scene with no edits, or
     * auto-save has been disabled the whole time). Hydrated from
     * [com.pockref.app.data.AutoSaveManager.lastSaveTimestamp] in
     * [PockRefViewModel]'s `_uiState` initializer so the
     * SettingsAutoSaveSection's "last saved: ..." line reflects
     * the on-disk pref on first launch (otherwise it would say
     * "never" even if the user auto-saved 10 minutes before
     * opening the settings sheet). Updated by the auto-save
     * coroutine after every successful write so a process
     * restart doesn't lose this — the pref is the durable
     * source, the field is the in-memory mirror.
     */
    val lastAutoSaveAt: Long? = null,
    /**
     * V_NOW (follow-up after auto-save-switch task):
     * mirror of [com.pockref.app.data.AppPreferences.autoSaveIntervalMs]
     * (the *active* interval the auto-save coroutine will use on its
     * next tick). Lives in [uiState] so the SettingsAutoSaveSection
     * slider position recomposes when the user drags — the reducer
     * [com.pockref.app.ui.PureRefViewModel.setAutoSaveIntervalMs]
     * writes pref + this field in the same call. Default
     * `3_600_000L` (60 min) matches the pref default. Source of
     * truth remains the pref (the auto-save loop reads it directly);
     * this field is purely a UI mirror.
     */
    val autoSaveIntervalMs: Long = 3_600_000L,
    /**
     * V_NOW (P2-C3): one-shot "selection just changed" flash
     * marker. Non-null means the renderer should briefly tint
     * the matching [imageId] with a fade-in/fade-out alpha
     * (0.1 → 0.5 → 0.0 over 200ms) so the user gets
     * visual confirmation that the two-finger-down hit-test
     * actually replaced the selection. The marker is set
     * by [com.pockref.app.ui.PockRefAction.FlashSelection]
     * (dispatched in the same `pointerInput` handler that
     * dispatches `SelectImage(nonSelectedHit)` on a
     * two-finger-down hit of an unselected image) and is
     * allowed to naturally expire — the renderer computes
     * `now - atMs` per frame and renders alpha = 0 once
     * the elapsed exceeds 200ms. No reducer is needed
     * to clear it.
     *
     * The marker is intentionally separate from the
     * selection itself (no reducer-level effect on
     * [selectedImageIds]) so a user re-selecting the
     * same image via a single-finger tap does NOT
     * trigger a flash — that would be a constant
     * distraction.
     */
    val selectionFlash: SelectionFlash? = null,
    /**
     * V_NOW (SelectionBottomSheet "弹不出来" 修复):
     * monotonically increasing counter that the
     * pointerInput dispatches alongside
     * [PockRefAction.CycleImageUnderPoint] /
     * [PockRefAction.FitImageToScreen] (i.e. any
     * "the user just did a meaningful image tap"
     * event). The [SelectionBottomSheet] keys a
     * [kotlinx.coroutines.LaunchedEffect] on this
     * counter and calls `sheetState.show()` on
     * every tick — which re-raises the sheet
     * after the user has previously dismissed it
     * via the X / 收起 / scrim-tap path.
     *
     * Why this is needed: M3 1.2
     * [androidx.compose.material3.ModalBottomSheet]
     * uses `rememberSaveable` for its
     * [androidx.compose.material3.SheetState] and
     * fires its own
     * `LaunchedEffect(sheetState) { sheetState.show() }`
     * only ONCE per state instance. After
     * `sheetState.hide()` puts the state at
     * `SheetValue.Hidden`, the state object
     * identity stays the same across user taps,
     * so that built-in `LaunchedEffect` does not
     * re-fire. Result: the sheet never re-raises
     * after the first dismissal, even though
     * `state.selectedImageIds` is still non-empty
     * and the [SelectionBottomSheet] Composable
     * is still in the composition. The user's
     * reported bug is exactly this scenario.
     *
     * Pure state counter (no side-effect
     * dependency on the SheetState instance)
     * works around the M3 1.2 limitation: every
     * user-tap event is a brand-new counter
     * value, so the [LaunchedEffect] re-fires
     * unconditionally and calls `show()`.
     *
     * Default `0` so a fresh UiState with no
     * prior tick history is well-defined. The
     * counter is allowed to grow without bound
     * (Int.MAX_VALUE taps ≈ 70 years at 4 taps
     * / sec — well outside the app's lifetime).
     */
    val selectionSheetShowTick: Int = 0,
    // V_NOW (2026-06-08 "连续弹出两次" 修复 v2):
    // wall-clock stamp of the LAST [TickSelectionSheet]
    // bump, used by the [PockRefViewModel] reducer to
    // debounce against. The previous v1 fix lived in
    // the [PureRefApp] pointerInput block and only
    // protected the single-finger-tap path
    // ([PockRefAction.CycleImageUnderPoint] with
    // [showSheet = false] → 200ms-delayed
    // [PockRefAction.TickSelectionSheet]). It MISSED
    // the two-finger-down path at L6287 of
    // [PureRefApp] (which dispatches
    // [PockRefAction.TickSelectionSheet] DIRECTLY,
    // not through the scheduled job) — so when an
    // S Pen's barrel / a tablet's palm / a ghost
    // touch brought a second pointer down inside the
    // same gesture window, both paths bumped
    // [selectionSheetShowTick] independently, the
    // `key(selectionSheetShowTick)` block re-keyed
    // twice, and M3's
    // [LaunchedEffect(sheetState) { sheetState.show() }]
    // re-ran twice → user saw the sheet pop twice.
    //
    // Moving the debounce to the reducer means the
    // SAME 800ms window protects ALL four
    // dispatch sites:
    //   1. Single-finger tap (L6717) — scheduled job
    //   2. Two-finger down on unselected image (L6306) — direct
    //   3. Long-press on selected image in multi-select (L6541) — direct
    //   4. cycleImageUnderPoint internal bump (L735) — direct
    // The double-tap fit-image path is unaffected
    // (it doesn't dispatch [TickSelectionSheet]).
    //
    // Default `0L` so the first user-tap on an empty
    // canvas never short-circuits the debounce.
    val lastSheetShowMs: Long = 0L,
    // V_NOW: monotonically increasing counter bumped
    // when the SelectionBottomSheet should be
    // dismissed programmatically (e.g. on double-tap
    // focus/unfocus — the user's intent is to zoom,
    // not to interact with the sheet). The
    // [SelectionBottomSheet] composable keys a
    // [LaunchedEffect] on this tick and calls
    // [sheetState.hide()] when it changes.
    val selectionSheetDismissTick: Int = 0,
)

/**
 * V_NOW (P2-C3): companion record to
 * [PockRefUiState.selectionFlash]. Holds the image id
 * and the wall-clock millis at which the flash was
 * triggered, so the renderer can compute
 * `now - atMs` per frame and drive the fade-in /
 * fade-out alpha curve. See
 * [PockRefUiState.selectionFlash] for the full
 * rationale.
 */
data class SelectionFlash(
    val imageId: String,
    val atMs: Long,
)

enum class BatchOptimizeStatus {
    IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED
}

data class BatchOptimizeItemState(
    val imageId: String,
    val imageName: String,
    val status: ItemOptimizeStatus = ItemOptimizeStatus.PENDING,
    val originalSize: Int = 0,
    val optimizedSize: Int = 0,
    val error: String? = null,
)

enum class ItemOptimizeStatus {
    PENDING, RUNNING, SKIPPED, DONE, FAILED
}

data class BatchOptimizeState(
    val status: BatchOptimizeStatus = BatchOptimizeStatus.IDLE,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val items: List<BatchOptimizeItemState> = emptyList(),
    val totalOriginalBytes: Long = 0,
    val totalOptimizedBytes: Long = 0,
    val currentImageName: String = "",
    val isPaused: Boolean = false,
) {
    val progress: Float get() = if (total == 0) 0f else (completed + failed + skipped).toFloat() / total
    val savedBytes: Long get() = totalOriginalBytes - totalOptimizedBytes
    val isRunning: Boolean get() = status == BatchOptimizeStatus.RUNNING || status == BatchOptimizeStatus.PAUSED
    val hasFailures: Boolean get() = failed > 0
    val failedItems: List<BatchOptimizeItemState> get() = items.filter { it.status == ItemOptimizeStatus.FAILED }
}

fun PockRefUiState.toScene(): PockRefScene = PockRefScene(
    name = sceneName,
    backgroundColor = backgroundColor,
    images = images.sortedBy { it.zIndex },
    defaultSpacing = sceneDefaultSpacing,
    defaultAlignment = sceneDefaultAlignment,
)

/**
 * V_NOW (2026-06-08 "global image lock" feature):
 * Resolve the "effective" lock state of a single image,
 * considering both the per-image [SceneImage.locked]
 * flag AND the app-wide [PockRefUiState.globalImageLock]
 * view-mode flag. Returns `true` if EITHER is on,
 * matching the user-facing semantic "the canvas
 * should refuse to move / rotate / scale / select /
 * delete / etc. this image".
 *
 * Pass `imageId = null` to ask "is the global lock
 * on, ignoring any specific image" — used by the
 * SelectionBottomSheet's [isLockedPanel] detector
 * so the "locked" 5-button panel shows up when the
 * user taps an image while the global lock is on,
 * not just when the individual image is locked.
 *
 * This is the SINGLE entry point for "is this image
 * locked?" throughout the viewmodel. The previous
 * design had 9 call sites reading `image.locked`
 * directly — replacing those with [isImageLocked]
 * is the only way the global-lock toggle actually
 * flips behavior across the whole app, and the
 * single entry point means future lock-like
 * constraints (e.g. "image in an active crop
 * gesture is locked") only need to be OR'd in
 * here, not hunted down across the codebase.
 */
fun PockRefUiState.isImageLocked(imageId: String?): Boolean {
    if (globalImageLock) return true
    if (imageId == null) return false
    return images.firstOrNull { it.id == imageId }?.locked == true
}
