package ru.sportpulse.info

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTimelinePolicyTest {
    private val now = instant("2026-08-10T09:00:00Z")
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test
    fun liveTerminalAndInterruptedStatusesTakePriority() {
        assertEquals(
            FeedTimelineFilter.LIVE,
            FeedTimelinePolicy.bucket(
                event("live", "2H", "2026-08-11T10:00:00Z"),
                now,
                moscow
            )
        )
        assertEquals(
            FeedTimelineFilter.COMPLETED,
            FeedTimelinePolicy.bucket(
                event("finished", "FT", "2026-08-11T10:00:00Z"),
                now,
                moscow
            )
        )
        assertEquals(
            FeedTimelineFilter.VERIFY,
            FeedTimelinePolicy.bucket(
                event("postponed", "PST", "2026-08-11T10:00:00Z"),
                now,
                moscow
            )
        )
    }

    @Test
    fun abnormalTerminalStatusesRequireVerification() {
        listOf("CANC", "ABD", "AWD", "WO").forEach { code ->
            assertEquals(
                code,
                FeedTimelineFilter.VERIFY,
                FeedTimelinePolicy.bucket(
                    event(code.lowercase(), code, "2026-08-11T10:00:00Z"),
                    now,
                    moscow
                )
            )
        }
    }

    @Test
    fun explanationsExposeTheReasonForEverySpecialState() {
        val postponed = FeedTimelinePolicy.explanation(
            event("postponed", "PST", "2026-08-11T10:00:00Z"),
            now,
            moscow
        )
        val cancelled = FeedTimelinePolicy.explanation(
            event("cancelled", "CANC", "2026-08-11T10:00:00Z"),
            now,
            moscow
        )
        val completed = FeedTimelinePolicy.explanation(
            event("completed", "FT", "2026-08-10T08:00:00Z"),
            now,
            moscow
        )
        val stale = FeedTimelinePolicy.explanation(
            event("stale", "NS", "2026-08-10T08:00:00Z"),
            now,
            moscow
        )
        val missing = FeedTimelinePolicy.explanation(
            event("missing", null, null),
            now,
            moscow
        )

        assertEquals(FeedTimelineReason.POSTPONED, postponed.reason)
        assertEquals("ПЕРЕНОС", postponed.badge)
        assertEquals(FeedTimelineReason.CANCELLED, cancelled.reason)
        assertEquals(FeedTimelineFilter.VERIFY, cancelled.filter)
        assertEquals(FeedTimelineReason.FINAL_STATUS, completed.reason)
        assertEquals(FeedTimelineFilter.COMPLETED, completed.filter)
        assertEquals(
            FeedTimelineReason.PAST_WITHOUT_FINAL_STATUS,
            stale.reason
        )
        assertEquals(FeedTimelineReason.UNKNOWN_START, missing.reason)
    }

    @Test
    fun upcomingEventsUseSelectedZoneCalendarDate() {
        val boundary = event(
            "boundary",
            "NS",
            "2026-08-10T21:30:00Z"
        )

        assertEquals(
            FeedTimelineFilter.TOMORROW,
            FeedTimelinePolicy.bucket(
                boundary,
                now,
                ZoneId.of("Europe/Moscow")
            )
        )
        assertEquals(
            FeedTimelineFilter.TODAY,
            FeedTimelinePolicy.bucket(
                boundary,
                now,
                ZoneId.of("Europe/Kaliningrad")
            )
        )
    }

    @Test
    fun futureDatesSplitIntoTodayTomorrowAndLater() {
        val events = listOf(
            event("today", "NS", "2026-08-10T15:00:00Z"),
            event("tomorrow", "NS", "2026-08-11T15:00:00Z"),
            event("later", "NS", "2026-08-12T15:00:00Z")
        )

        assertEquals(
            listOf(
                FeedTimelineFilter.TODAY,
                FeedTimelineFilter.TOMORROW,
                FeedTimelineFilter.LATER
            ),
            events.map {
                FeedTimelinePolicy.bucket(it, now, moscow)
            }
        )
    }

    @Test
    fun staleOrMissingStartRequiresVerification() {
        assertEquals(
            FeedTimelineFilter.VERIFY,
            FeedTimelinePolicy.bucket(
                event("past", "NS", "2026-08-10T08:00:00Z"),
                now,
                moscow
            )
        )
        assertEquals(
            FeedTimelineFilter.VERIFY,
            FeedTimelinePolicy.bucket(
                event("missing", null, null),
                now,
                moscow
            )
        )
    }

    @Test
    fun demoScheduleParticipatesInTimeline() {
        val demo = event("demo", null, null).copy(
            demoSchedule = DemoSchedule(
                dayOfWeek = DayOfWeek.TUESDAY,
                hour = 19,
                minute = 0
            )
        )

        assertEquals(
            FeedTimelineFilter.TOMORROW,
            FeedTimelinePolicy.bucket(demo, now, moscow)
        )
    }

    @Test
    fun summaryAndFilterKeepEveryEventAndOriginalOrder() {
        val events = listOf(
            event("today-1", "NS", "2026-08-10T13:00:00Z"),
            event("finished", "FT", "2026-08-10T08:00:00Z"),
            event("today-2", "NS", "2026-08-10T18:00:00Z"),
            event("unknown", null, null)
        )
        val summary = FeedTimelinePolicy.summary(
            events,
            now,
            moscow
        )

        assertEquals(4, summary.count(FeedTimelineFilter.ALL))
        assertEquals(2, summary.count(FeedTimelineFilter.TODAY))
        assertEquals(1, summary.count(FeedTimelineFilter.COMPLETED))
        assertEquals(1, summary.count(FeedTimelineFilter.VERIFY))
        assertEquals(
            listOf("today-1", "today-2"),
            FeedTimelinePolicy.filter(
                events,
                FeedTimelineFilter.TODAY,
                now,
                moscow
            ).map(SportEvent::id)
        )
    }

    @Test
    fun eventCounterUsesRussianPluralForms() {
        assertEquals("0 событий", eventCountText(0))
        assertEquals("1 событие", eventCountText(1))
        assertEquals("2 события", eventCountText(2))
        assertEquals("4 события", eventCountText(4))
        assertEquals("5 событий", eventCountText(5))
        assertEquals("11 событий", eventCountText(11))
        assertEquals("21 событие", eventCountText(21))
        assertEquals("22 события", eventCountText(22))
        assertEquals("25 событий", eventCountText(25))
    }

    private fun event(
        id: String,
        statusCode: String?,
        start: String?
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
            startAt = start?.let(::instant),
            origin = SportEventOrigin.API_SPORTS,
            providerStatusCode = statusCode
        )
    }

    private fun instant(value: String): Long {
        return Instant.parse(value).toEpochMilli()
    }
}
