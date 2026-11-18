package com.jetsport.ft10csync.presentation

/** Ephemeral presentation state that survives view rebuilds and Activity recreation. */
class ScreenStateStore(
    initialTabIndex: Int = 0,
    initialHistoryExpanded: Boolean = false
) {
    var activeTabIndex: Int = initialTabIndex.coerceIn(
        ScreenLifecycleCoordinator.TODAY_TAB_INDEX,
        ScreenLifecycleCoordinator.LAST_TAB_INDEX
    )
        private set
    var historyExpanded: Boolean = initialHistoryExpanded
        private set

    fun selectTab(index: Int) {
        activeTabIndex = index.coerceIn(
            ScreenLifecycleCoordinator.TODAY_TAB_INDEX,
            ScreenLifecycleCoordinator.LAST_TAB_INDEX
        )
    }

    fun setHistoryExpanded(expanded: Boolean) {
        historyExpanded = expanded
    }

    companion object {
        const val STATE_ACTIVE_TAB = "jetsport.screen.active_tab"
        const val STATE_HISTORY_EXPANDED = "jetsport.screen.history_expanded"
    }
}
