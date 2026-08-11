package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryThreadPosterFactoryTest {
    @Test
    fun fileNameIsStableAndFilesystemSafe() {
        val poster = poster(
            eventId = "thread/event with spaces",
            generatedAt = 123_456L
        )

        assertEquals(
            "sport_pulse_thread_thread_event_with_spaces_123456.png",
            StoryThreadPosterFactory.fileName(poster)
        )
    }

    @Test
    fun posterRejectsAnotherEvent() {
        val source = poster()

        assertThrows(IllegalArgumentException::class.java) {
            StoryThreadPosterFactory.create(
                event = source.event.copy(id = "another_event"),
                result = source.result,
                nextMoment = source.nextMoment,
                selectedZone = source.selectedZone,
                generatedAt = source.generatedAt
            )
        }
    }

    @Test
    fun shareTextCarriesQuestionTransitionFingerprintAndDisclaimer() {
        val source = poster()
        val text = StoryThreadPosterFactory.shareText(source)

        assertTrue(text.contains("Хватит ли подтвержденных фактов?"))
        assertTrue(text.contains("Тогда: активно"))
        assertTrue(text.contains("сейчас: внимание"))
        assertTrue(text.contains("нить сдвинулась"))
        assertTrue(text.contains(source.result.shortFingerprint))
        assertTrue(text.contains("не прогноз"))
        assertTrue(text.contains("не ставка"))
        assertTrue(text.contains("не гарантия"))
    }

    @Test
    fun nextMomentUsesSelectedRegionalZone() {
        val source = poster(
            selectedZone = RegionalZone.YEKATERINBURG
        )

        assertEquals(
            "Окно проверки: Нагрузка",
            StoryThreadPosterFactory.momentTitle(
                checkNotNull(source.nextMoment)
            )
        )
        assertTrue(
            StoryThreadPosterFactory.momentTime(
                source,
                checkNotNull(source.nextMoment)
            ).contains("Екатеринбург")
        )
    }

    @Test
    fun terminalStatusHasHonestClosureCopy() {
        val source = poster(currentState = EventStoryChapterState.COMPLETE)

        assertEquals(
            "ВОПРОС ЗАКРЫТ",
            StoryThreadPosterFactory.statusTitle(
                source.result.status
            )
        )
        assertTrue(
            StoryThreadPosterFactory.statusSummary(source.result)
                .contains("завершена")
        )
    }

    private fun poster(
        eventId: String = "thread_event",
        generatedAt: Long = START - 1_000L,
        selectedZone: RegionalZone = RegionalZone.MOSCOW,
        currentState: EventStoryChapterState =
            EventStoryChapterState.ATTENTION
    ): StoryThreadPoster {
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
        val initialStory = story(
            eventId = eventId,
            factsState = EventStoryChapterState.ACTIVE
        )
        val thread = StoryThreadFactory.create(
            story = initialStory,
            chapter = EventStoryChapter.FACTS,
            startedAt = START - 86_400_000L
        )
        val result = StoryThreadEngine.evaluate(
            thread = thread,
            story = story(
                eventId = eventId,
                factsState = currentState
            )
        )
        return StoryThreadPosterFactory.create(
            event = event,
            result = result,
            nextMoment = StoryBeaconMoment(
                kind = StoryBeaconMomentKind.CHECK_WINDOW,
                at = START - 3_600_000L,
                factors = listOf(SignalFactor.LOAD)
            ),
            selectedZone = selectedZone,
            generatedAt = generatedAt
        )
    }

    private fun story(
        eventId: String,
        factsState: EventStoryChapterState
    ): EventStoryResult {
        return EventStoryResult(
            eventId = eventId,
            eventLabel = "Север - Столица",
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = when (chapter) {
                        EventStoryChapter.SOURCE ->
                            EventStoryChapterState.CONTEXT
                        EventStoryChapter.FACTS -> factsState
                        EventStoryChapter.PLAN,
                        EventStoryChapter.DECISION,
                        EventStoryChapter.START ->
                            EventStoryChapterState.ACTIVE
                        EventStoryChapter.REVIEW ->
                            EventStoryChapterState.LOCKED
                    },
                    summary = "Тестовое состояние главы"
                )
            },
            phase = EventStoryPhase.PREPARING,
            action = EventStoryAction.OPEN_FACTS,
            actionFactor = SignalFactor.LOAD,
            startAt = START,
            reviewOpensAt = START +
                EventStoryPolicy.REVIEW_DELAY_MILLIS,
            fingerprint = "a".repeat(64)
        )
    }

    private companion object {
        val START = Instant.parse(
            "2026-08-05T16:30:00Z"
        ).toEpochMilli()
    }
}
