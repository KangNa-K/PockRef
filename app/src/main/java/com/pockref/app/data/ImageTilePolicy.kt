package com.pockref.app.data

import com.pockref.app.model.SceneImage

internal object ImageTilePolicy {
    private const val HD_TILE_TRIGGER_LONG_EDGE_PX = 2048f

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
        return selectedId.takeIf { id ->
            images.firstOrNull { it.id == id }
                ?.takeUnless { it.mimeType.equals("image/gif", ignoreCase = true) }
                ?.showsOriginalResolution == true
        }
    }

    fun shouldRequestTiles(
        image: SceneImage,
        canvasScale: Float,
        isSingleImageSelected: Boolean,
    ): Boolean {
        if (!isSingleImageSelected) return false
        val intrinsicLongEdge = maxOf(image.intrinsicWidth, image.intrinsicHeight)
        if (intrinsicLongEdge <= SceneImageDecoder.DECODE_LADDER.last()) return false

        val displayedLongEdge = maxOf(
            image.displaySize.width * image.scale * canvasScale,
            image.displaySize.height * image.scale * canvasScale,
        )
        return displayedLongEdge > HD_TILE_TRIGGER_LONG_EDGE_PX
    }
}
