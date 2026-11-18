package com.jetsport.ft10csync

import android.content.Context
import android.os.Handler
import com.jetsport.ft10csync.ble.Ft10cSyncCoordinator
import com.jetsport.ft10csync.data.ActiveDayRepository
import com.jetsport.ft10csync.data.MeasurementRepository
import com.jetsport.ft10csync.data.SettingsRepository
import com.jetsport.ft10csync.data.TechnicalLogStore
import com.jetsport.ft10csync.data.UserProfileRepository
import com.jetsport.ft10csync.device.DeviceProfileRegistry
import com.jetsport.ft10csync.sync.Ft10cBatteryCoordinator
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import com.jetsport.ft10csync.sync.Ft10cTimeSyncState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Composition root for application services used by the foreground UI.
 * Android views and Activity callbacks deliberately stay outside this class.
 * The GATT session remains owned by Ft10cDeviceManager in MainActivity until
 * its callback graph can be moved without retaining an Activity instance.
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
    val technicalLogStore = TechnicalLogStore()

    val historyCoordinator = Ft10cHistoryCoordinator()
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        syncCoordinator.onDisconnected()
        batteryCoordinator.onDisconnected()
        historyCoordinator.cancelActiveSession()
        measurementRepository.close()
    }
}
