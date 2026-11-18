package com.jetsport.ft10csync.sync

import com.jetsport.ft10csync.protocol.Ft10cHistoryEvent
import com.jetsport.ft10csync.protocol.Ft10cHistorySession
import com.jetsport.ft10csync.protocol.Ft10cStepRecord

/** Owns the lifecycle of one untagged B2 history stream. BLE I/O and persistence stay outside. */
class Ft10cHistoryCoordinator(
    private val minimumRequestIntervalMs: Long = DEFAULT_MINIMUM_REQUEST_INTERVAL_MS
) {
    enum class RequestDecision { READY, BLOCKED_UNTIL_RECONNECT, ALREADY_IN_FLIGHT, TOO_SOON }

    sealed class PacketResult {
        data class Ignored(val reason: String) : PacketResult()
        data class RecordAccepted(val record: Ft10cStepRecord, val recordCount: Int) : PacketResult()
        data class Completed(
            val records: List<Ft10cStepRecord>,
            val trailingByte: Int?,
            val valid: Boolean
        ) : PacketResult()
        data class Invalid(val reason: String) : PacketResult()
    }

    data class Timeout(val discardedRecordCount: Int)

    private var inFlight = false
    private var session: Ft10cHistorySession? = null
    private var sessionInvalid = false
    private var blockedUntilReconnect = false
    private var lastRequestAt = 0L
    private var deferredRetryAttempts = 0

    val isInFlight: Boolean get() = inFlight
    val isBlockedUntilReconnect: Boolean get() = blockedUntilReconnect

    fun requestDecision(now: Long, force: Boolean): RequestDecision = when {
        blockedUntilReconnect -> RequestDecision.BLOCKED_UNTIL_RECONNECT
        inFlight -> RequestDecision.ALREADY_IN_FLIGHT
        !force && now - lastRequestAt < minimumRequestIntervalMs -> RequestDecision.TOO_SOON
        else -> RequestDecision.READY
    }

    /** Called only after the B2 FA write has been accepted by the GATT queue. */
    fun onRequestAccepted(now: Long) {
        deferredRetryAttempts = 0
        inFlight = true
        session = Ft10cHistorySession()
        sessionInvalid = false
        lastRequestAt = now
    }

    fun onPacket(payload: ByteArray): PacketResult {
        if (blockedUntilReconnect || !inFlight) {
            return PacketResult.Ignored("no active request or history stream blocked until reconnect")
        }
        val activeSession = session ?: Ft10cHistorySession().also { session = it }
        return when (val event = activeSession.accept(payload)) {
            is Ft10cHistoryEvent.Record ->
                PacketResult.RecordAccepted(event.value, activeSession.snapshot().size)
            is Ft10cHistoryEvent.End -> {
                inFlight = false
                val result = PacketResult.Completed(
                    records = activeSession.snapshot(),
                    trailingByte = event.trailingByte,
                    valid = !sessionInvalid
                )
                session = null
                sessionInvalid = false
                result
            }
            is Ft10cHistoryEvent.Invalid -> {
                sessionInvalid = true
                PacketResult.Invalid(event.reason)
            }
        }
    }

    /** Timeout permanently blocks untagged B2 RX until a fresh GATT connection. */
    fun onTimeout(): Timeout? {
        if (!inFlight) return null
        val staged = session?.snapshot()?.size ?: 0
        inFlight = false
        session = null
        sessionInvalid = false
        blockedUntilReconnect = true
        return Timeout(staged)
    }

    /** Returns true while another bounded queue-busy deferral may be scheduled. */
    fun tryScheduleDeferredRetry(): Boolean {
        if (deferredRetryAttempts >= MAX_DEFERRED_RETRY_ATTEMPTS) return false
        deferredRetryAttempts++
        return true
    }

    /** Clears the queue-busy retry budget after a request or session boundary. */
    fun resetDeferredRetryBudget() {
        deferredRetryAttempts = 0
    }

    /** Disconnect clears the active session but deliberately retains timeout blocking. */
    fun onDisconnected() {
        resetDeferredRetryBudget()
        inFlight = false
        session = null
        sessionInvalid = false
    }

    /** A genuinely new GATT connection is the only operation that clears the timeout block. */
    fun onConnected() {
        onDisconnected()
        blockedUntilReconnect = false
    }

    /** Used when replacing a GATT connection before its connected callback arrives. */
    fun cancelActiveSession() = onDisconnected()

    companion object {
        const val DEFAULT_MINIMUM_REQUEST_INTERVAL_MS = 15 * 60 * 1000L
        const val DEFAULT_TIMEOUT_MS = 45_000L
        const val DEFER_RETRY_MS = 4_000L
        const val MAX_DEFERRED_RETRY_ATTEMPTS = 12
    }
}
