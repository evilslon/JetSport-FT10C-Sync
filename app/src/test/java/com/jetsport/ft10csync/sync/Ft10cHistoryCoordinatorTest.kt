package com.jetsport.ft10csync.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ft10cHistoryCoordinatorTest {
    private fun recordPacket(totalSteps: Int = 120): ByteArray = byteArrayOf(
        0xB2.toByte(), 0x07, 0xEA.toByte(), 0x0B, 0x0C, 0x0E,
        ((totalSteps shr 8) and 0xFF).toByte(), (totalSteps and 0xFF).toByte(),
        0x05, 0x10, 0x0B, 0x00, 0x1E, 0x12, 0x20, 0x0E, 0x00, 0x14
    )

    @Test
    fun acceptsRecordsAndCompletesOnlyOnEndMarker() {
        val coordinator = Ft10cHistoryCoordinator(minimumRequestIntervalMs = 1_000)
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.READY, coordinator.requestDecision(10_000, false))
        coordinator.onRequestAccepted(10_000)
        assertTrue(coordinator.isInFlight)

        val first = coordinator.onPacket(recordPacket()) as Ft10cHistoryCoordinator.PacketResult.RecordAccepted
        assertEquals(1, first.recordCount)
        assertEquals(120, first.record.totalSteps)
        assertTrue(coordinator.isInFlight)

        val second = coordinator.onPacket(recordPacket(135)) as Ft10cHistoryCoordinator.PacketResult.RecordAccepted
        assertEquals(1, second.recordCount) // same date/hour replaces, matching the existing session collector

        val completed = coordinator.onPacket(byteArrayOf(0xB2.toByte(), 0xFD.toByte(), 0x4D))
            as Ft10cHistoryCoordinator.PacketResult.Completed
        assertTrue(completed.valid)
        assertEquals(1, completed.records.size)
        assertEquals(135, completed.records.single().totalSteps)
        assertEquals(0x4D, completed.trailingByte)
        assertFalse(coordinator.isInFlight)
    }

    @Test
    fun invalidRecordMarksWholeSessionInvalidButWaitsForEndMarker() {
        val coordinator = Ft10cHistoryCoordinator()
        coordinator.onRequestAccepted(1_000)
        val malformed = recordPacket().also { it[3] = 13 }
        assertTrue(coordinator.onPacket(malformed) is Ft10cHistoryCoordinator.PacketResult.Invalid)
        assertTrue(coordinator.isInFlight)
        val completed = coordinator.onPacket(byteArrayOf(0xB2.toByte(), 0xFD.toByte()))
            as Ft10cHistoryCoordinator.PacketResult.Completed
        assertFalse(completed.valid)
        assertTrue(completed.records.isEmpty())
    }

    @Test
    fun timeoutDiscardsSessionAndBlocksAllB2UntilReconnect() {
        val coordinator = Ft10cHistoryCoordinator()
        coordinator.onRequestAccepted(5_000)
        coordinator.onPacket(recordPacket())

        val timeout = coordinator.onTimeout()!!
        assertEquals(1, timeout.discardedRecordCount)
        assertFalse(coordinator.isInFlight)
        assertTrue(coordinator.isBlockedUntilReconnect)
        assertTrue(coordinator.onPacket(recordPacket()) is Ft10cHistoryCoordinator.PacketResult.Ignored)
        assertEquals(
            Ft10cHistoryCoordinator.RequestDecision.BLOCKED_UNTIL_RECONNECT,
            coordinator.requestDecision(100_000, true)
        )

        coordinator.onDisconnected()
        assertTrue(coordinator.isBlockedUntilReconnect)
        coordinator.onConnected()
        assertFalse(coordinator.isBlockedUntilReconnect)
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.READY, coordinator.requestDecision(100_000, true))
    }

    @Test
    fun requestThrottleAndInFlightDecisionsArePreserved() {
        val coordinator = Ft10cHistoryCoordinator(minimumRequestIntervalMs = 900_000)
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.READY, coordinator.requestDecision(2_000_000, false))
        coordinator.onRequestAccepted(2_000_000)
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.ALREADY_IN_FLIGHT, coordinator.requestDecision(3_000_000, true))
        coordinator.onPacket(byteArrayOf(0xB2.toByte(), 0xFD.toByte()))
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.TOO_SOON, coordinator.requestDecision(2_899_999, false))
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.READY, coordinator.requestDecision(2_900_000, false))
    }

    @Test
    fun disconnectClearsActiveSessionButPreservesRequestThrottle() {
        val coordinator = Ft10cHistoryCoordinator()
        coordinator.onRequestAccepted(10)
        coordinator.onPacket(recordPacket())
        coordinator.onDisconnected()
        assertFalse(coordinator.isInFlight)
        assertTrue(coordinator.onPacket(recordPacket()) is Ft10cHistoryCoordinator.PacketResult.Ignored)
        assertEquals(Ft10cHistoryCoordinator.RequestDecision.TOO_SOON, coordinator.requestDecision(20, false))
    }
}
