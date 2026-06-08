package com.pockref.app.ui

import android.graphics.RectF
import com.pockref.app.model.SelectionMode
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.BoxSelectionKind
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

    /**
     * V_NOW (CAD-style box select): like [beginBoxSelect] but
     * also stores the screen-space drag start point so the first
     * [updateBoxSelectRect] can decide whether the drag is
     * WINDOW (left -> right) or CROSSING (right -> left). The
     * default [BoxSelectionKind.WINDOW] matches the old
     * `centerPoint.contains` behaviour for the most common
     * "drag a rectangle around the things I want" path.
     */
    internal fun beginBoxSelectAt(start: Offset) {
        mainVm._uiState.update { state ->
            state.copy(
                selectedImageIds = emptySet(),
                selectedImageId = null,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
                boxSelectStart = start,
                boxSelectKind = BoxSelectionKind.WINDOW,
            )
        }
    }

    internal fun updateBoxSelectRect(rect: RectF, current: Offset) {
 val scale = mainVm._uiState.value.canvasScale
 val offset = mainVm._uiState.value.canvasOffset
 val viewport = mainVm._uiState.value.canvasViewportSize
 val start = mainVm._uiState.value.boxSelectStart

 // V_NOW (CAD-style box select): direction lock. Only flip
 // WINDOW -> CROSSING, never back — once the drag has gone
 // "right to left" the kind sticks for the rest of the
 // gesture even if the pointer wobbles right again. Matches
 // AutoCAD / Visio / Figma. The first UpdateBoxSelectRect
 // call (when current.x crosses start.x from above) is the
 // trigger; subsequent calls read the locked kind from state.
 val currentKind = mainVm._uiState.value.boxSelectKind
 val newKind = if (
 currentKind == BoxSelectionKind.WINDOW &&
 start != null &&
 current.x < start.x
 ) {
 BoxSelectionKind.CROSSING
 } else {
 currentKind
 }

 // V_NOW (CAD-style box select): strict AABB-based hit test
 // instead of the old "rect.contains(centerPoint)" approach.
 // The old behaviour was technically a third mode ("center
 // in rect") that's neither WINDOW nor CROSSING — a small
 // object whose center sits inside the rect was always
 // selected even if the rect only clipped a corner. Using
 // imageRotatedAabb (the rotated, world-space AABB already
 // used by the minimap) gives accurate strict-containment
 // for WINDOW and accurate intersection for CROSSING. The
 // transform world -> screen mirrors what the renderer
 // applies to the image itself (canvasScale + canvasOffset
 // + viewport/2 origin), so a rect drawn by the user's
 // finger and an AABB drawn by the image overlap correctly.
 val selectionRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
 val previewIds = mainVm._uiState.value.images.filter { img ->
 val worldAabb = imageRotatedAabb(img)
 val screenLeft = viewport.width /2f + offset.x + worldAabb.left * scale
 val screenTop = viewport.height /2f + offset.y + worldAabb.top * scale
 val screenRight = viewport.width /2f + offset.x + worldAabb.right * scale
 val screenBottom = viewport.height /2f + offset.y + worldAabb.bottom * scale
 val imageRect = Rect(screenLeft, screenTop, screenRight, screenBottom)
 when (newKind) {
 BoxSelectionKind.WINDOW ->
 selectionRect.contains(
 imageRect.left,
 imageRect.top,
 imageRect.right,
 imageRect.bottom,
 )
 BoxSelectionKind.CROSSING ->
 selectionRect.intersects(imageRect)
 }
 }.map { it.id }.toSet()

 mainVm._uiState.update { state ->
 state.copy(
 boxSelectRect = rect,
 boxSelectPreviewIds = previewIds,
 boxSelectKind = newKind,
 )
 }
}
        val scale = mainVm._uiState.value.canvasScale
        val offset = mainVm._uiState.value.canvasOffset
        val viewport = mainVm._uiState.value.canvasViewportSize
        // V_NOW (2026-06-08 锁定图可框选): 之前 locked 图
        // [if (img.locked) return@filter false] 跳过,框选矩形
        // 拖到它们上面不进 preview。user 期望"被框中的图就被
        // 选中",不管锁不锁 — 锁只阻止**变换**(移动/缩放/
        // 旋转/翻转),**不**阻止**选中**。保留 locked 图进
        // preview(全锁/单锁),跟 [lassoSelect] 行为对齐
        // (lassoSelect **没** 有 locked filter)。
        val previewIds = mainVm._uiState.value.images.filter { img ->
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
                boxSelectStart = null,
            ).normalizeSelection()
        }
    }

    internal fun exitBoxSelectMode() {
        mainVm._uiState.update { state ->
            state.copy(
                boxSelectMode = false,
                boxSelectRect = null,
                boxSelectPreviewIds = emptySet(),
                boxSelectStart = null,
                boxSelectKind = BoxSelectionKind.WINDOW,
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
