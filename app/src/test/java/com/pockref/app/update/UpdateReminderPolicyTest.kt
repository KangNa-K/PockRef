package com.pockref.app.update

import org.junit.Assert.*
import org.junit.Test

class UpdateReminderPolicyTest {
    @Test fun checksOnFirstLaunchAndAfterSixHours() {
        assertTrue(UpdateReminderPolicy.shouldCheck(1, 0))
        assertFalse(UpdateReminderPolicy.shouldCheck(1001, 1000))
        assertTrue(UpdateReminderPolicy.shouldCheck(1000 + AppUpdateController.CHECK_INTERVAL_MS, 1000))
    }

    @Test fun clockRollbackDoesNotDisableChecks() {
        assertTrue(UpdateReminderPolicy.shouldCheck(1000, 2000))
    }

    @Test fun ignoreIsScopedToOneVersion() {
        assertFalse(UpdateReminderPolicy.shouldNotify(36, 36, -1, 0, 100))
        assertTrue(UpdateReminderPolicy.shouldNotify(37, 36, -1, 0, 100))
    }

    @Test fun snoozeExpiresAndDoesNotHideANewerRelease() {
        assertFalse(UpdateReminderPolicy.shouldNotify(36, -1, 36, 200, 100))
        assertTrue(UpdateReminderPolicy.shouldNotify(36, -1, 36, 200, 200))
        assertTrue(UpdateReminderPolicy.shouldNotify(37, -1, 36, 200, 100))
    }
}
