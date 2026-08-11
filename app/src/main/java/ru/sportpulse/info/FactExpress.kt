package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class FactExpressState {
    EMPTY,
    NEED_MORE,
    READY,
    TOO_MANY
}

internal enum class FactExpressEntryState {
    ACTION_NOW,
    WAITING,
    UNSCHEDULED,
    COMPLETE
}

internal data class FactExpressCandidate(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int,
    val story: EventStoryResult,
    val beacon: StoryBeaconResult
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
        require(story.eventId == eventId)
        require(story.eventLabel == match)
        require(beacon.eventId == eventId)
    }
}

internal data class FactExpressEntry(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int,
    val chapter: EventStoryChapter,
    val chapterState: EventStoryChapterState,
    val state: FactExpressEntryState,
    val action: EventStoryAction,
    val actionFactor: SignalFactor?,
    val nextMoment: StoryBeaconMoment?,
    val storyFingerprint: String,
    val beaconFingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
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
        when (state) {
            FactExpressEntryState.ACTION_NOW ->
                require(action != EventStoryAction.NONE)
            FactExpressEntryState.WAITING -> {
                require(action == EventStoryAction.NONE)
                require(nextMoment != null)
            }
            FactExpressEntryState.UNSCHEDULED,
            FactExpressEntryState.COMPLETE -> {
                require(action == EventStoryAction.NONE)
                require(nextMoment == null)
            }
        }
        require(HEX_64.matches(storyFingerprint))
        require(HEX_64.matches(beaconFingerprint))
    }

    val scheduledAt: Long?
        get() = nextMoment?.at

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal data class FactExpressResult(
    val entries: List<FactExpressEntry>,
    val state: FactExpressState,
    val selectedZone: RegionalZone,
    val evaluatedAtMinute: Long,
    val actionNowCount: Int,
    val scheduledCount: Int,
    val unscheduledCount: Int,
    val completeCount: Int,
    val fingerprint: String
) {
    init {
        require(evaluatedAtMinute >= 0L)
        require(
            entries.map(FactExpressEntry::eventId)
                .distinct().size == entries.size
        )
        require(
            entries.map(FactExpressEntry::catalogOrder)
                .distinct().size == entries.size
        )
        require(
            entries.zipWithNext().all { (left, right) ->
                FactExpressEngine.comparator.compare(left, right) <= 0
            }
        )
        require(
            state == when {
                entries.isEmpty() -> FactExpressState.EMPTY
                entries.size < FactExpressPolicy.MIN_EVENTS ->
                    FactExpressState.NEED_MORE
                entries.size > FactExpressPolicy.MAX_EVENTS ->
                    FactExpressState.TOO_MANY
                else -> FactExpressState.READY
            }
        )
        require(
            actionNowCount == entries.count {
                it.state == FactExpressEntryState.ACTION_NOW
            }
        )
        require(
            scheduledCount == entries.count {
                it.nextMoment != null
            }
        )
        require(
            unscheduledCount == entries.count {
                it.state == FactExpressEntryState.UNSCHEDULED
            }
        )
        require(
            completeCount == entries.count {
                it.state == FactExpressEntryState.COMPLETE
            }
        )
        require(HEX_64.matches(fingerprint))
    }

    val isReady: Boolean
        get() = state == FactExpressState.READY

    val overLimitCount: Int
        get() = (entries.size - FactExpressPolicy.MAX_EVENTS)
            .coerceAtLeast(0)

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object FactExpressPolicy {
    const val MIN_EVENTS = 2
    const val MAX_EVENTS = 4
}

internal object FactExpressEngine {
    private const val VERSION = "sport-pulse-fact-express-v1"
    private const val MINUTE_MILLIS = 60_000L
    private val hex = "0123456789abcdef".toCharArray()

    internal val comparator = compareBy<FactExpressEntry> {
        sortGroup(it.state)
    }.thenBy {
        it.scheduledAt ?: Long.MAX_VALUE
    }.thenBy {
        it.catalogOrder
    }.thenBy {
        it.eventId
    }

    fun evaluate(
        candidates: List<FactExpressCandidate>,
        selectedZone: RegionalZone,
        now: Long
    ): FactExpressResult {
        require(now >= 0L)
        require(
            candidates.map(FactExpressCandidate::eventId)
                .distinct().size == candidates.size
        )
        require(
            candidates.map(FactExpressCandidate::catalogOrder)
                .distinct().size == candidates.size
        )
        val evaluatedAtMinute = now / MINUTE_MILLIS
        require(candidates.all {
            it.beacon.evaluatedAtMinute == evaluatedAtMinute
        })
        val entries = candidates.map { candidate ->
            val story = candidate.story
            val nextMoment = candidate.beacon.moments
                .firstOrNull { it.at != null }
            val state = when {
                story.phase == EventStoryPhase.COMPLETE ->
                    FactExpressEntryState.COMPLETE
                story.action != EventStoryAction.NONE ->
                    FactExpressEntryState.ACTION_NOW
                nextMoment != null ->
                    FactExpressEntryState.WAITING
                else -> FactExpressEntryState.UNSCHEDULED
            }
            FactExpressEntry(
                eventId = candidate.eventId,
                match = candidate.match,
                sport = candidate.sport,
                region = candidate.region,
                catalogOrder = candidate.catalogOrder,
                chapter = story.currentChapter,
                chapterState = story.chapter(
                    story.currentChapter
                ).state,
                state = state,
                action = story.action,
                actionFactor = story.actionFactor,
                nextMoment = if (
                    state == FactExpressEntryState.COMPLETE
                ) {
                    null
                } else {
                    nextMoment
                },
                storyFingerprint = story.fingerprint,
                beaconFingerprint = candidate.beacon.fingerprint
            )
        }.sortedWith(comparator)
        val state = when {
            entries.isEmpty() -> FactExpressState.EMPTY
            entries.size < FactExpressPolicy.MIN_EVENTS ->
                FactExpressState.NEED_MORE
            entries.size > FactExpressPolicy.MAX_EVENTS ->
                FactExpressState.TOO_MANY
            else -> FactExpressState.READY
        }
        return FactExpressResult(
            entries = entries,
            state = state,
            selectedZone = selectedZone,
            evaluatedAtMinute = evaluatedAtMinute,
            actionNowCount = entries.count {
                it.state == FactExpressEntryState.ACTION_NOW
            },
            scheduledCount = entries.count {
                it.nextMoment != null
            },
            unscheduledCount = entries.count {
                it.state == FactExpressEntryState.UNSCHEDULED
            },
            completeCount = entries.count {
                it.state == FactExpressEntryState.COMPLETE
            },
            fingerprint = fingerprintFor(
                entries = entries,
                state = state,
                selectedZone = selectedZone,
                evaluatedAtMinute = evaluatedAtMinute
            )
        )
    }

    private fun fingerprintFor(
        entries: List<FactExpressEntry>,
        state: FactExpressState,
        selectedZone: RegionalZone,
        evaluatedAtMinute: Long
    ): String {
        val payload = buildString {
            appendToken(VERSION)
            appendToken(state.name)
            appendToken(selectedZone.name)
            appendToken(evaluatedAtMinute.toString())
            entries.forEach { entry ->
                appendToken(entry.eventId)
                appendToken(entry.match)
                appendToken(entry.sport)
                appendToken(entry.region)
                appendToken(entry.catalogOrder.toString())
                appendToken(entry.chapter.name)
                appendToken(entry.chapterState.name)
                appendToken(entry.state.name)
                appendToken(entry.action.name)
                appendToken(entry.actionFactor?.name.orEmpty())
                appendToken(entry.nextMoment?.kind?.name.orEmpty())
                appendToken(
                    entry.nextMoment?.at?.toString().orEmpty()
                )
                appendToken(
                    entry.nextMoment?.factors
                        ?.joinToString(",") { it.name }
                        .orEmpty()
                )
                appendToken(entry.storyFingerprint)
                appendToken(entry.beaconFingerprint)
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

    private fun StringBuilder.appendToken(value: String) {
        append(value.length)
        append(':')
        append(value)
        append('|')
    }

    private fun sortGroup(state: FactExpressEntryState): Int {
        return when (state) {
            FactExpressEntryState.ACTION_NOW -> 0
            FactExpressEntryState.WAITING -> 1
            FactExpressEntryState.UNSCHEDULED -> 2
            FactExpressEntryState.COMPLETE -> 3
        }
    }
}

internal object FactExpressText {
    fun stateTitle(state: FactExpressEntryState): String {
        return when (state) {
            FactExpressEntryState.ACTION_NOW -> "ПРОВЕРИТЬ СЕЙЧАС"
            FactExpressEntryState.WAITING -> "ЖДАТЬ ТОЧКУ"
            FactExpressEntryState.UNSCHEDULED -> "ВРЕМЯ НЕ ДОКАЗАНО"
            FactExpressEntryState.COMPLETE -> "СЮЖЕТ ЗАКРЫТ"
        }
    }

    fun actionTitle(entry: FactExpressEntry): String {
        return when (entry.action) {
            EventStoryAction.OPEN_SOURCE -> "Проверить источник"
            EventStoryAction.OPEN_FACTS -> entry.actionFactor?.let {
                "Проверить фактор: ${it.title}"
            } ?: "Открыть факты"
            EventStoryAction.OPEN_PLAN -> "Открыть план к старту"
            EventStoryAction.OPEN_DECISION ->
                "Зафиксировать решение"
            EventStoryAction.OPEN_REVIEW -> "Открыть разбор"
            EventStoryAction.NONE -> when (entry.state) {
                FactExpressEntryState.WAITING ->
                    "Действие сейчас не требуется"
                FactExpressEntryState.UNSCHEDULED ->
                    "Следующая проверка не определена"
                FactExpressEntryState.COMPLETE -> "Сюжет закрыт"
                FactExpressEntryState.ACTION_NOW ->
                    error("Action-now entry requires an action")
            }
        }
    }

    fun momentTitle(moment: StoryBeaconMoment): String {
        return when (moment.kind) {
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "Окно проверки: ${factorTitles(moment.factors)}"
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "Срок факта: ${factorTitles(moment.factors)}"
            StoryBeaconMomentKind.START -> "Указанный старт"
            StoryBeaconMomentKind.REVIEW_OPEN -> "Откроется разбор"
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.COMPLETE ->
                error("Fact Express keeps only absolute moments")
        }
    }

    fun momentTime(
        moment: StoryBeaconMoment,
        selectedZone: RegionalZone
    ): String {
        return TimeBridgeEngine.formatInstant(
            startAt = checkNotNull(moment.at),
            selectedZone = selectedZone
        )
    }

    private fun factorTitles(factors: List<SignalFactor>): String {
        return factors.joinToString(", ") { it.title }
    }
}
