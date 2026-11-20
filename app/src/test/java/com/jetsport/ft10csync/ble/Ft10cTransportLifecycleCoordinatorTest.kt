package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class Ft10cTransportLifecycleCoordinatorTest {
    @Test
    fun beforeConnectCancelsRetriesBeforeQueue() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onBeforeConnect()

        assertEquals(listOf("profile", "time", "history", "queue:replacing GATT connection"), calls)
    }

    @Test
    fun remoteDisconnectCancelsEveryRetryAndQueue() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onGattDisconnected()

        assertEquals(listOf("profile", "time", "history", "queue:GATT disconnected"), calls)
    }

    @Test
    fun unsupportedProfileCancelsWorkThenTimeoutsThenClosesGatt() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onUnsupportedDevice()

        assertEquals(
            listOf("profile", "time", "history", "queue:unsupported GATT profile", "timeouts", "close"),
            calls
        )
    }

    @Test
    fun runtimeTeardownCancelsWorkThenTimeoutsThenClosesGatt() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onRuntimeClose("AppCore closed")

        assertEquals(
            listOf("profile", "time", "history", "queue:AppCore closed", "timeouts", "close"),
            calls
        )
    }

    private fun coordinator(calls: MutableList<String>) = Ft10cTransportLifecycleCoordinator(
        cancelProfileRetry = { calls += "profile" },
        cancelTimeRetry = { calls += "time" },
        cancelHistoryRetry = { calls += "history" },
        cancelGattCommands = { calls += "queue:$it" },
        cancelConnectionTimeouts = { calls += "timeouts" },
        closeGatt = { calls += "close" }
    )
}
