package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class BleUiEventDispatcherTest {
    @Test fun deliversDecodedEventThroughProvidedUiExecutor() {
        val queue = mutableListOf<Runnable>()
        val received = mutableListOf<BleUiEvent>()
        val dispatcher = BleUiEventDispatcher(Executor { queue += it }, received::add)

        dispatcher.dispatch(BleUiEvent.BatteryReceived(50))
        assertEquals(0, received.size)
        queue.single().run()
        assertEquals(listOf(BleUiEvent.BatteryReceived(50)), received)
    }
    @Test fun preservesSubmissionOrderForMultipleEvents() {
        val queue = mutableListOf<Runnable>()
        val received = mutableListOf<BleUiEvent>()
        val dispatcher = BleUiEventDispatcher(Executor { queue += it }, received::add)
        val events = listOf(
            BleUiEvent.HistoryProgress(1),
            BleUiEvent.BatteryReceived(72),
            BleUiEvent.LiveStepsChanged(640)
        )

        events.forEach(dispatcher::dispatch)
        assertEquals(0, received.size)
        queue.forEach(Runnable::run)

        assertEquals(events, received)
    }

}
