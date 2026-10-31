package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.AngleSnapGuide
import com.pockref.app.model.SceneImage
import kotlin.math.IEEErem
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.hypot

/**
 * Rotation-snap threshold (degrees). When a continuous rotation gesture
 * brings the current angle within this many degrees of any 8-angle
 * (0°/45°/90°/135°/180°/225°/270°/315°), the rotation snaps to that
 * multiple — provided the 200ms pause + 10° continue rules (see
 * [PockRefViewModelTransform.ROTATION_SNAP_DEBOUNCE_MS] and
 * [ROTATION_SNAP_CONTINUE_THRESHOLD_DEG]) are also satisfied.
 *
 * Smaller than the edge-snap trigger (15px) because rotation is a finer
 * control and the user can FEEL the snap firing on the angle — no
 * proximity pre-display needed.
 */
private const val ROTATION_SNAP_THRESHOLD_DEG = 5f

/**
 * Number of degrees between adjacent snap points. 360° / 8 = 45°. This is
 * the divisor used by [detectAngleSnap] to compute the nearest 8-angle
 * via `round(normalized / 45f) * 45f`. Change this in one place if the
 * snap-point count ever needs to change.
 */
private const val ROTATION_SNAP_POINT_STRIDE_DEG = 45f

/**
 * Continue-threshold (degrees) for the post-snap release check. Once the
 * image is actively snapped to a target angle, the user must rotate
 * past the snap target by more than this many degrees (shortest-arc) to
 * cancel the snap and resume free rotation. 10° is the spec value —
 * large enough to be a deliberate move (small finger jitter doesn't
 * break the lock), small enough to feel responsive when the user
 * genuinely wants to leave the snap.
 */
internal const val ROTATION_SNAP_CONTINUE_THRESHOLD_DEG = 10f

/**
 * Activation-threshold (degrees) for the snap-zone entry check. When
 * the candidate rotation is within this many degrees (shortest-arc) of
 * any 8-angle, the engine considers the gesture "in the zone" and
 * starts the 200ms pause timer. Picked at 5° (slightly wider than the
 * old 4°/4-angle threshold) so 45°-multiples are reachable without
 * the user fighting the snap on every step.
 */
internal const val ROTATION_SNAP_ACTIVATION_THRESHOLD_DEG = 5f

/**
 * Debounce / pause window (ms) the gesture must dwell within the
 * snap zone before the rotation actually locks. 200ms is the spec
 * value and matches the [SNAP_ENGAGE_DELAY_MS] used by the edge-snap
 * engine, giving the rotation path a consistent "settle to engage"
 * feel with the rest of the gesture system.
 */
internal const val ROTATION_SNAP_DEBOUNCE_MS = 200L

/**
 * Pure-Kotlin helpers for the per-image transform math used by
 * [PockRefViewModelTransform]. These are `internal` (not private) so unit
 * tests in the `test` source set can verify the math without going through
 * the [com.pockref.app.model.PockRefUiState] state machine (which would
 * require AndroidViewModel + a coroutine scope).
 *
 * The transform functions all share the same "pivot" model:
 * - For a single-image selection, the pivot is the image's own center.
 * - For a multi-image selection, the pivot is the center of the
 *   axis-aligned bounding box that encloses the selected images.
 *
 * The math itself is a 2D rotation + uniform scale around that pivot, with
 * an extra pan in world coordinates (divided by canvas scale to convert
 * to scene coordinates).
 */

/** Compute the center of the AABB enclosing [selected], or a single image's
 *  center when the list has one element. Caller is responsible for picking
 *  the right pivot context; this is a pure geometry helper. */
internal fun calculateTransformPivot(selected: List<SceneImage>): Offset {
    require(selected.isNotEmpty()) { "selected must not be empty" }
    if (selected.size == 1) return selected.first().center
    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (img in selected) {
        val halfW = img.displaySize.width * img.scale / 2f
        val halfH = img.displaySize.height * img.scale / 2f
        val l = img.center.x - halfW
        val r = img.center.x + halfW
        val t = img.center.y - halfH
        val b = img.center.y + halfH
        if (l < left) left = l
        if (r > right) right = r
        if (t < top) top = t
        if (b > bottom) bottom = b
    }
    return Offset((left + right) / 2f, (top + bottom) / 2f)
}

/**
 * Return the signed shortest-arc delta (radians) that takes [from] to
 * [to]. The result is in the closed range `[-PI, PI]`:
 * - `shortestAngleDelta(0, π/2) = +π/2`  (small positive delta, no wrap)
 * - `shortestAngleDelta(0, -π/2) = -π/2` (small negative delta, no wrap)
 * - `shortestAngleDelta(π/2, 3π/2) = ±π`  (half-turn, sign is convention)
 * - `shortestAngleDelta(0, 2π) = 0`       (full revolution = identity)
 * - `shortestAngleDelta(170°, -170°) = +20°` (across the upper wrap: take
 *   the 20° CCW path, not the 340° CW path)
 *
 * Used by the rotation-snap path to keep cumulative `image.center`
 * rotation stable when the user crosses the ±π wrap (without this,
 * `atan2` would jump by 2π and the image would appear to teleport).
 *
 * Implementation note: the modulo convention picks the sign of the
 * boundary cases (±π) — they are equivalent on a circle. Code that
 * cares about the sign should test with values strictly inside
 * (-π, π).
 */
internal fun shortestAngleDelta(from: Float, to: Float): Float {
    val twoPi = (2.0 * PI).toFloat()
    val raw = to - from
    return ((raw + PI.toFloat()) % twoPi + twoPi) % twoPi - PI.toFloat()
}

/**
 * Apply a uniform [scaleChange] and a [rotationChange] (in degrees) to
 * [image] around [pivot], with an extra [pan] applied in scene coordinates
 * (the caller passes `pan / canvasScale` if it wants screen-space pan).
 *
 * The output preserves all other fields of [image] (color, flips, etc.);
 * only `center`, `scale`, and `rotationDegrees` may change.
 */
internal fun transformImageAroundPivot(
    image: SceneImage,
    pivot: Offset,
    scaleChange: Float,
    rotationChange: Float,
    pan: Offset,
): SceneImage {
    val hasScale = scaleChange != 1f
    val hasRotation = rotationChange != 0f

    if (!hasScale && !hasRotation) {
        return image.copy(center = image.center + pan)
    }
    // V_NOW (user spec "双指缩放选中图片"): per-image scale
    // clamped to [0.1, 8.0] = 10% .. 800% of the image's
    // intrinsic size. This matches the spec's bound (the
    // previous 0.05..10 was a wider safety range that let
    // images become tiny / huge and destabilised the
    // floating layer's aspect math).
    val newScale = (image.scale * scaleChange).coerceIn(0.1f, 8.0f)
    val effectiveScale = if (hasScale) newScale / image.scale else 1f

    val rad = if (hasRotation) Math.toRadians(rotationChange.toDouble()) else 0.0
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    val dx = image.center.x - pivot.x
    val dy = image.center.y - pivot.y
    val scaledDx = dx * effectiveScale
    val scaledDy = dy * effectiveScale
    val rotatedDx = scaledDx * cosR - scaledDy * sinR
    val rotatedDy = scaledDx * sinR + scaledDy * cosR

    return image.copy(
        center = Offset(pivot.x + rotatedDx, pivot.y + rotatedDy) + pan,
        scale = newScale,
        rotationDegrees = image.rotationDegrees + rotationChange,
    )
}

/**
 * Detect a 45°-multiple rotation snap. Returns the snapped angle
 * (degrees) if the candidate is within [thresholdDeg] of any of the
 * 8 cardinal/inter-cardinal angles
 * (0°/45°/90°/135°/180°/225°/270°/315°), or `null` otherwise.
 *
 * Snaps are computed in the [0, 360) range. The returned value is the
 * canonical snap angle, regardless of the sign of the candidate — so
 * `-89°` and `+89°` both snap to `90°` when within the threshold, and
 * `355°` snaps to `0°` (the wrap-around path, not `360°`).
 *
 * Distance to the candidate is measured as the shortest arc using
 * [shortestAngleDelta] (which handles negative angles and the
 * 360°/0° boundary correctly). The candidate is first normalized to
 * `[0, 360)` with [IEEErem], then the nearest 45°-multiple is picked
 * via `round(normalized / 45f) * 45f`, and the result is re-normalized
 * to `[0, 360)` (so `round(360/45)*45 = 360` becomes `0`).
 *
 * Pure function — no state, no allocation. Used by the transform
 * layer's rotation path to decide whether to lock the rotation to the
 * snap value and publish the angle-snap guide.
 */
internal fun detectAngleSnap(
    candidateDegrees: Float,
    thresholdDeg: Float = ROTATION_SNAP_THRESHOLD_DEG,
): Float? {
    // Normalize to [0, 360). IEEErem returns values in (-360, 360); the
    // sign correction brings negatives into [0, 360). Handles 360°/0°
    // wrap-around cleanly (e.g. -1° → 359° → 1° from 0°).
    val normalized = (candidateDegrees.IEEErem(360f)).let { if (it < 0f) it + 360f else it }
    // Find the nearest 8-angle (0/45/90/.../315) via stride-45 rounding.
    val nearestMultiple = kotlin.math.round(normalized / ROTATION_SNAP_POINT_STRIDE_DEG) * ROTATION_SNAP_POINT_STRIDE_DEG
    // Re-normalize to [0, 360): round(360/45)*45 = 360 wraps to 0, and
    // round(405/45)*45 = 405 wraps to 45, etc.
    val snapped = (nearestMultiple.IEEErem(360f)).let { if (it < 0f) it + 360f else it }
    // Shortest-arc distance in DEGREES. We can't reuse
    // [shortestAngleDelta] here because that function operates on
    // radians (the PI constant is the radian value, so feeding
    // degree-space values produces garbage like 1.28° instead of 5°).
    // The wrap-around-correct distance in [0, 180] is just
    // `min(|a - b|, 360 - |a - b|)`, which handles 355° → 0° (= 5°
    // via the wrap path) identically to the radians formula.
    val rawDelta = kotlin.math.abs(normalized - snapped)
    val distance = kotlin.math.min(rawDelta, 360f - rawDelta)
    return if (distance <= thresholdDeg) snapped else null
}

/**
 * One row in the rotation-snap state machine, computed by
 * [computeRotationSnapDecision] from a single
 * [PockRefViewModelTransform.transformSelectedImage] call.
 *
 * Fields:
 * - [newGuide] — the new value for [PockRefUiState.angleSnapGuide].
 *   Non-null when the gesture is in the snap zone (with the
 *   state-machine fields populated); null when the gesture is fully
 *   out of the zone or the snap was just cancelled.
 * - [effectiveDelta] — what to apply to `image.rotationDegrees` for
 *   this frame. Equals the user's [rotationChange] when not snapped;
 *   equals `snapTarget - firstRotation` (a "lock to target" delta) on
 *   the frame the snap engages; equals 0 while the snap is actively
 *   held (the image is locked, the user's continued rotation is
 *   buffered for the continue-threshold check).
 * - [shouldEngageNow] — true when the 200ms debounce has elapsed
 *   inside the zone and the snap should fire IMMEDIATELY on this
 *   frame (no coroutine needed). The caller still sets the guide
 *   and applies the locked rotation; the only thing this flag controls
 *   is whether a coroutine needs to be scheduled for a future fire.
 * - [engageTargetAngle] — the canonical 8-angle target when
 *   [shouldEngageNow] is true. Used by the coroutine variant of the
 *   state machine to know which angle to lock to on a delayed fire.
 * - [isInZone] — true when the current candidate rotation is within
 *   [ROTATION_SNAP_ACTIVATION_THRESHOLD_DEG] of an 8-angle. Used by
 *   the ViewModel layer to decide whether to (re)arm a coroutine for
 *   the 200ms debounce.
 * - [isActivelySnapped] — true when the existing [previousGuide] has
 *   an active snap and the candidate is within the continue window.
 *   Marker for tests / observability; the ViewModel layer mostly
 *   already knows this from [previousGuide].
 * - [isCancellingSnap] — true when the existing snap is being
 *   released (user rotated past [ROTATION_SNAP_CONTINUE_THRESHOLD_DEG]).
 *   Used by tests; the ViewModel clears the active snap and
 *   re-evaluates the new candidate as a fresh entry.
 */
internal data class RotationSnapDecision(
    val newGuide: AngleSnapGuide?,
    val effectiveDelta: Float,
    val shouldEngageNow: Boolean = false,
    val engageTargetAngle: Float? = null,
    val isInZone: Boolean = false,
    val isActivelySnapped: Boolean = false,
    val isCancellingSnap: Boolean = false,
)

/**
 * Pure rotation-snap state-machine transition. Decides, for one
 * [PockRefViewModelTransform.transformSelectedImage] call, what the
 * next [AngleSnapGuide] should be and what `effectiveDelta` to apply
 * to the image's `rotationDegrees`.
 *
 * The machine has three macro states:
 *
 * 1. **IDLE** (no previous guide, not in zone): pass the user's
 *    [rotationChange] through unchanged and emit a null guide.
 *
 * 2. **IN_ZONE** (in zone, no active snap yet): track the entry
 *    timestamp in [AngleSnapGuide.angleEnteredSnapZoneMs]. As long as
 *    `now - enteredMs < debounceMs`, stay in this state and pass the
 *    user's rotation through. When the debounce elapses, transition
 *    to ACTIVE by setting [AngleSnapGuide.activeSnapAngle] and
 *    [AngleSnapGuide.isEngaged], and emit a locked [effectiveDelta]
 *    that brings the image to the snap target.
 *
 * 3. **ACTIVE** (actively snapped): hold the image at the snap
 *    target ([effectiveDelta] = 0, the user's continued rotation is
 *    consumed only for the continue check). If the user's
 *    [rotationChange] pushes the candidate past
 *    [ROTATION_SNAP_CONTINUE_THRESHOLD_DEG] of the active snap, the
 *    snap is released: [activeSnapAngle] becomes null, the guide is
 *    cleared (or re-armed for the new zone, if any), and the user's
 *    rotation passes through.
 *
 * Re-entering the zone after a release starts the 200ms timer fresh
 * (we don't carry the old `angleEnteredSnapZoneMs` across a cancel).
 *
 * `rotationSnapActive = false` (user opt-out) collapses the machine
 * to IDLE regardless of the candidate — no guide, no lock, full
 * gesture passthrough.
 *
 * This function is pure: it allocates one [AngleSnapGuide] at most,
 * no I/O, no time source. The caller supplies [now] and the current
 * `previousGuide` from the state. The thresholds are passed in as
 * parameters (defaulting to the spec values) so unit tests can
 * override them without monkey-patching constants.
 */
internal fun computeRotationSnapDecision(
    firstRotation: Float,
    rotationChange: Float,
    rotationSnapActive: Boolean,
    previousGuide: AngleSnapGuide?,
    now: Long,
    activationThresholdDeg: Float = ROTATION_SNAP_ACTIVATION_THRESHOLD_DEG,
    continueThresholdDeg: Float = ROTATION_SNAP_CONTINUE_THRESHOLD_DEG,
    debounceMs: Long = ROTATION_SNAP_DEBOUNCE_MS,
): RotationSnapDecision {
    val candidate = firstRotation + rotationChange
    val target = if (rotationSnapActive) {
        detectAngleSnap(candidate, thresholdDeg = activationThresholdDeg)
    } else null
    val isInZone = target != null
    val previousActive = previousGuide?.activeSnapAngle

    // ----------------------------------------------------------------
    // State 3 (ACTIVE): a previous snap is currently held. Check
    // the continue / release rule first, before any other branch.
    // ----------------------------------------------------------------
    if (previousActive != null) {
        // Shortest-arc distance in DEGREES (see the note in
        // [detectAngleSnap] — [shortestAngleDelta] is radian-based).
        val rawDelta = kotlin.math.abs(previousActive - candidate)
        val distance = kotlin.math.min(rawDelta, 360f - rawDelta)
        if (distance > continueThresholdDeg) {
            // Release: clear the active snap. If the new candidate
            // happens to be in a snap zone, restart the zone-entry
            // timer (the user might re-engage a new snap after the
            // 200ms pause).
            return if (isInZone) {
                val newGuide = AngleSnapGuide(
                    snappedDegrees = target!!,
                    isEngaged = false,
                    startTimeMs = previousGuide.startTimeMs,
                    lastAngleChangeMs = now,
                    activeSnapAngle = null,
                    angleEnteredSnapZoneMs = now,
                )
                RotationSnapDecision(
                    newGuide = newGuide,
                    effectiveDelta = rotationChange,
                    isInZone = true,
                    isCancellingSnap = true,
                )
            } else {
                RotationSnapDecision(
                    newGuide = null,
                    effectiveDelta = rotationChange,
                    isCancellingSnap = true,
                )
            }
        }
        // Stay snapped: image is locked to previousActive. The user's
        // continued rotation is consumed (delta = 0); the continue
        // check on the next call decides whether to release.
        return RotationSnapDecision(
            newGuide = previousGuide,
            effectiveDelta = 0f,
            isActivelySnapped = true,
            isInZone = isInZone, // informational; the image stays locked regardless
        )
    }

    // ----------------------------------------------------------------
    // State 2 (IN_ZONE): in the snap zone but no active snap yet.
    // Track the entry time; fire the snap when the debounce elapses.
    // ----------------------------------------------------------------
    if (isInZone) {
        val enteredMs = previousGuide?.angleEnteredSnapZoneMs ?: now
        val elapsed = now - enteredMs
        val shouldFire = elapsed >= debounceMs

        val newGuide = AngleSnapGuide(
            snappedDegrees = target!!,
            isEngaged = false,
            startTimeMs = previousGuide?.startTimeMs ?: now,
            lastAngleChangeMs = now,
            activeSnapAngle = null,
            angleEnteredSnapZoneMs = enteredMs,
        )

        return if (shouldFire) {
            // Fire the snap on this frame: lock the image to the
            // target, set isEngaged / activeSnapAngle, clear the
            // pending-fire state. The caller applies effectiveDelta
            // and writes newGuide into the state.
            val firedGuide = newGuide.copy(
                isEngaged = true,
                activeSnapAngle = target,
                startTimeMs = now,
            )
            RotationSnapDecision(
                newGuide = firedGuide,
                effectiveDelta = target - firstRotation,
                shouldEngageNow = true,
                engageTargetAngle = target,
                isInZone = true,
            )
        } else {
            // Still waiting for the 200ms debounce. Image follows the
            // gesture (effectiveDelta = rotationChange), the entry
            // time is preserved so a subsequent call with the same
            // zone can keep counting.
            RotationSnapDecision(
                newGuide = newGuide,
                effectiveDelta = rotationChange,
                isInZone = true,
            )
        }
    }

    // ----------------------------------------------------------------
    // State 1 (IDLE): out of zone, not snapped. Pass through.
    // ----------------------------------------------------------------
    return RotationSnapDecision(
        newGuide = null,
        effectiveDelta = rotationChange,
    )
}

/**
 * Apply a uniform [factor] scale to [image] around [pivot], with no
 * rotation. Used by [PockRefViewModelTransform.scaleSelected] in
 * "scale-only" mode.
 */
internal fun scaleImageAroundPivot(
    image: SceneImage,
    pivot: Offset,
    factor: Float,
): SceneImage {
    // V_NOW: clamp range updated to [0.1, 8.0] = 10%..800% of
    // the image's intrinsic size (was [0.05, 10]). Keeps the
    // scale-only helper consistent with
    // [transformImageAroundPivot].
    val newScale = (image.scale * factor).coerceIn(0.1f, 8.0f)
    val effectiveFactor = newScale / image.scale
    val dx = image.center.x - pivot.x
    val dy = image.center.y - pivot.y
    return image.copy(
        center = Offset(pivot.x + dx * effectiveFactor, pivot.y + dy * effectiveFactor),
        scale = newScale,
    )
}

/**
 * Rotate [image] around [pivot] by [delta] degrees. Used by
 * [PockRefViewModelTransform.rotateSelected].
 */
internal fun rotateImageAroundPivot(
    image: SceneImage,
    pivot: Offset,
    delta: Float,
): SceneImage {
    val rad = Math.toRadians(delta.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val dx = image.center.x - pivot.x
    val dy = image.center.y - pivot.y
    return image.copy(
        center = Offset(
            pivot.x + (dx * cosR - dy * sinR),
            pivot.y + (dx * sinR + dy * cosR),
        ),
        rotationDegrees = image.rotationDegrees + delta,
    )
}

/**
 * Re-order the zIndex of the selected images so they sit on top of (or
 * below) the unselected images. This is the pure-logic core of
 * [PockRefViewModelTransform.bringToFront] and
 * [PockRefViewModelTransform.sendToBack].
 *
 * Behavior:
 * - bringToFront = true: each selected image gets a zIndex strictly
 *   greater than the previous max of all images, in the order they
 *   appear in [images].
 * - bringToFront = false: each selected image gets a zIndex strictly
 *   smaller than the previous min of all images, in the order they
 *   appear in [images].
 *
 * Unselected images keep their zIndex (and their position in the
 * returned list).
 */
internal fun reorderZIndex(
    images: List<SceneImage>,
    selectedIds: Set<String>,
    bringToFront: Boolean,
): List<SceneImage> {
    if (selectedIds.isEmpty()) return images
    if (bringToFront) {
        var nextZ = images.maxOfOrNull { it.zIndex } ?: 0
        return images.map { image ->
            if (image.id in selectedIds) image.copy(zIndex = ++nextZ) else image
        }
    } else {
        var nextZ = images.minOfOrNull { it.zIndex } ?: 0
        return images.map { image ->
            if (image.id in selectedIds) image.copy(zIndex = --nextZ) else image
        }
    }
}

/**
 * Compute the "effective" scale factor when scaling multiple images at
 * once. If any image would clamp against the [0.1, 8.0] range, the
 * effective factor for the group is reduced so the most-clamped image
 * lands exactly at the boundary. Mirrors the production behavior in
 * [PockRefViewModelTransform.scaleSelected] / `transformSelectedImage`.
 */
internal fun computeGroupScaleFactor(
    selected: List<SceneImage>,
    requested: Float,
): Float {
    if (selected.isEmpty()) return 1f
    val newScaleByImage = selected.associate { it.id to (it.scale * requested).coerceIn(0.1f, 8.0f) }
    val anyClamped = selected.any { img ->
        val raw = img.scale * requested
        val clamped = newScaleByImage[img.id]!!
        raw != clamped
    }
    if (!anyClamped) return requested
    val img = selected.first { newScaleByImage[it.id] != it.scale * requested }
    return newScaleByImage[img.id]!! / img.scale
}

/**
 * V_NOW (corner-handle scale gesture): the four corners of
 * a scene image, enumerated in the same order the renderer
 * draws them in [drawSceneImage]'s 4-corner handle block.
 *
 * - 0 = top-left     (anchor = top-left,    move-handle = bottom-right)
 * - 1 = top-right    (anchor = top-right,   move-handle = bottom-left)
 * - 2 = bottom-left  (anchor = bottom-left, move-handle = top-right)
 * - 3 = bottom-right (anchor = bottom-right,move-handle = top-left)
 *
 * Each entry stores the offset (in image-local space, i.e.
 * the SAME [0, width] x [0, height] box the renderer uses
 * inside [drawSceneImage]'s [translate] + [rotate] block)
 * of the move-handle corner and its opposite (anchored)
 * corner. Image-local (0, 0) is the top-left of the
 * un-rotated bitmap; the OUTER rotate+scale matrix
 * transforms it onto the rendered position so the
 * "hit-test in screen coords → image-local coords" math
 * is handled by [projectImageCornersToScreen] /
 * [unprojectScreenToImageLocal] below.
 *
 * The [width] / [height] fields are the image's CURRENT
 * scaled size in world units (displaySize * scale), so
 * the corners stay in the right place as the user scales
 * the image mid-gesture (the helper captures them at the
 * down event, when scale is still the pre-drag value).
 */
internal data class CornerHandles(
    val imageId: String,
    val width: Float,
    val height: Float,
    val corners: List<CornerSpec>,
) {
    companion object {
        /**
         * Enumerate the 4 corners for an image. [width] / [height]
         * are the image's world-space (displaySize * scale) extents
         * at the time of the corner hit-test.
         */
        fun forImage(imageId: String, width: Float, height: Float): CornerHandles {
            val w = width
            val h = height
            return CornerHandles(
                imageId = imageId,
                width = w,
                height = h,
                corners = listOf(
                    // 0: top-left move-handle — opposite (anchor) is bottom-right
                    CornerSpec(index = 0, moveLocal = Offset(0f, 0f), anchorLocal = Offset(w, h)),
                    // 1: top-right move-handle — anchor is bottom-left
                    CornerSpec(index = 1, moveLocal = Offset(w, 0f), anchorLocal = Offset(0f, h)),
                    // 2: bottom-left move-handle — anchor is top-right
                    CornerSpec(index = 2, moveLocal = Offset(0f, h), anchorLocal = Offset(w, 0f)),
                    // 3: bottom-right move-handle — anchor is top-left
                    CornerSpec(index = 3, moveLocal = Offset(w, h), anchorLocal = Offset(0f, 0f)),
                ),
            )
        }
    }
}

/**
 * One row in [CornerHandles]. [moveLocal] is the corner the
 * user is dragging (its position becomes the move-handle);
 * [anchorLocal] is the OPPOSITE corner, which stays fixed
 * during the drag (the image "grows / shrinks" with the
 * anchor pinned). Both are in the image's local (post-scale,
 * pre-rotation) coordinate frame.
 */
internal data class CornerSpec(
    val index: Int,
    val moveLocal: Offset,
    val anchorLocal: Offset,
)

/**
 * Project an image's 4 corners from image-local space to
 * screen pixels, applying the same transform chain
 * [drawSceneImage] uses (canvas scale + canvas offset +
 * viewport center). Returned list is in [CornerHandles]
 * order (TL, TR, BL, BR).
 *
 * The transform is:
 *
 *   screenX = viewportCenterX + canvasOffsetX + worldX * canvasScale
 *   screenY = viewportCenterY + canvasOffsetY + worldY * canvasScale
 *
 * where `worldX` is `imageCenterX + (localX - width/2) * cos(rot) - (localY - height/2) * sin(rot)`
 * (the rotation is applied first to put the local corner
 * in the image's world frame, then the canvas scale +
 * offset paint it on screen).
 *
 * Pure function: no I/O, no allocation beyond the result list.
 * Used by the corner-drag pointer handler to compute both
 * the hit-test (compare finger position to projected
 * corners) and the per-frame new image center / scale
 * (compare the move-handle's new screen position to the
 * anchor's fixed screen position).
 */
internal fun projectImageCornersToScreen(
    imageCenter: Offset,
    imageWidth: Float,
    imageHeight: Float,
    rotationDegrees: Float,
    canvasScale: Float,
    canvasOffset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): List<Offset> {
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val halfW = imageWidth / 2f
    val halfH = imageHeight / 2f
    val centerX = viewportWidth / 2f + canvasOffset.x
    val centerY = viewportHeight / 2f + canvasOffset.y
    // Corners in image-local space, TL / TR / BL / BR
    val localCorners = listOf(
        Offset(0f, 0f),
        Offset(imageWidth, 0f),
        Offset(0f, imageHeight),
        Offset(imageWidth, imageHeight),
    )
    return localCorners.map { local ->
        // Center the corner on the image's local origin,
        // rotate, then re-center, then translate to world.
        val dx = local.x - halfW
        val dy = local.y - halfH
        val worldX = imageCenter.x + dx * cosR - dy * sinR
        val worldY = imageCenter.y + dx * sinR + dy * cosR
        Offset(centerX + worldX * canvasScale, centerY + worldY * canvasScale)
    }
}

internal data class RotationHandleGeometry(
    val anchor: Offset,
    val handleCenter: Offset,
)

internal fun projectImageRotationHandleToScreen(
    imageCenter: Offset,
    imageHeight: Float,
    rotationDegrees: Float,
    handleOffsetPx: Float,
    canvasScale: Float,
    canvasOffset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): RotationHandleGeometry {
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val centerX = viewportWidth / 2f + canvasOffset.x
    val centerY = viewportHeight / 2f + canvasOffset.y
    val topCenterLocalY = -imageHeight / 2f
    val anchorWorld = Offset(
        x = imageCenter.x - topCenterLocalY * sinR,
        y = imageCenter.y + topCenterLocalY * cosR,
    )
    val anchor = Offset(
        x = centerX + anchorWorld.x * canvasScale,
        y = centerY + anchorWorld.y * canvasScale,
    )
    val outwardX = sinR
    val outwardY = -cosR
    return RotationHandleGeometry(
        anchor = anchor,
        handleCenter = Offset(
            x = anchor.x + outwardX * handleOffsetPx,
            y = anchor.y + outwardY * handleOffsetPx,
        ),
    )
}

internal fun projectGroupRotationHandleToScreen(
    groupAabb: GroupAabb,
    handleOffsetPx: Float,
    canvasScale: Float,
    canvasOffset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): RotationHandleGeometry {
    val centerX = viewportWidth / 2f + canvasOffset.x
    val centerY = viewportHeight / 2f + canvasOffset.y
    val anchor = Offset(
        x = centerX + groupAabb.center.x * canvasScale,
        y = centerY + groupAabb.rect.top * canvasScale,
    )
    return RotationHandleGeometry(
        anchor = anchor,
        handleCenter = Offset(anchor.x, anchor.y - handleOffsetPx),
    )
}

internal fun hitTestRotationHandle(
    geometry: RotationHandleGeometry,
    screenPoint: Offset,
    hitRadiusPx: Float,
): Boolean {
    return hypot(
        screenPoint.x - geometry.handleCenter.x,
        screenPoint.y - geometry.handleCenter.y,
    ) <= hitRadiusPx
}

/**
 * Hit-test a single corner handle. Returns the corner index
 * (0..3 = TL, TR, BL, BR) if [screenPoint] is within
 * [hitRadiusPx] of any of the 4 projected corners, or
 * `-1` if no corner is hit.
 *
 * `hitRadiusPx` is in SCREEN pixels and is typically
 * `32.dp.toPx()` — bigger than the visual handle radius
 * (~10.dp) so a fingertip landing on or just outside the
 * white dot still grabs the corner. The 4 corners are
 * tested in z-order-independent enumeration order; for a
 * well-spaced set of handles on a real image at most one
 * is within 32dp of the finger, so tie-breaking is not
 * needed in practice.
 *
 * The check uses raw pixel distance, NOT screen-DPI-aware
 * scaling — [hitRadiusPx] is expected to be pre-scaled at
 * the call site (the pointer handler has access to
 * [LocalDensity]).
 */
internal fun hitTestCornerHandle(
    projectedCorners: List<Offset>,
    screenPoint: Offset,
    hitRadiusPx: Float,
): Int {
    var bestIndex = -1
    var bestDist = hitRadiusPx
    projectedCorners.forEachIndexed { index, corner ->
        val dx = screenPoint.x - corner.x
        val dy = screenPoint.y - corner.y
        val d = hypot(dx, dy)
        if (d <= bestDist) {
            bestDist = d
            bestIndex = index
        }
    }
    return bestIndex
}

internal fun cornerHandleMovePointForFinger(
    fingerPosition: Offset,
    initialFingerOffsetFromHandle: Offset,
): Offset = fingerPosition - initialFingerOffsetFromHandle

/**
 * Compute the new (scale, center) for a corner-drag gesture.
 *
 * The geometry: the user is dragging corner [CornerSpec.index]
 * (the "move-handle"), and the OPPOSITE corner
 * ([CornerSpec.anchorLocal]) is pinned in place. As the
 * move-handle's screen position moves, the image grows /
 * shrinks around the anchor:
 *
 *   initialDiagonal = dist(initialMoveScreen, initialAnchorScreen)
 *   newDiagonal     = dist(currentMoveScreen,  initialAnchorScreen)
 *   scaleChange     = newDiagonal / initialDiagonal
 *
 *   newCenter = (currentMoveScreen + initialAnchorScreen) / 2
 *
 * Both inputs ([initialMoveScreen], [initialAnchorScreen],
 * [currentMoveScreen]) are in screen pixels — call
 * [projectImageCornersToScreen] to get the initial pair at
 * the down event, and pass the live move-handle screen
 * position on every move event. The returned
 * [scaleFactor] and [newCenterScreen] are used by the
 * pointer handler to dispatch a [TransformSelectedImage]
 * with a [worldPivot] derived from the screen delta.
 *
 * Edge case: if the user drags the move-handle EXACTLY
 * onto the anchor (initialDiagonal collapses to 0), the
 * returned scale factor is `1f` and the center is the
 * anchor itself — the image stops responding rather than
 * exploding to infinity. In practice the 32dp hit radius
 * + the user's natural drag motion keep this from
 * happening.
 */
internal fun computeCornerDragResult(
    // V_NOW (2026-06-11 "旋转图片角点缩放跳位置" fix):
    // added [anchorSignX] / [anchorSignY] so the center
    // formula works for ALL 4 anchor corners, not just BR.
    //
    // The image transform is
    //   corner_world = image.center
    //                + R(rot) * (anchor_local - (dW/2, dH/2))
    // where anchor_local is the anchor's position in the
    // image's local coordinate system. The offset from
    // center depends on which corner is the anchor:
    //   TL(0,0):    (-dW/2, -dH/2)  → signX=-1, signY=-1
    //   TR(dW,0):   (+dW/2, -dH/2)  → signX=+1, signY=-1
    //   BL(0,dH):   (-dW/2, +dH/2)  → signX=-1, signY=+1
    //   BR(dW,dH):  (+dW/2, +dH/2)  → signX=+1, signY=+1
    // Solving for image.center:
    //   center = anchor - R(rot) * (signX*halfW, signY*halfH)
    // The previous version hardcoded signX=+1, signY=+1
    // (BR anchor only), so dragging any corner other than
    // TL (whose anchor IS BR) caused the image to jump.
    initialMoveWorld: Offset,
    initialAnchorWorld: Offset,
    currentMoveWorld: Offset,
    worldWidth: Float,
    worldHeight: Float,
    rotationDegrees: Float,
    anchorSignX: Int = 1,
    anchorSignY: Int = 1,
    minScaleFactor: Float = 0f,
    maxScaleFactor: Float = Float.POSITIVE_INFINITY,
): Pair<Float, Offset> {
    val initialDiag = hypot(
        initialMoveWorld.x - initialAnchorWorld.x,
        initialMoveWorld.y - initialAnchorWorld.y,
    )
    if (initialDiag <= 0.0001f) {
        return Pair(1f, initialAnchorWorld)
    }
    val newDiag = hypot(
        currentMoveWorld.x - initialAnchorWorld.x,
        currentMoveWorld.y - initialAnchorWorld.y,
    )
    val rawRatio = newDiag / initialDiag
    val unclampedScaleFactor = if (kotlin.math.abs(rawRatio - 1f) <= CORNER_DRAG_DEADZONE) {
        1f
    } else {
        rawRatio
    }
    val scaleFactor = unclampedScaleFactor.coerceIn(minScaleFactor, maxScaleFactor)
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    val halfNewW = worldWidth * scaleFactor / 2f
    val halfNewH = worldHeight * scaleFactor / 2f
    // center = anchor - R(rot) * (signX*halfNewW, signY*halfNewH)
    val offsetX = anchorSignX * halfNewW
    val offsetY = anchorSignY * halfNewH
    val newCenterWorld = Offset(
        initialAnchorWorld.x - (offsetX * cosR - offsetY * sinR),
        initialAnchorWorld.y - (offsetX * sinR + offsetY * cosR),
    )
    return Pair(scaleFactor, newCenterWorld)
}

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path): the
 * AABB that encloses every selected image's ROTATED
 * footprint, in world (scene) coordinates. Returned as
 * a [Rect] with `left <= right`, `top <= bottom`, plus
 * a `center` and `width` / `height` convenience fields.
 *
 * Geometry: each image's rotated AABB is computed
 * locally (no canvas transform — world space) and then
 * unioned into a single enclosing rect. The resulting
 * rect is always axis-aligned, which is exactly what
 * the renderer's group-selection chrome wants: a
 * 1-stroke box with 4 clean corner points that don't
 * move as the user rotates individual members.
 *
 * Edge case: a selected list of 0 images returns a
 * zero-sized rect centered at the origin. The renderer
 * / hit-test code is expected to short-circuit on
 * `width <= 0` (no chrome to draw, no corners to
 * hit) — the helper itself doesn't refuse empty
 * lists so the caller can decide its own null
 * handling.
 *
 * Pure function: no I/O, no allocation beyond the
 * result rect. Called once per frame from the main
 * Canvas draw path when `selectedImageIds.size >= 2`
 * (the SINGLE path doesn't need it — per-image corners
 * are drawn by [drawSceneImage] directly).
 */
internal fun groupSelectionAabb(selected: List<SceneImage>): GroupAabb {
    if (selected.isEmpty()) {
        return GroupAabb(
            rect = Rect(0f, 0f, 0f, 0f),
            center = Offset.Zero,
            width = 0f,
            height = 0f,
        )
    }
    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (img in selected) {
        val halfW = img.displaySize.width * img.scale / 2f
        val halfH = img.displaySize.height * img.scale / 2f
        val theta = Math.toRadians(img.rotationDegrees.toDouble())
        val absCos = kotlin.math.abs(kotlin.math.cos(theta)).toFloat()
        val absSin = kotlin.math.abs(kotlin.math.sin(theta)).toFloat()
        val aabbHalfW = absCos * halfW + absSin * halfH
        val aabbHalfH = absSin * halfW + absCos * halfH
        val l = img.center.x - aabbHalfW
        val r = img.center.x + aabbHalfW
        val t = img.center.y - aabbHalfH
        val b = img.center.y + aabbHalfH
        if (l < left) left = l
        if (r > right) right = r
        if (t < top) top = t
        if (b > bottom) bottom = b
    }
    val rect = Rect(left, top, right, bottom)
    return GroupAabb(
        rect = rect,
        center = Offset((left + right) / 2f, (top + bottom) / 2f),
        width = (right - left).coerceAtLeast(0f),
        height = (bottom - top).coerceAtLeast(0f),
    )
}

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path): the
 * rotated footprint AABB of a SINGLE image in world
 * coords — i.e. the smallest axis-aligned rectangle
 * that contains the image's 4 corners after applying
 * `displaySize × scale × R(rot)` around [center].
 *
 * Used by the group corner-drag down handler to
 * capture each member's pre-drag AABB so the reducer
 * can apply
 *   newImageAabb = pivot + (initialImageAabb - pivot) * factor
 * on every move frame, producing a union (= group
 * AABB chrome) that's automatically pinned at the
 * anchor corner A. See the
 * [PockRefViewModelBatch.setSelectedImagesScale]
 * comment for the geometry.
 *
 * Pure function: no I/O, no allocation beyond the
 * result rect.
 */
internal fun rotatedFootprintAabb(
    center: Offset,
    displayW: Float,
    displayH: Float,
    scale: Float,
    rotationDegrees: Float,
): Rect {
    val halfW = displayW * scale / 2f
    val halfH = displayH * scale / 2f
    val theta = Math.toRadians(rotationDegrees.toDouble())
    val absCos = kotlin.math.abs(kotlin.math.cos(theta)).toFloat()
    val absSin = kotlin.math.abs(kotlin.math.sin(theta)).toFloat()
    val aabbHalfW = absCos * halfW + absSin * halfH
    val aabbHalfH = absSin * halfW + absCos * halfH
    return Rect(
        left = center.x - aabbHalfW,
        top = center.y - aabbHalfH,
        right = center.x + aabbHalfW,
        bottom = center.y + aabbHalfH,
    )
}

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path): the
 * unioned AABB result from [groupSelectionAabb].
 * Bundled with the most commonly-needed derived
 * values ([center], [width], [height]) so the
 * renderer / pointer handler doesn't have to
 * recompute them on every frame.
 */
internal data class GroupAabb(
    /** Axis-aligned union rect in world (scene) coords. */
    val rect: Rect,
    /** Center point — same as `(rect.left + rect.right) / 2`. */
    val center: Offset,
    /** Width — same as `rect.right - rect.left` (>= 0). */
    val width: Float,
    /** Height — same as `rect.bottom - rect.top` (>= 0). */
    val height: Float,
)

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path): project
 * the 4 axis-aligned corners of a group AABB to screen
 * pixels using the canvas transform. Same transform
 * chain the per-image helper
 * [projectImageCornersToScreen] uses, but the AABB is
 * ALREADY in world space (not local-to-an-image), so
 * we skip the "center on image, rotate, re-translate"
 * step — just `(viewportCenter + canvasOffset) +
 * worldCorner * canvasScale`.
 *
 * Returned list is in (TL, TR, BL, BR) order to match
 * the per-image helper, so the same
 * [hitTestCornerHandle] + [computeCornerDragResult]
 * pair can be reused for the group case.
 *
 * The AABB is by construction NOT rotated (it unions
 * each member's rotated footprint into a single
 * axis-aligned rect), so the projected corners are
 * always axis-aligned on screen. The user can still
 * rotate individual members, and the group AABB will
 * reshape — but the chrome follows the AABB, not the
 * members, so the box stays axis-aligned by design.
 */
internal fun projectGroupAabbCornersToScreen(
    groupAabb: GroupAabb,
    canvasScale: Float,
    canvasOffset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): List<Offset> {
    val centerX = viewportWidth / 2f + canvasOffset.x
    val centerY = viewportHeight / 2f + canvasOffset.y
    val r = groupAabb.rect
    return listOf(
        // TL
        Offset(centerX + r.left * canvasScale, centerY + r.top * canvasScale),
        // TR
        Offset(centerX + r.right * canvasScale, centerY + r.top * canvasScale),
        // BL
        Offset(centerX + r.left * canvasScale, centerY + r.bottom * canvasScale),
        // BR
        Offset(centerX + r.right * canvasScale, centerY + r.bottom * canvasScale),
    )
}

/**
 * V_NOW (4-corner scale handles, MULTI/BOX path):
 * per-image scale + center recompute for a group
 * scale gesture.
 *
 * The semantics differ from the SINGLE-image corner
 * drag in one important way: each member keeps its
 * OWN scale (and therefore its own size), but all
 * members scale by the SAME factor relative to
 * their pre-drag scale. The pre-drag "diagonal
 * length" we capture is the GROUP AABB diagonal at
 * down time, not any individual image's diagonal;
 * the move-handle / anchor pair is on the group
 * AABB. The reducer applies
 * `imageScale = initialImageScale * newScaleFactor`
 * (clamped) per image and re-centers each image
 * around the new group AABB center.
 *
 * Returns the per-frame `newScaleFactor` (>= 0;
 * the reducer clamps to `[0.1, 8.0]`) and the new
 * group center in world coordinates. The pointer
 * handler converts the screen-space new center to
 * world using the canvas transform's inverse (same
 * math the per-image handler uses).
 */
internal fun computeGroupCornerDragResult(
    initialGroupDiagScreen: Float,
    currentMoveScreen: Offset,
    initialAnchorScreen: Offset,
): Pair<Float, Offset> {
    if (initialGroupDiagScreen <= 0.0001f) {
        return Pair(1f, initialAnchorScreen)
    }
    val newDiag = kotlin.math.hypot(
        currentMoveScreen.x - initialAnchorScreen.x,
        currentMoveScreen.y - initialAnchorScreen.y,
    )
    // V_NOW (2026-06-10 user feedback "对角圆点还是会移动"):
    // Same reasoning as [computeCornerDragResult] — the
    // per-frame scale and the new group AABB center must be
    // derived from the SAME raw ratio. A softened curve here
    // would shrink the rendered box relative to the actual
    // finger position, so the opposite corner handle appears
    // to drift toward the move-handle instead of staying
    // pinned. We apply the symmetric deadzone (the only
    // smoothing that doesn't break the pinned-anchor
    // invariant) and use raw `newDiag/initialDiag` as the
    // per-image scale factor.
    val rawRatio = newDiag / initialGroupDiagScreen
    val scaleFactor = if (kotlin.math.abs(rawRatio - 1f) <= CORNER_DRAG_DEADZONE) {
        1f
    } else {
        kotlin.math.sqrt(rawRatio)
    }
    val newCenterScreen = Offset(
        (currentMoveScreen.x + initialAnchorScreen.x) / 2f,
        (currentMoveScreen.y + initialAnchorScreen.y) / 2f,
    )
    return Pair(scaleFactor, newCenterScreen)
}

/**
 * V_NOW (4-corner scale handles, user feedback
 * "多选 group 缩放太灵敏"): soft threshold +
 * square-root curve for the per-frame scale
 * ratio coming out of the corner drag.
 *
 * Why the curve is needed: the corner drag's raw
 * ratio is `newDiagonal / initialDiagonal`
 * (single image) or the equivalent for the
 * group AABB. A 10% finger motion produces a
 * 10% diagonal change, which becomes a 10%
 * scale factor. The user perceives this as
 * "twice as sensitive as a two-finger pinch"
 * (where the same 10% motion produces a 10%
 * change on the SHORTER of the two diagonals,
 * not the corner-drag's longer one). In the
 * group case, the AABB unions every member's
 * footprint so the diagonal is even longer
 * than any single image's — a tiny finger
 * drift on a 3-image group produces a
 * noticeable resize.
 *
 * Two softening layers:
 *
 *  1. **Symmetric deadzone**
 *     ([CORNER_DRAG_DEADZONE] = 5%): any ratio
 *     `r` in the SYMMETRIC range
 *     `[1/1.05, 1.05]` (i.e. `max(r, 1/r)` ≤
 *     1.05) collapses to exactly 1.0. The
 *     deadzone is computed against the LARGER
 *     of (r, 1/r) so a 5% shrink (r = 0.95)
 *     and a 5% grow (r = 1.05) BOTH fall in
 *     the deadzone — a `|r-1|`-based test
 *     would only catch the grow side. So:
 *     - r = 0.98 (2% shrink) → 1.0 (no-op)
 *     - r = 1.02 (2% grow)   → 1.0 (no-op)
 *     - r = 1.05 (5% grow, edge) → 1.0
 *     - r = 1.06 (6% grow, just outside) → softened
 *
 *  2. **Square-root curve on the ratio itself**
 *     (NOT on a residual): for ratios outside
 *     the deadzone, the effective scale factor
 *     is `sqrt(rawRatio)`. Square root is
 *     symmetric in the relevant sense
 *     (`sqrt(r) * sqrt(1/r) = 1`), so the
 *     shrink and grow directions are softened
 *     identically. So:
 *     - r = 1.06 (6% grow, just outside) → 1.029
 *     - r = 1.10 (10% grow) → 1.0488
 *       (the "feels right" range — 10% of
 *       diagonal drift becomes ~5% scale
 *       change, not 10%)
 *     - r = 1.20 (20% grow) → 1.0954
 *     - r = 1.50 (50% grow) → 1.2247
 *     - r = 2.00 (2x grow) → 1.4142 (large
 *       pulls still work, the curve doesn't
 *       go flat)
 *     - r = 0.80 (20% shrink) → 0.8944
 *     - r = 0.50 (50% shrink) → 0.7071
 *     - r = 0.20 (5x shrink) → 0.4472
 *
 *     The curve converges to the identity as
 *     the ratio diverges from 1 (so a "really
 *     pull it out" gesture still works), but
 *     flattens near 1 (so a "tiny tweak"
 *     gesture feels like a tiny tweak, not
 *     a 5% jump).
 */
private const val CORNER_DRAG_DEADZONE = 0.03f
