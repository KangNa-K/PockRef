package com.pockref.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.PracticeConfig
import com.pockref.app.ui.settings.SettingsPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PracticeConfigPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reusesSettingsFootprintWithoutModalWindow() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(600.dp, 600.dp)) {
                    SettingsPanel(open = true, onDismiss = {}, state = PockRefUiState(),
                        onAction = {}, onChooseDefaultSaveDirectory = {})
                    PracticeConfigSheet(true, 3, null, {}, {})
                }
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(
            compose.onNodeWithTag("settings_panel").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("practice_config_panel").fetchSemanticsNode().boundsInRoot,
        )
        compose.onNodeWithText("开始").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun narrowShortWindowWrapsChipsAndKeepsStartVisible() {
        var started: PracticeConfig? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 300.dp)) {
                    PracticeConfigSheet(true, 2, null, { started = it }, {}, topInset = 24.dp)
                }
            }
        }
        compose.onNodeWithText("开始").assertIsDisplayed()
        val countdownCustom = compose.onAllNodesWithText("自定义")[0]
        countdownCustom.performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("自定义倒计时").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("7")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("7m").performScrollTo().assertIsSelected()
        compose.onNodeWithText("切换图片时重置计时").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("开始").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(420_000L, started?.practiceDurationMs) }
    }

    @Test fun startPreservesLastConfigAndEmptySelectionIsDisabled() {
        val count = mutableStateOf(0)
        val config = PracticeConfig(420_000L, 120_000L, true, false)
        var started: PracticeConfig? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(600.dp, 600.dp)) {
                    PracticeConfigSheet(true, count.value, config, { started = it }, {})
                }
            }
        }
        compose.onNodeWithText("开始").assertIsNotEnabled()
        compose.runOnIdle { count.value = 3 }
        compose.onNodeWithText("开始").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(config, started) }
    }

    @Test fun closeAndBackDismissWithoutStartingAndReopenResetsEdits() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        var starts = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(600.dp, 600.dp)) {
                    PracticeConfigSheet(visible.value, 1, null, { starts++ }, {
                        dismissals++
                        visible.value = false
                    })
                }
            }
        }
        compose.onNodeWithText("10m").performScrollTo().performClick()
        compose.onNodeWithContentDescription("关闭速写练习窗口").performClick()
        compose.onNodeWithTag("practice_config_panel").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("10m").performScrollTo().assertIsNotSelected()
        pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("practice_config_panel").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, dismissals)
            assertEquals(0, starts)
        }
    }
}
