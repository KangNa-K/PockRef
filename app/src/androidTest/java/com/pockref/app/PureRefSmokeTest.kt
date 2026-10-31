package com.pockref.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PockRefSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenRendersCoreControls() {
        composeRule.onNodeWithText("PockRef", useUnmergedTree = true).assertExists()
        // The add button is icon-only, so it is exposed via contentDescription.
        composeRule.onNodeWithContentDescription("添加图片", useUnmergedTree = true).assertExists()
    }
}
