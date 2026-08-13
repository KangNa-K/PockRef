package com.pockref.app.ui

import com.pockref.app.model.PockRefUiState

internal fun PockRefUiState.posterizeLevelsFor(imageId: String): Int? =
    posterizeLevels.takeIf { levels ->
        levels != null &&
            posterizeControlsVisible &&
            imageId in posterizeTargetImageIds
    }

internal fun PockRefUiState.openPosterizeControls(targetIds: Set<String>): PockRefUiState {
    val validTargets = targetIds.filterTo(linkedSetOf()) { id ->
        images.any { it.id == id }
    }
    if (validTargets.isEmpty()) return clearPosterizePreview()
    return copy(
        posterizeControlsVisible = true,
        posterizeLevels = null,
        posterizeTargetImageIds = validTargets,
    )
}

internal fun PockRefUiState.togglePosterizeLevel(levels: Int): PockRefUiState {
    if (!posterizeControlsVisible || levels !in SUPPORTED_POSTERIZE_LEVELS) return this
    return copy(
        posterizeLevels = if (posterizeLevels == levels) null else levels,
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
