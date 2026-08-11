package ru.sportpulse.info

internal enum class CorridorDirection {
    DOWN,
    UP
}

internal data class DecisionBoundary(
    val direction: CorridorDirection,
    val factor: SignalFactor,
    val claimedBefore: Int,
    val claimedAfter: Int,
    val supportedBefore: Int,
    val supportedAfter: Int,
    val claimedChange: Int,
    val result: EvidenceResult
)

internal data class DecisionCorridor(
    val baseline: EvidenceResult,
    val lowerTarget: SignalVerdict?,
    val upperTarget: SignalVerdict?,
    val lowerBoundary: DecisionBoundary?,
    val upperBoundary: DecisionBoundary?,
    val nearestBoundary: DecisionBoundary?
)

internal object DecisionCorridorEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): DecisionCorridor {
        val baseline = EvidenceEngine.evaluate(assessment, evidence)
        val baselineVerdict = baseline.effectiveSignal.verdict
        val lowerTarget = previousVerdict(baselineVerdict)
        val upperTarget = nextVerdict(baselineVerdict)
        val lowerBoundary = lowerTarget?.let {
            findBoundary(
                assessment = assessment,
                evidence = evidence,
                baseline = baseline,
                direction = CorridorDirection.DOWN
            )
        }
        val upperBoundary = upperTarget?.let {
            findBoundary(
                assessment = assessment,
                evidence = evidence,
                baseline = baseline,
                direction = CorridorDirection.UP
            )
        }
        val nearest = listOfNotNull(
            lowerBoundary,
            upperBoundary
        ).minWithOrNull(
            compareBy<DecisionBoundary> { it.claimedChange }
                .thenBy { it.direction.ordinal }
                .thenBy { it.factor.ordinal }
        )

        return DecisionCorridor(
            baseline = baseline,
            lowerTarget = lowerTarget,
            upperTarget = upperTarget,
            lowerBoundary = lowerBoundary,
            upperBoundary = upperBoundary,
            nearestBoundary = nearest
        )
    }

    private fun findBoundary(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        baseline: EvidenceResult,
        direction: CorridorDirection
    ): DecisionBoundary? {
        val candidates = SignalFactor.values().mapNotNull { factor ->
            findFactorBoundary(
                assessment = assessment,
                evidence = evidence,
                baseline = baseline,
                factor = factor,
                direction = direction
            )
        }
        return candidates.minWithOrNull(
            compareBy<DecisionBoundary> { it.claimedChange }
                .thenBy { it.factor.ordinal }
        )
    }

    private fun findFactorBoundary(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        baseline: EvidenceResult,
        factor: SignalFactor,
        direction: CorridorDirection
    ): DecisionBoundary? {
        val claimedBefore = assessment.value(factor)
        val values = when (direction) {
            CorridorDirection.DOWN ->
                (claimedBefore - 1 downTo 0)
            CorridorDirection.UP ->
                (claimedBefore + 1..100)
        }
        for (claimedAfter in values) {
            val result = EvidenceEngine.evaluate(
                assessment.withValue(factor, claimedAfter),
                evidence
            )
            val verdictChanged = when (direction) {
                CorridorDirection.DOWN ->
                    result.effectiveSignal.verdict.ordinal <
                        baseline.effectiveSignal.verdict.ordinal
                CorridorDirection.UP ->
                    result.effectiveSignal.verdict.ordinal >
                        baseline.effectiveSignal.verdict.ordinal
            }
            if (!verdictChanged) continue

            return DecisionBoundary(
                direction = direction,
                factor = factor,
                claimedBefore = claimedBefore,
                claimedAfter = claimedAfter,
                supportedBefore = baseline.effectiveAssessment.value(factor),
                supportedAfter = result.effectiveAssessment.value(factor),
                claimedChange = kotlin.math.abs(
                    claimedAfter - claimedBefore
                ),
                result = result
            )
        }
        return null
    }

    private fun previousVerdict(
        verdict: SignalVerdict
    ): SignalVerdict? {
        return when (verdict) {
            SignalVerdict.SKIP -> null
            SignalVerdict.OBSERVE -> SignalVerdict.SKIP
            SignalVerdict.READY -> SignalVerdict.OBSERVE
        }
    }

    private fun nextVerdict(
        verdict: SignalVerdict
    ): SignalVerdict? {
        return when (verdict) {
            SignalVerdict.SKIP -> SignalVerdict.OBSERVE
            SignalVerdict.OBSERVE -> SignalVerdict.READY
            SignalVerdict.READY -> null
        }
    }
}
