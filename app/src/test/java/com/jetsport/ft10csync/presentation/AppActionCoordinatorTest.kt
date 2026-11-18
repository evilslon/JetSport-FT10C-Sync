package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppActionCoordinatorTest {
    @Test
    fun dispatchRoutesEveryActionToExactlyOneMatchingHandler() {
        val received = mutableListOf<String>()
        val coordinator = AppActionCoordinator(object : AppActionCoordinator.Handlers {
            override fun showLanguage() { received += "language" }
            override fun showProfile() { received += "profile" }
            override fun scan() { received += "scan" }
            override fun connect() { received += "connect" }
            override fun disconnect() { received += "disconnect" }
            override fun refreshHistory() { received += "history" }
            override fun syncTime() { received += "sync" }
            override fun chooseDateTime() { received += "date" }
            override fun exportLog() { received += "export" }
            override fun selectTab(index: Int) { received += "tab:$index" }
            override fun selectDevice(address: String) { received += "device:$address" }
            override fun setAutomaticTimeSync(enabled: Boolean) { received += "auto:$enabled" }
        })

        coordinator.dispatch(AppAction.ShowLanguage)
        coordinator.dispatch(AppAction.ShowProfile)
        coordinator.dispatch(AppAction.Scan)
        coordinator.dispatch(AppAction.Connect)
        coordinator.dispatch(AppAction.Disconnect)
        coordinator.dispatch(AppAction.RefreshHistory)
        coordinator.dispatch(AppAction.SyncTime)
        coordinator.dispatch(AppAction.ChooseDateTime)
        coordinator.dispatch(AppAction.ExportLog)
        coordinator.dispatch(AppAction.SelectTab(4))
        coordinator.dispatch(AppAction.SelectDevice("FT-10C"))
        coordinator.dispatch(AppAction.SetAutomaticTimeSync(false))

        assertEquals(
            listOf(
                "language", "profile", "scan", "connect", "disconnect", "history",
                "sync", "date", "export", "tab:4", "device:FT-10C", "auto:false"
            ),
            received
        )
    }
}
