package com.jetsport.ft10csync.presentation

/** User intents emitted by screen renderers. No Android views or BLE resources are owned here. */
sealed class AppAction {
    object ShowLanguage : AppAction()
    object ShowProfile : AppAction()
    object Scan : AppAction()
    object Connect : AppAction()
    object Disconnect : AppAction()
    object RefreshHistory : AppAction()
    object SyncTime : AppAction()
    object ChooseDateTime : AppAction()
    object ExportLog : AppAction()
    data class SelectTab(val index: Int) : AppAction()
    data class SelectDevice(val address: String) : AppAction()
    data class SetAutomaticTimeSync(val enabled: Boolean) : AppAction()
}

/** Single routing point from presentation events to application operations. */
class AppActionCoordinator(private val handlers: Handlers) {
    interface Handlers {
        fun showLanguage()
        fun showProfile()
        fun scan()
        fun connect()
        fun disconnect()
        fun refreshHistory()
        fun syncTime()
        fun chooseDateTime()
        fun exportLog()
        fun selectTab(index: Int)
        fun selectDevice(address: String)
        fun setAutomaticTimeSync(enabled: Boolean)
    }

    fun dispatch(action: AppAction) {
        when (action) {
            AppAction.ShowLanguage -> handlers.showLanguage()
            AppAction.ShowProfile -> handlers.showProfile()
            AppAction.Scan -> handlers.scan()
            AppAction.Connect -> handlers.connect()
            AppAction.Disconnect -> handlers.disconnect()
            AppAction.RefreshHistory -> handlers.refreshHistory()
            AppAction.SyncTime -> handlers.syncTime()
            AppAction.ChooseDateTime -> handlers.chooseDateTime()
            AppAction.ExportLog -> handlers.exportLog()
            is AppAction.SelectTab -> handlers.selectTab(action.index)
            is AppAction.SelectDevice -> handlers.selectDevice(action.address)
            is AppAction.SetAutomaticTimeSync -> handlers.setAutomaticTimeSync(action.enabled)
        }
    }
}
