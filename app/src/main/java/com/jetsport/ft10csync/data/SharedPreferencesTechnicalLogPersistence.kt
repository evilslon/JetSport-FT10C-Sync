package com.jetsport.ft10csync.data

import android.content.Context

/** Stores the bounded diagnostic journal in app-private preferences across process restarts. */
class SharedPreferencesTechnicalLogPersistence(context: Context) : TechnicalLogPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): String = preferences.getString(KEY_SNAPSHOT, "").orEmpty()

    override fun save(snapshot: String) {
        // apply() updates the in-memory preference value immediately and writes asynchronously.
        preferences.edit().putString(KEY_SNAPSHOT, snapshot).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "jetsport_technical_log"
        const val KEY_SNAPSHOT = "journal_snapshot"
    }
}
