package com.pockref.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pockref.app.model.SceneGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SelectionBottomSheetTest {
    @get:Rule val composeRule = createComposeRule()

    private val image = SceneImage(
        id = "reference", name = "reference", mimeType = "image/png",
        intrinsicWidth = 100, intrinsicHeight = 100,
        center = Offset.Zero, displaySize = Size2D(100f, 100f),
    )
    private val selectedState = PockRefUiState(
        images = listOf(image), selectedImageId = image.id,
        selectedImageIds = setOf(image.id),
    )

    @Test
    fun canvasToolbarBottomMatchesRightActionPanel() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    PockRefApp(state = selectedState, onAction = {})
                }
            }
        }
        val toolbar = composeRule.onNodeWithTag("selection-action-toolbar")
            .fetchSemanticsNode().boundsInRoot
        val canvas = composeRule.onRoot()
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Tablet bottom toolbar must be centered in the canvas",
            canvas.center.x, toolbar.center.x, 0.5f)
        listOf("一键排版").forEach { description ->
            val button = composeRule.onNodeWithContentDescription(description)
            button.assertIsDisplayed()
            assertEquals(description, button.fetchSemanticsNode().boundsInRoot.bottom,
                toolbar.bottom, 0.5f)
        }
    }

    @Test
    fun toolbarHeightMatchesLayerButtonAcrossSelectionStates() {
        val state = mutableStateOf(selectedState)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CanvasLayersChrome(state.value, {}, null)
                    SelectionBottomSheet(
                        state.value, {}, mutableStateMapOf(), mutableStateMapOf(), null,
                    )
                }
            }
        }
        val states = listOf(
            selectedState,
            selectedState.copy(multiSelectMode = true),
            selectedState.copy(boxSelectMode = true),
            selectedState.copy(images = listOf(image.copy(locked = true))),
            selectedState.copy(
                posterizeControlsVisible = true,
                posterizeTargetImageIds = selectedState.selectedImageIds,
            ),
        )
        states.forEach { selection ->
            composeRule.runOnIdle { state.value = selection }
            val toolbar = composeRule.onNodeWithTag("selection-action-toolbar")
            val layer = composeRule.onNodeWithContentDescription("打开图层窗口")
            toolbar.assertHeightIsEqualTo(Dimens.BottomPanelChromeSize)
            layer.assertHeightIsEqualTo(Dimens.BottomPanelChromeSize)
            assertEquals(
                layer.fetchSemanticsNode().boundsInRoot.height,
                toolbar.fetchSemanticsNode().boundsInRoot.height,
                0.5f,
            )
            assertEquals(
                "Toolbar bottom must align with the corner layer button",
                layer.fetchSemanticsNode().boundsInRoot.bottom,
                toolbar.fetchSemanticsNode().boundsInRoot.bottom,
                0.5f,
            )
        }
    }

    @Test
    fun toolbarHasOnlyIconsAndCapsuleIsLeftAlignedAboveIt() {
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                SelectionBottomSheet(
                    selectedState, actions::add, mutableStateMapOf(), mutableStateMapOf(), null,
                )
                }
            }
        }
        composeRule.onNodeWithText("已选 1 张").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("隐藏选区工具栏").assertDoesNotExist()
        listOf("水平翻转", "灰度", "锁定", "删除").forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("水平翻转").performScrollTo()
        val single = composeRule.onNodeWithText("单选").fetchSemanticsNode().boundsInRoot
        val multi = composeRule.onNodeWithText("多选").fetchSemanticsNode().boundsInRoot
        val box = composeRule.onNodeWithText("框选").fetchSemanticsNode().boundsInRoot
        val flip = composeRule.onNodeWithContentDescription("水平翻转").fetchSemanticsNode().boundsInRoot
        assertTrue(listOf(single, multi, box).all { it.bottom < flip.top })
        val toolbar = composeRule.onNodeWithTag("selection-action-toolbar")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(toolbar.left, composeRule.onNodeWithTag("selection-mode-toggle")
            .fetchSemanticsNode().boundsInRoot.left, 0.5f)
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir, "selection-toolbar-test.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        listOf("水平翻转", "灰度", "锁定", "删除").forEach {
            composeRule.onNodeWithContentDescription(it).performScrollTo().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(PockRefAction.FlipHorizontal, PockRefAction.ConvertToGrayscale,
                    PockRefAction.ToggleLockSelected, PockRefAction.DeleteSelected),
                actions,
            )
        }
        composeRule.onNodeWithContentDescription("成组").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun wideToolbarShowsEveryActionAndScrollsOnResize() {
        val width = mutableStateOf(1000.dp)
        val state = mutableStateOf(selectedState)
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.width(width.value).fillMaxSize()) {
                        SelectionBottomSheet(
                            state.value, actions::add, mutableStateMapOf(), mutableStateMapOf(), null,
                        )
                    }
                }
            }
        }
        val singleLabels = listOf("成组", "水平翻转", "裁切", "右旋 90°", "复原",
            "复制", "锁定", "灰度", "设为封面", "导出", "删除")
        fun assertInline(labels: List<String>) {
            composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
            val toolbar = composeRule.onNodeWithTag("selection-action-toolbar")
            toolbar.assertHeightIsEqualTo(Dimens.BottomPanelChromeSize)
            val bounds = toolbar.fetchSemanticsNode().boundsInRoot
            assertEquals("Tablet toolbar must stay centered as the action count changes",
                width.value.value / 2f, bounds.center.x, 0.5f)
            assertEquals("Selection modes must stay centered above the tablet toolbar",
                bounds.center.x, composeRule.onNodeWithTag("selection-mode-toggle")
                    .fetchSemanticsNode().boundsInRoot.center.x, 0.5f)
            var previousRight = bounds.left
            labels.forEach { label ->
                val button = composeRule.onNodeWithContentDescription(label)
                button.assertIsDisplayed()
                val rect = button.fetchSemanticsNode().boundsInRoot
                assertTrue("$label must fit in toolbar", rect.left >= bounds.left && rect.right <= bounds.right)
                assertEquals(bounds.center.y, rect.center.y, 0.5f)
                assertTrue("$label must follow the previous action", rect.left >= previousRight)
                previousRight = rect.right
            }
        }
        assertInline(singleLabels)
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir, "selection-toolbar-wide-test.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        singleLabels.forEach { composeRule.onNodeWithContentDescription(it).performClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.CreateGroup, PockRefAction.FlipHorizontal,
                PockRefAction.EnterCropMode, PockRefAction.RotateRight, PockRefAction.ResetTransform,
                PockRefAction.DuplicateSelected, PockRefAction.ToggleLockSelected,
                PockRefAction.ConvertToGrayscale, PockRefAction.SetAsCover(image.id),
                PockRefAction.ExportSelected, PockRefAction.DeleteSelected), actions)
            width.value = 400.dp
        }
        composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("删除").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("裁切").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("成组").performScrollTo()
        composeRule.runOnIdle { width.value = 1000.dp }
        assertInline(singleLabels)
        composeRule.onNodeWithText("裁切").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = selectedState.copy(posterizeControlsVisible = true,
                posterizeTargetImageIds = selectedState.selectedImageIds)
        }
        assertInline(singleLabels)
        listOf("2级", "4级", "8级", "16级").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        val second = image.copy(id = "second")
        listOf(false, true).forEach { boxMode ->
            composeRule.runOnIdle {
                state.value = selectedState.copy(images = listOf(image, second),
                    selectedImageIds = setOf(image.id, second.id),
                    multiSelectMode = !boxMode, boxSelectMode = boxMode)
            }
            val labels = listOf("成组", "水平翻转", "向上对齐", "向下对齐", "向左对齐", "向右对齐",
                "复制", "锁定", "灰度", "速写练习", "导出", "删除")
            assertInline(labels)
            composeRule.onNodeWithContentDescription("一键排版").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("向上对齐").performClick()
            composeRule.onNodeWithContentDescription("速写练习").performClick()
        }
        composeRule.runOnIdle {
            state.value = selectedState.copy(images = listOf(image.copy(locked = true)))
        }
        assertInline(listOf("翻转", "解锁", "灰度", "删除", "导出"))
        composeRule.runOnIdle {
            state.value = selectedState.copy(groups = listOf(SceneGroup(id = "group", imageIds = listOf(image.id))))
        }
        assertInline(listOf("解组", "重命名", "外观") + singleLabels.filter { it != "成组" })
        listOf("复制组", "锁定组").forEach {
            composeRule.onNodeWithContentDescription(it).assertDoesNotExist()
        }
        composeRule.onNodeWithContentDescription("重命名").performClick()
        composeRule.onNodeWithText("重命名组").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            state.value = selectedState.copy(
                images = listOf(image, second),
                selectedImageIds = setOf(image.id, second.id),
                multiSelectMode = true,
                groups = listOf(SceneGroup(id = "group", imageIds = listOf(image.id, second.id))),
            )
        }
        assertInline(listOf("解组", "重命名", "外观", "水平翻转",
            "向上对齐", "向下对齐", "向左对齐", "向右对齐", "复制", "锁定",
            "灰度", "速写练习", "导出", "删除"))
        listOf("复制组", "锁定组", "一键排版").forEach {
            composeRule.onNodeWithContentDescription(it).assertDoesNotExist()
        }
        composeRule.onNodeWithContentDescription("灰度").performClick()
        composeRule.onNodeWithContentDescription("速写练习").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.ConvertToGrayscale,
                PockRefAction.OpenPracticeConfigSheet), actions.takeLast(2))
            width.value = 400.dp
        }
        composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("导出").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("解组").performScrollTo()
        listOf("复制组", "锁定组", "一键排版").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
        composeRule.runOnIdle { width.value = 1000.dp }
        composeRule.runOnIdle { state.value = selectedState.copy(requiresGroupFlattening = true) }
        assertInline(listOf("导出"))
        composeRule.onNodeWithContentDescription("删除").assertDoesNotExist()
    }

    @Test
    fun posterizeLevelsSitAboveModesAndStayVisibleWhileActionsScroll() {
        val state = mutableStateOf(selectedState.copy(
            posterizeControlsVisible = true,
            posterizeTargetImageIds = selectedState.selectedImageIds,
        ))
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp).fillMaxSize()) {
                    SelectionBottomSheet(
                        state.value, actions::add, mutableStateMapOf(), mutableStateMapOf(), null,
                    )
                }
            }
        }
        val second = image.copy(id = "second")
        val levels = listOf(2, 4, 8, 16)
        listOf(
            state.value,
            state.value.copy(images = listOf(image, second),
                selectedImageIds = setOf(image.id, second.id),
                posterizeTargetImageIds = setOf(image.id, second.id), multiSelectMode = true),
            state.value.copy(images = listOf(image, second),
                selectedImageIds = setOf(image.id, second.id),
                posterizeTargetImageIds = setOf(image.id, second.id), boxSelectMode = true),
        ).forEach { selection ->
            composeRule.runOnIdle { state.value = selection }
            val presets = composeRule.onNodeWithTag("selection-posterize-levels")
                .fetchSemanticsNode().boundsInRoot
            val modes = composeRule.onNodeWithTag("selection-mode-toggle")
                .fetchSemanticsNode().boundsInRoot
            val toolbar = composeRule.onNodeWithTag("selection-action-toolbar")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Posterize presets must sit above selection modes", presets.bottom < modes.top)
            assertTrue("Selection modes must sit above action toolbar", modes.bottom < toolbar.top)
            assertEquals(toolbar.left, presets.left, 0.5f)
            composeRule.onNodeWithContentDescription("删除").performScrollTo().assertIsDisplayed()
            levels.forEach { level ->
                composeRule.onNodeWithText("${level}级").assertIsDisplayed().performClick()
            }
        }
        composeRule.runOnIdle {
            assertEquals(List(3) { levels.map { PockRefAction.TogglePosterizeLevel(it) } }.flatten(), actions)
            state.value = selectedState
        }
        composeRule.onNodeWithTag("selection-posterize-levels").assertDoesNotExist()
        composeRule.onNodeWithText("单选").assertIsDisplayed()
    }

    @Test
    fun lockedToolbarHidesStatusTextAndScrollsToExport() {
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp).fillMaxSize()) {
                    SelectionBottomSheet(
                        selectedState.copy(images = listOf(image.copy(locked = true))),
                        actions::add, mutableStateMapOf(), mutableStateMapOf(), null,
                    )
                }
            }
        }
        composeRule.onNodeWithText("锁定").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
        composeRule.onNodeWithTag("selection-action-toolbar").performTouchInput { swipeLeft() }
        composeRule.onNodeWithContentDescription("导出").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("解锁").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.ExportSelected, PockRefAction.ToggleLockSelected), actions)
        }
    }

    @Test
    fun detachedCapsuleSwitchesModesAndSelectionControlsVisibility() {
        val state = mutableStateOf(selectedState)
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            MaterialTheme {
                SelectionBottomSheet(
                    state.value, actions::add, mutableStateMapOf(), mutableStateMapOf(), null,
                )
            }
        }
        composeRule.onNodeWithText("多选").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.ToggleMultiSelectMode), actions)
            actions.clear()
            state.value = selectedState.copy(multiSelectMode = true)
        }
        composeRule.onNodeWithText("多选").assertIsDisplayed()
        composeRule.onNodeWithText("框选").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.ToggleBoxSelectMode), actions)
            actions.clear()
            state.value = selectedState.copy(multiSelectMode = true, boxSelectMode = true)
        }
        composeRule.onNodeWithText("框选").assertIsDisplayed()
        composeRule.onNodeWithText("单选").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(PockRefAction.ToggleBoxSelectMode, PockRefAction.ToggleMultiSelectMode), actions)
            state.value = PockRefUiState()
        }
        composeRule.onNodeWithText("单选").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("翻转").assertDoesNotExist()
    }
}
