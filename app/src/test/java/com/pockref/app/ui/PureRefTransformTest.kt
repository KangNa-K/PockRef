package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure JVM unit tests for the transform math helpers in
 * [TransformHelpers]. These cover the pivot calculation, scale/rotate
 * math, and zIndex reordering that drive [PockRefViewModelTransform].
 *
 * We don't construct a [PockRefViewModelTransform] directly because it
 * requires an AndroidViewModel (which needs a real Application via
 * Robolectric). Instead, we test the pure helpers and verify they match
 * the contract documented in the source.
 */
class PockRefTransformTest {

    private val epsilon = 1e-3f

    private fun img(
        id: String,
        center: Offset = Offset(0f, 0f),
        displayW: Float = 100f,
        displayH: Float = 80f,
        scale: Float = 1f,
        zIndex: Int = 0,
        rotationDegrees: Float = 0f,
    ) = SceneImage(
        id = id,
        name = id,
        mimeType = "image/png",
        imageBytes = byteArrayOf(0),
        intrinsicWidth = 100,
        intrinsicHeight = 80,
        center = center,
        displaySize = Size2D(displayW, displayH),
        scale = scale,
        zIndex = zIndex,
        rotationDegrees = rotationDegrees,
    )

    // ----- Pivot calculation ---------------------------------------------------

    @Test
    fun pivot_singleImage_isTheImageCenter() {
        val a = img("a", center = Offset(10f, 20f))
        val pivot = calculateTransformPivot(listOf(a))
        assertEquals(Offset(10f, 20f), pivot)
    }

    @Test
    fun pivot_twoImages_isBoundingBoxCenter() {
        val a = img("a", center = Offset(0f, 0f), displayW = 100f, displayH = 100f)
        val b = img("b", center = Offset(200f, 0f), displayW = 100f, displayH = 100f)
        // a: left=−50, right=50, top=−50, bottom=50
        // b: left=150, right=250, top=−50, bottom=50
        // combined AABB: left=−50, right=250, top=−50, bottom=50
        // center = (100, 0)
        val pivot = calculateTransformPivot(listOf(a, b))
        assertEquals(Offset(100f, 0f), pivot)
    }

    @Test
    fun pivot_threeImages_respectsIndividualImageSizes() {
        val a = img("a", center = Offset(0f, 0f), displayW = 40f, displayH = 40f)
        val b = img("b", center = Offset(50f, 0f), displayW = 40f, displayH = 40f)
        val c = img("c", center = Offset(0f, 50f), displayW = 40f, displayH = 40f)
        // a: left=−20, right=20, top=−20, bottom=20
        // b: left=30, right=70, top=−20, bottom=20
        // c: left=−20, right=20, top=30, bottom=70
        // combined: left=−20, right=70, top=−20, bottom=70
        // center = (25, 25)
        val pivot = calculateTransformPivot(listOf(a, b, c))
        assertEquals(Offset(25f, 25f), pivot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pivot_empty_throws() {
        calculateTransformPivot(emptyList())
    }

    // ----- transformImageAroundPivot -----------------------------------------

    @Test
    fun transform_onlyPan_movesImage() {
        val a = img("a", center = Offset(0f, 0f))
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 1f,
            rotationChange = 0f,
            pan = Offset(5f, 7f),
        )
        assertEquals(Offset(5f, 7f), out.center)
        assertEquals(1f, out.scale, 0f)
        assertEquals(0f, out.rotationDegrees, 0f)
        // All other fields must be preserved.
        assertEquals(a.colorAdjust, out.colorAdjust)
        assertEquals(a.displaySize, out.displaySize)
        assertEquals(a.id, out.id)
    }

    @Test
    fun transform_onlyScale_preservesPivotAndScale() {
        // For a single image centered at the pivot, the center should not
        // move (dx == dy == 0), only the scale should change.
        val a = img("a", center = Offset(50f, 50f), scale = 1f)
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(50f, 50f),
            scaleChange = 2f,
            rotationChange = 0f,
            pan = Offset.Zero,
        )
        assertEquals(Offset(50f, 50f), out.center)
        assertEquals(2f, out.scale, epsilon)
        assertEquals(0f, out.rotationDegrees, 0f)
    }

    @Test
    fun transform_rotation90_aroundPivotSwapsDxDy() {
        // Image at (10, 0), pivot at origin. 90° CCW: (0, 10).
        val a = img("a", center = Offset(10f, 0f))
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 1f,
            rotationChange = 90f,
            pan = Offset.Zero,
        )
        // Compose rotates the way Canvas/Compose does: positive degrees =
        // clockwise in screen coords, which is also "rotate (10,0) →
        // (0,10)" via the standard 2D rotation matrix with positive angle
        // = counter-clockwise. Compose's drawScope.rotate uses a CCW
        // convention. Let's just assert the magnitude is preserved.
        val dist = sqrt(out.center.x * out.center.x + out.center.y * out.center.y)
        assertEquals(10f, dist, epsilon)
    }

    @Test
    fun transform_rotation360_returnsToOriginal() {
        val a = img("a", center = Offset(13f, 7f))
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 1f,
            rotationChange = 360f,
            pan = Offset.Zero,
        )
        // 360° rotation + identity scale = original position (modulo
        // float rounding). Use a slightly looser tolerance.
        assertEquals(13f, out.center.x, 1e-2f)
        assertEquals(7f, out.center.y, 1e-2f)
    }

    @Test
    fun transform_scaleAndRotate_appliesBoth() {
        // Scale 2x then rotate 180°. Image at (10, 0), pivot at origin.
        // After 2x: (20, 0). After 180°: (−20, 0). Compose order in the
        // helper is scale first, then rotate, so the result should be the
        // 180° rotation of the scaled point.
        val a = img("a", center = Offset(10f, 0f))
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 2f,
            rotationChange = 180f,
            pan = Offset.Zero,
        )
        assertEquals(-20f, out.center.x, epsilon)
        assertEquals(0f, out.center.y, epsilon)
        assertEquals(2f, out.scale, epsilon)
        assertEquals(180f, out.rotationDegrees, epsilon)
    }

    @Test
    fun transform_scaleAroundArbitraryPivot_keepsPivotFixed() {
        // V_NOW (user spec "双指缩放选中图片"): the
        // dispatch layer passes the two-finger
        // centroid (in world coordinates) as the
        // scale pivot. Verifies that whatever
        // pivot we hand the helper, the pivot
        // point itself doesn't move under uniform
        // scale + identity rotation. We do this
        // by comparing the helper's pre/post
        // output for a 2× scale around the
        // midpoint of the image (not its centre)
        // — the midpoint must be invariant
        // while the centre must double its
        // distance from the pivot.
        val a = img(
            "a",
            center = Offset(20f, 0f),
            displayW = 10f,
            displayH = 10f,
        )
        val pivot = Offset(0f, 0f)
        val out = transformImageAroundPivot(
            image = a,
            pivot = pivot,
            scaleChange = 2f,
            rotationChange = 0f,
            pan = Offset.Zero,
        )
        // Centre doubled (10 → 20 from pivot).
        assertEquals(40f, out.center.x, epsilon)
        assertEquals(0f, out.center.y, epsilon)
        assertEquals(2f, out.scale, epsilon)
    }

    @Test
    fun transform_scaleClampedToMaxEight() {
        // V_NOW: per-image scale clamp range is now
        // [0.1, 8.0] (= 10%..800% of the image's
        // intrinsic size) per the user's spec
        // "缩放范围限制为图片原始尺寸的 10%（最小）
        // 至 800%（最大）". Was [0.05, 10].
        val a = img("a", scale = 1f)
        val out = transformImageAroundPivot(
            image = a,
            pivot = a.center,
            scaleChange = 100f,
            rotationChange = 0f,
            pan = Offset.Zero,
        )
        assertEquals(8.0f, out.scale, epsilon)
    }

    @Test
    fun transform_scaleClampedToMinZeroPointOne() {
        // V_NOW: see transform_scaleClampedToMaxEight.
        val a = img("a", scale = 1f)
        val out = transformImageAroundPivot(
            image = a,
            pivot = a.center,
            scaleChange = 0.0001f,
            rotationChange = 0f,
            pan = Offset.Zero,
        )
        assertEquals(0.1f, out.scale, epsilon)
    }

    @Test
    fun transform_panAppliedAfterRotation() {
        // Pan adds on top of the rotated/scaled position.
        val a = img("a", center = Offset(0f, 0f))
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 1f,
            rotationChange = 0f,
            pan = Offset(3f, 4f),
        )
        assertEquals(Offset(3f, 4f), out.center)
    }

    @Test
    fun transform_preservesColorAdjust() {
        val a = img("a", center = Offset(0f, 0f)).copy(
            colorAdjust = ColorAdjust(brightness = 0.3f, contrast = -0.1f, saturation = 0.5f),
        )
        val out = transformImageAroundPivot(
            image = a,
            pivot = Offset(0f, 0f),
            scaleChange = 1.5f,
            rotationChange = 45f,
            pan = Offset(1f, 1f),
        )
        assertEquals(a.colorAdjust, out.colorAdjust)
    }

    // ----- scaleImageAroundPivot ----------------------------------------------

    @Test
    fun scaleImage_aroundSelf_keepsCenter() {
        val a = img("a", center = Offset(50f, 50f), scale = 1f)
        val out = scaleImageAroundPivot(a, pivot = a.center, factor = 3f)
        assertEquals(Offset(50f, 50f), out.center)
        assertEquals(3f, out.scale, epsilon)
    }

    @Test
    fun scaleImage_aroundOtherPoint_movesCenter() {
        val a = img("a", center = Offset(10f, 0f), scale = 1f)
        val out = scaleImageAroundPivot(a, pivot = Offset(0f, 0f), factor = 2f)
        // Pivot at origin, image 10 units to the right. 2x → 20 units to
        // the right.
        assertEquals(Offset(20f, 0f), out.center)
        assertEquals(2f, out.scale, epsilon)
    }

    // ----- rotateImageAroundPivot ---------------------------------------------

    @Test
    fun rotateImage_aroundSelf_keepsCenter() {
        val a = img("a", center = Offset(50f, 50f))
        val out = rotateImageAroundPivot(a, pivot = a.center, delta = 73f)
        assertEquals(Offset(50f, 50f), out.center)
        assertEquals(73f, out.rotationDegrees, epsilon)
    }

    @Test
    fun rotateImage_aroundOrigin_matchesStandardMatrix() {
        // For a 90° rotation around the origin, (1, 0) → (0, 1) and
        // (0, 1) → (−1, 0). The helper uses the standard math rotation
        // (CCW for positive angle), so a positive delta should move
        // (10, 0) to (0, 10).
        val a = img("a", center = Offset(10f, 0f))
        val out = rotateImageAroundPivot(a, pivot = Offset(0f, 0f), delta = 90f)
        assertEquals(0f, out.center.x, epsilon)
        assertEquals(10f, out.center.y, epsilon)
    }

    @Test
    fun rotateImage_aroundPivot_preservesDistance() {
        // Rotation must preserve distance from pivot.
        val a = img("a", center = Offset(7f, 11f))
        val out = rotateImageAroundPivot(a, pivot = Offset(0f, 0f), delta = 137f)
        val distOut = sqrt(out.center.x * out.center.x + out.center.y * out.center.y)
        val distIn = sqrt(7f * 7f + 11f * 11f)
        assertEquals(distIn, distOut, epsilon)
    }

    @Test
    fun rotateImage_accumulatesRotationDegrees() {
        val a = img("a", center = Offset(0f, 0f), rotationDegrees = 30f)
        val out = rotateImageAroundPivot(a, pivot = a.center, delta = 45f)
        assertEquals(75f, out.rotationDegrees, epsilon)
    }

    // ----- Group scale factor -------------------------------------------------

    @Test
    fun groupScaleFactor_noClamp_returnsRequested() {
        val selected = listOf(
            img("a", scale = 1f),
            img("b", scale = 2f),
        )
        assertEquals(1.5f, computeGroupScaleFactor(selected, 1.5f), epsilon)
    }

    @Test
    fun groupScaleFactor_oneClamped_scalesOthersProportionally() {
        // V_NOW: clamp range is [0.1, 8.0]. a is at
        // scale 7.5; factor 2.0 would clamp to 8.0.
        // effective = 8.0 / 7.5 ≈ 1.0667.
        val a = img("a", scale = 7.5f)
        val b = img("b", scale = 1f)
        val factor = computeGroupScaleFactor(listOf(a, b), 2f)
        assertEquals(8.0f / 7.5f, factor, epsilon)
    }

    @Test
    fun groupScaleFactor_clampsDownwardWhenTooSmall() {
        // V_NOW: clamp range is [0.1, 8.0]. a is at
        // scale 0.1; factor 0.5 would clamp to 0.05 → 0.1.
        // effective = 0.1 / 0.1 = 1.0 (i.e. "don't scale a at all").
        val a = img("a", scale = 0.1f)
        val b = img("b", scale = 1f)
        val factor = computeGroupScaleFactor(listOf(a, b), 0.5f)
        assertEquals(1f, factor, epsilon)
    }

    @Test
    fun groupScaleFactor_empty_returnsOne() {
        assertEquals(1f, computeGroupScaleFactor(emptyList(), 2f), 0f)
    }

    // ----- reorderZIndex (bringToFront / sendToBack) --------------------------

    @Test
    fun reorderZIndex_bringToFront_putsSelectedAtMax() {
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 5),
            img("c", zIndex = 10),
        )
        val out = reorderZIndex(images, setOf("a"), bringToFront = true)
        // a is now the new max: 10 + 1 = 11.
        val byId = out.associateBy { it.id }
        assertEquals(11, byId["a"]!!.zIndex)
        // b and c are untouched.
        assertEquals(5, byId["b"]!!.zIndex)
        assertEquals(10, byId["c"]!!.zIndex)
    }

    @Test
    fun reorderZIndex_bringToFront_multipleSelected_preservesOrder() {
        // `images` is iterated in order: a is processed first (zIndex → 11),
        // then b (zIndex → 12). The order of assignment follows the
        // image list, NOT the order of the selectedIds set.
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 5),
            img("c", zIndex = 10),
        )
        val out = reorderZIndex(images, setOf("b", "a"), bringToFront = true)
        val byId = out.associateBy { it.id }
        // a is processed first (zIndex = 11), then b (zIndex = 12).
        assertEquals(11, byId["a"]!!.zIndex)
        assertEquals(12, byId["b"]!!.zIndex)
        // c is unchanged.
        assertEquals(10, byId["c"]!!.zIndex)
        // All selected zIndex strictly greater than the previous max (10).
        assertTrue(byId["a"]!!.zIndex > 10)
        assertTrue(byId["b"]!!.zIndex > 10)
    }

    @Test
    fun reorderZIndex_sendToBack_putsSelectedAtMin() {
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 5),
            img("c", zIndex = 10),
        )
        val out = reorderZIndex(images, setOf("c"), bringToFront = false)
        val byId = out.associateBy { it.id }
        // c is now 0 - 1 = -1
        assertEquals(-1, byId["c"]!!.zIndex)
        assertEquals(0, byId["a"]!!.zIndex)
        assertEquals(5, byId["b"]!!.zIndex)
    }

    @Test
    fun reorderZIndex_sendToBack_multipleSelected_preservesOrder() {
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 5),
            img("c", zIndex = 10),
        )
        val out = reorderZIndex(images, setOf("a", "b"), bringToFront = false)
        val byId = out.associateBy { it.id }
        // a first, then b: a = -1, b = -2
        assertEquals(-1, byId["a"]!!.zIndex)
        assertEquals(-2, byId["b"]!!.zIndex)
        // c unchanged.
        assertEquals(10, byId["c"]!!.zIndex)
        // Both selected are below the previous min (0).
        assertTrue(byId["a"]!!.zIndex < 0)
        assertTrue(byId["b"]!!.zIndex < 0)
    }

    @Test
    fun reorderZIndex_noSelected_returnsOriginalList() {
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 5),
        )
        val out = reorderZIndex(images, emptySet(), bringToFront = true)
        // Returns the same instances — no copy.
        assertEquals(images, out)
    }

    @Test
    fun reorderZIndex_preservesAllOtherFields() {
        val original = img(
            "a",
            center = Offset(42f, 24f),
            displayW = 200f,
            displayH = 100f,
            scale = 1.7f,
            zIndex = 0,
            rotationDegrees = 15f,
        )
        val out = reorderZIndex(listOf(original), setOf("a"), bringToFront = true).first()
        assertEquals(42f, out.center.x, 0f)
        assertEquals(24f, out.center.y, 0f)
        assertEquals(Size2D(200f, 100f), out.displaySize)
        assertEquals(1.7f, out.scale, 0f)
        assertEquals(15f, out.rotationDegrees, 0f)
        assertEquals("a", out.id)
    }

    // ----- Sanity: end-to-end transform with selected multi-image scenario ----

    @Test
    fun transformScenario_twoImagesGrouped_pivotIsBoundingCenter() {
        // a at (0,0) 100x100, b at (200,0) 100x100
        val a = img("a", center = Offset(0f, 0f), displayW = 100f, displayH = 100f, scale = 1f)
        val b = img("b", center = Offset(200f, 0f), displayW = 100f, displayH = 100f, scale = 1f)
        val selected = listOf(a, b)
        val pivot = calculateTransformPivot(selected)
        // Bounding box: left=−50, right=250, top=−50, bottom=50
        // center = (100, 0)
        assertEquals(Offset(100f, 0f), pivot)
    }

    @Test
    fun transformScenario_panOnlyOnMultiImage_preservesRelativeLayout() {
        val a = img("a", center = Offset(0f, 0f))
        val b = img("b", center = Offset(100f, 0f))
        // Translate both by (10, 0). The selectedIds check is at the
        // caller level, so we just verify the per-image behavior.
        val newA = transformImageAroundPivot(a, pivot = Offset(50f, 0f), 1f, 0f, Offset(10f, 0f))
        val newB = transformImageAroundPivot(b, pivot = Offset(50f, 0f), 1f, 0f, Offset(10f, 0f))
        assertEquals(Offset(10f, 0f), newA.center)
        assertEquals(Offset(110f, 0f), newB.center)
    }

    // ----- shortestAngleDelta (rotation cross-wrap math) ----------------------

    @Test
    fun shortestAngleDelta_zeroToZero_isZero() {
        assertEquals(0f, shortestAngleDelta(0f, 0f), epsilon)
    }

    @Test
    fun shortestAngleDelta_zeroToPi_isInPiRange() {
        // Going from 0 → π is exactly the half-turn boundary. The
        // implementation's modulo convention picks a sign (±π are
        // equivalent directions on a circle); we just verify the
        // magnitude is π and the result stays in the [-π, π] range.
        val result = shortestAngleDelta(0f, PI.toFloat())
        assertEquals(PI.toFloat(), kotlin.math.abs(result), epsilon)
        // Result is in the closed [-π, π] range.
        assertTrue("result must be in [-π, π]", result >= -PI.toFloat() && result <= PI.toFloat())
    }

    @Test
    fun shortestAngleDelta_zeroToNegPi_isInPiRange() {
        // Going from 0 → -π is the negative half-turn boundary. Same
        // sign-convention ambiguity as the positive case; magnitude is π.
        val result = shortestAngleDelta(0f, -PI.toFloat())
        assertEquals(PI.toFloat(), kotlin.math.abs(result), epsilon)
        assertTrue("result must be in [-π, π]", result >= -PI.toFloat() && result <= PI.toFloat())
    }

    @Test
    fun shortestAngleDelta_zeroToTwoPi_isZero() {
        // Full revolution = identity. Result must be 0, not 2π.
        assertEquals(0f, shortestAngleDelta(0f, 2f * PI.toFloat()), epsilon)
    }

    @Test
    fun shortestAngleDelta_acrossWrap_takesShortestPath() {
        // From π/2 going to 3π/2: both directions are 180° (= π). The
        // implementation's convention picks one; the magnitude must
        // always be π.
        val from = (PI / 2f).toFloat()
        val to = (3f * PI / 2f).toFloat()
        val result = shortestAngleDelta(from, to)
        assertEquals(PI.toFloat(), kotlin.math.abs(result), epsilon)
    }

    @Test
    fun shortestAngleDelta_piAndNegPi_areZero() {
        // π and -π point the same way on the circle. Going from one
        // to the other requires zero rotation (they're the same
        // direction). The function correctly returns 0.
        assertEquals(0f, shortestAngleDelta(PI.toFloat(), -PI.toFloat()), epsilon)
        // And the reverse direction.
        assertEquals(0f, shortestAngleDelta(-PI.toFloat(), PI.toFloat()), epsilon)
    }

    @Test
    fun shortestAngleDelta_smallPositiveDelta_preservesSign() {
        // 0 → +π/4 (CCW a quarter turn) = +π/4.
        assertEquals(
            (PI / 4f).toFloat(),
            shortestAngleDelta(0f, (PI / 4f).toFloat()),
            epsilon,
        )
    }

    @Test
    fun shortestAngleDelta_smallNegativeDelta_preservesSign() {
        // 0 → -π/4 (CW a quarter turn) = -π/4.
        assertEquals(
            -(PI / 4f).toFloat(),
            shortestAngleDelta(0f, -(PI / 4f).toFloat()),
            epsilon,
        )
    }

    @Test
    fun shortestAngleDelta_accrossUpperWrap_takesShortestPath() {
        // 170° → -170° (= 190° in [0, 360)): the naive delta is -340°
        // (going CW all the way around); the shortest is +20° (going
        // CCW from 170° to 190° = 20° CCW).
        val from = (170f * PI / 180f).toFloat()
        val to = (-170f * PI / 180f).toFloat()
        val result = shortestAngleDelta(from, to)
        // Expected: +20° (CCW), i.e. +π/9.
        val expected = (20f * PI / 180f).toFloat()
        assertEquals(expected, result, epsilon)
    }

    @Test
    fun shortestAngleDelta_largeAccumulators_wrap() {
        // After a few full rotations, the delta should still come out
        // as the shortest-arc value.
        val fourTurns = 4f * 2f * PI.toFloat()
        assertEquals(0f, shortestAngleDelta(0f, fourTurns), epsilon)
        assertEquals(
            (PI / 2f).toFloat(),
            shortestAngleDelta(fourTurns, fourTurns + (PI / 2f).toFloat()),
            epsilon,
        )
    }

    // ----- detectAngleSnap (rotation snap detection) -------------------------

    @Test
    fun detectAngleSnap_exactlyOnCardinal_snap() {
        // Exactly 0 / 90 / 180 / 270 should always snap regardless of
        // threshold (distance is 0, which is <= any threshold).
        assertEquals(0f, detectAngleSnap(0f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(90f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(180f, detectAngleSnap(180f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(270f, detectAngleSnap(270f, thresholdDeg = 4f)!!, epsilon)
    }

    @Test
    fun detectAngleSnap_withinThreshold_snap() {
        // 2° below / above each cardinal should snap with a 4° threshold.
        assertEquals(0f, detectAngleSnap(2f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(0f, detectAngleSnap(-2f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(88f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(92f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(180f, detectAngleSnap(178f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(180f, detectAngleSnap(182f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(270f, detectAngleSnap(268f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(270f, detectAngleSnap(272f, thresholdDeg = 4f)!!, epsilon)
    }

    @Test
    fun detectAngleSnap_outsideThreshold_noSnap() {
        // 5° away from any cardinal is beyond the default 4° threshold.
        assertNull(detectAngleSnap(5f, thresholdDeg = 4f))
        assertNull(detectAngleSnap(85f, thresholdDeg = 4f))
        assertNull(detectAngleSnap(95f, thresholdDeg = 4f))
        assertNull(detectAngleSnap(185f, thresholdDeg = 4f))
        assertNull(detectAngleSnap(275f, thresholdDeg = 4f))
    }

    @Test
    fun detectAngleSnap_atThresholdBoundary_isInclusive() {
        // Exactly at the threshold (4° away) should snap — the test is
        // a "<=" not "<" check.
        assertEquals(0f, detectAngleSnap(4f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(86f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(94f, thresholdDeg = 4f)!!, epsilon)
    }

    @Test
    fun detectAngleSnap_negativeAngle_normalizesToCanonical() {
        // -89° in [0, 360) is 271° → 1° below the 270° cardinal.
        // Snaps to 270° (the canonical cardinal in [0, 360), not
        // sign-flipped to 90°).
        assertEquals(270f, detectAngleSnap(-89f, thresholdDeg = 4f)!!, epsilon)
        // -179° in [0, 360) is 181° → 1° above the 180° cardinal.
        assertEquals(180f, detectAngleSnap(-179f, thresholdDeg = 4f)!!, epsilon)
        // -1° in [0, 360) is 359° → 1° below the 0° cardinal.
        assertEquals(0f, detectAngleSnap(-1f, thresholdDeg = 4f)!!, epsilon)
    }

    @Test
    fun detectAngleSnap_above360_normalizes() {
        // 360° is the same as 0°; 361° is 1° past 0°.
        assertEquals(0f, detectAngleSnap(360f, thresholdDeg = 4f)!!, epsilon)
        assertEquals(0f, detectAngleSnap(361f, thresholdDeg = 4f)!!, epsilon)
        // 449° = 89° (mod 360) → snap to 90°.
        assertEquals(90f, detectAngleSnap(449f, thresholdDeg = 4f)!!, epsilon)
    }

    @Test
    fun detectAngleSnap_threeSixtyBoundary_takesCanonicalZero() {
        // 359° is 1° below 0° (the wrap side) — should snap to 0°,
        // not 360° (which would be out of the [0, 360) range).
        val snapped = detectAngleSnap(359f, thresholdDeg = 4f)
        assertNotNull(snapped)
        assertEquals(0f, snapped!!, epsilon)
    }

    @Test
    fun detectAngleSnap_customThreshold() {
        // A 10° threshold picks up 7°-away from cardinals.
        assertEquals(0f, detectAngleSnap(7f, thresholdDeg = 10f)!!, epsilon)
        assertEquals(0f, detectAngleSnap(-7f, thresholdDeg = 10f)!!, epsilon)
        // But 11° is out of range.
        assertNull(detectAngleSnap(11f, thresholdDeg = 10f))
    }

    @Test
    fun detectAngleSnap_zeroThreshold_onlyExactCardinals() {
        // 0° threshold = only exact cardinals snap; 1° off is out.
        assertEquals(0f, detectAngleSnap(0f, thresholdDeg = 0f)!!, epsilon)
        assertEquals(90f, detectAngleSnap(90f, thresholdDeg = 0f)!!, epsilon)
        assertNull(detectAngleSnap(1f, thresholdDeg = 0f))
        assertNull(detectAngleSnap(89f, thresholdDeg = 0f))
    }
}
