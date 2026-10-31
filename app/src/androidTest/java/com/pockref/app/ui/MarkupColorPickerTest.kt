package com.pockref.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.settings.SettingsPanel
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkupColorPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun spectrumHexAndOpacityApplyImmediatelyAndCloseDoesNotReapply() {
        val colors = mutableListOf<Color>()
        var closed = false
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) { MarkupColorPicker(Color.Red, colors::add, { closed = true }) } } }
        screenshot("markup-color-grid")
        compose.onNodeWithText("光谱").performClick()
        compose.onNodeWithContentDescription("颜色光谱").performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(colors.isNotEmpty()) }
        screenshot("markup-color-spectrum")
        compose.onNodeWithText("滑块").performScrollTo().performClick()
        compose.onNodeWithContentDescription("十六进制颜色").performScrollTo().performTextReplacement("FED030")
        compose.onNodeWithContentDescription("十六进制颜色").performImeAction()
        compose.onNodeWithContentDescription("不透明度").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        compose.onNodeWithText("80%").assertExists()
        screenshot("markup-color-sliders")
        var countBeforeClose = 0
        compose.runOnIdle {
            assertEquals("FED030", markupHex(colors.last()))
            assertEquals(0.8f, colors.last().alpha, 0.005f)
            countBeforeClose = colors.size
        }
        compose.onNodeWithContentDescription("关闭颜色面板").performClick()
        compose.runOnIdle {
            assertTrue(closed)
            assertEquals(countBeforeClose, colors.size)
        }
    }

    @Test fun gridAndSavedSwatchWorkAndInvalidHexDoesNotChangeColor() {
        val colors = mutableListOf<Color>()
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) { MarkupColorPicker(Color.Red, colors::add, {}) } } }
        compose.onNodeWithContentDescription("网格颜色 1-1").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("保存当前颜色").performScrollTo().performClick()
        compose.onNodeWithContentDescription("已存颜色 FFFFFF").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Color.White, colors.single()) }
        compose.onNodeWithText("滑块").performScrollTo().performClick()
        compose.onNodeWithContentDescription("十六进制颜色").performScrollTo().performTextReplacement("NOTHEX")
        compose.onNodeWithContentDescription("关闭颜色面板").performClick()
        compose.runOnIdle { assertEquals(Color.White, colors.single()) }
    }

    @Test fun colorPanelReusesSettingsPopupFootprintAndIsNotAModalDialog() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsPanel(open = true, onDismiss = {}, state = PockRefUiState(),
                        onAction = {}, onChooseDefaultSaveDirectory = {})
                    MarkupColorPicker(Color.Red, {}, {})
                }
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        val settings = compose.onNodeWithTag("settings_panel").fetchSemanticsNode().boundsInRoot
        val colors = compose.onNodeWithTag("markup-color-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(settings, colors)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
