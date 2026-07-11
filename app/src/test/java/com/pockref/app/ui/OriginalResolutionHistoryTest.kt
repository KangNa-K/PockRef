package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalResolutionHistoryTest {

    @Test
    fun originalResolutionChange_usesReversibleImageReplacement() {
        val original = SceneImage(
            id = "image",
            name = "image",
            mimeType = "image/png",
            intrinsicWidth = 100,
            intrinsicHeight = 80,
            center = Offset.Zero,
            displaySize = Size2D(100f, 80f),
        )
        val before = PockRefUiState(images = listOf(original))
        val after = before.copy(
            images = listOf(original.copy(showsOriginalResolution = true)),
        )

        val diff = computeSceneDiff(before, after)
        assertTrue(diff is SceneDiff.ReplaceImages)
        diff as SceneDiff.ReplaceImages
        assertEquals(before.images, diff.oldImages)
        assertEquals(after.images, diff.newImages)

        val inverse = invertDiff(diff, after) as SceneDiff.ReplaceImages
        assertEquals(after.images, inverse.oldImages)
        assertEquals(before.images, inverse.newImages)
    }
}
