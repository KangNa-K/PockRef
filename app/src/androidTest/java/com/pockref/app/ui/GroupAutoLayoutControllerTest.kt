package com.pockref.app.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.*
import org.junit.Test

class GroupAutoLayoutControllerTest {
    private val members = (0..3).map { index ->
        SceneImage(id = "member$index", name = "$index", mimeType = "image/png",
            intrinsicWidth = 100, intrinsicHeight = 80, displaySize = Size2D(100f, 80f),
            center = Offset(400f, 300f), rotationDegrees = index * 15f, zIndex = index)
    }
    private val outside = members.first().copy(id = "outside", center = Offset(-1000f, -1000f))
    private val group = SceneGroup(id = "group", name = "References", imageIds = members.map { it.id })
    private val state = PockRefUiState(images = members + outside, groups = listOf(group),
        selectedGroupIds = setOf(group.id), selectedImageIds = group.imageIds.toSet(),
        multiSelectMode = true, canvasViewportSize = Size2D(1200f, 800f))

    @Test fun selectedGroupActionReflowsMembersAndSupportsUndoRedo() = withVm(state) { vm ->
        vm.onAction(PockRefAction.ExecuteAutoLayout)
        val result = vm.uiState.value
        assertNotEquals(state.images, result.images)
        assertEquals(outside, result.images.last())
        assertEquals(state.groups, result.groups)
        assertNoOverlap(result.images.take(4))
        assertTrue(result.canUndo)
        vm.onAction(PockRefAction.Undo)
        assertEquals(state.images, vm.uiState.value.images)
        assertEquals(state.groups, vm.uiState.value.groups)
        vm.onAction(PockRefAction.Redo)
        assertEquals(result.images, vm.uiState.value.images)
        assertEquals(result.groups, vm.uiState.value.groups)
    }

    @Test fun editingGroupWithoutSelectionDoesNotMoveOtherImages() = withVm(state.copy(
        editingGroupId = group.id, selectedGroupIds = emptySet(), selectedImageIds = emptySet(),
        multiSelectMode = false,
    )) { vm ->
        vm.onAction(PockRefAction.ExecuteAutoLayout)
        assertNoOverlap(vm.uiState.value.images.take(4))
        assertEquals(outside, vm.uiState.value.images.last())
        assertEquals(group.id, vm.uiState.value.editingGroupId)
    }

    @Test fun wholeCanvasLayoutKeepsGroupMemberGeometryTogether() = withVm(state.copy(
        selectedImageIds = emptySet(), selectedGroupIds = emptySet(), multiSelectMode = false,
        images = members.mapIndexed { i, image -> image.copy(center = Offset(i * 140f, 300f)) } + outside,
    )) { vm ->
        val before = vm.uiState.value
        vm.onAction(PockRefAction.ExecuteAutoLayout)
        val after = vm.uiState.value
        assertEquals(before.groups, after.groups)
        val factor = after.images.first().scale / before.images.first().scale
        before.images.take(4).zip(after.images).forEach { (a, b) ->
            assertEquals(a.scale * factor, b.scale, 0.001f)
            assertEquals(a.rotationDegrees, b.rotationDegrees, 0f)
            val oldDelta = a.center - before.images.first().center
            val newDelta = b.center - after.images.first().center
            assertEquals(oldDelta.x * factor, newDelta.x, 0.01f)
            assertEquals(oldDelta.y * factor, newDelta.y, 0.01f)
        }
    }

    private fun assertNoOverlap(images: List<SceneImage>) {
        images.forEachIndexed { i, a -> images.drop(i + 1).forEach { b ->
            val af = imageRotatedFootprint(a)
            val bf = imageRotatedFootprint(b)
            assertFalse(kotlin.math.abs(a.center.x - b.center.x) < (af.width + bf.width) / 2f - 0.01f &&
                kotlin.math.abs(a.center.y - b.center.y) < (af.height + bf.height) / 2f - 0.01f)
        } }
    }

    private fun withVm(initial: PockRefUiState, block: (PockRefViewModel) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val store = ViewModelStore()
            val vm = PockRefViewModel(instrumentation.targetContext.applicationContext as Application)
            store.put("group-layout", vm)
            try {
                vm._uiState.value = initial
                block(vm)
            } finally {
                store.clear()
            }
        }
    }
}
