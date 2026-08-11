package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryCheckpointTest {
    private val now = Instant.parse(
        "2026-08-04T09:00:00Z"
    ).toEpochMilli()
    private val startAt = now + 8L * FreshnessPolicy.HOUR_MILLIS
    private val reviewAt = startAt + EventStoryPolicy.REVIEW_DELAY_MILLIS

    @Test
    fun factoryCapturesStoryAndBeaconAndSealsFingerprint() {
        val story = story()
        val beacon = beacon()
        val checkpoint = StoryCheckpointFactory.create(
            story = story,
            beacon = beacon,
            savedAt = now
        )

        assertEquals(story.eventId, checkpoint.eventId)
        assertEquals(story.fingerprint, checkpoint.storyFingerprint)
        assertEquals(beacon.moments, checkpoint.beaconMoments)
        assertEquals(64, checkpoint.fingerprint.length)
        assertEquals(
            checkpoint.fingerprint,
            StoryCheckpointCodec.fingerprintFor(checkpoint)
        )
    }

    @Test
    fun codecRoundTripPreservesUnicodeAndMoments() {
        val checkpoint = StoryCheckpointFactory.create(
            story = story(label = "Зенит | Динамо: финал"),
            beacon = beacon(),
            savedAt = now
        )

        val decoded = StoryCheckpointCodec.decode(
            StoryCheckpointCodec.encode(checkpoint)
        )

        assertEquals(checkpoint, decoded)
    }

    @Test
    fun codecRejectsTamperedPayloadAndFingerprint() {
        val checkpoint = StoryCheckpointFactory.create(
            story = story(),
            beacon = beacon(),
            savedAt = now
        )
        val encoded = StoryCheckpointCodec.encode(checkpoint)

        assertNull(
            StoryCheckpointCodec.decode(
                encoded.replace("PREPARING", "READY")
            )
        )
        assertNull(
            StoryCheckpointCodec.decode(
                encoded.dropLast(1) +
                    if (encoded.last() == '0') '1' else '0'
            )
        )
    }

    @Test
    fun unsupportedVersionFailsClosed() {
        val encoded = StoryCheckpointCodec.encode(
            StoryCheckpointFactory.create(
                story = story(),
                beacon = beacon(),
                savedAt = now
            )
        )

        assertNull(
            StoryCheckpointCodec.decode(
                encoded.replaceFirst("1|", "2|")
            )
        )
    }

    @Test
    fun changingOnlyTechnicalFingerprintsDoesNotCreateAChange() {
        val baseline = StoryCheckpointFactory.create(
            story = story(fingerprint = "a".repeat(64)),
            beacon = beacon(fingerprint = "b".repeat(64)),
            savedAt = now
        )
        val current = StoryCheckpointFactory.create(
            story = story(fingerprint = "c".repeat(64)),
            beacon = beacon(fingerprint = "d".repeat(64)),
            savedAt = now + 60_000L
        )

        val comparison = StoryCheckpointEngine.compare(
            baseline,
            current
        )
        val laterComparison = StoryCheckpointEngine.compare(
            baseline,
            StoryCheckpointFactory.create(
                story = story(fingerprint = "e".repeat(64)),
                beacon = beacon(fingerprint = "f".repeat(64)),
                savedAt = now + 120_000L
            )
        )

        assertFalse(comparison.hasChanges)
        assertEquals(0, comparison.changeCount)
        assertEquals(
            comparison.fingerprint,
            laterComparison.fingerprint
        )
    }

    @Test
    fun chapterAndRouteChangesAreReportedExactly() {
        val baseline = checkpoint()
        val changedStory = story(
            phase = EventStoryPhase.READY,
            action = EventStoryAction.OPEN_DECISION,
            chapterStates = chapterStates().toMutableList().apply {
                this[EventStoryChapter.FACTS.ordinal] =
                    EventStoryChapterState.COMPLETE
            }
        )
        val current = StoryCheckpointFactory.create(
            story = changedStory,
            beacon = beacon(),
            savedAt = now + 60_000L
        )

        val comparison = StoryCheckpointEngine.compare(
            baseline,
            current
        )

        assertTrue(comparison.phaseChanged)
        assertTrue(comparison.actionChanged)
        assertEquals(1, comparison.chapterDeltas.size)
        assertEquals(
            EventStoryChapter.FACTS,
            comparison.chapterDeltas.single().chapter
        )
        assertEquals(3, comparison.changeCount)
    }

    @Test
    fun scheduleAndBeaconChangesAreGrouped() {
        val baseline = checkpoint()
        val movedStart = startAt + FreshnessPolicy.HOUR_MILLIS
        val current = StoryCheckpointFactory.create(
            story = story(
                startAt = movedStart,
                reviewAt = movedStart +
                    EventStoryPolicy.REVIEW_DELAY_MILLIS
            ),
            beacon = beacon(
                moments = listOf(
                    actionMoment(),
                    StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.START,
                        at = movedStart
                    )
                ),
                state = StoryBeaconState.WATCHING
            ),
            savedAt = now + 60_000L
        )

        val comparison = StoryCheckpointEngine.compare(
            baseline,
            current
        )

        assertTrue(comparison.startChanged)
        assertTrue(comparison.reviewChanged)
        assertTrue(comparison.beaconStateChanged)
        assertTrue(comparison.beaconMomentsChanged)
        assertEquals(4, comparison.changeCount)
    }

    @Test
    fun labelAndSourceChangesAreVisible() {
        val baseline = checkpoint()
        val current = StoryCheckpointFactory.create(
            story = story(
                label = "Новая афиша",
                source = EventStorySourceState.PRODUCTION_SIGNED
            ),
            beacon = beacon(),
            savedAt = now + 60_000L
        )

        val comparison = StoryCheckpointEngine.compare(
            baseline,
            current
        )

        assertTrue(comparison.labelChanged)
        assertTrue(comparison.sourceChanged)
        assertEquals(2, comparison.changeCount)
    }

    @Test
    fun comparisonFingerprintIsDeterministicAndBindsCurrentState() {
        val baseline = checkpoint()
        val sameCurrent = StoryCheckpointFactory.create(
            story = story(),
            beacon = beacon(),
            savedAt = now + 60_000L
        )
        val first = StoryCheckpointEngine.compare(
            baseline,
            sameCurrent
        )
        val same = StoryCheckpointEngine.compare(
            baseline,
            sameCurrent
        )
        val changed = StoryCheckpointEngine.compare(
            baseline,
            StoryCheckpointFactory.create(
                story = story(label = "Изменено"),
                beacon = beacon(),
                savedAt = now + 60_000L
            )
        )

        assertEquals(first.fingerprint, same.fingerprint)
        assertNotEquals(first.fingerprint, changed.fingerprint)
        assertTrue(Regex("[0-9a-f]{64}").matches(first.fingerprint))
    }

    private fun checkpoint(): StoryCheckpoint {
        return StoryCheckpointFactory.create(
            story = story(),
            beacon = beacon(),
            savedAt = now
        )
    }

    private fun story(
        label: String = "Команда А - Команда Б",
        source: EventStorySourceState = EventStorySourceState.DEMO,
        phase: EventStoryPhase = EventStoryPhase.PREPARING,
        action: EventStoryAction = EventStoryAction.OPEN_FACTS,
        chapterStates: List<EventStoryChapterState> = chapterStates(),
        startAt: Long = this.startAt,
        reviewAt: Long = this.reviewAt,
        fingerprint: String = "a".repeat(64)
    ): EventStoryResult {
        return EventStoryResult(
            eventId = "checkpoint-event",
            eventLabel = label,
            sourceState = source,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = chapterStates[chapter.ordinal],
                    summary = "Тестовая глава"
                )
            },
            phase = phase,
            action = action,
            actionFactor = if (
                action == EventStoryAction.OPEN_FACTS
            ) {
                SignalFactor.LOAD
            } else {
                null
            },
            startAt = startAt,
            reviewOpensAt = reviewAt,
            fingerprint = fingerprint
        )
    }

    private fun beacon(
        state: StoryBeaconState = StoryBeaconState.ACTION_NOW,
        moments: List<StoryBeaconMoment> = listOf(
            actionMoment(),
            StoryBeaconMoment(
                kind = StoryBeaconMomentKind.START,
                at = startAt
            ),
            StoryBeaconMoment(
                kind = StoryBeaconMomentKind.REVIEW_OPEN,
                at = reviewAt
            )
        ),
        fingerprint: String = "b".repeat(64)
    ): StoryBeaconResult {
        return StoryBeaconResult(
            eventId = "checkpoint-event",
            evaluatedAtMinute = now / 60_000L,
            state = state,
            moments = moments,
            fingerprint = fingerprint
        )
    }

    private fun actionMoment(): StoryBeaconMoment {
        return StoryBeaconMoment(
            kind = StoryBeaconMomentKind.ACTION_NOW,
            at = null,
            factors = listOf(SignalFactor.LOAD),
            action = EventStoryAction.OPEN_FACTS
        )
    }

    private fun chapterStates(): List<EventStoryChapterState> {
        return listOf(
            EventStoryChapterState.CONTEXT,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.LOCKED
        )
    }
}
