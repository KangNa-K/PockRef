package com.pockref.app.ui

import com.pockref.app.model.AxisKind
import com.pockref.app.model.ImageBounds
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SnapAxisX
import com.pockref.app.model.SnapAxisY
import com.pockref.app.model.SnapEdge
import com.pockref.app.model.SnapGuide
import com.pockref.app.model.SpeedSample
import kotlin.math.abs

/**
 * Edge-to-edge snap engine for image drag.
 *
 * The dragged group is represented as an axis-aligned bounding box. For
 * each of its 4 edges we consider the X- or Y-axis distance to every
 * other image's 4 edges; whichever pair is within [thresholdWorld] AND
 * has the smallest |distance| wins for that axis. The drag delta is then
 * adjusted so the moving edge sits exactly on the target edge.
 *
 * X and Y are evaluated independently: a single drag can snap on both
 * axes at once (e.g. landing on a target's top-left corner).
 *
 * Distance is measured in world units. Callers convert the user's "N
 * screen pixels" into world units via `thresholdWorld = thresholdPx /
 * canvasScale` so the snap range feels the same at any zoom.
 *
 * **Snap base rectangle — V6 spec**: every per-image bounds query in
 * this file (sub-image edge, target edge, group union) goes through
 * [imageSnapBounds] which returns the *rotated* axis-aligned bounding
 * box, computed by projecting the rotated image's pixel rectangle onto
 * the world X and Y axes (see [imageRotatedAabb] in PockRefApp.kt for
 * the math). The un-rotated box (`displaySize * scale`, as if rotation
 * were zero) is explicitly NOT used here: for a 45°-rotated portrait
 * image, the un-rotated box undershoots the on-screen footprint by up
 * to ~30% in each axis, which causes snap to fire at edges the user
 * can't actually see and miss edges they can. Per the spec ("以能够
 * 完整包裹住旋转后图形象素的最小水平/垂直矩形作为吸附边界"),
 * the AABB must wrap the visible footprint. Each [compute] / proximity
 * call recomputes the AABB from current [SceneImage.center],
 * [rotationDegrees] and [scale] — no caching, no rotation-ignoring
 * shortcut.
 */
internal object SnapEngine {

    data class SnapDelta(val dx: Float, val dy: Float)

    data class SnapResult(
        val adjustedDeltaWorld: SnapDelta,
        val guide: SnapGuide?,
    )

    fun compute(
        deltaWorld: SnapDelta,
        dragIds: Set<String>,
        allImages: List<SceneImage>,
        thresholdWorld: Float,
    ): SnapResult {
        if (dragIds.isEmpty() || allImages.size <= dragIds.size) {
            return SnapResult(deltaWorld, null)
        }
        if (thresholdWorld <= 0f) {
            return SnapResult(deltaWorld, null)
        }

        val dragImages = allImages.filter { it.id in dragIds }
        if (dragImages.isEmpty()) return SnapResult(deltaWorld, null)

        val group = groupBounds(dragImages, deltaWorld)
        val targets = allImages.filter { it.id !in dragIds }
        if (targets.isEmpty()) return SnapResult(deltaWorld, null)

        val xSnap = bestX(targets, group, thresholdWorld, dragImages, deltaWorld)
        val ySnap = bestY(targets, group, thresholdWorld, dragImages, deltaWorld)

        if (xSnap == null && ySnap == null) {
            return SnapResult(deltaWorld, null)
        }

        val adjustedDx = xSnap?.let { x ->
            if (x.axisKind == AxisKind.CENTER) {
                // CENTER snap: align group.cx to target.cx. The dx
                // needed is the SAME for every sub-image (group is a
                // rigid body), so we just use the arithmetic-mean
                // group center. sourceImageId is still recorded for
                // the highlight overlay — any sub-image is a fine
                // proxy.
                val groupCenterX = dragImages.map { it.center.x }.average().toFloat()
                x.targetEdgeWorld - groupCenterX
            } else {
                // For the X-axis candidate we look up the source sub-image (the
                // single dragged image whose edge fired the snap) and read its
                // ORIGINAL (pre-delta) edge position from the **rotated AABB**
                // (NOT the un-rotated half-extent) so the snap-aligned pose
                // matches the rotated-AABB edge the user actually saw snap
                // fire. Group drag: the source is the sub-image closest to
                // the target edge, so the resulting group translation lands
                // THAT image's edge on the target — not the group AABB edge.
                // Single-image drag degenerates naturally: there's only one
                // image and sourceEdge is its current edge.
                val sourceImg = dragImages.firstOrNull { it.id == x.sourceImageId }
                    ?: dragImages.first()
                val sb = imageSnapBounds(sourceImg)
                val originalEdge = if (x.movingEdge == SnapEdge.LEFT) sb.left else sb.right
                x.targetEdgeWorld - originalEdge
            }
        } ?: deltaWorld.dx

        val adjustedDy = ySnap?.let { y ->
            if (y.axisKind == AxisKind.CENTER) {
                // CENTER snap: align group.cy to target.cy (mirror
                // of the X CENTER branch).
                val groupCenterY = dragImages.map { it.center.y }.average().toFloat()
                y.targetEdgeWorld - groupCenterY
            } else {
                val sourceImg = dragImages.firstOrNull { it.id == y.sourceImageId }
                    ?: dragImages.first()
                val sb = imageSnapBounds(sourceImg)
                val originalEdge = if (y.movingEdge == SnapEdge.TOP) sb.top else sb.bottom
                y.targetEdgeWorld - originalEdge
            }
        } ?: deltaWorld.dy

        val adjustedGroup = ImageBounds(
            left = group.left + (adjustedDx - deltaWorld.dx),
            top = group.top + (adjustedDy - deltaWorld.dy),
            right = group.right + (adjustedDx - deltaWorld.dx),
            bottom = group.bottom + (adjustedDy - deltaWorld.dy),
        )

        // V5: detect the "corner snap" case — both axes are EDGE
        // candidates AND they target the SAME image AND they hit
        // the SAME corner of that target. Used by the renderer to
        // draw a slightly stronger guide (per user spec: corner >
        // mid > edge priority; the renderer reflects that in stroke
        // weight / color).
        val isCorner = xSnap != null && ySnap != null &&
            xSnap.axisKind == AxisKind.EDGE &&
            ySnap.axisKind == AxisKind.EDGE &&
            xSnap.targetImageId == ySnap.targetImageId &&
            shareSameCorner(xSnap.movingEdge, xSnap.targetEdge, ySnap.movingEdge, ySnap.targetEdge)

        val guide = SnapGuide(
            axisX = xSnap,
            axisY = ySnap,
            groupBounds = adjustedGroup,
            isCornerSnap = isCorner,
        )
        return SnapResult(SnapDelta(adjustedDx, adjustedDy), guide)
    }

    /**
     * V5: returns true if the (movingX, targetX) + (movingY, targetY)
     * edge pair both reference the SAME corner of the target image.
     * The four corners are:
     *   (LEFT,  TOP)    ↔ group-left aligns with target-left,
     *                     group-top aligns with target-top
     *   (RIGHT, TOP)    ↔ group-right aligns with target-left,
     *                     group-top aligns with target-top
     *   (LEFT,  BOTTOM) ↔ group-left aligns with target-right,
     *                     group-bottom aligns with target-bottom
     *   (RIGHT, BOTTOM) ↔ group-right aligns with target-right,
     *                     group-bottom aligns with target-bottom
     * In other words, when the two target edges are the SAME
     * corner's two sides, the moving edges must be the opposite
     * pair to physically align the moving image to that corner
     * (or wrap around it).
     */
    private fun shareSameCorner(
        mx: SnapEdge, tx: SnapEdge,
        my: SnapEdge, ty: SnapEdge,
    ): Boolean {
        // Target corner 1: (tx=LEFT, ty=TOP). Group must align
        // (mx=LEFT, my=TOP) — i.e. group-left to target-left AND
        // group-top to target-top. (Or equivalently, the moving
        // image sits "to the upper-left of" or "wraps over" the
        // target's upper-left corner.)
        val targetLeftTop = (tx == SnapEdge.LEFT && ty == SnapEdge.TOP)
        val groupLeftTop = (mx == SnapEdge.LEFT && my == SnapEdge.TOP)
        if (targetLeftTop && groupLeftTop) return true

        // Target corner 2: (tx=RIGHT, ty=TOP). Group must align
        // group-right to target-right + group-top to target-top.
        val targetRightTop = (tx == SnapEdge.RIGHT && ty == SnapEdge.TOP)
        val groupRightTop = (mx == SnapEdge.RIGHT && my == SnapEdge.TOP)
        if (targetRightTop && groupRightTop) return true

        // Target corner 3: (tx=LEFT, ty=BOTTOM). Group-left to
        // target-left + group-bottom to target-bottom.
        val targetLeftBottom = (tx == SnapEdge.LEFT && ty == SnapEdge.BOTTOM)
        val groupLeftBottom = (mx == SnapEdge.LEFT && my == SnapEdge.BOTTOM)
        if (targetLeftBottom && groupLeftBottom) return true

        // Target corner 4: (tx=RIGHT, ty=BOTTOM). Group-right to
        // target-right + group-bottom to target-bottom.
        val targetRightBottom = (tx == SnapEdge.RIGHT && ty == SnapEdge.BOTTOM)
        val groupRightBottom = (mx == SnapEdge.RIGHT && my == SnapEdge.BOTTOM)
        if (targetRightBottom && groupRightBottom) return true

        return false
    }

    /**
     * Lightweight distance probe used by the intent-detection state
     * machine: find the nearest target image to the dragged group under
     * the candidate delta, and return the (sub-image-min) distance plus
     * the target id. Used to decide whether the cursor is in the 15px
     * trigger range (and which target it's closest to) WITHOUT running
     * the full snap computation.
     *
     * Returns `null` if there are no targets. The returned distance is
     * the sub-image-min distance (matching [compute]'s snap-distance
     * semantics for the cross-edge candidates) so the trigger range
     * matches what would actually fire the snap.
     *
     * Only perpendicular-axis overlap is checked; if the group and
     * target have no overlap on the relevant axis, the pair is
     * considered "not facing" and the distance is treated as +∞ for
     * the comparison — same as the engine's internal bestX/bestY.
     */
    fun measureClosestTargetDistance(
        deltaWorld: SnapDelta,
        dragIds: Set<String>,
        allImages: List<SceneImage>,
    ): TargetProximity? {
        if (dragIds.isEmpty() || allImages.size <= dragIds.size) return null
        val dragImages = allImages.filter { it.id in dragIds }
        if (dragImages.isEmpty()) return null
        val group = groupBounds(dragImages, deltaWorld)
        val targets = allImages.filter { it.id !in dragIds }
        if (targets.isEmpty()) return null

        var bestDist = Float.POSITIVE_INFINITY
        var bestTargetId: String? = null
        for (target in targets) {
            val tb = imageSnapBounds(target)

            // Try every (movingEdge, targetEdge) pair as a candidate
            // proximity and take the smallest sub-image-min distance.
            // We deliberately compute this in the same way the snap
            // engine does, so a "near" reading here maps 1:1 to a snap
            // that would fire if engaged.
            val pairs: List<Pair<SnapEdge, Float>> = buildList {
                if (verticalRangesOverlap(group.top, group.bottom, tb.top, tb.bottom)) {
                    add(SnapEdge.LEFT to tb.left)
                    add(SnapEdge.LEFT to tb.right)
                    add(SnapEdge.RIGHT to tb.left)
                    add(SnapEdge.RIGHT to tb.right)
                }
                if (horizontalRangesOverlap(group.left, group.right, tb.left, tb.right)) {
                    add(SnapEdge.TOP to tb.top)
                    add(SnapEdge.TOP to tb.bottom)
                    add(SnapEdge.BOTTOM to tb.top)
                    add(SnapEdge.BOTTOM to tb.bottom)
                }
            }
            for ((movingEdge, targetEdgeWorld) in pairs) {
                val dist = subImageMinDistance(
                    dragImages = dragImages,
                    movingEdge = movingEdge,
                    targetEdgeWorld = targetEdgeWorld,
                    candidateDelta = deltaWorld,
                )
                if (dist < bestDist) {
                    bestDist = dist
                    bestTargetId = target.id
                }
            }
        }
        return if (bestTargetId != null) TargetProximity(bestTargetId, bestDist) else null
    }

    /**
     * Sub-image-min edge distance for ONE movingEdge, mirroring the
     * inner loop of [bestX] / [bestY]. Returns +∞ if [dragImages] is
     * empty so a missing-image fallback is a no-op rather than 0.
     */
    private fun subImageMinDistance(
        dragImages: List<SceneImage>,
        movingEdge: SnapEdge,
        targetEdgeWorld: Float,
        candidateDelta: SnapDelta,
    ): Float {
        if (dragImages.isEmpty()) return Float.POSITIVE_INFINITY
        var minDist = Float.POSITIVE_INFINITY
        for (img in dragImages) {
            val sb = imageSnapBounds(img, candidateDelta.dx, candidateDelta.dy)
            val subEdge = when (movingEdge) {
                SnapEdge.LEFT -> sb.left
                SnapEdge.RIGHT -> sb.right
                SnapEdge.TOP -> sb.top
                SnapEdge.BOTTOM -> sb.bottom
            }
            val d = kotlin.math.abs(subEdge - targetEdgeWorld)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    /**
     * Result of a [measureClosestTargetDistance] probe: which target
     * image is closest to the dragged group right now, and how far
     * (in world units) the closest sub-image edge sits from the
     * target's nearest relevant edge.
     */
    data class TargetProximity(
        val targetImageId: String,
        val distanceWorld: Float,
    )

    /**
     * Rotated AABB of a single image, optionally translated by a drag
     * delta. **The single source of truth for every per-image bounds
     * query in this file** — do not reintroduce
     * `displaySize * scale / 2f` style math anywhere, it ignores
     * [SceneImage.rotationDegrees] and breaks snap for rotated images
     * (a 45° rotated portrait's true AABB half-extents are
     * `|cos|·w/2 + |sin|·h/2` in X and the same in Y with axes swapped,
     * not just `w/2 × h/2`).
     */
    private fun imageSnapBounds(img: SceneImage, dx: Float = 0f, dy: Float = 0f): ImageBounds {
        val aabb = imageRotatedAabb(img)
        return ImageBounds(
            left = aabb.left + dx,
            top = aabb.top + dy,
            right = aabb.right + dx,
            bottom = aabb.bottom + dy,
        )
    }

    /**
     * AABB of the dragged group after the user's drag delta is applied.
     * Union of every sub-image's **rotated** AABB (so the group footprint
     * matches what the user actually sees on screen, including the
     * corners sticking out of any rotated sub-image's un-rotated box).
     */
    private fun groupBounds(dragImages: List<SceneImage>, delta: SnapDelta): ImageBounds {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (img in dragImages) {
            val b = imageSnapBounds(img, delta.dx, delta.dy)
            if (b.left < left) left = b.left
            if (b.top < top) top = b.top
            if (b.right > right) right = b.right
            if (b.bottom > bottom) bottom = b.bottom
        }
        return ImageBounds(left, top, right, bottom)
    }

    private fun bestX(
        targets: List<SceneImage>,
        group: ImageBounds,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
    ): SnapAxisX? {
        var best: SnapAxisX? = null
        for (target in targets) {
            // Use the rotated AABB so the overlap / edge / centre
            // checks all reference the image's visible footprint, not
            // its un-rotated half-extents.
            val tb = imageSnapBounds(target)
            if (!verticalRangesOverlap(group.top, group.bottom, tb.top, tb.bottom)) continue

            // For each (movingEdge, targetEdge) candidate, the snap distance
            // is the MIN over the dragged group's sub-images of the per-image
            // |subEdge - targetEdgeWorld| — NOT the group AABB edge distance.
            // This eliminates "false positive" snaps where the group's outer
            // AABB happens to graze a target but no individual sub-image is
            // actually close to it. For a single-image drag there's only one
            // sub-image so the formula reduces to the old group-edge math.
            best = pickCloserX(best, considerX(SnapEdge.LEFT, tb.left, SnapEdge.LEFT, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserX(best, considerX(SnapEdge.LEFT, tb.right, SnapEdge.RIGHT, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserX(best, considerX(SnapEdge.RIGHT, tb.left, SnapEdge.LEFT, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserX(best, considerX(SnapEdge.RIGHT, tb.right, SnapEdge.RIGHT, target, thresholdWorld, dragImages, candidateDelta, group))

            // V5: CENTER candidate — align group.cx to target.cx. This
            // is a "complement" to the EDGE candidates: when the user's
            // drag puts the group center near a target's center axis
            // but NOT near any of the target's edges (e.g. group and
            // target have different widths so the group center sits
            // over the target's interior), the EDGE candidates miss
            // and CENTER catches the intent. pickCloserX uses distance
            // as the tie-breaker; on equal distance EDGE wins (it's
            // evaluated first) which is the right behavior — the user
            // didn't really "aim" for center-alignment in that case.
            val centerSnap = considerCenterX(target, thresholdWorld, dragImages, candidateDelta, group)
            if (centerSnap != null) {
                best = pickCloserX(best, centerSnap)
            }
        }
        return best
    }

    private fun bestY(
        targets: List<SceneImage>,
        group: ImageBounds,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
    ): SnapAxisY? {
        var best: SnapAxisY? = null
        for (target in targets) {
            val tb = imageSnapBounds(target)
            if (!horizontalRangesOverlap(group.left, group.right, tb.left, tb.right)) continue

            best = pickCloserY(best, considerY(SnapEdge.TOP, tb.top, SnapEdge.TOP, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserY(best, considerY(SnapEdge.TOP, tb.bottom, SnapEdge.BOTTOM, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserY(best, considerY(SnapEdge.BOTTOM, tb.top, SnapEdge.TOP, target, thresholdWorld, dragImages, candidateDelta, group))
            best = pickCloserY(best, considerY(SnapEdge.BOTTOM, tb.bottom, SnapEdge.BOTTOM, target, thresholdWorld, dragImages, candidateDelta, group))

            // V5: CENTER (mid-axis) Y candidate; see bestX for the
            // "EDGE wins ties" rationale. CENTER fires only when EDGE
            // candidates miss entirely, giving the user a
            // mid-alignment fallback for asymmetric groups.
            val centerSnap = considerCenterY(target, thresholdWorld, dragImages, candidateDelta, group)
            if (centerSnap != null) {
                best = pickCloserY(best, centerSnap)
            }
        }
        return best
    }

    /**
     * V5: build a CENTER-axis X candidate. Returns null if the GROUP's
     * center axis is outside [thresholdWorld] of target.center.x. The
     * distance is measured at the GROUP level (midpoint of group.left
     * and group.right after the candidate delta is applied) — not at
     * the sub-image level — so a CENTER snap represents "the dragged
     * group's central X axis aligns with the target's central X
     * axis" as a single intent, distinct from the EDGE sub-image-min
     * candidate. This separation matters because for groups with
     * different-sized sub-images, the sub-image-min EDGE distance and
     * the group-level CENTER distance are NOT the same value, and
     * the spec's "mid > edge" priority only applies to genuine
     * "align to middle" intents, not to every random case where
     * one sub-image happens to be near target.cx.
     */
    private fun considerCenterX(
        target: SceneImage,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
        group: ImageBounds,
    ): SnapAxisX? {
        if (dragImages.isEmpty()) return null
        // Group center after applying the candidate delta. The group
        // AABB is already the union, so its midpoint IS the group
        // center for the purpose of "align to middle" — sub-image
        // arithmetic-mean would give a slightly different number
        // for asymmetric groups, but the AABB midpoint is the
        // visual center the user sees on screen.
        val groupCenterX = (group.left + group.right) / 2f
        val dist = abs(groupCenterX - target.center.x)
        if (dist > thresholdWorld) return null
        // sourceImageId is just a stable pick for the highlight
        // overlay; any sub-image is fine since the apply-delta
        // path uses group center arithmetic, not a per-image edge.
        val sourceId = dragImages.minByOrNull {
            abs(it.center.x - (groupCenterX - candidateDelta.dx))
        }!!.id
        return SnapAxisX(
            movingEdge = SnapEdge.LEFT,    // arbitrary; CENTER apply path ignores movingEdge
            movingEdgeWorld = group.left,
            sourceImageId = sourceId,
            targetImageId = target.id,
            targetEdge = SnapEdge.LEFT,    // arbitrary
            targetEdgeWorld = target.center.x,
            distanceWorld = dist,
            axisKind = AxisKind.CENTER,
        )
    }

    /**
     * V5: build a CENTER-axis Y candidate. Mirror of [considerCenterX].
     */
    private fun considerCenterY(
        target: SceneImage,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
        group: ImageBounds,
    ): SnapAxisY? {
        if (dragImages.isEmpty()) return null
        val groupCenterY = (group.top + group.bottom) / 2f
        val dist = abs(groupCenterY - target.center.y)
        if (dist > thresholdWorld) return null
        val sourceId = dragImages.minByOrNull {
            abs(it.center.y - (groupCenterY - candidateDelta.dy))
        }!!.id
        return SnapAxisY(
            movingEdge = SnapEdge.TOP,
            movingEdgeWorld = group.top,
            sourceImageId = sourceId,
            targetImageId = target.id,
            targetEdge = SnapEdge.TOP,
            targetEdgeWorld = target.center.y,
            distanceWorld = dist,
            axisKind = AxisKind.CENTER,
        )
    }

    /**
     * Two vertical ranges overlap iff [aTop] < [bBottom] AND [bTop] < [aBottom].
     * Used to gate X-axis snap: edges can only align when the dragged
     * group and target are actually facing each other on the Y axis.
     */
    private fun verticalRangesOverlap(aTop: Float, aBottom: Float, bTop: Float, bBottom: Float): Boolean =
        aTop < bBottom && bTop < aBottom

    private fun horizontalRangesOverlap(aLeft: Float, aRight: Float, bLeft: Float, bRight: Float): Boolean =
        aLeft < bRight && bLeft < aRight

    /**
     * Evaluate one X-axis snap candidate against a target image.
     *
     * The "snap distance" is the MIN over the dragged group's sub-images of
     * `|subImage.<movingEdge> + candidateDelta.dx - targetEdgeWorld|`. This
     * is the change from the previous group-AABB-distance math: a candidate
     * only fires when at least one sub-image is actually close to the target
     * edge, not just because the group's outer AABB grazes it.
     *
     * Returns null if no sub-image is within [thresholdWorld] of [targetEdgeWorld].
     */
    private fun considerX(
        movingEdge: SnapEdge,
        targetEdgeWorld: Float,
        targetEdge: SnapEdge,
        target: SceneImage,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
        group: ImageBounds,
    ): SnapAxisX? {
        var minDist = Float.POSITIVE_INFINITY
        var sourceId: String? = null
        for (img in dragImages) {
            // Sub-image snap base = rotated AABB edge (NOT the
            // un-rotated half-extent) so a 45°-rotated sub-image
            // contributes its visible left/right edge, not the
            // un-rotated box's edge which can be 20-30% inside the
            // actual visible footprint.
            val sb = imageSnapBounds(img, candidateDelta.dx, 0f)
            val subEdge = if (movingEdge == SnapEdge.LEFT) sb.left else sb.right
            val d = abs(subEdge - targetEdgeWorld)
            if (d < minDist) {
                minDist = d
                sourceId = img.id
            }
        }
        if (sourceId == null) return null
        if (minDist > thresholdWorld) return null
        val movingEdgeWorld = if (movingEdge == SnapEdge.LEFT) group.left else group.right
        return SnapAxisX(
            movingEdge = movingEdge,
            movingEdgeWorld = movingEdgeWorld,
            sourceImageId = sourceId,
            targetImageId = target.id,
            targetEdge = targetEdge,
            targetEdgeWorld = targetEdgeWorld,
            distanceWorld = minDist,
        )
    }

    /**
     * Y-axis counterpart of [considerX]. Same sub-image-min logic.
     */
    private fun considerY(
        movingEdge: SnapEdge,
        targetEdgeWorld: Float,
        targetEdge: SnapEdge,
        target: SceneImage,
        thresholdWorld: Float,
        dragImages: List<SceneImage>,
        candidateDelta: SnapDelta,
        group: ImageBounds,
    ): SnapAxisY? {
        var minDist = Float.POSITIVE_INFINITY
        var sourceId: String? = null
        for (img in dragImages) {
            val sb = imageSnapBounds(img, 0f, candidateDelta.dy)
            val subEdge = if (movingEdge == SnapEdge.TOP) sb.top else sb.bottom
            val d = abs(subEdge - targetEdgeWorld)
            if (d < minDist) {
                minDist = d
                sourceId = img.id
            }
        }
        if (sourceId == null) return null
        if (minDist > thresholdWorld) return null
        val movingEdgeWorld = if (movingEdge == SnapEdge.TOP) group.top else group.bottom
        return SnapAxisY(
            movingEdge = movingEdge,
            movingEdgeWorld = movingEdgeWorld,
            sourceImageId = sourceId,
            targetImageId = target.id,
            targetEdge = targetEdge,
            targetEdgeWorld = targetEdgeWorld,
            distanceWorld = minDist,
        )
    }

    private fun pickCloserX(a: SnapAxisX?, b: SnapAxisX?): SnapAxisX? = when {
        a == null -> b
        b == null -> a
        b.distanceWorld < a.distanceWorld -> b
        else -> a
    }

    private fun pickCloserY(a: SnapAxisY?, b: SnapAxisY?): SnapAxisY? = when {
        a == null -> b
        b == null -> a
        b.distanceWorld < a.distanceWorld -> b
        else -> a
    }
}
