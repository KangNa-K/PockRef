package com.pockref.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecentMultiSelectToolbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun longPressShowsFloatingToolbarAndPreservesBulkActions() {
        val first = RecentSceneEntry("file:///first.pur", "first.pur", "test")
        val second = RecentSceneEntry("file:///second.pur", "second.pur", "test", pinned = true)
        val state = mutableStateOf(PockRefUiState(recentScenes = listOf(first, second)))
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            PockRefApp(state = state.value, onAction = { action ->
                actions.add(action)
                state.value = when (action) {
                    is PockRefAction.EnterRecentMultiSelect -> state.value.copy(
                        recentMultiSelectMode = true, recentSelection = setOfNotNull(action.uri),
                    )
                    PockRefAction.SelectAllRecentScenes -> state.value.copy(recentSelection = setOf(first.uri, second.uri))
                    PockRefAction.ClearRecentSelection -> state.value.copy(recentSelection = emptySet())
                    PockRefAction.ExitRecentMultiSelect -> state.value.copy(recentMultiSelectMode = false, recentSelection = emptySet())
                    PockRefAction.RequestBulkDeleteConfirm -> state.value.copy(bulkDeleteConfirmOpen = true)
                    PockRefAction.DismissBulkDeleteConfirm -> state.value.copy(bulkDeleteConfirmOpen = false)
                    else -> state.value
                }
            })
        }
        composeRule.onNodeWithTag("recent-file-cover:${first.uri}").performTouchInput { longClick() }
        composeRule.onNodeWithTag("recent-selection-toolbar").assertIsDisplayed()
        composeRule.onNodeWithText("已选 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消置顶").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("置顶").performClick()
        composeRule.runOnIdle { assertTrue(actions.contains(PockRefAction.BulkSetRecentPin(true))) }
        composeRule.onNodeWithContentDescription("全选").performClick()
        composeRule.onNodeWithText("已选 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("取消置顶").performClick()
        composeRule.runOnIdle { assertTrue(actions.contains(PockRefAction.BulkSetRecentPin(false))) }
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.onNodeWithText("删除最近文件").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("清空").performClick()
        composeRule.onNodeWithText("已选 0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("取消多选").performClick()
        composeRule.onNodeWithTag("recent-selection-toolbar").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(actions.contains(PockRefAction.ExitRecentMultiSelect))
            assertTrue(!state.value.recentMultiSelectMode)
        }
    }

    @Test fun narrowToolbarCanScrollToExit() {
        val entry = RecentSceneEntry("file:///first.pur", "first.pur", "test")
        val actions = mutableListOf<PockRefAction>()
        composeRule.setContent {
            Box(Modifier.size(280.dp, 120.dp)) {
                RecentMultiSelectToolbar(
                    state = PockRefUiState(
                        recentScenes = listOf(entry), recentMultiSelectMode = true,
                        recentSelection = setOf(entry.uri),
                    ),
                    onAction = { actions.add(it) },
                )
            }
        }
        composeRule.onNodeWithContentDescription("取消多选").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(actions.contains(PockRefAction.ExitRecentMultiSelect)) }
    }
}
