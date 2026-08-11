package ru.sportpulse.info

internal enum class ConfidenceShadowStatus {
    CLEAR,
    CONTAINED,
    VERDICT_SHIFT
}

internal data class ConfidenceShadowFactor(
    val factor: SignalFactor,
    val claimedScore: Int,
    val supportedScore: Int,
    val unsupportedPoints: Int,
    val readinessImpact: Int,
    val restoredVerdict: SignalVerdict
)

internal data class ConfidenceShadowResult(
    val claimedAssessment: SignalAssessment,
    val supportedAssessment: SignalAssessment,
    val claimedSignal: SignalResult,
    val supportedSignal: SignalResult,
    val readinessGap: Int,
    val shadowedFactors: List<ConfidenceShadowFactor>,
    val criticalFactor: ConfidenceShadowFactor?,
    val status: ConfidenceShadowStatus
)

internal object ConfidenceShadowEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): ConfidenceShadowResult {
        val evidenceResult = EvidenceEngine.evaluate(assessment, evidence)
        val supportedAssessment = evidenceResult.effectiveAssessment
        val supportedSignal = evidenceResult.effectiveSignal
        val shadowedFactors = SignalFactor.values().mapNotNull { factor ->
            val claimedScore = assessment.value(factor)
            val supportedScore = supportedAssessment.value(factor)
            val unsupportedPoints = claimedScore - supportedScore
            if (unsupportedPoints <= 0) {
                null
            } else {
                val restoredSignal = SignalEngine.evaluate(
                    supportedAssessment.withValue(factor, claimedScore)
                )
                ConfidenceShadowFactor(
                    factor = factor,
                    claimedScore = claimedScore,
                    supportedScore = supportedScore,
                    unsupportedPoints = unsupportedPoints,
                    readinessImpact = (
                        restoredSignal.readiness - supportedSignal.readiness
                    ).coerceAtLeast(0),
                    restoredVerdict = restoredSignal.verdict
                )
            }
        }
        val criticalFactor = shadowedFactors.maxWithOrNull(
            compareBy<ConfidenceShadowFactor> { it.readinessImpact }
                .thenBy { it.unsupportedPoints }
                .thenBy { -it.factor.ordinal }
        )
        val status = when {
            shadowedFactors.isEmpty() ->
                ConfidenceShadowStatus.CLEAR
            evidenceResult.rawSignal.verdict != supportedSignal.verdict ->
                ConfidenceShadowStatus.VERDICT_SHIFT
            else ->
                ConfidenceShadowStatus.CONTAINED
        }

        return ConfidenceShadowResult(
            claimedAssessment = assessment,
            supportedAssessment = supportedAssessment,
            claimedSignal = evidenceResult.rawSignal,
            supportedSignal = supportedSignal,
            readinessGap = evidenceResult.readinessLoss,
            shadowedFactors = shadowedFactors,
            criticalFactor = criticalFactor,
            status = status
        )
    }
}
