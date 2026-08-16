package com.pockref.app.spen

import android.view.MotionEvent

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

    fun reset() {
        lastStylusButtonBits = 0
        detector.reset()
    }

    private companion object {
        val STYLUS_BUTTON_MASK =
            MotionEvent.BUTTON_STYLUS_PRIMARY or
                MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_TERTIARY
    }
}
