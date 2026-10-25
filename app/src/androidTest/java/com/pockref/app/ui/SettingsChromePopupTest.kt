package com.pockref.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SettingsTab
import com.pockref.app.ui.settings.SettingsPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsChromePopupTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun canvasSettingsButtonTogglesSharedPopupWithoutCloseIcon() {
        var state by mutableStateOf(PockRefUiState(blankCanvasOpen = true))
        composeRule.setContent {
            PockRefApp(state = state, onAction = { action ->
                if (action is PockRefAction.SetSettingsSheetOpen) {
                    state = state.copy(settingsSheetOpen = action.open)
                }
            })
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_panel").assertIsDisplayed()
        val toolbar = composeRule.onNodeWithTag("canvas-top-toolbar")
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithTag("settings_panel")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Settings must be wider than the toolbar", settings.width > toolbar.width)
        assertEquals(toolbar.top, settings.top, 1f)
        val actionButton = composeRule.onNodeWithContentDescription("一键排版")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(actionButton.bottom, settings.bottom, 1f)
        composeRule.onNodeWithContentDescription("关闭设置").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_panel").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_panel").assertIsDisplayed()
    }

    @Test
    fun settingsUsesFixedChromeSizeAndKeepsSizeAcrossTabsAndResize() {
        var state by mutableStateOf(PockRefUiState(settingsSheetOpen = true))
        var height by mutableStateOf(400.dp)
        composeRule.setContent {
            Box(Modifier.size(360.dp, height)) {
                SettingsPanel(
                    open = state.settingsSheetOpen,
                    onDismiss = { state = state.copy(settingsSheetOpen = false) },
                    state = state,
                    onAction = { action ->
                        if (action is PockRefAction.SetSettingsTab) {
                            state = state.copy(settingsTab = action.tab)
                        }
                    },
                    onChooseDefaultSaveDirectory = {},
                    panelWidth = 360.dp,
                    topInset = 24.dp,
                )
            }
        }
        val panel = composeRule.onNodeWithTag("settings_panel")
        panel.assertWidthIsEqualTo(256.dp).assertHeightIsEqualTo(352.dp)
        composeRule.onNodeWithText("外观").performClick()
        composeRule.runOnIdle { assertEquals(SettingsTab.APPEARANCE, state.settingsTab) }
        panel.assertHeightIsEqualTo(352.dp)
        composeRule.onNodeWithText("手势").performClick()
        composeRule.runOnIdle { assertEquals(SettingsTab.GESTURES, state.settingsTab) }
        panel.assertHeightIsEqualTo(352.dp)
        composeRule.runOnIdle { height = 300.dp }
        panel.assertHeightIsEqualTo(252.dp)
        composeRule.onNodeWithContentDescription("关闭设置").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(settingsSheetOpen = false) }
        composeRule.waitForIdle()
        panel.assertDoesNotExist()
    }

    @Test
    fun systemBackDismissesSettingsPopup() {
        var open by mutableStateOf(true)
        composeRule.setContent {
            Box(Modifier.size(360.dp, 400.dp)) {
                SettingsPanel(open, { open = false }, PockRefUiState(), {}, {})
            }
        }
        composeRule.onNodeWithTag("settings_panel").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_panel").assertDoesNotExist()
    }
}
