package com.winghaeger.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var defaultSeekJumpSec: Int
        get() = p.getInt(KEY_SEEK_JUMP, 10)
        set(v) = p.edit().putInt(KEY_SEEK_JUMP, v.coerceAtLeast(1)).apply()

    var defaultTrimStepMs: Long
        get() = p.getLong(KEY_TRIM_STEP_MS, 10000L)
        set(v) = p.edit().putLong(KEY_TRIM_STEP_MS, v.coerceAtLeast(1000L)).apply()

    var defaultVolumeStepPercent: Int
        get() = p.getInt(KEY_VOLUME_STEP, 5)
        set(v) = p.edit().putInt(KEY_VOLUME_STEP, v.coerceIn(1, 100)).apply()

    var defaultEnhancement: EnhancementMode
        get() = EnhancementMode.fromKey(p.getString(KEY_ENHANCEMENT, null))
        set(v) = p.edit().putString(KEY_ENHANCEMENT, v.storageKey).apply()

    companion object {
        private const val PREFS = "wing_haeger_prefs"
        private const val KEY_SEEK_JUMP = "default_seek_jump_sec"
        private const val KEY_TRIM_STEP_MS = "default_trim_step_ms"
        private const val KEY_VOLUME_STEP = "default_volume_step_percent"
        private const val KEY_ENHANCEMENT = "default_enhancement"
    }
}
