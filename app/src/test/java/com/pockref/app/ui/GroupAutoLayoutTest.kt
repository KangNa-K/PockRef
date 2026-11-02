package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.*
import org.junit.Test

class GroupAutoLayoutTest {
    private val members = (0..4).map { index ->
        SceneImage(
            id = "member$index", name = "$index.png", mimeType = "image/png",
            intrinsicWidth = 100, intrinsicHeight = 80,
            displaySize = Size2D(100f, 80f), center = Offset(300f, 200f),
            scale = 0.5f + index * 0.3f, rotationDegrees = index * 17f, zIndex = index,
        )
    }
    private val outside = members.first().copy(id = "outside", center = Offset(-500f, -600f))
    private val group = SceneGroup(id = "group", imageIds = members.map { it.id })
    private val state = PockRefUiState(
        images = members + outside, groups = listOf(group),
        selectedImageIds = group.imageIds.toSet(), selectedGroupIds = setOf(group.id),
        multiSelectMode = true, canvasViewportSize = Size2D(1200f, 800f),
    )

    @Test fun selectedGroupReflowsMembersWithoutChangingTransformsOrOutsideImages() {
        val result = layoutSelectedGroupMembers(state)!!
        assertNotEquals(state.images, result)
        assertEquals(outside, result.last())
        members.zip(result).forEach { (before, after) ->
            assertEquals(before, after.copy(center = before.center))
        }
        assertNoOverlap(result.take(members.size))
        assertEquals(state.images.map { it.id }, result.map { it.id })
        assertEquals(300f, boundsCenter(result.take(members.size)).x, 0.01f)
        assertEquals(200f, boundsCenter(result.take(members.size)).y, 0.01f)
    }

    @Test fun singleMemberSelectionOutsideEditorStillReflowsWholeGroup() {
        val result = layoutSelectedGroupMembers(state.copy(
            selectedImageIds = setOf(members.first().id), selectedGroupIds = emptySet(),
            multiSelectMode = false,
        ))!!
        assertNoOverlap(result.take(members.size))
        assertEquals(outside, result.last())
    }

    @Test fun editorWithNoSelectionReflowsOnlyItsGroup() {
        val result = layoutSelectedGroupMembers(state.copy(
            editingGroupId = group.id, selectedImageIds = emptySet(), selectedGroupIds = emptySet(),
        ))!!
        assertNoOverlap(result.take(members.size))
        assertEquals(outside, result.last())
    }

    @Test fun editorMultipleSelectionMovesOnlySelectedMembers() {
        val result = layoutSelectedGroupMembers(state.copy(
            editingGroupId = group.id, selectedGroupIds = emptySet(),
            selectedImageIds = members.take(2).map { it.id }.toSet(),
        ))!!
        assertNoOverlap(result.take(2))
        assertEquals(state.images.drop(2), result.drop(2))
    }

    @Test fun lockedGroupOrMemberAndGlobalLocksAreNoOps() {
        listOf(
            state.copy(groups = listOf(group.copy(positionLocked = true))),
            state.copy(images = state.images.mapIndexed { i, image -> if (i == 0) image.copy(locked = true) else image }),
            state.copy(globalImageLock = true),
            state.copy(lockCanvasFrozen = true),
        ).forEach { locked -> assertEquals(locked.images, layoutSelectedGroupMembers(locked)) }
    }

    @Test fun noSelectionAndMixedSelectionDelegateToCanvasLayout() {
        assertNull(layoutSelectedGroupMembers(state.copy(selectedImageIds = emptySet(), selectedGroupIds = emptySet())))
        assertNull(layoutSelectedGroupMembers(state.copy(selectedImageIds = state.selectedImageIds + outside.id)))
        val otherGroup = SceneGroup(id = "other", imageIds = listOf(outside.id))
        assertNull(layoutSelectedGroupMembers(state.copy(
            groups = listOf(group, otherGroup), selectedGroupIds = setOf(group.id, otherGroup.id),
        )))
    }

    @Test fun emptySingleMemberAndInvalidViewportDoNotChangeImages() {
        assertEquals(state.images, layoutSelectedGroupMembers(state.copy(canvasViewportSize = Size2D(Float.NaN, 800f))))
        assertEquals(state.images, layoutSelectedGroupMembers(state.copy(canvasViewportSize = Size2D(0f, 800f))))
        val single = state.copy(images = listOf(members.first()), groups = listOf(group.copy(imageIds = listOf(members.first().id))),
            selectedImageIds = setOf(members.first().id))
        assertEquals(single.images, layoutSelectedGroupMembers(single))
        val empty = state.copy(images = emptyList(), selectedImageIds = emptySet())
        assertEquals(empty.images, layoutSelectedGroupMembers(empty))
    }

    @Test fun repeatedLayoutIsStable() {
        val once = layoutSelectedGroupMembers(state)!!
        val twice = layoutSelectedGroupMembers(state.copy(images = once))!!
        once.zip(twice).forEach { (a, b) ->
            assertEquals(a.center.x, b.center.x, 0.001f)
            assertEquals(a.center.y, b.center.y, 0.001f)
            assertEquals(a.scale, b.scale, 0f)
        }
    }

    private fun boundsCenter(images: List<SceneImage>): Offset {
        val fp = images.map(::imageRotatedFootprint)
        return Offset(
            (images.indices.minOf { images[it].center.x - fp[it].width / 2f } + images.indices.maxOf { images[it].center.x + fp[it].width / 2f }) / 2f,
            (images.indices.minOf { images[it].center.y - fp[it].height / 2f } + images.indices.maxOf { images[it].center.y + fp[it].height / 2f }) / 2f,
        )
    }

    private fun assertNoOverlap(images: List<SceneImage>) {
        images.forEachIndexed { i, a -> images.drop(i + 1).forEach { b ->
            val af = imageRotatedFootprint(a)
            val bf = imageRotatedFootprint(b)
            assertFalse("${a.id} overlaps ${b.id}",
                kotlin.math.abs(a.center.x - b.center.x) < (af.width + bf.width) / 2f - 0.01f &&
                    kotlin.math.abs(a.center.y - b.center.y) < (af.height + bf.height) / 2f - 0.01f)
        } }
    }
}
