package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

internal enum class EventStartSource {
    EVENT_PACK,
    DEMO_SCHEDULE
}

internal data class ResolvedEventStart(
    val startAt: Long,
    val source: EventStartSource
) {
    init {
        require(startAt >= 0L)
    }
}

internal object EventStartResolver {
    private val moscow = RegionalZone.MOSCOW.zoneId

    fun resolve(
        event: SportEvent,
        now: Long
    ): ResolvedEventStart? {
        require(now >= 0L)
        event.startAt?.let { exactStart ->
            return exactStart.takeIf { it > now }?.let {
                ResolvedEventStart(
                    startAt = it,
                    source = EventStartSource.EVENT_PACK
                )
            }
        }
        val schedule = event.demoSchedule ?: return null
        val nowMoscow = Instant.ofEpochMilli(now).atZone(moscow)
        val monday = nowMoscow.toLocalDate().with(
            TemporalAdjusters.previousOrSame(
                java.time.DayOfWeek.MONDAY
            )
        )
        var start = ZonedDateTime.of(
            monday.plusDays(
                schedule.dayOfWeek.ordinal.toLong()
            ),
            schedule.localTime,
            moscow
        )
        if (!start.toInstant().isAfter(nowMoscow.toInstant())) {
            start = start.plusWeeks(1L)
        }
        return ResolvedEventStart(
            startAt = start.toInstant().toEpochMilli(),
            source = EventStartSource.DEMO_SCHEDULE
        )
    }
}

internal data class EvidenceRelayInput(
    val event: SportEvent,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline
)

internal enum class EvidenceRelayFactorState {
    SURVIVES,
    RECHECK_REQUIRED,
    UNCONFIRMED
}

internal data class EvidenceRelayFactor(
    val factor: SignalFactor,
    val sourceLevel: EvidenceLevel,
    val currentLevel: EvidenceLevel,
    val startLevel: EvidenceLevel,
    val state: EvidenceRelayFactorState,
    val firstTransitionAt: Long?,
    val safeRecheckAt: Long?
) {
    init {
        if (state == EvidenceRelayFactorState.UNCONFIRMED) {
            require(sourceLevel == EvidenceLevel.UNCONFIRMED)
            require(safeRecheckAt == null)
        }
    }
}

internal enum class EvidenceRelayState {
    INTACT,
    RECHECK_REQUIRED,
    READINESS_DROP,
    NO_CONFIRMED_FACTS
}

internal data class EvidenceRelayResult(
    val eventId: String,
    val evaluatedAt: Long,
    val evaluatedAtMinute: Long,
    val start: ResolvedEventStart,
    val minutesUntilStart: Long,
    val currentFreshness: FreshnessResult,
    val startFreshness: FreshnessResult,
    val currentEvidenceResult: EvidenceResult,
    val startEvidenceResult: EvidenceResult,
    val factors: List<EvidenceRelayFactor>,
    val priorityFactor: SignalFactor?,
    val state: EvidenceRelayState,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(evaluatedAt >= 0L)
        require(evaluatedAtMinute >= 0L)
        require(
            evaluatedAt / MINUTE_MILLIS == evaluatedAtMinute
        )
        require(minutesUntilStart > 0L)
        require(factors.size == SignalFactor.values().size)
        require(HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    val survivingCount: Int
        get() = factors.count {
            it.state == EvidenceRelayFactorState.SURVIVES
        }

    val recheckCount: Int
        get() = factors.count {
            it.state == EvidenceRelayFactorState.RECHECK_REQUIRED
        }

    val unconfirmedCount: Int
        get() = factors.count {
            it.state == EvidenceRelayFactorState.UNCONFIRMED
        }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object EvidenceRelayEngine {
    private const val VERSION = "sport-pulse-evidence-relay-v1"
    private const val MINUTE_MILLIS = 60_000L
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        input: EvidenceRelayInput,
        now: Long
    ): EvidenceRelayResult? {
        require(now >= 0L)
        val start = EventStartResolver.resolve(
            event = input.event,
            now = now
        ) ?: return null
        val sourceIntegrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = input.claimedEvidence,
            audit = input.sourceAudit
        )
        val currentFreshness = FreshnessEngine.evaluate(
            evidence = sourceIntegrity.effectiveEvidence,
            timeline = input.timeline,
            now = now
        )
        val startFreshness = FreshnessEngine.evaluate(
            evidence = sourceIntegrity.effectiveEvidence,
            timeline = input.timeline,
            now = start.startAt
        )
        val currentEvidenceResult = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = currentFreshness.effectiveEvidence
        )
        val startEvidenceResult = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = startFreshness.effectiveEvidence
        )
        val factors = SignalFactor.values().map { factor ->
            val sourceLevel = sourceIntegrity.effectiveEvidence.level(
                factor
            )
            val currentFactor = currentFreshness.factor(factor)
            val startLevel = startFreshness.effectiveEvidence.level(
                factor
            )
            val state = when {
                sourceLevel == EvidenceLevel.UNCONFIRMED ->
                    EvidenceRelayFactorState.UNCONFIRMED
                startLevel.ordinal < sourceLevel.ordinal ->
                    EvidenceRelayFactorState.RECHECK_REQUIRED
                else -> EvidenceRelayFactorState.SURVIVES
            }
            EvidenceRelayFactor(
                factor = factor,
                sourceLevel = sourceLevel,
                currentLevel = currentFactor.effectiveLevel,
                startLevel = startLevel,
                state = state,
                firstTransitionAt = currentFactor.nextTransitionAt
                    ?.takeIf { it <= start.startAt },
                safeRecheckAt = if (
                    state == EvidenceRelayFactorState.RECHECK_REQUIRED
                ) {
                    safeRecheckAt(
                        factor = factor,
                        startAt = start.startAt,
                        now = now
                    )
                } else {
                    null
                }
            )
        }
        val priorityFactor = factors
            .filter {
                it.state ==
                    EvidenceRelayFactorState.RECHECK_REQUIRED
            }
            .sortedWith(
                compareBy<EvidenceRelayFactor> {
                    (it.safeRecheckAt ?: Long.MAX_VALUE) > now
                }.thenBy {
                    it.safeRecheckAt ?: Long.MAX_VALUE
                }.thenBy {
                    it.firstTransitionAt ?: Long.MAX_VALUE
                }.thenBy {
                    it.factor.ordinal
                }
            )
            .firstOrNull()
            ?.factor
        val currentVerdict =
            currentEvidenceResult.effectiveSignal.verdict
        val startVerdict =
            startEvidenceResult.effectiveSignal.verdict
        val state = when {
            factors.all {
                it.state ==
                    EvidenceRelayFactorState.UNCONFIRMED
            } -> EvidenceRelayState.NO_CONFIRMED_FACTS
            startVerdict.ordinal < currentVerdict.ordinal ->
                EvidenceRelayState.READINESS_DROP
            factors.any {
                it.state ==
                    EvidenceRelayFactorState.RECHECK_REQUIRED
            } -> EvidenceRelayState.RECHECK_REQUIRED
            else -> EvidenceRelayState.INTACT
        }
        val evaluatedAtMinute = now / MINUTE_MILLIS
        val minutesUntilStart = (
            start.startAt - now + MINUTE_MILLIS - 1L
            ) / MINUTE_MILLIS
        val fingerprint = fingerprintFor(
            input = input,
            sourceIntegrity = sourceIntegrity,
            evaluatedAtMinute = evaluatedAtMinute,
            start = start,
            currentEvidenceResult = currentEvidenceResult,
            startEvidenceResult = startEvidenceResult,
            factors = factors,
            state = state
        )
        return EvidenceRelayResult(
            eventId = input.event.id,
            evaluatedAt = now,
            evaluatedAtMinute = evaluatedAtMinute,
            start = start,
            minutesUntilStart = minutesUntilStart,
            currentFreshness = currentFreshness,
            startFreshness = startFreshness,
            currentEvidenceResult = currentEvidenceResult,
            startEvidenceResult = startEvidenceResult,
            factors = factors,
            priorityFactor = priorityFactor,
            state = state,
            fingerprint = fingerprint
        )
    }

    private fun safeRecheckAt(
        factor: SignalFactor,
        startAt: Long,
        now: Long
    ): Long {
        val maximumSafeAge = (
            FreshnessPolicy.validForMillis(factor) -
                MINUTE_MILLIS
            ).coerceAtLeast(0L)
        val windowOpensAt = if (startAt > maximumSafeAge) {
            startAt - maximumSafeAge
        } else {
            0L
        }
        return windowOpensAt.coerceAtLeast(now)
    }

    private fun fingerprintFor(
        input: EvidenceRelayInput,
        sourceIntegrity: SourceIntegrityResult,
        evaluatedAtMinute: Long,
        start: ResolvedEventStart,
        currentEvidenceResult: EvidenceResult,
        startEvidenceResult: EvidenceResult,
        factors: List<EvidenceRelayFactor>,
        state: EvidenceRelayState
    ): String {
        val payload = buildString {
            append(VERSION)
            append("|event=")
            append(input.event.id)
            append("|now=")
            append(evaluatedAtMinute)
            append("|start=")
            append(start.startAt / MINUTE_MILLIS)
            append(':')
            append(start.source.name)
            append("|assessment=")
            append(input.assessment.values.joinToString(","))
            append("|source=")
            append(sourceIntegrity.fingerprint)
            append("|checked=")
            append(
                input.timeline.checkedAt.joinToString(",") {
                    (it / MINUTE_MILLIS).toString()
                }
            )
            append("|readiness=")
            append(currentEvidenceResult.effectiveSignal.readiness)
            append(':')
            append(startEvidenceResult.effectiveSignal.readiness)
            append("|state=")
            append(state.name)
            factors.forEach { factor ->
                append("|factor=")
                append(factor.factor.name)
                append(':')
                append(factor.sourceLevel.name)
                append(':')
                append(factor.currentLevel.name)
                append(':')
                append(factor.startLevel.name)
                append(':')
                append(factor.state.name)
                append(':')
                append(
                    factor.firstTransitionAt?.div(
                        MINUTE_MILLIS
                    ) ?: -1L
                )
                append(':')
                append(
                    factor.safeRecheckAt?.div(
                        MINUTE_MILLIS
                    ) ?: -1L
                )
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(
                payload.toByteArray(StandardCharsets.UTF_8)
            )
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
