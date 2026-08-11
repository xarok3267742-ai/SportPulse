package ru.sportpulse.info

import java.util.Locale

internal enum class FeedEventPhase {
    LIVE,
    UPCOMING,
    INTERRUPTED,
    UNKNOWN,
    TERMINAL
}

internal object FeedFocusPolicy {
    const val NEARBY_EVENT_LIMIT = 12
    const val MAX_FOCUS_EVENT_LIMIT = 16
    private val liveCodes = setOf(
        "1H",
        "HT",
        "2H",
        "ET",
        "BT",
        "P",
        "LIVE"
    )
    private val interruptedCodes = setOf(
        "PST",
        "SUSP",
        "INT"
    )
    private val terminalCodes = setOf(
        "FT",
        "AET",
        "PEN",
        "CANC",
        "ABD",
        "AWD",
        "WO"
    )

    fun phase(event: SportEvent, now: Long): FeedEventPhase {
        require(now >= 0L)
        val code = event.providerStatusCode
            ?.uppercase(Locale.ROOT)
        return when {
            code in liveCodes -> FeedEventPhase.LIVE
            code in interruptedCodes -> FeedEventPhase.INTERRUPTED
            code in terminalCodes -> FeedEventPhase.TERMINAL
            code == "NS" || code == "TBD" ->
                FeedEventPhase.UPCOMING
            event.startAt != null && event.startAt >= now ->
                FeedEventPhase.UPCOMING
            else -> FeedEventPhase.UNKNOWN
        }
    }

    fun actionable(
        events: List<SportEvent>,
        now: Long
    ): List<SportEvent> {
        val nonTerminal = events.filter {
            phase(it, now) != FeedEventPhase.TERMINAL
        }
        return nonTerminal.ifEmpty { events }
    }

    fun order(
        events: List<SportEvent>,
        now: Long
    ): List<SportEvent> {
        val catalogOrder = events.mapIndexed { index, event ->
            event.id to index
        }.toMap()
        return events.sortedWith(
            compareBy<SportEvent> {
                phase(it, now).ordinal
            }.thenBy {
                it.startAt ?: Long.MAX_VALUE
            }.thenBy {
                requireNotNull(catalogOrder[it.id])
            }
        )
    }

    fun focusScope(
        events: List<SportEvent>,
        bookmarkedIds: Set<String>,
        now: Long
    ): List<SportEvent> {
        val ordered = order(
            events = actionable(events, now),
            now = now
        )
        val nearby = ordered.take(NEARBY_EVENT_LIMIT)
        val saved = ordered.filter { it.id in bookmarkedIds }
        return (nearby + saved)
            .distinctBy(SportEvent::id)
            .take(MAX_FOCUS_EVENT_LIMIT)
    }
}
