package com.jetsport.ft10csync.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ft10cBatteryDecoderTest {
    @Test fun decodesObservedA2BatteryValues() {
        assertEquals(70, Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte(), 0x46))!!)
        assertEquals(71, Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte(), 0x47))!!)
    }

    @Test fun acceptsZeroAndOneHundredPercent() {
        assertEquals(0, Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte(), 0x00))!!)
        assertEquals(100, Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte(), 0x64))!!)
    }

    @Test fun clampsUnexpectedUnsignedValueToDisplayRange() {
        assertEquals(100, Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte(), 0xFF.toByte()))!!)
    }

    @Test fun rejectsUnrelatedAndShortPackets() {
        assertNull(Ft10cBatteryDecoder.decode(byteArrayOf(0xA3.toByte(), 0x46)))
        assertNull(Ft10cBatteryDecoder.decode(byteArrayOf(0xA2.toByte())))
        assertNull(Ft10cBatteryDecoder.decode(byteArrayOf()))
    }
}
