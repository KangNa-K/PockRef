package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDecodePolicyTest {

    @Test
    fun normalCanvasDecode_capsLargeImageToViewportPixels() {
        val image = testImage(
            displayWidth = 5000f,
            displayHeight = 3000f,
            imageScale = 1f,
            intrinsicWidth = 5000,
            intrinsicHeight = 3000,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 1f,
            viewportWidth = 1600f,
            viewportHeight = 900f,
            isFocusedImage = false,
        )

        assertEquals(1600, request.displayWidth)
        assertEquals(900, request.displayHeight)
        assertEquals(SceneImageDecoder.DECODE_LADDER.lastIndex, request.maxLevel)
    }

    @Test
    fun normalCanvasDecode_includesPerImageScale() {
        val image = testImage(
            displayWidth = 500f,
            displayHeight = 300f,
            imageScale = 3f,
            intrinsicWidth = 3000,
            intrinsicHeight = 1800,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 1f,
            viewportWidth = 4000f,
            viewportHeight = 3000f,
            isFocusedImage = false,
        )

        assertEquals(1500, request.displayWidth)
        assertEquals(900, request.displayHeight)
    }

    @Test
    fun focusedImage_requestsOriginalQuality() {
        val image = testImage(
            displayWidth = 900f,
            displayHeight = 600f,
            imageScale = 1f,
            intrinsicWidth = 3000,
            intrinsicHeight = 2000,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 1f,
            viewportWidth = 1800f,
            viewportHeight = 1200f,
            isFocusedImage = true,
        )

        assertEquals(3000, request.displayWidth)
        assertEquals(2000, request.displayHeight)
        assertEquals(SceneImageDecoder.ORIGINAL_LEVEL, request.maxLevel)
    }

    @Test
    fun hdImage_requestsOriginalQualityWithoutFocus() {
        val request = ImageDecodePolicy.requestFor(
            image = testImage(
                displayWidth = 900f,
                displayHeight = 600f,
                imageScale = 1f,
                intrinsicWidth = 10881,
                intrinsicHeight = 14073,
            ).copy(showsOriginalResolution = true),
            canvasScale = 0.1f,
            viewportWidth = 2800f,
            viewportHeight = 1752f,
            isFocusedImage = false,
        )

        assertEquals(10881, request.displayWidth)
        assertEquals(14073, request.displayHeight)
        assertEquals(SceneImageDecoder.ORIGINAL_LEVEL, request.maxLevel)
    }

    private fun testImage(
        displayWidth: Float,
        displayHeight: Float,
        imageScale: Float,
        intrinsicWidth: Int,
        intrinsicHeight: Int,
    ): SceneImage = SceneImage(
        id = "image",
        name = "image.jpg",
        mimeType = "image/jpeg",
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
        center = Offset.Zero,
        displaySize = Size2D(displayWidth, displayHeight),
        scale = imageScale,
    )
}
