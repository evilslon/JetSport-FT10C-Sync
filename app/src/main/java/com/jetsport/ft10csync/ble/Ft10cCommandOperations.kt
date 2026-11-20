package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.os.Handler
import com.jetsport.ft10csync.StepProfile
import com.jetsport.ft10csync.sync.Ft10cBatteryCoordinator
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import com.jetsport.ft10csync.sync.Ft10cTimeSyncState
import java.util.UUID

/**
 * Composition boundary for all FT-10C outbound command workflows.
 *
 * The individual coordinators retain their existing protocol and retry behavior;
 * this class only owns their construction and exposes the four operations plus
 * their cancellation hooks. The current Activity is still the session adapter
 * supplying GATT/session facts and UI callbacks. A later BLE-runtime step can
 * move this composition root without changing the command coordinators.
 */
internal class Ft10cCommandOperations(
    handler: Handler,
    gattProvider: () -> BluetoothGatt?,
    isCurrentConnection: (BluetoothGatt) -> Boolean,
    hasBlePermissions: () -> Boolean,
    requestBlePermissions: () -> Unit,
    isFt10cProfileConfirmed: () -> Boolean,
    profileIsBusy: () -> Boolean,
    timeIsBusy: () -> Boolean,
    historyIsBusy: () -> Boolean,
    batteryIsBusy: () -> Boolean,
    profileProvider: () -> StepProfile,
    timeSyncState: Ft10cTimeSyncState,
    historyCoordinator: Ft10cHistoryCoordinator,
    batteryCoordinator: Ft10cBatteryCoordinator,
    notificationCoordinator: Ft10cNotificationCoordinator,
    commandExecutionState: Ft10cCommandExecutionState,
    commandChannel: Ft10cGattCommandChannel,
    serviceUuid: UUID,
    characteristicUuid: UUID,
    onCommandCompleted: (String, Int, Boolean) -> Unit,
    onTimeStatus: (String) -> Unit,
    onCanSyncTimeChanged: (Boolean) -> Unit,
    logger: (String) -> Unit
) {
    private val profile = Ft10cProfileCommandCoordinator(
        handler = handler,
        gattProvider = gattProvider,
        isCurrentConnection = isCurrentConnection,
        hasBlePermissions = hasBlePermissions,
        isFt10cProfileConfirmed = isFt10cProfileConfirmed,
        isOtherWorkflowBusy = profileIsBusy,
        profileProvider = profileProvider,
        commandChannel = commandChannel,
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,
        setPendingWriteKind = commandExecutionState::markStarted,
        onCommandCompleted = onCommandCompleted,
        logger = logger
    )

    private val time = Ft10cTimeCommandCoordinator(
        handler = handler,
        gattProvider = gattProvider,
        isCurrentConnection = isCurrentConnection,
        hasBlePermissions = hasBlePermissions,
        requestBlePermissions = requestBlePermissions,
        isFt10cProfileConfirmed = isFt10cProfileConfirmed,
        isOtherWorkflowBusy = timeIsBusy,
        timeSyncState = timeSyncState,
        commandChannel = commandChannel,
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,
        setPendingWriteKind = commandExecutionState::markStarted,
        onCommandCompleted = onCommandCompleted,
        onStatus = onTimeStatus,
        onCanSyncTimeChanged = onCanSyncTimeChanged,
        logger = logger
    )

    private val history = Ft10cHistoryCommandCoordinator(
        handler = handler,
        gattProvider = gattProvider,
        isCurrentConnection = isCurrentConnection,
        hasBlePermissions = hasBlePermissions,
        isBusy = historyIsBusy,
        historyCoordinator = historyCoordinator,
        notificationCoordinator = notificationCoordinator,
        commandChannel = commandChannel,
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,
        setPendingWriteKind = commandExecutionState::markStarted,
        onCommandCompleted = onCommandCompleted,
        logger = logger
    )

    private val battery = Ft10cBatteryCommandCoordinator(
        gattProvider = gattProvider,
        isCurrentConnection = isCurrentConnection,
        hasBlePermissions = hasBlePermissions,
        isBusy = batteryIsBusy,
        batteryCoordinator = batteryCoordinator,
        commandChannel = commandChannel,
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,
        setPendingWriteKind = commandExecutionState::markStarted,
        onCommandCompleted = onCommandCompleted,
        logger = logger
    )

    fun sendProfile(trigger: String) = profile.send(trigger)
    fun syncTime(automatic: Boolean = false) = time.sync(automatic)
    fun requestHistory(trigger: String, force: Boolean = false) = history.request(trigger, force)
    fun requestBattery(force: Boolean = false) = battery.request(force)

    fun cancelProfile() = profile.cancelPending()
    fun cancelTime() = time.cancelPending()
    fun cancelHistory() = history.cancelPending()
}
