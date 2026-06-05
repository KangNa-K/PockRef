package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.AngleSnapGuide
import com.pockref.app.model.SceneImage
import kotlin.math.IEEErem
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

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
    val newScale = (image.scale * scaleChange).coerceIn(0.05f, 10f)
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
    val newScale = (image.scale * factor).coerceIn(0.05f, 10f)
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
 * once. If any image would clamp against the [0.05, 10] range, the
 * effective factor for the group is reduced so the most-clamped image
 * lands exactly at the boundary. Mirrors the production behavior in
 * [PockRefViewModelTransform.scaleSelected] / `transformSelectedImage`.
 */
internal fun computeGroupScaleFactor(
    selected: List<SceneImage>,
    requested: Float,
): Float {
    if (selected.isEmpty()) return 1f
    val newScaleByImage = selected.associate { it.id to (it.scale * requested).coerceIn(0.05f, 10f) }
    val anyClamped = selected.any { img ->
        val raw = img.scale * requested
        val clamped = newScaleByImage[img.id]!!
        raw != clamped
    }
    if (!anyClamped) return requested
    val img = selected.first { newScaleByImage[it.id] != it.scale * requested }
    return newScaleByImage[img.id]!! / img.scale
}
