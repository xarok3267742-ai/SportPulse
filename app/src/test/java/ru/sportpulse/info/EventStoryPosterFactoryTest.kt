package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStoryPosterFactoryTest {
    @Test
    fun fileNameIsStableAndFilesystemSafe() {
        val poster = poster(
            eventId = "story/event with spaces",
            generatedAt = 123_456L
        )

        assertEquals(
            "sport_pulse_story_story_event_with_spaces_123456.png",
            EventStoryPosterFactory.fileName(poster)
        )
    }

    @Test
    fun posterRejectsAnotherEvent() {
        val source = poster()

        assertThrows(IllegalArgumentException::class.java) {
            EventStoryPosterFactory.create(
                event = source.event.copy(id = "another_event"),
                story = source.story,
                selectedZone = source.selectedZone,
                generatedAt = source.generatedAt
            )
        }
    }

    @Test
    fun shareTextCarriesChapterActionFingerprintAndDisclaimer() {
        val source = poster()
        val text = EventStoryPosterFactory.shareText(source)

        assertTrue(text.contains("Глава 2 из 6"))
        assertTrue(text.contains("Нагрузка"))
        assertTrue(text.contains(source.story.shortFingerprint))
        assertTrue(text.contains("не прогноз"))
        assertTrue(text.contains("не ставка"))
        assertTrue(text.contains("не гарантия"))
    }

    @Test
    fun actionNamesTheExactEvidenceFactor() {
        val title = EventStoryPosterFactory.nextStepTitle(
            poster().story
        )

        assertEquals("Проверить фактор: Нагрузка", title)
    }

    @Test
    fun scheduleUsesSelectedRegionalZone() {
        val source = poster(selectedZone = RegionalZone.YEKATERINBURG)

        assertTrue(
            EventStoryPosterFactory.startTitle(source)
                .contains("Екатеринбург")
        )
        assertTrue(
            EventStoryPosterFactory.reviewTitle(source)
                .contains("Екатеринбург")
        )
    }

    private fun poster(
        eventId: String = "story_event",
        generatedAt: Long = START - 1_000L,
        selectedZone: RegionalZone = RegionalZone.MOSCOW
    ): EventStoryPoster {
        val event = SportEvent(
            id = eventId,
            sport = "Футбол",
            tournament = "Тестовая лига",
            region = "Россия",
            match = "Север - Столица",
            time = "Тест",
            focus = "Факты",
            note = "Тестовое событие",
            tags = emptyList(),
            imageRes = 0,
            seedAssessment = SignalAssessment(List(5) { 60 }),
            startAt = START
        )
        val chapters = EventStoryChapter.values().map { chapter ->
            EventStoryChapterResult(
                chapter = chapter,
                state = when (chapter) {
                    EventStoryChapter.SOURCE ->
                        EventStoryChapterState.CONTEXT
                    EventStoryChapter.FACTS,
                    EventStoryChapter.PLAN,
                    EventStoryChapter.DECISION,
                    EventStoryChapter.START ->
                        EventStoryChapterState.ACTIVE
                    EventStoryChapter.REVIEW ->
                        EventStoryChapterState.LOCKED
                },
                summary = when (chapter) {
                    EventStoryChapter.FACTS ->
                        "До старта нужно повторить: 3 из 5."
                    else -> "Тестовое состояние главы."
                }
            )
        }
        val story = EventStoryResult(
            eventId = event.id,
            eventLabel = event.match,
            sourceState = EventStorySourceState.DEMO,
            chapters = chapters,
            phase = EventStoryPhase.PREPARING,
            action = EventStoryAction.OPEN_FACTS,
            actionFactor = SignalFactor.LOAD,
            startAt = START,
            reviewOpensAt = START +
                EventStoryPolicy.REVIEW_DELAY_MILLIS,
            fingerprint = "a".repeat(64)
        )
        return EventStoryPosterFactory.create(
            event = event,
            story = story,
            selectedZone = selectedZone,
            generatedAt = generatedAt
        )
    }

    private companion object {
        val START = Instant.parse(
            "2026-08-05T16:30:00Z"
        ).toEpochMilli()
    }
}
