package com.pockref.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsThemeToggleTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactThemeOptionsSwitchInLightAppearance() = assertThemeOptions(false)

    @Test fun compactThemeOptionsSwitchInDarkAppearance() = assertThemeOptions(true)

    private fun assertThemeOptions(dark: Boolean) {
        var state by mutableStateOf(PockRefUiState(showBackgroundPicker = false))
        val requestedModes = mutableListOf<ThemeMode>()
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.size(width = 360.dp, height = 600.dp)) {
                    SettingsAppearanceTab(state) { action ->
                        if (action is PockRefAction.SetThemeMode) {
                            requestedModes += action.mode
                            state = state.copy(themeMode = action.mode)
                        }
                    }
                }
            }
        }
        // The reference control is compact, not the previous 56dp outlined row.
        val bounds = composeRule.onNodeWithTag("theme-mode-toggle").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Theme toggle should retain the compact pill height", height < 40.dp)
        listOf("浅色" to ThemeMode.LIGHT, "深色" to ThemeMode.DARK, "系统" to ThemeMode.SYSTEM)
            .forEach { (label, mode) ->
                composeRule.onNodeWithText(label).performClick()
                composeRule.runOnIdle { assertEquals(mode, state.themeMode) }
            }
        composeRule.runOnIdle {
            assertEquals(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), requestedModes)
        }
    }
}
