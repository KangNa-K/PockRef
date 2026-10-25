package com.pockref.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.CanvasMarkupTool
import androidx.compose.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkupOptionsPopupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun penStyleIsCompactAndOutsideTapOnlyDismisses() {
        verifyStylePopup(CanvasMarkupTool.PEN, "画笔")
    }

    @Test fun highlighterStyleIsCompactAndOutsideTapOnlyDismisses() {
        verifyStylePopup(CanvasMarkupTool.HIGHLIGHTER, "荧光笔")
    }

    private fun verifyStylePopup(tool: CanvasMarkupTool, label: String) {
        var canvasClicks = 0
        val actions = mutableListOf<PockRefAction>()
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf(PockRefUiState(markupTool = tool)) }
            Box(Modifier.fillMaxSize().testTag("style-test-canvas").clickable { canvasClicks++ },
                contentAlignment = Alignment.BottomCenter) {
                MarkupToolPalette(state, { action ->
                    actions.add(action)
                    if (action is PockRefAction.SetMarkupWidth) {
                        state = state.copy(markupLineWidth = action.width)
                    }
                })
            }
        } }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.onNodeWithContentDescription("关闭工具选项").assertDoesNotExist()
        val popup = compose.onNodeWithTag("markup-options-popup").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 254.dp.toPx() }, popup.width, 1f)
        assertEquals(with(compose.density) { 68.dp.toPx() }, popup.height, 1f)
        listOf(2, 4, 8, 16).forEach { width ->
            compose.onNodeWithContentDescription("线宽 $width").performClick().assertIsSelected()
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.takeScreenshot(java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "compact-style-${tool.name}.png"))
        val canvas = compose.onNodeWithTag("style-test-canvas").fetchSemanticsNode().boundsInWindow
        device.click(canvas.center.x.toInt(), canvas.top.toInt() + 20)
        compose.onNodeWithTag("markup-options-popup").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, canvasClicks)
            assertEquals(listOf(2f, 4f, 8f, 16f),
                actions.filterIsInstance<PockRefAction.SetMarkupWidth>().map { it.width })
        }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.onNodeWithContentDescription("线宽 16").assertIsSelected()
    }

    @Test fun popupIsCompactAndTextFollowsMove() {
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MarkupToolPalette(PockRefUiState(), {})
            }
        } }
        compose.onNodeWithContentDescription("形状和更多工具").performScrollTo().performClick()
        compose.onNodeWithContentDescription("关闭工具选项").assertDoesNotExist()
        val popup = compose.onNodeWithTag("markup-options-popup").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("添加文本").performScrollTo()
        val move = compose.onNodeWithContentDescription("选择").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithContentDescription("添加文本").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 68.dp.toPx() }, popup.height, 1f)
        assertTrue("Text must be right of move", text.left >= move.right)
        assertTrue("Popup must wrap tools", popup.width <= with(compose.density) { 368.dp.toPx() } + 1f)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "compact-markup-popup.png"))
    }

    @Test fun outsideTapDismissesWithoutActivatingCanvas() {
        var canvasClicks = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().testTag("palette-test-canvas").clickable { canvasClicks++ },
                contentAlignment = Alignment.BottomCenter) {
                MarkupToolPalette(PockRefUiState(), {})
            }
        } }
        compose.onNodeWithContentDescription("形状和更多工具").performScrollTo().performClick()
        val canvas = compose.onNodeWithTag("palette-test-canvas").fetchSemanticsNode().boundsInWindow
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .click(canvas.center.x.toInt(), canvas.top.toInt() + 20)
        compose.onNodeWithTag("markup-options-popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, canvasClicks) }
        compose.onNodeWithContentDescription("形状和更多工具").performScrollTo().performClick()
        compose.onNodeWithContentDescription("选择").performScrollTo().performClick()
        compose.onNodeWithTag("markup-options-popup").assertDoesNotExist()
    }
}
