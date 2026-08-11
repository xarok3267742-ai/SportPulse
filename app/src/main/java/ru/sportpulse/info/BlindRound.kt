package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class BlindRoundAlignment {
    ALIGNED,
    DIFFERENT
}

internal data class BlindRoundCard(
    val token: String,
    val code: String,
    val state: FactExpressEntryState,
    val chapter: EventStoryChapter,
    val chapterState: EventStoryChapterState,
    val action: EventStoryAction,
    val actionFactor: SignalFactor?,
    val nextMoment: StoryBeaconMoment?
) {
    init {
        require(HEX_64.matches(token))
        require(CODE.matches(code))
        require(
            actionFactor == null ||
                action == EventStoryAction.OPEN_FACTS
        )
        nextMoment?.let {
            require(it.at != null)
            require(
                it.kind != StoryBeaconMomentKind.ACTION_NOW &&
                    it.kind != StoryBeaconMomentKind.COMPLETE
            )
        }
    }

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
        val CODE = Regex("[A-D]")
    }
}

internal data class BlindRoundSession(
    val cards: List<BlindRoundCard>,
    val selectedZone: RegionalZone,
    val evaluatedAtMinute: Long,
    val sourceFingerprint: String,
    val fingerprint: String
) {
    init {
        require(
            cards.size in
                FactExpressPolicy.MIN_EVENTS..FactExpressPolicy.MAX_EVENTS
        )
        require(cards.map(BlindRoundCard::token).distinct().size == cards.size)
        require(cards.map(BlindRoundCard::code).distinct().size == cards.size)
        require(
            cards.map(BlindRoundCard::code) ==
                CODES.take(cards.size)
        )
        require(evaluatedAtMinute >= 0L)
        require(HEX_64.matches(sourceFingerprint))
        require(HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val CODES = listOf("A", "B", "C", "D")
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal data class BlindRoundRevealCard(
    val token: String,
    val code: String,
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val sourceRank: Int,
    val selected: Boolean,
    val firstByFacts: Boolean,
    val state: FactExpressEntryState,
    val chapter: EventStoryChapter,
    val chapterState: EventStoryChapterState,
    val action: EventStoryAction,
    val actionFactor: SignalFactor?,
    val nextMoment: StoryBeaconMoment?
) {
    init {
        require(token.isNotBlank())
        require(code.isNotBlank())
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(sourceRank > 0)
    }
}

internal data class BlindRoundReveal(
    val cards: List<BlindRoundRevealCard>,
    val selectedCode: String,
    val firstByFactsCode: String,
    val alignment: BlindRoundAlignment,
    val sessionFingerprint: String,
    val sourceFingerprint: String,
    val fingerprint: String
) {
    init {
        require(cards.size in 2..4)
        require(cards.count(BlindRoundRevealCard::selected) == 1)
        require(cards.count(BlindRoundRevealCard::firstByFacts) == 1)
        require(cards.single(BlindRoundRevealCard::selected).code == selectedCode)
        require(
            cards.single(BlindRoundRevealCard::firstByFacts).code ==
                firstByFactsCode
        )
        require(
            alignment == if (selectedCode == firstByFactsCode) {
                BlindRoundAlignment.ALIGNED
            } else {
                BlindRoundAlignment.DIFFERENT
            }
        )
        require(HEX_64.matches(sessionFingerprint))
        require(HEX_64.matches(sourceFingerprint))
        require(HEX_64.matches(fingerprint))
    }

    val selectedCard: BlindRoundRevealCard
        get() = cards.single(BlindRoundRevealCard::selected)

    val firstByFactsCard: BlindRoundRevealCard
        get() = cards.single(BlindRoundRevealCard::firstByFacts)

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object BlindRoundEngine {
    private const val VERSION = "sport-pulse-blind-round-v1"
    private val codes = listOf("A", "B", "C", "D")
    private val hex = "0123456789abcdef".toCharArray()

    fun prepare(result: FactExpressResult): BlindRoundSession {
        require(result.isReady)
        val offsetSeed = digest(
            listOf(VERSION, "order", result.fingerprint)
        ).take(8).toLong(16)
        val offset = 1 + (
            offsetSeed % (result.entries.size - 1).toLong()
        ).toInt()
        val shuffled = result.entries.indices.map { position ->
            result.entries[(position + offset) % result.entries.size]
        }
        val cards = shuffled.mapIndexed { index, entry ->
            BlindRoundCard(
                token = tokenFor(result.fingerprint, entry.eventId),
                code = codes[index],
                state = entry.state,
                chapter = entry.chapter,
                chapterState = entry.chapterState,
                action = entry.action,
                actionFactor = entry.actionFactor,
                nextMoment = entry.nextMoment
            )
        }
        return BlindRoundSession(
            cards = cards,
            selectedZone = result.selectedZone,
            evaluatedAtMinute = result.evaluatedAtMinute,
            sourceFingerprint = result.fingerprint,
            fingerprint = sessionFingerprint(
                cards = cards,
                result = result
            )
        )
    }

    fun reveal(
        session: BlindRoundSession,
        result: FactExpressResult,
        selectedToken: String
    ): BlindRoundReveal {
        require(session == prepare(result))
        require(session.cards.any { it.token == selectedToken })
        val entriesByToken = result.entries.associateBy {
            tokenFor(result.fingerprint, it.eventId)
        }
        val firstToken = tokenFor(
            result.fingerprint,
            result.entries.first().eventId
        )
        val cards = session.cards.map { card ->
            val entry = requireNotNull(entriesByToken[card.token])
            BlindRoundRevealCard(
                token = card.token,
                code = card.code,
                eventId = entry.eventId,
                match = entry.match,
                sport = entry.sport,
                region = entry.region,
                sourceRank = result.entries.indexOf(entry) + 1,
                selected = card.token == selectedToken,
                firstByFacts = card.token == firstToken,
                state = card.state,
                chapter = card.chapter,
                chapterState = card.chapterState,
                action = card.action,
                actionFactor = card.actionFactor,
                nextMoment = card.nextMoment
            )
        }
        val selectedCode = cards.single { it.selected }.code
        val firstByFactsCode = cards.single { it.firstByFacts }.code
        val alignment = if (selectedCode == firstByFactsCode) {
            BlindRoundAlignment.ALIGNED
        } else {
            BlindRoundAlignment.DIFFERENT
        }
        return BlindRoundReveal(
            cards = cards,
            selectedCode = selectedCode,
            firstByFactsCode = firstByFactsCode,
            alignment = alignment,
            sessionFingerprint = session.fingerprint,
            sourceFingerprint = result.fingerprint,
            fingerprint = revealFingerprint(
                session = session,
                cards = cards,
                selectedToken = selectedToken,
                alignment = alignment
            )
        )
    }

    private fun tokenFor(
        sourceFingerprint: String,
        eventId: String
    ): String {
        return digest(
            listOf(VERSION, "card", sourceFingerprint, eventId)
        )
    }

    private fun sessionFingerprint(
        cards: List<BlindRoundCard>,
        result: FactExpressResult
    ): String {
        val fields = buildList {
            add(VERSION)
            add("session")
            add(result.fingerprint)
            add(result.selectedZone.name)
            add(result.evaluatedAtMinute.toString())
            cards.forEach { card ->
                add(card.token)
                add(card.code)
                add(card.state.name)
                add(card.chapter.name)
                add(card.chapterState.name)
                add(card.action.name)
                add(card.actionFactor?.name.orEmpty())
                add(card.nextMoment?.kind?.name.orEmpty())
                add(card.nextMoment?.at?.toString().orEmpty())
                add(
                    card.nextMoment?.factors
                        ?.joinToString(",") { it.name }
                        .orEmpty()
                )
            }
        }
        return digest(fields)
    }

    private fun revealFingerprint(
        session: BlindRoundSession,
        cards: List<BlindRoundRevealCard>,
        selectedToken: String,
        alignment: BlindRoundAlignment
    ): String {
        val fields = buildList {
            add(VERSION)
            add("reveal")
            add(session.fingerprint)
            add(session.sourceFingerprint)
            add(selectedToken)
            add(alignment.name)
            cards.forEach { card ->
                add(card.token)
                add(card.code)
                add(card.eventId)
                add(card.match)
                add(card.sport)
                add(card.region)
                add(card.sourceRank.toString())
                add(card.selected.toString())
                add(card.firstByFacts.toString())
            }
        }
        return digest(fields)
    }

    private fun digest(fields: List<String>): String {
        val payload = buildString {
            fields.forEach { value ->
                append(value.length)
                append(':')
                append(value)
                append('|')
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

internal object BlindRoundText {
    fun actionTitle(card: BlindRoundCard): String {
        return actionTitle(
            action = card.action,
            actionFactor = card.actionFactor,
            state = card.state
        )
    }

    fun actionTitle(card: BlindRoundRevealCard): String {
        return actionTitle(
            action = card.action,
            actionFactor = card.actionFactor,
            state = card.state
        )
    }

    fun pointTitle(
        card: BlindRoundCard,
        selectedZone: RegionalZone
    ): String {
        return pointTitle(
            state = card.state,
            nextMoment = card.nextMoment,
            selectedZone = selectedZone
        )
    }

    fun pointTitle(
        card: BlindRoundRevealCard,
        selectedZone: RegionalZone
    ): String {
        return pointTitle(
            state = card.state,
            nextMoment = card.nextMoment,
            selectedZone = selectedZone
        )
    }

    fun resultTitle(alignment: BlindRoundAlignment): String {
        return when (alignment) {
            BlindRoundAlignment.ALIGNED ->
                "Выбор совпал с порядком фактов"
            BlindRoundAlignment.DIFFERENT ->
                "После раскрытия порядок отличается"
        }
    }

    fun resultSummary(reveal: BlindRoundReveal): String {
        return when (reveal.alignment) {
            BlindRoundAlignment.ALIGNED ->
                "Без названий команд вы выбрали то же досье, которое открытый маршрут ставит первым."
            BlindRoundAlignment.DIFFERENT ->
                "Это не ошибка: вы выбрали досье ${reveal.selectedCode}, а открытый маршрут начинает с ${reveal.firstByFactsCode}. Сравните только действие и ближайшую точку."
        }
    }

    private fun actionTitle(
        action: EventStoryAction,
        actionFactor: SignalFactor?,
        state: FactExpressEntryState
    ): String {
        return when (action) {
            EventStoryAction.OPEN_SOURCE -> "Проверить источник"
            EventStoryAction.OPEN_FACTS -> actionFactor?.let {
                "Проверить фактор: ${it.title}"
            } ?: "Открыть факты"
            EventStoryAction.OPEN_PLAN -> "Открыть план к старту"
            EventStoryAction.OPEN_DECISION ->
                "Зафиксировать решение"
            EventStoryAction.OPEN_REVIEW -> "Открыть разбор"
            EventStoryAction.NONE -> when (state) {
                FactExpressEntryState.WAITING ->
                    "Действие сейчас не требуется"
                FactExpressEntryState.UNSCHEDULED ->
                    "Следующая проверка не определена"
                FactExpressEntryState.COMPLETE -> "Сюжет закрыт"
                FactExpressEntryState.ACTION_NOW ->
                    error("Action-now card requires an action")
            }
        }
    }

    private fun pointTitle(
        state: FactExpressEntryState,
        nextMoment: StoryBeaconMoment?,
        selectedZone: RegionalZone
    ): String {
        return nextMoment?.let {
            "${FactExpressText.momentTitle(it)} • ${
                FactExpressText.momentTime(it, selectedZone)
            }"
        } ?: when (state) {
            FactExpressEntryState.COMPLETE ->
                "Все шесть глав локального сюжета завершены"
            FactExpressEntryState.UNSCHEDULED ->
                "Абсолютная следующая точка не доказана"
            FactExpressEntryState.ACTION_NOW,
            FactExpressEntryState.WAITING ->
                "Следующая абсолютная точка не определена"
        }
    }
}
