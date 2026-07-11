package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDecodePolicyTest {

    @Test
    fun normalCanvasDecode_matchesVisibleSizeWithHeadroom() {
        val image = testImage(
            displayWidth = 5000f,
            displayHeight = 3000f,
            imageScale = 1f,
            intrinsicWidth = 5000,
            intrinsicHeight = 3000,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 0.2f,
            viewportWidth = 2800f,
            viewportHeight = 1752f,
            isFocusedImage = false,
        )

        assertEquals(1250, request.displayWidth)
        assertEquals(750, request.displayHeight)
        assertEquals(SceneImageDecoder.DECODE_LADDER.lastIndex, request.maxLevel)
    }

    @Test
    fun normalCanvasDecode_usesImageAndCanvasScale() {
        val image = testImage(
            displayWidth = 500f,
            displayHeight = 300f,
            imageScale = 3f,
            intrinsicWidth = 3000,
            intrinsicHeight = 1800,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 2f,
            viewportWidth = 2800f,
            viewportHeight = 1752f,
            isFocusedImage = false,
        )

        assertEquals(2800, request.displayWidth)
        assertEquals(1752, request.displayHeight)
        assertEquals(SceneImageDecoder.DECODE_LADDER.lastIndex, request.maxLevel)
    }

    @Test
    fun normalCanvasDecode_neverRequestsMoreThanSourcePixels() {
        val image = testImage(
            displayWidth = 900f,
            displayHeight = 600f,
            imageScale = 1f,
            intrinsicWidth = 900,
            intrinsicHeight = 600,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 4f,
            viewportWidth = 2800f,
            viewportHeight = 1752f,
            isFocusedImage = false,
        )

        assertEquals(900, request.displayWidth)
        assertEquals(600, request.displayHeight)
        assertEquals(SceneImageDecoder.DECODE_LADDER.lastIndex, request.maxLevel)
    }

    @Test
    fun focusedImage_usesTheLargestBoundedBaseLevel() {
        val image = testImage(
            displayWidth = 900f,
            displayHeight = 600f,
            imageScale = 1f,
            intrinsicWidth = 10881,
            intrinsicHeight = 14073,
        )

        val request = ImageDecodePolicy.requestFor(
            image = image,
            canvasScale = 0.1f,
            viewportWidth = 2800f,
            viewportHeight = 1752f,
            isFocusedImage = true,
        )

        assertEquals(10881, request.displayWidth)
        assertEquals(14073, request.displayHeight)
        assertEquals(SceneImageDecoder.DECODE_LADDER.lastIndex, request.maxLevel)
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
