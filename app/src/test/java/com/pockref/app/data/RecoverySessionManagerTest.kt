package com.pockref.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverySessionManagerTest {

    @Test
    fun abnormalPreviousSession_withoutExternalOpen_shouldRestore() {
        assertTrue(
            shouldRestoreBackup(
                previousSessionActive = true,
                hasExternalOpenRequest = false,
                alreadyChecked = false,
            ),
        )
    }

    @Test
    fun externalOpenRequest_hasPriority() {
        assertFalse(
            shouldRestoreBackup(
                previousSessionActive = true,
                hasExternalOpenRequest = true,
                alreadyChecked = false,
            ),
        )
    }

    @Test
    fun normalPreviousSession_doesNotRestore() {
        assertFalse(
            shouldRestoreBackup(
                previousSessionActive = false,
                hasExternalOpenRequest = false,
                alreadyChecked = false,
            ),
        )
    }

    @Test
    fun activityRecreation_doesNotRestoreTwice() {
        assertFalse(
            shouldRestoreBackup(
                previousSessionActive = true,
                hasExternalOpenRequest = false,
                alreadyChecked = true,
            ),
        )
    }
}
