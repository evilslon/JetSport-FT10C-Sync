package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLifecycleCoordinatorTest {
    @Test
    fun selectingHistoryRefreshesHistoryAndStoresTab() {
        var refreshes = 0
        val coordinator = ScreenLifecycleCoordinator(refreshHistory = { refreshes++ })

        coordinator.onTabSelected(ScreenLifecycleCoordinator.HISTORY_TAB_INDEX)

        assertEquals(ScreenLifecycleCoordinator.HISTORY_TAB_INDEX, coordinator.activeTabIndex)
        assertEquals(1, refreshes)
    }

    @Test
    fun selectingTodayRefreshesDashboardAndDeviceDoesNotRefreshHistory() {
        var historyRefreshes = 0
        var todayRefreshes = 0
        val coordinator = ScreenLifecycleCoordinator(
            refreshHistory = { historyRefreshes++ },
            refreshToday = { todayRefreshes++ }
        )

        coordinator.onTabSelected(ScreenLifecycleCoordinator.TODAY_TAB_INDEX)
        coordinator.onTabSelected(ScreenLifecycleCoordinator.DEVICE_TAB_INDEX)

        assertEquals(0, historyRefreshes)
        assertEquals(1, todayRefreshes)
        assertEquals(ScreenLifecycleCoordinator.DEVICE_TAB_INDEX, coordinator.activeTabIndex)
    }

    @Test
    fun historyExpandedStateSurvivesViewRebuild() {
        val stateStore = ScreenStateStore()
        val first = ScreenLifecycleCoordinator(refreshHistory = {}, stateStore = stateStore)
        first.setHistoryExpanded(true)
        first.onTabSelected(ScreenLifecycleCoordinator.HISTORY_TAB_INDEX)

        // A rebuilt screen coordinator receives the same host-owned state store.
        val second = ScreenLifecycleCoordinator(refreshHistory = {}, stateStore = stateStore)

        assertTrue(second.historyExpanded)
        assertEquals(ScreenLifecycleCoordinator.HISTORY_TAB_INDEX, second.activeTabIndex)
        second.setHistoryExpanded(false)
        assertFalse(first.historyExpanded)
    }

    @Test
    fun screenStateStoreRestoresHistoryTabAndExpandedState() {
        val restored = ScreenStateStore(
            initialTabIndex = ScreenLifecycleCoordinator.HISTORY_TAB_INDEX,
            initialHistoryExpanded = true
        )

        assertEquals(ScreenLifecycleCoordinator.HISTORY_TAB_INDEX, restored.activeTabIndex)
        assertTrue(restored.historyExpanded)
    }

    @Test
    fun screenStateStoreAcceptsSettingsAsLastTab() {
        val store = ScreenStateStore(initialTabIndex = ScreenLifecycleCoordinator.SETTINGS_TAB_INDEX)

        assertEquals(ScreenLifecycleCoordinator.SETTINGS_TAB_INDEX, store.activeTabIndex)
    }

    @Test
    fun screenStateStoreClampsInvalidTabIndexesToSupportedRange() {
        val tooLow = ScreenStateStore(initialTabIndex = -4)
        val tooHigh = ScreenStateStore(initialTabIndex = 99)

        assertEquals(ScreenLifecycleCoordinator.TODAY_TAB_INDEX, tooLow.activeTabIndex)
        assertEquals(ScreenLifecycleCoordinator.LAST_TAB_INDEX, tooHigh.activeTabIndex)
    }

    @Test
    fun selectingHealthAndSettingsDoesNotTriggerDataRefresh() {
        var historyRefreshes = 0
        var todayRefreshes = 0
        val coordinator = ScreenLifecycleCoordinator(
            refreshHistory = { historyRefreshes++ },
            refreshToday = { todayRefreshes++ }
        )

        coordinator.onTabSelected(ScreenLifecycleCoordinator.HEALTH_TAB_INDEX)
        coordinator.onTabSelected(ScreenLifecycleCoordinator.SETTINGS_TAB_INDEX)

        assertEquals(ScreenLifecycleCoordinator.SETTINGS_TAB_INDEX, coordinator.activeTabIndex)
        assertEquals(0, historyRefreshes)
        assertEquals(0, todayRefreshes)
    }

}
