package ru.sportpulse.info

internal enum class MarketLeverageMode {
    IMPROVE,
    MAINTAIN
}

internal data class MarketLeverageImpact(
    val kind: MarketKind,
    val currentStatus: MarketLensStatus,
    val projectedStatus: MarketLensStatus,
    val conditionGain: Int
) {
    init {
        require(
            currentStatus !=
                MarketLensStatus.NOT_APPLICABLE
        )
        require(
            projectedStatus !=
                MarketLensStatus.NOT_APPLICABLE
        )
        require(conditionGain >= 0)
    }

    val statusGain: Int
        get() = (
            statusRank(projectedStatus) -
                statusRank(currentStatus)
            ).coerceAtLeast(0)

    val statusChanged: Boolean
        get() = currentStatus != projectedStatus

    private fun statusRank(
        status: MarketLensStatus
    ): Int {
        return when (status) {
            MarketLensStatus.CLOSED -> 0
            MarketLensStatus.CHECK -> 1
            MarketLensStatus.COVERED -> 2
            MarketLensStatus.NOT_APPLICABLE -> -1
        }
    }
}

internal data class MarketLeverageResult(
    val mode: MarketLeverageMode,
    val factor: SignalFactor,
    val baseline: MarketLensResult,
    val projected: MarketLensResult,
    val currentRawValue: Int,
    val targetValue: Int,
    val requiresNewData: Boolean,
    val requiresFreshQuorum: Boolean,
    val impacts: List<MarketLeverageImpact>,
    val criticalMarketCount: Int,
    val nextTransitionAt: Long?
) {
    init {
        require(currentRawValue in 0..100)
        require(targetValue in 0..100)
        require(targetValue >= currentRawValue)
        require(criticalMarketCount >= 0)
        require(
            impacts.map(MarketLeverageImpact::kind)
                .distinct()
                .size == impacts.size
        )
        if (mode == MarketLeverageMode.IMPROVE) {
            require(impacts.isNotEmpty())
            require(
                impacts.any {
                    it.statusGain > 0 ||
                        it.conditionGain > 0
                }
            )
            require(nextTransitionAt == null)
        } else {
            require(!requiresNewData)
            require(!requiresFreshQuorum)
            require(nextTransitionAt != null)
        }
    }

    val affectedMarketCount: Int
        get() = impacts.size

    val conditionGain: Int
        get() = impacts.sumOf(
            MarketLeverageImpact::conditionGain
        )

    val statusGain: Int
        get() = impacts.sumOf(
            MarketLeverageImpact::statusGain
        )

    val statusTransitionCount: Int
        get() = impacts.count(
            MarketLeverageImpact::statusChanged
        )

    val reopenedCount: Int
        get() = impacts.count {
            it.currentStatus ==
                MarketLensStatus.CLOSED &&
                it.projectedStatus !=
                MarketLensStatus.CLOSED
        }

    val coveredCount: Int
        get() = impacts.count {
            it.currentStatus !=
                MarketLensStatus.COVERED &&
                it.projectedStatus ==
                MarketLensStatus.COVERED
        }
}

internal object MarketLeverageEngine {
    fun evaluate(
        sport: String,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long,
        guides: List<MarketGuide> = DemoCatalog.markets
    ): MarketLeverageResult {
        require(now >= 0L)
        val baseline = MarketLensEngine.evaluate(
            sport = sport,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = now,
            guides = guides
        )
        val candidates = SignalFactor.values()
            .mapNotNull { factor ->
                improvementCandidate(
                    factor = factor,
                    sport = sport,
                    assessment = assessment,
                    evidence = evidence,
                    timeline = timeline,
                    now = now,
                    guides = guides,
                    baseline = baseline
                )
            }
        val best = candidates.maxWithOrNull(
            compareBy<MarketLeverageResult> {
                it.statusGain
            }.thenBy {
                it.statusTransitionCount
            }.thenBy {
                it.conditionGain
            }.thenBy {
                it.affectedMarketCount
            }.thenBy {
                it.criticalMarketCount
            }.thenBy {
                -it.factor.ordinal
            }
        )
        return best ?: maintenance(
            baseline = baseline,
            assessment = assessment
        )
    }

    private fun improvementCandidate(
        factor: SignalFactor,
        sport: String,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long,
        guides: List<MarketGuide>,
        baseline: MarketLensResult
    ): MarketLeverageResult? {
        val targetValue = assessment.value(factor)
        val projected = MarketLensEngine.evaluate(
            sport = sport,
            assessment = assessment,
            evidence = evidence.withLevel(
                factor,
                EvidenceLevel.QUORUM
            ),
            timeline = timeline.withCheckedAt(
                factor,
                now
            ),
            now = now,
            guides = guides
        )
        val impacts = baseline.items.mapNotNull {
                current ->
            if (
                current.status ==
                MarketLensStatus.NOT_APPLICABLE
            ) {
                return@mapNotNull null
            }
            val future = requireNotNull(
                projected.item(current.guide.kind)
            )
            val conditionGain = (
                future.metConditions -
                    current.metConditions
                ).coerceAtLeast(0)
            val impact = MarketLeverageImpact(
                kind = current.guide.kind,
                currentStatus = current.status,
                projectedStatus = future.status,
                conditionGain = conditionGain
            )
            impact.takeIf {
                it.statusGain > 0 ||
                    it.conditionGain > 0
            }
        }
        if (impacts.isEmpty()) return null

        val freshness = baseline.freshness.factor(factor)
        return MarketLeverageResult(
            mode = MarketLeverageMode.IMPROVE,
            factor = factor,
            baseline = baseline,
            projected = projected,
            currentRawValue = assessment.value(factor),
            targetValue = targetValue,
            requiresNewData =
                freshness.effectiveLevel ==
                    EvidenceLevel.UNCONFIRMED,
            requiresFreshQuorum =
                freshness.effectiveLevel !=
                    EvidenceLevel.QUORUM ||
                    freshness.status ==
                    FreshnessStatus.EXPIRING,
            impacts = impacts,
            criticalMarketCount =
                criticalMarketCount(
                    baseline,
                    factor
                ),
            nextTransitionAt = null
        )
    }

    private fun maintenance(
        baseline: MarketLensResult,
        assessment: SignalAssessment
    ): MarketLeverageResult {
        val applicable = baseline.items.filter {
            it.status !=
                MarketLensStatus.NOT_APPLICABLE
        }
        val factor = SignalFactor.values()
            .filter { candidate ->
                applicable.any {
                    candidate in
                        it.definition.criticalFactors
                }
            }
            .minWithOrNull(
                compareBy<SignalFactor> {
                    baseline.freshness.factor(it)
                        .nextTransitionAt
                        ?: Long.MAX_VALUE
                }.thenByDescending {
                    criticalMarketCount(
                        baseline,
                        it
                    )
                }.thenBy(SignalFactor::ordinal)
            )
            ?: error(
                "At least one applicable market must " +
                    "have a critical factor"
            )
        val nextTransitionAt = requireNotNull(
            baseline.freshness.factor(factor)
                .nextTransitionAt
        )
        val impacts = applicable
            .filter {
                factor in
                    it.definition.criticalFactors
            }
            .map {
                MarketLeverageImpact(
                    kind = it.guide.kind,
                    currentStatus = it.status,
                    projectedStatus = it.status,
                    conditionGain = 0
                )
            }
        return MarketLeverageResult(
            mode = MarketLeverageMode.MAINTAIN,
            factor = factor,
            baseline = baseline,
            projected = baseline,
            currentRawValue = assessment.value(factor),
            targetValue = assessment.value(factor),
            requiresNewData = false,
            requiresFreshQuorum = false,
            impacts = impacts,
            criticalMarketCount = impacts.size,
            nextTransitionAt = nextTransitionAt
        )
    }

    private fun criticalMarketCount(
        lens: MarketLensResult,
        factor: SignalFactor
    ): Int {
        return lens.items.count {
            it.status !=
                MarketLensStatus.NOT_APPLICABLE &&
                factor in
                it.definition.criticalFactors
        }
    }
}
