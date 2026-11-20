package com.jetsport.ft10csync.protocol

import java.util.UUID

/**
 * Decodes incoming BLE notifications into protocol-level events.
 *
 * This class is intentionally stateless: it does not access Android GATT,
 * Activity/UI, database, or command queue. Stateful B2 history assembly remains
 * in the application layer.
 */
sealed class Ft10cNotificationEvent {
    data class LiveSteps(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val count: Int
    ) : Ft10cNotificationEvent()

    data class HistoryPacket(val payload: ByteArray) : Ft10cNotificationEvent()
    data class Battery(val percent: Int) : Ft10cNotificationEvent()
    data class HeartRate(val bpm: Int) : Ft10cNotificationEvent()
    data class BloodPressure(val systolic: Int, val diastolic: Int) : Ft10cNotificationEvent()
    data class AuxiliaryChannel(val payload: ByteArray) : Ft10cNotificationEvent()
    object MeasurementIntermediate : Ft10cNotificationEvent()
    /** Reproducible FT-10C Phone Finder notification observed on 0x33F2. */
    object PhoneFinderActivated : Ft10cNotificationEvent()
}

object Ft10cNotificationDecoder {
    private val FT10C_NOTIFY_UUID: UUID =
        UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb")
    private val AUXILIARY_FF02_UUID: UUID =
        UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

    fun decode(uuid: UUID, payload: ByteArray): Ft10cNotificationEvent? {
        if (uuid == AUXILIARY_FF02_UUID) {
            return Ft10cNotificationEvent.AuxiliaryChannel(payload.copyOf())
        }
        if (uuid != FT10C_NOTIFY_UUID || payload.isEmpty()) return null

        val b = payload.map { it.toInt() and 0xFF }
        return when {
            b.size == 2 && b[0] == 0xD1 && b[1] == 0x0A -> Ft10cNotificationEvent.PhoneFinderActivated
            b[0] == 0xB1 && b.size >= 8 -> {
                val year = (b[1] shl 8) or b[2]
                val month = b[3]
                val day = b[4]
                val hour = b[5]
                if (!isValidDate(year, month, day) || hour !in 0..23) {
                    null
                } else {
                    Ft10cNotificationEvent.LiveSteps(
                        year = year,
                        month = month,
                        day = day,
                        hour = hour,
                        count = (b[6] shl 8) or b[7]
                    )
                }
            }
            b[0] == 0xB2 -> Ft10cNotificationEvent.HistoryPacket(payload.copyOf())
            b[0] == 0xA2 && b.size >= 2 -> {
                val percent = Ft10cBatteryDecoder.decode(payload) ?: return null
                Ft10cNotificationEvent.Battery(percent)
            }
            b[0] == 0xE5 && b.size >= 4 && b[1] == 0x00 && b[2] == 0x00 ->
                Ft10cNotificationEvent.HeartRate(b[3])
            b[0] == 0xC7 && b.size >= 5 && b[1] == 0x00 && b[2] == 0x00 ->
                Ft10cNotificationEvent.BloodPressure(b[3], b[4])
            b[0] == 0xE5 || b[0] == 0xC7 ->
                Ft10cNotificationEvent.MeasurementIntermediate
            else -> null
        }
    }

    /** Keep B1 date/time validation as strict as the B2 history parser. */
    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year !in 1..9999 || month !in 1..12) return false
        val daysInMonth = when (month) {
            2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..daysInMonth
    }
}
