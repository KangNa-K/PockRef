package com.pockref.app.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkupSessionTest {
    @Test fun handleDragCommitsOnceAndCancelledDragRestoresObjects() = session { vm ->
        draw(vm, CanvasMarkupTool.RECTANGLE, Offset(-50f,-25f), Offset(50f,25f))
        val id = vm._uiState.value.markupStrokes.single().id
        vm._uiState.value = vm._uiState.value.copy(markupTool=CanvasMarkupTool.SELECT,selectedMarkupIds=setOf(id))
        val before = vm._uiState.value.toMarkupHistorySnapshot()
        val handle = markupHandlePositions(vm._uiState.value).getValue(MarkupHandle.SCALE)
        vm.beginMarkupStroke(handle); vm.updateMarkupStroke(handle*1.5f); vm.updateMarkupStroke(handle*2f); vm.commitMarkupStroke()
        assertEquals(Offset(100f,50f),vm._uiState.value.markupStrokes.single().points.last())
        vm.undoMarkupStep()
        assertEquals(before,vm._uiState.value.toMarkupHistorySnapshot())
        vm._uiState.value = vm._uiState.value.copy(selectedMarkupIds=setOf(id))
        val rotation = markupHandlePositions(vm._uiState.value).getValue(MarkupHandle.ROTATE)
        vm.beginMarkupStroke(rotation); vm.updateMarkupStroke(Offset(-rotation.y,rotation.x))
        vm.cancelMarkupStroke()
        assertEquals(before,vm._uiState.value.toMarkupHistorySnapshot())
        vm.updateMarkupStroke(Offset(500f,500f)); vm.commitMarkupStroke()
        assertEquals(before,vm._uiState.value.toMarkupHistorySnapshot())
    }
    @Test fun reuseCopiesMixedSelectionAndSupportsUndoRedoAndDelete() = session { vm ->
        draw(vm, CanvasMarkupTool.LINE, Offset.Zero, Offset(100f, 0f))
        vm._uiState.value = vm._uiState.value.copy(
            markupTexts = listOf(com.pockref.app.model.CanvasMarkupText(id = "text", text = "备注", position = Offset.Zero)))
        val original = vm._uiState.value.toMarkupHistorySnapshot()
        vm._uiState.value = vm._uiState.value.copy(
            selectedMarkupIds = setOf(original.strokes.single().id, "text"))
        vm.onAction(PockRefAction.DuplicateSelectedMarkup)
        val copied = vm._uiState.value.toMarkupHistorySnapshot()
        assertEquals(2, copied.strokes.size)
        assertEquals(2, copied.texts.size)
        assertEquals(original.strokes.single(), copied.strokes.first())
        assertNotEquals(original.strokes.single().id, copied.strokes.last().id)
        assertNotEquals("text", copied.texts.last().id)
        assertEquals(2, vm._uiState.value.markupSelection.size)
        vm.onAction(PockRefAction.DeleteSelectedMarkup)
        assertEquals(original, vm._uiState.value.toMarkupHistorySnapshot())
        vm.undoMarkupStep()
        assertEquals(copied, vm._uiState.value.toMarkupHistorySnapshot())
        vm.undoMarkupStep()
        assertEquals(original, vm._uiState.value.toMarkupHistorySnapshot())
        vm.redoMarkupStep()
        assertEquals(copied, vm._uiState.value.toMarkupHistorySnapshot())
    }

    private fun session(block: (PockRefViewModel) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val store = ViewModelStore()
            val vm = PockRefViewModel(instrumentation.targetContext.applicationContext as Application)
            store.put("markup-test", vm)
            try {
                vm._uiState.value = PockRefUiState(blankCanvasOpen = true)
                vm.setMarkupModeActive(true)
                block(vm)
            } finally { store.clear(); vm.codec.release() }
        }
    }

    private fun draw(vm: PockRefViewModel, tool: CanvasMarkupTool, from: Offset, to: Offset) {
        vm.onAction(PockRefAction.SetMarkupTool(tool))
        vm.beginMarkupStroke(from)
        vm.updateMarkupStroke(to)
        vm.commitMarkupStroke()
    }

    @Test fun mixedObjectsUndoRedoAndCommitAreOneSceneTransaction() = session { vm ->
        draw(vm, CanvasMarkupTool.PEN, Offset.Zero, Offset(100f, 0f))
        draw(vm, CanvasMarkupTool.RECTANGLE, Offset(200f, 100f), Offset(300f, 200f))
        vm.requestMarkupText()
        vm.onAction(PockRefAction.SetPendingMarkupText("中文标记"))
        vm.onAction(PockRefAction.SetPendingMarkupTextFontSize(18f))
        vm.commitMarkupText()
        val document = vm._uiState.value.toMarkupHistorySnapshot()
        assertEquals(2, document.strokes.size)
        assertEquals(18f, document.texts.single().fontSize)
        assertTrue(vm._uiState.value.toScene().markupStrokes.isEmpty())
        vm.undoMarkupStep()
        assertTrue(vm._uiState.value.markupTexts.isEmpty())
        vm.undoMarkupStep()
        assertEquals(1, vm._uiState.value.markupStrokes.size)
        vm.redoMarkupStep(); vm.redoMarkupStep()
        assertEquals(document, vm._uiState.value.toMarkupHistorySnapshot())
        vm.setMarkupModeActive(false)
        assertEquals(2, vm._uiState.value.toScene().markupStrokes.size)
        vm.onAction(PockRefAction.Undo)
        assertTrue(vm._uiState.value.markupStrokes.isEmpty())
        assertTrue(vm._uiState.value.markupTexts.isEmpty())
        vm.onAction(PockRefAction.Redo)
        assertEquals(document, vm._uiState.value.toMarkupHistorySnapshot())
    }

    @Test fun cancelledMoveAndCancelledSessionRestoreCommittedDocument() = session { vm ->
        draw(vm, CanvasMarkupTool.LINE, Offset.Zero, Offset(100f, 0f))
        vm.setMarkupModeActive(false)
        val original = vm._uiState.value.toMarkupHistorySnapshot()
        vm.setMarkupModeActive(true)
        vm.onAction(PockRefAction.SetMarkupTool(CanvasMarkupTool.SELECT))
        vm.beginMarkupStroke(Offset(50f, 0f)); vm.updateMarkupStroke(Offset(70f, 30f))
        assertNotEquals(original, vm._uiState.value.toMarkupHistorySnapshot())
        assertEquals(original.strokes, vm._uiState.value.toScene().markupStrokes)
        vm.cancelMarkupStroke()
        assertEquals(original, vm._uiState.value.toMarkupHistorySnapshot())
        draw(vm, CanvasMarkupTool.ELLIPSE, Offset(200f, 0f), Offset(300f, 100f))
        vm.cancelMarkupSession()
        assertEquals(original, vm._uiState.value.toMarkupHistorySnapshot())
        assertFalse(vm._uiState.value.markupModeActive)
    }

    @Test fun fastObjectEraseSweepsBetweenSamplesAndUndoRestoresObjects() = session { vm ->
        draw(vm, CanvasMarkupTool.LINE, Offset(0f, -50f), Offset(0f, 50f))
        draw(vm, CanvasMarkupTool.RECTANGLE, Offset(200f, -50f), Offset(250f, 50f))
        val before = vm._uiState.value.toMarkupHistorySnapshot()
        draw(vm, CanvasMarkupTool.ERASER, Offset(-100f, 0f), Offset(350f, 0f))
        assertTrue(vm._uiState.value.markupStrokes.isEmpty())
        vm.undoMarkupStep()
        assertEquals(before, vm._uiState.value.toMarkupHistorySnapshot())
    }

    @Test fun lockedObjectsSurviveEraseAndSelection() = session { vm ->
        val locked = CanvasMarkupStroke(points = listOf(Offset.Zero, Offset(100f, 0f)), locked = true)
        vm._uiState.value = vm._uiState.value.copy(markupStrokes = listOf(locked))
        draw(vm, CanvasMarkupTool.SELECT, Offset(50f, 0f), Offset(80f, 40f))
        assertNull(vm._uiState.value.selectedMarkupId)
        draw(vm, CanvasMarkupTool.ERASER, Offset(-100f, 0f), Offset(200f, 0f))
        assertEquals(listOf(locked), vm._uiState.value.markupStrokes)
    }

    @Test fun singlePointInkIsCommittedButSinglePointShapeIsNot() = session { vm ->
        vm.beginMarkupStroke(Offset(10f, 20f)); vm.commitMarkupStroke()
        assertEquals(1, vm._uiState.value.markupStrokes.size)
        assertTrue(vm._uiState.value.markupStrokes.single().isRenderable)
        vm.onAction(PockRefAction.SetMarkupTool(CanvasMarkupTool.RECTANGLE))
        vm.beginMarkupStroke(Offset.Zero); vm.commitMarkupStroke()
        assertEquals(1, vm._uiState.value.markupStrokes.size)
    }

    @Test fun lassoMovesAndTransformsMixedSelectionAsOneUndoStep() = session { vm ->
        draw(vm, CanvasMarkupTool.LINE, Offset(0f, 0f), Offset(100f, 0f))
        vm.requestMarkupText()
        vm.onAction(PockRefAction.SetPendingMarkupText("组合"))
        vm.onAction(PockRefAction.SetPendingMarkupTextFontSize(18f))
        vm.commitMarkupText()
        vm.onAction(PockRefAction.SetMarkupTool(CanvasMarkupTool.LASSO))
        vm.beginMarkupStroke(Offset(-100f, -100f))
        listOf(Offset(200f, -100f), Offset(200f, 100f), Offset(-100f, 100f)).forEach(vm::updateMarkupStroke)
        vm.commitMarkupStroke()
        assertEquals(2, vm._uiState.value.selectedMarkupIds.size)
        assertEquals(CanvasMarkupTool.SELECT, vm._uiState.value.markupTool)
        val before = vm._uiState.value.toMarkupHistorySnapshot()
        vm.beginMarkupStroke(Offset(80f, 0f)); vm.updateMarkupStroke(Offset(100f, 40f)); vm.commitMarkupStroke()
        assertEquals(before.strokes.single().points.first()+Offset(20f, 40f), vm._uiState.value.markupStrokes.single().points.first())
        assertEquals(before.texts.single().position+Offset(20f, 40f), vm._uiState.value.markupTexts.single().position)
        val moved = vm._uiState.value.toMarkupHistorySnapshot()
        vm.onAction(PockRefAction.TransformSelectedMarkup(scale = 2f, rotation = 0.5f))
        assertEquals(moved.texts.single().fontSize*2, vm._uiState.value.markupTexts.single().fontSize)
        assertEquals(moved.strokes.single().id, vm._uiState.value.markupStrokes.single().id)
        vm.undoMarkupStep()
        assertEquals(moved, vm._uiState.value.toMarkupHistorySnapshot())
        vm.undoMarkupStep()
        assertEquals(before, vm._uiState.value.toMarkupHistorySnapshot())
    }

    @Test fun cancelledLassoDoesNotCreateInkOrHistory() = session { vm ->
        vm.onAction(PockRefAction.SetMarkupTool(CanvasMarkupTool.LASSO))
        vm.beginMarkupStroke(Offset.Zero); vm.updateMarkupStroke(Offset(100f, 100f))
        vm.cancelMarkupStroke(); vm.commitMarkupStroke()
        assertTrue(vm._uiState.value.markupLassoPoints.isEmpty())
        assertTrue(vm._uiState.value.markupStrokes.isEmpty())
        assertFalse(vm._uiState.value.canUndo)
    }

    @Test fun partialEraseCommitsOneStepAndCancelRestoresOriginalInk() = session { vm ->
        draw(vm, CanvasMarkupTool.PEN, Offset(-100f, 0f), Offset(100f, 0f))
        val before = vm._uiState.value.toMarkupHistorySnapshot()
        draw(vm, CanvasMarkupTool.INK_ERASER, Offset(0f, -50f), Offset(0f, 50f))
        assertEquals(2, vm._uiState.value.markupStrokes.size)
        vm.undoMarkupStep()
        assertEquals(before, vm._uiState.value.toMarkupHistorySnapshot())
        vm.beginMarkupStroke(Offset.Zero)
        assertEquals(2, vm._uiState.value.markupStrokes.size)
        vm.cancelMarkupStroke()
        assertEquals(before, vm._uiState.value.toMarkupHistorySnapshot())
    }
}
