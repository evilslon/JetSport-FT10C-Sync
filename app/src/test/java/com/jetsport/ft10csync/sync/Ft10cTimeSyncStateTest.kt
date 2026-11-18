package com.jetsport.ft10csync.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class Ft10cTimeSyncStateTest {
    @Test
    fun encodesJetSportTimePacketWithOneBasedMonth() {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 12, 14, 11, 2)
        }

        val actual = Ft10cTimeSyncState.encodeOriginalTimePacket(calendar)
        val expected = byteArrayOf(
            0xA3.toByte(), 0x07, 0xEA.toByte(), 0x0B, 0x0C, 0x0E, 0x0B, 0x02
        )

        assertArrayEquals(expected, actual)
    }

    @Test
    fun manualCalendarIsCopiedAndUsedOnlyInManualMode() {
        val state = Ft10cTimeSyncState()
        state.automaticMode = false
        state.manualDateTime = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2025, Calendar.JANUARY, 3, 4, 5, 0)
        }
        state.manualDateTimeChosen = true

        val selected = state.calendarForSync(automaticRequest = false)
        assertEquals(2025, selected.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, selected.get(Calendar.MONTH))
        selected.add(Calendar.DAY_OF_MONTH, 1)
        assertEquals(3, state.manualDateTime.get(Calendar.DAY_OF_MONTH))

        state.automaticMode = true
        val phoneTime = state.calendarForSync(automaticRequest = false)
        assertNotEquals(2025, phoneTime.get(Calendar.YEAR))
    }

    @Test
    fun writeAndAutomaticAttemptStateResetOnDisconnect() {
        val state = Ft10cTimeSyncState()
        assertFalse(state.writePending)
        assertFalse(state.isAutomaticSyncAttempted())

        state.markWriteAccepted()
        assertTrue(state.writePending)
        assertTrue(state.isAutomaticSyncAttempted())

        state.markWriteCompleted()
        assertFalse(state.writePending)
        assertTrue(state.isAutomaticSyncAttempted())

        state.onDisconnected()
        assertFalse(state.writePending)
        assertFalse(state.isAutomaticSyncAttempted())
    }
}
