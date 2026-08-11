package ru.sportpulse.info

internal enum class VerificationRouteStatus {
    REACHABLE,
    FACTS_LIMIT,
    READY_MAINTAIN
}

internal data class VerificationStep(
    val factor: SignalFactor,
    val currentLevel: EvidenceLevel,
    val readinessGain: Int,
    val scoreHeadroom: Int
)

internal data class VerificationRoute(
    val status: VerificationRouteStatus,
    val baselineResult: EvidenceResult,
    val targetVerdict: SignalVerdict?,
    val targetReadiness: Int?,
    val allQuorumResult: EvidenceResult,
    val projectedResult: EvidenceResult,
    val steps: List<VerificationStep>,
    val bestCheck: VerificationStep?,
    val remainingGap: Int
)

internal object VerificationRouteEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): VerificationRoute {
        val baseline = EvidenceEngine.evaluate(assessment, evidence)
        val allQuorumEvidence = EvidenceAssessment(
            List(SignalFactor.values().size) { EvidenceLevel.QUORUM }
        )
        val allQuorum = EvidenceEngine.evaluate(assessment, allQuorumEvidence)
        val target = nextTarget(baseline.effectiveSignal.verdict)

        if (target == null) {
            return VerificationRoute(
                status = VerificationRouteStatus.READY_MAINTAIN,
                baselineResult = baseline,
                targetVerdict = null,
                targetReadiness = null,
                allQuorumResult = allQuorum,
                projectedResult = baseline,
                steps = emptyList(),
                bestCheck = null,
                remainingGap = 0
            )
        }

        val candidates = SignalFactor.values().filter { factor ->
            evidence.level(factor) != EvidenceLevel.QUORUM
        }
        val singleSteps = candidates.associateWith { factor ->
            val upgraded = evidence.withLevel(factor, EvidenceLevel.QUORUM)
            val result = EvidenceEngine.evaluate(assessment, upgraded)
            VerificationStep(
                factor = factor,
                currentLevel = evidence.level(factor),
                readinessGain = (
                    result.effectiveSignal.readiness -
                        baseline.effectiveSignal.readiness
                    ).coerceAtLeast(0),
                scoreHeadroom = (
                    assessment.value(factor) -
                        baseline.effectiveAssessment.value(factor)
                    ).coerceAtLeast(0)
            )
        }

        var bestCandidate: RouteCandidate? = null
        val combinations = 1 shl candidates.size
        for (mask in 1 until combinations) {
            val factors = candidates.filterIndexed { index, _ ->
                mask and (1 shl index) != 0
            }
            var upgradedEvidence = evidence
            factors.forEach { factor ->
                upgradedEvidence = upgradedEvidence.withLevel(
                    factor,
                    EvidenceLevel.QUORUM
                )
            }
            val result = EvidenceEngine.evaluate(assessment, upgradedEvidence)
            if (result.effectiveSignal.readiness < target.readiness) continue

            val candidate = RouteCandidate(
                factors = factors,
                levelUpgradeCost = factors.sumOf { factor ->
                    EvidenceLevel.QUORUM.ordinal -
                        evidence.level(factor).ordinal
                },
                result = result
            )
            if (
                bestCandidate == null ||
                candidate.isBetterThan(bestCandidate)
            ) {
                bestCandidate = candidate
            }
        }

        if (bestCandidate == null) {
            val bestCheck = singleSteps.values
                .sortedWith(
                    compareByDescending<VerificationStep> {
                        it.readinessGain
                    }.thenByDescending {
                        it.scoreHeadroom
                    }.thenBy {
                        it.factor.ordinal
                    }
                )
                .firstOrNull { it.readinessGain > 0 }
            return VerificationRoute(
                status = VerificationRouteStatus.FACTS_LIMIT,
                baselineResult = baseline,
                targetVerdict = target.verdict,
                targetReadiness = target.readiness,
                allQuorumResult = allQuorum,
                projectedResult = allQuorum,
                steps = emptyList(),
                bestCheck = bestCheck,
                remainingGap = (
                    target.readiness -
                        allQuorum.effectiveSignal.readiness
                    ).coerceAtLeast(0)
            )
        }

        val steps = bestCandidate.factors
            .map { factor -> singleSteps.getValue(factor) }
            .sortedWith(
                compareByDescending<VerificationStep> {
                    it.readinessGain
                }.thenBy {
                    baseline.effectiveAssessment.value(it.factor)
                }.thenBy {
                    it.factor.ordinal
                }
            )
        return VerificationRoute(
            status = VerificationRouteStatus.REACHABLE,
            baselineResult = baseline,
            targetVerdict = target.verdict,
            targetReadiness = target.readiness,
            allQuorumResult = allQuorum,
            projectedResult = bestCandidate.result,
            steps = steps,
            bestCheck = steps.firstOrNull(),
            remainingGap = 0
        )
    }

    private fun nextTarget(verdict: SignalVerdict): RouteTarget? {
        return when (verdict) {
            SignalVerdict.SKIP -> RouteTarget(
                verdict = SignalVerdict.OBSERVE,
                readiness = SignalThresholds.OBSERVE
            )
            SignalVerdict.OBSERVE -> RouteTarget(
                verdict = SignalVerdict.READY,
                readiness = SignalThresholds.READY
            )
            SignalVerdict.READY -> null
        }
    }

    private data class RouteTarget(
        val verdict: SignalVerdict,
        val readiness: Int
    )

    private data class RouteCandidate(
        val factors: List<SignalFactor>,
        val levelUpgradeCost: Int,
        val result: EvidenceResult
    ) {
        fun isBetterThan(other: RouteCandidate): Boolean {
            if (factors.size != other.factors.size) {
                return factors.size < other.factors.size
            }
            if (levelUpgradeCost != other.levelUpgradeCost) {
                return levelUpgradeCost < other.levelUpgradeCost
            }
            val readiness = result.effectiveSignal.readiness
            val otherReadiness = other.result.effectiveSignal.readiness
            if (readiness != otherReadiness) {
                return readiness > otherReadiness
            }
            return factorMask() < other.factorMask()
        }

        private fun factorMask(): Int {
            return factors.fold(0) { mask, factor ->
                mask or (1 shl factor.ordinal)
            }
        }
    }
}
