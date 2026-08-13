package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.Size2D

internal fun PockRefUiState.openBlankCanvas(): PockRefUiState = copy(
    selectedImageId = null,
    selectedImageIds = emptySet(),
    multiSelectMode = false,
    boxSelectMode = false,
    boxSelectRect = null,
    boxSelectPreviewIds = emptySet(),
    boxSelectStart = null,
    recentMultiSelectMode = false,
    recentSelection = emptySet(),
    bulkDeleteConfirmOpen = false,
    importSourceMenuOpen = false,
    markupModeActive = false,
    draftMarkupStroke = null,
    markupSessionBaseStrokes = null,
    markupSessionBaseTexts = null,
    pendingMarkupTextPosition = null,
    pendingMarkupTextValue = "",
    pendingMarkupTextFontSize = 80f,
    editingMarkupTextId = null,
    cropImageId = null,
    cropRect = CropRect(),
    cropAspect = CropAspect.FREE,
    cropRotation = 0f,
    canvasScale = 1f,
    canvasOffset = Offset.Zero,
    canvasViewportSize = Size2D(0f, 0f),
    transientDragImageIds = emptySet(),
    transientDragDelta = Offset.Zero,
    canUndo = false,
    canRedo = false,
    canDirectSave = false,
    hasUnsavedChanges = false,
    fittedImageId = null,
    selectionFlash = null,
    selectionSheetShowTick = 0,
    lastSheetShowMs = 0L,
    selectionSheetDismissTick = 0,
    blankCanvasOpen = true,
)

internal fun PockRefUiState.closeBlankCanvas(): PockRefUiState = copy(
    importSourceMenuOpen = false,
    recentMultiSelectMode = false,
    recentSelection = emptySet(),
    bulkDeleteConfirmOpen = false,
    selectedImageId = null,
    selectedImageIds = emptySet(),
    multiSelectMode = false,
    boxSelectMode = false,
    boxSelectRect = null,
    boxSelectPreviewIds = emptySet(),
    boxSelectStart = null,
    markupModeActive = false,
    draftMarkupStroke = null,
    markupSessionBaseStrokes = null,
    markupSessionBaseTexts = null,
    pendingMarkupTextPosition = null,
    pendingMarkupTextValue = "",
    pendingMarkupTextFontSize = 80f,
    editingMarkupTextId = null,
    cropImageId = null,
    cropRect = CropRect(),
    cropAspect = CropAspect.FREE,
    cropRotation = 0f,
    canvasScale = 1f,
    canvasOffset = Offset.Zero,
    canvasViewportSize = Size2D(0f, 0f),
    transientDragImageIds = emptySet(),
    transientDragDelta = Offset.Zero,
    fittedImageId = null,
    selectionFlash = null,
    selectionSheetShowTick = 0,
    lastSheetShowMs = 0L,
    selectionSheetDismissTick = 0,
    blankCanvasOpen = false,
)

internal fun isPurSceneFileName(displayName: String?): Boolean =
    !displayName.isNullOrBlank() &&
        displayName.substringAfterLast('.', "").equals("pur", ignoreCase = true)
