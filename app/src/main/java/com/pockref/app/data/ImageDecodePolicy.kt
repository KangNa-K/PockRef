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

    /**
     * Use the image's rendered size as the decode target. The viewport only
     * decides which images need decoding; it must not shrink this target,
     * because the canvas can show a cropped part of an image larger than the
     * window. The decoder still selects a bounded LOD and clamps to source size.
     */
    fun requestFor(
        image: SceneImage,
        canvasScale: Float,
        isFocusedImage: Boolean,
    ): ImageDecodeRequest {
        if (isFocusedImage || image.showsOriginalResolution) {
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
                .roundToInt()
                .coerceIn(1, image.intrinsicWidth.coerceAtLeast(1)),
            displayHeight = screenHeight
                .roundToInt()
                .coerceIn(1, image.intrinsicHeight.coerceAtLeast(1)),
            maxLevel = SceneImageDecoder.DECODE_LADDER.lastIndex,
        )
    }
}
