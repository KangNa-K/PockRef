package com.pockref.app.ui

import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.isCanvasOpen

/** Centralizes the mutually-exclusive visibility rules for canvas chrome. */
internal data class CanvasChromePolicy(
    val showTopToolbar: Boolean,
    val showGlobalLock: Boolean,
    val showActionPanel: Boolean,
    val showLayerPanel: Boolean,
    val showSelectionPanel: Boolean,
    val showSelectionMode: Boolean,
) {
    companion object {
        fun resolve(state: PockRefUiState): CanvasChromePolicy {
            val canvasOpen = state.isCanvasOpen
            val practiceInactive = state.practiceState == null
            val lockEngaged = state.globalImageLock || state.lockCanvasFrozen
            val cropInactive = state.cropImageId == null
            val markupInactive = !state.markupModeActive

            return CanvasChromePolicy(
                showTopToolbar = canvasOpen && practiceInactive && markupInactive &&
                    (!lockEngaged || state.blankCanvasOpen),
                showGlobalLock = practiceInactive && markupInactive &&
                    !state.lockButtonHidden && state.images.isNotEmpty(),
                showActionPanel = canvasOpen && cropInactive && practiceInactive &&
                    !lockEngaged && markupInactive,
                showLayerPanel = state.images.isNotEmpty() && cropInactive && practiceInactive &&
                    !lockEngaged && markupInactive,
                showSelectionPanel = state.practiceState != null ||
                    (state.selectedImageIds.isNotEmpty() && !lockEngaged && markupInactive),
                showSelectionMode = practiceInactive && !lockEngaged && markupInactive &&
                    state.selectedImageIds.isEmpty(),
            )
        }
    }
}
