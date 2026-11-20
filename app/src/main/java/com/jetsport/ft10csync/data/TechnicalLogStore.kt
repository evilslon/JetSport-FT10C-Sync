package com.jetsport.ft10csync.data

import java.util.ArrayDeque

/** Thread-safe bounded technical log snapshot with optional durable persistence. */
class TechnicalLogStore(
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val persistence: TechnicalLogPersistence? = null
) {
    private val records = ArrayDeque<String>()
    private var bytes = 0

    init {
        require(maxRecords > 0) { "maxRecords must be positive" }
        require(maxBytes > 0) { "maxBytes must be positive" }
        restore(persistence?.load().orEmpty())
    }

    @Synchronized fun append(line: String) {
        val normalized = if (line.endsWith("\n")) line else "$line\n"
        if (!appendInMemory(normalized)) return
        persistSnapshot()
    }

    @Synchronized fun snapshot(): String = records.joinToString(separator = "")
    @Synchronized fun recordCount(): Int = records.size
    @Synchronized fun byteCount(): Int = bytes

    private fun restore(content: String) {
        if (content.isEmpty()) return
        content.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
            appendInMemory("$line\n")
        }
        // Rewrite old/oversized persisted data once so the on-disk copy obeys current limits.
        if (snapshot() != content) persistSnapshot()
    }

    /** Returns false when a single record cannot fit within the configured byte budget. */
    private fun appendInMemory(normalized: String): Boolean {
        val recordBytes = normalized.toByteArray(Charsets.UTF_8).size
        if (recordBytes > maxBytes) return false
        records.addLast(normalized)
        bytes += recordBytes
        while (records.size > maxRecords || bytes > maxBytes) {
            bytes -= records.removeFirst().toByteArray(Charsets.UTF_8).size
        }
        return true
    }

    private fun persistSnapshot() {
        persistence?.save(snapshot())
    }

    companion object {
        const val DEFAULT_MAX_RECORDS = 500
        const val DEFAULT_MAX_BYTES = 256 * 1024
    }
}

/** Small persistence boundary keeps the bounded store independently unit-testable. */
interface TechnicalLogPersistence {
    fun load(): String
    fun save(snapshot: String)
}
