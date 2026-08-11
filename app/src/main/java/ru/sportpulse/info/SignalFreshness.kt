package ru.sportpulse.info

import kotlin.math.max

internal data class EvidenceTimeline(
    val checkedAt: List<Long>
) {
    init {
        require(checkedAt.size == SignalFactor.values().size)
        require(checkedAt.all { it >= 0L })
    }

    fun checkedAt(factor: SignalFactor): Long = checkedAt[factor.ordinal]

    fun withCheckedAt(
        factor: SignalFactor,
        timestamp: Long
    ): EvidenceTimeline {
        require(timestamp >= 0L)
        val updated = checkedAt.toMutableList()
        updated[factor.ordinal] = timestamp
        return copy(checkedAt = updated)
    }
}

internal enum class FreshnessStatus {
    FRESH,
    EXPIRING,
    DEGRADED,
    EXPIRED,
    UNCONFIRMED
}

internal data class FactorFreshness(
    val factor: SignalFactor,
    val claimedLevel: EvidenceLevel,
    val effectiveLevel: EvidenceLevel,
    val checkedAt: Long,
    val validForMillis: Long,
    val nextTransitionAt: Long?,
    val remainingMillis: Long?,
    val status: FreshnessStatus
)

internal data class FreshnessResult(
    val effectiveEvidence: EvidenceAssessment,
    val factors: List<FactorFreshness>,
    val degradedFactors: List<SignalFactor>,
    val expiringFactors: List<SignalFactor>,
    val expiredFactors: List<SignalFactor>,
    val nextTransitionAt: Long?,
    val nextTransitionFactor: SignalFactor?
) {
    fun factor(factor: SignalFactor): FactorFreshness {
        return factors[factor.ordinal]
    }
}

internal object FreshnessPolicy {
    const val HOUR_MILLIS = 60L * 60L * 1000L

    fun validForMillis(factor: SignalFactor): Long {
        return when (factor) {
            SignalFactor.FORM -> 72L * HOUR_MILLIS
            SignalFactor.LINEUP -> 6L * HOUR_MILLIS
            SignalFactor.LOAD -> 24L * HOUR_MILLIS
            SignalFactor.CONTEXT -> 48L * HOUR_MILLIS
            SignalFactor.SOURCES -> 12L * HOUR_MILLIS
        }
    }
}

internal object FreshnessEngine {
    fun evaluate(
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long
    ): FreshnessResult {
        require(now >= 0L)
        val factors = SignalFactor.values().map { factor ->
            evaluateFactor(
                factor = factor,
                claimedLevel = evidence.level(factor),
                checkedAt = timeline.checkedAt(factor),
                now = now
            )
        }
        val next = factors
            .filter { it.nextTransitionAt != null }
            .minByOrNull { it.nextTransitionAt ?: Long.MAX_VALUE }

        return FreshnessResult(
            effectiveEvidence = EvidenceAssessment(
                factors.map(FactorFreshness::effectiveLevel)
            ),
            factors = factors,
            degradedFactors = factors
                .filter { it.effectiveLevel != it.claimedLevel }
                .map(FactorFreshness::factor),
            expiringFactors = factors
                .filter { it.status == FreshnessStatus.EXPIRING }
                .map(FactorFreshness::factor),
            expiredFactors = factors
                .filter { it.status == FreshnessStatus.EXPIRED }
                .map(FactorFreshness::factor),
            nextTransitionAt = next?.nextTransitionAt,
            nextTransitionFactor = next?.factor
        )
    }

    fun evaluateFactor(
        factor: SignalFactor,
        claimedLevel: EvidenceLevel,
        checkedAt: Long,
        now: Long
    ): FactorFreshness {
        require(checkedAt >= 0L)
        require(now >= 0L)
        val validFor = FreshnessPolicy.validForMillis(factor)
        val age = (now - checkedAt).coerceAtLeast(0L)

        if (claimedLevel == EvidenceLevel.UNCONFIRMED) {
            return FactorFreshness(
                factor = factor,
                claimedLevel = claimedLevel,
                effectiveLevel = claimedLevel,
                checkedAt = checkedAt,
                validForMillis = validFor,
                nextTransitionAt = null,
                remainingMillis = null,
                status = FreshnessStatus.UNCONFIRMED
            )
        }

        val stages = when (claimedLevel) {
            EvidenceLevel.QUORUM -> 2
            EvidenceLevel.SINGLE_SOURCE -> 1
            EvidenceLevel.UNCONFIRMED -> 0
        }
        val elapsedStages = (age / validFor).coerceAtMost(stages.toLong()).toInt()
        val effectiveLevel = when (claimedLevel) {
            EvidenceLevel.QUORUM -> when (elapsedStages) {
                0 -> EvidenceLevel.QUORUM
                1 -> EvidenceLevel.SINGLE_SOURCE
                else -> EvidenceLevel.UNCONFIRMED
            }
            EvidenceLevel.SINGLE_SOURCE -> if (elapsedStages == 0) {
                EvidenceLevel.SINGLE_SOURCE
            } else {
                EvidenceLevel.UNCONFIRMED
            }
            EvidenceLevel.UNCONFIRMED -> EvidenceLevel.UNCONFIRMED
        }
        val hasNextTransition = elapsedStages < stages
        val nextTransitionAt = if (hasNextTransition) {
            checkedAt + validFor * (elapsedStages + 1L)
        } else {
            null
        }
        val remaining = nextTransitionAt?.let { max(0L, it - now) }
        val status = when {
            effectiveLevel == EvidenceLevel.UNCONFIRMED ->
                FreshnessStatus.EXPIRED
            effectiveLevel != claimedLevel ->
                FreshnessStatus.DEGRADED
            remaining != null && remaining <= validFor / 4L ->
                FreshnessStatus.EXPIRING
            else ->
                FreshnessStatus.FRESH
        }

        return FactorFreshness(
            factor = factor,
            claimedLevel = claimedLevel,
            effectiveLevel = effectiveLevel,
            checkedAt = checkedAt,
            validForMillis = validFor,
            nextTransitionAt = nextTransitionAt,
            remainingMillis = remaining,
            status = status
        )
    }
}

internal object FreshnessFormatter {
    fun duration(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalMinutes = (safeMillis / 60_000L).coerceAtLeast(1L)
        val days = totalMinutes / (24L * 60L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L && hours % 24L == 0L -> "${days} д"
            days > 0L -> "${days} д ${hours % 24L} ч"
            hours > 0L && minutes == 0L -> "${hours} ч"
            hours > 0L -> "${hours} ч ${minutes} мин"
            else -> "${minutes} мин"
        }
    }
}
