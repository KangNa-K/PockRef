package com.pockref.app.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

internal fun shouldRestoreBackup(
    previousSessionActive: Boolean,
    hasExternalOpenRequest: Boolean,
    alreadyChecked: Boolean,
): Boolean = previousSessionActive && !hasExternalOpenRequest && !alreadyChecked

// commit() is intentional: the session-active flag must reach disk synchronously,
// otherwise a crash right after launch could lose it and skip backup recovery.
@SuppressLint("ApplySharedPref")
class RecoverySessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun beginSession(): Boolean {
        val previousSessionActive = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        prefs.edit(commit = true) { putBoolean(KEY_SESSION_ACTIVE, true) }
        return previousSessionActive
    }

    fun resetSession() {
        prefs.edit(commit = true) { putBoolean(KEY_SESSION_ACTIVE, false) }
        prefs.edit(commit = true) { putBoolean(KEY_SESSION_ACTIVE, true) }
    }

    fun endSession() {
        prefs.edit(commit = true) { putBoolean(KEY_SESSION_ACTIVE, false) }
    }

    private companion object {
        const val PREFS_NAME = "pockref_recovery_session"
        const val KEY_SESSION_ACTIVE = "session_active"
    }
}
