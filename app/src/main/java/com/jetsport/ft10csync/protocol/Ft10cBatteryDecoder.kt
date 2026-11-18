package com.jetsport.ft10csync.protocol

/** Decoder for the user-verified FT-10C A2 battery notification. */
object Ft10cBatteryDecoder {
    /** Returns the percentage from A2 byte 1, or null for unrelated/malformed data. */
    fun decode(packet: ByteArray): Int? {
        if (packet.size < 2 || (packet[0].toInt() and 0xFF) != 0xA2) return null
        return (packet[1].toInt() and 0xFF).coerceIn(0, 100)
    }
}
