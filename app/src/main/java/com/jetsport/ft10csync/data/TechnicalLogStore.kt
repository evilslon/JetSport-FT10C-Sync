package com.jetsport.ft10csync.data
import java.util.ArrayDeque

/** Thread-safe bounded technical log snapshot used by UI and TXT export. */
class TechnicalLogStore(
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val maxBytes: Int = DEFAULT_MAX_BYTES
) {
    private val records = ArrayDeque<String>()
    private var bytes = 0

    init {
        require(maxRecords > 0) { "maxRecords must be positive" }
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    @Synchronized fun append(line: String) {
        val normalized = if (line.endsWith("\n")) line else "$line\n"
        val recordBytes = normalized.toByteArray(Charsets.UTF_8).size
        if (recordBytes > maxBytes) return
        records.addLast(normalized)
        bytes += recordBytes
        while (records.size > maxRecords || bytes > maxBytes) {
            bytes -= records.removeFirst().toByteArray(Charsets.UTF_8).size
        }
    }

    @Synchronized fun snapshot(): String = records.joinToString(separator = "")
    @Synchronized fun recordCount(): Int = records.size
    @Synchronized fun byteCount(): Int = bytes

    companion object {
        const val DEFAULT_MAX_RECORDS = 500
        const val DEFAULT_MAX_BYTES = 256 * 1024
    }
}
