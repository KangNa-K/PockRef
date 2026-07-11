package com.pockref.app.data

import android.content.Context

internal fun shouldRestoreBackup(
    previousSessionActive: Boolean,
    hasExternalOpenRequest: Boolean,
    alreadyChecked: Boolean,
): Boolean = previousSessionActive && !hasExternalOpenRequest && !alreadyChecked

class RecoverySessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun beginSession(): Boolean {
        val previousSessionActive = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, true).commit()
        return previousSessionActive
    }

    fun resetSession() {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).commit()
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, true).commit()
    }

    fun endSession() {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).commit()
    }

    private companion object {
        const val PREFS_NAME = "pockref_recovery_session"
        const val KEY_SESSION_ACTIVE = "session_active"
    }
}
