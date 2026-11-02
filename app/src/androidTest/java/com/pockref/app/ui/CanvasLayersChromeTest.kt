package com.pockref.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CanvasLayersChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun layerLauncherIsDirectlyAboveSettingsAndOpensExistingPanel() {
        composeRule.setContent {
            PockRefApp(
                state = PockRefUiState(images = listOf(image("reference", "reference", 1))),
                onAction = {},
            )
        }
        val launcher = composeRule.onNodeWithContentDescription("打开图层窗口")
        launcher.assertIsDisplayed()
        val layers = launcher.fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription("Settings")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(settings.left, layers.left, 1f)
        assertEquals(settings.right, layers.right, 1f)
        assertEquals(settings.top, layers.bottom, 1f)
        assertTrue(layers.left > composeRule.onNodeWithTag("canvas-top-toolbar")
            .fetchSemanticsNode().boundsInRoot.left)
        launcher.performClick()
        val panel = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "图层窗口"),
        )
        panel.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭图层窗口").assertDoesNotExist()
        launcher.assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        panel.assertDoesNotExist()
        launcher.performClick()
        panel.assertIsDisplayed()
        launcher.performClick()
        composeRule.waitForIdle()
        panel.assertDoesNotExist()
    }

    @Test
    fun panelHeightStaysFixedAsLayersChangeAndFitsSmallWindows() {
        val state = mutableStateOf(PockRefUiState(
            images = listOf(image("single", "single", 1)),
        ))
        val windowHeight = mutableStateOf(400.dp)
        composeRule.setContent {
            Box(Modifier.size(360.dp, windowHeight.value)) {
                CanvasLayersChrome(state.value, {}, null, topInset = 24.dp)
            }
        }
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        composeRule.waitForIdle()
        val panel = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "图层窗口"),
        )
        panel.assertHeightIsEqualTo(352.dp)
        composeRule.runOnIdle {
            state.value = state.value.copy(
                images = (1..30).map { image("layer-$it", "layer-$it", it) },
            )
        }
        panel.assertHeightIsEqualTo(352.dp)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(29)
        composeRule.onNodeWithContentDescription("图层 30：layer-1").assertIsDisplayed()
        composeRule.runOnIdle { windowHeight.value = 300.dp }
        panel.assertHeightIsEqualTo(252.dp)
        composeRule.onNodeWithContentDescription("关闭图层窗口").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                images = listOf(image("single", "single", 1)),
            )
        }
        panel.assertHeightIsEqualTo(252.dp)
    }

    @Test
    fun layerPanelMatchesSettingsWithFloatingWindowButton() {
        assertLayerPanelMatchesSettings(showFloatingWindowButton = true)
    }

    @Test
    fun layerPanelMatchesSettingsWithoutFloatingWindowButton() {
        assertLayerPanelMatchesSettings(showFloatingWindowButton = false)
    }

    @Test
    fun returningHomeClosesSettingsAndDoesNotReopenLayersOnNextScene() {
        val scene = PockRefUiState(images = listOf(image("reference", "reference", 1)))
        val state = mutableStateOf(scene)
        composeRule.setContent {
            PockRefApp(state = state.value, onAction = { action ->
                if (action is PockRefAction.SetSettingsSheetOpen) {
                    state.value = state.value.copy(settingsSheetOpen = action.open)
                }
            })
        }
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        val layers = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "图层窗口"),
        )
        layers.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_panel").assertIsDisplayed()

        composeRule.runOnIdle { state.value = state.value.copy(images = emptyList()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_panel").assertDoesNotExist()
        layers.assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(!state.value.settingsSheetOpen) }

        composeRule.runOnIdle { state.value = state.value.copy(images = scene.images) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_panel").assertDoesNotExist()
        layers.assertDoesNotExist()
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        layers.assertIsDisplayed()
    }

    @Test
    fun cancellingSceneCloseKeepsSettingsOpenButDiscardingClosesIt() {
        val store = ViewModelStore()
        composeRule.runOnIdle {
            val application = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as Application
            val vm = PockRefViewModel(application)
            store.put("close-scene", vm)
            try {
                vm._uiState.value = PockRefUiState(
                    images = listOf(image("reference", "reference", 1)),
                    settingsSheetOpen = true,
                )
                vm.onAction(PockRefAction.CloseScene)
                assertTrue(vm.uiState.value.showUnsavedChangesDialog)
                vm.onAction(PockRefAction.UnsavedDialogCancel)
                assertTrue(vm.uiState.value.settingsSheetOpen)

                vm.onAction(PockRefAction.CloseScene)
                vm.onAction(PockRefAction.UnsavedDialogDiscard)
                assertTrue(vm.uiState.value.images.isEmpty())
                assertTrue(!vm.uiState.value.settingsSheetOpen)
            } finally {
                store.clear()
            }
        }
    }

    private fun assertLayerPanelMatchesSettings(showFloatingWindowButton: Boolean) {
        val state = mutableStateOf(PockRefUiState(
            images = listOf(image("reference", "reference", 1)),
        ))
        composeRule.setContent {
            Box(Modifier.size(360.dp, 600.dp)) {
                PockRefApp(
                    state = state.value,
                    onAction = { action ->
                        if (action is PockRefAction.SetSettingsSheetOpen) {
                            state.value = state.value.copy(settingsSheetOpen = action.open)
                        }
                    },
                    showFloatingWindowButton = showFloatingWindowButton,
                )
            }
        }
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        composeRule.waitForIdle()
        val toolbar = composeRule.onNodeWithTag("canvas-top-toolbar")
            .fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "图层窗口"),
        )
            .fetchSemanticsNode().boundsInRoot
        assertEquals(toolbar.top, panel.top, 1f)
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        val settings = composeRule.onNodeWithTag("settings_panel")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(settings, panel)
    }

    @Test
    fun doubleClickSelectsAndTogglesFocusForTheTappedLayer() {
        val actions = mutableListOf<PockRefAction>()
        val state = mutableStateOf(PockRefUiState(
            images = listOf(
                image(id = "lower", name = "底层", zIndex = 1),
                image(id = "upper", name = "顶层", zIndex = 8),
            ),
        ))
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) { CanvasLayersChrome(state.value, actions::add, null) }
        }
        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        val row = composeRule.onNodeWithContentDescription("图层 2：底层")
        row.performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.SelectImage("lower"),
                PockRefAction.FitImageToScreen("lower")), actions)
            actions.clear()
            state.value = state.value.copy(fittedImageId = "lower")
        }
        row.performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.SelectImage("lower"),
                PockRefAction.FitImageToScreen("lower")), actions)
            actions.clear()
            state.value = state.value.copy(fittedImageId = null)
        }
        row.performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.SelectImage("lower"),
                PockRefAction.FitImageToScreen("lower")), actions)
            actions.clear()
            state.value = state.value.copy(fittedImageId = "lower")
        }
        composeRule.onNodeWithContentDescription("图层 1：顶层")
            .performTouchInput { doubleClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.SelectImage("upper"),
                PockRefAction.FitImageToScreen("upper")), actions)
        }
        composeRule.onNodeWithContentDescription("关闭图层窗口").assertDoesNotExist()
    }

    @Test
    fun repeatedLayerDoubleClicksRestoreTheOriginalViewport() {
        val initial = PockRefUiState(
            images = listOf(image("reference", "参考图", 1)),
            canvasViewportSize = Size2D(1200f, 800f),
            canvasScale = 1.25f,
            canvasOffset = Offset(120f, -80f),
        )
        val store = ViewModelStore()
        lateinit var vm: PockRefViewModel
        composeRule.runOnIdle {
            val application = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as Application
            vm = PockRefViewModel(application)
            store.put("layer-focus", vm)
            vm._uiState.value = initial
        }
        try {
            composeRule.setContent {
                val state = vm.uiState.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    CanvasLayersChrome(state.value, vm::onAction, null)
                }
            }
            composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
            val row = composeRule.onNodeWithContentDescription("图层 1：参考图")
            repeat(2) {
                row.performTouchInput { doubleClick() }
                composeRule.runOnIdle {
                    assertEquals("reference", vm.uiState.value.fittedImageId)
                    assertEquals(8f, vm.uiState.value.canvasScale, 0f)
                    assertEquals(0f, vm.uiState.value.canvasOffset.x, 0f)
                    assertEquals(0f, vm.uiState.value.canvasOffset.y, 0f)
                }
                row.performTouchInput { doubleClick() }
                composeRule.runOnIdle {
                    assertNull(vm.uiState.value.fittedImageId)
                    assertEquals(initial.canvasScale, vm.uiState.value.canvasScale, 0f)
                    assertEquals(initial.canvasOffset, vm.uiState.value.canvasOffset)
                    assertEquals(initial.images, vm.uiState.value.images)
                }
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
        }
    }

    @Test
    fun selectingImagesDoesNotMoveLayerButtonOrOpenPanel() {
        val state = mutableStateOf(PockRefUiState(
            images = listOf(image(id = "reference", name = "reference", zIndex = 1)),
        ))
        composeRule.setContent {
            Box(Modifier.size(600.dp, 600.dp)) {
                CanvasLayersChrome(state.value, {}, null)
            }
        }
        val button = composeRule.onNodeWithContentDescription("打开图层窗口")
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedImageIds = setOf("reference"))
        }
        composeRule.waitForIdle()
        assertEquals(buttonBounds, button.fetchSemanticsNode().boundsInRoot)
        button.performClick()
        button.assertIsDisplayed()
        val panel = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "图层窗口"),
        )
        panel.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭图层窗口").assertDoesNotExist()
        val panelBounds = panel.fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedImageIds = emptySet())
        }
        composeRule.waitForIdle()
        assertEquals(panelBounds, panel.fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle {
            state.value = state.value.copy(selectedImageIds = setOf("reference"))
        }
        composeRule.waitForIdle()
        assertEquals(panelBounds, panel.fetchSemanticsNode().boundsInRoot)
        assertEquals(buttonBounds, button.fetchSemanticsNode().boundsInRoot)
        button.performClick()
        composeRule.waitForIdle()
        panel.assertDoesNotExist()
        button.performClick()
        panel.assertIsDisplayed()
    }

    @Test
    fun opensOrderedLayerListAndSelectingLayerDispatchesSelection() {
        val actions = mutableListOf<PockRefAction>()
        val state = PockRefUiState(
            images = listOf(
                image(id = "lower", name = "底层", zIndex = 1),
                image(id = "upper", name = "顶层", zIndex = 8),
            ),
        )

        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                CanvasLayersChrome(
                    state = state,
                    onAction = actions::add,
                    imageDecoder = null,
                )
            }
        }

        composeRule.onNodeWithContentDescription("打开图层窗口").performClick()
        composeRule.onNodeWithContentDescription("图层 1：顶层").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("图层 2：底层").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("图层 1：顶层").performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.SelectImage("upper")), actions)
        }
    }

    private fun image(id: String, name: String, zIndex: Int) = SceneImage(
        id = id,
        name = name,
        mimeType = "image/png",
        intrinsicWidth = 100,
        intrinsicHeight = 100,
        center = Offset.Zero,
        displaySize = Size2D(100f, 100f),
        zIndex = zIndex,
    )
}
