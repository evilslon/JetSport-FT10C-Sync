package com.jetsport.ft10csync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSyncIntervalSettingsTest {
    @Test
    fun defaultIntervalIsThreeHours() {
        assertEquals(180, SettingsRepository.DEFAULT_TIME_SYNC_INTERVAL_MINUTES)
    }

    @Test
    fun onlyAgreedIntervalsAreSupported() {
        assertEquals(setOf(30, 180, 360, 720), SettingsRepository.TIME_SYNC_INTERVALS_MINUTES)
        assertTrue(SettingsRepository.DEFAULT_TIME_SYNC_INTERVAL_MINUTES in SettingsRepository.TIME_SYNC_INTERVALS_MINUTES)
        assertEquals(180, com.jetsport.ft10csync.sync.Ft10cTimeSyncIntervals.normalize(999))
        assertEquals(10_800_000L, com.jetsport.ft10csync.sync.Ft10cTimeSyncIntervals.delayMillis(180))
        assertEquals(1_800_000L, com.jetsport.ft10csync.sync.Ft10cTimeSyncIntervals.delayMillis(30))
    }
}
