package com.jetsport.ft10csync.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class Ft10cPhoneFinderNotificationTest {
    private val notifyUuid = UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb")

    @Test fun exactD1ZeroAOnFt10cNotifyIsPhoneFinderActivation() {
        assertEquals(
            Ft10cNotificationEvent.PhoneFinderActivated,
            Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xD1.toByte(), 0x0A))
        )
    }

    @Test fun otherPayloadsAreNotTreatedAsPhoneFinderActivation() {
        assertNull(Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xD1.toByte(), 0x0B)))
        assertNull(Ft10cNotificationDecoder.decode(notifyUuid, byteArrayOf(0xD1.toByte(), 0x0A, 0x00)))
        assertNull(Ft10cNotificationDecoder.decode(UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb"), byteArrayOf(0xD1.toByte(), 0x0A)))
    }
}
