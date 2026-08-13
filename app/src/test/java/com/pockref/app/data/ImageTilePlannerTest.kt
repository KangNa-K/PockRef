package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.CropRect
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTilePlannerTest {

    @Test
    fun visibleTiles_coverTheViewportInSourceCoordinates() {
        val requests = ImageTilePlanner.visibleTiles(
            image = image(
                intrinsicWidth = 3000,
                intrinsicHeight = 2000,
                displayWidth = 3000f,
                displayHeight = 2000f,
            ),
            viewport = Rect(-500f, -500f, 500f, 500f),
        )

        assertEquals(
            setOf(
                ImageTileKey("image", column = 0, row = 0),
                ImageTileKey("image", column = 1, row = 0),
                ImageTileKey("image", column = 0, row = 1),
                ImageTileKey("image", column = 1, row = 1),
            ),
            requests.map { it.key }.toSet(),
        )
        assertEquals(4, requests.size)
        assertEquals(ImageTileKey("image", column = 1, row = 0), requests.first().key)
    }

    @Test
    fun visibleTiles_respectTheImageCrop() {
        val requests = ImageTilePlanner.visibleTiles(
            image = image(
                intrinsicWidth = 4096,
                intrinsicHeight = 2048,
                displayWidth = 2048f,
                displayHeight = 2048f,
                cropRect = CropRect(left = 0.5f, top = 0f, right = 1f, bottom = 1f),
            ),
            viewport = Rect(-1024f, -1024f, 1024f, 1024f),
        )

        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.key.column >= 2 })
    }

    @Test
    fun visibleTiles_returnsNothingWhenImageIsOutsideViewport() {
        val requests = ImageTilePlanner.visibleTiles(
            image = image(
                intrinsicWidth = 3000,
                intrinsicHeight = 2000,
                displayWidth = 3000f,
                displayHeight = 2000f,
            ),
            viewport = Rect(2000f, 2000f, 3000f, 3000f),
        )

        assertTrue(requests.isEmpty())
    }

    private fun image(
        intrinsicWidth: Int,
        intrinsicHeight: Int,
        displayWidth: Float,
        displayHeight: Float,
        cropRect: CropRect? = null,
    ) = SceneImage(
        id = "image",
        name = "image.jpg",
        mimeType = "image/jpeg",
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
        center = Offset.Zero,
        displaySize = Size2D(displayWidth, displayHeight),
        cropRect = cropRect,
    )
}
