package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.isImageLocked
import com.pockref.app.model.selectedGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGroupStateTest {
    @Test
    fun aSingleImageCanBeAGroupWithoutForcingMultiSelection() {
        val group = SceneGroup(imageIds = listOf("only"))

        assertEquals(listOf("only"), group.imageIds)
        assertTrue(group.movesAsUnit)
        assertFalse(group.positionLocked)
    }

    @Test
    fun normalizeSelection_expandsMemberSelectionToWholeGroup() {
        val state = state().copy(
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).normalizeSelection()

        assertEquals(setOf("a", "b"), state.selectedImageIds)
        assertEquals("a", state.selectedImageId)
    }

    @Test
    fun normalizeSelection_preservesMultipleExplicitGroupTargets() {
        val second = SceneGroup(id = "g2", imageIds = listOf("outside"))
        val state = state().copy(
            groups = state().groups + second,
            selectedGroupIds = setOf("g", "g2"),
            selectedImageIds = setOf("a"),
            selectedImageId = "a",
        ).normalizeSelection()

        assertEquals(setOf("g", "g2"), state.selectedGroupIds)
        assertEquals(setOf("a", "b", "outside"), state.selectedImageIds)
        assertEquals(setOf("g", "g2"), state.selectedGroups.map { it.id }.toSet())
    }

    @Test
    fun normalizeSelection_doesNotPromoteAnUngroupedSelectionToAGroup() {
        val state = state().copy(
            selectedImageId = "outside",
            selectedImageIds = setOf("outside"),
        ).normalizeSelection()

        assertTrue(state.selectedGroupIds.isEmpty())
        assertEquals(setOf("outside"), state.selectedImageIds)
    }

    @Test
    fun normalizeSelection_keepsEmptySelectionInsideGroupEditor() {
        val state = state().copy(
            editingGroupId = "g",
            selectedImageId = "outside",
            selectedImageIds = setOf("outside"),
        ).normalizeSelection()

        assertEquals("g", state.editingGroupId)
        assertTrue(state.selectedImageIds.isEmpty())
        assertNull(state.selectedImageId)
        assertTrue(state.isImageLocked("outside"))
        assertFalse(state.isImageLocked("a"))
    }

    @Test
    fun lockedGroupCannotRemainInEditModeAndLocksEveryMember() {
        val state = state(groupLocked = true).copy(
            editingGroupId = "g",
            selectedImageId = "a",
            selectedImageIds = setOf("a"),
        ).normalizeSelection()

        assertNull(state.editingGroupId)
        assertTrue(state.isImageLocked("a"))
        assertTrue(state.isImageLocked("b"))
    }

    @Test
    fun selectedGroupWithLockedMemberLocksWholeGroupOutsideEditor() {
        val state = state().copy(
            images = state().images.map { image ->
                if (image.id == "a") image.copy(locked = true) else image
            },
            selectedImageId = "b",
            selectedImageIds = setOf("b"),
        ).normalizeSelection()

        assertEquals(setOf("a", "b"), state.selectedImageIds)
        assertTrue(state.isImageLocked("a"))
        assertTrue(state.isImageLocked("b"))
    }

    @Test
    fun importedImagesJoinEditingGroupAndKeepItsLayerBlockContiguous() {
        val state = PockRefUiState(
            images = listOf(
                image("member", zIndex = 0),
                image("outside", zIndex = 1),
            ),
            groups = listOf(SceneGroup(id = "g", imageIds = listOf("member"))),
            editingGroupId = "g",
        )

        val merged = state.mergeImportedImages(listOf(image("imported", zIndex = 2)))

        assertEquals("g", merged.editingGroupId)
        assertEquals(listOf("member", "imported"), merged.groups.single().imageIds)
        assertEquals(
            listOf("member", "imported", "outside"),
            merged.images.sortedBy { it.zIndex }.map { it.id },
        )
    }

    @Test
    fun importingIntoLockedEditingGroupClosesEditorWithoutAttachingImage() {
        val state = PockRefUiState(
            images = listOf(image("member", zIndex = 0)),
            groups = listOf(
                SceneGroup(id = "g", imageIds = listOf("member"), positionLocked = true),
            ),
            editingGroupId = "g",
        )

        val merged = state.mergeImportedImages(listOf(image("imported", zIndex = 1)))

        assertEquals(null, merged.editingGroupId)
        assertEquals(listOf("member"), merged.groups.single().imageIds)
    }

    @Test
    fun dragIntoGroupUsesRowScanLayoutWithoutChangingImageScale() {
        val images = listOf("a", "b", "c").map { id ->
            image(id, zIndex = id[0] - 'a').copy(
                center = Offset.Zero,
                displaySize = Size2D(10f, 10f),
                scale = 1.5f,
            )
        }

        val laidOut = layoutGroupMembersForDrag(images, maxRowWidth = 50f)

        assertEquals(listOf("a", "b", "c"), laidOut.map { it.id })
        assertEquals(1.5f, laidOut[0].scale, 0f)
        assertEquals(1.5f, laidOut[1].scale, 0f)
        assertEquals(1.5f, laidOut[2].scale, 0f)
        assertEquals("first two members share a row", laidOut[0].center.y, laidOut[1].center.y, 0f)
        assertTrue(laidOut[2].center.y > laidOut[1].center.y)
    }

    private fun state(groupLocked: Boolean = false) = PockRefUiState(
        images = listOf("a", "b", "outside").mapIndexed { index, id ->
            SceneImage(
                id = id,
                name = "$id.png",
                mimeType = "image/png",
                intrinsicWidth = 10,
                intrinsicHeight = 10,
                center = Offset(index * 20f, 0f),
                displaySize = Size2D(10f, 10f),
            )
        },
        groups = listOf(
            SceneGroup(
                id = "g",
                name = "References",
                imageIds = listOf("a", "b"),
                backgroundColor = Color.Black,
                positionLocked = groupLocked,
            ),
        ),
    )

    private fun image(id: String, zIndex: Int) = SceneImage(
        id = id,
        name = "$id.png",
        mimeType = "image/png",
        intrinsicWidth = 10,
        intrinsicHeight = 10,
        center = Offset.Zero,
        displaySize = Size2D(10f, 10f),
        zIndex = zIndex,
    )
}
