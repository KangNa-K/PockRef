package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.selectedGroups
import kotlin.math.sqrt

/** Null delegates to whole-canvas layout; an unchanged list is a handled no-op. */
internal fun layoutSelectedGroupMembers(state: PockRefUiState): List<SceneImage>? {
    val editingGroup = state.groups.firstOrNull { it.id == state.editingGroupId }
    val group = editingGroup ?: state.selectedGroups.singleOrNull()?.takeIf { candidate ->
        state.selectedImageIds.all { it in candidate.imageIds }
    } ?: return null
    if (state.globalImageLock || state.lockCanvasFrozen || group.positionLocked) return state.images
    val memberIds = group.imageIds.toSet()
    val selectedMembers = state.selectedImageIds.intersect(memberIds)
    val targetIds = if (editingGroup != null && selectedMembers.size >= 2) selectedMembers else memberIds
    val members = state.images.filter { it.id in targetIds }.sortedBy { it.zIndex }
    if (members.size < 2 || members.any { it.locked }) return state.images

    val footprints = members.map(::imageRotatedFootprint)
    val width = state.canvasViewportSize.width
    val height = state.canvasViewportSize.height
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return state.images
    val area = footprints.sumOf { (it.width * it.height).toDouble() }.toFloat()
    val rowWidth = maxOf(sqrt(area * width / height), footprints.maxOf { it.width })
    val left = members.indices.minOf { members[it].center.x - footprints[it].width / 2f }
    val right = members.indices.maxOf { members[it].center.x + footprints[it].width / 2f }
    val top = members.indices.minOf { members[it].center.y - footprints[it].height / 2f }
    val bottom = members.indices.maxOf { members[it].center.y + footprints[it].height / 2f }
    val center = Offset((left + right) / 2f, (top + bottom) / 2f)
    // Reuse the group row packer, preserving member scales, rotations and layer order.
    val replacements = layoutGroupMembersForDrag(members, rowWidth, gap = 2f)
        .associate { it.id to it.copy(center = it.center + center) }
    return state.images.map { replacements[it.id] ?: it }
}
