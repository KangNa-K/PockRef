package com.pockref.app

internal enum class FloatingWindowDragTarget(val left: Boolean = false, val top: Boolean = false) {
    MOVE,
    TOP_LEFT(left = true, top = true),
    TOP_RIGHT(top = true),
    BOTTOM_LEFT(left = true),
    BOTTOM_RIGHT,
}

internal data class FloatingWindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun fitWithin(screenWidth: Int, screenTop: Int, screenBottom: Int): FloatingWindowBounds {
        val fittedWidth = width.coerceAtMost(screenWidth)
        val fittedHeight = height.coerceAtMost((screenBottom - screenTop).coerceAtLeast(1))
        return FloatingWindowBounds(
            x.coerceIn(0, (screenWidth - fittedWidth).coerceAtLeast(0)),
            y.coerceIn(screenTop, (screenBottom - fittedHeight).coerceAtLeast(screenTop)),
            fittedWidth,
            fittedHeight,
        )
    }

    fun dragged(
        target: FloatingWindowDragTarget,
        dx: Int,
        dy: Int,
        screenWidth: Int,
        screenBottom: Int,
        screenTop: Int,
        minWidth: Int,
        minHeight: Int,
    ): FloatingWindowBounds {
        if (target == FloatingWindowDragTarget.MOVE) {
            return copy(
                x = (x + dx).coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
                y = (y + dy).coerceIn(screenTop, (screenBottom - height).coerceAtLeast(screenTop)),
            )
        }
        val right = x + width
        val bottom = y + height
        // Clamp the moving edges, preserving the opposite corner even at a limit.
        val leftEdge = if (target.left) (x + dx).coerceIn(0, (right - minWidth).coerceAtLeast(0)) else x
        val topEdge = if (target.top) {
            (y + dy).coerceIn(screenTop, (bottom - minHeight).coerceAtLeast(screenTop))
        } else y
        val rightEdge = if (target.left) right else {
            (right + dx).coerceIn(x + minWidth, screenWidth.coerceAtLeast(x + minWidth))
        }
        val bottomEdge = if (target.top) bottom else {
            (bottom + dy).coerceIn(y + minHeight, screenBottom.coerceAtLeast(y + minHeight))
        }
        return FloatingWindowBounds(leftEdge, topEdge, rightEdge - leftEdge, bottomEdge - topEdge)
    }
}
