package com.jetsport.ft10csync.ble

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Immutable BLE-to-presentation events. Payloads contain decoded values, never Android GATT objects. */
sealed class BleUiEvent {
    data object TechnicalLogChanged : BleUiEvent()
    data object PhoneFinderActivated : BleUiEvent()
    data class PhoneFinderAlertChanged(val active: Boolean) : BleUiEvent()
    data object BlePermissionsRequired : BleUiEvent()
    data class TimeSyncStatus(val message: String) : BleUiEvent()
    data class CanSyncTimeChanged(val enabled: Boolean) : BleUiEvent()
    data class TimeWriteCompleted(val status: Int, val timedOut: Boolean, val connected: Boolean) : BleUiEvent()
    data class GattConnected(val address: String) : BleUiEvent()
    data class GattDisconnected(val address: String) : BleUiEvent()
    data class UnsupportedGattDevice(val address: String) : BleUiEvent()
    data class GattProfileReady(
        val modelId: String,
        val timeCharacteristicFound: Boolean,
        val timeWriteAvailable: Boolean
    ) : BleUiEvent()
    data class GattConnectTimedOut(val address: String) : BleUiEvent()
    data class GattServiceDiscoveryTimedOut(val address: String) : BleUiEvent()
    data class GattServiceDiscoveryFailed(val address: String, val status: Int) : BleUiEvent()
    data class LiveDayChanged(val dayKey: String) : BleUiEvent()
    data class LiveStepsChanged(val count: Int) : BleUiEvent()
    data class HistoryCommitted(val recordCount: Int) : BleUiEvent()
    data class HistoryProgress(val receivedRecords: Int) : BleUiEvent()
    data class BatteryReceived(val percent: Int) : BleUiEvent()
    data class HeartRateReceived(val bpm: Int, val time: String) : BleUiEvent()
    data class BloodPressureReceived(val systolic: Int, val diastolic: Int, val time: String) : BleUiEvent()
}

/**
 * Application-scoped event stream between BLE workflows and presentation.
 * Producers never retain an Activity or invoke UI callbacks. Events are emitted in
 * order on the caller thread. The stream is intentionally non-replaying: events are
 * delivered to active subscribers only and are not durable application state. UI
 * state needed after Activity recreation must be restored from its owning store.
 */
internal class BleUiEventDispatcher {
    private val mutableEvents = MutableSharedFlow<BleUiEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<BleUiEvent> = mutableEvents.asSharedFlow()

    fun dispatch(event: BleUiEvent) {
        mutableEvents.tryEmit(event)
    }
}
