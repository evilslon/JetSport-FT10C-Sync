package com.jetsport.ft10csync.ble

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BleUiEventDispatcherTest {
    @Test fun publishesDecodedEventToCurrentSubscriber() = runBlocking {
        val dispatcher = BleUiEventDispatcher()
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            dispatcher.events.take(1).toList()
        }

        dispatcher.dispatch(BleUiEvent.BatteryReceived(50))

        assertEquals(listOf(BleUiEvent.BatteryReceived(50)), received.await())
    }

    @Test fun preservesSubmissionOrderForMultipleEvents() = runBlocking {
        val dispatcher = BleUiEventDispatcher()
        val events = listOf(
            BleUiEvent.LiveDayChanged("2026-11-19"),
            BleUiEvent.HistoryProgress(1),
            BleUiEvent.BatteryReceived(72),
            BleUiEvent.LiveStepsChanged(640),
            BleUiEvent.HistoryCommitted(16)
        )
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            dispatcher.events.take(events.size).toList()
        }

        events.forEach(dispatcher::dispatch)

        assertEquals(events, received.await())
    }

    @Test fun publishesConnectionTimeoutEventsWithoutActivityCallbacks() = runBlocking {
        val dispatcher = BleUiEventDispatcher()
        val events = listOf(
            BleUiEvent.GattConnectTimedOut("AA:BB:CC:DD:EE:FF"),
            BleUiEvent.GattServiceDiscoveryTimedOut("AA:BB:CC:DD:EE:FF"),
            BleUiEvent.GattServiceDiscoveryFailed("AA:BB:CC:DD:EE:FF", 133)
        )
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            dispatcher.events.take(events.size).toList()
        }

        events.forEach(dispatcher::dispatch)

        assertEquals(events, received.await())
    }

    @Test fun publishesGattLifecycleEventsWithoutAndroidObjects() = runBlocking {
        val dispatcher = BleUiEventDispatcher()
        val events = listOf(
            BleUiEvent.GattConnected("AA:BB:CC:DD:EE:FF"),
            BleUiEvent.GattProfileReady("FT-10C", true, true),
            BleUiEvent.GattDisconnected("AA:BB:CC:DD:EE:FF")
        )
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            dispatcher.events.take(events.size).toList()
        }

        events.forEach(dispatcher::dispatch)

        assertEquals(events, received.await())
    }
}
