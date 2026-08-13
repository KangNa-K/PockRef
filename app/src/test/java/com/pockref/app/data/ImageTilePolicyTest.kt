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
    fun hdImageIdForDisplay_retainsEnabledImageAfterSelectionClearsOnlyWhileLocked() {
        val enabled = testImage(id = "enabled").copy(showsOriginalResolution = true)

        assertEquals(
            "enabled",
            ImageTilePolicy.hdImageIdForDisplay(
                images = listOf(enabled),
                selectedImageIds = emptySet(),
                selectedImageId = null,
                retainedImageId = "enabled",
                retainWhileLocked = true,
            ),
        )
        assertNull(
            ImageTilePolicy.hdImageIdForDisplay(
                images = listOf(enabled),
                selectedImageIds = emptySet(),
                selectedImageId = null,
                retainedImageId = "enabled",
                retainWhileLocked = false,
            ),
        )
    }

    @Test
    fun hdImageIdIfEnabled_rejectsMissingDisabledAndGifImages() {
        val disabled = testImage(id = "disabled")
        val gif = testImage(id = "gif").copy(
            mimeType = "image/gif",
            showsOriginalResolution = true,
        )

        assertNull(ImageTilePolicy.hdImageIdIfEnabled(listOf(disabled), "missing"))
        assertNull(ImageTilePolicy.hdImageIdIfEnabled(listOf(disabled), "disabled"))
        assertNull(ImageTilePolicy.hdImageIdIfEnabled(listOf(gif), "gif"))
    }

    @Test
    fun hdImageIdForDisplay_prefersCurrentSelectedImageOverRetainedId() {
        val selected = testImage(id = "selected").copy(showsOriginalResolution = true)
        val retained = testImage(id = "retained").copy(showsOriginalResolution = true)

        assertEquals(
            "selected",
            ImageTilePolicy.hdImageIdForDisplay(
                images = listOf(selected, retained),
                selectedImageIds = setOf("selected"),
                selectedImageId = "selected",
                retainedImageId = "retained",
                retainWhileLocked = true,
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
    fun requestsTilesForHdImageEvenAtSmallWindowScale() {
        val image = testImage()

        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 0.14f, isHdDisplayActive = true))
        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 0.15f, isHdDisplayActive = true))
        assertFalse(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 1f, isHdDisplayActive = false))
    }

    @Test
    fun requestsTilesForHdImageBelowBoundedLodWhenNormalDecodeWouldDownsample() {
        val image = testImage().copy(
            intrinsicWidth = 3000,
            intrinsicHeight = 2000,
            displaySize = Size2D(720f, 930f),
        )

        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 1f, isHdDisplayActive = true))
        assertFalse(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 3f, isHdDisplayActive = true))
    }

    @Test
    fun requestsTilesForHdImageAtTypicalTabletZoom() {
        val image = testImage().copy(
            displaySize = Size2D(720f, 930f),
        )

        assertTrue(ImageTilePolicy.shouldRequestTiles(image, canvasScale = 3f, isHdDisplayActive = true))
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
