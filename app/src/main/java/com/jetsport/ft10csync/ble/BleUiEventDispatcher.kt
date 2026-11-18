package com.jetsport.ft10csync.ble

import java.util.concurrent.Executor

/** Immutable BLE-to-presentation events. Payloads contain decoded values, never Android GATT objects. */
sealed class BleUiEvent {
    data class LiveStepsChanged(val count: Int) : BleUiEvent()
    data class HistoryProgress(val receivedRecords: Int) : BleUiEvent()
    data class BatteryReceived(val percent: Int) : BleUiEvent()
    data class HeartRateReceived(val bpm: Int, val time: String) : BleUiEvent()
    data class BloodPressureReceived(val systolic: Int, val diastolic: Int, val time: String) : BleUiEvent()
}

/** Serializes decoded BLE events onto the UI executor without exposing Activity to BLE handlers. */
class BleUiEventDispatcher(
    private val uiExecutor: Executor,
    private val consumer: (BleUiEvent) -> Unit
) {
    fun dispatch(event: BleUiEvent) {
        uiExecutor.execute { consumer(event) }
    }
}
