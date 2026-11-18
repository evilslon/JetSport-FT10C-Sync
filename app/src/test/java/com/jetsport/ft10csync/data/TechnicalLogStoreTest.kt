package com.jetsport.ft10csync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalLogStoreTest {
    @Test fun removesOldestWhenRecordLimitExceeded() {
        val store = TechnicalLogStore(maxRecords = 2, maxBytes = 1024)
        store.append("one")
        store.append("two")
        store.append("three")
        assertEquals("two\nthree\n", store.snapshot())
        assertEquals(2, store.recordCount())
    }

    @Test fun removesOldestByUtf8ByteLimit() {
        val store = TechnicalLogStore(maxRecords = 10, maxBytes = 7)
        store.append("éé") // 5 bytes including newline
        store.append("abc") // 4 bytes including newline
        assertEquals("abc\n", store.snapshot())
        assertEquals(4, store.byteCount())
    }

    @Test fun rejectsRecordLargerThanByteLimit() {
        val store = TechnicalLogStore(maxRecords = 5, maxBytes = 4)
        store.append("too-long")
        assertEquals("", store.snapshot())
        assertEquals(0, store.recordCount())
    }

    @Test fun defaultsRetainOnlyLatestFiveHundredRecords() {
        val store = TechnicalLogStore()
        repeat(510) { store.append("record-$it") }
        assertEquals(500, store.recordCount())
        assertTrue(store.byteCount() <= TechnicalLogStore.DEFAULT_MAX_BYTES)
        assertTrue(store.snapshot().startsWith("record-10\n"))
    }
}
