package com.pockref.app.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MarkupColorDrawingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gridSelectionChangesNextStrokeWhilePanelStaysOpen() = session { vm ->
        compose.onNodeWithContentDescription("颜色").performScrollTo().performClick()
        compose.onNodeWithContentDescription("网格颜色 1-1").performClick()
        compose.runOnIdle { assertEquals(Color.White, vm._uiState.value.markupColor) }
        drawOnCanvas()
        compose.onNodeWithTag("markup-color-panel").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(Color.White, vm._uiState.value.markupStrokes.single().color)
        }
        compose.onNodeWithContentDescription("网格颜色 1-12").performClick()
        drawOnCanvas()
        compose.onNodeWithContentDescription("关闭颜色面板").performClick()
        compose.runOnIdle {
            assertEquals(listOf(Color.White, Color.Black), vm._uiState.value.markupStrokes.map { it.color })
            vm.undoMarkupStep()
            assertEquals(Color.White, vm._uiState.value.markupStrokes.single().color)
        }
    }

    @Test fun hexAndOpacityChangeNextStrokeWithoutClosingPanel() = session { vm ->
        compose.onNodeWithContentDescription("颜色").performScrollTo().performClick()
        compose.onNodeWithText("滑块").performScrollTo().performClick()
        compose.onNodeWithContentDescription("十六进制颜色").performScrollTo().performTextReplacement("20A0E0")
        compose.onNodeWithContentDescription("十六进制颜色").performImeAction()
        compose.onNodeWithContentDescription("不透明度").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
        drawOnCanvas()
        compose.runOnIdle {
            val color = vm._uiState.value.markupStrokes.single().color
            assertEquals("20A0E0", markupHex(color))
            assertEquals(0.5f, color.alpha, 0.005f)
        }
        compose.onNodeWithTag("markup-color-panel").assertIsDisplayed()
    }

    private fun drawOnCanvas() {
        compose.onRoot().performTouchInput {
            val start = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.45f)
            down(start); moveTo(start + androidx.compose.ui.geometry.Offset(40f, 30f)); up()
        }
        compose.waitForIdle()
    }

    private fun session(test: (PockRefViewModel) -> Unit) {
        val store = ViewModelStore()
        lateinit var vm: PockRefViewModel
        compose.runOnIdle {
            vm = PockRefViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
            store.put("color-drawing-test", vm)
            vm._uiState.value = com.pockref.app.model.PockRefUiState(blankCanvasOpen = true)
            vm.setMarkupModeActive(true)
        }
        try {
            compose.setContent { PockRefApp(state = vm.uiState.collectAsState().value, onAction = vm::onAction) }
            test(vm)
        } finally {
            compose.runOnIdle { store.clear(); vm.codec.release() }
        }
    }
}
