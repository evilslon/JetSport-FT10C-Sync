package com.jetsport.ft10csync.protocol


/**
 * Parser for the FT-10C offline step-history notification.
 *
 * Confirmed from a Sony Xperia Z3 Compact HCI capture:
 * - Data record: 18 bytes, starts with B2.
 * - End marker: starts with B2 FD. The trailing byte is preserved but
 *   intentionally not interpreted until its meaning is confirmed.
 *
 * This class does not perform BLE I/O and does not send commands.
 */
data class Ft10cStepRecord(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val totalSteps: Int,
    val runStartMinute: Int,
    val runEndMinute: Int,
    val runDurationMinutes: Int,
    val runSteps: Int,
    val walkStartMinute: Int,
    val walkEndMinute: Int,
    val walkDurationMinutes: Int,
    val walkSteps: Int,
    val rawHex: String
) {
    val dateKey: String
        get() = "%04d%02d%02d".format(year, month, day)

    val uncategorizedSteps: Int
        get() = totalSteps - runSteps - walkSteps
}

sealed class Ft10cHistoryEvent {
    data class Record(val value: Ft10cStepRecord) : Ft10cHistoryEvent()
    data class End(val trailingByte: Int?) : Ft10cHistoryEvent()
    data class Invalid(val reason: String) : Ft10cHistoryEvent()
}

object Ft10cStepHistoryParser {
    const val RECORD_SIZE = 18
    private const val RECORD_HEADER = 0xB2
    private const val END_MARKER = 0xFD

    fun parse(value: ByteArray): Ft10cHistoryEvent {
        if (value.size >= 2 &&
            u(value[0]) == RECORD_HEADER &&
            u(value[1]) == END_MARKER
        ) {
            return Ft10cHistoryEvent.End(
                trailingByte = if (value.size >= 3) u(value[2]) else null
            )
        }

        if (value.size != RECORD_SIZE) {
            return Ft10cHistoryEvent.Invalid(
                "Expected $RECORD_SIZE bytes for B2 record, got ${value.size}"
            )
        }
        if (u(value[0]) != RECORD_HEADER) {
            return Ft10cHistoryEvent.Invalid("Unknown record header 0x%02X".format(u(value[0])))
        }

        val year = (u(value[1]) shl 8) or u(value[2])
        val month = u(value[3])
        val day = u(value[4])
        val hour = u(value[5])
        val total = be16(value, 6)

        val runStart = u(value[8])
        val runEnd = u(value[9])
        val runDuration = u(value[10])
        val runSteps = be16(value, 11)

        val walkStart = u(value[13])
        val walkEnd = u(value[14])
        val walkDuration = u(value[15])
        val walkSteps = be16(value, 16)

        if (year !in 1..9999) return Ft10cHistoryEvent.Invalid("Invalid year: $year")
        if (month !in 1..12) return Ft10cHistoryEvent.Invalid("Invalid month: $month")
        if (day !in 1..daysInMonth(year, month)) {
            return Ft10cHistoryEvent.Invalid("Invalid day $day for $year-$month")
        }
        if (hour !in 0..23) return Ft10cHistoryEvent.Invalid("Invalid hour: $hour")
        if (runSteps + walkSteps > total) {
            return Ft10cHistoryEvent.Invalid(
                "Activity steps exceed total: run=$runSteps + walk=$walkSteps > total=$total"
            )
        }

        // Values are minutes within the record's hour. Preserve zero values;
        // do not infer that a zero time means a missing activity segment.
        val minuteFields = listOf(runStart, runEnd, walkStart, walkEnd)
        if (minuteFields.any { it !in 0..59 }) {
            return Ft10cHistoryEvent.Invalid("Activity minute outside 0..59")
        }

        return Ft10cHistoryEvent.Record(
            Ft10cStepRecord(
                year, month, day, hour, total,
                runStart, runEnd, runDuration, runSteps,
                walkStart, walkEnd, walkDuration, walkSteps,
                value.joinToString(" ") { "%02X".format(u(it)) }
            )
        )
    }

    private fun u(b: Byte): Int = b.toInt() and 0xFF

    private fun be16(bytes: ByteArray, offset: Int): Int =
        (u(bytes[offset]) shl 8) or u(bytes[offset + 1])

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun isLeapYear(year: Int): Boolean =
        year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
}

/**
 * In-memory session collector. A repeated date+hour replaces the previous
 * record, regardless of packet ordering. Call finish() only after B2 FD.
 */
class Ft10cHistorySession {
    private val records = linkedMapOf<Pair<String, Int>, Ft10cStepRecord>()
    var endMarkerTrailingByte: Int? = null
        private set
    var isComplete: Boolean = false
        private set

    fun accept(notificationValue: ByteArray): Ft10cHistoryEvent {
        if (isComplete) {
            return Ft10cHistoryEvent.Invalid("Session already completed")
        }

        return when (val event = Ft10cStepHistoryParser.parse(notificationValue)) {
            is Ft10cHistoryEvent.Record -> {
                val r = event.value
                records[r.dateKey to r.hour] = r
                event
            }
            is Ft10cHistoryEvent.End -> {
                endMarkerTrailingByte = event.trailingByte
                isComplete = true
                event
            }
            is Ft10cHistoryEvent.Invalid -> event
        }
    }

    fun snapshot(): List<Ft10cStepRecord> =
        records.values.sortedWith(compareBy<Ft10cStepRecord>({ it.dateKey }, { it.hour }))

}
