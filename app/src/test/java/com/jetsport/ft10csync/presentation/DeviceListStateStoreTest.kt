package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListStateStoreTest {
    @Test fun upsertAddsAndUpdatesDeviceWithoutDuplicatingItsRow() {
        val store = DeviceListStateStore()
        store.upsert("AA", "FT-10C", -60, "AA", null)
        store.upsert("AA", "FT-10C-93DC", -45, "AA", null)

        assertEquals(1, store.snapshot().size)
        assertEquals("FT-10C-93DC", store.nameFor("AA"))
        assertEquals(-45, store.snapshot().single().rssi)
        assertTrue(store.snapshot().single().selected)
    }

    @Test fun refreshMovesSelectionAndConnectionFlagsWithoutChangingDiscoveryData() {
        val store = DeviceListStateStore()
        store.upsert("AA", "FT-10C", -55, "AA", null)
        store.upsert("BB", "Other", -80, "AA", null)

        store.refresh("BB", "AA")

        val rows = store.snapshot().associateBy { it.address }
        assertTrue(rows.getValue("AA").connected)
        assertFalse(rows.getValue("AA").selected)
        assertTrue(rows.getValue("BB").selected)
        assertEquals("Other", rows.getValue("BB").name)
        assertEquals(-80, rows.getValue("BB").rssi)
    }

    @Test fun retainAddressesKeepsOnlyRequestedRowsAndPreservesOrder() {
        val store = DeviceListStateStore()
        store.upsert("AA", "First", -50, null, null)
        store.upsert("BB", "Second", -60, null, null)
        store.upsert("CC", "Third", -70, null, null)

        store.retainAddresses(linkedSetOf("CC", "AA"), "CC", "AA")

        assertEquals(listOf("CC", "AA"), store.snapshot().map { it.address })
        assertTrue(store.snapshot()[0].selected)
        assertTrue(store.snapshot()[1].connected)
    }

    @Test fun unknownRetainedAddressesAreIgnored() {
        val store = DeviceListStateStore()
        store.upsert("AA", "Known", -55, null, null)

        store.retainAddresses(setOf("MISSING"), null, null)

        assertTrue(store.snapshot().isEmpty())
        assertFalse(store.contains("AA"))
        assertNull(store.nameFor("AA"))
    }

    @Test fun clearRemovesAllRows() {
        val store = DeviceListStateStore()
        store.upsert("AA", "Known", -55, null, null)
        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }
}
