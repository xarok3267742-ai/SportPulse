package ru.sportpulse.info

internal enum class EvidenceLevel(
    val title: String,
    val scoreCap: Int
) {
    UNCONFIRMED(
        title = "Не подтверждено",
        scoreCap = 25
    ),
    SINGLE_SOURCE(
        title = "1 источник",
        scoreCap = 60
    ),
    QUORUM(
        title = "Кворум 2+",
        scoreCap = 100
    )
}

internal data class EvidenceAssessment(
    val levels: List<EvidenceLevel>
) {
    init {
        require(levels.size == SignalFactor.values().size)
    }

    fun level(factor: SignalFactor): EvidenceLevel = levels[factor.ordinal]

    fun withLevel(
        factor: SignalFactor,
        level: EvidenceLevel
    ): EvidenceAssessment {
        val updated = levels.toMutableList()
        updated[factor.ordinal] = level
        return copy(levels = updated)
    }

    companion object {
        fun singleSource(): EvidenceAssessment {
            return EvidenceAssessment(
                List(SignalFactor.values().size) {
                    EvidenceLevel.SINGLE_SOURCE
                }
            )
        }
    }
}

internal data class EvidenceResult(
    val rawAssessment: SignalAssessment,
    val effectiveAssessment: SignalAssessment,
    val rawSignal: SignalResult,
    val effectiveSignal: SignalResult,
    val cappedFactors: List<SignalFactor>,
    val quorumCount: Int,
    val readinessLoss: Int
)

internal object EvidenceEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): EvidenceResult {
        val effectiveValues = SignalFactor.values().map { factor ->
            assessment.value(factor).coerceAtMost(
                evidence.level(factor).scoreCap
            )
        }
        val effectiveAssessment = SignalAssessment(effectiveValues)
        val rawSignal = SignalEngine.evaluate(assessment)
        val effectiveSignal = SignalEngine.evaluate(effectiveAssessment)

        return EvidenceResult(
            rawAssessment = assessment,
            effectiveAssessment = effectiveAssessment,
            rawSignal = rawSignal,
            effectiveSignal = effectiveSignal,
            cappedFactors = SignalFactor.values().filter { factor ->
                effectiveAssessment.value(factor) < assessment.value(factor)
            },
            quorumCount = evidence.levels.count { it == EvidenceLevel.QUORUM },
            readinessLoss = (
                rawSignal.readiness - effectiveSignal.readiness
            ).coerceAtLeast(0)
        )
    }
}
