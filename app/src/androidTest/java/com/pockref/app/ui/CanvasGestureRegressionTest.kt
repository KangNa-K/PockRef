package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CanvasGestureRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun slowTwoFingerPanDoesNotTriggerUndo() {
        val actions = mutableListOf<PockRefAction>()
        val image = SceneImage(
            id = "offscreen", name = "offscreen", mimeType = "image/png",
            intrinsicWidth = 100, intrinsicHeight = 100,
            center = Offset(10000f, 10000f), displaySize = Size2D(100f, 100f),
        )
        compose.setContent {
            PockRefApp(
                state = PockRefUiState(
                    images = listOf(image),
                    gestureToggles = mapOf(GestureId.TWO_FINGER_TAP to true),
                ),
                onAction = actions::add,
            )
        }
        compose.onRoot().performTouchInput {
            val first = Offset(width * 0.4f, height * 0.4f)
            val second = first + Offset(100f, 0f)
            down(0, first)
            down(1, second)
            repeat(20) { index ->
                val delta = Offset((index + 1).toFloat(), 0f)
                updatePointerTo(0, first + delta)
                updatePointerTo(1, second + delta)
                move()
            }
            up(1)
            up(0)
        }
        compose.runOnIdle { assertFalse(actions.contains(PockRefAction.Undo)) }
    }
}
