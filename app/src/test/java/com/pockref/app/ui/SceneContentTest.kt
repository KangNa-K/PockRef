package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.hasSceneContent
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneContentTest {
    @Test
    fun textOnlySceneCountsAsSceneContent() {
        val state = PockRefUiState(
            markupTexts = listOf(
                CanvasMarkupText(
                    text = "hello",
                    position = Offset.Zero,
                ),
            ),
        )

        assertTrue(state.hasSceneContent)
    }
}
