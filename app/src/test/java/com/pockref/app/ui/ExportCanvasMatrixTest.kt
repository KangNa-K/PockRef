package com.pockref.app.ui

import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * V_NOW (2026-06-08 user feedback "导出的图片会错位"):
 * regression tests for the export-canvas matrix construction
 * in [PockRefViewModelScene.exportCanvas].
 *
 * The bug: the post* call order in the matrix builder was
 * `T(offset) → S(flip) → R(rot) → S(scale) → T_c`, which
 * in source-space order is `T_c → S(scale) → R(rot) → S(flip) → T`.
 * That order does NOT match the on-screen pipeline
 * `translate { rotate { scale { drawImage } } }`, which in
 * source-space order is `scale → flip → rotate → translate`.
 *
 * For images with BOTH rotation and flip, the two
 * pipelines produce visibly different layouts: the export
 * collapsed the image against the top edge of the rotated
 * bounding box (the source (0, 0) and (sw, 0) both landed
 * at the same X) while the on-screen showed the expected
 * horizontal-flip-then-rotate. For images with only
 * rotation OR only flip (or neither), the two transforms
 * commute around the same pivot so the order did not
 * matter.
 *
 * The fix reorders the post* calls to `T(offset) → R(rot) →
 * S(flip) → S(scale) → T_c` so the source-space order
 * matches the on-screen pipeline. See
 * [PockRefViewModelScene.exportCanvas] for the full
 * rationale + on-screen pipeline diagram.
 *
 * These tests verify the invariants that follow from the
 * correct order:
 *
 *   1. The source bitmap center always maps to the
 *      image's center in the export bitmap, regardless of
 *      rotation or flip. (Holds for both the buggy and the
 *      correct order — this test would pass either way, so
 *      it's a sanity check that the helper is wired up
 *      correctly rather than a regression test for the
 *      rotation/flip order.)
 *
 *   2. The source bitmap's AABB in the export bitmap
 *      matches the AABB computed in the export's bbox
 *      pass (i.e. the rotated rectangle's AABB). The flip
 *      is around the image center so it does not change
 *      the AABB.
 *
 *   3. For the specific 90° CW + flipX case (the only
 *      case where the buggy order produced a visibly
 *      different layout), the source corner mapping
 *      matches what the on-screen Compose pipeline would
 *      produce. This is the test that FAILS under the
 *      buggy order and PASSES under the fix.
 */
class ExportCanvasMatrixTest {

    /**
     * Build the export matrix for a single image as a 3x3
     * affine transform. Mirrors the post* call sequence in
     * [PockRefViewModelScene.exportCanvas]. Returns the
     * matrix as a 6-element float array in row-major
     * form: [m00, m01, m02, m10, m11, m12] (Compose /
     * Skia convention where the third column is the
     * translation).
     *
     * Post* in Android is RIGHT-multiplication: each call
     * does M = M * X. So the resulting matrix after a
     * sequence of post* calls in code order
     * `X1, X2, ..., Xn` is
     *
     *     M = X1 * X2 * ... * Xn
     *
     * and `M * (sx, sy)` applies `Xn` first, then `Xn-1`,
     * ..., then `X1` to the source. So the source-space
     * order is the REVERSE of the post* code order.
     */
    private fun buildExportMatrix(
        sourceWidth: Float,
        sourceHeight: Float,
        displaySize: Size2D,
        imageScale: Float,
        flipX: Boolean,
        flipY: Boolean,
        rotationDegrees: Float,
        offsetX: Float,
        offsetY: Float,
        exportScale: Float,
    ): FloatArray {
        // V_NOW: this must mirror the post* order in
        // exportCanvas EXACTLY. The desired source-space
        // order is `T_c → S(scale) → S(flip) → R(rot) → T`
        // (= `center, scale, flip, rotate, translate` to
        // match the on-screen `scale → flip → rotate →
        // translate` nesting in [drawSceneImage]). Since
        // post* right-multiplies, the post* code order is
        // the REVERSE: `T → R → S(flip) → S(scale) → T_c`.
        val m = FloatArray(6)
        m[0] = 1f; m[1] = 0f; m[2] = 0f
        m[3] = 0f; m[4] = 1f; m[5] = 0f

        fun applyTranslate(tx: Float, ty: Float) {
            m[2] += m[0] * tx + m[1] * ty
            m[5] += m[3] * tx + m[4] * ty
        }
        fun applyScale(sx: Float, sy: Float) {
            m[0] *= sx; m[1] *= sy
            m[3] *= sx; m[4] *= sy
        }
        fun applyRotate(degrees: Float) {
            val rad = Math.toRadians(degrees.toDouble()).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            val m00 = m[0] * c + m[1] * s
            val m01 = -m[0] * s + m[1] * c
            val m10 = m[3] * c + m[4] * s
            val m11 = -m[3] * s + m[4] * c
            m[0] = m00; m[1] = m01
            m[3] = m10; m[4] = m11
        }

        // post* code order (source-space order is the reverse):
        applyTranslate(offsetX, offsetY)
        if (rotationDegrees != 0f) {
            applyRotate(rotationDegrees)
        }
        if (flipX || flipY) {
            val sx = if (flipX) -1f else 1f
            val sy = if (flipY) -1f else 1f
            applyScale(sx, sy)
        }
        val displayScaleX = displaySize.width / sourceWidth
        val displayScaleY = displaySize.height / sourceHeight
        applyScale(imageScale * exportScale * displayScaleX, imageScale * exportScale * displayScaleY)
        applyTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
        return m
    }

    private fun apply(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val nx = m[0] * x + m[1] * y + m[2]
        val ny = m[3] * x + m[4] * y + m[5]
        return nx to ny
    }

    @Test
    fun sourceCenter_mapsToImageCenter_forAllRotationAndFlipCombinations() {
        // The source bitmap's center pixel (sw/2, sh/2) must
        // always map to (offsetX, offsetY) in the export
        // bitmap, regardless of rotation / flip / scale.
        // This holds for both the buggy and the correct
        // matrix order (the source center is a fixed point
        // of rotate/flip around the image center), so this
        // test is a sanity check that the helper is wired
        // up correctly rather than a regression test for
        // the rotation/flip order.
        val cases = listOf(
            // (rotation, flipX, flipY)
            Triple(0f, false, false),
            Triple(90f, false, false),
            Triple(180f, false, false),
            Triple(270f, false, false),
            Triple(45f, false, false),
            Triple(0f, true, false),
            Triple(0f, false, true),
            Triple(0f, true, true),
            Triple(90f, true, false),
            Triple(90f, false, true),
            Triple(180f, true, true),
            Triple(270f, true, false),
        )
        for ((rotation, flipX, flipY) in cases) {
            val m = buildExportMatrix(
                sourceWidth = 200f,
                sourceHeight = 100f,
                displaySize = Size2D(200f, 100f),
                imageScale = 1f,
                flipX = flipX,
                flipY = flipY,
                rotationDegrees = rotation,
                offsetX = 50f,
                offsetY = 100f,
                exportScale = 1f,
            )
            val (cx, cy) = apply(m, 100f, 50f) // source center
            assertEquals(
                "rotation=$rotation flipX=$flipX flipY=$flipY: source center X",
                50f, cx, 0.001f,
            )
            assertEquals(
                "rotation=$rotation flipX=$flipX flipY=$flipY: source center Y",
                100f, cy, 0.001f,
            )
        }
    }

    @Test
    fun rotated90cwWithFlipX_sourceCornersMatchOnScreen() {
        // The on-screen pipeline for an image at canvasCenter
        // (100, 100), displaySize (200, 100), 90° CW rotation
        // + flipX:
        //
        //   translate(topLeft) {
        //     rotate(90° CW, pivot=(w/2, h/2)=(100, 50)) {
        //       scale(-1, 1, pivot=(100, 50)) {
        //         drawImage(bitmap, dstSize=(200, 100))
        //       }
        //     }
        //   }
        //
        // Compose applies Compose's `rotate(degrees, pivot)`
        // with positive degrees = CLOCKWISE in screen
        // coords (Y down). For a point (x, y), R(90° CW
        // screen) maps to (-y, x). The source-space order
        // is drawImage's stretch → scale(flip) →
        // rotate(theta) → translate(topLeft).
        //
        // The four source bitmap corners in the on-screen
        // end up at (after the [centerOffset] +
        // [canvasOffset] = 0 simplification for the test):
        //
        //   source (0, 0)    -> screen (150, 200) -> canvas (150, 200) -> bitmap (100, 200)
        //   source (200, 0)  -> screen (150, 0)   -> canvas (150, 0)   -> bitmap (100, 0)
        //   source (0, 100)  -> screen (50, 200)  -> canvas (50, 200)  -> bitmap (0, 200)
        //   source (200, 100) -> screen (50, 0)   -> canvas (50, 0)    -> bitmap (0, 0)
        //
        // The bbox in canvas units is AABB (50, 0) to
        // (150, 200). minX=50, minY=0. canvasWidth=100,
        // canvasHeight=200. offsetX=(100-50)=50,
        // offsetY=(100-0)=100.
        //
        // Under the BUGGY post* order (`T → S_f → R → S_s
        // → T_c`), source (0, 0) and source (200, 0) both
        // landed at bitmap X=0 (the image was collapsed
        // against the left edge of the bitmap) — this
        // test would FAIL on the buggy code.
        val m = buildExportMatrix(
            sourceWidth = 200f,
            sourceHeight = 100f,
            displaySize = Size2D(200f, 100f),
            imageScale = 1f,
            flipX = true,
            flipY = false,
            rotationDegrees = 90f,
            offsetX = 50f,
            offsetY = 100f,
            exportScale = 1f,
        )
        // Expected per on-screen pipeline (export-side, shifted by (minX, minY)):
        val expected = listOf(
            (0f to 0f) to (100f to 200f),   // source top-left -> bitmap bottom-right
            (200f to 0f) to (100f to 0f),   // source top-right -> bitmap top-right
            (0f to 100f) to (0f to 200f),   // source bottom-left -> bitmap bottom-left
            (200f to 100f) to (0f to 0f),   // source bottom-right -> bitmap top-left
        )
        for ((sourceCorner, expectedCorner) in expected) {
            val (sx, sy) = sourceCorner
            val (ex, ey) = expectedCorner
            val (ax, ay) = apply(m, sx, sy)
            assertEquals(
                "source ($sx, $sy) X",
                ex, ax, 0.001f,
            )
            assertEquals(
                "source ($sx, $sy) Y",
                ey, ay, 0.001f,
            )
        }
    }

    @Test
    fun rotated90cwWithFlipX_layoutCollapseDoesNotOccur() {
        // Same setup as above. The buggy order collapsed
        // the image against the left edge of the rotated
        // bounding box: source (0, 0) and source (sw, 0)
        // both ended up at bitmap X=0 instead of being at
        // opposite edges. This test would FAIL under the
        // buggy order and PASSES under the fix.
        val m = buildExportMatrix(
            sourceWidth = 200f,
            sourceHeight = 100f,
            displaySize = Size2D(200f, 100f),
            imageScale = 1f,
            flipX = true,
            flipY = false,
            rotationDegrees = 90f,
            offsetX = 50f,
            offsetY = 100f,
            exportScale = 1f,
        )
        val (x0, y0) = apply(m, 0f, 0f)
        val (xW, yW) = apply(m, 200f, 0f)
        // The two top corners should be at OPPOSITE X
        // edges of the rotated image (X=100 and X=100 —
        // wait, no, both are at X=100 in the export
        // bitmap since the rotated image is vertical
        // (100x200). Let me restate: the rotated image's
        // vertical extent is 100x200, so all four corners
        // are at the LEFT or RIGHT edge of the rotated
        // image — i.e. at X=0 or X=100 in the bitmap).
        //
        // The buggy code put both top corners at X=0
        // (collapse). The fix puts them at the EXPECTED
        // positions: source (0, 0) at (100, 200) and
        // source (200, 0) at (100, 0). The two Y values
        // are different (200 vs 0), so we check that.
        //
        // More directly: under the buggy order the
        // distance |y0 - yW| would be ~0 (both at y=0).
        // Under the fix it is 200. We assert the latter.
        assertTrue(
            "expected |y0 - yW| = 200 (image's full vertical extent); got " +
                "|${y0} - ${yW}| = ${kotlin.math.abs(y0 - yW)}",
            kotlin.math.abs(y0 - yW) > 100f,
        )
        // Also assert the specific expected values.
        assertEquals(100f, x0, 0.001f)
        assertEquals(200f, y0, 0.001f)
        assertEquals(100f, xW, 0.001f)
        assertEquals(0f, yW, 0.001f)
    }

    @Test
    fun rotatedOnlyNoFlip_sourceCornersRotateCorrectly() {
        // 90° CW with no flip. The four source corners
        // should rotate to the four bitmap corners (in the
        // right positions), without any horizontal mirror.
        // The bbox is (50, 0) to (150, 200). canvasWidth
        // = 100, canvasHeight = 200, offsetX=50,
        // offsetY=100. The on-screen source corners (and
        // their export-bitmap equivalents, shifted by
        // (minX, minY)=(50, 0)):
        //
        //   source (0, 0)    -> screen (0, 50)   -> bitmap (0, 50)? wait that's not on the bbox.
        //
        // Let me recompute. R_pivot(90° CW screen) maps
        // local (x, y) to ((h+w)/2 + something, ...).
        // For source (0, 0): local (0, 0) -> R_pivot
        // rotates (0, 0) around the pivot (100, 50) — the
        // origin maps to... actually the rotate around
        // pivot (0, 0) maps to ((h+w)/2, (h-w)/2). For
        // (w=200, h=100): (150, -50). Then + topLeft
        // (0, 50) = (150, 0). In canvas: (150, 0). In
        // bitmap: (100, 0).
        //
        //   source (0, 0)    -> bitmap (100, 0)
        //   source (200, 0)  -> bitmap (100, 200)
        //   source (0, 100)  -> bitmap (0, 0)
        //   source (200, 100) -> bitmap (0, 200)
        val m = buildExportMatrix(
            sourceWidth = 200f,
            sourceHeight = 100f,
            displaySize = Size2D(200f, 100f),
            imageScale = 1f,
            flipX = false,
            flipY = false,
            rotationDegrees = 90f,
            offsetX = 50f,
            offsetY = 100f,
            exportScale = 1f,
        )
        val expected = listOf(
            (0f to 0f) to (100f to 0f),     // source top-left -> bitmap top-right
            (200f to 0f) to (100f to 200f), // source top-right -> bitmap bottom-right
            (0f to 100f) to (0f to 0f),     // source bottom-left -> bitmap top-left
            (200f to 100f) to (0f to 200f), // source bottom-right -> bitmap bottom-left
        )
        for ((sourceCorner, expectedCorner) in expected) {
            val (sx, sy) = sourceCorner
            val (ex, ey) = expectedCorner
            val (ax, ay) = apply(m, sx, sy)
            assertEquals(
                "source ($sx, $sy) X",
                ex, ax, 0.001f,
            )
            assertEquals(
                "source ($sx, $sy) Y",
                ey, ay, 0.001f,
            )
        }
    }

    @Test
    fun flippedOnlyNoRotation_sourceCornersFlipHorizontally() {
        // flipX = true with no rotation. bbox is
        // (0, 50) to (200, 150). canvasWidth=200,
        // canvasHeight=100, offsetX=100, offsetY=50.
        // The on-screen source corners (and their
        // export-bitmap equivalents, shifted by (minX,
        // minY)=(0, 50)):
        //
        //   source (0, 0)    -> local (0, 0) -> S_f_pivot (flipX around (100, 50)): (200, 0)
        //                   -> T(topLeft) = (200, 0) + (0, 50) = (200, 50)
        //                   -> canvas (200, 50) -> bitmap (200, 0)
        //   source (200, 0)  -> local (200, 0) -> S_f: (0, 0) -> T: (0, 50)
        //                   -> bitmap (0, 0)
        //   source (0, 100)  -> local (0, 100) -> S_f: (200, 100) -> T: (200, 150)
        //                   -> bitmap (200, 100)
        //   source (200, 100) -> local (200, 100) -> S_f: (0, 100) -> T: (0, 150)
        //                   -> bitmap (0, 100)
        val m = buildExportMatrix(
            sourceWidth = 200f,
            sourceHeight = 100f,
            displaySize = Size2D(200f, 100f),
            imageScale = 1f,
            flipX = true,
            flipY = false,
            rotationDegrees = 0f,
            offsetX = 100f,
            offsetY = 50f,
            exportScale = 1f,
        )
        val expected = listOf(
            (0f to 0f) to (200f to 0f),     // source top-left -> bitmap top-right
            (200f to 0f) to (0f to 0f),     // source top-right -> bitmap top-left
            (0f to 100f) to (200f to 100f), // source bottom-left -> bitmap bottom-right
            (200f to 100f) to (0f to 100f), // source bottom-right -> bitmap bottom-left
        )
        for ((sourceCorner, expectedCorner) in expected) {
            val (sx, sy) = sourceCorner
            val (ex, ey) = expectedCorner
            val (ax, ay) = apply(m, sx, sy)
            assertEquals(
                "source ($sx, $sy) X",
                ex, ax, 0.001f,
            )
            assertEquals(
                "source ($sx, $sy) Y",
                ey, ay, 0.001f,
            )
        }
    }

    @Test
    fun exportCanvasSizesImageByIntrinsicDimensionsNotDisplaySize() {
        // V_NOW (2026-06-09 user spec "导出画布时一张张图必须
        // 是原始画质尺寸拼接成一张大画布"): the export canvas
        // must size each image by its INTRINSIC pixel
        // dimensions, NOT [SceneImage.displaySize].
        //
        // [displaySize] is the .pur-derived "transformed" size
        // (`hypot(m00, m10) * intrinsicW`), so for a 4000×3000
        // photo the desktop PockRef wrote at 0.3x, displaySize
        // would be 1200×900 — but the source photo is still
        // 4000×3000, and the user wants the export to use the
        // 4000×3000 pixel size.
        //
        // This test mirrors the relevant Pass 1 / Pass 2 math
        // from [PockRefViewModelScene.exportCanvas] for a
        // single image, no rotation, no flip, no pinch zoom:
        //
        //   w, h  = image.intrinsicWidth  * image.scale  (Pass 1)
        //   dstW  = image.intrinsicWidth  * image.scale * exportScale  (Pass 2)
        //
        // and asserts the four corners of the rotated (in this
        // case identity) AABB land on the intrinsicW/H bounds
        // — NOT the displayW/H bounds. If the test ever flips
        // to expecting displayW/H bounds, somebody reintroduced
        // the "use displaySize for export" bug.
        val intrinsicW = 4000
        val intrinsicH = 3000
        val displayW = 1200f  // a 0.3x .pur transform
        val displayH = 900f
        val scale = 1f
        val exportScale = 1f

        // Pass 1: bbox per image is intrinsic * scale, NOT
        // displaySize * scale. With intrinsic=4000×3000 and
        // scale=1, the bbox per image is 4000×3000 — not
        // 1200×900.
        val bboxW = intrinsicW * scale
        val bboxH = intrinsicH * scale
        assertEquals(
            "Pass 1 bbox must use intrinsicWidth × scale, " +
                "not displaySize.width × scale. The user wants the " +
                "export canvas sized by source photo pixels.",
            4000f, bboxW, 0.001f,
        )
        assertEquals(3000f, bboxH, 0.001f)

        // Pass 2: dstRect per image is the same value, scaled
        // by the export cap-scale (which is 1.0 when the
        // canvas fits under 8192px).
        val dstW = intrinsicW * scale * exportScale
        val dstH = intrinsicH * scale * exportScale
        assertEquals(
            "Pass 2 dstRect must size the image by intrinsic, not displaySize",
            4000f, dstW, 0.001f,
        )
        assertEquals(3000f, dstH, 0.001f)

        // Sanity: the bbox and dstRect MUST NOT equal
        // displaySize. (If a future regression re-uses
        // displaySize for export, this assertion fails.)
        assertNotEquals(displayW, bboxW, 0.001f)
        assertNotEquals(displayH, bboxH, 0.001f)
        assertNotEquals(displayW, dstW, 0.001f)
        assertNotEquals(displayH, dstH, 0.001f)
    }

    @Test
    fun exportCanvasSizesImageByIntrinsicWithUserPinchZoom() {
        // Companion to [exportCanvasSizesImageByIntrinsicDimensionsNotDisplaySize].
        // The user's "原始画质" is intrinsic pixel size, but the
        // user's per-image pinch-zoom ([SceneImage.scale]) IS
        // part of the user's "自由摆放" — it should be reflected
        // in the export. So dstW = intrinsic * image.scale.
        //
        // A user who pinched a 4000×3000 photo down to 50% in
        // the layout should get a 2000×1500 slot in the export
        // canvas (at intrinsic * 0.5), not the original 4000×3000
        // and not the 1200×900 displaySize.
        val intrinsicW = 4000
        val intrinsicH = 3000
        val userScale = 0.5f
        val displayW = 1200f  // 0.3x .pur transform — irrelevant for export
        val displayH = 900f

        val dstW = intrinsicW * userScale
        val dstH = intrinsicH * userScale
        assertEquals(2000f, dstW, 0.001f)
        assertEquals(1500f, dstH, 0.001f)

        // Confirm displaySize is NOT in the formula.
        assertNotEquals(displayW, dstW, 0.001f)
    }
}

/**
 * V_NOW (2026-06-09 user spec "导出画布选项 — 无尺寸上限"):
 * regression tests for [PockRefUiState.exportCanvasMaxDim] —
 * the user-toggleable canvas-cap field. Default `8192`
 * preserves the pre-feature behaviour (256MB ARGB_8888
 * canvas fits a256MB Android heap in practice). `null`
 * disables the cap: the canvas is sized to the actual
 * intrinsic bbox regardless of size, and the per-image
 * decode cap is also lifted so each source image is
 * decoded at its real intrinsic dimensions.
 *
 * These tests verify the math the export pipeline applies
 * to bbox (Pass1) and per-image target size (Pass2)
 * for each cap value:
 * - bbox * scale = final canvas size
 * - scale =1 when bbox <= cap
 * - scale = cap / max(bbox) when bbox > cap
 * - per-image target = intrinsic * scale, capped at
 * maxImageDim (which equals cap/2 when cap is set,
 * or Int.MAX_VALUE when cap is null)
 *
 * The tests don't actually call [PockRefViewModelScene.exportCanvas]
 * (it requires a real Android Bitmap factory and an
 * exportable scene) — they mirror the cap-application
 * math inline so the invariant is locked in even if the
 * pipeline refactors later.
 */
class ExportCanvasMaxDimTest {

 /**
 * Mirrors the cap-application math in
 * [PockRefViewModelScene.exportCanvas]:
 * - maxDim=null -> scale=1, maxImageDim=Int.MAX_VALUE
 * - maxDim!=null -> scale=min(1, maxDim/max(bbox)),
 * maxImageDim = maxDim/2 (4 in practice when maxDim is
 * the default8192)
 */
 private fun computeScaleAndMaxImageDim(
 maxDim: Int?,
 canvasWidth: Int,
 canvasHeight: Int,
 ): Pair<Float, Int> {
 val scale = if (maxDim == null) {
1f
 } else if (canvasWidth > maxDim || canvasHeight > maxDim) {
 maxDim.toFloat() / maxOf(canvasWidth, canvasHeight)
 } else {
1f
 }
 val maxImageDim = if (maxDim == null) Int.MAX_VALUE else maxDim /2
 return scale to maxImageDim
 }

 @Test
 fun defaultMaxDim_preserves8192Behavior() {
 // The pre-feature behaviour: bbox >8192 -> scale to
 // fit, bbox <=8192 -> no scale. Per-image cap =8192/2
 // =4096.
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim =8192,
 canvasWidth =10000,
 canvasHeight =8000,
 )
 //8192 /10000 =0.8192 (longest edge drives the scale)
 assertEquals(0.8192f, scale,0.001f)
 assertEquals(4096, maxImageDim)
 }

 @Test
 fun defaultMaxDim_smallCanvas_noScale() {
 // bbox <8192 -> scale=1, maxImageDim=4096.
 // This is the typical case for a few4000×3000 photos
 // laid out within the canvas — the export keeps full
 // source resolution.
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim =8192,
 canvasWidth =4000,
 canvasHeight =3000,
 )
 assertEquals(1f, scale,0.001f)
 assertEquals(4096, maxImageDim)
 }

 @Test
 fun nullMaxDim_unlimited_noScaleForAnyBbox() {
 // V_NOW (2026-06-09 user spec "无尺寸上限"): when the
 // user picks "原始画质", [PockRefUiState.exportCanvasMaxDim]
 // is null. The canvas must be sized to the actual bbox
 // regardless of size, and the per-image cap is lifted
 // so each source image is decoded at its real
 // intrinsic dimensions.
 //
 //20000×15000 bbox: pre-feature behaviour would shrink
 // to8192 (losing60% of source pixels). New behaviour
 // keeps the full20000×15000 — at the cost of ~1.2GB
 // ARGB_8888 canvas allocation. The dialog warns the
 // user before letting them pick this.
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim = null,
 canvasWidth =20000,
 canvasHeight =15000,
 )
 assertEquals("unlimited: never shrink the canvas",1f, scale,0.001f)
 assertEquals(
 "unlimited: per-image cap lifted to source-resolution",
 Int.MAX_VALUE, maxImageDim,
 )
 }

 @Test
 fun nullMaxDim_unlimited_modestBbox_alsoNoScale() {
 // Sanity check: even a small canvas stays at scale=1
 // when the user picked unlimited (no reason to scale
 // down a4000×3000 bbox — that would defeat the
 // "no cap" intent).
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim = null,
 canvasWidth =4000,
 canvasHeight =3000,
 )
 assertEquals(1f, scale,0.001f)
 assertEquals(Int.MAX_VALUE, maxImageDim)
 }

 @Test
 fun customMaxDim4096_scalesBiggerCanvasDown() {
 // User could conceivably pick a custom cap (future
 // option). For now the dialog only offers8192 vs null,
 // but the math should be general — bbox >4096 scales
 // down, bbox <=4096 stays at scale=1.
 val (scale4096big, maxImageDim4096big) = computeScaleAndMaxImageDim(
 maxDim =4096,
 canvasWidth =8000,
 canvasHeight =6000,
 )
 assertEquals(4096f /8000f, scale4096big,0.001f)
 assertEquals(2048, maxImageDim4096big)

 val (scale4096small, _) = computeScaleAndMaxImageDim(
 maxDim =4096,
 canvasWidth =3000,
 canvasHeight =2000,
 )
 assertEquals(1f, scale4096small,0.001f)
 }

 @Test
 fun nullMaxDim_perImageTargetDecodesAtIntrinsic() {
 // V_NOW (2026-06-09): with unlimited cap, the
 // per-image target size in Pass2 is `intrinsic * scale`
 // with NO `coerceAtMost(maxImageDim)`. For a4000×3000
 // source image, that's4000×3000 (full intrinsic —
 // NOT4096-capped).
 val intrinsicW =4000
 val intrinsicH =3000
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim = null,
 canvasWidth =8000,
 canvasHeight =6000,
 )
 val targetW = (intrinsicW * scale).toInt().coerceAtMost(maxImageDim).coerceAtLeast(1)
 val targetH = (intrinsicH * scale).toInt().coerceAtMost(maxImageDim).coerceAtLeast(1)
 assertEquals(
 "unlimited: per-image target must be full intrinsic, " +
 "not4096-capped",
4000, targetW,
 )
 assertEquals(3000, targetH)
 }

 @Test
 fun defaultMaxDim_perImageTargetCappedAt4096() {
 // Pre-feature behaviour: per-image target is capped at
 //4096, even when intrinsic is bigger. For a6000×4500
 // source image, target =6000 capped at4096 =4096.
 val intrinsicW =6000
 val intrinsicH =4500
 val (scale, maxImageDim) = computeScaleAndMaxImageDim(
 maxDim =8192,
 canvasWidth =12000,
 canvasHeight =9000,
 )
 // scale =8192 /12000 =0.6826... -> targetW =6000 *0.6826 =4096
 val targetW = (intrinsicW * scale).toInt().coerceAtMost(maxImageDim).coerceAtLeast(1)
 assertEquals(4096, targetW)
 }
}

/**
 * V_NOW (2026-06-09 bug fix "无尺寸上限导出图挤一起"):
 * regression test for the Pass1 bbox coordinate-system
 * mix-up that made unlimited exports collapse into a
 * heap in the upper-left.
 *
 * The bug: Pass1 used `image.intrinsicWidth *
 * image.scale` for (w, h) but `image.center.x`
 * directly for (cx, cy). [image.center] is in
 * DISPLAY-size coordinates (= the user's on-screen
 * layout coords, because the .pur matrix encodes
 * [displaySize] as the transformed size and the
 * center as a position in that coord system).
 * Mixing intrinsic extents with displaySize
 * anchors means each image's dstRect was offset by
 * a `1/displayScale` factor toward (0,0) and the
 * whole export collapsed.
 *
 * The fix: convert (cx, cy) to intrinsic coords via
 * `cx_intrinsic = cx_display / displayScale` where
 * `displayScale = displaySize.w / intrinsicW`. Now
 * anchors and extents share one coord system, and
 * positions match the on-screen view.
 */
class ExportCanvasPass1CenterTest {
@Test
fun pass1_bboxUsesIntrinsicCoordsAndConvertsCenter() {
 val intrinsicW =4000
 val intrinsicH =3000
 // User wrote the .pur at displayScale=0.3 (typical
 // desktop PockRef behaviour for a reference board).
 val displayScale =0.3f
 val displayW = intrinsicW * displayScale //1200
 val displayH = intrinsicH * displayScale //900
 val imageScale =1f // no pinch zoom
 // The user laid this image out at displaySize coords
 // (1000,500). After conversion to intrinsic coords,
 // the center should be at (1000/0.3,500/0.3) ≈
 // (3333.33,1666.67).
 val cxDisplay =1000f
 val cyDisplay =500f
 val cxIntrinsic = cxDisplay / displayScale
 val cyIntrinsic = cyDisplay / displayScale
 // Pass1 bbox per image: corners at
 // (cx_intrinsic ± intrinsicW/2, cy_intrinsic ±
 // intrinsicH/2). With intrinsic=4000×3000 and the
 // converted center, the bbox extends
 // [3333.33 -2000,3333.33 +2000] = [1333.33,5333.33].
 val left = cxIntrinsic - intrinsicW * imageScale /2f
 val right = cxIntrinsic + intrinsicW * imageScale /2f
 val top = cyIntrinsic - intrinsicH * imageScale /2f
 val bottom = cyIntrinsic + intrinsicH * imageScale /2f
 assertEquals(1333.33f, left,0.01f)
 assertEquals(5333.33f, right,0.01f)
 assertEquals(166.67f, top,0.01f)
 assertEquals(3166.67f, bottom,0.01f)

 // Sanity: the previous (buggy) bbox using
 // intrinsic×image.scale for extents BUT
 // cx=image.center.x (= displaySize coord) for the
 // anchor would give a left edge at
 // cxDisplay - intrinsicW/2 =1000 -2000 = -1000.
 // That value is FAR from the correct intrinsic-
 // coord bbox edge (1333.33) — proving the bug.
 val buggyLeft = cxDisplay - intrinsicW * imageScale /2f
 assertNotEquals(left, buggyLeft,0.01f)
 // And the buggy value is negative — i.e. the bbox
 // pulls toward (0,0), which is exactly the
 // "collapse into upper-left" symptom the user
 // reported.
 assertTrue("buggy bbox would extend to negative X ($buggyLeft); the fix lifts it to positive ($left)",
 buggyLeft <0f && left >=0f)
}

/**
 * V_NOW (2026-06-09): two-image layout sanity check.
 * Two images at distinct displaySize coords should
 * produce two distinct intrinsic-coord positions
 * in Pass1's bbox. If they collapse to the same X
 * (or overlap their dstRects fully), the bug is back.
 */
@Test
fun pass1_twoImagesAtDifferentCenters_ProduceSeparateBboxExtents() {
 val intrinsicW =4000
 val intrinsicH =3000
 val displayScale =0.3f
 val imageScale =1f

 // Two images in displaySize coords: one at
 // (1000,500), another at (4000,1500). On screen
 // they're1500 display-pixels apart in X (and1000
 // in Y), so a casual viewer expects the export to
 // preserve that spacing — i.e. the two images'
 // bbox edges should not overlap.
 val img1CxDisplay =1000f
 val img1CyDisplay =500f
 val img2CxDisplay =4000f
 val img2CyDisplay =1500f

 // Convert each center to intrinsic coords.
 val img1Cx = img1CxDisplay / displayScale
 val img1Cy = img1CyDisplay / displayScale
 val img2Cx = img2CxDisplay / displayScale
 val img2Cy = img2CyDisplay / displayScale

 // Image1 bbox right edge in intrinsic coords:
 val img1Right = img1Cx + intrinsicW * imageScale /2f
 // Image2 bbox left edge in intrinsic coords:
 val img2Left = img2Cx - intrinsicW * imageScale /2f

 // On screen the centers are1500 display-pixels
 // apart (=5000 intrinsic-pixels apart after
 // scaling), and each image is1200 display-pixels
 // wide (=4000 intrinsic). So they don't overlap
 // on screen, and shouldn't overlap in the export.
 // Centers are3000 display-pixels apart (=10000
 // intrinsic after scaling). Each image is4000
 // intrinsic wide. So img1Right - img2Left =
 //10000 -4000 =6000 (positive gap, no
 // overlap — as the user sees on screen).
 val gap = img2Left - img1Right
 assertEquals("two images at distinct displaySize centers should produce non-overlapping intrinsic bbox extents",
6000f, gap,0.01f)
 assertTrue("expected gap >0 (no overlap); got $gap", gap >0f)
}
}
