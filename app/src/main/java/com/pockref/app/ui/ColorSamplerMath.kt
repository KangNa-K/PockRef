package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.CropRect
import com.pockref.app.model.SceneImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Pure coordinate and color math for the original-pixel color sampler. */
internal object ColorSamplerMath {
    data class SourcePixel(val x: Int, val y: Int)

    data class PreviewGeometry(val center: Offset, val width: Float, val height: Float)

    fun hoverPreviewGeometry(
        image: SceneImage,
        viewportSize: Size,
        canvasOffset: Offset,
        canvasScale: Float,
        progress: Float,
    ): PreviewGeometry? {
        val vpW = viewportSize.width
        val vpH = viewportSize.height
        if (vpW <= 0f || vpH <= 0f || canvasScale <= 0f || progress <= 0f) return null
        val eased = progress.coerceIn(0f, 1f)
        val maxW = vpW * 0.9f
        val maxH = vpH * 0.9f
        val intrinsicW = image.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val intrinsicH = image.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val zoom = maxOf(vpW * 0.5f / intrinsicW, vpH * 0.5f / intrinsicH).coerceIn(1f, 3.5f)
        val zoomedW = intrinsicW * zoom
        val zoomedH = intrinsicH * zoom
        val fit = minOf(maxW / zoomedW, maxH / zoomedH, 1f)
        val baseW = (zoomedW * fit).coerceAtLeast(1f)
        val baseH = (zoomedH * fit).coerceAtLeast(1f)
        val radians = Math.toRadians(image.rotationDegrees.toDouble())
        val absCos = abs(cos(radians).toFloat())
        val absSin = abs(sin(radians).toFloat())
        val bboxW = baseW * absCos + baseH * absSin
        val bboxH = baseW * absSin + baseH * absCos
        val rotationFit = minOf(maxW / bboxW, maxH / bboxH, 1f)
        val targetW = (baseW * rotationFit).coerceAtLeast(1f)
        val targetH = (baseH * rotationFit).coerceAtLeast(1f)
        val boxW = (targetW * absCos + targetH * absSin) * eased
        val boxH = (targetW * absSin + targetH * absCos) * eased
        val originW = (image.displaySize.width * image.scale * canvasScale).coerceAtLeast(1f)
        val originH = (image.displaySize.height * image.scale * canvasScale).coerceAtLeast(1f)
        val left = boxW / 2f + (vpW - maxW) / 2f
        val right = vpW - boxW / 2f - (vpW - maxW) / 2f
        val top = boxH / 2f + (vpH - maxH) / 2f
        val bottom = vpH - boxH / 2f - (vpH - maxH) / 2f
        return PreviewGeometry(
            center = Offset(
                (vpW / 2f + canvasOffset.x + image.center.x * canvasScale)
                    .coerceIn(minOf(left, right), maxOf(left, right)),
                (vpH / 2f + canvasOffset.y + image.center.y * canvasScale)
                    .coerceIn(minOf(top, bottom), maxOf(top, bottom)),
            ),
            width = originW + (targetW - originW) * eased,
            height = originH + (targetH - originH) * eased,
        )
    }

    // The hover preview draws the entire decoded bitmap, independently of cropRect.
    fun sourcePixelForHoverPreview(
        image: SceneImage,
        point: Offset,
        geometry: PreviewGeometry,
    ): SourcePixel? {
        if (geometry.width <= 0f || geometry.height <= 0f ||
            image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0
        ) return null
        val delta = point - geometry.center
        val radians = Math.toRadians(image.rotationDegrees.toDouble())
        val cosTheta = cos(radians).toFloat()
        val sinTheta = sin(radians).toFloat()
        var x = (cosTheta * delta.x + sinTheta * delta.y) / geometry.width + 0.5f
        var y = (-sinTheta * delta.x + cosTheta * delta.y) / geometry.height + 0.5f
        if (x !in 0f..1f || y !in 0f..1f) return null
        if (image.flipX) x = 1f - x
        if (image.flipY) y = 1f - y
        return SourcePixel(
            floor(x * image.intrinsicWidth).toInt().coerceIn(0, image.intrinsicWidth - 1),
            floor(y * image.intrinsicHeight).toInt().coerceIn(0, image.intrinsicHeight - 1),
        )
    }

    data class ColorValues(
        val argb: Int,
        val hue: Float,
        val saturation: Float,
        val value: Float,
        val alpha: Float,
        val rgbHex: String,
    )

    /**
     * Maps a canvas-local screen point to the unmodified source image.
     * The order mirrors hitTestImage: canvas transform, image rotation,
     * image flip, then the non-destructive crop rectangle.
     */
    fun sourcePixelForScreenPoint(
        image: SceneImage,
        screenPoint: Offset,
        viewportSize: Size,
        canvasOffset: Offset,
        canvasScale: Float,
    ): SourcePixel? {
        if (canvasScale <= 0f || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) {
            return null
        }
        val displayWidth = image.displaySize.width * image.scale
        val displayHeight = image.displaySize.height * image.scale
        if (displayWidth <= 0f || displayHeight <= 0f) return null

        val mappedCenter = Offset(
            screenPoint.x - viewportSize.width / 2f - canvasOffset.x,
            screenPoint.y - viewportSize.height / 2f - canvasOffset.y,
        )
        val dx = mappedCenter.x / canvasScale - image.center.x
        val dy = mappedCenter.y / canvasScale - image.center.y
        val radians = Math.toRadians(image.rotationDegrees.toDouble())
        val cosTheta = kotlin.math.cos(radians).toFloat()
        val sinTheta = kotlin.math.sin(radians).toFloat()
        var localX = cosTheta * dx + sinTheta * dy + displayWidth / 2f
        var localY = -sinTheta * dx + cosTheta * dy + displayHeight / 2f
        if (image.flipX) localX = displayWidth - localX
        if (image.flipY) localY = displayHeight - localY
        if (localX !in 0f..displayWidth || localY !in 0f..displayHeight) return null

        val crop = image.cropRect ?: CropRect()
        val normalizedX = (localX / displayWidth).coerceIn(0f, 1f)
        val normalizedY = (localY / displayHeight).coerceIn(0f, 1f)
        val sourceX = floor(
            (crop.left + normalizedX * crop.cropWidth) * image.intrinsicWidth,
        ).toInt().coerceIn(0, image.intrinsicWidth - 1)
        val sourceY = floor(
            (crop.top + normalizedY * crop.cropHeight) * image.intrinsicHeight,
        ).toInt().coerceIn(0, image.intrinsicHeight - 1)
        return SourcePixel(sourceX, sourceY)
    }

    fun fromArgb(argb: Int): ColorValues {
        val red = (argb shr 16 and 0xFF) / 255f
        val green = (argb shr 8 and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val maxChannel = max(red, max(green, blue))
        val minChannel = min(red, min(green, blue))
        val delta = maxChannel - minChannel
        val hue = when {
            delta == 0f -> 0f
            maxChannel == red -> ((green - blue) / delta).let { if (it < 0f) it + 6f else it } * 60f
            maxChannel == green -> (((blue - red) / delta) + 2f) * 60f
            else -> (((red - green) / delta) + 4f) * 60f
        }
        return ColorValues(
            argb = argb,
            hue = hue,
            saturation = if (maxChannel == 0f) 0f else delta / maxChannel,
            value = maxChannel,
            alpha = (argb ushr 24) / 255f,
            rgbHex = "#%02X%02X%02X".format(
                argb shr 16 and 0xFF,
                argb shr 8 and 0xFF,
                argb and 0xFF,
            ),
        )
    }

    fun composeColor(argb: Int): Color = Color(argb)
}
