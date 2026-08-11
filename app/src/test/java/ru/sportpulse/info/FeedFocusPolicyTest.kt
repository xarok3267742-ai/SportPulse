package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedFocusPolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun activePhasesPrecedeTerminalEvents() {
        val events = listOf(
            event("finished", "FT", now - 2_000L),
            event("postponed", "PST", now + 3_000L),
            event("future", "NS", now + 2_000L),
            event("live", "2H", now - 1_000L),
            event("unknown", null, now - 3_000L)
        )

        assertEquals(
            listOf(
                "live",
                "future",
                "postponed",
                "unknown",
                "finished"
            ),
            FeedFocusPolicy.order(events, now).map(SportEvent::id)
        )
    }

    @Test
    fun focusCandidatesExcludeTerminalEventsWhenPossible() {
        val events = listOf(
            event("finished", "FT", now - 2_000L),
            event("future", "NS", now + 2_000L),
            event("cancelled", "CANC", now + 4_000L)
        )

        assertEquals(
            listOf("future"),
            FeedFocusPolicy.actionable(events, now)
                .map(SportEvent::id)
        )
    }

    @Test
    fun allTerminalScopeStillProducesAUsableFocus() {
        val events = listOf(
            event("finished", "FT", now - 2_000L),
            event("cancelled", "CANC", now - 1_000L)
        )

        assertEquals(
            events,
            FeedFocusPolicy.actionable(events, now)
        )
    }

    @Test
    fun unknownFutureEventIsTreatedAsUpcoming() {
        assertEquals(
            FeedEventPhase.UPCOMING,
            FeedFocusPolicy.phase(
                event("future", null, now + 1_000L),
                now
            )
        )
    }

    @Test
    fun focusScopeKeepsNearbyEventsAndAStoredOutlier() {
        val events = List(20) { index ->
            event(
                id = "event-$index",
                statusCode = "NS",
                startAt = now + index * 1_000L
            )
        }

        val scope = FeedFocusPolicy.focusScope(
            events = events,
            bookmarkedIds = setOf("event-18"),
            now = now
        )

        assertEquals(13, scope.size)
        assertEquals("event-0", scope.first().id)
        assertEquals("event-11", scope[11].id)
        assertEquals("event-18", scope.last().id)
    }

    private fun event(
        id: String,
        statusCode: String?,
        startAt: Long
    ): SportEvent {
        return SportEvent(
            id = id,
            sport = "Футбол",
            tournament = "Турнир",
            region = "Россия",
            match = "Команда $id - Соперник $id",
            time = "API",
            focus = "Факты",
            note = "Примечание",
            tags = listOf("API"),
            imageRes = 1,
            seedAssessment = SignalAssessment(
                List(SignalFactor.values().size) { 0 }
            ),
            startAt = startAt,
            origin = SportEventOrigin.API_SPORTS,
            providerStatusCode = statusCode
        )
    }
}
