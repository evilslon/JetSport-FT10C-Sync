package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveDayStateTest {
    @Test fun liveBraceletDayWinsWhenItDiffersFromPhoneAfterManualDateChange() {
        val state = ActiveDayState()
        state.onLivePacket("2026-11-13")
        assertEquals("2026-11-13", state.resolveDayKey("2026-11-17"))
    }

    @Test fun phoneCalendarIsFallbackBeforeFirstLivePacket() {
        val state = ActiveDayState()
        assertEquals("2026-11-17", state.resolveDayKey("2026-11-17"))
    }

    @Test fun restoredPreviousDayDoesNotOverridePhoneUntilFreshLivePacket() {
        val restored = ActiveDayState(initialDayKey = "2026-11-13")
        assertEquals("2026-11-17", restored.resolveDayKey("2026-11-17"))
        restored.onLivePacket("2026-11-13")
        assertEquals("2026-11-13", restored.resolveDayKey("2026-11-17"))
    }

    @Test fun disconnectReturnsDashboardDaySelectionToPhoneFallback() {
        val state = ActiveDayState()
        state.onLivePacket("2026-11-13")
        assertEquals("2026-11-13", state.resolveDayKey("2026-11-17"))
        state.onDisconnected()
        assertEquals("2026-11-17", state.resolveDayKey("2026-11-17"))
    }

    @Test fun samePersistedDateLivePacketStillActivatesBraceletDayForSession() {
        val state = ActiveDayState(initialDayKey = "2026-11-13")
        assertEquals("2026-11-17", state.resolveDayKey("2026-11-17"))
        state.onLivePacket("2026-11-13")
        assertEquals("2026-11-13", state.resolveDayKey("2026-11-17"))
    }

    @Test fun newLiveDayIsPersistedOnlyWhenItChanges() {
        val saved = mutableListOf<String>()
        val state = ActiveDayState(onDayChanged = saved::add)
        state.onLivePacket("2026-11-13")
        state.onLivePacket("2026-11-13")
        state.onLivePacket("2026-11-14")
        assertEquals(listOf("2026-11-13", "2026-11-14"), saved)
    }

    @Test fun invalidPersistedDayIsIgnored() {
        val state = ActiveDayState(initialDayKey = "2024-02-30")
        assertNull(state.braceletDayKey)
        assertEquals("2026-11-17", state.resolveDayKey("2026-11-17"))
    }


    @Test fun validLeapDayIsAccepted() {
        val state = ActiveDayState()
        state.onLivePacket("2024-02-29")
        assertEquals("2024-02-29", state.resolveDayKey("2026-11-17"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleCalendarDateIsRejected() {
        ActiveDayState().onLivePacket("2026-02-29")
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeMonthIsRejected() {
        ActiveDayState().onLivePacket("2026-13-01")
    }

    @Test fun invalidLiveDateDoesNotReplacePreviouslyAcceptedDate() {
        val state = ActiveDayState()
        state.onLivePacket("2026-11-13")
        try {
            state.onLivePacket("2026-02-29")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid input must not mutate the accepted session date.
        }
        assertEquals("2026-11-13", state.resolveDayKey("2026-11-17"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDayKeyIsRejected() {
        ActiveDayState().onLivePacket("not-a-date")
    }
}
