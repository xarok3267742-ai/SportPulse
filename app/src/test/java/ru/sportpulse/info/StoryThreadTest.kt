package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryThreadTest {
    private val startedAt = 1_785_840_000_000L

    @Test
    fun policyRecommendsCurrentAndExcludesTerminalChapters() {
        val story = story(
            states = states().toMutableList().apply {
                this[EventStoryChapter.SOURCE.ordinal] =
                    EventStoryChapterState.COMPLETE
                this[EventStoryChapter.REVIEW.ordinal] =
                    EventStoryChapterState.MISSED
            }
        )

        val choices = StoryThreadPolicy.choices(story)

        assertEquals(EventStoryChapter.FACTS, choices.first())
        assertFalse(choices.contains(EventStoryChapter.SOURCE))
        assertFalse(choices.contains(EventStoryChapter.REVIEW))
    }

    @Test
    fun factoryCapturesChapterStateAndSealsThread() {
        val story = story()
        val thread = StoryThreadFactory.create(
            story = story,
            chapter = EventStoryChapter.FACTS,
            startedAt = startedAt
        )

        assertEquals(story.eventId, thread.eventId)
        assertEquals(
            EventStoryChapterState.ACTIVE,
            thread.initialState
        )
        assertEquals(story.fingerprint, thread.initialStoryFingerprint)
        assertEquals(64, thread.fingerprint.length)
        assertEquals(
            thread.fingerprint,
            StoryThreadCodec.fingerprintFor(thread)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun factoryRejectsAlreadyCompletedChapter() {
        StoryThreadFactory.create(
            story = story(
                states = states().toMutableList().apply {
                    this[EventStoryChapter.FACTS.ordinal] =
                        EventStoryChapterState.COMPLETE
                }
            ),
            chapter = EventStoryChapter.FACTS,
            startedAt = startedAt
        )
    }

    @Test
    fun codecRoundTripPreservesUnicodeEventId() {
        val thread = StoryThreadFactory.create(
            story = story(eventId = "нить|матч:1"),
            chapter = EventStoryChapter.PLAN,
            startedAt = startedAt
        )

        val decoded = StoryThreadCodec.decode(
            StoryThreadCodec.encode(thread)
        )

        assertEquals(thread, decoded)
    }

    @Test
    fun codecRejectsTamperedPayloadFingerprintAndVersion() {
        val encoded = StoryThreadCodec.encode(thread())

        assertNull(
            StoryThreadCodec.decode(
                encoded.replace("FACTS", "PLAN")
            )
        )
        assertNull(
            StoryThreadCodec.decode(
                encoded.dropLast(1) +
                    if (encoded.last() == '0') '1' else '0'
            )
        )
        assertNull(
            StoryThreadCodec.decode(
                encoded.replaceFirst("1|", "2|")
            )
        )
    }

    @Test
    fun unchangedChapterStaysOpenAcrossTechnicalStoryChanges() {
        val thread = thread()
        val first = StoryThreadEngine.evaluate(
            thread,
            story(fingerprint = "b".repeat(64))
        )
        val later = StoryThreadEngine.evaluate(
            thread,
            story(
                phase = EventStoryPhase.READY,
                fingerprint = "c".repeat(64)
            )
        )

        assertEquals(StoryThreadStatus.OPEN, first.status)
        assertEquals(first.fingerprint, later.fingerprint)
    }

    @Test
    fun nonTerminalStateTransitionMovesThread() {
        val result = StoryThreadEngine.evaluate(
            thread(),
            story(
                states = states().toMutableList().apply {
                    this[EventStoryChapter.FACTS.ordinal] =
                        EventStoryChapterState.ATTENTION
                }
            )
        )

        assertEquals(StoryThreadStatus.MOVED, result.status)
        assertEquals(
            EventStoryChapterState.ATTENTION,
            result.currentState
        )
    }

    @Test
    fun completeAndMissedAreTerminalOutcomes() {
        val complete = StoryThreadEngine.evaluate(
            thread(),
            story(
                states = states().toMutableList().apply {
                    this[EventStoryChapter.FACTS.ordinal] =
                        EventStoryChapterState.COMPLETE
                }
            )
        )
        val missed = StoryThreadEngine.evaluate(
            thread(),
            story(
                states = states().toMutableList().apply {
                    this[EventStoryChapter.FACTS.ordinal] =
                        EventStoryChapterState.MISSED
                }
            )
        )

        assertEquals(StoryThreadStatus.RESOLVED, complete.status)
        assertEquals(StoryThreadStatus.MISSED, missed.status)
        assertNotEquals(complete.fingerprint, missed.fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun engineRejectsDifferentEvent() {
        StoryThreadEngine.evaluate(
            thread(),
            story(eventId = "another-event")
        )
    }

    @Test
    fun readResultRequiresThreadOnlyForValidIntegrity() {
        StoryThreadReadResult(StoryThreadIntegrity.EMPTY, null)
        StoryThreadReadResult(StoryThreadIntegrity.TAMPERED, null)
        val valid = StoryThreadReadResult(
            StoryThreadIntegrity.VALID,
            thread()
        )

        assertTrue(valid.thread != null)
    }

    private fun thread(): StoryThread {
        return StoryThreadFactory.create(
            story = story(),
            chapter = EventStoryChapter.FACTS,
            startedAt = startedAt
        )
    }

    private fun story(
        eventId: String = "thread-event",
        states: List<EventStoryChapterState> = states(),
        phase: EventStoryPhase = EventStoryPhase.PREPARING,
        fingerprint: String = "a".repeat(64)
    ): EventStoryResult {
        return EventStoryResult(
            eventId = eventId,
            eventLabel = "Команда А - Команда Б",
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = states[chapter.ordinal],
                    summary = "Состояние главы"
                )
            },
            phase = phase,
            action = EventStoryAction.OPEN_FACTS,
            actionFactor = SignalFactor.LOAD,
            startAt = startedAt + 86_400_000L,
            reviewOpensAt = startedAt + 100_800_000L,
            fingerprint = fingerprint
        )
    }

    private fun states(): List<EventStoryChapterState> {
        return listOf(
            EventStoryChapterState.CONTEXT,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.LOCKED,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.LOCKED
        )
    }
}
