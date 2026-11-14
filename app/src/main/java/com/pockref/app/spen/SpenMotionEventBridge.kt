package com.pockref.app.spen

import android.view.MotionEvent
import android.view.InputDevice

/** Converts raw MotionEvent button levels into detector down/up edges. */
class SpenMotionEventBridge(
    private val detector: SpenButtonDetector,
) {
    val mode: SpenMode
        get() = detector.mode

    private var lastStylusButtonBits = 0

    fun onButtonState(buttonState: Int) {
        val stylusBits = buttonState and STYLUS_BUTTON_MASK
        if (stylusBits == 0 && lastStylusButtonBits == 0) return

        if (stylusBits != 0 && lastStylusButtonBits == 0) {
            detector.onDown()
        } else if (stylusBits == 0 && lastStylusButtonBits != 0) {
            detector.onUp()
        }
        lastStylusButtonBits = stylusBits
    }

    fun onMotionEvent(event: MotionEvent) {
        if (!isStylusInput(event.source, (0 until event.pointerCount).map(event::getToolType))) return
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) cancel() else onButtonState(event.buttonState)
    }

    fun cancel() {
        lastStylusButtonBits = 0
        detector.cancel()
    }

    fun reset() {
        lastStylusButtonBits = 0
        detector.reset()
    }

    internal companion object {
        fun isStylusInput(source: Int, toolTypes: List<Int>): Boolean =
            (source and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
                toolTypes.any { it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER }

        val STYLUS_BUTTON_MASK =
            MotionEvent.BUTTON_STYLUS_PRIMARY or
                MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_TERTIARY
    }
}
