package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizeStateTest {
    private val imageA = SceneImage(
        id = "a",
        name = "a.png",
        mimeType = "image/png",
        intrinsicWidth = 100,
        intrinsicHeight = 100,
        center = Offset.Zero,
        displaySize = Size2D(100f, 100f),
    )
    private val imageB = imageA.copy(id = "b", name = "b.png")

    @Test
    fun openPosterizeControls_startsInOrdinaryGrayscale() {
        val result = PockRefUiState(
            images = listOf(imageA, imageB),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        assertTrue(result.posterizeControlsVisible)
        assertEquals(null, result.posterizeLevels)
        assertEquals(setOf("a"), result.posterizeTargetImageIds)
    }

    @Test
    fun togglePosterizeLevel_repeatedClickClearsPersistedLevel() {
        val opened = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        val enabled = opened.togglePosterizeLevel(2)
        val disabled = enabled.togglePosterizeLevel(2)

        assertEquals(2, enabled.posterizeLevels)
        assertEquals(null, disabled.posterizeLevels)
        assertEquals(2, enabled.images.single().posterizeLevels)
        assertEquals(null, disabled.images.single().posterizeLevels)
        assertTrue(disabled.posterizeControlsVisible)
    }

    @Test
    fun togglePosterizeLevel_switchesBetweenSupportedLevels() {
        val state = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        val result = state.togglePosterizeLevel(2).togglePosterizeLevel(8)

        assertEquals(8, result.posterizeLevels)
        assertEquals(8, result.images.single().posterizeLevels)
    }

    @Test
    fun togglePosterizeLevel_rejectsUnsupportedLevels() {
        val state = PockRefUiState(
            images = listOf(imageA),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))

        assertEquals(state, state.togglePosterizeLevel(3))
    }

    @Test
    fun normalizeSelection_clearsPreviewWhenSelectionChanges() {
        val state = PockRefUiState(
            images = listOf(imageA, imageB),
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).openPosterizeControls(setOf("a"))
            .togglePosterizeLevel(4)

        val result = state.copy(
            selectedImageId = "b",
            selectedImageIds = setOf("b"),
        ).normalizeSelection()

        assertFalse(result.posterizeControlsVisible)
        assertEquals(null, result.posterizeLevels)
        assertTrue(result.posterizeTargetImageIds.isEmpty())
        assertEquals(4, result.posterizeLevelsFor("a"))
    }

    @Test
    fun openPosterizeControls_restoresCommonPersistedLevel() {
        val result = PockRefUiState(
            images = listOf(
                imageA.copy(posterizeLevels = 8),
                imageB.copy(posterizeLevels = 8),
            ),
            selectedImageId = "a",
            selectedImageIds = setOf("a", "b"),
        ).openPosterizeControls(setOf("a", "b"))

        assertTrue(result.posterizeControlsVisible)
        assertEquals(8, result.posterizeLevels)
    }
}
