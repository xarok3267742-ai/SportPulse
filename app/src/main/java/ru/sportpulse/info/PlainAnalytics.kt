package ru.sportpulse.info

internal enum class PlainAnalyticsStatus {
    STOP,
    CHECK,
    READY
}

internal data class PlainAnalyticsFactorSummary(
    val factor: SignalFactor,
    val effectiveLevel: EvidenceLevel,
    val freshnessStatus: FreshnessStatus,
    val isNextAction: Boolean
)

internal data class PlainAnalyticsResult(
    val status: PlainAnalyticsStatus,
    val confirmedFactorCount: Int,
    val independentlyVerifiedCount: Int,
    val totalFactorCount: Int,
    val headline: String,
    val knownSummary: String,
    val gapSummary: String,
    val actionSummary: String,
    val actionFactor: SignalFactor,
    val factorSummaries: List<PlainAnalyticsFactorSummary>
) {
    init {
        require(totalFactorCount > 0)
        require(confirmedFactorCount in 0..totalFactorCount)
        require(independentlyVerifiedCount in 0..confirmedFactorCount)
        require(headline.isNotBlank())
        require(knownSummary.isNotBlank())
        require(gapSummary.isNotBlank())
        require(actionSummary.isNotBlank())
        require(factorSummaries.size == totalFactorCount)
        require(factorSummaries.map { it.factor } == SignalFactor.values().toList())
        require(factorSummaries.count { it.isNextAction } == 1)
        require(factorSummaries.single { it.isNextAction }.factor == actionFactor)
    }
}

internal object PlainAnalyticsEngine {
    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        now: Long
    ): PlainAnalyticsResult {
        require(now >= 0L)
        val freshness = FreshnessEngine.evaluate(
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val evaluated = EvidenceEngine.evaluate(
            assessment = assessment,
            evidence = freshness.effectiveEvidence
        )
        val factors = SignalFactor.values().toList()
        val quorumCount = factors.count {
            freshness.effectiveEvidence.level(it) == EvidenceLevel.QUORUM
        }
        val singleCount = factors.count {
            freshness.effectiveEvidence.level(it) ==
                EvidenceLevel.SINGLE_SOURCE
        }
        val missing = factors.filter {
            freshness.effectiveEvidence.level(it) ==
                EvidenceLevel.UNCONFIRMED
        }
        val actionFactor = factors.minWithOrNull(
            compareBy<SignalFactor> {
                evidencePriority(freshness.effectiveEvidence.level(it))
            }.thenBy {
                evaluated.effectiveAssessment.value(it)
            }.thenBy(SignalFactor::ordinal)
        ) ?: SignalFactor.SOURCES
        val status = when {
            missing.isNotEmpty() -> PlainAnalyticsStatus.STOP
            singleCount > 0 -> PlainAnalyticsStatus.CHECK
            else -> PlainAnalyticsStatus.READY
        }
        val headline = when (status) {
            PlainAnalyticsStatus.STOP -> "Не хватает подтверждений"
            PlainAnalyticsStatus.CHECK -> "Нужна независимая сверка"
            PlainAnalyticsStatus.READY -> "Источники собраны"
        }
        val confirmedCount = factors.size - missing.size
        val knownSummary = "Есть источник: $confirmedCount из ${factors.size}. " +
            "Независимо сверено: $quorumCount из ${factors.size}."
        val gapSummary = when {
            missing.isNotEmpty() -> "Без подтверждения: ${
                missing.joinToString(", ") { it.shortTitle.lowercase() }
            }."
            singleCount > 0 -> "Нужен второй независимый источник: ${
                factors.filter {
                    freshness.effectiveEvidence.level(it) ==
                        EvidenceLevel.SINGLE_SOURCE
                }.joinToString(", ") { it.shortTitle.lowercase() }
            }."
            else -> "Все пять факторов независимо сверены. " +
                "Первым обновите «${actionFactor.title}»."
        }
        val actionSummary = when (
            freshness.effectiveEvidence.level(actionFactor)
        ) {
            EvidenceLevel.UNCONFIRMED ->
                "Найдите свежий источник по фактору «${actionFactor.title}»."
            EvidenceLevel.SINGLE_SOURCE ->
                "Добавьте независимый второй источник по фактору «${actionFactor.title}»."
            EvidenceLevel.QUORUM ->
                "Уточните оценку фактора «${actionFactor.title}» по собранным фактам."
        }
        val factorSummaries = freshness.factors.map { factorFreshness ->
            PlainAnalyticsFactorSummary(
                factor = factorFreshness.factor,
                effectiveLevel = factorFreshness.effectiveLevel,
                freshnessStatus = factorFreshness.status,
                isNextAction = factorFreshness.factor == actionFactor
            )
        }
        return PlainAnalyticsResult(
            status = status,
            confirmedFactorCount = confirmedCount,
            independentlyVerifiedCount = quorumCount,
            totalFactorCount = factors.size,
            headline = headline,
            knownSummary = knownSummary,
            gapSummary = gapSummary,
            actionSummary = actionSummary,
            actionFactor = actionFactor,
            factorSummaries = factorSummaries
        )
    }

    private fun evidencePriority(level: EvidenceLevel): Int {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> 0
            EvidenceLevel.SINGLE_SOURCE -> 1
            EvidenceLevel.QUORUM -> 2
        }
    }
}
