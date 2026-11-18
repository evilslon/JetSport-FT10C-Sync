package com.jetsport.ft10csync.data

import android.content.Context

/** Keeps persistent preferences out of Activity and presentation code. */
class SettingsRepository(context: Context) {
    private val app = context.applicationContext
    private val settings = app.getSharedPreferences("jetsport_settings", Context.MODE_PRIVATE)

    var language: String
        get() = settings.getString("language", "ru") ?: "ru"
        set(value) { settings.edit().putString("language", value).apply() }

    var automaticTimeSync: Boolean
        get() = settings.getBoolean("auto_time_sync", true)
        set(value) { settings.edit().putBoolean("auto_time_sync", value).apply() }

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

}
