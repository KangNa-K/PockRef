package com.pockref.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.isImageLocked
import com.pockref.app.model.selectedGroups
import com.pockref.app.model.SceneImage
import kotlinx.coroutines.flow.update

/** Canvas-group editing operations, kept separate from image transforms. */
class PureRefViewModelGroups internal constructor(private val mainVm: PockRefViewModel) {
    fun createGroup(name: String? = null) {
        val before = mainVm._uiState.value
        val ids = before.selectedImageIds
        if (before.requiresGroupFlattening || before.editingGroupId != null || ids.isEmpty() ||
            ids.any { id -> before.groupContaining(id) != null || before.isImageLocked(id) }
        ) return

        val sorted = before.images.sortedBy { it.zIndex }
        val selected = sorted.filter { it.id in ids }
        if (selected.size != ids.size) return
        val highestSelected = sorted.indexOfLast { it.id in ids }
        val insertionIndex = sorted.take(highestSelected + 1).count { it.id !in ids }
        val reordered = sorted.filterNot { it.id in ids }.toMutableList().apply {
            addAll(insertionIndex.coerceIn(0, size), selected)
        }
        val names = before.groups.mapTo(mutableSetOf()) { it.name }
        var suffix = 1
        while ("组 $suffix" in names) suffix++
        val groupName = name?.trim()?.takeIf { it.isNotEmpty() } ?: "组 $suffix"
        val group = SceneGroup(name = groupName, imageIds = selected.map { it.id })
        mainVm.mutateScene {
            copy(
                images = reordered.mapIndexed { index, image -> image.copy(zIndex = index) },
                groups = groups + group,
                selectedImageId = group.imageIds.lastOrNull(),
                selectedImageIds = group.imageIds.toSet(),
                selectedGroupIds = setOf(group.id),
                multiSelectMode = group.imageIds.size > 1,
                boxSelectMode = false,
            )
        }
    }

    /**
     * Completes an image drag by attaching ungrouped images to a group. The
     * gesture snapshot captured by beginImageDrag is kept as the single undo
     * boundary, so the move, regrouping, and reflow undo together.
     */
    fun addImagesToGroupAfterDrag(groupId: String, imageIds: Set<String>) {
        val before = mainVm._uiState.value
        val dragIds = before.transientDragImageIds
        val dragDelta = before.transientDragDelta
        if (dragIds.isEmpty() || dragDelta == Offset.Zero || imageIds.isEmpty()) {
            mainVm.transformController.commitImageDrag()
            return
        }
        if (before.requiresGroupFlattening || before.editingGroupId != null) {
            mainVm.transformController.commitImageDrag()
            return
        }
        val target = before.groups.firstOrNull { it.id == groupId }
        val existingIds = target?.imageIds?.toSet().orEmpty()
        val existingMembers = before.images.filter { it.id in existingIds }
        val incoming = before.images.filter { it.id in imageIds }
        val valid = target != null &&
            target.positionLocked.not() &&
            existingMembers.isNotEmpty() &&
            existingMembers.none { it.locked } &&
            incoming.size == imageIds.size &&
            incoming.all { image ->
                !image.locked &&
                    before.groups.none { group -> image.id in group.imageIds }
            }
        val originalBounds = target?.canvasBounds(before.images, GROUP_BOUNDS_PADDING)
        if (!valid || originalBounds == null) {
            mainVm.transformController.commitImageDrag()
            return
        }

        val combinedIds = existingIds + imageIds
        val orderedMembers = before.images.sortedBy { it.zIndex }.filter { it.id in combinedIds }
        if (orderedMembers.size != combinedIds.size) {
            mainVm.transformController.commitImageDrag()
            return
        }
        // The drag is rendered through transientDragDelta until release, so
        // include that delta before the iOS-compatible reflow is calculated.
        val movedMembers = orderedMembers.map { image ->
            if (image.id in imageIds) image.copy(center = image.center + dragDelta) else image
        }
        val widestMember = movedMembers.maxOfOrNull { imageRotatedFootprint(it).width } ?: 1f
        val rowWidth = maxOf(originalBounds.width(), widestMember, 1f)
        val laidOut = layoutGroupMembersForDrag(
            images = movedMembers,
            maxRowWidth = rowWidth,
        )
        val layoutBounds = imagesBounds(laidOut)
        if (layoutBounds == null) {
            mainVm.transformController.commitImageDrag()
            return
        }
        val translate = Offset(
            originalBounds.centerX() - layoutBounds.centerX(),
            originalBounds.centerY() - layoutBounds.centerY(),
        )
        val finalMembers = laidOut.map { it.copy(center = it.center + translate) }
        val finalById = finalMembers.associateBy { it.id }
        val sortedImages = before.images.sortedBy { it.zIndex }
        val reordered = sortedImages
            .filterNot { it.id in combinedIds }
            .toMutableList()
        val lastMemberIndex = sortedImages.indexOfLast { it.id in combinedIds }
        val insertionIndex = sortedImages.take(lastMemberIndex + 1).count { it.id !in combinedIds }
        reordered.addAll(insertionIndex.coerceIn(0, reordered.size), finalMembers)
        val normalizedImages = reordered.mapIndexed { index, image ->
            (finalById[image.id] ?: image).copy(zIndex = index)
        }
        mainVm.updateSceneForGesture {
            copy(
                images = normalizedImages,
                groups = groups.map { group ->
                    if (group.id == groupId) group.copy(imageIds = orderedMembers.map { it.id }) else group
                },
                selectedGroupIds = setOf(groupId),
                selectedImageIds = combinedIds,
                selectedImageId = selectedImageId?.takeIf { it in combinedIds }
                    ?: orderedMembers.lastOrNull()?.id,
                multiSelectMode = combinedIds.size > 1,
                transientDragImageIds = emptySet(),
                transientDragMarkupIds = emptySet(),
                transientDragDelta = Offset.Zero,
                snapGuide = null,
                snapInRangeSinceMs = null,
                snapEngagedTargetId = null,
                snapRecentSpeeds = emptyList(),
                snapLastSampleTimeMs = null,
                snapGuideAlpha = 1f,
            )
        }
        mainVm.endSceneGesture()
    }

    fun enterSelectedGroup() {
        val state = mainVm._uiState.value
        val group = state.selectedGroups.singleOrNull() ?: return
        if (state.requiresGroupFlattening || group.positionLocked || state.isImageLocked(null)) return
        enterGroup(group.id, group.imageIds.firstOrNull())
    }

    fun enterGroup(groupId: String, imageId: String? = null) {
        val state = mainVm._uiState.value
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        if (state.requiresGroupFlattening || group.positionLocked || state.globalImageLock) return
        val members = group.imageIds.filter { id -> state.images.any { it.id == id } }
        if (members.isEmpty()) return
        val selected = imageId?.takeIf { it in members }?.let(::setOf) ?: members.toSet()
        mainVm._uiState.update {
            it.copy(
                editingGroupId = group.id,
                selectedGroupIds = emptySet(),
                selectedImageId = selected.lastOrNull(),
                selectedImageIds = selected,
                multiSelectMode = selected.size > 1,
                boxSelectMode = false,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
                selectionSheetDismissTick = it.selectionSheetDismissTick + 1,
            ).normalizeSelection()
        }
    }

    fun exitGroup() {
        val state = mainVm._uiState.value
        val group = state.groups.firstOrNull { it.id == state.editingGroupId } ?: return
        val members = group.imageIds.filter { id -> state.images.any { it.id == id } }.toSet()
        mainVm._uiState.update {
            it.copy(
                editingGroupId = null,
                selectedGroupIds = setOf(group.id),
                selectedImageId = members.lastOrNull(),
                selectedImageIds = members,
                multiSelectMode = members.size > 1,
                boxSelectMode = false,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
                selectionSheetDismissTick = it.selectionSheetDismissTick + 1,
            ).normalizeSelection()
        }
    }

    fun ungroupSelected() {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening || state.editingGroupId != null) return
        val ids = state.selectedGroups.mapTo(mutableSetOf()) { it.id }
        if (ids.isEmpty()) return
        mainVm.mutateScene {
            copy(
                groups = groups.filterNot { it.id in ids },
                selectedGroupIds = emptySet(),
            )
        }
    }

    fun duplicateSelectedGroup() {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening || state.editingGroupId != null) return
        val source = state.selectedGroups.singleOrNull() ?: return
        val members = source.imageIds.mapNotNull { id -> state.images.firstOrNull { it.id == id } }
        if (members.isEmpty()) return
        val nextZ = (state.images.maxOfOrNull { it.zIndex } ?: -1) + 1
        val idMap = members.associate { it.id to java.util.UUID.randomUUID().toString() }
        val copies = members.mapIndexed { index, image ->
            image.copy(
                id = idMap.getValue(image.id),
                center = image.center + androidx.compose.ui.geometry.Offset(20f, 20f),
                zIndex = nextZ + index,
            )
        }
        val duplicate = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${source.name} 副本",
            imageIds = source.imageIds.mapNotNull(idMap::get),
        )
        mainVm.mutateScene {
            copy(
                images = images + copies,
                groups = groups + duplicate,
                selectedImageId = duplicate.imageIds.lastOrNull(),
                selectedImageIds = duplicate.imageIds.toSet(),
                selectedGroupIds = setOf(duplicate.id),
                multiSelectMode = true,
            )
        }
    }

    fun removeSelectedFromGroup() {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening) return
        val groupId = state.editingGroupId ?: return
        val selected = state.selectedImageIds
        if (selected.isEmpty()) return
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        if (group.positionLocked) return
        val removedIds = group.imageIds.filter { it in selected }.toSet()
        if (removedIds.isEmpty()) return
        val remaining = group.imageIds.filterNot { it in removedIds }
        mainVm.mutateScene {
            val ordered = images.sortedBy { it.zIndex }
            val groupMembers = group.imageIds.toSet()
            val orderedMembers = ordered.filter { it.id in groupMembers }
            val replacement = (
                orderedMembers.filter { it.id in removedIds } +
                    orderedMembers.filter { it.id !in removedIds }
                ).iterator()
            val reordered = ordered.map { image ->
                if (image.id in groupMembers) replacement.next() else image
            }.mapIndexed { index, image -> image.copy(zIndex = index) }
            copy(
                images = reordered,
                groups = if (remaining.isEmpty()) groups.filterNot { it.id == groupId }
                else groups.map { if (it.id == groupId) it.copy(imageIds = remaining) else it },
                editingGroupId = groupId.takeIf { remaining.isNotEmpty() },
                selectedGroupIds = emptySet(),
                selectedImageId = null,
                selectedImageIds = emptySet(),
                multiSelectMode = false,
            )
        }
    }

    fun renameSelectedGroup(name: String) {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening) return
        val group = state.selectedGroups.singleOrNull() ?: return
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        mainVm.mutateScene {
            copy(groups = groups.map { if (it.id == group.id) it.copy(name = normalized) else it })
        }
    }

    fun setSelectedGroupAppearance(color: Color, opacity: Float) {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening) return
        val group = state.selectedGroups.singleOrNull() ?: return
        val normalizedOpacity = opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        mainVm.mutateScene {
            copy(groups = groups.map {
                if (it.id == group.id) it.copy(backgroundColor = color, backgroundOpacity = normalizedOpacity)
                else it
            })
        }
    }

    fun toggleSelectedGroupLock() {
        val state = mainVm._uiState.value
        if (state.requiresGroupFlattening) return
        val selected = state.selectedGroups
        val targets = if (selected.isNotEmpty()) selected else state.editingGroupId
            ?.let { id -> state.groups.firstOrNull { it.id == id } }
            ?.let(::listOf)
            .orEmpty()
        if (targets.isEmpty()) return
        if (state.globalImageLock) return
        val groupIds = targets.mapTo(mutableSetOf()) { it.id }
        val shouldUnlock = targets.any { it.positionLocked }
        mainVm.mutateScene {
            copy(groups = groups.map {
                if (it.id in groupIds) {
                    it.copy(positionLocked = !shouldUnlock)
                } else it
            })
        }
    }

    fun deselectOrExit() {
        if (mainVm._uiState.value.editingGroupId != null) exitGroup()
        else mainVm.transformController.deselect()
    }

    fun clearSelection() {
        mainVm._uiState.update { state ->
            if (state.editingGroupId == null) return@update state
            state.copy(
                selectedImageId = null,
                selectedImageIds = emptySet(),
                multiSelectMode = false,
                boxSelectMode = false,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
                bottomRightPanelExpanded = false,
                selectionSheetDismissTick = state.selectionSheetDismissTick + 1,
            ).normalizeSelection()
        }
    }
}

private const val GROUP_BOUNDS_PADDING = 12f

private fun PockRefUiState.groupContaining(imageId: String): SceneGroup? =
    groups.firstOrNull { it.contains(imageId) }
