package com.pockref.app.data

import com.pockref.app.model.SceneImage

internal object ImageTilePolicy {
    private const val NORMAL_DECODE_HEADROOM = 1.25f

    fun singleSelectedImageId(
        selectedImageIds: Set<String>,
        selectedImageId: String?,
    ): String? {
        val selectedIds = selectedImageIds.ifEmpty {
            selectedImageId?.let(::setOf).orEmpty()
        }
        return selectedIds.singleOrNull()
    }

    fun hdSingleSelectedImageId(
        images: List<SceneImage>,
        selectedImageIds: Set<String>,
        selectedImageId: String?,
    ): String? {
        val selectedId = singleSelectedImageId(selectedImageIds, selectedImageId) ?: return null
        return hdImageIdIfEnabled(images, selectedId)
    }

    /**
     * Return [imageId] only when it is an eligible non-GIF image with
     * original-resolution display enabled. This is also used after the
     * lock reducer clears selection, so the renderer never trusts a stale
     * retained ID without validating it against the current scene.
     */
    fun hdImageIdIfEnabled(
        images: List<SceneImage>,
        imageId: String?,
    ): String? = imageId?.takeIf { id ->
        images.firstOrNull { it.id == id }
            ?.takeUnless { it.mimeType.equals("image/gif", ignoreCase = true) }
            ?.showsOriginalResolution == true
    }

    /**
     * Resolve the image whose HD tiles should be rendered. Normal mode
     * remains selection-scoped; lock mode may use the image retained just
     * before selection was cleared.
     */
    fun hdImageIdForDisplay(
        images: List<SceneImage>,
        selectedImageIds: Set<String>,
        selectedImageId: String?,
        retainedImageId: String?,
        retainWhileLocked: Boolean,
    ): String? {
        hdSingleSelectedImageId(
            images = images,
            selectedImageIds = selectedImageIds,
            selectedImageId = selectedImageId,
        )?.let { return it }
        if (!retainWhileLocked) return null
        return hdImageIdIfEnabled(images, retainedImageId)
    }

    /**
     * HD must remain source-resolution based even when the window is small.
     * Request visible source tiles whenever the normal bounded decode would
     * lose source pixels; the tile cache still bounds memory use.
     */
    fun shouldRequestTiles(
        image: SceneImage,
        canvasScale: Float,
        isHdDisplayActive: Boolean,
    ): Boolean {
        if (!isHdDisplayActive) return false
        val intrinsicLongEdge = maxOf(image.intrinsicWidth, image.intrinsicHeight)
        val displayedLongEdge = maxOf(
            image.displaySize.width * image.scale * canvasScale,
            image.displaySize.height * image.scale * canvasScale,
        )
        if (intrinsicLongEdge <= 0 || displayedLongEdge <= 0f) return false

        val normalTargetLongEdge = displayedLongEdge * NORMAL_DECODE_HEADROOM
        return intrinsicLongEdge > SceneImageDecoder.DECODE_LADDER.last() ||
            intrinsicLongEdge.toFloat() > normalTargetLongEdge
    }
}
