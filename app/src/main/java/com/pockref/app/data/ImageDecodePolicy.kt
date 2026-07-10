package com.pockref.app.data

import com.pockref.app.model.SceneImage
import kotlin.math.roundToInt

data class ImageDecodeRequest(
    val displayWidth: Int,
    val displayHeight: Int,
    val maxLevel: Int,
)

object ImageDecodePolicy {
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
                maxLevel = SceneImageDecoder.ORIGINAL_LEVEL,
            )
        }

        val screenWidth = image.displaySize.width * image.scale * canvasScale
        val screenHeight = image.displaySize.height * image.scale * canvasScale
        return ImageDecodeRequest(
            displayWidth = screenWidth
                .coerceAtMost(viewportWidth.coerceAtLeast(1f))
                .roundToInt()
                .coerceAtLeast(1),
            displayHeight = screenHeight
                .coerceAtMost(viewportHeight.coerceAtLeast(1f))
                .roundToInt()
                .coerceAtLeast(1),
            maxLevel = SceneImageDecoder.DECODE_LADDER.lastIndex,
        )
    }
}
