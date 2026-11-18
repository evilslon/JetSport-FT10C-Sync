package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Observes Bluetooth adapter state while the owning Activity is started. */
class BluetoothStateMonitor(
    context: Context,
    private val onAvailabilityChanged: (enabled: Boolean) -> Unit,
    private val logger: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var registered = false
    private var lastState: Int? = null
    private var lastAvailability: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            publish(state, "broadcast")
        }
    }


    fun register() {
        if (registered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
        registered = true
        val state = try { BluetoothAdapter.getDefaultAdapter()?.state ?: BluetoothAdapter.ERROR } catch (_: Exception) { BluetoothAdapter.ERROR }
        publish(state, "lifecycle-check")
    }

    fun unregister() {
        if (!registered) return
        try { appContext.unregisterReceiver(receiver) } finally { registered = false }
    }

    private fun publish(state: Int, source: String) {
        if (lastState == state) return
        val previous = lastState
        lastState = state
        val label = stateLabel(state)
        logger("Bluetooth state ${previous?.let(::stateLabel) ?: "UNKNOWN"} -> $label (source=$source, time=${timestamp()})")
        val availability = when (state) {
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> false
            BluetoothAdapter.STATE_ON -> true
            else -> null
        }
        if (availability != null && availability != lastAvailability) {
            lastAvailability = availability
            logger("Bluetooth availability reaction: ${if (availability) "available" else "unavailable"} (state=$label, source=$source)")
            onAvailabilityChanged(availability)
        }
    }

    private fun stateLabel(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
        else -> "UNKNOWN($state)"
    }

    private fun timestamp() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}
