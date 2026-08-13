package com.pockref.app.ui

import kotlin.math.round

internal val SUPPORTED_POSTERIZE_LEVELS: Set<Int> = setOf(2, 4, 8, 16)

internal const val POSTERIZE_RED_WEIGHT = 0.213f
internal const val POSTERIZE_GREEN_WEIGHT = 0.715f
internal const val POSTERIZE_BLUE_WEIGHT = 0.072f

/** Quantize normalized luminance to the nearest one of [levels] values. */
internal fun quantizeLuminance(luminance: Float, levels: Int): Float {
    if (levels !in SUPPORTED_POSTERIZE_LEVELS) {
        return luminance.coerceIn(0f, 1f)
    }

    val scale = (levels - 1).toFloat()
    return (round(luminance.coerceIn(0f, 1f) * scale) / scale)
        .coerceIn(0f, 1f)
}

/** Convert an ARGB pixel to a grayscale posterized pixel while retaining alpha. */
internal fun posterizeArgb(argb: Int, levels: Int): Int {
    val alpha = (argb ushr 24) and 0xFF
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val luminance =
        red * POSTERIZE_RED_WEIGHT +
            green * POSTERIZE_GREEN_WEIGHT +
            blue * POSTERIZE_BLUE_WEIGHT
    val gray = (quantizeLuminance(luminance, levels) * 255f).toInt()

    return (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
}

internal fun posterizeArgbPixels(pixels: IntArray, levels: Int) {
    if (levels !in SUPPORTED_POSTERIZE_LEVELS) return

    pixels.indices.forEach { index ->
        pixels[index] = posterizeArgb(pixels[index], levels)
    }
}
