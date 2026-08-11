package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class PreflightFactorState {
    HOLDS,
    CHECK_NOW,
    SCHEDULED,
    MISSING
}

internal enum class PreflightProtocolState {
    SEALED,
    PLANNED,
    ACTION_NOW,
    INCOMPLETE
}

internal data class PreflightFactorCheck(
    val factor: SignalFactor,
    val state: PreflightFactorState,
    val scheduledAt: Long?
) {
    init {
        require(scheduledAt == null || scheduledAt >= 0L)
        require(
            (state == PreflightFactorState.HOLDS) ==
                (scheduledAt == null)
        )
    }
}

internal data class PreflightSlot(
    val scheduledAt: Long,
    val factors: List<SignalFactor>,
    val immediate: Boolean
) {
    init {
        require(scheduledAt >= 0L)
        require(factors.isNotEmpty())
        require(factors.distinct().size == factors.size)
        require(factors.zipWithNext().all { (left, right) ->
            left.ordinal < right.ordinal
        })
    }
}

internal data class PreflightProtocol(
    val eventId: String,
    val eventLabel: String,
    val evaluatedAtMinute: Long,
    val start: ResolvedEventStart,
    val checks: List<PreflightFactorCheck>,
    val slots: List<PreflightSlot>,
    val state: PreflightProtocolState,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(evaluatedAtMinute >= 0L)
        require(checks.size == SignalFactor.values().size)
        require(checks.map { it.factor } == SignalFactor.values().toList())
        require(slots.zipWithNext().all { (left, right) ->
            left.scheduledAt < right.scheduledAt
        })
        require(fingerprint.length == 64)
    }

    val evaluatedAt: Long
        get() = evaluatedAtMinute * MINUTE_MILLIS

    val actionCount: Int
        get() = checks.count { it.state != PreflightFactorState.HOLDS }

    val holdingCount: Int
        get() = checks.size - actionCount

    val missingCount: Int
        get() = checks.count { it.state == PreflightFactorState.MISSING }

    val nextSlot: PreflightSlot?
        get() = slots.firstOrNull()

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

internal object PreflightProtocolEngine {
    private const val VERSION = "sport-pulse-preflight-protocol-v1"
    private const val MINUTE_MILLIS = 60_000L
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        event: SportEvent,
        relay: EvidenceRelayResult
    ): PreflightProtocol {
        require(event.id == relay.eventId)
        val evaluatedAt = relay.evaluatedAtMinute * MINUTE_MILLIS
        val checks = relay.factors.map { relayFactor ->
            when (relayFactor.state) {
                EvidenceRelayFactorState.SURVIVES ->
                    PreflightFactorCheck(
                        factor = relayFactor.factor,
                        state = PreflightFactorState.HOLDS,
                        scheduledAt = null
                    )
                EvidenceRelayFactorState.UNCONFIRMED ->
                    PreflightFactorCheck(
                        factor = relayFactor.factor,
                        state = PreflightFactorState.MISSING,
                        scheduledAt = evaluatedAt
                    )
                EvidenceRelayFactorState.RECHECK_REQUIRED -> {
                    val safeAt = checkNotNull(
                        relayFactor.safeRecheckAt
                    )
                    val immediate = safeAt <= relay.evaluatedAt
                    PreflightFactorCheck(
                        factor = relayFactor.factor,
                        state = if (immediate) {
                            PreflightFactorState.CHECK_NOW
                        } else {
                            PreflightFactorState.SCHEDULED
                        },
                        scheduledAt = if (immediate) {
                            evaluatedAt
                        } else {
                            ceilToMinute(safeAt)
                        }
                    )
                }
            }
        }
        val slots = checks
            .filter { it.scheduledAt != null }
            .groupBy { checkNotNull(it.scheduledAt) }
            .toSortedMap()
            .map { (scheduledAt, groupedChecks) ->
                PreflightSlot(
                    scheduledAt = scheduledAt,
                    factors = groupedChecks
                        .map { it.factor }
                        .sortedBy { it.ordinal },
                    immediate = scheduledAt == evaluatedAt
                )
            }
        val state = when {
            checks.any {
                it.state == PreflightFactorState.MISSING
            } -> PreflightProtocolState.INCOMPLETE
            checks.any {
                it.state == PreflightFactorState.CHECK_NOW
            } -> PreflightProtocolState.ACTION_NOW
            slots.isNotEmpty() -> PreflightProtocolState.PLANNED
            else -> PreflightProtocolState.SEALED
        }
        val fingerprint = fingerprintFor(
            event = event,
            relay = relay,
            checks = checks,
            slots = slots,
            state = state
        )
        return PreflightProtocol(
            eventId = event.id,
            eventLabel = event.match,
            evaluatedAtMinute = relay.evaluatedAtMinute,
            start = relay.start,
            checks = checks,
            slots = slots,
            state = state,
            fingerprint = fingerprint
        )
    }

    private fun ceilToMinute(value: Long): Long {
        if (value == 0L) return 0L
        return ((value - 1L) / MINUTE_MILLIS + 1L) *
            MINUTE_MILLIS
    }

    private fun fingerprintFor(
        event: SportEvent,
        relay: EvidenceRelayResult,
        checks: List<PreflightFactorCheck>,
        slots: List<PreflightSlot>,
        state: PreflightProtocolState
    ): String {
        val payload = buildString {
            append(VERSION)
            append("|event=")
            append(event.id)
            append(':')
            append(event.match)
            append("|relay=")
            append(relay.fingerprint)
            append("|state=")
            append(state.name)
            checks.forEach { check ->
                append("|check=")
                append(check.factor.name)
                append(':')
                append(check.state.name)
                append(':')
                append(
                    check.scheduledAt?.div(MINUTE_MILLIS)
                        ?: -1L
                )
            }
            slots.forEach { slot ->
                append("|slot=")
                append(slot.scheduledAt / MINUTE_MILLIS)
                append(':')
                append(slot.immediate)
                append(':')
                append(slot.factors.joinToString(",") { it.name })
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
