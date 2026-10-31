package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pockref.app.model.CanvasMarkupTool
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkupCanvasUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun markupTopControlsOnlyCancelOrCompleteTheSession() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            PockRefApp(state = PockRefUiState(blankCanvasOpen = true, markupModeActive = true),
                onAction = actions::add)
        }
        compose.onNodeWithTag("canvas-top-toolbar").assertDoesNotExist()
        compose.onNodeWithText("标记").assertDoesNotExist()
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.runOnIdle { actions.clear() }
        compose.onNodeWithContentDescription("不保存退出标记").performClick()
        compose.onNodeWithContentDescription("完成标记").performClick()
        compose.runOnIdle {
            assertEquals(listOf(PockRefAction.CancelMarkupSession,
                PockRefAction.SetMarkupModeActive(false)), actions)
        }
    }

    @Test fun colorButtonOpensAndClosesTheSettingsStylePopup() {
        compose.setContent {
            PockRefApp(state = PockRefUiState(blankCanvasOpen = true, markupModeActive = true), onAction = {})
        }
        compose.onNodeWithContentDescription("颜色").performScrollTo().performClick()
        compose.onNodeWithTag("markup-color-panel").assertIsDisplayed()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("网格").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭颜色面板").performClick()
        compose.onNodeWithTag("markup-color-panel").assertDoesNotExist()
    }

    @Test fun populatedSelectionRendersWithExistingChrome() {
        compose.setContent {
            PockRefApp(state=PockRefUiState(blankCanvasOpen=true,markupModeActive=true,markupVisible=true,
                markupTool=CanvasMarkupTool.SELECT,selectedMarkupIds=setOf("shape","text"),
                markupStrokes=listOf(
                    CanvasMarkupStroke(id="shape",kind="rectangle",color=Color.Blue,
                        points=listOf(Offset(-140f,-90f),Offset(80f,50f)),rotation=0.2f),
                    CanvasMarkupStroke(id="ink",color=Color.Red,lineWidth=8f,
                        points=listOf(Offset(-120f,100f),Offset(0f,70f),Offset(120f,120f)))),
                markupTexts=listOf(CanvasMarkupText(id="text",text="双端可编辑",
                    position=Offset(130f,-50f),fontSize=24f))),onAction={})
        }
        compose.onNodeWithContentDescription("已选 2 项").assertExists()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"markup-selection-review.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun selectionActionsStayInTopRightBelowDoneAndOutsideDock() {
        compose.setContent {
            PockRefApp(state = PockRefUiState(blankCanvasOpen = true, markupModeActive = true,
                selectedMarkupIds = setOf("shape"), markupStrokes = listOf(CanvasMarkupStroke(
                    id = "shape", points = listOf(Offset.Zero, Offset(100f, 0f))))), onAction = {})
        }
        val done = compose.onNodeWithContentDescription("完成标记").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("markup-selection-actions").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("markup-tool-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.top > done.bottom)
        assertEquals(done.right, controls.right, 1f)
        assertTrue(controls.bottom < dock.top)
    }

    @Test fun emptyCanvasDispatchesDrawingIncludingPointerUpPosition() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            PockRefApp(state=PockRefUiState(blankCanvasOpen=true,markupModeActive=true),onAction=actions::add)
        }
        compose.onRoot().performTouchInput {
            val start = Offset(width*0.4f,height*0.35f)
            down(start); moveTo(start+Offset(80f,30f)); up()
        }
        compose.runOnIdle {
            assertEquals(1,actions.filterIsInstance<PockRefAction.BeginMarkupStroke>().size)
            assertTrue(actions.filterIsInstance<PockRefAction.UpdateMarkupStroke>().isNotEmpty())
            assertTrue(actions.contains(PockRefAction.CommitMarkupStroke))
        }
    }

    @Test fun lassoAndPartialEraserPaletteDispatchExistingActions() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent { MarkupToolPalette(PockRefUiState(markupModeActive=true),actions::add) }
        compose.onNodeWithContentDescription("套索").performScrollTo().performClick()
        compose.onNodeWithContentDescription("局部擦墨迹").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(actions.contains(PockRefAction.SetMarkupTool(CanvasMarkupTool.LASSO)))
            assertTrue(actions.contains(PockRefAction.SetMarkupTool(CanvasMarkupTool.INK_ERASER)))
        }
    }

    @Test fun secondFingerCancelsDrawingInsteadOfCommittingStrayInk() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            PockRefApp(state=PockRefUiState(blankCanvasOpen=true,markupModeActive=true),onAction=actions::add)
        }
        compose.onRoot().performTouchInput {
            val start = Offset(width*0.4f,height*0.35f)
            down(0,start); moveTo(0,start+Offset(30f,0f))
            down(1,start+Offset(150f,0f))
            moveTo(1,start+Offset(200f,0f))
            up(1); up(0)
        }
        compose.runOnIdle {
            assertTrue(actions.contains(PockRefAction.CancelMarkupStroke))
            assertFalse(actions.contains(PockRefAction.CommitMarkupStroke))
        }
    }

    @Test fun twoFingerPanWorksWhenSingleFingerPanIsEnabled() {
        assertTwoFingerMarkupPan(singleFingerPanEnabled = true)
    }

    @Test fun twoFingerPanWorksWhenSingleFingerPanIsDisabled() {
        assertTwoFingerMarkupPan(singleFingerPanEnabled = false)
    }

    private fun assertTwoFingerMarkupPan(singleFingerPanEnabled: Boolean) {
        val actions = mutableListOf<PockRefAction>()
        val state = mutableStateOf(PockRefUiState(blankCanvasOpen = true, markupModeActive = true,
            gestureToggles = mapOf(GestureId.SINGLE_FINGER_PAN to singleFingerPanEnabled)))
        compose.setContent {
            PockRefApp(
                state = state.value,
                onAction = { action ->
                    actions.add(action)
                    if (action is PockRefAction.PanCanvas) {
                        state.value = state.value.copy(canvasOffset = state.value.canvasOffset + action.delta)
                    }
                },
            )
        }
        compose.onRoot().performTouchInput {
            val first = Offset(width * 0.35f, height * 0.35f)
            val second = first + Offset(120f, 0f)
            down(0, first); down(1, second)
            // Update both pointers in one event: pure translation, not pinch.
            updatePointerTo(0, first + Offset(60f, 40f))
            updatePointerTo(1, second + Offset(60f, 40f))
            move()
            up(1)
            // The remaining finger must not resume drawing or pan the canvas.
            moveTo(0, first + Offset(100f, 80f))
            up(0)
        }
        compose.runOnIdle {
            val pan = actions.filterIsInstance<PockRefAction.PanCanvas>()
                .fold(Offset.Zero) { total, action -> total + action.delta }
            assertEquals(60f, pan.x, 0.01f)
            assertEquals(40f, pan.y, 0.01f)
            assertEquals(pan, state.value.canvasOffset)
            assertTrue(actions.contains(PockRefAction.CancelMarkupStroke))
            assertFalse(actions.contains(PockRefAction.CommitMarkupStroke))
            assertTrue(actions.filterIsInstance<PockRefAction.ZoomCanvas>().isEmpty())
        }
    }

    @Test fun twoFingerPinchStillZoomsWithoutPanningOrCommittingInk() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            PockRefApp(state = PockRefUiState(blankCanvasOpen = true, markupModeActive = true,
                gestureToggles = mapOf(GestureId.SINGLE_FINGER_PAN to true, GestureId.PINCH_ZOOM to true)),
                onAction = actions::add)
        }
        compose.onRoot().performTouchInput {
            val first = Offset(width * 0.35f, height * 0.35f)
            val second = first + Offset(120f, 0f)
            down(0, first); down(1, second)
            updatePointerTo(0, first - Offset(20f, 0f))
            updatePointerTo(1, second + Offset(20f, 0f))
            move(); up(1); up(0)
        }
        compose.runOnIdle {
            assertEquals(1, actions.filterIsInstance<PockRefAction.ZoomCanvas>().size)
            assertTrue(actions.filterIsInstance<PockRefAction.PanCanvas>().isEmpty())
            assertTrue(actions.contains(PockRefAction.CancelMarkupStroke))
            assertFalse(actions.contains(PockRefAction.CommitMarkupStroke))
        }
    }

    @Test fun cancelledPointerRestoresDraftInsteadOfCommittingIt() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            PockRefApp(state=PockRefUiState(blankCanvasOpen=true,markupModeActive=true),onAction=actions::add)
        }
        compose.onRoot().performTouchInput {
            val start = Offset(width*0.4f,height*0.35f)
            down(start); moveTo(start+Offset(80f,30f)); cancel()
        }
        compose.runOnIdle {
            assertTrue(actions.contains(PockRefAction.CancelMarkupStroke))
            assertFalse(actions.contains(PockRefAction.CommitMarkupStroke))
        }
    }
}
