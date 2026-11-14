package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [computeEdgeAutoScroll], the per-tick screen
 * delta the drag-to-edge auto-scroll worker dispatches as a
 * PanCanvas action when the pointer is inside the edge zone.
 *
 * The contract:
 *   - pointer in the safe center of the viewport → Offset.Zero
 *     (no auto-scroll)
 *   - pointer at the very edge of the zone → 0 magnitude
     (smooth start)
 *   - pointer right at the edge of the viewport → maxSpeedPx
     in the direction "away from the edge, into the safe
     center" (quadratic ramp)
 *   - sign convention: LEFT edge → -dx, RIGHT edge → +dx,
     TOP edge → -dy, BOTTOM edge → +dy
 */
class EdgeAutoScrollTest {

    private val viewport = Size(1000f, 800f)
    private val edgeZone = 100f
    private val maxSpeed = 40f

    @Test
    fun draggedSelectionStaysUnderPointerDuringAutoScroll() {
        val actions = mutableListOf<PockRefAction>()
        val scroll = Offset(30f, -20f)

        dispatchEdgeAutoScroll(scroll, setOf("first", "second"), actions::add)

        assertEquals(
            listOf(PockRefAction.PanCanvas(scroll), PockRefAction.UpdateImageDrag(-scroll)),
            actions,
        )
        val scale = 2f
        val worldDrag = (actions[1] as PockRefAction.UpdateImageDrag).delta / scale
        assertEquals(Offset.Zero, scroll + worldDrag * scale)
    }

    @Test
    fun selectionBoxAutoScrollDoesNotMoveImages() {
        val actions = mutableListOf<PockRefAction>()
        dispatchEdgeAutoScroll(Offset(10f, 0f), emptySet(), actions::add)
        assertEquals(listOf(PockRefAction.PanCanvas(Offset(10f, 0f))), actions)
    }

    @Test
    fun pointerAtCenter_returnsZero() {
        val pos = Offset(500f, 400f)  // dead center
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(Offset.Zero, scroll)
    }

    @Test
    fun pointerInSafeZone_returnsZero() {
        // The safe zone is the area more than edgeZone from
        // any edge. With edgeZone=100 and viewport 1000x800,
        // the safe rect is (100, 100) to (900, 700).
        val points = listOf(
            Offset(150f, 400f),    // 50px from top of safe zone, far from sides
            Offset(500f, 150f),    // 50px from top, far from sides
            Offset(500f, 650f),    // 50px from bottom of safe
            Offset(850f, 400f),    // 50px from right side of safe
        )
        for (p in points) {
            val scroll = computeEdgeAutoScroll(p, viewport, edgeZone, maxSpeed)
            assertEquals(
                "expected Zero at $p, got $scroll",
                Offset.Zero,
                scroll,
            )
        }
    }

    @Test
    fun pointerAtLeftEdge_returnsPositiveMaxDx() {
        // Pointer touching the very left edge: distLeft = 0,
        // t = 1 - 0/100 = 1, dx = +40 * 1 * 1 = +40.
        // (V2 fix: was -40. The convention is "show more on
        // the side the pointer is on", so LEFT edge → +dx.)
        val pos = Offset(0f, 400f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(maxSpeed, scroll.x, 0.001f)
        assertEquals(0f, scroll.y, 0.001f)
    }

    @Test
    fun pointerAtRightEdge_returnsNegativeMaxDx() {
        val pos = Offset(1000f, 400f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(-maxSpeed, scroll.x, 0.001f)
        assertEquals(0f, scroll.y, 0.001f)
    }

    @Test
    fun pointerAtTopEdge_returnsPositiveMaxDy() {
        val pos = Offset(500f, 0f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(0f, scroll.x, 0.001f)
        assertEquals(maxSpeed, scroll.y, 0.001f)
    }

    @Test
    fun pointerAtBottomEdge_returnsNegativeMaxDy() {
        val pos = Offset(500f, 800f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(0f, scroll.x, 0.001f)
        assertEquals(-maxSpeed, scroll.y, 0.001f)
    }

    @Test
    fun pointerAtZoneBoundary_returnsZero() {
        // Pointer exactly at the zone boundary (100px from
        // left edge): t = 1 - 100/100 = 0, dx = 0.
        val pos = Offset(100f, 400f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(0f, scroll.x, 0.001f)
        assertEquals(0f, scroll.y, 0.001f)
    }

    @Test
    fun pointerInZone_returnsQuadraticRamp() {
        // Pointer 50px from left edge: t = 1 - 50/100 = 0.5,
        // dx = +40 * 0.5 * 0.5 = +10. (Quadratic ramp, not
        // linear — feels more natural at the slow end.)
        val pos = Offset(50f, 400f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(10f, scroll.x, 0.001f)
        assertEquals(0f, scroll.y, 0.001f)
    }

    @Test
    fun pointerInCorner_returnsDiagonalMax() {
        // Pointer at the bottom-right corner: both x and y
        // should hit ±maxSpeed (V2: bottom-right is +x
        // surface and -y surface, so... wait, no, corner
        // is BOTH right edge AND bottom edge. The function
        // only enters ONE of the `else if` branches per
        // axis, so each axis gets its own sign. Right
        // edge → dx negative. Bottom edge → dy negative.
        // So the corner scroll is (-max, -max).)
        val pos = Offset(1000f, 800f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, maxSpeed)
        assertEquals(-maxSpeed, scroll.x, 0.001f)
        assertEquals(-maxSpeed, scroll.y, 0.001f)
    }

    @Test
    fun zeroEdgeZone_returnsZero() {
        // Defensive: a zero edge zone means "no auto-scroll
        // ever" — the function should short-circuit before
        // any division-by-zone math.
        val pos = Offset(0f, 0f)
        val scroll = computeEdgeAutoScroll(pos, viewport, 0f, maxSpeed)
        assertEquals(Offset.Zero, scroll)
    }

    @Test
    fun zeroMaxSpeed_returnsZero() {
        val pos = Offset(0f, 0f)
        val scroll = computeEdgeAutoScroll(pos, viewport, edgeZone, 0f)
        assertEquals(Offset.Zero, scroll)
    }

    @Test
    fun pointerExactlyAtViewportEdge_yieldsFullSpeed() {
        // The viewport's right edge is at x=1000 (width=1000).
        // A pointer at x=1000 means distRight=0, t=1, full
        // speed. (V2: sign is negative for right edge.)
        val scroll = computeEdgeAutoScroll(
            Offset(1000f, 400f),
            Size(1000f, 800f),
            100f,
            20f,
        )
        assertTrue(
            "expected -dx at right edge, got $scroll",
            scroll.x < 0f,
        )
        assertEquals(-20f, scroll.x, 0.001f)
    }
}
