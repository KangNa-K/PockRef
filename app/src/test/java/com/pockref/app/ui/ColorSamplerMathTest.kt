package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.pockref.app.model.CropRect
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorSamplerMathTest {
    private fun image(
        rotation: Float = 0f,
        flipX: Boolean = false,
        flipY: Boolean = false,
        crop: CropRect? = null,
    ) = SceneImage(
        name = "sample",
        mimeType = "image/png",
        intrinsicWidth = 100,
        intrinsicHeight = 50,
        center = Offset.Zero,
        displaySize = Size2D(100f, 50f),
        rotationDegrees = rotation,
        flipX = flipX,
        flipY = flipY,
        cropRect = crop,
    )

    @Test
    fun centerMapsToCenterOfOriginal() {
        assertEquals(
            ColorSamplerMath.SourcePixel(50, 25),
            ColorSamplerMath.sourcePixelForScreenPoint(
                image(), Offset(50f, 25f), Size(100f, 50f), Offset.Zero, 1f,
            ),
        )
    }

    @Test
    fun rotationAndFlipsAreAppliedBeforeSourceMapping() {
        assertEquals(
            ColorSamplerMath.SourcePixel(75, 49),
            ColorSamplerMath.sourcePixelForScreenPoint(
                image(rotation = 90f, flipX = true),
                Offset(25f, 0f), Size(100f, 50f), Offset.Zero, 1f,
            ),
        )
        assertEquals(
            ColorSamplerMath.SourcePixel(0, 0),
            ColorSamplerMath.sourcePixelForScreenPoint(
                image(flipX = true, flipY = true),
                Offset(100f, 50f), Size(100f, 50f), Offset.Zero, 1f,
            ),
        )
    }

    @Test
    fun cropMapsDisplayEdgesIntoOriginalCrop() {
        assertEquals(
            ColorSamplerMath.SourcePixel(20, 10),
            ColorSamplerMath.sourcePixelForScreenPoint(
                image(crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)),
                Offset.Zero, Size(100f, 50f), Offset.Zero, 1f,
            ),
        )
    }

    @Test
    fun pointOutsideImageDoesNotSample() {
        assertNull(
            ColorSamplerMath.sourcePixelForScreenPoint(
                image(), Offset(101f, 25f), Size(100f, 50f), Offset.Zero, 1f,
            ),
        )
    }

    @Test
    fun hoverPreviewUsesItsDisplayedPositionRatherThanCanvasPosition() {
        val image = image()
        val geometry = ColorSamplerMath.hoverPreviewGeometry(
            image, Size(1000f, 600f), Offset(200f, 100f), 1f, 1f,
        )!!
        assertEquals(
            ColorSamplerMath.SourcePixel(50, 25),
            ColorSamplerMath.sourcePixelForHoverPreview(image, geometry.center, geometry),
        )
        assertEquals(
            ColorSamplerMath.SourcePixel(25, 12),
            ColorSamplerMath.sourcePixelForHoverPreview(
                image, geometry.center - Offset(geometry.width / 4f, geometry.height / 4f), geometry,
            ),
        )
        assertNull(ColorSamplerMath.sourcePixelForHoverPreview(
            image, geometry.center + Offset(geometry.width, 0f), geometry,
        ))
    }

    @Test
    fun hoverPreviewMappingMatchesRotationFlipAndFullBitmap() {
        val image = image(rotation = 90f, flipX = true, crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f))
        val geometry = ColorSamplerMath.PreviewGeometry(Offset(400f, 300f), 200f, 100f)
        assertEquals(
            ColorSamplerMath.SourcePixel(75, 12),
            ColorSamplerMath.sourcePixelForHoverPreview(image, Offset(425f, 250f), geometry),
        )
    }

    @Test
    fun hoverGeometryInterpolatesFromCanvasImageAndClampsToViewport() {
        val image = image(rotation = 45f)
        val viewport = Size(1000f, 600f)
        val geometry = ColorSamplerMath.hoverPreviewGeometry(
            image, viewport, Offset(1000f, 1000f), 2f, 1f,
        )!!
        val cos = kotlin.math.cos(Math.PI / 4).toFloat()
        val halfBoundWidth = (geometry.width + geometry.height) * cos / 2f
        val halfBoundHeight = halfBoundWidth
        assertEquals(950f, geometry.center.x + halfBoundWidth, 0.01f)
        assertEquals(570f, geometry.center.y + halfBoundHeight, 0.01f)
        assertNull(ColorSamplerMath.hoverPreviewGeometry(image, viewport, Offset.Zero, 1f, 0f))
    }

    @Test
    fun lensFollowsPointerAndAvoidsViewportEdges() {
        val viewport = Size(1000f, 600f)
        assertEquals(Offset(500f, 312f), colorSamplerLensCenter(Offset(500f, 400f), viewport, 128f, 24f, 8f))
        assertEquals(Offset(72f, 108f), colorSamplerLensCenter(Offset(5f, 20f), viewport, 128f, 24f, 8f))
        assertEquals(Offset(928f, 512f), colorSamplerLensCenter(Offset(995f, 600f), viewport, 128f, 24f, 8f))
    }

    @Test
    fun argbFormatsRgbAndHsv() {
        val values = ColorSamplerMath.fromArgb(0x80408020.toInt())
        assertEquals("#408020", values.rgbHex)
        assertEquals(0.75f, values.saturation, 0.0001f)
        assertEquals(0.50196f, values.value, 0.0001f)
        assertEquals(0.50196f, values.alpha, 0.0001f)
        assertEquals(100f, values.hue, 0.01f)
    }
}
