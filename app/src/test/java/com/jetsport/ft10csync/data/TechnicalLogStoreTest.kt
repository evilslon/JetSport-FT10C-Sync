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
    @Test fun restoresPersistedJournalAfterStoreRecreation() {
        val persistence = InMemoryTechnicalLogPersistence()
        val firstRun = TechnicalLogStore(maxRecords = 3, maxBytes = 1024, persistence = persistence)
        firstRun.append("first")
        firstRun.append("second")

        val secondRun = TechnicalLogStore(maxRecords = 3, maxBytes = 1024, persistence = persistence)
        assertEquals("first\nsecond\n", secondRun.snapshot())
        secondRun.append("third")

        val thirdRun = TechnicalLogStore(maxRecords = 3, maxBytes = 1024, persistence = persistence)
        assertEquals("first\nsecond\nthird\n", thirdRun.snapshot())
    }

    @Test fun restoredJournalIsTrimmedToCurrentLimitsAndSavedBack() {
        val persistence = InMemoryTechnicalLogPersistence("one\ntwo\nthree\n")
        val store = TechnicalLogStore(maxRecords = 2, maxBytes = 1024, persistence = persistence)

        assertEquals("two\nthree\n", store.snapshot())
        assertEquals("two\nthree\n", persistence.savedSnapshot)
        assertEquals(2, store.recordCount())
    }

    @Test fun persistedJournalRespectsUtf8ByteLimit() {
        val persistence = InMemoryTechnicalLogPersistence()
        val firstRun = TechnicalLogStore(maxRecords = 10, maxBytes = 7, persistence = persistence)
        firstRun.append("éé")
        firstRun.append("abc")

        val secondRun = TechnicalLogStore(maxRecords = 10, maxBytes = 7, persistence = persistence)
        assertEquals("abc\n", secondRun.snapshot())
        assertEquals(4, secondRun.byteCount())
    }

    private class InMemoryTechnicalLogPersistence(initial: String = "") : TechnicalLogPersistence {
        private var content = initial
        var savedSnapshot: String = initial
            private set

        override fun load(): String = content
        override fun save(snapshot: String) {
            content = snapshot
            savedSnapshot = snapshot
        }
    }

}
