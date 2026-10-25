package com.pockref.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.CanvasMarkupTool
import com.pockref.app.model.PockRefUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkupToolPaletteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolsAreCenteredWithHistoryAndDockHasCompactHeight() {
        compose.setContent { MaterialTheme { MarkupToolPalette(PockRefUiState(), {}) } }
        val undo = compose.onNodeWithContentDescription("撤销").fetchSemanticsNode().boundsInRoot
        val redo = compose.onNodeWithContentDescription("重做").fetchSemanticsNode().boundsInRoot
        val centerY = (undo.center.y + redo.center.y) / 2f
        listOf("画笔", "荧光笔", "对象橡皮", "局部擦墨迹", "套索", "颜色", "形状和更多工具").forEach {
            val bounds = compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
            assertEquals(it, centerY, bounds.center.y, 1f)
        }
        val dock = compose.onNodeWithTag("markup-tool-dock").fetchSemanticsNode().boundsInRoot
        val padding = with(compose.density) { 8.dp.toPx() }
        val history = compose.onNodeWithTag("markup-history-controls").fetchSemanticsNode().boundsInRoot
        assertEquals(history.height + padding, dock.height, 1f)
    }

    @Test fun allToolsRemainReachableWithoutVisibleLabels() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent { MaterialTheme { MarkupToolPalette(PockRefUiState(), actions::add) } }
        listOf("荧光笔", "对象橡皮", "局部擦墨迹", "套索").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
            compose.onNodeWithContentDescription(it).performScrollTo().performClick()
        }
        listOf("直线", "箭头", "矩形", "椭圆", "添加文本", "选择").forEach {
            compose.onNodeWithContentDescription("形状和更多工具").performScrollTo().performClick()
            compose.onNodeWithText(it).assertDoesNotExist()
            compose.onNodeWithContentDescription(it).performScrollTo().performClick()
        }
        compose.runOnIdle {
            assertTrue(actions.contains(PockRefAction.RequestMarkupText))
            assertEquals((CanvasMarkupTool.entries.toSet() - CanvasMarkupTool.PEN), actions.filterIsInstance<PockRefAction.SetMarkupTool>().map { it.tool }.toSet())
        }
    }

    @Test fun swatchesAndStrokePreviewsUpdateStateAndCanClose() {
        compose.setContent {
            var state by remember { mutableStateOf(PockRefUiState()) }
            MaterialTheme {
                MarkupToolPalette(state, { action ->
                    state = when (action) {
                        is PockRefAction.SetMarkupWidth -> state.copy(markupLineWidth = action.width)
                        is PockRefAction.SetMarkupColor -> state.copy(markupColor = action.color)
                        else -> state
                    }
                })
            }
        }
        compose.onNodeWithContentDescription("画笔").performScrollTo().performClick()
        compose.onNodeWithContentDescription("线宽 16").performClick().assertIsSelected()
        val popup = compose.onNodeWithTag("markup-options-popup").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 68.dp.toPx() }, popup.height, 1f)
        assertEquals(with(compose.density) { 254.dp.toPx() }, popup.width, 1f)
        compose.onNodeWithText("16").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭工具选项").assertDoesNotExist()
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithContentDescription("线宽 16").assertDoesNotExist()
    }

    @Test fun narrowPaletteScrollsToMoreToolsAndStyleControls() {
        compose.setContent {
            MaterialTheme { Box(Modifier.width(300.dp)) { MarkupToolPalette(PockRefUiState(), {},
                onColorRequested = {}) } }
        }
        compose.onNodeWithContentDescription("形状和更多工具").performScrollTo().performClick()
        compose.onNodeWithContentDescription("选择").performScrollTo().performClick()
        compose.onNodeWithContentDescription("颜色").performScrollTo().performClick()
        compose.onNodeWithContentDescription("颜色").assertExists()
    }

    @Test fun colorButtonRequestsTheSharedPopup() {
        var requested = false
        compose.setContent {
            MaterialTheme { MarkupToolPalette(PockRefUiState(), {},
                onColorRequested = { requested = true }) }
        }
        compose.onNodeWithContentDescription("颜色").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test fun selectedObjectsDoNotAddABottomRow() {
        compose.setContent {
            MaterialTheme { MarkupToolPalette(PockRefUiState(canUndo = false, canRedo = false,
                selectedMarkupIds = setOf("text"), markupTexts = listOf(CanvasMarkupText(id = "text", text = "备注", position = androidx.compose.ui.geometry.Offset.Zero))), {}) }
        }
        compose.onNodeWithContentDescription("撤销").assertIsNotEnabled()
        compose.onNodeWithContentDescription("重做").assertIsNotEnabled()
        listOf("放大", "缩小", "旋转", "编辑文字", "删除", "复用").forEach {
            compose.onNodeWithContentDescription(it).assertDoesNotExist()
        }
        val dock = compose.onNodeWithTag("markup-tool-dock").fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithTag("markup-history-controls").fetchSemanticsNode().boundsInRoot
        assertEquals(history.height + with(compose.density) { 8.dp.toPx() }, dock.height, 1f)
    }

    @Test fun selectionActionsAreVerticalWithDeleteAboveReuse() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            MaterialTheme { MarkupSelectionActions(PockRefUiState(selectedMarkupIds = setOf("text")), actions::add) }
        }
        val delete = compose.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
        val reuse = compose.onNodeWithContentDescription("复用").fetchSemanticsNode().boundsInRoot
        assertEquals(delete.center.x, reuse.center.x, 1f)
        assertTrue(delete.bottom <= reuse.top)
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithContentDescription("复用").performClick()
        compose.runOnIdle {
            assertEquals(listOf(PockRefAction.DeleteSelectedMarkup, PockRefAction.DuplicateSelectedMarkup), actions)
        }
    }

    @Test fun selectionActionsAreHiddenWithoutSelection() {
        compose.setContent { MaterialTheme { MarkupSelectionActions(PockRefUiState(), {}) } }
        compose.onNodeWithTag("markup-selection-actions").assertDoesNotExist()
    }
}
