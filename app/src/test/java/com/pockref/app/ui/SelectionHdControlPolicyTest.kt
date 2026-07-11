package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionHdControlPolicyTest {

    @Test
    fun stateFor_emptyAndAllFalse_areOff() {
        assertEquals(OriginalResolutionSelectionState.OFF, stateFor(emptyList()))
        assertEquals(
            OriginalResolutionSelectionState.OFF,
            stateFor(listOf(false, false)),
        )
    }

    @Test
    fun stateFor_allTrueNonempty_isOn() {
        assertEquals(
            OriginalResolutionSelectionState.ON,
            stateFor(listOf(true, true)),
        )
    }

    @Test
    fun stateFor_mixedValues_isMixed() {
        assertEquals(
            OriginalResolutionSelectionState.MIXED,
            stateFor(listOf(true, false)),
        )
    }

    @Test
    fun targetOriginalResolutionEnabled_turnsOnUnlessAllValuesAreOn() {
        assertTrue(targetOriginalResolutionEnabled(listOf(false, false)))
        assertTrue(targetOriginalResolutionEnabled(listOf(true, false)))
        assertFalse(targetOriginalResolutionEnabled(listOf(true, true)))
        assertFalse(targetOriginalResolutionEnabled(emptyList()))
    }

    @Test
    fun shouldShowOriginalResolutionControl_onlyWhenRotationAndGifAreInactive() {
        assertTrue(
            shouldShowOriginalResolutionControl(
                rotationActive = false,
                containsAnimatedGif = false,
            ),
        )
        assertFalse(
            shouldShowOriginalResolutionControl(
                rotationActive = true,
                containsAnimatedGif = false,
            ),
        )
        assertFalse(
            shouldShowOriginalResolutionControl(
                rotationActive = false,
                containsAnimatedGif = true,
            ),
        )
    }

    @Test
    fun originalResolutionControlPosition_usesBottomEndInsetWhenFrameFits() {
        assertEquals(
            Offset(166f, 70f),
            originalResolutionControlPosition(Rect(0f, 0f, 200f, 100f)),
        )
    }

    @Test
    fun originalResolutionControlPosition_centersOnAxesTooSmallForControlAndInsets() {
        assertEquals(
            Offset(25f, 22f),
            originalResolutionControlPosition(Rect(0f, 0f, 50f, 44f)),
        )
    }

    @Test
    fun originalResolutionControlPosition_centersOnlyTheUndersizedAxis() {
        assertEquals(
            Offset(25f, 70f),
            originalResolutionControlPosition(Rect(0f, 0f, 50f, 100f)),
        )
        assertEquals(
            Offset(166f, 22f),
            originalResolutionControlPosition(Rect(0f, 0f, 200f, 44f)),
        )
    }
}
