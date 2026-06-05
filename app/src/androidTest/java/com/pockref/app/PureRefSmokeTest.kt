package com.pockref.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PockRefSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenRendersCoreControls() {
        composeRule.onNodeWithText("PockRef Android", useUnmergedTree = true).assertTextEquals("PockRef Android")
        composeRule.onNodeWithText("Start with images", useUnmergedTree = true).assertTextEquals("Start with images")
        composeRule.onNodeWithText("Import", useUnmergedTree = true).assertExists()
    }
}
