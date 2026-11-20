package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class Ft10cSessionLifecycleCoordinatorTest {
    @Test
    fun beforeConnectCancelsPreviousSessionWorkInStableOrder() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onBeforeConnect()

        assertEquals(
            listOf("sync", "battery", "clear-command", "clear-time-write", "cancel-history", "cancel-timeout"),
            calls
        )
    }

    @Test
    fun disconnectResetsAllSharedSessionStateAndIsSafeToRepeat() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.onDisconnected()
        coordinator.onDisconnected()

        assertEquals(
            listOf(
                "sync", "battery", "reset-time", "clear-command", "disconnect-history", "cancel-timeout",
                "sync", "battery", "reset-time", "clear-command", "disconnect-history", "cancel-timeout"
            ),
            calls
        )
    }

    private fun coordinator(calls: MutableList<String>) = Ft10cSessionLifecycleCoordinator(
        cancelSyncWorkflow = { calls += "sync" },
        cancelBatteryRetry = { calls += "battery" },
        clearPendingTimeWrite = { calls += "clear-time-write" },
        resetTimeSyncSession = { calls += "reset-time" },
        clearCommandExecution = { calls += "clear-command" },
        cancelHistorySession = { calls += "cancel-history" },
        disconnectHistorySession = { calls += "disconnect-history" },
        cancelHistoryTimeout = { calls += "cancel-timeout" }
    )
}
