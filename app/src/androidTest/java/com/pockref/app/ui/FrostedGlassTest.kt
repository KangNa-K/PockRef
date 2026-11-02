package com.pockref.app.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.pockref.app.data.ThemeMode
import com.pockref.app.ui.theme.PockRefTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FrostedGlassTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Checker() {
        Canvas(Modifier.fillMaxSize().glassBackdropSource()) {
            drawRect(Color.Black)
            val stripe = 8.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawRect(Color.White, Offset(x, 0f), Size(stripe, size.height))
                x += stripe * 2
            }
        }
    }

    @Test fun materialReallyBlursBackdropWithoutBlurringForeground() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.size(320.dp)) {
                    Checker()
                    GlassSurface(Modifier.size(180.dp).align(Alignment.Center).testTag("glass")) {
                        Text("清晰文字", Modifier.padding(60.dp), color = Color.Black)
                    }
                }
            }
        }
        rule.waitForIdle()
        val pixels = rule.onNodeWithTag("glass").captureToImage().toPixelMap()
        val samples = (pixels.width / 4 until pixels.width * 3 / 4).map {
            pixels[it, pixels.height / 6].luminance()
        }
        assertTrue("Backdrop is blurred, not opaque: ${samples.average()}", samples.average() in 0.2..0.8)
        assertTrue("Fine stripes are diffused", samples.max() - samples.min() < 0.12f)
        rule.onNodeWithText("清晰文字").assertIsDisplayed()
    }

    @Test fun dialogUsesItsOwnWindowBackdropAndStillDismisses() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        var open by mutableStateOf(true)
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    Checker()
                    if (open) GlassAlertDialog(
                        onDismissRequest = { open = false },
                        title = { Text("毛玻璃弹窗") },
                        text = { Text("背景应模糊，文字保持清晰") },
                        modifier = Modifier.testTag("glass-dialog"),
                        confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
                    )
                }
            }
        }
        val image = rule.onNodeWithTag("glass-dialog").captureToImage().toPixelMap()
        val value = image[image.width / 2, image.height / 10].luminance()
        assertTrue("Dialog material must sample the activity, not an opaque fallback: $value", value in 0.2f..0.85f)
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithText("毛玻璃弹窗").assertDoesNotExist()
    }

    @Test fun materialTracksChangingBackdropInDarkTheme() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        var background by mutableStateOf(Color.Red)
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.size(280.dp)) {
                    Canvas(Modifier.fillMaxSize().glassBackdropSource()) { drawRect(background) }
                    GlassSurface(Modifier.size(120.dp).align(Alignment.Center).testTag("glass")) {}
                }
            }
        }
        fun center(): Color {
            val image = rule.onNodeWithTag("glass").captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        val red = center()
        assertTrue("Red backdrop shows through", red.red > red.blue + 0.3f)
        rule.runOnIdle { background = Color.Blue }
        val blue = center()
        assertTrue("Updated blue backdrop shows through", blue.blue > blue.red + 0.3f)
    }

    @Test fun popupSamplesBackdropAndKeepsMenuActions() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        var open by mutableStateOf(true)
        var clicked = false
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    Checker()
                    GlassDropdownMenu(open, { open = false }, Modifier.testTag("glass-menu")) {
                        DropdownMenuItem(text = { Text("执行操作") }, onClick = { clicked = true; open = false })
                    }
                }
            }
        }
        val pixels = rule.onNodeWithTag("glass-menu").captureToImage().toPixelMap()
        val value = pixels[pixels.width / 2, 3].luminance()
        assertTrue("Popup is frosted rather than opaque: $value", value in 0.2f..0.85f)
        rule.onNodeWithText("执行操作").performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun bottomSheetSamplesBackdropAndKeepsActions() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        var open by mutableStateOf(true)
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    Checker()
                    if (open) ModalBottomSheet(
                        onDismissRequest = { open = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        modifier = Modifier.testTag("glass-sheet")
                            .glassMaterial(MaterialTheme.shapes.extraLarge, GlassRole.Popup),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        Spacer(Modifier.height(40.dp))
                        TextButton(onClick = { open = false }) { Text("关闭面板") }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
        val pixels = rule.onNodeWithTag("glass-sheet").captureToImage().toPixelMap()
        val value = pixels[pixels.width / 2, pixels.height * 3 / 4].luminance()
        assertTrue("Bottom sheet is frosted rather than opaque: $value", value in 0.2f..0.85f)
        rule.onNodeWithText("关闭面板").performClick()
        rule.onNodeWithText("关闭面板").assertDoesNotExist()
    }


    @Test fun glassSwitchImmediatelyUpdatesMaterialAndNestedHosts() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        var enabled by mutableStateOf(true)
        rule.setContent {
            PockRefTheme(themeMode = ThemeMode.LIGHT, frostedGlassEnabled = enabled) {
                GlassBackdropHost {
                    Box(Modifier.size(280.dp)) {
                        Canvas(Modifier.fillMaxSize().glassBackdropSource()) { drawRect(Color.Red) }
                        GlassSurface(
                            Modifier.size(120.dp).align(Alignment.Center).testTag("glass"),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        ) {}
                    }
                }
            }
        }
        fun center(): Color {
            val image = rule.onNodeWithTag("glass").captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        assertTrue(center().let { it.red > it.blue + 0.3f })
        rule.runOnIdle { enabled = false }
        assertTrue("Disabled material is opaque", center().luminance() > 0.9f)
        rule.runOnIdle { enabled = true }
        assertTrue("Re-enabling restores the backdrop", center().let { it.red > it.blue + 0.3f })
    }

    @Test fun appearanceSwitchDispatchesAndReflectsSetting() {
        var state by mutableStateOf(com.pockref.app.model.PockRefUiState())
        rule.setContent {
            PockRefTheme(frostedGlassEnabled = state.frostedGlassEnabled) {
                com.pockref.app.ui.settings.SettingsAppearanceTab(state) { action ->
                    if (action is PockRefAction.SetFrostedGlassEnabled) {
                        state = state.copy(frostedGlassEnabled = action.enabled)
                    }
                }
            }
        }
        rule.onNodeWithText("毛玻璃效果").assertIsDisplayed()
        rule.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
        rule.runOnIdle { assertFalse(state.frostedGlassEnabled) }
        rule.onNode(isToggleable()).performClick().assertIsOn()
        rule.runOnIdle { assertTrue(state.frostedGlassEnabled) }
    }

}
