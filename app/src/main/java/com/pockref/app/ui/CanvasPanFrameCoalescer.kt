package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset

internal class CanvasPanFrameCoalescer {
    private var pendingDelta = Offset.Zero

    fun enqueue(delta: Offset) {
        pendingDelta += delta
    }

    fun flush(): Offset {
        val delta = pendingDelta
        pendingDelta = Offset.Zero
        return delta
    }
}

internal fun splitCanvasPanDelta(delta: Offset): List<Offset> {
    if (delta == Offset.Zero) return emptyList()

    val steps = mutableListOf<Offset>()
    var remaining = delta
    while (remaining != Offset.Zero) {
        val step = Offset(
            x = remaining.x.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX),
            y = remaining.y.coerceIn(-MAX_CANVAS_PAN_STEP_PX, MAX_CANVAS_PAN_STEP_PX),
        )
        steps += step
        remaining -= step
    }
    return steps
}
