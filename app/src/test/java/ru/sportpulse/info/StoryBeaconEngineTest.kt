package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBeaconEngineTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-04T08:00:00Z"
    ).toEpochMilli()

    @Test
    fun missingTimelineKeepsOnlyTheCurrentSourceAction() {
        val result = evaluate(
            story = story(
                startAt = null,
                reviewAt = null,
                phase = EventStoryPhase.INCOMPLETE,
                action = EventStoryAction.OPEN_SOURCE
            )
        )

        assertEquals(StoryBeaconState.NO_TIMELINE, result.state)
        assertEquals(1, result.moments.size)
        assertEquals(
            StoryBeaconMomentKind.ACTION_NOW,
            result.primaryMoment?.kind
        )
        assertEquals(
            EventStoryAction.OPEN_SOURCE,
            result.primaryMoment?.action
        )
        assertNull(result.primaryMoment?.at)
    }

    @Test
    fun preparingStoryBuildsFiveMeaningfulMomentsInOrder() {
        val startAt = now + 8L * hour
        val reviewAt = startAt + 4L * hour
        val result = evaluate(
            story = story(
                startAt = startAt,
                reviewAt = reviewAt,
                phase = EventStoryPhase.PREPARING,
                action = EventStoryAction.OPEN_FACTS,
                actionFactor = SignalFactor.LOAD
            ),
            slots = listOf(
                slot(
                    at = now + hour,
                    SignalFactor.LOAD
                )
            ),
            transitions = listOf(
                StoryBeaconFactorTransition(
                    factor = SignalFactor.LINEUP,
                    at = now + 2L * hour
                )
            )
        )

        assertEquals(StoryBeaconState.ACTION_NOW, result.state)
        assertEquals(
            listOf(
                StoryBeaconMomentKind.ACTION_NOW,
                StoryBeaconMomentKind.CHECK_WINDOW,
                StoryBeaconMomentKind.FACT_EXPIRY,
                StoryBeaconMomentKind.START,
                StoryBeaconMomentKind.REVIEW_OPEN
            ),
            result.moments.map { it.kind }
        )
        assertEquals(
            listOf(SignalFactor.LOAD),
            result.moments[1].factors
        )
        assertEquals(4, result.timedCount)
    }

    @Test
    fun onlyTheNearestFutureCheckAndTransitionAreShown() {
        val startAt = now + 12L * hour
        val result = evaluate(
            story = story(
                startAt = startAt,
                reviewAt = startAt + 4L * hour,
                phase = EventStoryPhase.READY,
                action = EventStoryAction.NONE
            ),
            slots = listOf(
                slot(now + hour, SignalFactor.LOAD),
                slot(now + 3L * hour, SignalFactor.LINEUP)
            ),
            transitions = listOf(
                StoryBeaconFactorTransition(
                    SignalFactor.CONTEXT,
                    now + 4L * hour
                ),
                StoryBeaconFactorTransition(
                    SignalFactor.SOURCES,
                    now + 2L * hour
                )
            )
        )

        assertEquals(StoryBeaconState.WATCHING, result.state)
        assertEquals(
            listOf(
                StoryBeaconMomentKind.CHECK_WINDOW,
                StoryBeaconMomentKind.FACT_EXPIRY,
                StoryBeaconMomentKind.START,
                StoryBeaconMomentKind.REVIEW_OPEN
            ),
            result.moments.map { it.kind }
        )
        assertEquals(now + hour, result.moments[0].at)
        assertEquals(now + 2L * hour, result.moments[1].at)
    }

    @Test
    fun factorsSharingTheFirstTransitionAreGrouped() {
        val startAt = now + 8L * hour
        val transitionAt = now + 2L * hour
        val result = evaluate(
            story = story(
                startAt = startAt,
                reviewAt = startAt + 4L * hour,
                phase = EventStoryPhase.READY,
                action = EventStoryAction.NONE
            ),
            transitions = listOf(
                StoryBeaconFactorTransition(
                    SignalFactor.FORM,
                    transitionAt
                ),
                StoryBeaconFactorTransition(
                    SignalFactor.LOAD,
                    transitionAt
                )
            )
        )

        assertEquals(
            listOf(SignalFactor.FORM, SignalFactor.LOAD),
            result.moments.first().factors
        )
    }

    @Test
    fun activeEventShowsOnlyTheReviewBoundary() {
        val startAt = now - hour
        val reviewAt = now + 3L * hour
        val result = evaluate(
            story = story(
                startAt = startAt,
                reviewAt = reviewAt,
                phase = EventStoryPhase.IN_PROGRESS,
                action = EventStoryAction.NONE
            )
        )

        assertEquals(StoryBeaconState.EVENT_ACTIVE, result.state)
        assertEquals(1, result.moments.size)
        assertEquals(
            StoryBeaconMomentKind.REVIEW_OPEN,
            result.primaryMoment?.kind
        )
        assertEquals(reviewAt, result.primaryMoment?.at)
    }

    @Test
    fun reviewDueBecomesAnImmediateMoment() {
        val result = evaluate(
            story = story(
                startAt = now - 5L * hour,
                reviewAt = now - hour,
                phase = EventStoryPhase.REVIEW_DUE,
                action = EventStoryAction.OPEN_REVIEW
            )
        )

        assertEquals(StoryBeaconState.REVIEW_DUE, result.state)
        assertEquals(
            StoryBeaconMomentKind.ACTION_NOW,
            result.primaryMoment?.kind
        )
        assertEquals(
            EventStoryAction.OPEN_REVIEW,
            result.primaryMoment?.action
        )
    }

    @Test
    fun completedStoryHasOneClosedMoment() {
        val result = evaluate(
            story = story(
                startAt = now - 8L * hour,
                reviewAt = now - 4L * hour,
                phase = EventStoryPhase.COMPLETE,
                action = EventStoryAction.NONE
            )
        )

        assertEquals(StoryBeaconState.COMPLETE, result.state)
        assertEquals(
            listOf(StoryBeaconMomentKind.COMPLETE),
            result.moments.map { it.kind }
        )
    }

    @Test
    fun missedReviewHasNoInventedFutureMoment() {
        val result = evaluate(
            story = story(
                startAt = now - 8L * hour,
                reviewAt = now - 4L * hour,
                phase = EventStoryPhase.INCOMPLETE,
                action = EventStoryAction.NONE
            )
        )

        assertEquals(StoryBeaconState.INCOMPLETE, result.state)
        assertTrue(result.moments.isEmpty())
    }

    @Test
    fun fingerprintIsStableInsideMinuteAndChangesWithSchedule() {
        val startAt = now + 8L * hour
        val story = story(
            startAt = startAt,
            reviewAt = startAt + 4L * hour,
            phase = EventStoryPhase.READY,
            action = EventStoryAction.NONE
        )
        val first = evaluate(
            story = story,
            slots = listOf(
                slot(now + hour, SignalFactor.LOAD)
            )
        )
        val sameMinute = StoryBeaconEngine.evaluate(
            StoryBeaconInput(
                story = story,
                checkSlots = listOf(
                    slot(now + hour, SignalFactor.LOAD)
                ),
                factorTransitions = emptyList(),
                now = now + 30_000L
            )
        )
        val changed = evaluate(
            story = story,
            slots = listOf(
                slot(now + hour + minute, SignalFactor.LOAD)
            )
        )

        assertEquals(first.fingerprint, sameMinute.fingerprint)
        assertNotEquals(first.fingerprint, changed.fingerprint)
        assertTrue(Regex("[0-9a-f]{64}").matches(first.fingerprint))
    }

    private fun evaluate(
        story: EventStoryResult,
        slots: List<PreflightSlot> = emptyList(),
        transitions: List<StoryBeaconFactorTransition> = emptyList()
    ): StoryBeaconResult {
        return StoryBeaconEngine.evaluate(
            StoryBeaconInput(
                story = story,
                checkSlots = slots,
                factorTransitions = transitions,
                now = now
            )
        )
    }

    private fun slot(
        at: Long,
        vararg factors: SignalFactor
    ): PreflightSlot {
        return PreflightSlot(
            scheduledAt = at,
            factors = factors.toList().sortedBy { it.ordinal },
            immediate = at == now
        )
    }

    private fun story(
        startAt: Long?,
        reviewAt: Long?,
        phase: EventStoryPhase,
        action: EventStoryAction,
        actionFactor: SignalFactor? = null
    ): EventStoryResult {
        return EventStoryResult(
            eventId = "beacon-event",
            eventLabel = "Команда А - Команда Б",
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = EventStoryChapterState.ACTIVE,
                    summary = "Тестовая глава"
                )
            },
            phase = phase,
            action = action,
            actionFactor = actionFactor,
            startAt = startAt,
            reviewOpensAt = reviewAt,
            fingerprint = "a".repeat(64)
        )
    }
}
