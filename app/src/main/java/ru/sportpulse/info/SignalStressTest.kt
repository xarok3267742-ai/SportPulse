package ru.sportpulse.info

internal enum class SignalStressStatus {
    ROBUST,
    FRAGILE,
    NO_BUFFER
}

internal data class EvidenceShock(
    val factor: SignalFactor,
    val fromLevel: EvidenceLevel,
    val toLevel: EvidenceLevel,
    val result: EvidenceResult,
    val readinessDrop: Int,
    val verdictChanged: Boolean
)

internal data class SignalStressPoint(
    val at: Long,
    val result: EvidenceResult,
    val changedFactors: List<SignalFactor>
)

internal data class SignalStressResult(
    val status: SignalStressStatus,
    val baselineFreshness: FreshnessResult,
    val baselineResult: EvidenceResult,
    val shocks: List<EvidenceShock>,
    val criticalShock: EvidenceShock?,
    val timeline: List<SignalStressPoint>,
    val firstVerdictChange: SignalStressPoint?
) {
    val nextTransition: SignalStressPoint?
        get() = timeline.firstOrNull()
}

internal object SignalStressEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long
    ): SignalStressResult {
        require(now >= 0L)
        val baselineFreshness = FreshnessEngine.evaluate(
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val effectiveEvidence = baselineFreshness.effectiveEvidence
        val baselineResult = EvidenceEngine.evaluate(
            assessment = assessment,
            evidence = effectiveEvidence
        )
        val shocks = SignalFactor.values().mapNotNull { factor ->
            val fromLevel = effectiveEvidence.level(factor)
            val toLevel = downgrade(fromLevel) ?: return@mapNotNull null
            val result = EvidenceEngine.evaluate(
                assessment = assessment,
                evidence = effectiveEvidence.withLevel(factor, toLevel)
            )
            EvidenceShock(
                factor = factor,
                fromLevel = fromLevel,
                toLevel = toLevel,
                result = result,
                readinessDrop = (
                    baselineResult.effectiveSignal.readiness -
                        result.effectiveSignal.readiness
                    ).coerceAtLeast(0),
                verdictChanged = (
                    result.effectiveSignal.verdict !=
                        baselineResult.effectiveSignal.verdict
                    )
            )
        }
        val criticalShock = shocks.sortedWith(
            compareByDescending<EvidenceShock> {
                it.verdictChanged
            }.thenByDescending {
                it.readinessDrop
            }.thenBy {
                it.result.effectiveSignal.readiness
            }.thenBy {
                it.factor.ordinal
            }
        ).firstOrNull()
        val stressTimeline = futureTimeline(
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val baselineVerdict = baselineResult.effectiveSignal.verdict
        val firstVerdictChange = stressTimeline.firstOrNull {
            it.result.effectiveSignal.verdict != baselineVerdict
        }
        val status = when {
            shocks.isEmpty() -> SignalStressStatus.NO_BUFFER
            shocks.any(EvidenceShock::verdictChanged) ->
                SignalStressStatus.FRAGILE
            else -> SignalStressStatus.ROBUST
        }

        return SignalStressResult(
            status = status,
            baselineFreshness = baselineFreshness,
            baselineResult = baselineResult,
            shocks = shocks,
            criticalShock = criticalShock,
            timeline = stressTimeline,
            firstVerdictChange = firstVerdictChange
        )
    }

    private fun futureTimeline(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long
    ): List<SignalStressPoint> {
        val transitions = sortedMapOf<Long, MutableList<SignalFactor>>()
        SignalFactor.values().forEach { factor ->
            val stages = transitionCount(evidence.level(factor))
            val validFor = FreshnessPolicy.validForMillis(factor)
            for (stage in 1..stages) {
                val transitionAt = timeline.checkedAt(factor) +
                    validFor * stage
                if (transitionAt > now) {
                    transitions.getOrPut(transitionAt) { mutableListOf() }
                        .add(factor)
                }
            }
        }

        return transitions.map { (transitionAt, factors) ->
            val freshness = FreshnessEngine.evaluate(
                evidence = evidence,
                timeline = timeline,
                now = transitionAt
            )
            SignalStressPoint(
                at = transitionAt,
                result = EvidenceEngine.evaluate(
                    assessment = assessment,
                    evidence = freshness.effectiveEvidence
                ),
                changedFactors = factors.sortedBy(SignalFactor::ordinal)
            )
        }
    }

    private fun transitionCount(level: EvidenceLevel): Int {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> 0
            EvidenceLevel.SINGLE_SOURCE -> 1
            EvidenceLevel.QUORUM -> 2
        }
    }

    private fun downgrade(level: EvidenceLevel): EvidenceLevel? {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> null
            EvidenceLevel.SINGLE_SOURCE -> EvidenceLevel.UNCONFIRMED
            EvidenceLevel.QUORUM -> EvidenceLevel.SINGLE_SOURCE
        }
    }
}
