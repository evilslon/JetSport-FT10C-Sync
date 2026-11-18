package com.jetsport.ft10csync.sync

import java.util.Calendar

/**
 * Owns the mutable time-sync selection and per-connection write state.
 * Android UI and BLE command execution remain outside this class.
 */
internal class Ft10cTimeSyncState {
    var automaticMode: Boolean = true
    var manualDateTime: Calendar = Calendar.getInstance()
    var manualDateTimeChosen: Boolean = false
    var writePending: Boolean = false
        private set

    private var automaticSyncAttempted: Boolean = false

    fun resetAutomaticSyncAttempt() {
        automaticSyncAttempted = false
    }

    fun isAutomaticSyncAttempted(): Boolean = automaticSyncAttempted

    private fun markAutomaticSyncAttempted() {
        automaticSyncAttempted = true
    }

    fun markWriteAccepted() {
        writePending = true
        // Preserve existing behavior: once a time write is accepted, don't
        // schedule another automatic attempt during this connection.
        markAutomaticSyncAttempted()
    }

    fun markWriteCompleted() {
        writePending = false
    }

    fun onDisconnected() {
        writePending = false
        resetAutomaticSyncAttempt()
    }

    fun calendarForSync(automaticRequest: Boolean): Calendar {
        return if (automaticRequest || automaticMode || !manualDateTimeChosen) {
            Calendar.getInstance()
        } else {
            manualDateTime.clone() as Calendar
        }
    }

    companion object {
        /**
         * JetSport 1.8.0 packet: A3, year high, year low, month (1..12),
         * day, hour, minute, second. Android Calendar.MONTH is zero-based.
         */
        fun encodeOriginalTimePacket(calendar: Calendar): ByteArray {
            val year = calendar.get(Calendar.YEAR)
            return byteArrayOf(
                0xA3.toByte(),
                ((year ushr 8) and 0xFF).toByte(),
                (year and 0xFF).toByte(),
                ((calendar.get(Calendar.MONTH) + 1) and 0xFF).toByte(),
                (calendar.get(Calendar.DAY_OF_MONTH) and 0xFF).toByte(),
                (calendar.get(Calendar.HOUR_OF_DAY) and 0xFF).toByte(),
                (calendar.get(Calendar.MINUTE) and 0xFF).toByte(),
                (calendar.get(Calendar.SECOND) and 0xFF).toByte()
            )
        }
    }
}
