package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ft10cCommandQueueTest {
    private class FakeScheduler : Ft10cQueueScheduler {
        private val tasks = linkedMapOf<Runnable, Long>()
        override fun postDelayed(task: Runnable, delayMs: Long) { tasks[task] = delayMs }
        override fun removeCallbacks(task: Runnable) { tasks.remove(task) }
        fun runNext() {
            val task = tasks.keys.firstOrNull() ?: error("No scheduled task")
            tasks.remove(task)
            task.run()
        }
        fun pendingCount(): Int = tasks.size
    }

    @Test fun immediateWriteRejectionCompletesAndContinuesQueue() {
        val scheduler = FakeScheduler()
        val writes = mutableListOf<Byte>()
        val completed = mutableListOf<String>()
        val queue = Ft10cCommandQueue(scheduler, { packet ->
            writes += packet.single()
            if (packet.single() == 1.toByte()) 257 else 0
        }, {}, 5_000L)
        queue.enqueue("first", byteArrayOf(1), onCompleted = { _, _ -> completed += "first" })
        queue.enqueue("second", byteArrayOf(2), onCompleted = { _, _ -> completed += "second" })
        assertEquals(listOf(1.toByte(), 2.toByte()), writes)
        assertEquals(listOf("first"), completed)
        queue.onWriteCallback(0)
        assertEquals(listOf("first", "second"), completed)
        assertFalse(queue.isBusy)
    }

    @Test fun acceptedHandlerFailureKeepsActiveCommandUntilCallback() {
        val scheduler = FakeScheduler()
        var writeCount = 0
        val queue = Ft10cCommandQueue(scheduler, { writeCount++; 0 }, {})
        queue.enqueue("first", byteArrayOf(1), onAccepted = { error("observer failure") })
        queue.enqueue("second", byteArrayOf(2))
        assertEquals(1, writeCount)
        assertEquals(1, scheduler.pendingCount())
        queue.onWriteCallback(0)
        assertEquals(2, writeCount)
        assertEquals(1, scheduler.pendingCount())
    }

    @Test fun completionHandlerFailureDoesNotStopNextCommand() {
        val scheduler = FakeScheduler()
        var writeCount = 0
        val queue = Ft10cCommandQueue(scheduler, { writeCount++; 0 }, {})
        queue.enqueue("first", byteArrayOf(1), onCompleted = { _, _ -> error("completion failure") })
        queue.enqueue("second", byteArrayOf(2))
        queue.onWriteCallback(0)
        assertEquals(2, writeCount)
        assertTrue(queue.isBusy)
    }

    @Test fun callbackTimeoutBlocksQueueUntilReconnect() {
        val scheduler = FakeScheduler()
        var writeCount = 0
        val rejected = mutableListOf<Pair<Int, Boolean>>()
        val queue = Ft10cCommandQueue(scheduler, { writeCount++; 0 }, {}, 100L)
        queue.enqueue("first", byteArrayOf(1))
        queue.enqueue("second", byteArrayOf(2))
        scheduler.runNext()
        assertEquals(1, writeCount)
        queue.enqueue("third", byteArrayOf(3), onCompleted = { status, timedOut -> rejected += status to timedOut })
        assertEquals(1, writeCount)
        assertTrue(rejected.single().second)
        queue.resetForNewConnection()
        queue.enqueue("after reconnect", byteArrayOf(4))
        assertEquals(2, writeCount)
        assertTrue(queue.isBusy)
    }

    @Test fun cancelAllRemovesTimeoutAndClearsQueue() {
        val scheduler = FakeScheduler()
        var writeCount = 0
        val queue = Ft10cCommandQueue(scheduler, { writeCount++; 0 }, {})
        queue.enqueue("active", byteArrayOf(1))
        queue.enqueue("pending", byteArrayOf(2))
        assertEquals(1, scheduler.pendingCount())
        queue.cancelAll("test")
        assertFalse(queue.isBusy)
        assertEquals(0, scheduler.pendingCount())
        queue.onWriteCallback(0)
        assertEquals(1, writeCount)
        assertFalse(queue.isBusy)
    }
    @Test fun cancelAllDropsActiveAndPendingCommandsAndIgnoresDelayedCallback() {
        val scheduler = FakeScheduler()
        val writes = mutableListOf<Byte>()
        val completed = mutableListOf<String>()
        val queue = Ft10cCommandQueue(scheduler, { packet ->
            writes += packet.single()
            0
        }, {}, 5_000L)

        queue.enqueue("active", byteArrayOf(1), onCompleted = { _, _ -> completed += "active" })
        queue.enqueue("pending", byteArrayOf(2), onCompleted = { _, _ -> completed += "pending" })
        queue.cancelAll("disconnect")

        assertEquals(listOf(1.toByte()), writes)
        assertTrue(completed.isEmpty())
        assertFalse(queue.isBusy)
        assertEquals(0, scheduler.pendingCount())

        // A callback arriving after cancellation must not complete a command
        // that was already discarded.
        queue.onWriteCallback(0)
        assertTrue(completed.isEmpty())
        assertFalse(queue.isBusy)
    }

    @Test fun callbackAfterCancellationDoesNotPreventNewCommandFromStarting() {
        val scheduler = FakeScheduler()
        val writes = mutableListOf<Byte>()
        val queue = Ft10cCommandQueue(scheduler, { packet ->
            writes += packet.single()
            0
        }, {})

        queue.enqueue("cancelled", byteArrayOf(1))
        queue.cancelAll("disconnect")
        queue.onWriteCallback(0) // delayed callback while no command is active
        queue.enqueue("new session command", byteArrayOf(2))

        assertEquals(listOf(1.toByte(), 2.toByte()), writes)
        assertTrue(queue.isBusy)
        assertEquals(1, scheduler.pendingCount())
    }

    @Test fun callbackFailureCompletesActiveCommandAndContinuesQueue() {
        val scheduler = FakeScheduler()
        val statuses = mutableListOf<Pair<Int, Boolean>>()
        var writeCount = 0
        val queue = Ft10cCommandQueue(scheduler, { writeCount++; 0 }, {})

        queue.enqueue("first", byteArrayOf(1), onCompleted = { status, timedOut ->
            statuses += status to timedOut
        })
        queue.enqueue("second", byteArrayOf(2))
        queue.onWriteCallback(133)

        assertEquals(listOf(133 to false), statuses)
        assertEquals(2, writeCount)
        assertTrue(queue.isBusy)
        assertEquals(1, scheduler.pendingCount())
    }

    @Test fun resetForNewConnectionClearsActiveAndPendingCommandsAndRearmsQueue() {
        val scheduler = FakeScheduler()
        val writes = mutableListOf<Byte>()
        val queue = Ft10cCommandQueue(scheduler, { packet ->
            writes += packet.single()
            0
        }, {}, 100L)

        queue.enqueue("old active", byteArrayOf(1))
        queue.enqueue("old pending", byteArrayOf(2))
        queue.resetForNewConnection()

        assertFalse(queue.isBusy)
        assertEquals(0, scheduler.pendingCount())
        queue.enqueue("new session", byteArrayOf(3))

        assertEquals(listOf(1.toByte(), 3.toByte()), writes)
        assertTrue(queue.isBusy)
        assertEquals(1, scheduler.pendingCount())
    }

    @Test fun timeoutDropsPendingCommandsAndRequiresExplicitReconnectReset() {
        val scheduler = FakeScheduler()
        val writes = mutableListOf<Byte>()
        val completion = mutableListOf<Pair<Int, Boolean>>()
        val queue = Ft10cCommandQueue(scheduler, { packet ->
            writes += packet.single()
            0
        }, {}, 100L)

        queue.enqueue("active", byteArrayOf(1), onCompleted = { status, timedOut ->
            completion += status to timedOut
        })
        queue.enqueue("pending", byteArrayOf(2))
        scheduler.runNext()

        assertEquals(listOf(1.toByte()), writes)
        assertEquals(1, completion.size)
        assertTrue(completion.single().second)
        assertTrue(queue.isBusy)
        assertEquals(0, scheduler.pendingCount())

        queue.enqueue("blocked", byteArrayOf(3))
        assertEquals(listOf(1.toByte()), writes)
        queue.resetForNewConnection()
        queue.enqueue("after reconnect", byteArrayOf(4))
        assertEquals(listOf(1.toByte(), 4.toByte()), writes)
    }

}
