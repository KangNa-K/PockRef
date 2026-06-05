package com.pockref.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [CropRect] and the [CropAspect] enum.
 *
 * The crop rectangle lives in normalized [0, 1] coordinates relative to
 * the source image. The "aspect ratio" of a crop is the aspect ratio of
 * the area it covers in **image pixels**, not the ratio of its
 * normalized [cropWidth] / [cropHeight]. The two only match when the
 * image itself is square.
 *
 * Example: for a 1:1 target on a 16:9 image, the resulting normalized
 * crop is 0.5625 × 1.0 (height fills the full image), which covers an
 * equal number of pixels in width and height.
 */
class CropRectTest {

    private val epsilon = 1e-4f

    /** The "image-pixel" aspect ratio of a crop, i.e. what the user sees. */
    private fun imageAspect(out: CropRect, imgW: Int, imgH: Int): Float {
        val pixelW = out.cropWidth * imgW
        val pixelH = out.cropHeight * imgH
        return pixelW / pixelH
    }

    // ----- withAspect / aspect preservation -----------------------------------

    @Test
    fun withAspect_returnsIdentityForFree() {
        val rect = CropRect(left = 0.1f, top = 0.2f, right = 0.7f, bottom = 0.8f)
        val out = rect.withAspect(CropAspect.FREE, imgW = 1920, imgH = 1080)
        // Contract: when aspect is FREE, the same instance is returned so
        // callers can rely on reference equality (e.g. skip re-render).
        assertSame(rect, out)
    }

    @Test
    fun withAspect_preservesImageAspectForSquare() {
        val rect = CropRect(left = 0.1f, top = 0.1f, right = 0.9f, bottom = 0.9f)
        val out = rect.withAspect(CropAspect.RATIO_1_1, imgW = 1920, imgH = 1080)
        // The crop should cover a square area in image pixels.
        assertEquals(1f, imageAspect(out, 1920, 1080), epsilon)
        // Center must be preserved.
        val cxIn = (rect.left + rect.right) / 2f
        val cyIn = (rect.top + rect.bottom) / 2f
        val cxOut = (out.left + out.right) / 2f
        val cyOut = (out.top + out.bottom) / 2f
        assertEquals(cxIn, cxOut, epsilon)
        assertEquals(cyIn, cyOut, epsilon)
    }

    @Test
    fun withAspect_matchesWidescreenSixteenNine() {
        val rect = CropRect(left = 0.05f, top = 0.05f, right = 0.95f, bottom = 0.95f)
        val out = rect.withAspect(CropAspect.RATIO_16_9, imgW = 1920, imgH = 1080)
        // 16:9 on a 16:9 image should preserve the input rect (it's
        // already the right aspect in image pixels).
        assertEquals(16f / 9f, imageAspect(out, 1920, 1080), epsilon)
    }

    @Test
    fun withAspect_matchesPortraitNineSixteen() {
        val rect = CropRect()
        val out = rect.withAspect(CropAspect.RATIO_9_16, imgW = 1080, imgH = 1920)
        // 9:16 on a 9:16 image preserves the input rect.
        assertEquals(9f / 16f, imageAspect(out, 1080, 1920), epsilon)
    }

    @Test
    fun withAspect_oneToOneOnSixteenNine_producesLetterbox() {
        // For a 1:1 target on a 16:9 image with full-frame input, the
        // output must be a vertical strip (height fills the image,
        // width is 9/16 of the height) — the result is a square in
        // image-pixel space.
        val rect = CropRect(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val out = rect.withAspect(CropAspect.RATIO_1_1, imgW = 1920, imgH = 1080)
        assertEquals(1f, imageAspect(out, 1920, 1080), epsilon)
        // Height should fill the full image.
        assertEquals(1f, out.cropHeight, epsilon)
        // Width should be 9/16 (= 0.5625) of the image width.
        assertEquals(9f / 16f, out.cropWidth, epsilon)
    }

    @Test
    fun withAspect_sixteenNineOnSquare_fitsWidth() {
        // 16:9 on a 1:1 (square) image — width fills, height is 9/16.
        val rect = CropRect(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val out = rect.withAspect(CropAspect.RATIO_16_9, imgW = 1000, imgH = 1000)
        assertEquals(16f / 9f, imageAspect(out, 1000, 1000), epsilon)
        assertEquals(1f, out.cropWidth, epsilon)
        assertEquals(9f / 16f, out.cropHeight, epsilon)
    }

    @Test
    fun withAspect_resultFitsInsideUnitSquare() {
        val cases = listOf(
            CropAspect.RATIO_1_1 to (1920 to 1080),
            CropAspect.RATIO_4_3 to (1920 to 1080),
            CropAspect.RATIO_3_2 to (1920 to 1080),
            CropAspect.RATIO_16_9 to (1920 to 1080),
            CropAspect.RATIO_9_16 to (1080 to 1920),
        )
        for ((aspect, dims) in cases) {
            val out = CropRect().withAspect(aspect, imgW = dims.first, imgH = dims.second)
            assertTrue("left >= 0 for $aspect", out.left >= -epsilon)
            assertTrue("top >= 0 for $aspect", out.top >= -epsilon)
            assertTrue("right <= 1 for $aspect", out.right <= 1f + epsilon)
            assertTrue("bottom <= 1 for $aspect", out.bottom <= 1f + epsilon)
            assertTrue("width > 0 for $aspect", out.cropWidth > 0f)
            assertTrue("height > 0 for $aspect", out.cropHeight > 0f)
        }
    }

    @Test
    fun withAspect_centrePreservedForMultiImage_aspect() {
        val original = CropRect(left = 0.25f, top = 0.3f, right = 0.65f, bottom = 0.7f)
        val out = original.withAspect(CropAspect.RATIO_4_3, imgW = 1920, imgH = 1080)
        val cxIn = (original.left + original.right) / 2f
        val cyIn = (original.top + original.bottom) / 2f
        val cxOut = (out.left + out.right) / 2f
        val cyOut = (out.top + out.bottom) / 2f
        assertEquals(cxIn, cxOut, epsilon)
        assertEquals(cyIn, cyOut, epsilon)
    }

    // ----- isValid boundary cases (0.05 threshold) ----------------------------

    @Test
    fun isValid_trueForDefaultRect() {
        assertTrue(CropRect().isValid())
    }

    @Test
    fun isValid_trueAtWidthJustAboveThreshold() {
        val rect = CropRect(left = 0f, top = 0f, right = 0.0501f, bottom = 1f)
        assertTrue(rect.isValid())
    }

    @Test
    fun isValid_falseAtWidthAtThreshold() {
        // Per the contract: must be STRICTLY greater than 0.05.
        val rect = CropRect(left = 0f, top = 0f, right = 0.05f, bottom = 1f)
        assertFalse(rect.isValid())
    }

    @Test
    fun isValid_falseAtHeightBelowThreshold() {
        val rect = CropRect(left = 0f, top = 0f, right = 1f, bottom = 0.04f)
        assertFalse(rect.isValid())
    }

    @Test
    fun isValid_falseForDegenerateRect() {
        // left == right means zero width.
        val rect = CropRect(left = 0.5f, top = 0.5f, right = 0.5f, bottom = 0.9f)
        assertFalse(rect.isValid())
    }

    // ----- Sanity: default rect dimensions ------------------------------------

    @Test
    fun cropWidthAndHeight_matchBounds() {
        val r = CropRect(left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.7f)
        assertEquals(0.5f, r.cropWidth, 0f)
        assertEquals(0.5f, r.cropHeight, 0f)
    }

    // ----- Aspect enum properties ---------------------------------------------

    @Test
    fun cropAspect_ratiosMatchLabels() {
        assertEquals(1f, CropAspect.RATIO_1_1.ratioW / CropAspect.RATIO_1_1.ratioH, 0f)
        assertEquals(4f / 3f, CropAspect.RATIO_4_3.ratioW / CropAspect.RATIO_4_3.ratioH, 0f)
        assertEquals(3f / 2f, CropAspect.RATIO_3_2.ratioW / CropAspect.RATIO_3_2.ratioH, 0f)
        assertEquals(16f / 9f, CropAspect.RATIO_16_9.ratioW / CropAspect.RATIO_16_9.ratioH, 0f)
        assertEquals(9f / 16f, CropAspect.RATIO_9_16.ratioW / CropAspect.RATIO_9_16.ratioH, 0f)
    }

    @Test
    fun cropAspect_freeHasZeroRatios() {
        assertEquals(0f, CropAspect.FREE.ratioW, 0f)
        assertEquals(0f, CropAspect.FREE.ratioH, 0f)
    }

    @Test
    fun cropAspect_enumsAreDistinct() {
        val all = CropAspect.values()
        assertEquals(6, all.size)
        val labels = all.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    // ----- Defensive: withAspect on a non-default rect ------------------------

    @Test
    fun withAspect_changesDimensionsFromOriginal() {
        val original = CropRect(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val out = original.withAspect(CropAspect.RATIO_1_1, imgW = 1920, imgH = 1080)
        // The square in normalized space has different width/height than
        // the original full-frame 1.0/1.0.
        assertTrue(out.cropWidth != original.cropWidth)
    }
}
