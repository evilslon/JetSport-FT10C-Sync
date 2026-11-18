package com.jetsport.ft10csync.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ft10cBatteryCoordinatorTest {
    @Test fun refreshIsNotDueBeforeThirtyMinutes() {
        val lastResponse = 1_000_000L
        assertFalse(
            Ft10cBatteryCoordinator.isRefreshDue(
                lastResponse,
                lastResponse + Ft10cBatteryCoordinator.REFRESH_INTERVAL_MS - 1
            )
        )
    }

    @Test fun refreshIsDueAtThirtyMinuteBoundary() {
        val lastResponse = 1_000_000L
        assertTrue(
            Ft10cBatteryCoordinator.isRefreshDue(
                lastResponse,
                lastResponse + Ft10cBatteryCoordinator.REFRESH_INTERVAL_MS
            )
        )
    }

    @Test fun retryDelayRemainsFiveSeconds() {
        assertTrue(Ft10cBatteryCoordinator.RETRY_DELAY_MS == 5_000L)
    }

    @Test fun batteryRetryBudgetIsBoundedToOneMinute() {
        assertTrue(Ft10cBatteryCoordinator.MAX_RETRY_ATTEMPTS == 12)
        assertTrue(
            Ft10cBatteryCoordinator.MAX_RETRY_ATTEMPTS * Ft10cBatteryCoordinator.RETRY_DELAY_MS ==
                60_000L
        )
    }
}
