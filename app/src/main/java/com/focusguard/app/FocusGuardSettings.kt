package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences

class FocusGuardSettings(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "smart_focus_guard_prefs"
        private const val KEY_INSTAGRAM = "pref_instagram_enabled"
        private const val KEY_YOUTUBE = "pref_youtube_enabled"
        private const val KEY_FRICTION_SECONDS = "pref_friction_seconds"
        private const val KEY_PASS_EXPIRY = "pref_pass_expiry_epoch_ms"
        private const val KEY_BLOCKED_COUNT = "pref_sessions_blocked_count"
        private const val KEY_MASTER_GUARD = "pref_master_guard_enabled"
    }

    var isMasterGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_GUARD, false)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_GUARD, value).apply()

    var isInstagramEnabled: Boolean
        get() = prefs.getBoolean(KEY_INSTAGRAM, true)
        set(value) = prefs.edit().putBoolean(KEY_INSTAGRAM, value).apply()

    var isYouTubeEnabled: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE, true)
        set(value) = prefs.edit().putBoolean(KEY_YOUTUBE, value).apply()

    var frictionSeconds: Int
        get() = prefs.getInt(KEY_FRICTION_SECONDS, 10)
        set(value) = prefs.edit().putInt(KEY_FRICTION_SECONDS, value).apply()

    var sessionsBlockedToday: Int
        get() = prefs.getInt(KEY_BLOCKED_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BLOCKED_COUNT, value).apply()

    fun recordBlockedSession() {
        sessionsBlockedToday = sessionsBlockedToday + 1
    }

    fun grantPass(durationMinutes: Int = 15) {
        val expiryMs = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit().putLong(KEY_PASS_EXPIRY, expiryMs).apply()
    }

    fun hasActivePass(): Boolean {
        val expiryMs = prefs.getLong(KEY_PASS_EXPIRY, 0L)
        return System.currentTimeMillis() < expiryMs
    }

    fun getRemainingPassMinutes(): Int {
        val expiryMs = prefs.getLong(KEY_PASS_EXPIRY, 0L)
        val remainingMs = expiryMs - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 60000L).toInt() + 1 else 0
    }
}
