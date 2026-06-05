package com.pockref.app.ui

import android.graphics.RectF
import com.pockref.app.model.SelectionMode
import kotlinx.coroutines.flow.update

class PockRefViewModelSelection(private val mainVm: PockRefViewModel) {

    /**
     * Drive the canvas selection mode to one of the three
     * exclusive values. Idempotent: setting the mode the state
     * is already in is a no-op. Driven by the S Pen button
     * binding (hold on touchdown → BOX, release mid-stroke →
     * MULTI, double-tap empty canvas → SINGLE).
     *
     * SINGLE clears both booleans. MULTI enables lasso and
     * disables box. BOX enables both (box-select is a
     * multi-image gesture that drags a rectangle). The
     * state-model invariant `boxSelectMode → multiSelectMode`
     * is preserved here.
     */
    internal fun setSelectionMode(mode: SelectionMode) {
        mainVm._uiState.update { state ->
            val wantBox = mode == SelectionMode.BOX
            val wantMulti = mode == SelectionMode.MULTI || mode == SelectionMode.BOX
            if (state.boxSelectMode == wantBox && state.multiSelectMode == wantMulti) {
                // Idempotent: no reducer work needed when the
                // requested mode is already active. The selector
                // value (`it === state`) is preserved by update
                // when the lambda returns the same reference.
                state
            } else {
                when (mode) {
                    SelectionMode.SINGLE -> {
                        // SINGLE preserves the current primary
                        // selection: the user was probably looking
                        // at something; the S Pen double-tap exit
                        // is about the LASSO/BOX interaction, not
                        // about clearing the selection. The
                        // existing ToggleMultiSelectMode(false)
                        // path takes the same shortcut.
                        val retainedPrimary = state.selectedImageId
                            ?: state.selectedImageIds.lastOrNull()
                        state.copy(
                            boxSelectMode = false,
                            multiSelectMode = false,
                            boxSelectRect = null,
                            boxSelectPreviewIds = emptySet(),
                            selectedImageIds = if (retainedPrimary != null) setOf(retainedPrimary) else emptySet(),
                            selectedImageId = retainedPrimary,
                        ).normalizeSelection()
                    }
                    SelectionMode.MULTI -> state.copy(
                        boxSelectMode = false,
                        boxSelectRect = null,
                        boxSelectPreviewIds = emptySet(),
                        multiSelectMode = true,
                    )
                    SelectionMode.BOX -> state.copy(
                        boxSelectMode = true,
                        multiSelectMode = true,
                        boxSelectRect = null,
                        boxSelectPreviewIds = emptySet(),
                    )
                }
            }
        }
    }

    internal fun toggleMultiSelectMode() {
        mainVm._uiState.update { state ->
            val enabled = !state.multiSelectMode
            val retainedPrimary = state.selectedImageId ?: state.selectedImageIds.lastOrNull()
            state.copy(
                multiSelectMode = enabled,
                selectedImageIds = when {
                    enabled -> state.selectedImageIds.ifEmpty { retainedPrimary?.let(::setOf).orEmpty() }
                    retainedPrimary != null -> setOf(retainedPrimary)
                    else -> emptySet()
                },
                selectedImageId = retainedPrimary,
            )
                .normalizeSelection()
        }
    }

    internal fun toggleBoxSelectMode() {
        mainVm._uiState.update { state ->
            val enabling = !state.boxSelectMode
            if (enabling) {
                state.copy(
                    boxSelectMode = true,
                    multiSelectMode = true,
                    boxSelectRect = null,
                    boxSelectPreviewIds = emptySet(),
                )
            } else {
                state.copy(
                    boxSelectMode = false,
                    boxSelectRect = null,
                    boxSelectPreviewIds = emptySet(),
                )
            }
        }
    }

    internal fun beginBoxSelect() {
        mainVm._uiState.update { state ->
            state.copy(
                selectedImageIds = emptySet(),
                selectedImageId = null,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
            )
        }
    }

    internal fun updateBoxSelectRect(rect: RectF) {
        val scale = mainVm._uiState.value.canvasScale
        val offset = mainVm._uiState.value.canvasOffset
        val viewport = mainVm._uiState.value.canvasViewportSize
        val previewIds = mainVm._uiState.value.images.filter { img ->
            // V_NOW: locked images are immune to box-select. The
            // drag-rect passes over them and they don't enter the
            // preview set, so on commit they're not in the
            // selection. They still render normally (so the user
            // can see what was skipped) and remain tappable to
            // unlock.
            if (img.locked) return@filter false
            val screenX = viewport.width / 2f + offset.x + img.center.x * scale
            val screenY = viewport.height / 2f + offset.y + img.center.y * scale
            rect.contains(screenX, screenY)
        }.map { it.id }.toSet()
        mainVm._uiState.update { state ->
            state.copy(
                boxSelectRect = rect,
                boxSelectPreviewIds = previewIds,
            )
        }
    }

    internal fun commitBoxSelect() {
        val previewIds = mainVm._uiState.value.boxSelectPreviewIds
        mainVm._uiState.update { state ->
            state.copy(
                selectedImageIds = previewIds,
                selectedImageId = previewIds.lastOrNull(),
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
            ).normalizeSelection()
        }
    }

    internal fun exitBoxSelectMode() {
        mainVm._uiState.update { state ->
            state.copy(
                boxSelectMode = false,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
            )
        }
    }

    internal fun lassoSelect(rect: RectF) {
        val scale = mainVm._uiState.value.canvasScale
        val offset = mainVm._uiState.value.canvasOffset
        val viewport = mainVm._uiState.value.canvasViewportSize
        val hitIds = mainVm._uiState.value.images.filter { img ->
            val screenX = viewport.width / 2f + offset.x + img.center.x * scale
            val screenY = viewport.height / 2f + offset.y + img.center.y * scale
            rect.contains(screenX, screenY)
        }.map { it.id }.toSet()
        mainVm._uiState.update { state ->
            val merged = state.selectedImageIds + hitIds
            state.copy(
                selectedImageIds = merged,
                selectedImageId = merged.lastOrNull() ?: state.selectedImageId,
                multiSelectMode = true,
            ).normalizeSelection()
        }
    }
}
