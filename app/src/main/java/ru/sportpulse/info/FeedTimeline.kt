package ru.sportpulse.info

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal enum class FeedTimelineFilter(
    val title: String
) {
    ALL("Все"),
    LIVE("Сейчас"),
    TODAY("Сегодня"),
    TOMORROW("Завтра"),
    LATER("Позже"),
    COMPLETED("Завершены"),
    VERIFY("Проверить");

    companion object {
        fun fromStored(value: String?): FeedTimelineFilter {
            return entries.firstOrNull { it.name == value } ?: ALL
        }
    }
}

internal enum class FeedTimelineReason {
    LIVE_STATUS,
    TODAY_DATE,
    TOMORROW_DATE,
    LATER_DATE,
    FINAL_STATUS,
    POSTPONED,
    SUSPENDED,
    INTERRUPTED,
    CANCELLED,
    ABANDONED,
    AWARDED,
    WALKOVER,
    PAST_WITHOUT_FINAL_STATUS,
    UNKNOWN_START
}

internal data class FeedTimelineExplanation(
    val filter: FeedTimelineFilter,
    val reason: FeedTimelineReason,
    val badge: String,
    val body: String
) {
    init {
        require(filter != FeedTimelineFilter.ALL)
        require(badge.isNotBlank())
        require(body.isNotBlank())
    }
}

internal data class FeedTimelineSummary(
    val totalCount: Int,
    val counts: Map<FeedTimelineFilter, Int>
) {
    init {
        require(totalCount >= 0)
        require(FeedTimelineFilter.ALL !in counts)
        require(counts.values.all { it >= 0 })
        require(counts.values.sum() == totalCount)
    }

    fun count(filter: FeedTimelineFilter): Int {
        return if (filter == FeedTimelineFilter.ALL) {
            totalCount
        } else {
            counts[filter] ?: 0
        }
    }
}

internal fun eventCountText(count: Int): String {
    require(count >= 0)
    val noun = when {
        count % 100 in 11..14 -> "событий"
        count % 10 == 1 -> "событие"
        count % 10 in 2..4 -> "события"
        else -> "событий"
    }
    return "$count $noun"
}

internal object FeedTimelinePolicy {
    private val completedCodes = setOf("FT", "AET", "PEN")

    fun bucket(
        event: SportEvent,
        now: Long,
        zoneId: ZoneId
    ): FeedTimelineFilter {
        require(now >= 0L)
        return when (FeedFocusPolicy.phase(event, now)) {
            FeedEventPhase.LIVE -> FeedTimelineFilter.LIVE
            FeedEventPhase.TERMINAL -> if (
                normalizedStatusCode(event) in completedCodes
            ) {
                FeedTimelineFilter.COMPLETED
            } else {
                FeedTimelineFilter.VERIFY
            }
            FeedEventPhase.INTERRUPTED ->
                FeedTimelineFilter.VERIFY
            FeedEventPhase.UPCOMING,
            FeedEventPhase.UNKNOWN -> bucketByStart(
                event = event,
                now = now,
                zoneId = zoneId
            )
        }
    }

    fun explanation(
        event: SportEvent,
        now: Long,
        zoneId: ZoneId
    ): FeedTimelineExplanation {
        val filter = bucket(event, now, zoneId)
        val code = normalizedStatusCode(event)
        if (filter == FeedTimelineFilter.VERIFY) {
            return verificationExplanation(
                event = event,
                code = code,
                now = now
            )
        }
        return when (filter) {
            FeedTimelineFilter.LIVE -> FeedTimelineExplanation(
                filter = filter,
                reason = FeedTimelineReason.LIVE_STATUS,
                badge = "ИДЁТ СЕЙЧАС",
                body = "Поставщик передал статус «${statusTitle(event, "матч идёт")}». " +
                    "Перед действием сверьте его с официальной трансляцией."
            )
            FeedTimelineFilter.TODAY -> FeedTimelineExplanation(
                filter = filter,
                reason = FeedTimelineReason.TODAY_DATE,
                badge = "СЕГОДНЯ",
                body = "Точный старт попадает в сегодняшний день выбранного часового пояса."
            )
            FeedTimelineFilter.TOMORROW -> FeedTimelineExplanation(
                filter = filter,
                reason = FeedTimelineReason.TOMORROW_DATE,
                badge = "ЗАВТРА",
                body = "Точный старт попадает в следующий календарный день выбранного часового пояса."
            )
            FeedTimelineFilter.LATER -> FeedTimelineExplanation(
                filter = filter,
                reason = FeedTimelineReason.LATER_DATE,
                badge = "ПОЗЖЕ",
                body = "Старт назначен после завтрашнего дня в выбранном часовом поясе."
            )
            FeedTimelineFilter.COMPLETED -> FeedTimelineExplanation(
                filter = filter,
                reason = FeedTimelineReason.FINAL_STATUS,
                badge = "ЗАВЕРШЕНО",
                body = "Поставщик передал финальный статус «${statusTitle(event, "матч завершён")}». " +
                    "Он отделён от текущих и будущих событий."
            )
            FeedTimelineFilter.ALL,
            FeedTimelineFilter.VERIFY -> error(
                "Unexpected timeline filter: $filter"
            )
        }
    }

    fun summary(
        events: List<SportEvent>,
        now: Long,
        zoneId: ZoneId
    ): FeedTimelineSummary {
        val counts = events.groupingBy {
            bucket(it, now, zoneId)
        }.eachCount()
        return FeedTimelineSummary(
            totalCount = events.size,
            counts = counts
        )
    }

    fun filter(
        events: List<SportEvent>,
        selected: FeedTimelineFilter,
        now: Long,
        zoneId: ZoneId
    ): List<SportEvent> {
        if (selected == FeedTimelineFilter.ALL) return events
        return events.filter {
            bucket(it, now, zoneId) == selected
        }
    }

    private fun bucketByStart(
        event: SportEvent,
        now: Long,
        zoneId: ZoneId
    ): FeedTimelineFilter {
        val startAt = event.startAt
            ?: EventStartResolver.resolve(event, now)?.startAt
            ?: return FeedTimelineFilter.VERIFY
        if (startAt < now) return FeedTimelineFilter.VERIFY

        val today = Instant.ofEpochMilli(now)
            .atZone(zoneId)
            .toLocalDate()
        val eventDate = Instant.ofEpochMilli(startAt)
            .atZone(zoneId)
            .toLocalDate()
        return when (eventDate) {
            today -> FeedTimelineFilter.TODAY
            today.plusDays(1L) -> FeedTimelineFilter.TOMORROW
            else -> FeedTimelineFilter.LATER
        }
    }

    private fun verificationExplanation(
        event: SportEvent,
        code: String?,
        now: Long
    ): FeedTimelineExplanation {
        val reason = when (code) {
            "PST" -> FeedTimelineReason.POSTPONED
            "SUSP" -> FeedTimelineReason.SUSPENDED
            "INT" -> FeedTimelineReason.INTERRUPTED
            "CANC" -> FeedTimelineReason.CANCELLED
            "ABD" -> FeedTimelineReason.ABANDONED
            "AWD" -> FeedTimelineReason.AWARDED
            "WO" -> FeedTimelineReason.WALKOVER
            else -> {
                val startAt = event.startAt
                    ?: EventStartResolver.resolve(event, now)?.startAt
                if (startAt == null) {
                    FeedTimelineReason.UNKNOWN_START
                } else {
                    FeedTimelineReason.PAST_WITHOUT_FINAL_STATUS
                }
            }
        }
        val copy = when (reason) {
            FeedTimelineReason.POSTPONED ->
                "ПЕРЕНОС" to "Поставщик пометил событие как перенесённое. Сверьте новую дату в официальном источнике."
            FeedTimelineReason.SUSPENDED ->
                "ПРИОСТАНОВЛЕНО" to "Событие приостановлено. Не считайте его завершённым или будущим до официального обновления."
            FeedTimelineReason.INTERRUPTED ->
                "ПРЕРВАНО" to "Поставщик сообщил о прерывании. Проверьте статус возобновления и правила турнира."
            FeedTimelineReason.CANCELLED ->
                "ОТМЕНЕНО" to "Событие отменено и не считается штатно завершённым. Сверьте официальное решение организатора."
            FeedTimelineReason.ABANDONED ->
                "ПРЕКРАЩЕНО" to "Событие прекращено до штатного финала. Причину и последствия нужно сверить у организатора."
            FeedTimelineReason.AWARDED ->
                "РЕШЕНИЕ ОРГАНИЗАТОРА" to "Результат присуждён решением организатора. Проверьте регламент и официальную публикацию."
            FeedTimelineReason.WALKOVER ->
                "ТЕХНИЧЕСКИЙ ИСХОД" to "Зафиксирован технический исход без обычного завершения матча. Проверьте регламент турнира."
            FeedTimelineReason.PAST_WITHOUT_FINAL_STATUS ->
                "СТАТУС НЕ ОБНОВЛЁН" to "Заявленное время уже прошло, но финальный или live-статус не получен. Нужна ручная сверка."
            FeedTimelineReason.UNKNOWN_START ->
                "НЕТ ТОЧНОГО ВРЕМЕНИ" to "Точный старт отсутствует. Не относите событие к предстоящим до официальной сверки."
            else -> error("Unexpected verification reason: $reason")
        }
        return FeedTimelineExplanation(
            filter = FeedTimelineFilter.VERIFY,
            reason = reason,
            badge = copy.first,
            body = copy.second
        )
    }

    private fun normalizedStatusCode(event: SportEvent): String? {
        return event.providerStatusCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
    }

    private fun statusTitle(
        event: SportEvent,
        fallback: String
    ): String {
        return event.providerStatus
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: fallback
    }
}
