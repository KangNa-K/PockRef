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
}
