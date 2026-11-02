package com.pockref.app.ui

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.isCanvasOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankCanvasFlowTest {
    @Test
    fun initialState_doesNotHaveAnOpenCanvas() {
        val state = PockRefUiState()

        assertFalse(state.isCanvasOpen)
    }

    @Test
    fun openBlankCanvas_preservesSettingsAndRecentScenes_clearsSceneAndTemporaryState() {
        val recentScenes = listOf(RecentSceneEntry(uri = "content://scene", label = "Scene", source = "test"))
        val image = SceneImage(
            name = "image.png",
            mimeType = "image/png",
            intrinsicWidth = 100,
            intrinsicHeight = 80,
            center = Offset(10f, 20f),
            displaySize = Size2D(100f, 80f),
        )
        val stroke = CanvasMarkupStroke(points = listOf(Offset.Zero, Offset(1f, 1f)))
        val markupText = CanvasMarkupText(text = "note", position = Offset(2f, 3f))

        val opened = PockRefUiState(
            backgroundColor = Color.Red,
            themeMode = ThemeMode.DARK,
            recentScenes = recentScenes,
            images = listOf(image),
            markupStrokes = listOf(stroke),
            markupTexts = listOf(markupText),
            recentMultiSelectMode = true,
            recentSelection = setOf("content://scene"),
            bulkDeleteConfirmOpen = true,
            importSourceMenuOpen = true,
            selectedImageId = image.id,
            selectedImageIds = setOf(image.id),
            multiSelectMode = true,
            boxSelectMode = true,
            boxSelectRect = RectF(1f, 2f, 3f, 4f),
            boxSelectPreviewIds = setOf(image.id),
            boxSelectStart = Offset(5f, 6f),
            markupModeActive = true,
            draftMarkupStroke = stroke,
            markupSessionBaseStrokes = listOf(stroke),
            markupSessionBaseTexts = listOf(markupText),
            pendingMarkupTextPosition = Offset(7f, 8f),
            pendingMarkupTextValue = "draft",
            pendingMarkupTextFontSize = 42f,
            editingMarkupTextId = markupText.id,
            cropImageId = image.id,
            cropRect = CropRect(0.1f, 0.2f, 0.8f, 0.9f),
            cropAspect = CropAspect.RATIO_4_3,
            cropRotation = 15f,
            canvasScale = 2f,
            canvasOffset = Offset(9f, 10f),
            canvasViewportSize = Size2D(1000f, 800f),
            transientDragImageIds = setOf(image.id),
            transientDragDelta = Offset(11f, 12f),
        )

        val result = opened.openBlankCanvas()

        assertTrue(result.blankCanvasOpen)
        assertTrue(result.isCanvasOpen)
        assertEquals(recentScenes, result.recentScenes)
        assertEquals(Color.Red, result.backgroundColor)
        assertEquals(ThemeMode.DARK, result.themeMode)
        assertEquals(listOf(image), result.images)
        assertEquals(listOf(stroke), result.markupStrokes)
        assertEquals(listOf(markupText), result.markupTexts)
        assertFalse(result.recentMultiSelectMode)
        assertTrue(result.recentSelection.isEmpty())
        assertFalse(result.bulkDeleteConfirmOpen)
        assertFalse(result.importSourceMenuOpen)
        assertNull(result.selectedImageId)
        assertTrue(result.selectedImageIds.isEmpty())
        assertFalse(result.multiSelectMode)
        assertFalse(result.boxSelectMode)
        assertNull(result.boxSelectRect)
        assertTrue(result.boxSelectPreviewIds.isEmpty())
        assertNull(result.boxSelectStart)
        assertFalse(result.markupModeActive)
        assertNull(result.draftMarkupStroke)
        assertNull(result.markupSessionBaseStrokes)
        assertNull(result.markupSessionBaseTexts)
        assertNull(result.pendingMarkupTextPosition)
        assertEquals("", result.pendingMarkupTextValue)
        assertNull(result.editingMarkupTextId)
        assertNull(result.cropImageId)
        assertEquals(CropRect(), result.cropRect)
        assertEquals(CropAspect.FREE, result.cropAspect)
        assertEquals(0f, result.cropRotation, 0f)
        assertEquals(1f, result.canvasScale, 0f)
        assertEquals(Offset.Zero, result.canvasOffset)
        assertEquals(opened.canvasViewportSize, result.canvasViewportSize)
        assertTrue(result.transientDragImageIds.isEmpty())
        assertEquals(Offset.Zero, result.transientDragDelta)
    }

    @Test
    fun blankCanvasRoundTrip_preservesMeasuredViewportForAutoLayout() {
        val viewport = Size2D(1080f, 2376f)
        val measured = PockRefUiState(canvasViewportSize = viewport)

        val opened = measured.openBlankCanvas()
        val closed = opened.closeBlankCanvas()
        val reopened = closed.openBlankCanvas()

        assertEquals(viewport, opened.canvasViewportSize)
        assertEquals(viewport, closed.canvasViewportSize)
        assertEquals(viewport, reopened.canvasViewportSize)
    }

    @Test
    fun closeBlankCanvas_clearsTemporaryState_preservesSettingsAndSceneContent() {
        val image = SceneImage(
            name = "image.png",
            mimeType = "image/png",
            intrinsicWidth = 100,
            intrinsicHeight = 80,
            center = Offset.Zero,
            displaySize = Size2D(100f, 80f),
        )
        val stroke = CanvasMarkupStroke(points = listOf(Offset.Zero, Offset(1f, 1f)))
        val markupText = CanvasMarkupText(text = "note", position = Offset.Zero)
        val state = PockRefUiState(
            sceneName = "Saved scene",
            backgroundColor = Color.Blue,
            themeMode = ThemeMode.DARK,
            images = listOf(image),
            markupStrokes = listOf(stroke),
            markupTexts = listOf(markupText),
            selectedImageId = image.id,
            selectedImageIds = setOf(image.id),
            multiSelectMode = true,
            boxSelectMode = true,
            boxSelectRect = RectF(1f, 2f, 3f, 4f),
            boxSelectPreviewIds = setOf(image.id),
            boxSelectStart = Offset(5f, 6f),
            markupModeActive = true,
            draftMarkupStroke = stroke,
            markupSessionBaseStrokes = listOf(stroke),
            markupSessionBaseTexts = listOf(markupText),
            pendingMarkupTextPosition = Offset(7f, 8f),
            pendingMarkupTextValue = "draft",
            pendingMarkupTextFontSize = 42f,
            editingMarkupTextId = markupText.id,
            cropImageId = image.id,
            cropRect = CropRect(0.1f, 0.2f, 0.8f, 0.9f),
            cropAspect = CropAspect.RATIO_4_3,
            cropRotation = 15f,
            selectionFlash = com.pockref.app.model.SelectionFlash(image.id, 123L),
            blankCanvasOpen = true,
        )

        val result = state.closeBlankCanvas()

        assertFalse(result.blankCanvasOpen)
        assertTrue(result.isCanvasOpen)
        assertEquals(state.sceneName, result.sceneName)
        assertEquals(state.backgroundColor, result.backgroundColor)
        assertEquals(state.themeMode, result.themeMode)
        assertEquals(state.images, result.images)
        assertEquals(state.markupStrokes, result.markupStrokes)
        assertEquals(state.markupTexts, result.markupTexts)
        assertNull(result.selectedImageId)
        assertTrue(result.selectedImageIds.isEmpty())
        assertFalse(result.multiSelectMode)
        assertFalse(result.boxSelectMode)
        assertNull(result.boxSelectRect)
        assertTrue(result.boxSelectPreviewIds.isEmpty())
        assertNull(result.boxSelectStart)
        assertFalse(result.markupModeActive)
        assertNull(result.draftMarkupStroke)
        assertNull(result.markupSessionBaseStrokes)
        assertNull(result.markupSessionBaseTexts)
        assertNull(result.pendingMarkupTextPosition)
        assertEquals("", result.pendingMarkupTextValue)
        assertNull(result.editingMarkupTextId)
        assertNull(result.cropImageId)
        assertEquals(CropRect(), result.cropRect)
        assertEquals(CropAspect.FREE, result.cropAspect)
        assertEquals(0f, result.cropRotation, 0f)
        assertNull(result.selectionFlash)
    }

    @Test
    fun closeBlankCanvas_fromTrulyBlankState_closesCanvas() {
        val result = PockRefUiState(blankCanvasOpen = true, settingsSheetOpen = true).closeBlankCanvas()

        assertFalse(result.blankCanvasOpen)
        assertFalse(result.isCanvasOpen)
        assertFalse(result.settingsSheetOpen)
    }

    @Test
    fun isPurSceneFileName_acceptsPurExtensionOnly() {
        assertTrue(isPurSceneFileName("reference.pur"))
        assertTrue(isPurSceneFileName("reference.PUR"))
        assertFalse(isPurSceneFileName("reference"))
        assertFalse(isPurSceneFileName("reference.png"))
        assertFalse(isPurSceneFileName(null))
    }
}
