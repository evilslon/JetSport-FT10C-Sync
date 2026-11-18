package com.jetsport.ft10csync.presentation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tracks the last calendar day reported by a live bracelet B1 packet.
 * B2 history records never change this state. The last valid day is retained
 * across disconnects for diagnostics, but only a B1 packet observed in the
 * current connection session may select the dashboard day. This allows an
 * intentional manual bracelet-date change to become the active dashboard day
 * without letting a persisted date from yesterday override the phone date at
 * cold start.
 */
class ActiveDayState(
    initialDayKey: String? = null,
    private val onDayChanged: (String) -> Unit = {}
) {
    var braceletDayKey: String? = initialDayKey?.takeIf(::isValidDayKey)
        private set

    private var livePacketObservedThisSession: Boolean = false

    fun onLivePacket(dayKey: String) {
        require(isValidDayKey(dayKey)) { "Invalid bracelet day key: $dayKey" }
        livePacketObservedThisSession = true
        if (braceletDayKey == dayKey) return
        braceletDayKey = dayKey
        onDayChanged(dayKey)
    }

    fun onDisconnected() {
        // Keep the last reported date persisted, but stop treating it as a
        // current-session authority. A fresh B1 packet is required on reconnect.
        livePacketObservedThisSession = false
    }

    /**
     * Before the first B1 packet of a connection session, the phone calendar
     * remains the fallback. Once B1 arrives, the bracelet's own day is
     * authoritative for step totals, including after an intentional manual
     * time/date change that differs from the phone calendar.
     */
    fun resolveDayKey(phoneDayKey: String): String =
        if (livePacketObservedThisSession) braceletDayKey ?: phoneDayKey else phoneDayKey

    private companion object {
        fun isValidDayKey(value: String): Boolean {
            if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val parsed = format.parse(value, position)
            return parsed != null && position.index == value.length
        }
    }
}
