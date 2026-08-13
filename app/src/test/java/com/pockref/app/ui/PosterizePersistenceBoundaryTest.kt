package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.data.AutoSaveHash
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.toScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterizePersistenceBoundaryTest {
    private val image = SceneImage(
        id = "image-1",
        name = "image.png",
        mimeType = "image/png",
        imageBytes = byteArrayOf(1, 2, 3),
        intrinsicWidth = 100,
        intrinsicHeight = 80,
        center = Offset.Zero,
        displaySize = Size2D(100f, 80f),
    )

    private val ordinaryState = PockRefUiState(
        images = listOf(image),
        selectedImageId = image.id,
        selectedImageIds = setOf(image.id),
    )

    @Test
    fun posterizePreviewFields_areExcludedFromSceneSerialization() {
        val previewState = ordinaryState
            .openPosterizeControls(setOf(image.id))
            .togglePosterizeLevel(2)

        assertTrue(previewState.posterizeControlsVisible)
        assertEquals(2, previewState.posterizeLevels)
        assertEquals(ordinaryState.toScene(), previewState.toScene())
    }

    @Test
    fun posterizePreviewFields_doNotChangeAutoSaveHash() {
        val previewState = ordinaryState
            .openPosterizeControls(setOf(image.id))
            .togglePosterizeLevel(16)
        val codec = PurSceneCodec()

        assertEquals(
            AutoSaveHash.compute(codec, ordinaryState.toScene()),
            AutoSaveHash.compute(codec, previewState.toScene()),
        )
    }
}
