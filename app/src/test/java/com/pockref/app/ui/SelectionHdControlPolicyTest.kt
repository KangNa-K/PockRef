package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionHdControlPolicyTest {

    @Test
    fun stateAndToggleTarget_reflectTheSelectedImages() {
        assertEquals(OriginalResolutionSelectionState.OFF, originalResolutionStateFor(emptyList()))
        assertEquals(OriginalResolutionSelectionState.OFF, originalResolutionStateFor(listOf(false)))
        assertEquals(OriginalResolutionSelectionState.MIXED, originalResolutionStateFor(listOf(true, false)))
        assertEquals(OriginalResolutionSelectionState.ON, originalResolutionStateFor(listOf(true, true)))
        assertTrue(targetOriginalResolutionEnabled(listOf(false)))
        assertFalse(targetOriginalResolutionEnabled(listOf(true)))
    }

    @Test
    fun controlPosition_usesBottomEndWhenSelectionFrameHasRoom() {
        assertEquals(
            Offset(166f, 70f),
            originalResolutionControlPosition(Rect(0f, 0f, 200f, 100f)),
        )
    }

    @Test
    fun controlCenter_rotatesAroundImageCenter() {
        val imageCenter = Offset(100f, 100f)

        assertEquals(
            Offset(166f, 120f),
            projectOriginalResolutionControlCenter(
                imageCenter = imageCenter,
                imageWidthPx = 200f,
                imageHeightPx = 100f,
                rotationDegrees = 0f,
            ),
        )
        assertOffsetEquals(
            expected = Offset(80f, 166f),
            actual = projectOriginalResolutionControlCenter(
                imageCenter = imageCenter,
                imageWidthPx = 200f,
                imageHeightPx = 100f,
                rotationDegrees = 90f,
            ),
        )
        assertOffsetEquals(
            expected = Offset(34f, 80f),
            actual = projectOriginalResolutionControlCenter(
                imageCenter = imageCenter,
                imageWidthPx = 200f,
                imageHeightPx = 100f,
                rotationDegrees = 180f,
            ),
        )
    }

    @Test
    fun controlCenter_followsVisualCornerAfterFlip() {
        assertOffsetEquals(
            expected = Offset(34f, 120f),
            actual = projectOriginalResolutionControlCenter(
                imageCenter = Offset(100f, 100f),
                imageWidthPx = 200f,
                imageHeightPx = 100f,
                rotationDegrees = 0f,
                flipX = true,
            ),
        )
        assertOffsetEquals(
            expected = Offset(120f, 166f),
            actual = projectOriginalResolutionControlCenter(
                imageCenter = Offset(100f, 100f),
                imageWidthPx = 200f,
                imageHeightPx = 100f,
                rotationDegrees = 90f,
                flipY = true,
            ),
        )
    }

    @Test
    fun controlCenter_fallsBackToImageCenterWhenImageIsTooSmall() {
        assertOffsetEquals(
            expected = Offset(100f, 100f),
            actual = projectOriginalResolutionControlCenter(
                imageCenter = Offset(100f, 100f),
                imageWidthPx = 50f,
                imageHeightPx = 44f,
                rotationDegrees = 137f,
            ),
        )
    }

    private fun assertOffsetEquals(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
