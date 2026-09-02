package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal enum class DecisionDeskSection(
    val title: String
) {
    DECISION("Решение"),
    HISTORY("История"),
    PROFILE("Профиль");

    companion object {
        fun fromStored(value: String?): DecisionDeskSection {
            return values().firstOrNull { it.name == value }
                ?: DECISION
        }
    }
}

internal enum class DecisionDeskField(
    val title: String,
    val actionObject: String,
    val actionTitle: String
) {
    THESIS("идея матча", "идею матча", "Записать идею матча"),
    COUNTERARGUMENT(
        "главное возражение",
        "главное возражение",
        "Записать возражение"
    ),
    STOP_CONDITION(
        "условие отказа",
        "условие отказа",
        "Задать условие отказа"
    )
}

internal enum class DecisionDeskStatus(
    val title: String,
    val decision: SavedDecision
) {
    STOP("СТОП", SavedDecision.SKIP),
    OBSERVE("НАБЛЮДАТЬ", SavedDecision.OBSERVE),
    FACTS_READY("ФАКТЫ ГОТОВЫ", SavedDecision.DATA_READY)
}

internal data class DecisionDeskDraft(
    val eventId: String,
    val marketKind: MarketKind,
    val thesis: String,
    val counterargument: String,
    val stopCondition: String,
    val updatedAt: Long,
    val formatVersion: Int,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventId.length <= MAX_EVENT_ID_LENGTH)
        require(thesis.length <= MAX_THESIS_LENGTH)
        require(counterargument.length <= MAX_COUNTERARGUMENT_LENGTH)
        require(stopCondition.length <= MAX_STOP_CONDITION_LENGTH)
        require(updatedAt >= 0L)
        require(formatVersion == CURRENT_FORMAT_VERSION)
        require(fingerprint.isEmpty() || HEX_64.matches(fingerprint))
    }

    val missingFields: List<DecisionDeskField>
        get() = buildList {
            if (thesis.isBlank()) add(DecisionDeskField.THESIS)
            if (counterargument.isBlank()) {
                add(DecisionDeskField.COUNTERARGUMENT)
            }
            if (stopCondition.isBlank()) {
                add(DecisionDeskField.STOP_CONDITION)
            }
        }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val MAX_EVENT_ID_LENGTH = 200
        const val MAX_THESIS_LENGTH = 280
        const val MAX_COUNTERARGUMENT_LENGTH = 280
        const val MAX_STOP_CONDITION_LENGTH = 220
        private val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object DecisionDeskDraftFactory {
    fun create(
        eventId: String,
        marketKind: MarketKind,
        thesis: String,
        counterargument: String,
        stopCondition: String,
        updatedAt: Long
    ): DecisionDeskDraft {
        require(updatedAt >= 0L)
        val draft = DecisionDeskDraft(
            eventId = clean(eventId, DecisionDeskDraft.MAX_EVENT_ID_LENGTH)
                .also { require(it.isNotBlank()) },
            marketKind = marketKind,
            thesis = clean(thesis, DecisionDeskDraft.MAX_THESIS_LENGTH),
            counterargument = clean(
                counterargument,
                DecisionDeskDraft.MAX_COUNTERARGUMENT_LENGTH
            ),
            stopCondition = clean(
                stopCondition,
                DecisionDeskDraft.MAX_STOP_CONDITION_LENGTH
            ),
            updatedAt = updatedAt,
            formatVersion = DecisionDeskDraft.CURRENT_FORMAT_VERSION,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = DecisionDeskDraftCodec.fingerprintFor(draft)
        )
    }

    private fun clean(value: String, limit: Int): String {
        return value
            .filter { character ->
                character == '\n' ||
                    character == '\t' ||
                    !character.isISOControl()
            }
            .trim()
            .take(limit)
    }
}

internal object DecisionDeskDraftCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 8
    private const val MAX_ENCODED_LENGTH = 4_096
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(draft: DecisionDeskDraft): String {
        val expected = fingerprintFor(draft)
        require(
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.US_ASCII),
                draft.fingerprint.toByteArray(StandardCharsets.US_ASCII)
            )
        )
        return "${payload(draft)}|$expected"
    }

    fun decode(encoded: String): DecisionDeskDraft? {
        if (encoded.length > MAX_ENCODED_LENGTH) return null
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val draft = DecisionDeskDraft(
                eventId = decodeText(parts[1]),
                marketKind = MarketKind.valueOf(parts[2]),
                thesis = decodeText(parts[3]),
                counterargument = decodeText(parts[4]),
                stopCondition = decodeText(parts[5]),
                updatedAt = parts[6].toLong(),
                formatVersion = DecisionDeskDraft.CURRENT_FORMAT_VERSION,
                fingerprint = parts[7].lowercase()
            )
            val expected = fingerprintFor(draft)
            require(
                MessageDigest.isEqual(
                    expected.toByteArray(StandardCharsets.US_ASCII),
                    draft.fingerprint.toByteArray(StandardCharsets.US_ASCII)
                )
            )
            draft
        }.getOrNull()
    }

    fun fingerprintFor(draft: DecisionDeskDraft): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
            payload(draft).toByteArray(StandardCharsets.UTF_8)
        )
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun payload(draft: DecisionDeskDraft): String {
        return listOf(
            VERSION,
            encodeText(draft.eventId),
            draft.marketKind.name,
            encodeText(draft.thesis),
            encodeText(draft.counterargument),
            encodeText(draft.stopCondition),
            draft.updatedAt.toString()
        ).joinToString("|")
    }

    private fun encodeText(value: String): String {
        return encoder.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun decodeText(value: String): String {
        return String(decoder.decode(value), StandardCharsets.UTF_8)
    }
}

internal data class DecisionDeskResult(
    val status: DecisionDeskStatus,
    val missingFields: List<DecisionDeskField>,
    val marketStatus: MarketLensStatus?,
    val counterVerdict: CounterViewVerdict,
    val nextFactor: SignalFactor?,
    val headline: String,
    val explanation: String,
    val actionTitle: String
)

internal object DecisionDeskEngine {
    fun evaluate(
        draft: DecisionDeskDraft,
        market: MarketLensItem?,
        counterView: CounterViewResult
    ): DecisionDeskResult {
        require(market == null || market.guide.kind == draft.marketKind)
        val missing = draft.missingFields
        val firstMissing = missing.firstOrNull()
        if (firstMissing != null) {
            return result(
                status = DecisionDeskStatus.STOP,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Идея ещё не проверена",
                explanation =
                    "Запишите ${firstMissing.actionObject}: без этого после матча нельзя честно сравнить ожидание с фактом.",
                actionTitle = firstMissing.actionTitle
            )
        }
        if (
            market == null ||
            market.status == MarketLensStatus.NOT_APPLICABLE
        ) {
            return result(
                status = DecisionDeskStatus.STOP,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Рынок не подходит событию",
                explanation = "Выберите тип рынка, для которого у этого вида спорта есть проверяемая карта факторов.",
                actionTitle = "Выбрать другой рынок"
            )
        }
        if (market.status == MarketLensStatus.CLOSED) {
            val factor = market.nextCheck?.factor
            return result(
                status = DecisionDeskStatus.STOP,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Критический факт не подтверждён",
                explanation = factor?.let {
                    "Сначала подтвердите фактор «${it.title}» независимыми источниками."
                } ?: "Сначала закройте критический пробел в фактах.",
                actionTitle = factor?.let {
                    "Проверить: ${it.shortTitle}"
                } ?: "Открыть карту факторов"
            )
        }
        if (counterView.verdict == CounterViewVerdict.REFUTED) {
            return result(
                status = DecisionDeskStatus.STOP,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Факт опроверг исходную идею",
                explanation =
                    "Не подгоняйте ожидание под результат. Зафиксируйте отказ или запишите новую идею.",
                actionTitle = "Записать новую идею"
            )
        }
        if (counterView.defensibleVerdict == SignalVerdict.SKIP) {
            val factor = counterView.nextFactor ?: market.nextCheck?.factor
            return result(
                status = DecisionDeskStatus.STOP,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Проверка пока не выдержана",
                explanation = factor?.let {
                    "Следующий обязательный шаг: проверить альтернативную трактовку фактора «${it.title}»."
                } ?: "Проверенных фактов пока недостаточно для решения.",
                actionTitle = factor?.let {
                    "Проверить: ${it.shortTitle}"
                } ?: "Открыть подробную проверку"
            )
        }
        if (
            market.status == MarketLensStatus.CHECK ||
            counterView.defensibleVerdict == SignalVerdict.OBSERVE ||
            counterView.decisionCeiling == SavedDecision.OBSERVE
        ) {
            val factor = market.nextCheck?.factor ?: counterView.nextFactor
            return result(
                status = DecisionDeskStatus.OBSERVE,
                draft = draft,
                market = market,
                counterView = counterView,
                headline = "Идею стоит наблюдать",
                explanation = factor?.let {
                    "До фиксации не хватает проверки фактора «${it.title}»."
                } ?: "Факты расходятся: не повышайте статус, пока спор не разрешён.",
                actionTitle = factor?.let {
                    "Дособрать: ${it.shortTitle}"
                } ?: "Разобрать спор фактов"
            )
        }
        return result(
            status = DecisionDeskStatus.FACTS_READY,
            draft = draft,
            market = market,
            counterView = counterView,
            headline = "Идея выдержала проверку",
            explanation = "Критические факторы свежие, а альтернативная версия проверена. Это готовность данных, не прогноз исхода.",
            actionTitle = "Зафиксировать в журнале"
        )
    }

    private fun result(
        status: DecisionDeskStatus,
        draft: DecisionDeskDraft,
        market: MarketLensItem?,
        counterView: CounterViewResult,
        headline: String,
        explanation: String,
        actionTitle: String
    ): DecisionDeskResult {
        return DecisionDeskResult(
            status = status,
            missingFields = draft.missingFields,
            marketStatus = market?.status,
            counterVerdict = counterView.verdict,
            nextFactor = if (
                status == DecisionDeskStatus.FACTS_READY
            ) {
                null
            } else {
                market?.nextCheck?.factor
                    ?: counterView.nextFactor
            },
            headline = headline,
            explanation = explanation,
            actionTitle = actionTitle
        )
    }
}

internal data class DecisionDeskProfile(
    val totalDecisions: Long,
    val stopCount: Int,
    val observeCount: Int,
    val readyCount: Int,
    val reviewedEvents: Int
) {
    val visibleDecisionCount: Int
        get() = stopCount + observeCount + readyCount

    val cautiousShare: Int
        get() = if (visibleDecisionCount == 0) {
            0
        } else {
            (((stopCount + observeCount) * 100L) /
                visibleDecisionCount)
                .toInt()
        }
}

internal object DecisionDeskProfileEngine {
    fun create(
        ledger: DecisionLedger,
        reviewedEvents: Int
    ): DecisionDeskProfile {
        require(reviewedEvents >= 0)
        return DecisionDeskProfile(
            totalDecisions = ledger.totalRecordCount,
            stopCount = ledger.records.count {
                it.decision == SavedDecision.SKIP
            },
            observeCount = ledger.records.count {
                it.decision == SavedDecision.OBSERVE
            },
            readyCount = ledger.records.count {
                it.decision == SavedDecision.DATA_READY
            },
            reviewedEvents = reviewedEvents
        )
    }
}
