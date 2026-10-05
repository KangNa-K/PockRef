package com.pockref.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.PockRefApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutoLayoutFabTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tappingFabThroughOpenImportMenuDismissesMenuAndRunsLayout() {
        val actions = mutableListOf<PockRefAction>()
        val state = PockRefUiState(
            images = listOf(
                SceneImage(
                    name = "reference",
                    mimeType = "image/png",
                    intrinsicWidth = 100,
                    intrinsicHeight = 100,
                    center = Offset.Zero,
                    displaySize = Size2D(100f, 100f),
                ),
            ),
            importSourceMenuOpen = true,
        )
        composeRule.setContent {
            PockRefApp(state = state, onAction = actions::add)
        }

        composeRule.onNodeWithContentDescription("一键排版").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PockRefAction.SetImportSourceMenuOpen(false),
                    PockRefAction.ExecuteAutoLayout,
                ),
                actions,
            )
        }
    }
}
