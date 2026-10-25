package com.pockref.app.ui

import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.Size2D
import com.pockref.app.model.SceneImage
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasChromePolicyTest {
    @Test
    fun emptyCanvasKeepsCommandsAndLayoutActionButNoLayerPanelOrSelectionTools() {
        val policy = CanvasChromePolicy.resolve(PockRefUiState(blankCanvasOpen = true))

        assertTrue(policy.showTopToolbar)
        assertTrue(policy.showActionPanel)
        assertFalse(policy.showGlobalLock)
        assertFalse(policy.showLayerPanel)
        assertFalse(policy.showSelectionPanel)
        assertTrue(policy.showSelectionMode)
    }

    @Test
    fun selectedObjectsShowSelectionPanelInsteadOfSelectionMode() {
        val state = PockRefUiState(
            images = listOf(testImage()),
            selectedImageIds = setOf("image-1"),
        )

        val policy = CanvasChromePolicy.resolve(state)

        assertTrue(policy.showSelectionPanel)
        assertFalse(policy.showSelectionMode)
        assertTrue(policy.showLayerPanel)
    }

    @Test
    fun frozenCanvasHidesEditingChromeAndRetainsUnlockControl() {
        val state = PockRefUiState(
            images = listOf(testImage()),
            lockCanvasFrozen = true,
        )

        val policy = CanvasChromePolicy.resolve(state)

        assertTrue(policy.showGlobalLock)
        assertFalse(policy.showActionPanel)
        assertFalse(policy.showLayerPanel)
        assertFalse(policy.showSelectionPanel)
        assertFalse(policy.showSelectionMode)
    }

    @Test
    fun markupModeHidesNormalToolbarAndOtherEditingChrome() {
        listOf(PockRefUiState(blankCanvasOpen = true),
            PockRefUiState(images = listOf(testImage()))).forEach { state ->
            val policy = CanvasChromePolicy.resolve(state.copy(markupModeActive = true))
            assertFalse(policy.showTopToolbar)
            assertFalse(policy.showGlobalLock)
            assertFalse(policy.showActionPanel)
            assertFalse(policy.showLayerPanel)
            assertFalse(policy.showSelectionPanel)
            assertFalse(policy.showSelectionMode)
        }
    }

    private fun testImage() = SceneImage(
        id = "image-1",
        name = "Reference",
        mimeType = "image/png",
        intrinsicWidth = 1,
        intrinsicHeight = 1,
        center = Offset.Zero,
        displaySize = Size2D(1f, 1f),
    )
}
