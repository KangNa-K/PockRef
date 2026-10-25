package com.pockref.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockref.app.model.CanvasMarkupText
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
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

        composeRule.onNodeWithContentDescription("一键排版").performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PockRefAction.SetImportSourceMenuOpen(false),
                    PockRefAction.ExecuteAutoLayout,
                ),
                actions.filter {
                    it is PockRefAction.SetImportSourceMenuOpen ||
                        it == PockRefAction.ExecuteAutoLayout
                },
            )
        }
    }

    @Test
    fun blankCanvasShowsAutoLayoutFab() {
        val state = PockRefUiState(
            blankCanvasOpen = true,
        )

        composeRule.setContent {
            PockRefApp(state = state, onAction = {})
        }

        composeRule.onNodeWithContentDescription("一键排版").assertExists()
    }

    @Test
    fun blankCanvasShowsAllActionsWithoutExpanding() {
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            PockRefApp(
                state = PockRefUiState(blankCanvasOpen = true),
                onAction = actions::add,
            )
        }

        assertAllPanelActionsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.runOnIdle {
            assertEquals(true, actions.contains(PockRefAction.SetSettingsSheetOpen(true)))
        }
    }

    @Test
    fun idleAndLongPressDoNotCollapsePanel() {
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            PockRefApp(
                state = PockRefUiState(
                    blankCanvasOpen = true,
                    bottomRightPanelExpanded = true,
                ),
                onAction = actions::add,
            )
        }

        assertAllPanelActionsDisplayed()
        // The former timeout used wall-clock time, not the Compose test clock.
        android.os.SystemClock.sleep(3500)
        composeRule.waitForIdle()
        assertAllPanelActionsDisplayed()
        composeRule.onNodeWithContentDescription("一键排版")
            .performTouchInput { longClick() }
        assertAllPanelActionsDisplayed()
        composeRule.runOnIdle {
            assertEquals(emptyList<PockRefAction.SetBottomRightPanelExpanded>(),
                actions.filterIsInstance<PockRefAction.SetBottomRightPanelExpanded>())
        }
    }

    @Test
    fun selectedPhoneCanvasKeepsCornerActionsAlignedBesideSelectionToolbar() {
        val image = SceneImage(
            id = "phone-image", name = "reference", mimeType = "image/png",
            intrinsicWidth = 100, intrinsicHeight = 100, center = Offset.Zero,
            displaySize = Size2D(100f, 100f),
        )
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                PockRefApp(
                    state = PockRefUiState(images = listOf(image), selectedImageIds = setOf(image.id)),
                    onAction = actions::add,
                )
            }
        }
        assertAllPanelActionsDisplayed()
        val toolbar = composeRule.onNode(androidx.compose.ui.test.hasTestTag("selection-action-toolbar"))
            .fetchSemanticsNode().boundsInRoot
        val layout = composeRule.onNodeWithContentDescription("一键排版")
        val layoutBounds = layout.fetchSemanticsNode().boundsInRoot
        assertEquals(layoutBounds.bottom, toolbar.bottom, 1f)
        assertTrue("Selection toolbar must leave room for the right action panel",
            toolbar.right < layoutBounds.left)
        layout.performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(actions.contains(PockRefAction.ExecuteAutoLayout)) }
    }

    @Test
    fun markupVisibilityIsDirectlyBelowCanvasLock() {
        val image = SceneImage(
            name = "reference", mimeType = "image/png",
            intrinsicWidth = 100, intrinsicHeight = 100, center = Offset.Zero,
            displaySize = Size2D(100f, 100f),
        )
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                PockRefApp(
                    state = PockRefUiState(
                        images = listOf(image),
                        markupTexts = listOf(CanvasMarkupText(text = "test", position = Offset.Zero)),
                    ),
                    onAction = actions::add,
                )
            }
        }
        val lock = composeRule.onNodeWithContentDescription(
            "全局图片未锁定,点击锁定全部图片,长按进入冻结模式",
        ).fetchSemanticsNode().boundsInRoot
        val visibility = composeRule.onNodeWithContentDescription("隐藏标记")
        visibility.assertIsDisplayed()
        val bounds = visibility.fetchSemanticsNode().boundsInRoot
        assertEquals(lock.center.x, bounds.center.x, 1f)
        assertTrue(bounds.top > lock.bottom)
        visibility.performClick()
        composeRule.runOnIdle { assertTrue(actions.contains(PockRefAction.ToggleMarkupVisibility)) }
    }

    private fun assertAllPanelActionsDisplayed() {
        composeRule.onNodeWithContentDescription("添加文本").assertDoesNotExist()
        listOf("标记", "Settings", "Export canvas", "Save scene", "一键排版")
            .forEach { description ->
                composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            }
    }

}
