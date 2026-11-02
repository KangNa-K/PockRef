package com.pockref.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pockref.app.model.PracticeState
import com.pockref.app.model.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PracticePausedSheetTest {
    @get:Rule val compose = createComposeRule()

    // The glass modifier used to sit on ModalBottomSheet itself, which M3
    // applies before moving the sheet down: the content was clipped away
    // and taps on the buttons fell through to the scrim (= resume).
    @Test fun pausedSheetButtonsAreVisibleAndClickable() {
        val actions = mutableListOf<PockRefAction>()
        compose.setContent {
            MaterialTheme {
                GlassBackdropHost {
                    Box(Modifier.fillMaxSize()) {
                        PracticeScreen(
                            state = PracticeState(
                                status = PracticeStatus.PAUSED,
                                imageIds = listOf("a", "b"),
                                remainingMs = 30_000L,
                            ),
                            images = emptyList(),
                            imageDecoder = null,
                            onAction = { actions += it },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("已暂停 · 1 / 2").assertIsDisplayed()
        compose.onNodeWithText("结束练习").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(PockRefAction.ExitPractice), actions) }
    }
}
