package ru.sportpulse.info

import kotlin.math.max
import kotlin.math.roundToInt

internal enum class SignalFactor(
    val title: String,
    val shortTitle: String,
    val hint: String
) {
    FORM(
        title = "Форма",
        shortTitle = "Форма",
        hint = "Есть данные против соперников сопоставимого уровня"
    ),
    LINEUP(
        title = "Состав",
        shortTitle = "Состав",
        hint = "Подтверждены травмы, ротация и стартовый состав"
    ),
    LOAD(
        title = "Нагрузка",
        shortTitle = "Нагрузка",
        hint = "Учтены перелеты, отдых и плотность календаря"
    ),
    CONTEXT(
        title = "Контекст",
        shortTitle = "Контекст",
        hint = "Понятны мотивация, формат турнира и условия"
    ),
    SOURCES(
        title = "Источники",
        shortTitle = "Источники",
        hint = "Факты подтверждены несколькими свежими источниками"
    )
}

internal data class SignalAssessment(
    val values: List<Int>
) {
    init {
        require(values.size == SignalFactor.values().size)
        require(values.all { it in 0..100 })
    }

    fun value(factor: SignalFactor): Int = values[factor.ordinal]

    fun withValue(factor: SignalFactor, value: Int): SignalAssessment {
        val updated = values.toMutableList()
        updated[factor.ordinal] = value.coerceIn(0, 100)
        return copy(values = updated)
    }
}

internal enum class SignalVerdict {
    SKIP,
    OBSERVE,
    READY
}

internal object SignalThresholds {
    const val OBSERVE = 40
    const val READY = 72
}

internal data class SignalResult(
    val readiness: Int,
    val noise: Int,
    val weakestFactor: SignalFactor,
    val spread: Int,
    val verdict: SignalVerdict
)

internal object SignalEngine {
    fun evaluate(assessment: SignalAssessment): SignalResult {
        val average = assessment.values.average()
        val weakestValue = assessment.values.minOrNull() ?: 0
        val strongestValue = assessment.values.maxOrNull() ?: 0
        val spread = strongestValue - weakestValue
        val imbalancePenalty = max(0, spread - 50) * 0.08
        val readiness = (
            average * 0.72 +
                weakestValue * 0.28 -
                imbalancePenalty
            ).roundToInt().coerceIn(0, 100)
        val noise = (100 - readiness + spread * 0.12)
            .roundToInt()
            .coerceIn(0, 100)
        val weakestIndex = assessment.values.indices.minByOrNull {
            assessment.values[it]
        } ?: 0
        val verdict = when {
            readiness < SignalThresholds.OBSERVE -> SignalVerdict.SKIP
            readiness < SignalThresholds.READY -> SignalVerdict.OBSERVE
            else -> SignalVerdict.READY
        }

        return SignalResult(
            readiness = readiness,
            noise = noise,
            weakestFactor = SignalFactor.values()[weakestIndex],
            spread = spread,
            verdict = verdict
        )
    }
}

internal enum class SavedDecision {
    SKIP,
    OBSERVE,
    DATA_READY
}

internal enum class SportEventOrigin {
    DEMO,
    EVENT_PACKAGE,
    API_SPORTS
}

internal object SportEventContentPolicy {
    const val MAX_SPORT_LENGTH = 32
    const val MAX_TOURNAMENT_LENGTH = 80
    const val MAX_REGION_LENGTH = 80
    const val MAX_MATCH_LENGTH = 120
    const val MAX_FOCUS_LENGTH = 160
    const val MAX_NOTE_LENGTH = 240
}

internal data class SportEvent(
    val id: String,
    val sport: String,
    val tournament: String,
    val region: String,
    val match: String,
    val time: String,
    val focus: String,
    val note: String,
    val tags: List<String>,
    val imageRes: Int,
    val seedAssessment: SignalAssessment,
    val startAt: Long? = null,
    val demoSchedule: DemoSchedule? = null,
    val origin: SportEventOrigin = SportEventOrigin.DEMO,
    val defaultEvidenceLevel: EvidenceLevel =
        EvidenceLevel.SINGLE_SOURCE,
    val providerRef: String? = null,
    val providerStatus: String? = null,
    val providerStatusCode: String? = null,
    val syncedAt: Long? = null
) {
    init {
        require(sport.isNotBlank() && sport.length <=
            SportEventContentPolicy.MAX_SPORT_LENGTH)
        require(tournament.isNotBlank() && tournament.length <=
            SportEventContentPolicy.MAX_TOURNAMENT_LENGTH)
        require(region.isNotBlank() && region.length <=
            SportEventContentPolicy.MAX_REGION_LENGTH)
        require(match.isNotBlank() && match.length <=
            SportEventContentPolicy.MAX_MATCH_LENGTH)
        require(focus.isNotBlank() && focus.length <=
            SportEventContentPolicy.MAX_FOCUS_LENGTH)
        require(note.isNotBlank() && note.length <=
            SportEventContentPolicy.MAX_NOTE_LENGTH)
    }
}

internal data class MarketGuide(
    val kind: MarketKind,
    val title: String,
    val summary: String,
    val check: String,
    val stopSignal: String
)
