package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.*
import org.junit.Test

class DroppedImagesLayoutTest {
    private fun image(id: String, width: Float = 400f, height: Float = 300f) = SceneImage(
        id = id, name = id, mimeType = "image/png",
        intrinsicWidth = width.toInt(), intrinsicHeight = height.toInt(),
        displaySize = Size2D(width, height), center = Offset.Zero,
    )
    private val state = PockRefUiState(
        canvasViewportSize = Size2D(1200f, 800f),
        canvasOriginInWindow = Offset(20f, 60f),
        canvasOffset = Offset(100f, -40f), canvasScale = 0.25f,
    )
    @Test fun convertsWindowCoordinatesUsingZoomAndPan() {
        val placed = layoutDroppedImages(listOf(image("new")), state, Offset(320f, 260f)).single()
        assertEquals(-1600f, placed.center.x, 0.01f)
        assertEquals(-640f, placed.center.y, 0.01f)
    }
    @Test fun distantExistingImagesDoNotPullBatchDown() {
        val originals = (0..299).map { image("old$it").copy(center = Offset(100000f, it * 10000f)) }
        val batch = listOf(image("a"), image("b"))
        val placed = layoutDroppedImages(batch, state.copy(images = originals), Offset(420f, 360f))
        val left = placed.minOf { it.center.x - imageRotatedFootprint(it).width / 2f }
        val right = placed.maxOf { it.center.x + imageRotatedFootprint(it).width / 2f }
        assertEquals(-1200f, (left + right) / 2f, 0.01f)
        assertEquals(-240f, placed.first().center.y, 0.01f)
        assertEquals(2990000f, originals.last().center.y, 0f)
    }
    @Test fun repeatedDropsAvoidEarlierBatchesWithoutMovingThem() {
        val batch = listOf(image("a"))
        val first = layoutDroppedImages(batch, state, Offset(420f, 360f))
        val second = layoutDroppedImages(listOf(image("b")), state.copy(images = first), Offset(420f, 360f))
        assertNoOverlap(first + second)
        assertEquals(0.25f, state.canvasScale, 0f)
    }
    @Test fun normalizesHeightsWhilePreservingAspectRatios() {
        val batch = listOf(image("portrait", 100f, 900f), image("landscape", 800f, 120f), image("square", 300f, 300f))
        val placed = layoutDroppedImages(batch, state, null)
        val height = placed.first().displaySize.height * placed.first().scale
        placed.zip(batch).forEach { (actual, original) ->
            assertEquals(height, actual.displaySize.height * actual.scale, 0.01f)
            assertEquals(original.displaySize, actual.displaySize)
            assertEquals(original.intrinsicWidth, actual.intrinsicWidth)
            assertEquals(original.intrinsicHeight, actual.intrinsicHeight)
        }
        assertNoOverlap(placed)
    }
    @Test fun avoidsRotatedAndLockedExistingImages() {
        val original = image("old", 1800f, 1800f).copy(center = Offset(-400f, 160f), rotationDegrees = 45f, locked = true)
        val placed = layoutDroppedImages(listOf(image("new")), state.copy(images = listOf(original)), null)
        assertNoOverlap(listOf(original) + placed)
        assertTrue(kotlin.math.abs(placed.single().center.y - original.center.y) < 10000f)
    }
    @Test fun denseCanvasStillFindsFreeSpace() {
        val old = (0..99).map { image("old$it", 500f, 500f).copy(center = Offset((it % 10) * 450f - 2200f, (it / 10) * 450f - 2200f)) }
        val placed = layoutDroppedImages((0..7).map { image("new$it") }, state.copy(images = old), null)
        placed.forEach { new -> old.forEach { existing -> assertNoOverlap(listOf(new, existing)) } }
        assertNoOverlap(placed)
    }
    @Test fun matchesExistingScaledHeightAndIgnoresRotation() {
        val old = image("old", 400f, 300f).copy(scale = 2f, rotationDegrees = 90f)
        val batch = listOf(image("portrait", 100f, 900f), image("landscape", 800f, 120f))
        val placed = layoutDroppedImages(batch, state.copy(images = listOf(old)), null)
        placed.forEach { assertEquals(600f, it.displaySize.height * it.scale, 0.01f) }
        assertNoOverlap(placed + old)
    }
    @Test fun largeBatchDoesNotShrinkBelowExistingHeight() {
        val old = image("old", 300f, 600f).copy(center = Offset(20000f, 20000f))
        val placed = layoutDroppedImages((0..39).map { image("new$it") }, state.copy(images = listOf(old)), null)
        placed.forEach { assertEquals(600f, it.displaySize.height * it.scale, 0.01f) }
        assertNoOverlap(placed + old)
    }
    @Test fun existingSizeIsIndependentOfCanvasZoom() {
        val old = image("old", 300f, 420f)
        listOf(0.1f, 1f, 4f).forEach { zoom ->
            val placed = layoutDroppedImages(listOf(image("new")), state.copy(images = listOf(old), canvasScale = zoom), null)
            assertEquals(420f, placed.single().displaySize.height * placed.single().scale, 0.01f)
        }
    }
    @Test fun medianSizeIgnoresOversizedOutliers() {
        val old = listOf(image("a", 100f, 300f), image("b", 100f, 300f), image("outlier", 100f, 9000f))
        val placed = layoutDroppedImages(listOf(image("new")), state.copy(images = old), null)
        assertEquals(300f, placed.single().displaySize.height * placed.single().scale, 0.01f)
    }
    private fun assertNoOverlap(images: List<SceneImage>) {
        images.forEachIndexed { index, a -> images.drop(index + 1).forEach { b ->
            val fa = imageRotatedFootprint(a)
            val fb = imageRotatedFootprint(b)
            val x = kotlin.math.abs(a.center.x - b.center.x) < (fa.width + fb.width) / 2f - 0.01f
            val y = kotlin.math.abs(a.center.y - b.center.y) < (fa.height + fb.height) / 2f - 0.01f
            assertFalse("Overlap between ${a.id} and ${b.id}", x && y)
        } }
    }
    @Test fun mixedAspectRatiosFitWithoutOverlaps() {
        val batch = (0..16).map { image("$it", if (it % 2 == 0) 720f else 100f, if (it % 3 == 0) 1400f else 120f) }
        val placed = layoutDroppedImages(batch, state, Offset(620f, 460f))
        val left = placed.minOf { it.center.x - it.displaySize.width * it.scale / 2f }
        val right = placed.maxOf { it.center.x + it.displaySize.width * it.scale / 2f }
        val top = placed.minOf { it.center.y - it.displaySize.height * it.scale / 2f }
        val bottom = placed.maxOf { it.center.y + it.displaySize.height * it.scale / 2f }
        assertTrue((right - left) * state.canvasScale <= 780.01f)
        assertTrue((bottom - top) * state.canvasScale <= 520.01f)
        placed.forEachIndexed { i, a -> placed.drop(i + 1).forEach { b ->
            val overlapsX = kotlin.math.abs(a.center.x - b.center.x) < (a.displaySize.width * a.scale + b.displaySize.width * b.scale) / 2f
            val overlapsY = kotlin.math.abs(a.center.y - b.center.y) < (a.displaySize.height * a.scale + b.displaySize.height * b.scale) / 2f
            assertFalse(overlapsX && overlapsY)
        } }
    }
    @Test fun missingCoordinatesUseVisibleCenter() {
        val placed = layoutDroppedImages(listOf(image("new")), state, null).single()
        assertEquals(-400f, placed.center.x, 0.01f)
        assertEquals(160f, placed.center.y, 0.01f)
    }
    @Test fun emptyBatchAndUnmeasuredCanvasAreSafe() {
        assertTrue(layoutDroppedImages(emptyList(), PockRefUiState(), null).isEmpty())
        val placed = layoutDroppedImages(listOf(image("new")), PockRefUiState(canvasScale = 0f), null).single()
        assertEquals(Offset.Zero, placed.center)
        assertTrue(placed.scale.isFinite() && placed.scale > 0f)
    }
}
