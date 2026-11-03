package com.pockref.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.ColorSample
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ColorSamplerPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun copyDismissesPanelAndCopiesHex() {
        var sample by mutableStateOf<ColorSample?>(
            ColorSample("image", Offset.Zero, 0, 0, 0xff408020.toInt(), 100f, 0.75f, 0.5f, 1f, "#408020"),
        )
        compose.setContent {
            MaterialTheme {
                Box {
                    ColorSamplerOverlay(sample, lens = null, bitmap = null, onClose = { sample = null })
                }
            }
        }

        compose.onNodeWithTag("color-sampler-copy").performClick()
        compose.onNodeWithTag("color-sampler-panel").assertDoesNotExist()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("#408020", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertEquals(null, sample)
        }
    }

    @Test fun releasingPickerRemovesLensButKeepsPanel() {
        val image = SceneImage(
            id = "image", name = "image", mimeType = "image/png", intrinsicWidth = 100,
            intrinsicHeight = 100, center = Offset.Zero, displaySize = Size2D(100f, 100f),
        )
        val sample = ColorSample("image", Offset(100f, 100f), 0, 0, 0xff408020.toInt(), 100f, 0.75f, 0.5f, 1f, "#408020")
        var lens by mutableStateOf<ColorSamplerLens?>(
            ColorSamplerLens(image, sample.screenPosition,
                ColorSamplerMath.PreviewGeometry(Offset(100f, 100f), 100f, 100f), false),
        )
        val bitmap = ImageBitmap(100, 100)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorSamplerOverlay(sample, lens, bitmap, onClose = {})
                }
            }
        }

        compose.onNodeWithTag("color-sampler-lens").assertExists()
        compose.runOnIdle { lens = null }
        compose.onNodeWithTag("color-sampler-lens").assertDoesNotExist()
        compose.onNodeWithTag("color-sampler-panel").assertExists()
    }

    @Test fun hoverPanelMatchesSelectionPanelBoundsAndRemainsClickable() {
        val image = SceneImage(
            id = "image", name = "image", mimeType = "image/png", intrinsicWidth = 100,
            intrinsicHeight = 100, center = Offset.Zero, displaySize = Size2D(100f, 100f),
        )
        val sample = ColorSample("image", Offset.Zero, 0, 0, 0xff408020.toInt(), 100f, 0.75f, 0.5f, 1f, "#408020")
        val bounds = mutableStateOf<Rect?>(null)
        val aboveHover = mutableStateOf(false)
        var overlayOrigin by mutableStateOf(Offset.Zero)
        var dismissed = false
        val state = PockRefUiState(images = listOf(image), selectedImageId = image.id,
            selectedImageIds = setOf(image.id), colorSample = sample)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInWindow() }) {
                    SelectionBottomSheet(
                        state, {}, remember { mutableStateMapOf() }, remember { mutableStateMapOf() }, null,
                        colorPanelBounds = bounds, colorPanelAboveHover = aboveHover.value,
                    )
                    if (aboveHover.value) {
                        ColorSamplerOverlay(sample, lens = null, bitmap = null,
                            panelBounds = bounds.value?.translate(-overlayOrigin),
                            onClose = { dismissed = true })
                    }
                }
            }
        }
        val normal = compose.onNodeWithTag("color-sampler-panel").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("selection-mode-toggle").fetchSemanticsNode().boundsInRoot
        assertTrue(normal.bottom < toggle.top)
        compose.runOnIdle { aboveHover.value = true }
        val hover = compose.onNodeWithTag("color-sampler-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(normal.left, hover.left, 1f)
        assertEquals(normal.top, hover.top, 1f)
        assertEquals(normal.width, hover.width, 1f)
        assertEquals(normal.height, hover.height, 1f)
        compose.onNodeWithTag("color-sampler-close").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
