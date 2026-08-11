package ru.sportpulse.info

import java.util.Locale

internal enum class MarketKind(
    val shortTitle: String
) {
    ONE_X_TWO("1X2"),
    HANDICAP("Фора"),
    TOTAL("Тотал"),
    BOTH_SCORE("Обе"),
    INDIVIDUAL_TOTAL("Инд."),
    PERIOD("Период")
}

internal enum class SportFamily {
    FOOTBALL,
    HOCKEY,
    BASKETBALL,
    TENNIS,
    COMBAT,
    ESPORTS,
    OTHER
}

internal enum class MarketLensStatus {
    NOT_APPLICABLE,
    CLOSED,
    CHECK,
    COVERED
}

internal enum class MarketFactorState {
    UNUSED,
    BLOCKED,
    PARTIAL,
    COVERED
}

internal enum class MarketNextCheckReason {
    BLOCKER,
    QUORUM,
    EVIDENCE_GAP,
    FRESHNESS,
    MAINTENANCE
}

internal data class MarketLensDefinition(
    val kind: MarketKind,
    val requiredFactors: List<SignalFactor>,
    val criticalFactors: Set<SignalFactor>,
    val applicableSports: Set<SportFamily>
) {
    init {
        require(requiredFactors.isNotEmpty())
        require(requiredFactors.distinct().size == requiredFactors.size)
        require(criticalFactors.isNotEmpty())
        require(requiredFactors.containsAll(criticalFactors))
        require(applicableSports.isNotEmpty())
    }
}

internal data class MarketFactorCoverage(
    val factor: SignalFactor,
    val required: Boolean,
    val critical: Boolean,
    val effectiveValue: Int,
    val evidence: EvidenceLevel,
    val freshness: FactorFreshness,
    val state: MarketFactorState
)

internal data class MarketNextCheck(
    val factor: SignalFactor,
    val reason: MarketNextCheckReason
)

internal data class MarketLensItem(
    val guide: MarketGuide,
    val definition: MarketLensDefinition,
    val status: MarketLensStatus,
    val factors: List<MarketFactorCoverage>,
    val blockingFactors: List<SignalFactor>,
    val metConditions: Int,
    val conditionCount: Int,
    val nextCheck: MarketNextCheck?
) {
    fun factor(
        factor: SignalFactor
    ): MarketFactorCoverage {
        return factors[factor.ordinal]
    }
}

internal data class MarketLensResult(
    val sportFamily: SportFamily,
    val effectiveAssessment: SignalAssessment,
    val freshness: FreshnessResult,
    val items: List<MarketLensItem>
) {
    val applicableCount: Int
        get() = items.count {
            it.status != MarketLensStatus.NOT_APPLICABLE
        }

    val coveredCount: Int
        get() = items.count {
            it.status == MarketLensStatus.COVERED
        }

    val checkCount: Int
        get() = items.count {
            it.status == MarketLensStatus.CHECK
        }

    val closedCount: Int
        get() = items.count {
            it.status == MarketLensStatus.CLOSED
        }

    fun item(
        kind: MarketKind
    ): MarketLensItem? {
        return items.firstOrNull {
            it.guide.kind == kind
        }
    }
}

internal object SportFamilyClassifier {
    fun classify(
        sport: String
    ): SportFamily {
        val normalized = sport
            .trim()
            .lowercase(Locale.ROOT)
        return when {
            normalized.containsAny(
                "футбол",
                "football",
                "soccer",
                "futsal",
                "futbol"
            ) -> SportFamily.FOOTBALL
            normalized.containsAny(
                "хоккей",
                "hockey"
            ) -> SportFamily.HOCKEY
            normalized.containsAny(
                "баскетбол",
                "basketball"
            ) -> SportFamily.BASKETBALL
            normalized.containsAny(
                "теннис",
                "tennis"
            ) -> SportFamily.TENNIS
            normalized.containsAny(
                "мма",
                "mma",
                "бокс",
                "boxing",
                "единобор"
            ) -> SportFamily.COMBAT
            normalized.containsAny(
                "кибер",
                "esport",
                "cs2",
                "dota"
            ) -> SportFamily.ESPORTS
            else -> SportFamily.OTHER
        }
    }

    private fun String.containsAny(
        vararg values: String
    ): Boolean {
        return values.any(::contains)
    }
}

internal object MarketLensCatalog {
    private val allSports = SportFamily.values().toSet()
    private val scoreSports = setOf(
        SportFamily.FOOTBALL,
        SportFamily.HOCKEY
    )
    private val individualSports = setOf(
        SportFamily.FOOTBALL,
        SportFamily.HOCKEY,
        SportFamily.BASKETBALL,
        SportFamily.TENNIS,
        SportFamily.ESPORTS
    )
    private val segmentSports = individualSports +
        SportFamily.COMBAT

    val definitions: List<MarketLensDefinition> = listOf(
        definition(
            kind = MarketKind.ONE_X_TWO,
            required = listOf(
                SignalFactor.FORM,
                SignalFactor.LINEUP,
                SignalFactor.CONTEXT,
                SignalFactor.SOURCES
            ),
            critical = setOf(
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            sports = scoreSports
        ),
        definition(
            kind = MarketKind.HANDICAP,
            required = SignalFactor.values().toList(),
            critical = setOf(
                SignalFactor.FORM,
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            sports = allSports
        ),
        definition(
            kind = MarketKind.TOTAL,
            required = SignalFactor.values().toList(),
            critical = setOf(
                SignalFactor.LOAD,
                SignalFactor.CONTEXT,
                SignalFactor.SOURCES
            ),
            sports = allSports
        ),
        definition(
            kind = MarketKind.BOTH_SCORE,
            required = listOf(
                SignalFactor.FORM,
                SignalFactor.LINEUP,
                SignalFactor.CONTEXT,
                SignalFactor.SOURCES
            ),
            critical = setOf(
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            sports = setOf(SportFamily.FOOTBALL)
        ),
        definition(
            kind = MarketKind.INDIVIDUAL_TOTAL,
            required = listOf(
                SignalFactor.FORM,
                SignalFactor.LINEUP,
                SignalFactor.CONTEXT,
                SignalFactor.SOURCES
            ),
            critical = setOf(
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            sports = individualSports
        ),
        definition(
            kind = MarketKind.PERIOD,
            required = SignalFactor.values().toList(),
            critical = setOf(
                SignalFactor.LINEUP,
                SignalFactor.LOAD,
                SignalFactor.SOURCES
            ),
            sports = segmentSports
        )
    )

    fun definition(
        kind: MarketKind
    ): MarketLensDefinition {
        return requireNotNull(
            definitions.firstOrNull { it.kind == kind }
        )
    }

    private fun definition(
        kind: MarketKind,
        required: List<SignalFactor>,
        critical: Set<SignalFactor>,
        sports: Set<SportFamily>
    ): MarketLensDefinition {
        return MarketLensDefinition(
            kind = kind,
            requiredFactors = required,
            criticalFactors = critical,
            applicableSports = sports
        )
    }
}

internal object MarketLensEngine {
    fun evaluate(
        sport: String,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long,
        guides: List<MarketGuide> = DemoCatalog.markets
    ): MarketLensResult {
        require(now >= 0L)
        require(guides.map(MarketGuide::kind).distinct().size == guides.size)
        val definitions = guides.associate {
            it.kind to MarketLensCatalog.definition(it.kind)
        }
        val freshness = FreshnessEngine.evaluate(
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val evidenceResult = EvidenceEngine.evaluate(
            assessment = assessment,
            evidence = freshness.effectiveEvidence
        )
        val family = SportFamilyClassifier.classify(sport)
        val items = guides.map { guide ->
            evaluateMarket(
                guide = guide,
                definition = requireNotNull(
                    definitions[guide.kind]
                ),
                family = family,
                effectiveAssessment =
                    evidenceResult.effectiveAssessment,
                freshness = freshness
            )
        }
        return MarketLensResult(
            sportFamily = family,
            effectiveAssessment =
                evidenceResult.effectiveAssessment,
            freshness = freshness,
            items = items
        )
    }

    private fun evaluateMarket(
        guide: MarketGuide,
        definition: MarketLensDefinition,
        family: SportFamily,
        effectiveAssessment: SignalAssessment,
        freshness: FreshnessResult
    ): MarketLensItem {
        val applicable =
            family in definition.applicableSports
        val factors = SignalFactor.values().map { factor ->
            val required =
                factor in definition.requiredFactors
            val value =
                effectiveAssessment.value(factor)
            MarketFactorCoverage(
                factor = factor,
                required = required,
                critical =
                    factor in definition.criticalFactors,
                effectiveValue = value,
                evidence =
                    freshness.effectiveEvidence.level(factor),
                freshness = freshness.factor(factor),
                state = when {
                    !required ->
                        MarketFactorState.UNUSED
                    freshness.effectiveEvidence.level(factor) ==
                        EvidenceLevel.UNCONFIRMED ->
                        MarketFactorState.BLOCKED
                    factor in
                        definition.criticalFactors &&
                        (
                            freshness.effectiveEvidence
                                .level(factor) !=
                                EvidenceLevel.QUORUM ||
                                freshness.factor(factor).status ==
                                FreshnessStatus.EXPIRING
                            ) ->
                        MarketFactorState.PARTIAL
                    else ->
                        MarketFactorState.COVERED
                }
            )
        }
        val blocking = if (applicable) {
            definition.criticalFactors
                .filter {
                    freshness.effectiveEvidence.level(it) ==
                        EvidenceLevel.UNCONFIRMED
                }
                .sortedBy(SignalFactor::ordinal)
        } else {
            emptyList()
        }
        val requiredConfirmed = definition.requiredFactors.all {
            freshness.effectiveEvidence.level(it) !=
                EvidenceLevel.UNCONFIRMED
        }
        val criticalVerified =
            definition.criticalFactors.all {
                freshness.effectiveEvidence.level(it) ==
                    EvidenceLevel.QUORUM &&
                    freshness.factor(it).status !=
                    FreshnessStatus.EXPIRING
            }
        val status = when {
            !applicable ->
                MarketLensStatus.NOT_APPLICABLE
            blocking.isNotEmpty() ->
                MarketLensStatus.CLOSED
            requiredConfirmed && criticalVerified ->
                MarketLensStatus.COVERED
            else ->
                MarketLensStatus.CHECK
        }
        val metConditions = if (!applicable) {
            0
        } else {
            definition.requiredFactors.count {
                freshness.effectiveEvidence.level(it) !=
                    EvidenceLevel.UNCONFIRMED
            } + definition.criticalFactors.count {
                freshness.effectiveEvidence.level(it) ==
                    EvidenceLevel.QUORUM &&
                    freshness.factor(it).status !=
                    FreshnessStatus.EXPIRING
            }
        }
        val conditionCount =
            definition.requiredFactors.size +
                definition.criticalFactors.size
        return MarketLensItem(
            guide = guide,
            definition = definition,
            status = status,
            factors = factors,
            blockingFactors = blocking,
            metConditions = metConditions,
            conditionCount = conditionCount,
            nextCheck = nextCheck(
                status = status,
                definition = definition,
                effectiveAssessment = effectiveAssessment,
                freshness = freshness,
                blockingFactors = blocking
            )
        )
    }

    private fun nextCheck(
        status: MarketLensStatus,
        definition: MarketLensDefinition,
        effectiveAssessment: SignalAssessment,
        freshness: FreshnessResult,
        blockingFactors: List<SignalFactor>
    ): MarketNextCheck? {
        if (status == MarketLensStatus.NOT_APPLICABLE) {
            return null
        }
        if (blockingFactors.isNotEmpty()) {
            val factor = blockingFactors.minWithOrNull(
                compareBy<SignalFactor> {
                    effectiveAssessment.value(it)
                }.thenBy(SignalFactor::ordinal)
            ) ?: return null
            return MarketNextCheck(
                factor,
                MarketNextCheckReason.BLOCKER
            )
        }
        val missingQuorum = definition.criticalFactors
            .filter {
                freshness.effectiveEvidence.level(it) !=
                    EvidenceLevel.QUORUM
            }
            .minWithOrNull(
                compareBy<SignalFactor> {
                    freshness.effectiveEvidence
                        .level(it)
                        .ordinal
                }.thenBy {
                    effectiveAssessment.value(it)
                }.thenBy(SignalFactor::ordinal)
            )
        if (missingQuorum != null) {
            return MarketNextCheck(
                missingQuorum,
                MarketNextCheckReason.QUORUM
            )
        }
        val evidenceGap = definition.requiredFactors
            .filter {
                freshness.effectiveEvidence.level(it) ==
                    EvidenceLevel.UNCONFIRMED
            }
            .minWithOrNull(
                compareBy<SignalFactor> {
                    if (it in definition.criticalFactors) 1 else 0
                }.thenBy(SignalFactor::ordinal)
            )
        if (evidenceGap != null) {
            return MarketNextCheck(
                evidenceGap,
                MarketNextCheckReason.EVIDENCE_GAP
            )
        }
        val expiring = definition.criticalFactors
            .filter {
                freshness.factor(it).status ==
                    FreshnessStatus.EXPIRING
            }
            .minWithOrNull(
                compareBy<SignalFactor> {
                    freshness.factor(it)
                        .nextTransitionAt
                        ?: Long.MAX_VALUE
                }.thenBy(SignalFactor::ordinal)
            )
        if (expiring != null) {
            return MarketNextCheck(
                expiring,
                MarketNextCheckReason.FRESHNESS
            )
        }
        val maintenance = definition.requiredFactors
            .filter {
                freshness.factor(it).nextTransitionAt != null
            }
            .minWithOrNull(
                compareBy<SignalFactor> {
                    freshness.factor(it)
                        .nextTransitionAt
                        ?: Long.MAX_VALUE
                }.thenBy(SignalFactor::ordinal)
            )
        return maintenance?.let {
            MarketNextCheck(
                it,
                MarketNextCheckReason.MAINTENANCE
            )
        }
    }
}
