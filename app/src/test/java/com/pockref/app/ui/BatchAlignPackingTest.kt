package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.AlignMode
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAlignPackingTest {

    private val epsilon = 0.001f
    private val gap = 15f

    private fun img(
        id: String,
        center: Offset,
        displayW: Float,
        displayH: Float,
        rotationDegrees: Float = 0f,
        scale: Float = 1f,
    ) = SceneImage(
        id = id,
        name = id,
        mimeType = "image/png",
        intrinsicWidth = displayW.toInt(),
        intrinsicHeight = displayH.toInt(),
        center = center,
        displaySize = Size2D(displayW, displayH),
        rotationDegrees = rotationDegrees,
        scale = scale,
    )

    @Test
    fun alignLeft_packsVerticallyWithoutOverlap() {
        val aligned = alignEdgeAndPackWithoutOverlap(
            selected = listOf(
                img("a", center = Offset(50f, 50f), displayW = 100f, displayH = 100f),
                img("b", center = Offset(80f, 60f), displayW = 40f, displayH = 220f),
                img("c", center = Offset(30f, 80f), displayW = 180f, displayH = 60f),
            ),
            mode = AlignMode.ALIGN_LEFT,
            gap = gap,
        )

        assertSameLeftEdge(aligned)
        assertPackedTopToBottom(aligned)
    }

    @Test
    fun alignRight_packsVerticallyWithoutOverlap() {
        val aligned = alignEdgeAndPackWithoutOverlap(
            selected = listOf(
                img("a", center = Offset(50f, 50f), displayW = 100f, displayH = 100f),
                img("b", center = Offset(80f, 60f), displayW = 40f, displayH = 220f),
                img("c", center = Offset(30f, 80f), displayW = 180f, displayH = 60f),
            ),
            mode = AlignMode.ALIGN_RIGHT,
            gap = gap,
        )

        assertSameRightEdge(aligned)
        assertPackedTopToBottom(aligned)
    }

    @Test
    fun alignTop_packsHorizontallyWithoutOverlap() {
        val aligned = alignEdgeAndPackWithoutOverlap(
            selected = listOf(
                img("a", center = Offset(50f, 50f), displayW = 100f, displayH = 100f),
                img("b", center = Offset(60f, 80f), displayW = 220f, displayH = 40f),
                img("c", center = Offset(80f, 30f), displayW = 60f, displayH = 180f),
            ),
            mode = AlignMode.ALIGN_TOP,
            gap = gap,
        )

        assertSameTopEdge(aligned)
        assertPackedLeftToRight(aligned)
    }

    @Test
    fun alignBottom_packsHorizontallyWithoutOverlap() {
        val aligned = alignEdgeAndPackWithoutOverlap(
            selected = listOf(
                img("a", center = Offset(50f, 50f), displayW = 100f, displayH = 100f),
                img("b", center = Offset(60f, 80f), displayW = 220f, displayH = 40f),
                img("c", center = Offset(80f, 30f), displayW = 60f, displayH = 180f),
            ),
            mode = AlignMode.ALIGN_BOTTOM,
            gap = gap,
        )

        assertSameBottomEdge(aligned)
        assertPackedLeftToRight(aligned)
    }

    @Test
    fun edgePacking_usesRotatedFootprint() {
        val aligned = alignEdgeAndPackWithoutOverlap(
            selected = listOf(
                img("a", center = Offset(0f, 0f), displayW = 100f, displayH = 40f, rotationDegrees = 45f),
                img("b", center = Offset(10f, 10f), displayW = 80f, displayH = 120f, rotationDegrees = -30f),
            ),
            mode = AlignMode.ALIGN_LEFT,
            gap = gap,
        )

        assertSameLeftEdge(aligned)
        assertPackedTopToBottom(aligned)
    }

    private fun assertSameLeftEdge(images: List<SceneImage>) {
        val expected = left(images.first())
        images.forEach { assertEquals(expected, left(it), epsilon) }
    }

    private fun assertSameRightEdge(images: List<SceneImage>) {
        val expected = right(images.first())
        images.forEach { assertEquals(expected, right(it), epsilon) }
    }

    private fun assertSameTopEdge(images: List<SceneImage>) {
        val expected = top(images.first())
        images.forEach { assertEquals(expected, top(it), epsilon) }
    }

    private fun assertSameBottomEdge(images: List<SceneImage>) {
        val expected = bottom(images.first())
        images.forEach { assertEquals(expected, bottom(it), epsilon) }
    }

    private fun assertPackedTopToBottom(images: List<SceneImage>) {
        images.zipWithNext { upper, lower ->
            assertTrue("${upper.id} overlaps ${lower.id}", top(lower) >= bottom(upper) + gap - epsilon)
        }
    }

    private fun assertPackedLeftToRight(images: List<SceneImage>) {
        images.zipWithNext { previous, next ->
            assertTrue("${previous.id} overlaps ${next.id}", left(next) >= right(previous) + gap - epsilon)
        }
    }

    private fun left(image: SceneImage): Float {
        val fp = imageRotatedFootprint(image)
        return image.center.x - fp.width / 2f
    }

    private fun right(image: SceneImage): Float {
        val fp = imageRotatedFootprint(image)
        return image.center.x + fp.width / 2f
    }

    private fun top(image: SceneImage): Float {
        val fp = imageRotatedFootprint(image)
        return image.center.y - fp.height / 2f
    }

    private fun bottom(image: SceneImage): Float {
        val fp = imageRotatedFootprint(image)
        return image.center.y + fp.height / 2f
    }
}
