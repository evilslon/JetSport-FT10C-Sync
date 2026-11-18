package com.jetsport.ft10csync.data

import android.content.Context

/** Persists the last date explicitly reported by the bracelet's live B1 stream. */
class ActiveDayRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("jetsport_active_day", Context.MODE_PRIVATE)

    var lastBraceletDayKey: String?
        get() = preferences.getString(KEY_LAST_BRACELET_DAY, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_LAST_BRACELET_DAY)
                else putString(KEY_LAST_BRACELET_DAY, value)
            }.apply()
        }

    private companion object {
        const val KEY_LAST_BRACELET_DAY = "last_bracelet_day_key"
    }
}
