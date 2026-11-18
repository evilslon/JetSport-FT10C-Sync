package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleConnectionStateStoreTest {
    @Test
    fun connectingDoesNotExposeConnectedAddress() {
        val store = BleConnectionStateStore()
        store.connecting("AA:BB:CC:DD:EE:FF")
        assertEquals(BleConnectionState.Connecting("AA:BB:CC:DD:EE:FF"), store.state)
        assertNull(store.connectedAddress)
    }

    @Test
    fun discoveringAndReadyExposeConnectedAddress() {
        val store = BleConnectionStateStore()
        store.discovering("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", store.connectedAddress)
        store.ready("AA:BB:CC:DD:EE:FF", "jetsport-ft-10c")
        assertEquals(BleConnectionState.Ready("AA:BB:CC:DD:EE:FF", "jetsport-ft-10c"), store.state)
        assertEquals("AA:BB:CC:DD:EE:FF", store.connectedAddress)
    }

    @Test
    fun unknownDeviceRemainsConnectedButIsNotReady() {
        val store = BleConnectionStateStore()
        store.connectedUnknown("11:22:33:44:55:66")
        assertEquals("11:22:33:44:55:66", store.connectedAddress)
        assertEquals(BleConnectionState.ConnectedUnknown("11:22:33:44:55:66"), store.state)
    }

    @Test
    fun disconnectedAndFailedDoNotExposeConnectedAddress() {
        val store = BleConnectionStateStore()
        store.discovering("AA:BB:CC:DD:EE:FF")
        store.disconnected()
        assertEquals(BleConnectionState.Disconnected, store.state)
        assertNull(store.connectedAddress)
        store.fail("AA:BB:CC:DD:EE:FF", "timeout")
        assertEquals(BleConnectionState.Failed("AA:BB:CC:DD:EE:FF", "timeout"), store.state)
        assertNull(store.connectedAddress)
    }

    @Test
    fun disconnectingHidesConnectedAddressLikePreviousActivityBehavior() {
        val store = BleConnectionStateStore()
        store.ready("AA:BB:CC:DD:EE:FF", "jetsport-ft-10c")
        store.disconnecting("AA:BB:CC:DD:EE:FF")
        assertEquals(BleConnectionState.Disconnecting("AA:BB:CC:DD:EE:FF"), store.state)
        assertNull(store.connectedAddress)
    }
}
