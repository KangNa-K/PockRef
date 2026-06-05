package com.pockref.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for the rotation angle-snap state.
 *
 * Covers two things:
 * - The [AngleSnapGuide] data class itself (equality, defaults, copy).
 * - The new [PockRefUiState] fields introduced for rotation snap
 *   ([PockRefUiState.rotationSnapEnabled] default-true and
 *   [PockRefUiState.angleSnapGuide] default-null).
 *
 * These tests don't exercise the snap detection itself — that lives
 * in `TransformHelpers.detectAngleSnap` and is covered by
 * [com.pockref.app.ui.PockRefTransformTest]. They just verify the
 * state shapes match what the transform layer writes into them.
 */
class AngleSnapGuideTest {

    @Test
    fun angleSnapGuide_dataClass_equality() {
        val a = AngleSnapGuide(
            snappedDegrees = 90f,
            isEngaged = true,
            startTimeMs = 1_700_000_000_000L,
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun angleSnapGuide_inequalityOnEachField() {
        val base = AngleSnapGuide(snappedDegrees = 0f, isEngaged = true, startTimeMs = 100L)
        assertNotEquals(base, base.copy(snappedDegrees = 90f))
        assertNotEquals(base, base.copy(isEngaged = false))
        assertNotEquals(base, base.copy(startTimeMs = 200L))
    }

    @Test
    fun angleSnapGuide_copyPreservesUntouchedFields() {
        val a = AngleSnapGuide(snappedDegrees = 180f, isEngaged = true, startTimeMs = 42L)
        val b = a.copy(isEngaged = false)
        assertEquals(180f, b.snappedDegrees, 0f)
        assertFalse(b.isEngaged)
        assertEquals(42L, b.startTimeMs)
    }

    @Test
    fun pureRefUiState_rotationSnapEnabled_defaultsTrue() {
        // The default behavior is "snap on" — most users want it.
        val s = PockRefUiState()
        assertTrue("rotationSnapEnabled must default to true", s.rotationSnapEnabled)
    }

    @Test
    fun pureRefUiState_angleSnapGuide_defaultsNull() {
        // No gesture in progress → no live guide.
        val s = PockRefUiState()
        assertNull(s.angleSnapGuide)
    }

    @Test
    fun pureRefUiState_canCarryAngleSnapGuide() {
        val guide = AngleSnapGuide(
            snappedDegrees = 90f,
            isEngaged = true,
            startTimeMs = 1L,
        )
        val s = PockRefUiState(angleSnapGuide = guide)
        assertEquals(guide, s.angleSnapGuide)
    }

    @Test
    fun pureRefUiState_canDisableRotationSnap() {
        // User opt-out path: set rotationSnapEnabled = false. Other
        // defaults should be unaffected.
        val s = PockRefUiState(rotationSnapEnabled = false)
        assertFalse(s.rotationSnapEnabled)
        // Default snap-guide still null.
        assertNull(s.angleSnapGuide)
    }

    @Test
    fun pureRefUiState_copyClearsAngleSnapGuide() {
        // Mirror the transform-layer commit pattern: copy() with
        // angleSnapGuide = null to clear a stale guide.
        val withGuide = PockRefUiState(
            angleSnapGuide = AngleSnapGuide(0f, true, 1L),
        )
        val cleared = withGuide.copy(angleSnapGuide = null)
        assertNull(cleared.angleSnapGuide)
        // rotationSnapEnabled is unaffected.
        assertTrue(cleared.rotationSnapEnabled)
    }
}
