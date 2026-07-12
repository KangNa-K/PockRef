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
