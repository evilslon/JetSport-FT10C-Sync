package com.jetsport.ft10csync.presentation

/**
 * Coordinates navigation-level screen lifecycle events without owning Android views,
 * BLE resources, repositories, or Activity references.
 *
 * Screen renderers/controllers remain independently bound by the host. Selecting History
 * requests a fresh database snapshot; selecting Today or Device does not trigger BLE work.
 */
class ScreenLifecycleCoordinator(
    private val refreshHistory: () -> Unit,
    private val stateStore: ScreenStateStore = ScreenStateStore(),
    private val refreshToday: () -> Unit = {}
) {
    val activeTabIndex: Int
        get() = stateStore.activeTabIndex

    val historyExpanded: Boolean
        get() = stateStore.historyExpanded

    fun onTabSelected(index: Int) {
        stateStore.selectTab(index)
        when (stateStore.activeTabIndex) {
            TODAY_TAB_INDEX -> refreshToday()
            HISTORY_TAB_INDEX -> refreshHistory()
        }
    }

    fun setHistoryExpanded(expanded: Boolean) {
        stateStore.setHistoryExpanded(expanded)
    }

    companion object {
        const val TODAY_TAB_INDEX = 0
        const val HEALTH_TAB_INDEX = 1
        const val HISTORY_TAB_INDEX = 2
        const val DEVICE_TAB_INDEX = 3
        const val SETTINGS_TAB_INDEX = 4
        const val LAST_TAB_INDEX = SETTINGS_TAB_INDEX
    }
}
