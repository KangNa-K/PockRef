package com.pockref.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.RecentSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class RecentFileRenameTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun filenameOpensRenameCoverOpensFileAndMultiSelectStillSelects() {
        val entry = RecentSceneEntry(uri = "file:///missing.pur", label = "Original.pur", source = "test")
        val multiSelect = mutableStateOf(false)
        val opened = mutableListOf<String>()
        val selected = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            RecentFileGrid(
                entries = listOf(entry), sortMode = RecentSortMode.RECENT, showPinnedOnly = false,
                multiSelectMode = multiSelect.value, selectedUris = emptySet(),
                onOpen = { opened.add(it) }, onEnterMultiSelect = {}, onToggleSelection = { selected.add(it) },
                onSwipePin = {}, onSwipeDelete = {}, onRename = { uri, name -> renamed.add(uri to name) },
            )
        }
        composeRule.onNodeWithText("Original").performClick()
        composeRule.onNodeWithText("重命名").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("New name")
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(entry.uri to "New name"), renamed)
            assertTrue(opened.isEmpty())
        }
        composeRule.onNodeWithTag("recent-file-cover:${entry.uri}").performClick()
        composeRule.runOnIdle { assertEquals(listOf(entry.uri), opened); multiSelect.value = true }
        composeRule.onNodeWithText("Original").performClick()
        composeRule.runOnIdle { assertEquals(listOf(entry.uri), selected) }
        composeRule.onNodeWithText("重命名").assertDoesNotExist()
    }

    @Test fun renamesFileAndKeepsExistingFileOnCollisionOrInvalidName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("pockref_ui", Context.MODE_PRIVATE)
        val previous = prefs.getString(RECENT_SCENES_KEY, null)
        val dir = File(context.cacheDir, "rename-${System.nanoTime()}").apply { mkdirs() }
        val original = File(dir, "old.pur").apply { writeText("source") }
        val collision = File(dir, "taken.pur").apply { writeText("untouched") }
        val entry = RecentSceneEntry(uri = Uri.fromFile(original).toString(), label = "old.pur", source = "test")
        val store = ViewModelStore()
        try {
            composeRule.runOnIdle {
                val vm = PockRefViewModel(context.applicationContext as Application)
                store.put("rename", vm)
                vm._uiState.value = vm.uiState.value.copy(recentScenes = listOf(entry))
                vm.renameRecentScene(entry.uri, "taken")
                assertEquals(entry, vm.uiState.value.recentScenes.single())
                assertEquals("untouched", collision.readText())
                assertTrue(original.exists())
                vm.renameRecentScene(entry.uri, "../unsafe")
                assertEquals(entry, vm.uiState.value.recentScenes.single())
                vm.renameRecentScene(entry.uri, " new.PUR ")
                val renamed = File(dir, "new.pur")
                assertTrue(renamed.exists())
                assertFalse(original.exists())
                assertEquals("source", renamed.readText())
                assertEquals(Uri.fromFile(renamed).toString(), vm.uiState.value.recentScenes.single().uri)
                assertEquals("new.pur", vm.uiState.value.recentScenes.single().label)
                vm.renameRecentScene(Uri.fromFile(renamed).toString(), "again")
                assertTrue(File(dir, "again.pur").exists())
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
            prefs.edit().apply {
                if (previous == null) remove(RECENT_SCENES_KEY) else putString(RECENT_SCENES_KEY, previous)
            }.commit()
            dir.deleteRecursively()
        }
    }
}
