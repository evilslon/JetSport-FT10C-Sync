package com.jetsport.ft10csync.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import com.jetsport.ft10csync.AppCore
import com.jetsport.ft10csync.StepProfile
import com.jetsport.ft10csync.device.BraceletDeviceProfile
import com.jetsport.ft10csync.device.Ft10cDeviceProfile
import java.util.UUID

/**
 * AppCore-owned owner of one FT-10C BLE session and its serialized command path.
 * It is constructed with application context and is retained by AppCore, never by an
 * Activity. UI-facing effects are published as immutable BleUiEvents.
 */
internal class Ft10cBleSessionRuntime(
    context: Context,
    private val handler: Handler,
    private val core: AppCore
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var activeDeviceProfile: BraceletDeviceProfile? = null
    private val commandExecutionState get() = core.commandExecutionState
    private val connectedAddress: String? get() = core.bleConnectionState.connectedAddress
    private val gatt: BluetoothGatt? get() = if (deviceManagerDelegate.isInitialized()) deviceManager.gatt else null

    private val transportLifecycleCoordinator: Ft10cTransportLifecycleCoordinator by lazy {
        Ft10cTransportLifecycleCoordinator(
            cancelProfileRetry = { if (commandOperationsDelegate.isInitialized()) commandOperations.cancelProfile() },
            cancelTimeRetry = { if (commandOperationsDelegate.isInitialized()) commandOperations.cancelTime() },
            cancelHistoryRetry = { if (commandOperationsDelegate.isInitialized()) commandOperations.cancelHistory() },
            cancelGattCommands = { reason -> if (commandChannelDelegate.isInitialized()) commandChannel.cancelAll(reason) },
            cancelConnectionTimeouts = { if (connectionCoordinatorDelegate.isInitialized()) connectionCoordinator.cancelPendingWorkflow() },
            closeGatt = { if (deviceManagerDelegate.isInitialized()) deviceManager.disconnectAndClose() }
        )
    }

    private val connectionCoordinatorDelegate = lazy {
        Ft10cConnectionCoordinator(
            handler = handler,
            transport = deviceManager,
            isConnected = { address -> connectedAddress == address },
            logger = ::log,
            events = core.bleUiEvents,
            listener = object : Ft10cConnectionCoordinator.Listener {
                override fun onBeforeConnect() {
                    connectedAddress?.let { core.bleUiEvents.dispatch(BleUiEvent.GattDisconnected(it)) }
                    transportLifecycleCoordinator.onBeforeConnect()
                    core.sessionLifecycleCoordinator.onBeforeConnect()
                    activeDeviceProfile = null
                }
                override fun onBeforeDisconnect(reason: String) {
                    transportLifecycleCoordinator.onBeforeDisconnect(reason)
                    activeDeviceProfile = null
                    core.sessionLifecycleCoordinator.onDisconnected()
                }
            }
        )
    }
    private val connectionCoordinator: Ft10cConnectionCoordinator by connectionCoordinatorDelegate

    private val gattCallbackRouter: Ft10cGattCallbackRouter by lazy {
        Ft10cGattCallbackRouter(
            sessionEvents = gattSessionEventHandler,
            writeEvents = commandChannel.writeEventHandler,
            onServicesDiscovered = { status -> connectionCoordinator.onServicesDiscovered(status) }
        )
    }

    private val deviceManagerDelegate = lazy {
        Ft10cBleSessionController(appContext, gattCallbackRouter, core.bleConnectionState).also { it.setLogSink(::log) }
    }
    private val deviceManager: Ft10cBleSessionController by deviceManagerDelegate

    private val gattSessionEventHandler: Ft10cGattSessionEventHandler by lazy {
        Ft10cGattSessionEventHandler(
            profileRegistry = core.deviceProfileRegistry,
            timeServiceUuid = FT10C_SERVICE_UUID,
            timeWriteCharacteristicUuid = FT10C_TIME_WRITE_UUID,
            addressOf = { safeAddress(it.device) ?: "<unknown>" },
            statusName = ::gattStatusName,
            stateName = ::gattStateName,
            properties = ::properties,
            logger = ::log,
            uiEvents = core.bleUiEvents,
            markUnknown = { address -> deviceManager.markUnknown(address) },
            markReady = { address, modelId -> deviceManager.markReady(address, modelId) },
            enableNotifications = { current, profile -> deviceManager.enableNotifications(current, profile) },
            onConnected = { current ->
                connectionCoordinator.onConnected()
                if (commandChannelDelegate.isInitialized()) commandChannel.resetForNewConnection()
                core.historyCoordinator.onConnected()
                core.timeSyncState.resetAutomaticSyncAttempt()
                log("GATT device=${safeAddress(current.device)}: connected. Discovering services…")
            },
            onDisconnected = { current ->
                connectionCoordinator.onDisconnected()
                activeDeviceProfile = null
                transportLifecycleCoordinator.onGattDisconnected()
                core.sessionLifecycleCoordinator.onDisconnected()
                log("GATT device=${safeAddress(current.device)}: connection closed")
            },
            onUnknownProfile = { current ->
                activeDeviceProfile = null
                val address = safeAddress(current.device) ?: "<unknown>"
                log("GATT: device $address is not a supported JetSport FT-10C; closing connection")
                transportLifecycleCoordinator.onUnsupportedDevice()
            },
            onMatchedProfile = { current, profile, _, timeWriteAvailable ->
                activeDeviceProfile = profile
                core.syncCoordinator.scheduleConnectionWorkflow(
                    isCurrentConnection = { connectedAddress != null && gatt === current },
                    automaticTimeSyncEnabled = { core.timeSyncState.automaticMode && timeWriteAvailable },
                    automaticTimeSyncAlreadyAttempted = { core.timeSyncState.isAutomaticSyncAttempted() },
                    onProfile = { sendProfile("connect") },
                    onAutomaticTimeSync = {
                        log("AUTO TIME SYNC: starting after profile transfer")
                        syncTime(automatic = true)
                    },
                    onHistory = { requestHistory("connect", force = true) },
                    onBattery = { requestBattery(force = true) }
                )
            },
            onNotification = core.notificationCoordinator::handle
        )
    }

    private val commandChannelDelegate = lazy {
        Ft10cGattCommandChannel(
            gattProvider = { gatt },
            scheduler = HandlerFt10cQueueScheduler(handler),
            callbackHandler = handler,
            serviceUuid = FT10C_SERVICE_UUID,
            characteristicUuid = FT10C_TIME_WRITE_UUID,
            logger = ::log,
            addressOf = { safeAddress(it.device) }
        )
    }
    private val commandChannel: Ft10cGattCommandChannel by commandChannelDelegate

    private val commandOperationsDelegate = lazy {
        Ft10cCommandOperations(
            handler = handler,
            gattProvider = { gatt },
            isCurrentConnection = { candidate -> connectedAddress != null && gatt === candidate },
            hasBlePermissions = ::hasBlePermissions,
            requestBlePermissions = { core.bleUiEvents.dispatch(BleUiEvent.BlePermissionsRequired) },
            isFt10cProfileConfirmed = { activeDeviceProfile?.modelId == Ft10cDeviceProfile.modelId },
            profileIsBusy = { commandExecutionState.isBusy || core.timeSyncState.writePending || core.historyCoordinator.isInFlight },
            timeIsBusy = { commandExecutionState.isBusy || core.historyCoordinator.isInFlight },
            historyIsBusy = { commandExecutionState.isBusy || core.timeSyncState.writePending },
            batteryIsBusy = { commandChannel.isBusy || core.timeSyncState.writePending || core.historyCoordinator.isInFlight || commandExecutionState.isBusy },
            profileProvider = {
                val profile = core.userProfileRepository.load()
                StepProfile(profile.heightCm, profile.weightKg, profile.isMale, profile.ageYears)
            },
            timeSyncState = core.timeSyncState,
            historyCoordinator = core.historyCoordinator,
            batteryCoordinator = core.batteryCoordinator,
            notificationCoordinator = core.notificationCoordinator,
            commandExecutionState = commandExecutionState,
            commandChannel = commandChannel,
            serviceUuid = FT10C_SERVICE_UUID,
            characteristicUuid = FT10C_TIME_WRITE_UUID,
            onCommandCompleted = ::onCommandCompleted,
            onTimeStatus = { core.bleUiEvents.dispatch(BleUiEvent.TimeSyncStatus(it)) },
            onCanSyncTimeChanged = { core.bleUiEvents.dispatch(BleUiEvent.CanSyncTimeChanged(it)) },
            logger = ::log
        )
    }
    private val commandOperations: Ft10cCommandOperations by commandOperationsDelegate

    val hasGatt: Boolean get() = gatt != null
    val isConnected: Boolean get() = connectedAddress != null && gatt != null

    fun connect(device: BluetoothDevice, address: String) = connectionCoordinator.connect(device, address)
    fun disconnect(reason: String) = connectionCoordinator.disconnect(reason)
    fun sendProfile(trigger: String) = commandOperations.sendProfile(trigger)
    fun syncTime(automatic: Boolean = false) = commandOperations.syncTime(automatic)

    fun onTimeSyncSettingsChanged() {
        if (!core.timeSyncState.automaticMode) {
            core.syncCoordinator.cancelPeriodicTimeSync()
            if (commandOperationsDelegate.isInitialized()) commandOperations.cancelTime()
            log("AUTO TIME SYNC: periodic schedule disabled")
        } else {
            scheduleNextAutomaticTimeSync("настройки изменены")
        }
    }

    private fun scheduleNextAutomaticTimeSync(reason: String) {
        val enabled = core.timeSyncState.automaticMode && core.settingsRepository.automaticTimeSync
        core.syncCoordinator.schedulePeriodicTimeSync(
            intervalMinutes = core.settingsRepository.automaticTimeSyncIntervalMinutes,
            isConnected = { connectedAddress != null && gatt != null },
            isEnabled = { core.timeSyncState.automaticMode && core.settingsRepository.automaticTimeSync },
            onTimeSync = {
                log("AUTO TIME SYNC: ${core.settingsRepository.automaticTimeSyncIntervalMinutes}-minute interval reached; schedule reason: $reason")
                syncTime(automatic = true)
            }
        )
        if (enabled && connectedAddress != null) {
            log("AUTO TIME SYNC: next synchronization in ${core.settingsRepository.automaticTimeSyncIntervalMinutes} min")
        }
    }

    fun requestHistory(trigger: String, force: Boolean = false) = commandOperations.requestHistory(trigger, force)
    fun requestBattery(force: Boolean = false) = commandOperations.requestBattery(force)
    fun cancelHistory() { if (commandOperationsDelegate.isInitialized()) commandOperations.cancelHistory() }

    private fun onCommandCompleted(name: String, status: Int, timedOut: Boolean) {
        commandExecutionState.clearIfMatches(name)
        when (name) {
            "PROFILE" -> log("PROFILE A9: ${if (timedOut) "BLE callback timeout" else "BLE write callback status=$status"}; bracelet-side application is confirmed separately by A9 00")
            "TIME" -> {
                core.timeSyncState.markWriteCompleted()
                core.bleUiEvents.dispatch(BleUiEvent.TimeWriteCompleted(status, timedOut, connectedAddress != null))
                scheduleNextAutomaticTimeSync("BLE callback completed")
            }
            "BATTERY" -> log(if (timedOut) "BATTERY A2: BLE callback timeout; A2 notification may still arrive" else "BATTERY A2 write callback: status=$status; waiting for A2 notification")
            "HISTORY" -> log(if (timedOut) "STEP HISTORY: BLE write callback timeout; waiting for/checking B2 response" else "STEP HISTORY: BLE write callback status=$status")
        }
    }

    private fun hasBlePermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun log(message: String) = core.appendTechnicalLog(message)
    private fun safeAddress(device: BluetoothDevice): String? = try { device.address } catch (_: SecurityException) { null }
    private fun gattStateName(state: Int): String = when (state) {
        android.bluetooth.BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        android.bluetooth.BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        android.bluetooth.BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN"
    }
    private fun gattStatusName(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
        8 -> "GATT_CONN_TIMEOUT"
        19 -> "GATT_CONN_TERMINATE_PEER_USER"
        22 -> "GATT_CONN_TERMINATE_LOCAL_HOST"
        133 -> "GATT_ERROR (133)"
        else -> "status=$status"
    }
    private fun properties(value: Int): String = buildList {
        if (value and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (value and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (value and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (value and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (value and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    }.joinToString("|")

    override fun close() {
        activeDeviceProfile = null
        transportLifecycleCoordinator.onRuntimeClose("application runtime closed")
        core.sessionLifecycleCoordinator.onDisconnected()
        core.bleConnectionState.disconnected()
    }

    companion object {
        private val FT10C_SERVICE_UUID: UUID = Ft10cDeviceProfile.serviceUuid
        private val FT10C_TIME_WRITE_UUID: UUID = Ft10cDeviceProfile.writeCharacteristicUuid
    }
}
