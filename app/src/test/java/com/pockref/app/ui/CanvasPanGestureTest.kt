package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPanGestureTest {

    @Test
    fun singlePointerPanDelta_isIgnored() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 1,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
            singleFingerPanEnabled = false,
        )

        assertEquals(Offset.Zero, delta)
    }

    @Test
    fun singlePointerPanDelta_tracksMovement_whenSingleFingerPanIsEnabled() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 1,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
            singleFingerPanEnabled = true,
        )

        assertEquals(Offset(15f, 25f), delta)
    }

    @Test
    fun twoPointerPanDelta_tracksCentroidMovement() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 2,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
            singleFingerPanEnabled = false,
        )

        assertEquals(Offset(15f, 25f), delta)
    }

    @Test
    fun twoPointerPanDelta_isIgnored_whenSingleFingerPanIsEnabled() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 2,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
            singleFingerPanEnabled = true,
        )

        assertEquals(Offset.Zero, delta)
    }

    @Test
    fun frozenCanvas_skipsPointerWorkWhileAnyFingerIsStillDown() {
        assertTrue(
            shouldSkipFrozenCanvasPointerWork(
                canvasFrozen = true,
                hasPressedPointers = true,
            ),
        )
    }

    @Test
    fun frozenCanvas_allowsReleaseProcessingAfterAllFingersLift() {
        assertFalse(
            shouldSkipFrozenCanvasPointerWork(
                canvasFrozen = true,
                hasPressedPointers = false,
            ),
        )
    }

    @Test
    fun softLockedCanvas_skipsTwoFingerImageSelection() {
        assertFalse(
            shouldSelectImageForTwoFingerCanvasGesture(
                globalImageLock = true,
            ),
        )
    }

    @Test
    fun unlockedCanvas_keepsTwoFingerImageSelection() {
        assertTrue(
            shouldSelectImageForTwoFingerCanvasGesture(
                globalImageLock = false,
            ),
        )
    }

    @Test
    fun softLockedCanvas_usesDedicatedTwoFingerNavigation() {
        assertTrue(
            shouldUseSoftLockedTwoFingerCanvasNavigation(
                globalImageLock = true,
                canvasFrozen = false,
            ),
        )
    }

    @Test
    fun softLockedCanvas_usesLocalPreviewForSingleFingerPan() {
        assertTrue(
            shouldUseLocalSoftLockedSingleFingerPan(
                globalImageLock = true,
                canvasFrozen = false,
                singleFingerPanEnabled = true,
            ),
        )
    }

    @Test
    fun twoFingerCanvasPan_usesLocalPreviewUntilTransformCommits() {
        assertTrue(
            shouldUseLocalTwoFingerCanvasPan(
                canvasFrozen = false,
                transformCommitted = false,
            ),
        )
        assertFalse(
            shouldUseLocalTwoFingerCanvasPan(
                canvasFrozen = false,
                transformCommitted = true,
            ),
        )
    }

    @Test
    fun canvasPanPresentation_staysAtTheCommittedTargetUntilStateCatchesUp() {
        val stateOffset = Offset(10f, 20f)
        val presentationOffset = Offset(40f, 70f)

        assertEquals(
            presentationOffset,
            renderCanvasOffsetForLocalPan(stateOffset, presentationOffset),
        )
        assertEquals(
            presentationOffset,
            renderCanvasOffsetForLocalPan(presentationOffset, null),
        )
    }

    @Test
    fun canvasPanCommit_skipsAnAlreadyDispatchedTarget() {
        val stateOffset = Offset(10f, 20f)
        val committedTarget = Offset(40f, 70f)

        assertEquals(
            Offset(30f, 50f),
            canvasPanCommitDelta(
                presentationOffset = committedTarget,
                lastCommittedOffset = null,
                stateOffset = stateOffset,
            ),
        )
        assertEquals(
            Offset.Zero,
            canvasPanCommitDelta(
                presentationOffset = committedTarget,
                lastCommittedOffset = committedTarget,
                stateOffset = stateOffset,
            ),
        )
    }

    @Test
    fun frozenCanvas_skipsLocalTwoFingerPanPreview() {
        assertFalse(
            shouldUseLocalTwoFingerCanvasPan(
                canvasFrozen = true,
                transformCommitted = false,
            ),
        )
    }

    @Test
    fun unlockedFrozenOrDisabledSingleFingerPan_skipsLocalPreview() {
        assertFalse(
            shouldUseLocalSoftLockedSingleFingerPan(
                globalImageLock = false,
                canvasFrozen = false,
                singleFingerPanEnabled = true,
            ),
        )
        assertFalse(
            shouldUseLocalSoftLockedSingleFingerPan(
                globalImageLock = true,
                canvasFrozen = true,
                singleFingerPanEnabled = true,
            ),
        )
        assertFalse(
            shouldUseLocalSoftLockedSingleFingerPan(
                globalImageLock = true,
                canvasFrozen = false,
                singleFingerPanEnabled = false,
            ),
        )
    }

    @Test
    fun frozenOrUnlockedCanvas_usesTheExistingTwoFingerGesturePath() {
        assertFalse(
            shouldUseSoftLockedTwoFingerCanvasNavigation(
                globalImageLock = true,
                canvasFrozen = true,
            ),
        )
        assertFalse(
            shouldUseSoftLockedTwoFingerCanvasNavigation(
                globalImageLock = false,
                canvasFrozen = false,
            ),
        )
    }

    @Test
    fun lockedCanvasPinch_requiresTheExistingScaleThreshold() {
        assertFalse(
            hasCrossedCanvasPinchZoomThreshold(
                initialDistance = 100f,
                currentDistance = 111f,
            ),
        )
        assertTrue(
            hasCrossedCanvasPinchZoomThreshold(
                initialDistance = 100f,
                currentDistance = 112f,
            ),
        )
    }

    @Test
    fun threePointerPanDelta_isIgnored() {
        val delta = canvasPanDeltaForPointerCount(
            pointerCount = 3,
            previousCentroid = Offset(10f, 20f),
            centroid = Offset(25f, 45f),
            singleFingerPanEnabled = false,
        )

        assertEquals(Offset.Zero, delta)
    }
}
