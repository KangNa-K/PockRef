package com.pockref.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.pockref.app.model.SettingsTab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsTabRowTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun tabsKeepSelectionSemanticsAndTouchTargets() {
        var selected by mutableStateOf(SettingsTab.GENERAL)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(256.dp)) {
                    SettingsTabRow(selected, { selected = it })
                }
            }
        }
        composeRule.onNodeWithTag("settings_tab_general").assertIsSelected()
        listOf("appearance", "gestures", "general").forEach { tab ->
            composeRule.onNodeWithTag("settings_tab_$tab")
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                .performClick().assertIsSelected()
        }
        composeRule.onNodeWithTag("settings_tab_appearance").assertIsNotSelected()
        composeRule.onNodeWithTag("settings_tab_gestures").assertIsNotSelected()
    }

    @Test fun unselectedTabsPreserveBackdropInLightTheme() = assertBackdropVisible(false)

    @Test fun unselectedTabsPreserveBackdropInDarkTheme() = assertBackdropVisible(true)

    private fun assertBackdropVisible(dark: Boolean) {
        val backdrop = Color(0xFFB45B9A)
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(256.dp).background(backdrop)) {
                    SettingsTabRow(SettingsTab.GENERAL, {})
                }
            }
        }
        val pixels = composeRule.onNodeWithTag("settings_tab_gestures")
            .captureToImage().toPixelMap()
        val sample = pixels[pixels.width / 2, 2]
        assertTrue("The unselected tab must not paint an opaque surface",
            kotlin.math.abs(sample.red - backdrop.red) < 0.08f &&
                kotlin.math.abs(sample.green - backdrop.green) < 0.08f &&
                kotlin.math.abs(sample.blue - backdrop.blue) < 0.08f)
    }
}
