package com.jetsport.ft10csync

/**
 * Pure encoder for the FT-10C A9 user-profile command.
 *
 * Packet layout is preserved from the original JetSport implementation and
 * the captured FT-10C HCI packet. Do not change reserved/feature bytes without
 * a new protocol capture and a real-device regression test.
 */
object Ft10cProfileEncoder {
    const val PACKET_SIZE = 19

    fun encode(profile: StepProfile): ByteArray {
        require(profile.heightCm in 100..230) { "Height must be 100..230 cm" }
        require(profile.weightKg in 25..250) { "Weight must be 25..250 kg" }
        require(profile.ageYears in 5..100) { "Age must be 5..100 years" }

        val weight = profile.weightKg
        return byteArrayOf(
            0xA9.toByte(), 0x00,
            (profile.heightCm and 0xFF).toByte(),
            ((weight ushr 8) and 0xFF).toByte(),
            (weight and 0xFF).toByte(),
            0x05, 0x00, 0x00, 0x27, 0x10,
            0x01, 0xFE.toByte(), 0x00,
            (profile.ageYears and 0xFF).toByte(),
            (if (profile.isMale) 0x01 else 0x02).toByte(),
            0x00, 0x01, 0x00, 0x00
        )
    }
}
