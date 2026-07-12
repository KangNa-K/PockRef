package com.pockref.app.ui

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

internal const val CANVAS_PAN_VIEWPORT_DECODE_DELAY_MS = 80L

@OptIn(FlowPreview::class)
internal fun Flow<Unit>.debounceCanvasPanViewportUpdates(): Flow<Unit> =
    debounce(CANVAS_PAN_VIEWPORT_DECODE_DELAY_MS)
