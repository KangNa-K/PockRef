package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTilePolicyTest {

    @Test
    fun hdSingleSelectedImageId_requiresTheSelectedImageToEnableHd() {
        val disabled = testImage(id = "disabled")
        val enabled = testImage(id = "enabled").copy(showsOriginalResolution = true)

        assertEquals(
            "enabled",
            ImageTilePolicy.hdSingleSelectedImageId(
                images = listOf(disabled, enabled),
                selectedImageIds = setOf("enabled"),
                selectedImageId = "enabled",
            ),
        )
        assertNull(
            ImageTilePolicy.hdSingleSelectedImageId(
                images = listOf(disabled, enabled),
                selectedImageIds = setOf("disabled"),
                selectedImageId = "disabled",
            ),
        )
    }

    @Test
    fun hdSingleSelectedImageId_rejectsGifEvenIfHdWasPersisted() {
        val gif = testImage(id = "gif").copy(
            mimeType = "image/gif",
            showsOriginalResolution = true,
        )

        assertNull(
            ImageTilePolicy.hdSingleSelectedImageId(
                images = listOf(gif),
                selectedImageIds = setOf("gif"),
                selectedImageId = "gif",
            ),
        )
    }

    @Test
    fun singleSelectedImageId_staysAvailableAfterFitModeClears() {
        assertEquals(
            "selected",
            ImageTilePolicy.singleSelectedImageId(
                selectedImageIds = setOf("selected"),
                selectedImageId = "selected",
            ),
        )
        assertEquals(
            "legacy-selected",
            ImageTilePolicy.singleSelectedImageId(
                selectedImageIds = emptySet(),
                selectedImageId = "legacy-selected",
            ),
        )
        assertNull(
            ImageTilePolicy.singleSelectedImageId(
                selectedImageIds = setOf("first", "second"),
                selectedImageId = "second",
            ),
        )
    }

    @Test
    fun requestsTilesWhenSingleSelectedImageExceedsHdTriggerOnScreen() {
        val image = testImage()

        assertFalse(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 0.14f, isSingleImageSelected = true))
        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 0.15f, isSingleImageSelected = true))
        assertFalse(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 1f, isSingleImageSelected = false))
    }

    @Test
    fun requestsTilesForHdImageAtTypicalTabletZoom() {
        val image = testImage().copy(
            displaySize = Size2D(720f, 930f),
        )

        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 3f, isSingleImageSelected = true))
    }

    private fun testImage(id: String = "image") = SceneImage(
        id = id,
        name = "$id.jpg",
        mimeType = "image/jpeg",
        intrinsicWidth = 10881,
        intrinsicHeight = 14073,
        center = Offset.Zero,
        displaySize = Size2D(10881f, 14073f),
    )
}
