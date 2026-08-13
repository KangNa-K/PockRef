package com.pockref.app.ui

import com.pockref.app.model.PockRefUiState

internal fun PockRefUiState.posterizeLevelsFor(imageId: String): Int? =
    images.firstOrNull { it.id == imageId }?.posterizeLevels

internal fun PockRefUiState.openPosterizeControls(targetIds: Set<String>): PockRefUiState {
    val validTargets = targetIds.filterTo(linkedSetOf()) { id ->
        images.any { it.id == id }
    }
    if (validTargets.isEmpty()) return clearPosterizePreview()
    val targetLevels = validTargets.mapNotNull { id ->
        images.firstOrNull { it.id == id }?.posterizeLevels
    }
    val commonLevels = targetLevels
        .takeIf { it.size == validTargets.size && it.distinct().size == 1 }
        ?.first()
    return copy(
        posterizeControlsVisible = true,
        posterizeLevels = commonLevels,
        posterizeTargetImageIds = validTargets,
    )
}

internal fun PockRefUiState.togglePosterizeLevel(levels: Int): PockRefUiState {
    if (!posterizeControlsVisible || levels !in SUPPORTED_POSTERIZE_LEVELS) return this
    val validTargets = posterizeTargetImageIds.filterTo(linkedSetOf()) { id ->
        images.any { it.id == id }
    }
    if (validTargets.isEmpty()) return clearPosterizePreview()
    val shouldClear = posterizeLevels == levels && validTargets.all { id ->
        images.firstOrNull { it.id == id }?.posterizeLevels == levels
    }
    val nextLevels = if (shouldClear) null else levels
    return copy(
        images = images.map { image ->
            if (image.id in validTargets) image.copy(posterizeLevels = nextLevels) else image
        },
        posterizeLevels = nextLevels,
    )
}

internal fun PockRefUiState.clearPosterizePreview(): PockRefUiState = copy(
    posterizeControlsVisible = false,
    posterizeLevels = null,
    posterizeTargetImageIds = emptySet(),
)

internal fun PockRefUiState.clearPosterizeIfSelectionChanged(
    normalizedSelection: Set<String>,
): PockRefUiState {
    if (!posterizeControlsVisible) {
        return if (posterizeLevels == null && posterizeTargetImageIds.isEmpty()) {
            this
        } else {
            clearPosterizePreview()
        }
    }
    return if (posterizeTargetImageIds == normalizedSelection) {
        copy(posterizeTargetImageIds = normalizedSelection)
    } else {
        clearPosterizePreview()
    }
}
