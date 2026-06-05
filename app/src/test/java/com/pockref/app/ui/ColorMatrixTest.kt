package com.pockref.app.ui

import com.pockref.app.model.ColorAdjust
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM unit tests for the color-matrix math used by the renderer.
 *
 * These helpers live in `ColorMatrixHelpers.kt` (extracted from
 * `PockRefApp.buildColorFilter`) so they can be unit-tested without a
 * Compose runtime. Behavior is asserted to match the original
 * implementation — if you change the math there, update here too.
 */
class ColorMatrixTest {

    private val epsilon = 1e-4f

    // ----- buildColorMatrix ---------------------------------------------------

    @Test
    fun buildColorMatrix_returnsNullForIdentity() {
        // ColorAdjust() == identity (no brightness / contrast / saturation
        // shift), so the contract is "no filter needed" → null.
        val matrix = buildColorMatrix(ColorAdjust())
        assertNull(matrix)
    }

    @Test
    fun buildColorMatrix_nonNullForNonIdentity() {
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = 1f, contrast = 0f, saturation = 0f),
        )
        assertNotNull(matrix)
        assertEquals(20, matrix!!.size)
    }

    @Test
    fun buildColorMatrix_brightnessOne_translatesBy255() {
        // Brightness +1 means +255 in the translation column for all 3
        // color channels (R, G, B).
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = 1f, contrast = 0f, saturation = 0f),
        )!!
        // Translation column is index 4, 9, 14.
        assertEquals(255f, matrix[4], epsilon)
        assertEquals(255f, matrix[9], epsilon)
        assertEquals(255f, matrix[14], epsilon)
    }

    @Test
    fun buildColorMatrix_brightnessNegativeOne_translatesByMinus255() {
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = -1f, contrast = 0f, saturation = 0f),
        )!!
        assertEquals(-255f, matrix[4], epsilon)
        assertEquals(-255f, matrix[9], epsilon)
        assertEquals(-255f, matrix[14], epsilon)
    }

    @Test
    fun buildColorMatrix_brightnessClampedAboveOne() {
        // brightness > 1 should be clamped to 1, so shift = 255 (not 510).
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = 5f, contrast = 0f, saturation = 0f),
        )!!
        assertEquals(255f, matrix[4], epsilon)
    }

    @Test
    fun buildColorMatrix_brightnessClampedBelowMinusOne() {
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = -5f, contrast = 0f, saturation = 0f),
        )!!
        assertEquals(-255f, matrix[4], epsilon)
    }

    @Test
    fun buildColorMatrix_contrastZero_keepsDiagonalOnes() {
        // contrast = 0 → scale = 1 + 0 = 1 → diagonal of the contrast
        // block stays 1. We need a non-default ColorAdjust to bypass
        // the "no filter needed" short-circuit, so we use a tiny
        // brightness shift. The brightness only affects the translation
        // column (4, 9, 14), not the diagonal.
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = 0.01f, contrast = 0f, saturation = 0f),
        )!!
        // The diagonal of the RGB block stays 1 (the contrast scale).
        assertEquals(1f, matrix[0], epsilon)  // R scale
        assertEquals(1f, matrix[6], epsilon)  // G scale
        assertEquals(1f, matrix[12], epsilon) // B scale
        assertEquals(1f, matrix[18], epsilon) // A unchanged
    }

    @Test
    fun buildColorMatrix_contrastNegativeOne_collapsesToZero() {
        // contrast = -1 → scale = 0, so the diagonal becomes 0 and the
        // translation compensates with +128 per channel.
        val matrix = buildColorMatrix(
            ColorAdjust(brightness = 0f, contrast = -1f, saturation = 0f),
        )!!
        assertEquals(0f, matrix[0], epsilon)
        assertEquals(0f, matrix[6], epsilon)
        assertEquals(0f, matrix[12], epsilon)
        // 128 * (1 - 0) = 128 in each translation slot.
        assertEquals(128f, matrix[4], epsilon)
        assertEquals(128f, matrix[9], epsilon)
        assertEquals(128f, matrix[14], epsilon)
    }

    // ----- saturationMatrix ---------------------------------------------------

    @Test
    fun saturationMatrix_zero_producesGrayscaleRows() {
        val m = saturationMatrix(0f)
        assertEquals(20, m.size)
        // All three rows should hold the luminance weights (Rec.709).
        // row 0: [0.213, 0.715, 0.072, 0, 0]
        assertArrayEquals(
            floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
            m.copyOfRange(0, 5),
            epsilon,
        )
        // row 1: same weights
        assertArrayEquals(
            floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
            m.copyOfRange(5, 10),
            epsilon,
        )
        // row 2: same weights
        assertArrayEquals(
            floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f),
            m.copyOfRange(10, 15),
            epsilon,
        )
        // row 3: alpha stays [0, 0, 0, 1, 0]
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f, 1f, 0f),
            m.copyOfRange(15, 20),
            epsilon,
        )
    }

    @Test
    fun saturationMatrix_one_producesIdentity() {
        val m = saturationMatrix(1f)
        assertEquals(20, m.size)
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        assertArrayEquals(expected, m, epsilon)
    }

    @Test
    fun saturationMatrix_rowsPreserveWeightSum() {
        // For any scale s, each row's RGB coefficients should sum to 1
        // (so neutral colors stay neutral).
        for (s in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
            val m = saturationMatrix(s)
            val sumR = m[0] + m[1] + m[2]
            val sumG = m[5] + m[6] + m[7]
            val sumB = m[10] + m[11] + m[12]
            assertEquals("R row sum for s=$s", 1f, sumR, epsilon)
            assertEquals("G row sum for s=$s", 1f, sumG, epsilon)
            assertEquals("B row sum for s=$s", 1f, sumB, epsilon)
        }
    }

    @Test
    fun saturationMatrix_alphaRowUnchangedForAnyScale() {
        for (s in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
            val m = saturationMatrix(s)
            assertArrayEquals(
                "alpha row for s=$s",
                floatArrayOf(0f, 0f, 0f, 1f, 0f),
                m.copyOfRange(15, 20),
                epsilon,
            )
        }
    }

    // ----- multiplyColorMatrices ---------------------------------------------

    @Test
    fun multiply_identityLeft_isNoOp() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val b = floatArrayOf(
            2f, 0f, 0f, 0f, 7f,
            0f, 3f, 0f, 0f, 8f,
            0f, 0f, 4f, 0f, 9f,
            0f, 0f, 0f, 1f, 0f,
        )
        val r = multiplyColorMatrices(identity, b)
        assertArrayEquals(b, r, epsilon)
    }

    @Test
    fun multiply_identityRight_isNoOp() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val a = floatArrayOf(
            2f, 0f, 0f, 0f, 7f,
            0f, 3f, 0f, 0f, 8f,
            0f, 0f, 4f, 0f, 9f,
            0f, 0f, 0f, 1f, 0f,
        )
        val r = multiplyColorMatrices(a, identity)
        assertArrayEquals(a, r, epsilon)
    }

    @Test
    fun multiply_simpleCase_correctResult() {
        // 2x scale on R, no other changes. M_left = diag(2, 1, 1, 1).
        // M_right = identity. Result should be diag(2, 1, 1, 1).
        val left = floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val right = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val r = multiplyColorMatrices(left, right)
        assertArrayEquals(left, r, epsilon)
    }

    @Test
    fun multiply_resultSizeIsAlways20() {
        val left = saturationMatrix(0.5f)
        val right = saturationMatrix(0.8f)
        val r = multiplyColorMatrices(left, right)
        assertEquals(20, r.size)
    }

    @Test
    fun multiply_alphaRowIsAlwaysIdentity() {
        // Multiply any two valid matrices — the alpha row (index 3) of
        // the result should be [0, 0, 0, 1, 0] because both inputs respect
        // the same convention (alpha is treated as a copy of itself with
        // no color coupling).
        val a = saturationMatrix(0.3f)
        val b = saturationMatrix(0.7f)
        val r = multiplyColorMatrices(a, b)
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f, 1f, 0f),
            r.copyOfRange(15, 20),
            epsilon,
        )
    }

    // ----- End-to-end: buildColorMatrix = compose of the helpers -------------

    @Test
    fun buildColorMatrix_saturationMinusOne_equalsGrayscale() {
        // saturation = -1 → saturationScale = 0 → grayscale matrix.
        // With brightness = 0, contrast = 0, the final matrix should
        // equal a pure grayscale (luminance) matrix.
        val m = buildColorMatrix(
            ColorAdjust(brightness = 0f, contrast = 0f, saturation = -1f),
        )!!
        // Diagonal of the color block should be the luminance weights.
        assertEquals(0.213f, m[0], epsilon)
        assertEquals(0.715f, m[1], epsilon)
        assertEquals(0.072f, m[2], epsilon)
        assertEquals(0.213f, m[5], epsilon)
        assertEquals(0.715f, m[6], epsilon)
        assertEquals(0.072f, m[7], epsilon)
        assertEquals(0.213f, m[10], epsilon)
        assertEquals(0.715f, m[11], epsilon)
        assertEquals(0.072f, m[12], epsilon)
    }
}
