package com.jetsport.ft10csync.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class Ft10cNotificationDecoderTest {
    private val notifyUuid = UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb")
    private val otherUuid = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

    @Test fun decodesLiveStepsB1() {
        val event = Ft10cNotificationDecoder.decode(
            notifyUuid, byteArrayOf(0xB1.toByte(), 0x07, 0xEA.toByte(), 0x0B, 0x0C, 0x09, 0x02, 0x4B)
        )
        assertTrue(event is Ft10cNotificationEvent.LiveSteps)
        event as Ft10cNotificationEvent.LiveSteps
        assertEquals(2026, event.year)
        assertEquals(11, event.month)
        assertEquals(12, event.day)
        assertEquals(9, event.hour)
        assertEquals(587, event.count)
    }

    @Test fun preservesB2PayloadForStatefulHistoryParser() {
        val packet = byteArrayOf(0xB2.toByte(), 0x07, 0xEA.toByte(), 0x0B, 0x0C)
        val event = Ft10cNotificationDecoder.decode(notifyUuid, packet)
        assertTrue(event is Ft10cNotificationEvent.HistoryPacket)
        assertTrue((event as Ft10cNotificationEvent.HistoryPacket).payload.contentEquals(packet))
    }

    @Test fun decodesProprietaryBatteryA2() {
        val event = Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xA2.toByte(), 0x3D))
        assertEquals(Ft10cNotificationEvent.Battery(61), event)
    }

    @Test fun decodesHeartRateE5() {
        val event = Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xE5.toByte(), 0, 0, 89))
        assertEquals(Ft10cNotificationEvent.HeartRate(89), event)
    }

    @Test fun decodesBloodPressureC7() {
        val event = Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xC7.toByte(), 0, 0, 120, 71))
        assertEquals(Ft10cNotificationEvent.BloodPressure(120, 71), event)
    }

    @Test fun routesAuxiliaryChannelSeparately() {
        val packet = byteArrayOf(1, 2, 3)
        val event = Ft10cNotificationDecoder.decode(otherUuid, packet)
        assertTrue(event is Ft10cNotificationEvent.AuxiliaryChannel)
        assertTrue((event as Ft10cNotificationEvent.AuxiliaryChannel).payload.contentEquals(packet))
    }

    @Test fun ignoresUnknownAndEmptyNotifications() {
        assertNull(Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf()))
        assertNull(Ft10cNotificationDecoder.decode(UUID.randomUUID(), byteArrayOf(1, 2)))
    }

    @Test fun identifiesIncompleteMeasurementAsIntermediate() {
        assertEquals(
            Ft10cNotificationEvent.MeasurementIntermediate,
            Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xE5.toByte(), 0, 0))
        )
    }
}
