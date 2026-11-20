package com.jetsport.ft10csync.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ft10cCommandExecutionStateTest {
    @Test
    fun `mark started exposes current command and busy state`() {
        val state = Ft10cCommandExecutionState()

        state.markStarted("TIME")

        assertEquals("TIME", state.pendingWriteKind)
        assertTrue(state.isBusy)
    }

    @Test
    fun `clear if matches does not clear a different command`() {
        val state = Ft10cCommandExecutionState()
        state.markStarted("BATTERY")

        assertFalse(state.clearIfMatches("TIME"))
        assertEquals("BATTERY", state.pendingWriteKind)
        assertTrue(state.isBusy)
    }

    @Test
    fun `matching completion clears pending command`() {
        val state = Ft10cCommandExecutionState()
        state.markStarted("HISTORY")

        assertTrue(state.clearIfMatches("HISTORY"))
        assertEquals(null, state.pendingWriteKind)
        assertFalse(state.isBusy)
    }

    @Test
    fun `disconnect clear removes pending command`() {
        val state = Ft10cCommandExecutionState()
        state.markStarted("PROFILE")

        state.clear()

        assertEquals(null, state.pendingWriteKind)
        assertFalse(state.isBusy)
    }
}
