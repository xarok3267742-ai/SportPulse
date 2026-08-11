package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.max

internal data class DataDuelInput(
    val eventId: String,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline,
    val counterReview: CounterReviewAssessment
) {
    init {
        require(eventId.isNotBlank())
    }
}

internal data class DataDuelProfile(
    val eventId: String,
    val readiness: Int,
    val clarity: Int,
    val quorumCount: Int,
    val independentCount: Int,
    val counterReviewedCount: Int,
    val freshnessReserveMinutes: Int,
    val decisionCeiling: SavedDecision,
    val defensibleVerdict: SignalVerdict,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(readiness in 0..100)
        require(clarity in 0..100)
        require(quorumCount in 0..SignalFactor.values().size)
        require(independentCount in 0..SignalFactor.values().size)
        require(counterReviewedCount in 0..SignalFactor.values().size)
        require(freshnessReserveMinutes >= 0)
        require(fingerprint.length == 64)
    }
}

internal enum class DataDuelMetricKind(
    val title: String,
    val fixedMaximum: Int?,
    val minimumLead: Int
) {
    READINESS("Полнота", 100, 1),
    CLARITY("Чистота сигнала", 100, 1),
    QUORUMS(
        "Свежий кворум",
        SignalFactor.values().size,
        1
    ),
    INDEPENDENCE(
        "Независимость",
        SignalFactor.values().size,
        1
    ),
    COUNTERCHECKS(
        "Контрпроверка",
        SignalFactor.values().size,
        1
    ),
    FRESHNESS_RESERVE("Запас срока", null, 5)
}

internal enum class DataDuelSide {
    LEFT,
    RIGHT,
    TIE
}

internal data class DataDuelMetric(
    val kind: DataDuelMetricKind,
    val leftValue: Int,
    val rightValue: Int,
    val visualMaximum: Int,
    val leader: DataDuelSide
) {
    init {
        require(leftValue >= 0)
        require(rightValue >= 0)
        require(visualMaximum >= max(leftValue, rightValue).coerceAtLeast(1))
    }
}

internal data class DataDuelResult(
    val left: DataDuelProfile,
    val right: DataDuelProfile,
    val metrics: List<DataDuelMetric>,
    val leftWins: Int,
    val rightWins: Int,
    val ties: Int,
    val balance: DataDuelSide,
    val evaluatedAtMinute: Long,
    val fingerprint: String
) {
    init {
        require(left.eventId != right.eventId)
        require(metrics.size == DataDuelMetricKind.values().size)
        require(leftWins + rightWins + ties == metrics.size)
        require(evaluatedAtMinute >= 0L)
        require(fingerprint.length == 64)
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun metric(kind: DataDuelMetricKind): DataDuelMetric {
        return metrics[kind.ordinal]
    }
}

internal object DataDuelEngine {
    private const val PROFILE_VERSION = "sport-pulse-data-duel-profile-v1"
    private const val RESULT_VERSION = "sport-pulse-data-duel-v1"
    private const val MINUTE_MILLIS = 60_000L
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        left: DataDuelInput,
        right: DataDuelInput,
        now: Long
    ): DataDuelResult {
        require(left.eventId != right.eventId)
        require(now >= 0L)
        val evaluatedAtMinute = now / MINUTE_MILLIS
        val leftProfile = profile(
            input = left,
            now = now,
            evaluatedAtMinute = evaluatedAtMinute
        )
        val rightProfile = profile(
            input = right,
            now = now,
            evaluatedAtMinute = evaluatedAtMinute
        )
        val metrics = DataDuelMetricKind.values().map { kind ->
            metric(
                kind = kind,
                left = leftProfile,
                right = rightProfile
            )
        }
        val leftWins = metrics.count {
            it.leader == DataDuelSide.LEFT
        }
        val rightWins = metrics.count {
            it.leader == DataDuelSide.RIGHT
        }
        val ties = metrics.size - leftWins - rightWins
        val balance = when {
            leftWins > rightWins -> DataDuelSide.LEFT
            rightWins > leftWins -> DataDuelSide.RIGHT
            else -> DataDuelSide.TIE
        }
        val fingerprint = digest(
            buildString {
                append(RESULT_VERSION)
                append('|')
                append(evaluatedAtMinute)
                append("|left:")
                append(leftProfile.fingerprint)
                append("|right:")
                append(rightProfile.fingerprint)
                metrics.forEach { metric ->
                    append('|')
                    append(metric.kind.name)
                    append(':')
                    append(metric.leftValue)
                    append(':')
                    append(metric.rightValue)
                    append(':')
                    append(metric.leader.name)
                }
            }
        )

        return DataDuelResult(
            left = leftProfile,
            right = rightProfile,
            metrics = metrics,
            leftWins = leftWins,
            rightWins = rightWins,
            ties = ties,
            balance = balance,
            evaluatedAtMinute = evaluatedAtMinute,
            fingerprint = fingerprint
        )
    }

    private fun profile(
        input: DataDuelInput,
        now: Long,
        evaluatedAtMinute: Long
    ): DataDuelProfile {
        val integrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = input.claimedEvidence,
            audit = input.sourceAudit
        )
        val freshness = FreshnessEngine.evaluate(
            evidence = integrity.effectiveEvidence,
            timeline = input.timeline,
            now = now
        )
        val evidence = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence
        )
        val counterView = CounterViewEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence,
            review = input.counterReview
        )
        val signal = evidence.effectiveSignal
        val freshnessReserveMinutes = freshness.factors
            .mapNotNull(FactorFreshness::remainingMillis)
            .minOrNull()
            ?.div(MINUTE_MILLIS)
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0
        val independentCount = integrity.factors.count {
            it.auditState == SourceAuditState.INDEPENDENT &&
                it.claimedLevel != EvidenceLevel.UNCONFIRMED
        }
        val counterReviewedCount = input.counterReview.states.count {
            it != CounterReviewState.UNCHECKED
        }
        val fingerprint = digest(
            buildString {
                append(PROFILE_VERSION)
                append('|')
                append(evaluatedAtMinute)
                append('|')
                append(input.eventId)
                append("|assessment:")
                append(input.assessment.values.joinToString(","))
                append("|claimed:")
                append(
                    input.claimedEvidence.levels.joinToString(",") {
                        it.name
                    }
                )
                append("|audit:")
                append(
                    input.sourceAudit.states.joinToString(",") {
                        it.name
                    }
                )
                append("|checked:")
                append(input.timeline.checkedAt.joinToString(","))
                append("|counter:")
                append(
                    input.counterReview.states.joinToString(",") {
                        it.name
                    }
                )
                append("|effective:")
                append(
                    freshness.effectiveEvidence.levels.joinToString(",") {
                        it.name
                    }
                )
                append("|reserve:")
                append(freshnessReserveMinutes)
            }
        )

        return DataDuelProfile(
            eventId = input.eventId,
            readiness = signal.readiness,
            clarity = 100 - signal.noise,
            quorumCount = evidence.quorumCount,
            independentCount = independentCount,
            counterReviewedCount = counterReviewedCount,
            freshnessReserveMinutes = freshnessReserveMinutes,
            decisionCeiling = counterView.decisionCeiling,
            defensibleVerdict = counterView.defensibleVerdict,
            fingerprint = fingerprint
        )
    }

    private fun metric(
        kind: DataDuelMetricKind,
        left: DataDuelProfile,
        right: DataDuelProfile
    ): DataDuelMetric {
        val leftValue = kind.value(left)
        val rightValue = kind.value(right)
        val visualMaximum = kind.fixedMaximum
            ?: max(leftValue, rightValue).coerceAtLeast(1)
        val delta = leftValue - rightValue
        val leader = when {
            delta >= kind.minimumLead -> DataDuelSide.LEFT
            delta <= -kind.minimumLead -> DataDuelSide.RIGHT
            else -> DataDuelSide.TIE
        }
        return DataDuelMetric(
            kind = kind,
            leftValue = leftValue,
            rightValue = rightValue,
            visualMaximum = visualMaximum,
            leader = leader
        )
    }

    private fun DataDuelMetricKind.value(
        profile: DataDuelProfile
    ): Int {
        return when (this) {
            DataDuelMetricKind.READINESS ->
                profile.readiness
            DataDuelMetricKind.CLARITY ->
                profile.clarity
            DataDuelMetricKind.QUORUMS ->
                profile.quorumCount
            DataDuelMetricKind.INDEPENDENCE ->
                profile.independentCount
            DataDuelMetricKind.COUNTERCHECKS ->
                profile.counterReviewedCount
            DataDuelMetricKind.FRESHNESS_RESERVE ->
                profile.freshnessReserveMinutes
        }
    }

    private fun digest(payload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
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
