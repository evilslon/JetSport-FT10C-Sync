package com.jetsport.ft10csync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Ft10cProfileEncoderTest {
    @Test fun encodesKnownMaleProfileExactly() {
        val actual = Ft10cProfileEncoder.encode(StepProfile(187, 120, true, 45))
        val expected = hex("A9 00 BB 00 78 05 00 00 27 10 01 FE 00 2D 01 00 01 00 00")
        assertEquals(Ft10cProfileEncoder.PACKET_SIZE, actual.size)
        assertArrayEquals(expected, actual)
    }

    @Test fun encodesFemaleSexAndBigEndianWeight() {
        val actual = Ft10cProfileEncoder.encode(StepProfile(155, 160, false, 45))
        assertEquals(0x9B, actual[2].toInt() and 0xFF)
        assertEquals(0x00, actual[3].toInt() and 0xFF)
        assertEquals(0xA0, actual[4].toInt() and 0xFF)
        assertEquals(0x02, actual[14].toInt() and 0xFF)
    }

    @Test fun rejectsOutOfRangeProfile() {
        listOf(
            StepProfile(99, 120, true, 45),
            StepProfile(187, 24, true, 45),
            StepProfile(187, 120, true, 4),
            StepProfile(231, 120, true, 45)
        ).forEach { profile ->
            try {
                Ft10cProfileEncoder.encode(profile)
                throw AssertionError("Expected invalid profile to be rejected: $profile")
            } catch (_: IllegalArgumentException) { }
        }
    }

    private fun hex(value: String) = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
