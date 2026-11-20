package com.jetsport.ft10csync

import android.content.Context
import android.os.Handler
import com.jetsport.ft10csync.ble.Ft10cSyncCoordinator
import com.jetsport.ft10csync.ble.BleConnectionStateStore
import com.jetsport.ft10csync.ble.Ft10cCommandExecutionState
import com.jetsport.ft10csync.ble.Ft10cSessionLifecycleCoordinator
import com.jetsport.ft10csync.ble.BleUiEventDispatcher
import com.jetsport.ft10csync.ble.Ft10cNotificationCoordinator
import com.jetsport.ft10csync.ble.Ft10cBleSessionRuntime
import com.jetsport.ft10csync.data.ActiveDayRepository
import com.jetsport.ft10csync.data.MeasurementRepository
import com.jetsport.ft10csync.data.SettingsRepository
import com.jetsport.ft10csync.data.TechnicalLogStore
import com.jetsport.ft10csync.data.SharedPreferencesTechnicalLogPersistence
import com.jetsport.ft10csync.data.UserProfileRepository
import com.jetsport.ft10csync.device.DeviceProfileRegistry
import com.jetsport.ft10csync.sync.Ft10cBatteryCoordinator
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import com.jetsport.ft10csync.sync.Ft10cTimeSyncState
import java.util.concurrent.Executor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Composition root for application services used by the foreground UI.
 * Android views and Activity callbacks deliberately stay outside this class.
 * The BLE session runtime is application-scoped and owns its complete GATT callback graph.
 */
internal class AppCore(context: Context, mainHandler: Handler) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val measurementRepository = MeasurementRepository(
        context.applicationContext,
        Executor { command -> mainHandler.post(command) }
    )
    val settingsRepository = SettingsRepository(context.applicationContext)
    val activeDayRepository = ActiveDayRepository(context.applicationContext)
    val userProfileRepository = UserProfileRepository(context.applicationContext)
    val technicalLogStore = TechnicalLogStore(
        persistence = SharedPreferencesTechnicalLogPersistence(context.applicationContext)
    )
    /** Shared event channel; UI subscribers may come and go without being retained by BLE code. */
    val bleUiEvents = BleUiEventDispatcher()
    val phoneFinderAlert = PhoneFinderAlertController(
        context = context.applicationContext,
        settings = settingsRepository,
        mainHandler = mainHandler,
        onStateChanged = { active -> bleUiEvents.dispatch(com.jetsport.ft10csync.ble.BleUiEvent.PhoneFinderAlertChanged(active)) },
        onLog = ::appendTechnicalLog
    )
    /** Shared connection lifecycle state; survives Activity recreation with this AppCore. */
    val bleConnectionState = BleConnectionStateStore()

    val historyCoordinator = Ft10cHistoryCoordinator()
    /** Shared write-in-flight state for all FT-10C command coordinators. */
    val commandExecutionState = Ft10cCommandExecutionState()
    internal val timeSyncState = Ft10cTimeSyncState().apply {
        automaticMode = settingsRepository.automaticTimeSync
    }
    val deviceProfileRegistry = DeviceProfileRegistry()
    internal val syncCoordinator = Ft10cSyncCoordinator(mainHandler)
    internal val batteryCoordinator = Ft10cBatteryCoordinator(
        handler = mainHandler,
        lastResponseAt = { settingsRepository.batteryA2LastResponseAt },
        saveResponseAt = { timestamp -> settingsRepository.batteryA2LastResponseAt = timestamp }
    )
    /** Decodes and persists FT-10C notifications without retaining an Activity. */
    val notificationCoordinator = Ft10cNotificationCoordinator(
        historyCoordinator = historyCoordinator,
        batteryCoordinator = batteryCoordinator,
        measurementRepository = measurementRepository,
        eventDispatcher = bleUiEvents,
        mainHandler = mainHandler,
        saveBatteryPercent = { percent -> settingsRepository.lastBatteryPercent = percent },
        onLog = ::appendTechnicalLog
    )
    /** Application-owned cleanup for connection replacement and terminal disconnects. */
    val sessionLifecycleCoordinator = Ft10cSessionLifecycleCoordinator(
        cancelSyncWorkflow = syncCoordinator::onDisconnected,
        cancelBatteryRetry = batteryCoordinator::onDisconnected,
        clearPendingTimeWrite = timeSyncState::markWriteCompleted,
        resetTimeSyncSession = timeSyncState::onDisconnected,
        clearCommandExecution = commandExecutionState::clear,
        cancelHistorySession = historyCoordinator::cancelActiveSession,
        disconnectHistorySession = historyCoordinator::onDisconnected,
        cancelHistoryTimeout = notificationCoordinator::cancelHistoryTimeout
    )

    internal fun appendTechnicalLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        technicalLogStore.append("[$stamp] $message\n")
        bleUiEvents.dispatch(com.jetsport.ft10csync.ble.BleUiEvent.TechnicalLogChanged)
    }

    /** Application-owned BLE runtime; independent of Activity recreation and visibility. */
    val bleSessionRuntime = Ft10cBleSessionRuntime(context.applicationContext, mainHandler, this)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        phoneFinderAlert.close()
        bleSessionRuntime.close()
        bleConnectionState.disconnected()
        measurementRepository.close()
    }
}
