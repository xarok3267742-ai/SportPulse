package ru.sportpulse.info

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchdayBriefingTest {
    private val now = Instant.parse("2026-08-10T09:00:00Z")
        .toEpochMilli()
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun evaluatesTimelineAndSavedEventsFromOneCatalog() {
        val briefing = MatchdayBriefingEngine.evaluate(
            events = listOf(
                event("live", "2H", "2026-08-11T10:00:00Z"),
                event("today", "NS", "2026-08-10T15:00:00Z"),
                event("verify", "PST", "2026-08-11T15:00:00Z")
            ),
            bookmarkedIds = setOf("live", "verify", "missing"),
            now = now,
            zoneId = moscow
        )

        assertEquals(3, briefing.totalCount)
        assertEquals(2, briefing.savedCount)
        assertEquals(1, briefing.liveCount)
        assertEquals(1, briefing.todayCount)
        assertEquals(1, briefing.verifyCount)
    }

    @Test
    fun formatsCompactRussianHeroCopy() {
        val briefing = MatchdayBriefing(
            totalCount = 21,
            savedCount = 4,
            liveCount = 2,
            todayCount = 7,
            verifyCount = 1
        )

        assertEquals(
            "Сейчас 2  •  Сегодня 7  •  Проверить 1",
            briefing.timelineText()
        )
        assertEquals("21 событие • 4 сохранено", briefing.catalogText())
    }

    private fun event(
        id: String,
        statusCode: String?,
        start: String
    ): SportEvent {
        return SportEvent(
            id = id,
            sport = "Футбол",
            tournament = "Турнир",
            region = "Россия",
            match = "Команда $id - Соперник $id",
            time = "По расписанию",
            focus = "Проверить факты",
            note = "Сверить время и статус события.",
            tags = listOf("расписание"),
            imageRes = 1,
            seedAssessment = SignalAssessment(List(5) { 0 }),
            startAt = Instant.parse(start).toEpochMilli(),
            origin = SportEventOrigin.API_SPORTS,
            providerStatusCode = statusCode
        )
    }
}
