package com.jetsport.ft10csync.data

import android.content.Context
import com.jetsport.ft10csync.sync.Ft10cTimeSyncIntervals

/** Keeps persistent preferences out of Activity and presentation code. */
class SettingsRepository(context: Context) {
    companion object {
        val TIME_SYNC_INTERVALS_MINUTES = Ft10cTimeSyncIntervals.SUPPORTED_MINUTES
        const val DEFAULT_TIME_SYNC_INTERVAL_MINUTES = Ft10cTimeSyncIntervals.DEFAULT_MINUTES
    }
    private val app = context.applicationContext
    private val settings = app.getSharedPreferences("jetsport_settings", Context.MODE_PRIVATE)

    var language: String
        get() = settings.getString("language", "ru") ?: "ru"
        set(value) { settings.edit().putString("language", value).apply() }

    var automaticTimeSync: Boolean
        get() = settings.getBoolean("auto_time_sync", true)
        set(value) { settings.edit().putBoolean("auto_time_sync", value).apply() }

    /** Period between automatic A3 time writes, stored in minutes. */
    var automaticTimeSyncIntervalMinutes: Int
        get() = Ft10cTimeSyncIntervals.normalize(settings.getInt("auto_time_sync_interval_minutes", DEFAULT_TIME_SYNC_INTERVAL_MINUTES))
        set(value) {
            settings.edit().putInt("auto_time_sync_interval_minutes", Ft10cTimeSyncIntervals.normalize(value)).apply()
        }

    var phoneFinderSoundEnabled: Boolean
        get() = settings.getBoolean("phone_finder_sound_enabled", true)
        set(value) { settings.edit().putBoolean("phone_finder_sound_enabled", value).apply() }

    var phoneFinderVibrationEnabled: Boolean
        get() = settings.getBoolean("phone_finder_vibration_enabled", true)
        set(value) { settings.edit().putBoolean("phone_finder_vibration_enabled", value).apply() }

    /** null = system default alarm ringtone; SILENT = explicitly selected silence; otherwise a ringtone URI. */
    var phoneFinderRingtoneUri: String?
        get() = settings.getString("phone_finder_ringtone_uri", null)
        set(value) { settings.edit().apply { if (value == null) remove("phone_finder_ringtone_uri") else putString("phone_finder_ringtone_uri", value) }.apply() }

    var darkTheme: Boolean
        get() = settings.getBoolean("dark_theme", false)
        set(value) { settings.edit().putBoolean("dark_theme", value).apply() }

    /** DMY = dd.MM.yyyy, MDY = MM.dd.yyyy. */
    var dateFormat: String
        get() = settings.getString("date_format", "DMY") ?: "DMY"
        set(value) { settings.edit().putString("date_format", if (value == "MDY") "MDY" else "DMY").apply() }

    var batteryA2LastResponseAt: Long
        get() = settings.getLong("battery_a2_last_request_at", 0L)
        set(value) { settings.edit().putLong("battery_a2_last_request_at", value).apply() }

    /** Last valid proprietary A2 battery percentage, retained for UI restoration. */
    var lastBatteryPercent: Int?
        get() = if (settings.contains("battery_a2_last_percent")) {
            settings.getInt("battery_a2_last_percent", 0).coerceIn(0, 100)
        } else null
        set(value) {
            settings.edit().apply {
                if (value == null) remove("battery_a2_last_percent")
                else putInt("battery_a2_last_percent", value.coerceIn(0, 100))
            }.apply()
        }

}
