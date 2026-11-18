package com.pockref.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowGeometryTest {
    private val start = FloatingWindowBounds(200, 150, 400, 300)

    private fun drag(target: FloatingWindowDragTarget, dx: Int, dy: Int) = start.dragged(
        target, dx, dy,
        screenWidth = 1000, screenBottom = 800, screenTop = 30,
        minWidth = 180, minHeight = 220,
    )

    @Test fun eachCornerResizesWithOppositeCornerFixed() {
        assertEquals(FloatingWindowBounds(220, 180, 380, 270), drag(FloatingWindowDragTarget.TOP_LEFT, 20, 30))
        assertEquals(FloatingWindowBounds(200, 180, 420, 270), drag(FloatingWindowDragTarget.TOP_RIGHT, 20, 30))
        assertEquals(FloatingWindowBounds(220, 150, 380, 330), drag(FloatingWindowDragTarget.BOTTOM_LEFT, 20, 30))
        assertEquals(FloatingWindowBounds(200, 150, 420, 330), drag(FloatingWindowDragTarget.BOTTOM_RIGHT, 20, 30))
    }

    @Test fun minimumSizeDoesNotPushOppositeCorner() {
        assertEquals(FloatingWindowBounds(420, 230, 180, 220), drag(FloatingWindowDragTarget.TOP_LEFT, 900, 900))
        assertEquals(FloatingWindowBounds(200, 230, 180, 220), drag(FloatingWindowDragTarget.TOP_RIGHT, -900, 900))
        assertEquals(FloatingWindowBounds(420, 150, 180, 220), drag(FloatingWindowDragTarget.BOTTOM_LEFT, 900, -900))
        assertEquals(FloatingWindowBounds(200, 150, 180, 220), drag(FloatingWindowDragTarget.BOTTOM_RIGHT, -900, -900))
    }

    @Test fun allCornersRespectScreenAndStatusBarBounds() {
        assertEquals(FloatingWindowBounds(0, 30, 600, 420), drag(FloatingWindowDragTarget.TOP_LEFT, -900, -900))
        assertEquals(FloatingWindowBounds(200, 30, 800, 420), drag(FloatingWindowDragTarget.TOP_RIGHT, 900, -900))
        assertEquals(FloatingWindowBounds(0, 150, 600, 650), drag(FloatingWindowDragTarget.BOTTOM_LEFT, -900, 900))
        assertEquals(FloatingWindowBounds(200, 150, 800, 650), drag(FloatingWindowDragTarget.BOTTOM_RIGHT, 900, 900))
    }

    @Test fun movingPreservesSizeAtEveryScreenEdge() {
        assertEquals(FloatingWindowBounds(0, 30, 400, 300), drag(FloatingWindowDragTarget.MOVE, -900, -900))
        assertEquals(FloatingWindowBounds(600, 500, 400, 300), drag(FloatingWindowDragTarget.MOVE, 900, 900))
    }

    @Test fun displacementIsAlwaysRelativeToGestureStart() {
        drag(FloatingWindowDragTarget.TOP_LEFT, -900, -900)
        assertEquals(start, drag(FloatingWindowDragTarget.TOP_LEFT, 0, 0))
        drag(FloatingWindowDragTarget.BOTTOM_RIGHT, -900, -900)
        assertEquals(start, drag(FloatingWindowDragTarget.BOTTOM_RIGHT, 0, 0))
    }

    @Test fun windowFitsAgainAfterScreenShrinksOrRotates() {
        assertEquals(
            FloatingWindowBounds(0, 30, 900, 670),
            FloatingWindowBounds(100, 150, 1000, 800).fitWithin(900, 30, 700),
        )
        assertEquals(
            FloatingWindowBounds(500, 400, 400, 300),
            FloatingWindowBounds(700, 600, 400, 300).fitWithin(900, 30, 700),
        )
    }
}
