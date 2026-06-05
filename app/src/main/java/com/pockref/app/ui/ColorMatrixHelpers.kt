package com.pockref.app.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.pockref.app.model.ColorAdjust

/**
 * Pure-Kotlin helpers for the color-matrix math used by the rendering
 * pipeline. These are `internal` (not `private`) so unit tests in the
 * `test` source set can verify the math without going through Compose
 * (which would force us into Robolectric for Compose previews).
 *
 * Source: extracted from [buildColorFilter] / [saturationMatrix] /
 * [multiplyColorMatrices] in PockRefApp.kt to make the math testable.
 * Behavior is intentionally identical to the originals; the existing
 * private functions in PockRefApp.kt now delegate here.
 */
internal fun saturationMatrix(scale: Float): FloatArray {
    val inverse = 1f - scale
    val r = 0.213f * inverse
    val g = 0.715f * inverse
    val b = 0.072f * inverse
    return floatArrayOf(
        r + scale, g, b, 0f, 0f,
        r, g + scale, b, 0f, 0f,
        r, g, b + scale, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

internal fun multiplyColorMatrices(left: FloatArray, right: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0 until 4) {
        val rowBase = row * 5
        for (column in 0 until 5) {
            result[rowBase + column] = if (column == 4) left[rowBase + 4] else 0f
            for (k in 0 until 4) {
                val rightValue = right[k * 5 + column]
                result[rowBase + column] += left[rowBase + k] * rightValue
            }
        }
    }
    return result
}

/**
 * Compute the 4x5 color matrix that corresponds to [adjust], or `null` if
 * [adjust] is the identity (no filter needed).
 *
 * This is the testable core of `buildColorFilter`. The original function
 * also wraps the matrix in a Compose [ColorFilter]; the wrap is trivial
 * and is exercised by the production render path, so we test only the
 * matrix math here.
 */
internal fun buildColorMatrix(adjust: ColorAdjust): FloatArray? {
    if (adjust == ColorAdjust()) return null

    val brightnessShift = adjust.brightness.coerceIn(-1f, 1f) * 255f
    val contrastScale = (1f + adjust.contrast.coerceIn(-1f, 1f)).coerceAtLeast(0f)
    val contrastTranslate = 128f * (1f - contrastScale)
    val saturationScale = (1f + adjust.saturation.coerceIn(-1f, 1f)).coerceAtLeast(0f)

    val sat = saturationMatrix(saturationScale)
    val contrastBrightnessMatrix = floatArrayOf(
        contrastScale, 0f, 0f, 0f, contrastTranslate + brightnessShift,
        0f, contrastScale, 0f, 0f, contrastTranslate + brightnessShift,
        0f, 0f, contrastScale, 0f, contrastTranslate + brightnessShift,
        0f, 0f, 0f, 1f, 0f,
    )
    return multiplyColorMatrices(contrastBrightnessMatrix, sat)
}
