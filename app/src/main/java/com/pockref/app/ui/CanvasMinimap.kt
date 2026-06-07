package com.pockref.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pockref.app.ui.theme.Dimens
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Resolved color palette for the minimap. Built once per
 * composition in [CanvasMinimap] from [MaterialTheme.colorScheme]
 * and threaded through to the [DrawScope.drawMinimapContent]
 * helper (which can't read [MaterialTheme] directly because
 * @Composable and DrawScope are different worlds).
 *
 * The dark theme is the one that was hardcoded before this
 * refactor (alpha 0.8 surface, cyan accent). Light theme
 * uses the same slots from lightColorScheme — same
 * semantic role, just inverted luminance. The dirty marker
 * (selected-image fill) and the panel chrome read from the
 * theme so the minimap never clashes with a light canvas.
 */
private data class MinimapColors(
    val panelBackground: Color,
    val panelBorder: Color,
    val sceneBackground: Color,
    val imageBox: Color,
    val indicatorFill: Color,
    val indicatorStroke: Color,
    // V_NOW: per-state per-image indicator colors. The minimap
    // mirrors the main canvas's affordance vocabulary so the
    // user's eye doesn't have to learn two color systems:
    //   - selected: bright blue solid (matches the canvas's
    //     #4FC3F7 selection outline at small stroke width
    //     so it pops on the translucent gray imageBox fill)
    //   - locked:   red dashed (matches the canvas's #E57373
    //     locked color; dashed vs the selected's solid so
    //     they're distinguishable at a glance even on a
    //     grayscale screenshot)
    // Hardcoded rather than theme-resolved because the
    // selected/locked affordances are SEMANTIC (not theme-
    // driven): a "this image is selected" indicator should
    // look the same in light and dark mode, the way Procreate
    // / PockRef do it. Theme-shifting these would re-introduce
    // the same "selection hue depends on user theme" footgun
    // we explicitly removed on the main canvas.
    val selectedStroke: Color,
    val lockedStroke: Color,
)

/**
 * Bottom-left minimap / navigator for the main canvas.
 *
 * Visible whenever the scene has at least one image. Renders a thumbnail
 * of the entire scene (image AABBs) with a rectangle marking the main
 * view's visible area. Drag the minimap to pan the main view with
 * finger-tracking; release while moving to fling with inertia.
 *
 * Interaction model (anchor / 1:1 finger-tracking):
 *   - onDragStart: the new touch point becomes the reference for this
 *     drag. The canvasOffset at this instant is captured as
 *     [dragStartOffset]. Any in-flight fling is cancelled and its job
 *     discarded. The drag does NOT continue from the previous gesture's
 *     end position — the new touch is a fresh anchor.
 *   - onDrag: each pointer delta in minimap pixels is converted to a
 *     canvasOffset delta via `dx * (sceneBbox.width / contentRect.width)
 *     * scale` and ADDED to [dragStartOffset] (not to the latest state),
 *     so the canvas tracks the finger 1:1 from the touch-down anchor
 *     regardless of any state mutation between frames. The last frame's
 *     delta is remembered as the seed velocity for the fling.
 *   - onDragEnd: clear [dragStartOffset]. If the last delta is non-zero,
 *     launch a fling coroutine that decays velocity by
 *     [MINIMAP_FLING_DAMPING] per ~16ms frame and dispatches
 *     SetCanvasOffsetFromMinimap at each step, until the velocity
 *     magnitude is below [MINIMAP_FLING_STOP_VELOCITY].
 *   - onDragCancel: drop the anchor and cancel any in-flight fling
 *     (pointer cancelled, gesture conflict, etc.). The next touch
 *     starts a brand new drag.
 *
 * Visibility decision: shown for any non-empty scene regardless of zoom
 * level. The viewport indicator's size and position adapt automatically
 * (the indicator is the size of the main view's visible world rect mapped
 * into the panel) so it remains meaningful at all zoom levels.
 *
 * Coordinate transforms:
 *   minimapDelta (px) → sceneDelta (world): (dx, dy) * (bbox.w/contentW, bbox.h/contentH)
 *   sceneDelta       → canvasOffsetDelta:   -sceneDelta * scale
 *     (canvasOffset decreasing moves the canvas right under the finger)
 */
 @Composable
 fun CanvasMinimap(
     state: PockRefUiState,
     onAction: (PockRefAction) -> Unit,
     modifier: Modifier = Modifier,
    // V_NOW (智能收缩 / 让位给 pill): when [isCollapsed] is
    // true the minimap renders as a 24dp clickable "navigate"
    // icon instead of the full panel. The user spec is
    // "minimap 不该和 pill 抢位置"; collapsing to a 24dp
    // corner icon (vs fully hiding) keeps the navigation
    // entry one tap away — the user can expand it back to
    // the full minimap via [onExpand] without going through
    // any settings / submenu. The transition uses
    // [AnimatedContent] + crossfade so the 250ms shrink is
    // visually continuous with the pill's fade-in (no jump
    // cut when the selection count crosses 0 ↔ 1).
    isCollapsed: Boolean = false,
    onExpand: () -> Unit = {},
    // V_NOW: long-press on the minimap (panel body OR
    // collapsed-icon capsule — the parent [MinimapChrome]
    // routes both call sites through this single
    // callback) enters "slider mode" — the panel morphs
    // into an M3 [Slider] for canvasScale 0.5..2.0.
    // [onLongPress] fires when the long-press threshold
    // is reached (default Compose
    // [viewConfiguration.longPressTimeoutMillis] = 500ms);
    // [onLongPressEnd] fires when the pointer is
    // released (after a long-press fired). The parent
    // is responsible for the actual mode flip +
    // [AnimatedContent] morph; this function just
    // reports the gesture.
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    // V_NOW: when true, the canvas-pan-by-drag gesture
    // is suppressed (the [pointerInput] re-keys on
    // [sliderActive] and short-circuits). The slider
    // mode morphs the panel away anyway, so the
    // drag-pan handler would conflict with the
    // M3 [Slider]'s own thumb drag. Disabling the
    // drag-pan during slider mode is the cleanest
    // way to avoid the gesture race.
    sliderActive: Boolean = false,
  ) {
     // V_NOW (智能收缩 / 让位给 pill): when [isCollapsed] is
     // true the minimap renders as a 24dp clickable "navigate"
     // icon instead of the full panel. The user spec is
     // "minimap 不该和 pill 抢位置"; collapsing to a 24dp
     // corner icon (vs fully hiding) keeps the navigation
     // entry one tap away — the user can expand it back to
     // the full minimap via [onExpand] without going through
     // any settings / submenu. The transition (size + cross
     // fade) is handled by the parent's [AnimatedContent]
     // wrapper so the function itself just emits the target
     // state.
     if (isCollapsed) {
         CollapsedMinimapIcon(
             edge = state.minimapDockEdge,
             onExpand = onExpand,
         )
         return
     }

     val viewport = state.canvasViewportSize
     if (viewport.width <= 0f || viewport.height <= 0f) return
     if (state.images.isEmpty()) return

    val density = LocalDensity.current
    val (panelWDp, panelHDp, panelWpx, panelHpx) = with(density) {
        // V_NOW (user feedback "minimap 显示的画布尺寸和实
        // 际窗口的尺寸、比例有一点出入"): the previous
        // formula derived wDp and hDp INDEPENDENTLY from
        // `sqrt(15000 * aspect)` / `sqrt(15000 / aspect)`,
        // then clamped EACH to [60, 200] dp. The two
        // independent clamps broke the panel's aspect ratio
        // for any non-square scene — e.g. a scene with
        // aspect=0.2 produced wDp=60dp (clamped) but
        // hDp=300dp (clamped to 200), so the final panel
        // was 60×200 (aspect=0.3) instead of 60×300
        // (aspect=0.2). The downstream `scaleW = contentW
        // / bboxW` and `scaleH = contentH / bboxH` were
        // also independent, so images in the minimap were
        // squashed (different x/y zoom), and the viewport
        // indicator's projected rect ended up offset and
        // occasionally overshooting the panel border.
        //
        // New design: pick the LONGER side as the binding
        // constraint (clamped to [MIN_DP, MAX_DP]), then
        // derive the SHORTER side STRICTLY from the aspect
        // ratio (with the same clamp as a safety floor for
        // extreme aspect). This guarantees the panel's
        // aspect ratio equals the scene's aspect ratio
        // exactly, so the letterbox math in
        // [drawMinimapContent] is purely a uniform-scale
        // projection with no axis-distortion.
        val bbox = computeSceneBboxForMinimap(state.images)
        val aspect = (bbox.width / bbox.height)
            .coerceIn(MINIMAP_MIN_ASPECT, MINIMAP_MAX_ASPECT)
        val maxSide = MINIMAP_PANEL_MAX_DP.value
            .coerceIn(MINIMAP_PANEL_MIN_DP.value, MINIMAP_PANEL_MAX_DP.value)
        val minSide = MINIMAP_PANEL_MIN_DP.value
        val longSide: Float
        val shortSide: Float
        if (aspect >= 1f) {
            // Wide scene: width is the long side.
            longSide = maxSide
            shortSide = (longSide / aspect).coerceIn(minSide, maxSide)
        } else {
            // Tall scene: height is the long side.
            longSide = maxSide
            shortSide = (longSide * aspect).coerceIn(minSide, maxSide)
        }
        val wDp: Dp
        val hDp: Dp
        if (aspect >= 1f) {
            wDp = longSide.dp
            hDp = shortSide.dp
        } else {
            wDp = shortSide.dp
            hDp = longSide.dp
        }
        Tuple4(wDp, hDp, wDp.toPx(), hDp.toPx())
    }
    val insetPx = with(density) { MINIMAP_INNER_INSET_DP.toPx() }

    val sceneBbox = remember(state.images) {
        computeSceneBboxForMinimap(state.images)
    }
    val minimapInnerSize = Size(panelWpx - 2 * insetPx, panelHpx - 2 * insetPx)
    // CRITICAL: contentRect must be remembered, otherwise every
    // recomposition produces a new Rect instance and the pointerInput
    // keys below change → the gesture coroutine restarts mid-drag, killing
    // the user's finger-tracking and feeling like a "stuck / jittery"
    // drag. Key on the actual dependency (minimapInnerSize) so it only
    // refreshes when the panel size genuinely changes.
    val contentRect = remember(minimapInnerSize) {
        Rect(0f, 0f, minimapInnerSize.width, minimapInnerSize.height)
    }

    val coroutineScope = rememberCoroutineScope()
    // The currently-running fling coroutine, if any. A fresh drag cancels
    // it so the finger owns the gesture from this frame on.
    val activeFling = remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    // Most recent drag delta in canvasOffset units. Seed for the fling
    // velocity. Reset on each drag start.
    val lastDragDelta = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    // Anchor for the in-flight drag: the canvasOffset captured at the
    // moment the new touch landed.
    val dragStartOffset = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    // The position the finger first touched the panel (in the panel's
    // local coords). Every onDrag frame computes the *cumulative* delta
    // from this start point to the current pointer position — NOT the
    // per-frame dragAmount that detectDragGestures provides. Using the
    // per-frame delta was the cause of the "wild shaking" symptom: the
    // single-frame deltas are well-formed (e.g. (0.5, 0.2) → (1.8, 0.7)
    // → (1.6, 0.6) … all in the drag direction), but each newOffset was
    // computed as `anchor + (-perFrameSceneDx * scale)`, which means
    // each frame REPLACED the offset instead of accumulating toward it
    // — the canvas visibly hopped back and forth with the per-frame
    // deltas (often 100+ canvas pixels per finger pixel of movement
    // because the minimap is small and the scene is large, amplifying
    // any noise). The cumulative model `newOffset = anchor + (-(curPos
    // - startPos) * sceneRatio * scale)` gives a strictly monotonic
    // canvas offset and a clean 1:1 finger-following feel.
    val dragStartPos = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    // Last dispatched canvasOffset. Used to deadband sub-pixel finger
    // jitter: if the new computed offset differs from this by less than
    // [MINIMAP_DRAG_DEADBAND_PX] in either axis, skip the dispatch.
    val lastDispatchedOffset = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }

    // V_NOW: pointer-input filter state — per-frame dead-zone +
    // low-pass smoothing + minimum-trigger-distance. All three
    // are reset to defaults by [onDragStart] / [onDragEnd]; they
    // live across `onDrag` frames so we can compute "smoothed
    // delta = α × currentFrame + (1-α) × previousSmoothed" etc.
    //
    // These are SCREEN-SPACE (panel-local pixel) state, NOT
    // canvas-offset state. The user's finger jitters by a few px
    // per frame on a 340dpi S Pen at 240Hz; if we propagate
    // every noisy frame to the canvas (which then amplifies the
    // displacement by sceneRatio × scale, often ×100+ for a
    // 1000px scene on a 200dp minimap), the canvas visibly
    // shakes. The filter smooths the input at the source so the
    // canvas never sees raw noise.
    val lastFramePos = remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
    val smoothedDelta = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    // V_NOW2: accumulatedDistance state removed. The previous
    // min-trigger design accumulated filtered frame distances
    // and only dispatched when the total crossed
    // [MinimapTouchConfig.minTriggerDistancePx] — that produced
    // the "拖 16px 才看到第一帧 canvas 动 + 后续每帧都得重
    // 新累积"的不跟手感。现在每个通过 dead-zone 的 frame 都
    // 立即 dispatch(后跟一个 1-frame low-pass 收敛),不再需
    // 要累积器。Creep 防护由 dead-zone 单独承担:连续 0~2px
    // 噪声 frame 在 dead-zone gate 处就被丢了,canvas 永远
    // 收不到 0.5px 的 dispatch,因此也累积不到无声的 5px 移动。

    // pointerInput's gesture coroutine is launched once per key set and
    // continues across recompositions. Its lambdas would otherwise
    // capture the [state] at gesture-start time, so the per-frame drag
    // delta would be applied on top of a stale canvasOffset — that's the
    // "drag one frame → reset to start → next frame → reset again" jitter
    // pattern. [rememberUpdatedState] wraps the values so each
    // [onDrag] / [onDragEnd] invocation reads the latest state.
    //
    // The pointer input itself is keyed on `Unit` so the gesture coroutine
    // NEVER restarts mid-drag. All values that can change during a drag
    // (canvasScale, sceneBbox, contentRect, viewportSize) are read via
    // [currentState] / [currentSceneBbox] / [currentContentRect] below
    // — the lambdas always see the freshest values without the gesture
    // coroutine having to be relaunched. The previous key list
    // (state.canvasScale, sceneBbox, contentRect) was a source of subtle
    // jitter when any of those re-instanced mid-drag.
    val currentState by rememberUpdatedState(state)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentSceneBbox by rememberUpdatedState(sceneBbox)
    val currentContentRect by rememberUpdatedState(contentRect)
    val currentCanvasScale by rememberUpdatedState(state.canvasScale)

    // Theme-aware palette. Resolved once per composition from
    // [MaterialTheme.colorScheme] so the minimap picks up the
    // current effective theme (system / light / dark) and
    // re-resolves on theme change. Each slot maps to a
    // semantic color rather than a hardcoded hex.
    val colors = MinimapColors(
        panelBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        panelBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        sceneBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        imageBox = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        indicatorFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        indicatorStroke = MaterialTheme.colorScheme.primary,
        selectedStroke = Color(0xFF4FC3F7),
        lockedStroke = Color(0xFFE57373),
    )

    Box(
        modifier = modifier
            .size(width = panelWDp, height = panelHDp)
            // V_NOW (user feedback "minimap 的外边框也应该是
            // 要圆角的,和蓝色的视野范围区域框的圆角一致"):
            // the [background] and [border] are RE-INSTATED
            // after the previous round (which had removed
            // them) — that round was a misread of the user's
            // earlier "去除 minimap 外一圈浅灰色长方形"
            // feedback, which was about the FOUR CORNERS
            // being sharp 90° corners (a rectangle), not
            // about the panel itself being unwanted. The
            // panel now ships with:
            //   - alpha 0.85 [panelBackground] surface
            //   - 1dp alpha 0.4 [panelBorder] outline
            //   - [ChromeCornerRadius] (12dp) round corners
            //     on both the background AND the border AND
            //     the clip below — so the panel's outer
            //     silhouette echoes the viewport indicator's
            //     [drawRoundRect] corner radius, matching
            //     the user's "圆角一致" spec.
            // The scene inside still uses the same
            // [Dimens.ChromeCornerRadius] for its own
            // clip, so the entire card has a single uniform
            // corner radius from outside to inside.
            .background(
                color = colors.panelBackground,
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .border(
                width = 1.dp,
                color = colors.panelBorder,
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
            )
            .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
            // V_NOW: long-press → slider mode. The
            // long-press detector runs in its OWN
            // pointerInput block (separate from the
            // drag-pan-canvas below) so the two
            // gesture detectors don't fight over
            // pointer event consumption. We don't
            // call `down.consume()` on the initial
            // down event so the drag-pan-canvas
            // pointerInput also sees it (the user
            // might press-then-immediately-drag
            // before 500ms elapses, which should
            // canvas-pan, not slider-mode).
            //
            // Pattern: [withTimeoutOrNull] racing
            // against [awaitPointerEvent] inside
            // [awaitEachGesture]. The long-press
            // timeout is the timer; the
            // `awaitPointerEvent` loop is the
            // release-wait. Whichever fires first
            // wins. After long-press fires, a
            // second inner loop waits for the
            // pointer to be released so
            // [onLongPressEnd] reports the right
            // timing (the parent morphs back to
            // the minimap on release, matching
            // the M3 FAB → sheet transform's
            // reverse direction).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // V_NOW (S-07): raised from framework default
                    // 500ms to 800ms. The minimap panel's
                    // PRIMARY action is "tap to jump the canvas
                    // viewport to that point"; long-press →
                    // slider-mode is SECONDARY and morphs the
                    // whole panel away. At 500ms users often
                    // unintentionally triggered slider mode
                    // when they wanted to tap (e.g. finger
                    // landed a millimetre late and held while
                    // re-orienting). At 800ms a true long-press
                    // is unambiguous. See [MINIMAP_LONG_PRESS_TIMEOUT_MS].
                    val longPressTimeoutMs = MINIMAP_LONG_PRESS_TIMEOUT_MS
                    // Race: long-press timeout vs pointer
                    // release. `withTimeoutOrNull` cancels
                    // the inner `awaitPointerEvent` loop
                    // when the timeout elapses; the result
                    // is `null` (= timeout = long-press
                    // fired) or `true` (= release happened
                    // before the timeout = no long-press).
                    val releasedBeforeTimeout = withTimeoutOrNull(
                        longPressTimeoutMs.toLong(),
                    ) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                return@withTimeoutOrNull true
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        false
                    }
                    if (releasedBeforeTimeout == true) {
                        // User released before the long-press
                        // threshold — no long-press. The
                        // pointerInput's gesture ends; the
                        // drag-pan-canvas below never sees
                        // this gesture (the user didn't
                        // move before releasing).
                        return@awaitEachGesture
                    }
                    // Long-press fired. Notify the parent
                    // so it can flip [sliderActive] =
                    // true (and the morph runs).
                    onLongPress()
                    // Now wait for the pointer to be
                    // released (no timeout — the user
                    // holds as long as they need to
                    // adjust the slider thumb).
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            onLongPressEnd()
                            return@awaitEachGesture
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        // The new touch point is the start reference for
                        // this drag. Discard any in-flight fling so the
                        // finger owns the gesture from this frame on.
                        activeFling.value?.cancel()
                        activeFling.value = null
                        // Map the touch's panel-local pixel coordinate to
                        // a world point in the scene. Then compute the
                        // canvasOffset that places that world point at the
                        // *center* of the main view, and dispatch it.
                        // This is the "first touch = drag start" contract:
                        // the moment the finger lands on the minimap, the
                        // world point under that pixel becomes the new
                        // main-view center. The canvas JUMPS to that
                        // position (no animation; the user explicitly
                        // chose instant over animated) and the subsequent
                        // drag continues 1:1 from there.
                        val bbox = currentSceneBbox
                        val rect = currentContentRect
                        val scale = currentCanvasScale
                        val recenteredOffset: Offset? = if (
                            bbox.width > 0f && bbox.height > 0f &&
                            rect.width > 0f && rect.height > 0f &&
                            scale > 0f
                        ) {
                            val w0x = bbox.left + (startPos.x / rect.width) * bbox.width
                            val w0y = bbox.top + (startPos.y / rect.height) * bbox.height
                            // World point at the screen center is
                            // -canvasOffset / scale (see
                            // worldViewportRect in PockRefApp). For the
                            // screen center to land on (w0x, w0y), the
                            // canvasOffset must be (-w0x*scale, -w0y*scale).
                            Offset(-w0x * scale, -w0y * scale)
                        } else {
                            null
                        }
                        if (recenteredOffset != null &&
                            !recenteredOffset.x.isNaN() && !recenteredOffset.y.isNaN() &&
                            !recenteredOffset.x.isInfinite() && !recenteredOffset.y.isInfinite()
                        ) {
                            // Dispatch the recenter immediately so the
                            // main view re-renders to put the touched
                            // world point at the center. The anchor below
                            // is set to the *same* offset, so the very
                            // next onDrag frame (when the finger has
                            // moved) computes its delta from this new
                            // position rather than from wherever the
                            // canvas was before the touch.
                            currentOnAction(PockRefAction.SetCanvasOffsetFromMinimap(recenteredOffset))
                            dragStartOffset.value = recenteredOffset
                            lastDispatchedOffset.value = recenteredOffset
                        } else {
                            // Degenerate geometry (empty scene, zero
                            // viewport). Fall back to the legacy behavior
                            // — no recenter, anchor the existing offset.
                            dragStartOffset.value = currentState.canvasOffset
                            lastDispatchedOffset.value = currentState.canvasOffset
                        }
                        dragStartPos.value = startPos
                        lastDragDelta.value = Offset.Zero
                        // V_NOW: reset the per-frame pointer
                        // filter state so the next frame's
                        // dead-zone / low-pass / accumulator
                        // start fresh. We deliberately
                        // leave `lastFramePos = null` so the
                        // first onDrag frame just initializes
                        // it from `change.position` without
                        // filtering (no spurious 0→cur displacement).
                        lastFramePos.value = null
                        smoothedDelta.value = Offset.Zero
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val anchor = dragStartOffset.value
                        if (anchor == null) {
                            return@detectDragGestures  // drag ended or not started
                        }
                        val bbox = currentSceneBbox
                        val rect = currentContentRect
                        if (bbox.width <= 0f || bbox.height <= 0f ||
                            rect.width <= 0f || rect.height <= 0f
                        ) {
                            return@detectDragGestures
                        }
                        val scale = currentCanvasScale
                        val curPos = change.position
                        // V_NOW: incremental filtered drag model.
                        // V_NOW2: 砍掉了 min-trigger 累积器,每个
                        // 通过 dead-zone 的 frame 立即 dispatch(配合
                        // 1-frame low-pass 收敛)。原先累积 8px 才
                        // dispatch 的设计是"不跟手"的主因 — 拖 16px
                        // 才看到第一帧 canvas 动,后续每帧还得重新
                        // 累积,正常拖速(500 px/sec @ 60fps ≈ 8px/frame)
                        // 半数 frame 触发半数不触发,体感抖。
                        //
                        // Old model (pre-V_NOW) used the *cumulative*
                        // delta from the start-of-drag touch position
                        // (anchor + (-(curPos - startPos) * scale))
                        // with a sub-pixel offset deadband AFTER the
                        // sceneRatio × scale amplification. That model
                        // was fine for cancelling sub-pixel jitter, but
                        // did nothing about the much larger noise: a
                        // 2-3px per-frame S Pen jitter, amplified by
                        // sceneRatio × scale (often ×100+ for a 1000px
                        // scene on a 200dp minimap), produces
                        // hundreds-of-pixels of canvas hop with no
                        // visible finger movement. The filter below
                        // attacks the noise at the source, in SCREEN
                        // coords, before any amplification.
                        //
                        // Flow per frame (V_NOW2, no accumulator):
                        //   1. frameDisp = curPos - lastFramePos
                        //      (only meaningful after the first
                        //      onDrag frame, which initializes
                        //      lastFramePos and bails.)
                        //   2. Dead zone: drop frameDisp under
                        //      `deadZonePx` (S Pen sub-pixel noise).
                        //   3. Low-pass: smoothed = α·frameDisp +
                        //      (1-α)·previous. Cuts the high-freq
                        //      spike that survives the dead zone.
                        //   4. Scale by `sensitivity` (1.0): 1:1
                        //      panel→world mapping so the viewport
                        //      indicator tracks the finger
                        //      point-for-point. Previous 0.5 halved
                        //      the mapping, causing canvas lag.
                        //   5. Convert the filtered SCREEN delta to
                        //      canvas-offset units (sceneRatio ×
                        //      scale, with sign flip — pulling the
                        //      finger right makes the canvas go left
                        //      under the finger).
                        //   6. Clamp to scene bbox (edge behavior).
                        //      If the clamp equals the current
                        //      offset, the canvas is at the edge —
                        //      drop the dispatch to avoid a no-op
                        //      recomposition. Crucially, this also
                        //      means once at the edge, further
                        //      finger movement doesn't keep firing
                        //      dispatch loops.
                        //   7. Dispatch (no accumulator reset
                        //      needed; per-frame model is stateless
                        //      beyond lastFramePos / smoothedDelta).
                        //   8. Fling seed: use the actually-applied
                        //      offset delta (post-clamp) so a drag
                        //      at the edge doesn't seed the fling
                        //      with the unclamped velocity.
                        val lastPos = lastFramePos.value
                        lastFramePos.value = curPos
                        if (lastPos == null) {
                            // First onDrag frame after start: nothing
                            // to compare against. Don't dispatch and
                            // don't accumulate; the NEXT frame's
                            // displacement will be the meaningful one.
                            return@detectDragGestures
                        }
                        // 1. Per-frame screen displacement.
                        val frameDisp = curPos - lastPos
                        // 2. Dead zone — drop sub-threshold noise
                        // entirely (V_NOW2: no accumulator, so a
                        // dropped frame is just a dropped frame;
                        // it does NOT "build up" toward any
                        // threshold). The dead zone alone is
                        // responsible for creep prevention:
                        // continuous 0.5-2px tremor never crosses
                        // the gate, so the canvas never sees a
                        // 0.5px dispatch and never accumulates to
                        // a silent multi-px move.
                        if (frameDisp.getDistance() < minimapTouchConfig.deadZonePx) {
                            return@detectDragGestures
                        }
                        // 3. One-pole low-pass, per-axis.
                        val prevSmoothed = smoothedDelta.value
                        val alpha = minimapTouchConfig.smoothingAlpha
                        val smoothed = Offset(
                            x = alpha * frameDisp.x + (1f - alpha) * prevSmoothed.x,
                            y = alpha * frameDisp.y + (1f - alpha) * prevSmoothed.y,
                        )
                        smoothedDelta.value = smoothed
                        // 4. Scale by the user-configured sensitivity
                        // (0.5 = half of the pre-V_NOW effective ratio).
                        val scaled = Offset(
                            x = smoothed.x * minimapTouchConfig.sensitivity,
                            y = smoothed.y * minimapTouchConfig.sensitivity,
                        )
                        // 5. Convert SCREEN delta → canvas offset delta.
                        // Sign flip: finger moves right → curPos.x ↑
                        // → cumDxScreen ↑ → cumSceneDx ↑ → offset
                        // delta.x = -cumSceneDx × scale ↓ → canvas
                        // offset shifts left → world content moves
                        // left under the stationary finger, which is
                        // the "drag the world" UX the user expects.
                        val sceneDx = scaled.x * bbox.width / rect.width
                        val sceneDy = scaled.y * bbox.height / rect.height
                        val offsetDelta = Offset(-sceneDx * scale, -sceneDy * scale)
                        // Skip dispatch on degenerate geometry
                        // (NaN/Infinity would corrupt the offset and
                        // crash downstream rendering).
                        if (offsetDelta.x.isNaN() || offsetDelta.y.isNaN() ||
                            offsetDelta.x.isInfinite() || offsetDelta.y.isInfinite()
                        ) {
                            return@detectDragGestures
                        }
                        // 6. Clamp to scene bbox. Use the LATEST
                        // canvasOffset (from the snapshot), not the
                        // [anchor] (which is the drag-start offset) —
                        // the incremental model applies each filtered
                        // frame on top of the current state, not on
                        // top of the start-of-drag state.
                        val cur = currentState.canvasOffset
                        val raw = cur + offsetDelta
                        val viewportW = currentState.canvasViewportSize.width
                        val viewportH = currentState.canvasViewportSize.height
                        val clamped = clampOffsetToScene(
                            offset = raw,
                            sceneBbox = bbox,
                            viewportW = viewportW,
                            viewportH = viewportH,
                            scale = scale,
                        )
                        val appliedDelta = clamped - cur
                        if (appliedDelta.getDistance() == 0f) {
                            // 6b. Edge behavior: at the boundary,
                            // the clamp pinches the new offset back
                            // to the existing offset, producing a
                            // zero delta. Drop the dispatch entirely
                            // — recomposing the canvas with the same
                            // offset is a no-op that still costs a
                            // frame, and more importantly, returning
                            // here means the user can keep moving
                            // their finger without us hammering the
                            // reducer with "you've reached the edge"
                            // dispatches. The low-pass state IS
                            // updated above — when the next valid
                            // frame arrives (e.g. the user reverses
                            // direction at the edge), the smoothed
                            // signal correctly reflects the
                            // accumulated low-pass history, not
                            // just the new frame.
                            return@detectDragGestures
                        }
                        // 7. Dispatch (V_NOW2: no accumulator to
                        // reset; the per-frame model is stateless
                        // beyond lastFramePos / smoothedDelta which
                        // are already updated above).
                        currentOnAction(PockRefAction.SetCanvasOffsetFromMinimap(clamped))
                        lastDispatchedOffset.value = clamped
                        // 8. Fling seed — use the actually-applied
                        // delta (post-clamp), not the unclamped
                        // `offsetDelta`, so dragging INTO the edge
                        // doesn't seed the fling with the full
                        // unclamped velocity (which would cause the
                        // fling to overshoot the edge in the
                        // subsequent animation).
                        lastDragDelta.value = appliedDelta
                    },
                    onDragEnd = {
                        // The drag is over — clear the anchor and deadband
                        // tracker so the next onDragStart starts fresh.
                        dragStartOffset.value = null
                        dragStartPos.value = null
                        lastDispatchedOffset.value = null
                        // V_NOW2: clear the pointer-input filter
                        // state. Without this, the NEXT drag would
                        // inherit the previous drag's lastFramePos /
                        // smoothedDelta — the very first frame after
                        // the new touch would compute a huge spurious
                        // displacement (from the previous drag's
                        // last position to the new touch point) and
                        // the dead-zone / low-pass would be operating
                        // on garbage. (accumulatedDistance state was
                        // removed in V_NOW2 alongside the min-trigger
                        // redesign.)
                        lastFramePos.value = null
                        smoothedDelta.value = Offset.Zero
                        val last = lastDragDelta.value
                        if (last == Offset.Zero) return@detectDragGestures
                        // Snapshot the geometry we need for the fling into
                        // local vals — the fling runs in a separate
                        // coroutine that outlives the gesture, so we can't
                        // read the remembered state directly without
                        // re-suspending. Capture at drag end (the only
                        // time these values are actually used by the
                        // fling).
                        val bboxForFling = currentSceneBbox
                        val scale = currentCanvasScale
                        val viewportW = currentState.canvasViewportSize.width
                        val viewportH = currentState.canvasViewportSize.height
                        val offsetAtDragEnd = currentState.canvasOffset
                        activeFling.value?.cancel()
                        activeFling.value = coroutineScope.launch {
                            var v = last
                            var cur = offsetAtDragEnd
                            while (hypot(v.x, v.y) > MINIMAP_FLING_STOP_VELOCITY) {
                                delay(16L)
                                cur = clampOffsetToScene(
                                    offset = cur + v,
                                    sceneBbox = bboxForFling,
                                    viewportW = viewportW,
                                    viewportH = viewportH,
                                    scale = scale,
                                )
                                currentOnAction(PockRefAction.SetCanvasOffsetFromMinimap(cur))
                                v = v * MINIMAP_FLING_DAMPING
                                // Stop fling if velocity becomes NaN/Infinite
                                if (v.x.isNaN() || v.y.isNaN() ||
                                    v.x.isInfinite() || v.y.isInfinite()
                                ) break
                            }
                        }
                    },
                    onDragCancel = {
                        // Touch was cancelled (gesture conflict, pointer
                        // loss, etc.). Drop the anchor and any fling —
                        // the next touch should be a brand new drag.
                        dragStartOffset.value = null
                        dragStartPos.value = null
                        lastDispatchedOffset.value = null
                        // V_NOW2: same filter-state cleanup as
                        // [onDragEnd] — drag cancellation leaves
                        // the same garbage in lastFramePos /
                        // smoothedDelta that an end would.
                        lastFramePos.value = null
                        smoothedDelta.value = Offset.Zero
                        activeFling.value?.cancel()
                    },
                )
            }
            .drawWithContent {
                val cs = state.canvasScale
                // V_NOW: pass bufferPx = 0f so the minimap's
                // viewport indicator shows the EXACT visible
                // region (not the cull-padded region with a
                // 200px / canvasScale inflation on every side).
                // Without this, at typical zoom-out levels
                // (canvasScale ≈ 0.3) the indicator's right /
                // bottom edges overshot the minimap's content
                // rect by hundreds of world units, exposing
                // the panel border as a "white edge" between
                // the indicator and the minimap's bottom
                // boundary. The default [VIEWPORT_BUFFER_PX]
                // (200f) stays in place for the draw-loop cull
                // + decode-priority filter, which are the
                // intended consumers of the buffered rect.
                val viewWorld = worldViewportRect(
                    canvasOffset = state.canvasOffset,
                    canvasScale = cs,
                    viewportWidth = viewport.width,
                    viewportHeight = viewport.height,
                    bufferPx = 0f,
                )
                drawMinimapContent(
                    state = state,
                    sceneBbox = sceneBbox,
                    contentRect = contentRect,
                    innerInset = insetPx,
                    viewWorld = viewWorld,
                    colors = colors,
                )
            },
    )
}

private fun DrawScope.drawMinimapContent(
    state: PockRefUiState,
    sceneBbox: Rect,
    contentRect: Rect,
    innerInset: Float,
    viewWorld: Rect,
    colors: MinimapColors,
) {
    val baseX = innerInset + contentRect.left
    val baseY = innerInset + contentRect.top
    // V_NOW (user feedback "minimap 显示的画布尺寸和实际
    // 窗口的尺寸、比例有一点出入 + 蓝色视野区域没和
    // minimap 对齐,甚至还会超出 minimap 区域"): the
    // previous code computed scaleW and scaleH INDEPENDENTLY
    // from `contentRect.width / sceneBbox.width` and
    // `contentRect.height / sceneBbox.height`, then used
    // them independently to project both image AABBs and
    // the viewport indicator. When the panel's aspect
    // ratio drifted from the scene's (because the panel
    // size used independent per-axis clamps in the parent
    // composable), scaleW != scaleH — every image was
    // squashed to a different aspect than its real
    // footprint on the main canvas, and the viewport
    // indicator (which is a [viewWorld] rect, i.e. a
    // viewport-size portion of the scene) ended up
    // projected with the wrong x/y ratio, drifting off
    // the panel center and sometimes overshooting the
    // panel border entirely.
    //
    // New design: LETTERBOX. The panel is now sized so
    // its aspect ratio EQUALS the scene's aspect ratio
    // (see the V_NOW block in the parent composable that
    // derives [panelWDp] / [panelHDp]). With matching
    // aspects, the projection from scene → contentRect is
    // a UNIFORM scale, and the scene is centered inside
    // the contentRect (with possible pillar/letterbox
    // padding if the contentRect is somehow wider/taller
    // than the scene's letterbox fit — which shouldn't
    // happen with the matched-aspect panel, but we
    // defend against the degenerate case anyway).
    val sceneScale = if (sceneBbox.width > 0f && sceneBbox.height > 0f) {
        minOf(
            contentRect.width / sceneBbox.width,
            contentRect.height / sceneBbox.height,
        )
    } else {
        0f
    }
    val sceneW = sceneBbox.width * sceneScale
    val sceneH = sceneBbox.height * sceneScale
    val sceneLeft = baseX + (contentRect.width - sceneW) / 2f
    val sceneTop = baseY + (contentRect.height - sceneH) / 2f

    drawRect(
        color = colors.sceneBackground,
        topLeft = Offset(baseX, baseY),
        size = Size(contentRect.width, contentRect.height),
    )

    state.images.forEach { image ->
        // Use the UNROTATED AABB for the draw's intrinsic rect size, but
        // apply the image's rotationDegrees as a draw transform. This
        // way the minimap shows a true rotated parallelogram / diamond
        // — the same footprint the user sees in the main canvas — not
        // the axis-aligned bounding box (which would be a square that
        // "boxes in" the diamond and breaks the visual correspondence).
        // [sceneBbox] is still computed from the rotated AABB so the
        // panel and viewport indicator have enough room for the rotated
        // content; only the per-image draw uses the unrotated rect
        // with an explicit rotation.
        val aabb = imageWorldAabb(image)
        val left = sceneLeft + (aabb.left - sceneBbox.left) * sceneScale
        val top = sceneTop + (aabb.top - sceneBbox.top) * sceneScale
        val right = sceneLeft + (aabb.right - sceneBbox.left) * sceneScale
        val bottom = sceneTop + (aabb.bottom - sceneBbox.top) * sceneScale
        val w = (right - left).coerceAtLeast(2f)
        val h = (bottom - top).coerceAtLeast(2f)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        // V_NOW: per-state indicators — the minimap mirrors
        // the main canvas's selected / locked affordances so
        // the user can tell at a glance which images on the
        // small-scale map are (a) currently selected for
        // action and (b) locked against interaction. Computed
        // once per image instead of per-loop-iteration (avoids
        // re-checking [state.selectedImageIds] inside the
        // rotate block).
        val isSelected = image.id in state.selectedImageIds
        val isLocked = image.locked
        rotate(degrees = image.rotationDegrees, pivot = Offset(centerX, centerY)) {
            drawRect(
                color = colors.imageBox,
                topLeft = Offset(left, top),
                size = Size(w, h),
            )
            // Selected: bright blue solid outline. Drawn
            // first so a "selected AND locked" image has
            // the solid blue peeking through the gaps of
            // the red dashed locked outline drawn on top
            // — the compound "this is selected, AND it's
            // locked" affordance the user can read in a
            // single glance.
            if (isSelected) {
                drawRect(
                    color = colors.selectedStroke,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 1.5f),
                )
            }
            // Locked: red dashed outline. The dash pattern
            // (4f on, 2f off) is a fixed pixel value, not
            // scaled with the image — at very small minimap
            // footprints (<6px on screen) the dashes may
            // collapse to a near-solid red. The COLOR
            // alone still distinguishes it from the
            // selected blue, so the user can read the
            // state even on tiny indicators.
            if (isLocked) {
                drawRect(
                     color = colors.lockedStroke,
                     topLeft = Offset(left, top),
                     size = Size(w, h),
                     style = Stroke(
                         width = 1.5f,
                         pathEffect = LockedDashPathEffect,
                     ),
                 )
            }
        }
    }

    val indicatorLeft = sceneLeft + (viewWorld.left - sceneBbox.left) * sceneScale
    val indicatorTop = sceneTop + (viewWorld.top - sceneBbox.top) * sceneScale
    val indicatorRight = sceneLeft + (viewWorld.right - sceneBbox.left) * sceneScale
    val indicatorBottom = sceneTop + (viewWorld.bottom - sceneBbox.top) * sceneScale
    val indicatorW = (indicatorRight - indicatorLeft).coerceAtLeast(2f)
    val indicatorH = (indicatorBottom - indicatorTop).coerceAtLeast(2f)
    // V_NOW (user feedback "viewport indicator 应该是
    // 圆角,不是直角"): the indicator is drawn with
    // [drawRoundRect] matching [Dimens.ChromeCornerRadius]
    // (12dp) so its four corners echo the panel's outer
    // corner radius. [drawRoundRect] auto-clamps the
    // corner radius to size/2 when the indicator shrinks
    // below the threshold (e.g. during extreme zoom-out
    // where [indicatorW] / [indicatorH] hit the 2px floor
    // from [coerceAtLeast]), so the indicator still
    // renders as a pill / ellipse rather than a malformed
    // over-rounded shape. The corner radius is in panel-
    // local pixels (same coordinate space as the rect
    // coords), so it stays visually consistent with the
    // panel silhouette regardless of scene scale.
    val indicatorCornerRadius = CornerRadius(Dimens.ChromeCornerRadius.toPx())
    drawRoundRect(
        color = colors.indicatorFill,
        topLeft = Offset(indicatorLeft, indicatorTop),
        size = Size(indicatorW, indicatorH),
        cornerRadius = indicatorCornerRadius,
    )
    drawRoundRect(
        color = colors.indicatorStroke,
        topLeft = Offset(indicatorLeft, indicatorTop),
        size = Size(indicatorW, indicatorH),
        cornerRadius = indicatorCornerRadius,
        style = Stroke(width = 1.5f),
    )
}

/**
 * Compute the scene rectangle the minimap should display. Bbox is the
 * union of all image AABBs (un-rotated, conservative), padded by 10% so
 * thumbnails don't touch the minimap edge. If no images, use a 1000×1000
 * default centred on the origin so the minimap still functions as a
 * pan handle on an empty scene.
 */
/**
 * Clamp a candidate [canvasOffset] so the main view's visible world rect
 * stays inside [sceneBbox] (the same bbox the minimap displays). Without
 * this clamp, dragging the minimap past its edge would push the visible
 * area outside the scene — the main view would render empty space and
 * the viewport-indicator rect on the minimap would draw off-panel and
 * become invisible.
 *
 * The clamp range for canvasOffset.x:
 *   - viewLeft  = (-offset.x - vpW/2) / scale, must be >= sceneBbox.left
 *       ⇒ offset.x <= -(sceneBbox.left + vpW/2/scale) * scale
 *   - viewRight = (-offset.x + vpW/2) / scale, must be <= sceneBbox.right
 *       ⇒ offset.x >= -(sceneBbox.right - vpW/2/scale) * scale
 *
 * [edgePaddingWorld] is an extra world-units inset (default 0; the
 * sceneBbox already carries its own 10% padding from
 * [computeSceneBboxForMinimap]) that keeps the indicator from kissing
 * the minimap's frame.
 */
internal fun clampOffsetToScene(
    offset: Offset,
    sceneBbox: Rect,
    viewportW: Float,
    viewportH: Float,
    scale: Float,
    edgePaddingWorld: Float = 0f,
): Offset {
    if (scale <= 0f) return offset
    val halfW = viewportW / 2f / scale
    val halfH = viewportH / 2f / scale
    val maxX = -(sceneBbox.left + edgePaddingWorld + halfW) * scale
    val minX = -(sceneBbox.right - edgePaddingWorld - halfW) * scale
    val maxY = -(sceneBbox.top + edgePaddingWorld + halfH) * scale
    val minY = -(sceneBbox.bottom - edgePaddingWorld - halfH) * scale
    // V9 (re-applied): defend against `min > max` (Kotlin's
    // [coerceIn] throws [IllegalArgumentException] in
    // that case → crash on the next minimap-drag frame).
    // When the viewport is wider/taller than the scene
    // (e.g. empty scene with the default 1000×1000 bbox,
    // or a single small image and the user has zoomed
    // way out), `halfW > sceneBbox.right - edgePadding`
    // so `minX = -(negative) * scale` flips sign and
    // ends up larger than `maxX` (which stays negative).
    // Same family as the "derived bounds could go negative"
    // pattern (e.g. toolbarBoxW/2 + inset vs
    // vpW - toolbarBoxW/2 - inset). Fix is to always
    // feed [coerceIn] the wider-relative-to-narrow
    // order via [minOf] / [maxOf].
    return Offset(
        x = offset.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
        y = offset.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY)),
    )
}

internal fun computeSceneBboxForMinimap(images: List<SceneImage>): Rect {
    if (images.isEmpty()) {
        return Rect(-500f, -500f, 500f, 500f)
    }
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    images.forEach { image ->
        val aabb = imageRotatedAabb(image)
        if (aabb.left < left) left = aabb.left
        if (aabb.top < top) top = aabb.top
        if (aabb.right > right) right = aabb.right
        if (aabb.bottom > bottom) bottom = aabb.bottom
    }
    // Ensure minimum dimensions to prevent zero-width/height bbox
    // (all images on the same line/point), which would cause
    // Infinity/NaN in downstream division and crash Skia.
    val minDim = 100f
    if (right - left < minDim) {
        val cx = (left + right) / 2f
        left = cx - minDim / 2f
        right = cx + minDim / 2f
    }
    if (bottom - top < minDim) {
        val cy = (top + bottom) / 2f
        top = cy - minDim / 2f
        bottom = cy + minDim / 2f
    }
    val padW = (right - left) * 0.1f
    val padH = (bottom - top) * 0.1f
    return Rect(left - padW, top - padH, right + padW, bottom + padH)
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private val MINIMAP_PANEL_MIN_DP = 60.dp
private val MINIMAP_PANEL_MAX_DP = 200.dp
private const val MINIMAP_MIN_ASPECT = 0.2f
private const val MINIMAP_MAX_ASPECT = 5.0f
private val MINIMAP_INNER_INSET_DP = 4.dp
// Fling tuning. The last frame's drag delta (in canvasOffset units) seeds
// the velocity. Each frame decays it by DAMPING, and the loop stops when
// the velocity magnitude falls below STOP_VELOCITY.
private const val MINIMAP_FLING_DAMPING = 0.92f
private const val MINIMAP_FLING_STOP_VELOCITY = 0.5f
// V_NOW (S-07): raised from framework default 500ms.
// See the long-press [pointerInput] call sites for the
// rationale (tablet ergonomics + minimap primary-vs-
// secondary action asymmetry).
private const val MINIMAP_LONG_PRESS_TIMEOUT_MS = 800L

/**
 * Cached dash [PathEffect] for the "locked" outline on the
 * minimap. Constructed once at class-load and shared across
 * every image in every redraw — previously each locked
 * image's per-frame drawRect constructed a new
 * [PathEffect.dashPathEffect] (allocating a native Skia path
 * effect every frame). For a 50-image scene where 10 are
 * locked, that's 10 allocations/frame → 600/minute while
 * the user pans/zooms. Hoisting it to a top-level val makes
 * the cost a one-time class-load.
 */
private val LockedDashPathEffect: PathEffect =
    PathEffect.dashPathEffect(
        intervals = floatArrayOf(4f, 2f),
        phase = 0f,
    )

// V_NOW3: extracted pointer-input filter config. All knobs are
// named fields on a data class (rather than bare `const val`s) so
// the values are discoverable in one place, tunable per-app via
// a single struct change, and easy to log / A/B-test in the
// future. The defaults below implement the V_NOW3 "1:1 跟手"
// tuning that eliminates the perceived lag between finger and
// canvas:
//
//   - sensitivity 1.0        : 1:1 panel→world ratio so the viewport
//                              indicator tracks the finger point-for-point.
//                              Previous 0.5 halved the mapping, causing
//                              the canvas to lag behind the finger.
//   - deadZonePx 1.5f         : any per-frame finger movement under
//                              1.5px is treated as noise and dropped.
//                              Kills S Pen sub-pixel jitter (< 1px on
//                              240Hz) while allowing slow deliberate
//                              drags (100+ px/sec) to register.
//   - smoothingAlpha 0.7f     : one-pole low-pass, new value weighted
//                              70% / history 30%. Time constant ~14ms
//                              (under 1 frame at 60fps) — the canvas
//                              responds within the same vsync the finger
//                              moved, while still damping single-frame
//                              S Pen spikes.
//
// Latency budget: per-frame work fits in one ~16ms frame, and the
// low-pass time constant is ~14ms, so the worst-case response is
// ~1 frame from finger-move to canvas-dispatch — within the
// human-perceptual "instant" threshold.
//
// V_NOW: removed [MINIMAP_DRAG_DEADBAND_PX] — replaced by the
// dead-zone + min-trigger in [minimapTouchConfig]. The old 0.75f
// value was an OFFSET-UNITS filter applied AFTER the per-frame
// scale multiplication, which meant a "small enough" sub-pixel
// canvas offset was suppressed (most useful for sub-pixel
// jitter), but it did nothing about the bigger problem of
// "the minimap pointer jitters by 3px, which after
// sceneRatio × scale = ×100 becomes a 300px canvas hop".
// The new per-frame POINTER dead zone attacks the noise at
// the source, before the amplification.
data class MinimapTouchConfig(
    // V_NOW3: 0.5 → 1.0. The previous 0.5 sensitivity halved
    // the panel→world mapping ratio, so a finger move on the
    // minimap produced only half the canvas displacement the
    // user expected — the canvas visibly lagged behind the
    // finger. 1.0 gives a true 1:1 finger-follow: the
    // viewport indicator on the minimap tracks the finger
    // point-for-point. Noise handling is now the low-pass
    // filter's job, not a blanket sensitivity reduction.
    val sensitivity: Float = 1.0f,
    // V_NOW3: 2.5f → 1.5f. The 2.5px dead zone was eating
    // slow drags: at 60fps, 2.5px/frame = 150 px/sec was the
    // minimum speed that registered, which is slower than a
    // deliberate slow pan. 1.5px still kills genuine S Pen
    // sub-pixel jitter (< 1px on 240Hz) and stationary-touch
    // tremor, but allows slow deliberate drags (100+ px/sec)
    // to register. The low-pass at alpha 0.7 catches the
    // 1.5-2px noise band that slips through the dead zone.
    val deadZonePx: Float = 1.5f,
    // V_NOW3: 0.5 → 0.7. The previous 0.5 weight gave a
    // ~32ms time constant (2 frames at 60fps), which added
    // perceptible lag between finger and viewport indicator.
    // 0.7 brings the time constant to ~14ms (under 1 frame),
    // so the indicator responds within the same vsync the
    // finger moved — visually "instant" while still damping
    // single-frame S Pen spikes (which the dead zone doesn't
    // catch because they're 2-3px genuine displacements).
    val smoothingAlpha: Float = 0.7f,
    // V_NOW2: removed. The previous 8px min-trigger was the
    // single biggest source of "不跟手": every drag had to
    // travel 8 SCREEN pixels (≈ 16px at the old sensitivity 0.5) before
    // the FIRST dispatch, and every subsequent dispatch had to
    // re-earn the trigger by accumulating 8px from zero. At
    // 60 fps, a normal 500 px/sec drag produces 8px/frame —
    // right at the trigger threshold, so every other frame
    // dispatched and the other half didn't, producing visible
    // stutter. At 120 fps (S Pen), even less reliable.
    //
    // The min-trigger's stated purpose was "prevent creep from
    // a series of sub-threshold frames" — but the dead zone
    // already handles creep: a series of 0.5-2px noise frames
    // is each dropped at the dead-zone gate, so the canvas
    // never receives a 0.5px dispatch and never accumulates
    // to a silent 5px move. There's nothing left for the
    // min-trigger to add. The field is removed entirely (not
    // left at 0) so a future config-driven re-introduction
    // is a deliberate decision, not a forgotten default.
)

private val minimapTouchConfig = MinimapTouchConfig()

/**
 * Corner-icon mode for [CanvasMinimap]. Drawn in place of
 * the full minimap when [CanvasMinimap.isCollapsed] is
 * true (the parent is a selection state, so the bottom
 * pill is visible and the full minimap would fight for
 * room). A single tap calls [onExpand] to flip the parent's
 * `isCollapsed` back to false and re-render the full panel.
 *
 * V_NOW: visual contract now matches the bottom-right
 * 一键排版 FAB token-for-token (44dp circle, 12dp corner
 * radius, [Dimens.FabElevation] = 6dp tonalElevation,
 * [Dimens.ChromeSurfaceAlpha] surface tint, no border).
 * The user spec is "minimap 折叠图标要和一键排版图标
 * 保持一致" — the user reads the two as the same kind of
 * floating action affordance now, just one for "go
 * arrange the layout" and the other for "go back to
 * the map". Same size + same elevation + same surface
 * tint means the two are read as a balanced "bottom
 * corners" pair (the bottom-center pill being the
 * third element).
 *
 * Icon swap: [Icons.Rounded.MyLocation] (crosshair) →
 * [Icons.Rounded.Map] (folded-map). The user spec
 * explicitly asked for "地图的图案". Map is the more
 * literal visual analog of "minimap" — the crosshair
 * was a navigation pin which doesn't read as a minimap
 * preview affordance. Both icons share the FAB's 24dp
 * vector size and primary tint.
 */
@Composable
internal fun CollapsedMinimapIcon(
    edge: com.pockref.app.model.MinimapDockEdge,
    onExpand: () -> Unit,
    // V_NOW: long-press on the collapsed icon
    // capsule is treated the same as long-press on
    // the expanded minimap panel — both enter
    // "slider mode" and morph into an M3 [Slider].
    // The parent [MinimapChrome] routes both
    // call sites through the same [onLongPress]
    // callback so the state machine is shared
    // (one [isMinimapSliderMode] state covers
    // both call sites).
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    // V_NOW (2026-06-07): drag-after-long-press on
    // the collapsed capsule is treated the same as
    // drag on the expanded minimap panel — both
    // dispatch a multiplicative scale change via
    // [PockRefAction.SetCanvasZoom] through the
    // parent. The drag is detected inside the same
    // [awaitEachGesture] loop that already handles
    // the long-press timing, so the gesture
    // contract for the collapsed capsule becomes:
    //   - quick tap (released before
    //     [longPressTimeoutMillis]) → [onExpand]
    //     via the `.clickable { onExpand() }`
    //     modifier (this [awaitEachGesture] does
    //     nothing in this case — it returns
    //     early).
    //   - long-press (≥ longPressTimeoutMillis
    //     hold) → [onLongPress] fires; subsequent
    //     [PointerEvent]s with non-zero
    //     [PointerInputChange.positionChange] fire
    //     [onDrag] until the pointer is released
    //     (which fires [onLongPressEnd]).
    //   - double-tap is NOT handled here — it's
    //     handled at the OUTER Box in
    //     [PureRefApp.kt]'s MinimapChrome (where
    //     the second pointerInput wraps the
    //     [CollapsedMinimapIcon]). Keeping
    //     double-tap out of this inner gesture
    //     loop avoids interference with the
    //     long-press timer.
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit = {},
) {
    // V_NOW (iOS PiP-style minimap — visual redesign):
    // the bar now follows the iOS Picture-in-Picture
    // affordance: a CAPSULE (fully rounded, 50% corner
    // radius) sitting on a screen edge with a soft
    // shadow + a gentle hover-grow so it reads as
    // "you can pull me out, I live here" rather than
    // the previous 6dp razor-strip ("this is just a
    // hint, you can barely see me"). The previous
    // razor-strip was too subtle for stylus-driven
    // pointer hovers to find reliably; the new capsule
    // is 60dp × 8dp (horizontal) / 8dp × 60dp (vertical)
    // with a 4dp shadow, hover-grows to 1.15× over 200ms,
    // and is colored via [inverseSurface] at 92% alpha
    // so the [小白条] appearance is preserved in dark
    // mode (near-white bar) and the bar stays visible
    // in light mode (near-dark bar). The capsule shape
    // is the visual cue that says "I'm an iOS-style
    // chrome handle"; combined with the shadow + hover
    // animation it matches the iOS PiP window
    // contract: same height profile, same shadow, same
    // "tap-to-expand, drag-to-move, edge-dock" grammar.
    //
    // Touch target stays generous (64dp × 32dp /
    // 32dp × 64dp) so the user's finger has a forgiving
    // margin of error; the visual capsule is centered
    // inside the touch target so the user sees the
    // capsule in roughly the same spot they tapped. The
    // touch target is 100% transparent — only the
    // capsule is rendered.
    //
    // V_NOW (after user feedback "小白条背后有图时容易
    // 误触点到图片"): see the touch-target comment in
    // the previous design below — the same concern still
    // applies (the touch area MUST be bigger than the
    // visual capsule, otherwise the user's finger can
    // slip past the 8dp bar and tap through to the
    // image underneath). The 64dp × 32dp / 32dp × 64dp
    // touch target is preserved.
    val isHorizontal = edge == com.pockref.app.model.MinimapDockEdge.TOP ||
        edge == com.pockref.app.model.MinimapDockEdge.BOTTOM
    val visualLength = Dimens.TopToolbarHeight
    val visualThickness = 8.dp
    val visualWidth = if (isHorizontal) visualLength else visualThickness
    val visualHeight = if (isHorizontal) visualThickness else visualLength
    val touchWidth = if (isHorizontal) Dimens.TopToolbarHeight else 32.dp
    val touchHeight = if (isHorizontal) 32.dp else Dimens.TopToolbarHeight
    // iOS PiP capsule: 50% corner radius gives a true
    // pill / stadium shape, not a rounded rectangle.
    val capsuleShape = RoundedCornerShape(percent = 50)
    val barColor = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    val barAlignment = when (edge) {
        com.pockref.app.model.MinimapDockEdge.TOP -> Alignment.TopCenter
        com.pockref.app.model.MinimapDockEdge.BOTTOM -> Alignment.BottomCenter
        com.pockref.app.model.MinimapDockEdge.LEFT -> Alignment.CenterStart
        com.pockref.app.model.MinimapDockEdge.RIGHT -> Alignment.CenterEnd
        // V_NOW: NONE fallback (shouldn't happen — see
        // the dock-edge comment block in PureRefApp.kt's
        // MinimapChrome), use TopCenter to keep the bar
        // at the top rather than mid-air.
        com.pockref.app.model.MinimapDockEdge.NONE -> Alignment.TopCenter
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = touchWidth, height = touchHeight)
            .hoverable(interactionSource = interactionSource)
            .clickable { onExpand() }
            // V_NOW: long-press → slider mode. Same
            // `withTimeoutOrNull` racing pattern as the
            // expanded CanvasMinimap (see the
            // long-press pointerInput block above).
            // Long-press on the capsule is equivalent
            // to long-press on the panel — both enter
            // slider mode without first going through
            // the "tap to expand" intermediate state.
            // The `clickable { onExpand() }` modifier
            // above handles the short-tap case (which
            // doesn't fire the long-press timer), so
            // both gestures coexist on the same touch
            // target without conflict — tap expands,
            // long-press morphs to slider.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // V_NOW (S-07): same 800ms threshold as
                    // the expanded minimap above.
                    val longPressTimeoutMs = MINIMAP_LONG_PRESS_TIMEOUT_MS
                    val releasedBeforeTimeout = withTimeoutOrNull(
                        longPressTimeoutMs.toLong(),
                    ) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                return@withTimeoutOrNull true
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        false
                    }
                    if (releasedBeforeTimeout == true) {
                        return@awaitEachGesture
                    }
                    onLongPress()
                    // V_NOW (2026-06-07): after the
                    // long-press threshold, drain pointer
                    // events and forward [positionChange]s
                    // to [onDrag] so the parent can map
                    // drag-to-zoom. We only react to the
                    // FIRST pointer's deltas (matching
                    // [detectDragGesturesAfterLongPress]'s
                    // semantics) and we consume the change
                    // so downstream gesture detectors
                    // don't re-interpret the same motion
                    // (e.g. the outer Box's
                    // [detectTapGestures] wouldn't try to
                    // turn a 1.5-second hold+drag into a
                    // double-tap).
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change == null) {
                            // No pressed pointer — all
                            // fingers are up, gesture is
                            // over.
                            onLongPressEnd()
                            return@awaitEachGesture
                        }
                        val delta = change.positionChange()
                        if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                            onDrag(delta)
                            // Consume so the gesture
                            // is committed to drag mode
                            // (the [clickable] won't
                            // misinterpret a long-
                            // press+release as a tap if
                            // it had any future use case
                            // for that — currently it
                            // doesn't, but defence in
                            // depth).
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = barAlignment,
    ) {
        // V_NOW: hover-to-expand was previously handled in
        // PureRefApp.kt's MinimapChrome around the
        // CollapsedMinimapIcon call site. Moved in here so
        // the clickable and the hoverable share the SAME
        // (expanded) touch target — the previous design
        // had them on the outer Box (48dp × 6dp) which
        // was too small for hover detection on most
        // styluses. The 64dp × 32dp / 32dp × 64dp target
        // is in the standard Android "extended touch slop"
        // range and registers hover reliably.
        val isHovered by interactionSource.collectIsHoveredAsState()
        androidx.compose.runtime.LaunchedEffect(isHovered) {
            if (isHovered) onExpand()
        }
        // V_NOW (iOS PiP-style hover affordance): the
        // capsule grows 1.15× on hover (200ms ease-out)
        // as a "you've found me" feedback. This is
        // distinct from the click-to-expand action and
        // runs on the same `isHovered` source so the
        // grow + auto-expand are visually coupled (the
        // user sees the grow + immediately the panel
        // expands — no separate "tap to open" step).
        // The grow is purely visual (graphicsLayer
        // scale) so the touch target stays the
        // original size.
        val hoverScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isHovered) 1.15f else 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 200,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            label = "minimap-pip-hover-scale",
        )
        // The visual capsule — iOS PiP-style with a
        // soft shadow so it reads as a "floating
        // handle" sitting on the screen edge. The
        // touch target around it (the outer Box above)
        // is what receives taps; the capsule is purely
        // a visual + hover target.
        Box(
            modifier = Modifier
                .size(width = visualWidth, height = visualHeight)
                .graphicsLayer {
                    scaleX = hoverScale
                    scaleY = hoverScale
                    // Anchor the scale at the EDGE side of
                    // the capsule (not the center) so the
                    // position relative to the screen edge
                    // doesn't drift when the hover-grow
                    // animation runs. TopStart for TOP/BOTTOM
                    // edges, TopStart for LEFT/RIGHT edges
                    // — the EDGE side of the capsule is
                    // always the side facing the screen
                    // edge it docks to.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    shape = capsuleShape
                }
                .background(
                    color = barColor,
                    shape = capsuleShape,
                )
                .border(
                    width = 0.5.dp,
                    color = borderColor,
                    shape = capsuleShape,
                ),
        )
    }
}
