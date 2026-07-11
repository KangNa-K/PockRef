package com.pockref.app.data

import com.pockref.app.model.SceneImage
import kotlin.math.roundToInt

data class ImageDecodeRequest(
    val displayWidth: Int,
    val displayHeight: Int,
    val maxLevel: Int,
)

object ImageDecodePolicy {
    private const val DISPLAY_HEADROOM = 1.25f

    fun requestFor(
        image: SceneImage,
        canvasScale: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        isFocusedImage: Boolean,
    ): ImageDecodeRequest {
        if (isFocusedImage) {
            return ImageDecodeRequest(
                displayWidth = image.intrinsicWidth.coerceAtLeast(1),
                displayHeight = image.intrinsicHeight.coerceAtLeast(1),
                maxLevel = SceneImageDecoder.DECODE_LADDER.lastIndex,
            )
        }

        val screenWidth = image.displaySize.width * image.scale * canvasScale * DISPLAY_HEADROOM
        val screenHeight = image.displaySize.height * image.scale * canvasScale * DISPLAY_HEADROOM
        return ImageDecodeRequest(
            displayWidth = screenWidth
                .coerceAtMost(viewportWidth.coerceAtLeast(1f))
                .roundToInt()
                .coerceIn(1, image.intrinsicWidth.coerceAtLeast(1)),
            displayHeight = screenHeight
                .coerceAtMost(viewportHeight.coerceAtLeast(1f))
                .roundToInt()
                .coerceIn(1, image.intrinsicHeight.coerceAtLeast(1)),
            maxLevel = SceneImageDecoder.DECODE_LADDER.lastIndex,
        )
    }
}
