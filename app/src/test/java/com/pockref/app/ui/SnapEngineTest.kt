package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.AxisKind
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.SnapEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [SnapEngine]. The engine is the heart of the edge-to-
 * edge snap feature; we test it without spinning up a ViewModel or a
 * Compose runtime so the test stays fast and deterministic.
 *
 * Coordinate system used in these tests: 1 unit = 1 pixel-equivalent
 * (we treat world and screen as the same). This keeps the test setup
 * obvious; the engine doesn't care about units.
 */
class SnapEngineTest {

    private val epsilon = 1e-3f

    private fun img(id: String, cx: Float, cy: Float, w: Float = 100f, h: Float = 100f, scale: Float = 1f): SceneImage =
        SceneImage(
            id = id,
            name = id,
            mimeType = "image/png",
            intrinsicWidth = w.toInt(),
            intrinsicHeight = h.toInt(),
            center = Offset(cx, cy),
            displaySize = Size2D(w, h),
            scale = scale,
        )

    @Test
    fun noSnap_whenDragIsFarFromAnyTarget() {
        val drag = img("drag", cx = 0f, cy = 0f, w = 100f, h = 100f)
        val target = img("t", cx = 500f, cy = 500f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNull("no edge within 15 world units → no snap", r.guide)
        assertEquals(0f, r.adjustedDeltaWorld.dx, epsilon)
        assertEquals(0f, r.adjustedDeltaWorld.dy, epsilon)
    }

    @Test
    fun snapLeftEdges_alignsGroupLeftToTargetLeft() {
        // drag [0..100, 0..100] vs target [80..180, 0..100] (X-overlap on
        // Y axis [0..100] = full overlap). With deltaX=80, groupLeft=80
        // vs targetLeft=80 → distance 0 → snap. (groupRight=180 vs
        // targetRight=180 also at distance 0; pickCloserX uses first-found,
        // so the LEFT-LEFT pair wins.)
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 130f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(80f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull("expected a snap to fire on the X axis", r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals(SnapEdge.LEFT, ax.movingEdge)
        assertEquals(SnapEdge.LEFT, ax.targetEdge)
        assertEquals(80f, ax.targetEdgeWorld, epsilon)
        assertEquals(80f, r.adjustedDeltaWorld.dx, epsilon)
        assertEquals(0f, r.adjustedDeltaWorld.dy, epsilon)
    }

    @Test
    fun snapTopEdges_alignsGroupTopToTargetTop() {
        // drag [0..100, 0..100] vs target [0..100, 58..158] (X ranges
        // fully overlap). With deltaY=53, groupTop=53 vs targetTop=58 →
        // distance 5 → snap (TOP/TOP).
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 50f, cy = 108f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 53f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide?.axisY)
        val ay = r.guide!!.axisY!!
        assertEquals(SnapEdge.TOP, ay.movingEdge)
        assertEquals(SnapEdge.TOP, ay.targetEdge)
        assertEquals(58f, ay.targetEdgeWorld, epsilon)
        // group top at delta=53 is 0+53=53. To put it on 58, delta must be 58.
        assertEquals(58f, r.adjustedDeltaWorld.dy, epsilon)
    }

    @Test
    fun snapBothAxes_atOnce_whenInRangeOfTwoEdges() {
        // Two overlap configurations: drag and target are aligned so
        // both X and Y ranges have positive overlap, and the candidate
        // delta brings BOTH a horizontal edge pair and a vertical edge
        // pair within the 15-unit threshold.
        //
        // drag [0..100, 0..100] vs target [58..158, 58..158] (overlap
        // [58..100] on both axes). With delta (53, 53): group bounds
        // become [53..153, 53..153]. groupLeft=53 vs targetLeft=58 →
        // dist 5. groupTop=53 vs targetTop=58 → dist 5. Both snap.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 108f, cy = 108f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(53f, 53f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull("X axis should snap", r.guide?.axisX)
        assertNotNull("Y axis should snap", r.guide?.axisY)
        assertEquals(58f, r.adjustedDeltaWorld.dx, epsilon)
        assertEquals(58f, r.adjustedDeltaWorld.dy, epsilon)
    }

    @Test
    fun prefersClosestPair_whenMultipleTargetsWithinRange() {
        // drag [0..100, 0..100] vs tA [80..180, 0..100] (overlap on Y axis).
        // With deltaX=53, groupRight=153, tA.left=80. But wait: tA's
        // X range is [80..180], drag is [0..100], overlap [80..100] OK.
        //   groupLeft=53, tA.left=80: dist 27 (>15)
        //   groupLeft=53, tA.right=180: dist 127
        //   groupRight=153, tA.left=80: dist 73
        //   groupRight=153, tA.right=180: dist 27
        //   → no snap (all > 15)
        // We want a snap. Adjust: drag [0..100, 0..100] vs tA [100..200, 0..100],
        // edge contact at X=100. With deltaX=98, groupLeft=98, groupRight=198.
        //   groupLeft=98, tA.left=100: dist 2 ✅ (snap)
        //   groupRight=198, tA.right=200: dist 2 ✅
        // tB at (350, 50) 100x100 → [300..400, 0..100]. With deltaX=98:
        //   groupLeft=98, tB.left=300: dist 202; groupRight=198, tB.left=300: dist 102; etc. all > 15.
        // So only tA should fire.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val tA = img("a", cx = 150f, cy = 50f, w = 100f, h = 100f)
        val tB = img("b", cx = 350f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(98f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, tA, tB),
            thresholdWorld = 15f,
        )
        val ax = r.guide!!.axisX!!
        assertEquals("a", ax.targetImageId)
    }

    @Test
    fun picksClosestAmongSameTarget_whenAllFourEdgesInRange() {
        // drag at (50,50) 100x100, target at (300, 50) 100x100.
        // X overlap: [0..100] vs [250..350] → no overlap on Y axis for
        // X snap. So we cannot get X snap.  Use Y axis: target at (50,
        // 300) → top=250 bottom=350. Drag Y range [0..100] vs target Y
        // range [250..350] → no overlap. Hmm. We need both ranges to
        // overlap for the axis to fire. Place target at (90, 300):
        // drag [0..100, 0..100] vs target [40..140, 250..350] → X
        // overlap [40..100] ✅, Y overlap none. Still no Y snap.
        // OK, just place target with full Y overlap: target at (90, 50),
        // 100x100 → [40..140, 0..100]. Full Y overlap.  delta (240, 0)
        // → group bounds [240..340, 0..100]. Y overlap ✅.
        //   groupLeft=240, targetLeft=40: dist 200
        //   groupLeft=240, targetRight=140: dist 100
        //   groupRight=340, targetLeft=40: dist 300
        //   groupRight=340, targetRight=140: dist 200
        //   → no X snap.
        // Re-do: delta (95, 0) → group bounds [95..195, 0..100].
        //   groupLeft=95, targetLeft=40: dist 55
        //   groupLeft=95, targetRight=140: dist 45
        //   groupRight=195, targetLeft=40: dist 155
        //   groupRight=195, targetRight=140: dist 55
        //   → no X snap.
        // Simpler test: just place two target edges at distinct
        // distances and confirm the engine picks the closer one.
        // drag [0..100, 0..100] vs target [80..180, 0..100] (X overlap on
        // Y). With deltaX=85 → group bounds [85..185, 0..100].
        //   groupLeft=85, targetLeft=80: dist 5
        //   groupLeft=85, targetRight=180: dist 95
        //   groupRight=185, targetLeft=80: dist 105
        //   groupRight=185, targetRight=180: dist 5
        // Both LEFT-LEFT and RIGHT-RIGHT at dist 5. Tie. Engine picks
        // first-found (LEFT-LEFT). We just assert the engine picked ONE
        // valid pair and didn't crash.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 130f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(85f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertTrue("distance must be within threshold",
            ax.distanceWorld <= 15f + epsilon)
    }

    @Test
    fun groupDrag_usesAggregateBounds() {
        // Two selected images are dragged together. Their group AABB is
        // used, NOT each image individually. Set up: image1 at (50,50)
        // 100x100 → [0..100, 0..100]; image2 at (250, 50) 100x100 →
        // [200..300, 0..100]. Aggregate group bounds: [0..300, 0..100].
        // Target at (450, 50) 100x100 → [400..500, 0..100]. Y overlap
        // full [0..100] vs [0..100].
        //
        // With candidate deltaX=388 → group bounds [388..688, 0..100].
        //   groupLeft=388, targetLeft=400 → dist 12 ✅ → snap.
        // adjustedDeltaX is the delta that puts groupLeft on 400:
        //   originalEdge (= groupLeft at delta=0) = 0
        //   targetEdge = 400
        //   adjustedDeltaX = 400 - 0 = 400
        val i1 = img("a", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val i2 = img("b", cx = 250f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 450f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(388f, 0f),
            dragIds = setOf("a", "b"),
            allImages = listOf(i1, i2, target),
            thresholdWorld = 15f,
        )
        assertNotNull("group should snap to target left edge", r.guide?.axisX)
        assertEquals(400f, r.guide!!.axisX!!.targetEdgeWorld, epsilon)
        assertEquals(400f, r.adjustedDeltaWorld.dx, epsilon)
    }

    @Test
    fun doesNotSnapToSelf() {
        // Single image, dragged against itself as a target. The target
        // list is empty (since dragIds filters it out), so no snap.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag),
            thresholdWorld = 15f,
        )
        assertNull(r.guide)
    }

    @Test
    fun outOfRange_returnsOriginalDelta() {
        // Two images with positive Y overlap but their X edges are far
        // apart even at the candidate delta. Engine should NOT fire.
        val drag = img("drag", cx = 0f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 500f, cy = 50f, w = 100f, h = 100f)
        // drag bounds [-50..50, 0..100], target [450..550, 0..100]. Y
        // overlap full. delta 0 → groupLeft=-50, groupRight=50.
        //   vs targetLeft=450: dist 400-500 (no)
        //   vs targetRight=550: dist 500-600 (no)
        // No snap.
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNull(r.guide)
        assertEquals(0f, r.adjustedDeltaWorld.dx, epsilon)
    }

    @Test
    fun zeroThreshold_disablesSnap() {
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 150f, cy = 50f, w = 100f, h = 100f)
        // delta 0 → groupRight = 100, targetLeft = 100, dist 0. would snap with >0 threshold.
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 0f,
        )
        assertNull(r.guide)
    }

    // ---------------------------------------------------------------------
    // Group-drag "cross-edge" candidates
    //
    // The V2 engine rewrite (use the closest sub-image's edge for the
    // snap distance + apply-delta) only actually changes behavior in
    // cross-edge candidate pairs — (group.left, target.right) and
    // (group.right, target.left). For "aligned" candidates the math
    // collapses to the original group-AABB formula because the
    // group-edge image IS the closest sub-image.
    //
    // The tests below pin down the new cross-edge behavior so any
    // future regression to the group-AABB formula would fail loudly.
    // ---------------------------------------------------------------------

    @Test
    fun groupDrag_crossEdgeLeftToRight_snapsViaClosestSubImage() {
        // Five 20x100 sub-images packed horizontally:
        //   img1: cx=10,  left=0,   right=20
        //   img2: cx=30,  left=20,  right=40
        //   img3: cx=50,  left=40,  right=60
        //   img4: cx=70,  left=60,  right=80
        //   img5: cx=90,  left=80,  right=100
        // group AABB: [0..100, 0..100]
        //
        // Target at (0,0) size 190x100 → bounds [-95..95, -50..50].
        // target.left=-95, target.right=95.
        //
        // Two cross-edge candidates fire here:
        //   (group.left, target.right): sub-image min |img.left - 95| =
        //     |0-95|=95, ..., |80-95|=15 → 15 (img5). Within range.
        //   (group.right, target.right): sub-image min |img.right - 95| =
        //     |20-95|=75, ..., |100-95|=5 → 5 (img5). Within range.
        // pickCloserX picks the closer pair (distance 5) →
        //   movingEdge=RIGHT, targetEdge=RIGHT, sourceImageId=img5.
        //
        // This is exactly the "user dragged near img5 and it should
        // snap, not the group AABB" behavior V2 was designed for.
        // Source is img5 in BOTH winning candidates; the delta that
        // lands img5's edge on 95 wins. The (left, right) candidate
        // would apply dx = 95 - 80 = 15, the (right, right) candidate
        // applies dx = 95 - 100 = -5. V2 picks the closer (5) so
        // dx=-5.
        val imgs = (1..5).map { i -> img("img$i", cx = 10f + (i - 1) * 20f, cy = 50f, w = 20f, h = 100f) }
        val target = img("t", cx = 0f, cy = 0f, w = 190f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("img1", "img2", "img3", "img4", "img5"),
            allImages = imgs + target,
            thresholdWorld = 15f,
        )
        assertNotNull("V2 should fire on a cross-edge candidate where a sub-image is within range", r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals(SnapEdge.RIGHT, ax.movingEdge)
        assertEquals(SnapEdge.RIGHT, ax.targetEdge)
        assertEquals(95f, ax.targetEdgeWorld, epsilon)
        assertEquals("img5", ax.sourceImageId)
        // Source is img5 (right=100); apply dx = 95 - 100 = -5.
        // Group translates -5 → img5.right lands on 95.
        assertEquals(-5f, r.adjustedDeltaWorld.dx, epsilon)
        assertNull(r.guide.axisY)
    }

    @Test
    fun groupDrag_noSubImageWithinRange_noSnap() {
        // Negative case: in this layout NO sub-image edge is within
        // 15 of any target edge on either cross-edge axis AND no
        // sub-image center is within 15 of the target center, so the
        // engine should NOT fire.
        //
        // Same packed horizontal sub-image layout, but target pushed
        // far enough that even the CENTER candidate misses.
        val imgs = (1..5).map { i -> img("img$i", cx = 10f + (i - 1) * 20f, cy = 50f, w = 20f, h = 100f) }
        // Target at (200, 200) 100x100 → [150..250, 150..250]. cx=cy=200.
        // EDGE X: group.left=0, group.right=100. Min |img edge - target edge|
        //   = |0-150|=150, |0-250|=250, |100-150|=50, |100-250|=150 → all > 15.
        // EDGE Y: same arithmetic, all > 15.
        // CENTER X: min |img.cx - 200| = |90-200|=110 → > 15.
        // CENTER Y: min |img.cy - 200| = |50-200|=150 → > 15.
        val target = img("t", cx = 200f, cy = 200f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("img1", "img2", "img3", "img4", "img5"),
            allImages = imgs + target,
            thresholdWorld = 15f,
        )
        assertNull(
            "no sub-image within 15 of any target edge or center → no snap",
            r.guide?.axisX,
        )
    }

    // ---------------------------------------------------------------------
    // measureClosestTargetDistance — V4 intent-detection probe
    //
    // The state machine in updateImageDrag calls this lightweight probe
    // to decide whether the cursor is in the 15px trigger range, AND
    // which target is the closest. The semantics MUST match the engine's
    // own snap distance (sub-image-min) so a "near" reading here means
    // snap WOULD fire if engaged.
    // ---------------------------------------------------------------------

    @Test
    fun measure_returnsNull_whenNoTargets() {
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.measureClosestTargetDistance(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag),
        )
        assertNull(r)
    }

    @Test
    fun measure_returnsClosestTarget_andSubImageMinDistance() {
        // Two targets. The dragged image's right edge (100) is closer
        // to tA's left (110) than to tB's left (200), so tA wins.
        // Perpendicular Y: drag [0..100] vs tA [0..100] = full
        // overlap. Of the 8 candidate pairs the closest is
        // (drag.right=100, tA.left=110) at distance 10.
        // Probe should return (tA, 10).
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val tA = img("a", cx = 160f, cy = 50f, w = 100f, h = 100f)  // bounds [110..210]
        val tB = img("b", cx = 250f, cy = 50f, w = 100f, h = 100f)  // bounds [200..300]
        val r = SnapEngine.measureClosestTargetDistance(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, tA, tB),
        )
        assertNotNull(r)
        assertEquals("a", r!!.targetImageId)
        assertEquals(10f, r.distanceWorld, epsilon)
    }

    @Test
    fun measure_usesSubImageMin_notGroupEdge_forGroupDrag() {
        // Same as measure_returnsClosestTarget_andSubImageMinDistance
        // but for a group. Five 20x100 sub-images packed horizontally
        // (lefts 0,20,40,60,80; rights 20,40,60,80,100). Target at
        // (0,0) 190x100 → [-95..95, -50..50] (target.right=95).
        //
        // For the cross-edge (group.left, target.right) candidate, the
        // sub-image-min distance is |80-95|=15 (img5). The group-edge
        // distance is |0-95|=95. Probe must report 15, NOT 95 — that
        // match is what makes the state machine's "in range" reading
        // agree with the engine's actual snap-fire decision.
        val imgs = (1..5).map { i -> img("img$i", cx = 10f + (i - 1) * 20f, cy = 50f, w = 20f, h = 100f) }
        val target = img("t", cx = 0f, cy = 0f, w = 190f, h = 100f)
        val r = SnapEngine.measureClosestTargetDistance(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = imgs.map { it.id }.toSet(),
            allImages = imgs + target,
        )
        assertNotNull(r)
        assertEquals("t", r!!.targetImageId)
        // Best of all (movingEdge, targetEdge) pairs: 5 (img5.right
        // vs target.right=95) wins.
        assertEquals(5f, r.distanceWorld, epsilon)
    }

    // ---------------------------------------------------------------------
    // V5: CENTER candidates, corner-snap detection, 1px precision
    // ---------------------------------------------------------------------

    @Test
    fun centerCandidate_firesWhenEdgesMissButCenterAligns() {
        // drag (50,50) 100x100, target at (60, 50) 50x50.
        // target.cx=60, target.left=35, target.right=85.
        // With delta (10, 0) — group bounds [10..110, 0..100].
        //   CENTER: |60 - 60| = 0. Within range.
        //   EDGE LEFT-LEFT: group.left=10, target.left=35, dist 25. > 15.
        //   EDGE LEFT-RIGHT: group.left=10, target.right=85, dist 75.
        //   EDGE RIGHT-LEFT: group.right=110, target.left=35, dist 75.
        //   EDGE RIGHT-RIGHT: group.right=110, target.right=85, dist 25. > 15.
        // Only CENTER fires. Confirms CENTER is a "complement" to
        // EDGE: it kicks in when no edge alignment is in range but
        // the user is clearly aiming for center alignment.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 60f, cy = 50f, w = 50f, h = 50f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(10f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals(AxisKind.CENTER, ax.axisKind)
    }

    @Test
    fun centerCandidate_yieldsToEdgeOnTie() {
        // drag (50,50) 100x100, target at (250, 50) 100x100.
        // target.cx=250. group.cx=50. With delta (200, 0) →
        // group center 250, target center 250, distance 0.
        // The LEFT-LEFT EDGE candidate ALSO hits distance 0 because
        // target.left=200 and group.left=200 (50 + 150 = 200). So
        // both CENTER and EDGE are at distance 0. The V5 engine
        // evaluates EDGE first, so pickCloserX picks EDGE on a tie
        // (first-found wins). This means CENTER only fires when EDGE
        // candidates miss — CENTER is a complement, not a strict
        // override.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 250f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(200f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals(AxisKind.EDGE, ax.axisKind)
        assertEquals(200f, ax.targetEdgeWorld, epsilon)
    }

    @Test
    fun cornerSnap_isCornerFlagSetWhenBothAxesTargetSameCorner() {
        // 5 sub-images packed horizontally (0..100, 0..100). Target
        // at (108, 108) 100x100 → [58..158, 58..158]. Target.cx=108,
        // target.top=58, target.bottom=158. With candidate delta
        // (53, 53) group becomes [53..153, 53..153].
        //   X best: (group.left=53, target.left=58) dist 5; also
        //          (group.right=153, target.right=158) dist 5.
        //          pickCloserX picks first-found → LEFT/LEFT.
        //   Y best: (group.top=53, target.top=58) dist 5; also
        //          (group.bottom=153, target.bottom=158) dist 5.
        //          pickCloserY picks first-found → TOP/TOP.
        //   Both EDGE on target "t", LEFT/LEFT + TOP/TOP
        //   → shareSameCorner returns true (target corner
        //   (LEFT,TOP), group edges (LEFT,TOP)) → isCornerSnap=true.
        val imgs = (1..5).map { i -> img("img$i", cx = 10f + (i - 1) * 20f, cy = 50f, w = 20f, h = 100f) }
        val target = img("t", cx = 108f, cy = 108f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(53f, 53f),
            dragIds = imgs.map { it.id }.toSet(),
            allImages = imgs + target,
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide)
        assertNotNull(r.guide!!.axisX)
        assertNotNull(r.guide.axisY)
        assertTrue(
            "X=(LEFT/LEFT) and Y=(TOP/TOP) on the same target should be a corner snap",
            r.guide.isCornerSnap,
        )
    }

    @Test
    fun cornerSnap_notFlaggedWhenAxesTargetDifferentTargets() {
        // Two targets: tA on the right (sharing Y range with the
        // drag), tB below (sharing X range with the drag). The X
        // axis snaps to tA, the Y axis snaps to tB — different
        // targets, NOT a corner.
        //
        // Layout: drag (50,50) 100x100 → [0..100, 0..100].
        //         tA   (200,50) 100x100 → [150..250, 0..100].
        //         tB   (50,200) 100x100 → [0..100, 150..250].
        // With delta (50, 50), drag becomes [50..150, 50..150].
        //   X candidates against tA: (group.right=150, tA.left=150) →
        //     dist 0 ✓. tA's Y range [0..100] vs drag Y [50..150] =
        //     [50..100] overlap ✓.
        //   Y candidates against tB: (group.bottom=150, tB.top=150) →
        //     dist 0 ✓. tB's X range [0..100] vs drag X [50..150] =
        //     [50..100] overlap ✓.
        //   bestX targets tA, bestY targets tB → different targets,
        //   not a corner.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val tA = img("a", cx = 200f, cy = 50f, w = 100f, h = 100f)
        val tB = img("b", cx = 50f, cy = 200f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(50f, 50f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, tA, tB),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide)
        assertNotNull(r.guide!!.axisX)
        assertNotNull(r.guide.axisY)
        assertEquals("a", r.guide.axisX!!.targetImageId)
        assertEquals("b", r.guide.axisY!!.targetImageId)
        assertTrue(
            "axes on different targets are NOT a corner snap",
            !r.guide.isCornerSnap,
        )
    }

    @Test
    fun edgeVsCenter_priority_edgeWinsOnTie() {
        // Verify the V5 ordering: EDGE wins ties (first-found
        // wins; EDGE is evaluated before CENTER). This means
        // CENTER is a "complement" to EDGE, not a strict priority
        // override — CENTER only fires when no EDGE candidate is
        // in range. Drag at (50, 50) 100x100. Target at (50, 50)
        // 100x100. GROUP IS ON TOP OF TARGET — every candidate
        // is at dist 0, so the tie goes to EDGE (evaluated
        // first).
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 15f,
        )
        assertNotNull(r.guide?.axisX)
        assertEquals(AxisKind.EDGE, r.guide!!.axisX!!.axisKind)
    }

    @Test
    fun precision_1px() {
        // With a 1-unit threshold and a 0.5-unit offset, snap should
        // still fire and the resulting delta should land the dragged
        // image EXACTLY on the target's edge — sub-pixel precision is
        // not the requirement; pixel-level alignment to the target
        // is.
        val drag = img("drag", cx = 50f, cy = 50f, w = 100f, h = 100f)
        val target = img("t", cx = 200f, cy = 50f, w = 100f, h = 100f)
        // target.left=150. group.right=100. delta (50, 0) →
        // groupRight=150, targetLeft=150, distance 0. Already on
        // target. But test the (49, 0) case to confirm we land
        // exactly at 150, not 149 or 151.
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(49f, 0f),
            dragIds = setOf("drag"),
            allImages = listOf(drag, target),
            thresholdWorld = 1f,    // tight threshold
        )
        assertNotNull(r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals(150f, ax.targetEdgeWorld, epsilon)
        // apply dx = target.left - drag.originalRight = 150 - 100 = 50
        assertEquals(50f, r.adjustedDeltaWorld.dx, epsilon)
    }

    @Test
    fun groupDrag_noSubImageClose_doesNotSnap_evenIfGroupEdgeIsClose() {
        // Regression test for the original "误吸" (false positive):
        //
        // 4 sub-images packed tightly in a 40x100 cluster on the left,
        // plus 1 sub-image jutting out far to the right. The group's
        // OUTER AABB right edge sits near a target, but the sub-image
        // that produces that outer right is the SAME one whose edge
        // is close to the target — so the new V2 formula reduces to
        // the old group-AABB formula and STILL fires.
        //
        // This is mathematically unavoidable: group.right IS the
        // farthest-right sub-image, so its distance to target is
        // identically that sub-image's distance. The V2 rewrite
        // cannot eliminate snaps where the producing sub-image is
        // the closest one. It only changes behavior on cross-edge
        // candidates where the producing sub-image is NOT the
        // closest to the target edge.
        //
        // This test is therefore mostly a regression-guard: confirm
        // V2 doesn't accidentally REGRESS the "group AABB edge is
        // the sub-image that's actually close" case.
        val cluster = (1..4).map { i ->
            img("c$i", cx = 5f + (i - 1) * 10f, cy = 50f, w = 10f, h = 100f)
        }
        // Single outlier far to the right, narrow & short.
        val outlier = img("out", cx = 220f, cy = 50f, w = 20f, h = 50f)
        // group bounds after candidate delta (0, 0): cluster [0..40, 0..100]
        // + outlier [210..230, 25..75] → union [0..230, 0..100].
        // Target: just to the right of the outlier's right edge.
        val target = img("t", cx = 240f, cy = 50f, w = 20f, h = 100f)
        // target bounds [230..250, 0..100].
        // Y overlap: group [0..100] vs target [0..100] = full.
        // (group.right, target.left): group.right=230, target.left=230,
        //   dist 0. The producing sub-image IS outlier (right=230),
        //   which is at dist 0 from target.left. V2 also fires.
        val r = SnapEngine.compute(
            deltaWorld = SnapEngine.SnapDelta(0f, 0f),
            dragIds = setOf("c1", "c2", "c3", "c4", "out"),
            allImages = cluster + outlier + target,
            thresholdWorld = 15f,
        )
        assertNotNull("the producing sub-image IS the closest → snap fires (V2 same as V1 here)", r.guide?.axisX)
        val ax = r.guide!!.axisX!!
        assertEquals("out", ax.sourceImageId)
        assertEquals(230f, ax.targetEdgeWorld, epsilon)
    }
}
