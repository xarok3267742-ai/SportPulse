package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryThreadMapTest {
    private val startAt = 1_785_840_000_000L

    @Test
    fun emptyInputProducesSealedEmptyMap() {
        val result = StoryThreadMapEngine.evaluate(emptyList())

        assertTrue(result.entries.isEmpty())
        assertEquals(StoryThreadMapState.EMPTY, result.leadingState)
        assertEquals(64, result.fingerprint.length)
    }

    @Test
    fun transparentStateOrderLeadsWithIntegrityAndLinkIssues() {
        val result = StoryThreadMapEngine.evaluate(
            listOf(
                listed("resolved", 5, StoryThreadStatus.RESOLVED),
                listed("open", 4, StoryThreadStatus.OPEN),
                listed("missed", 3, StoryThreadStatus.MISSED),
                listed("moved", 2, StoryThreadStatus.MOVED),
                detached("detached"),
                tampered("tampered")
            )
        )

        assertEquals(
            listOf(
                StoryThreadMapState.TAMPERED,
                StoryThreadMapState.DETACHED,
                StoryThreadMapState.MOVED,
                StoryThreadMapState.MISSED,
                StoryThreadMapState.OPEN,
                StoryThreadMapState.RESOLVED
            ),
            result.entries.map(StoryThreadMapEntry::state)
        )
        assertEquals(StoryThreadMapState.TAMPERED, result.leadingState)
        assertEquals(1, result.tamperedCount)
        assertEquals(1, result.detachedCount)
        assertEquals(1, result.movedCount)
        assertEquals(1, result.missedCount)
        assertEquals(1, result.openCount)
        assertEquals(1, result.resolvedCount)
        assertEquals(2, result.outsideCatalogCount)
        assertEquals(4, result.visibleEntries.size)
    }

    @Test
    fun openThreadsUseNearestRelevantMomentBeforeCatalogOrder() {
        val result = StoryThreadMapEngine.evaluate(
            listOf(
                listed(
                    eventId = "no-moment",
                    order = 0,
                    status = StoryThreadStatus.OPEN
                ),
                listed(
                    eventId = "later",
                    order = 1,
                    status = StoryThreadStatus.OPEN,
                    nextAt = startAt + 20_000L
                ),
                listed(
                    eventId = "earlier",
                    order = 2,
                    status = StoryThreadStatus.OPEN,
                    nextAt = startAt + 10_000L
                )
            )
        )

        assertEquals(
            listOf("earlier", "later", "no-moment"),
            result.entries.map(StoryThreadMapEntry::eventId)
        )
    }

    @Test
    fun detachedThreadKeepsQuestionButDoesNotInventCurrentStatus() {
        val result = StoryThreadMapEngine.evaluate(
            listOf(detached("removed-event"))
        )
        val entry = result.entries.single()

        assertEquals(StoryThreadMapState.DETACHED, entry.state)
        assertEquals(EventStoryChapter.FACTS, entry.thread?.chapter)
        assertNull(entry.result)
        assertNull(entry.nextMoment)
        assertTrue(!entry.presentInCatalog)
    }

    @Test
    fun fingerprintIsInputOrderIndependentAndTracksMomentChanges() {
        val first = listed(
            eventId = "first",
            order = 0,
            status = StoryThreadStatus.OPEN,
            nextAt = startAt + 10_000L
        )
        val second = listed(
            eventId = "second",
            order = 1,
            status = StoryThreadStatus.MOVED,
            nextAt = startAt + 20_000L
        )
        val original = StoryThreadMapEngine.evaluate(
            listOf(first, second)
        )
        val reordered = StoryThreadMapEngine.evaluate(
            listOf(second, first)
        )
        val changed = StoryThreadMapEngine.evaluate(
            listOf(
                first.copy(
                    nextMoment = timedMoment(startAt + 30_000L)
                ),
                second
            )
        )

        assertEquals(original.fingerprint, reordered.fingerprint)
        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateEventIdsAreRejected() {
        StoryThreadMapEngine.evaluate(
            listOf(
                listed("same", 0, StoryThreadStatus.OPEN),
                listed("same", 1, StoryThreadStatus.OPEN)
            )
        )
    }

    @Test
    fun relevantMomentUsesChapterContractInsteadOfListOrder() {
        val expiry = StoryBeaconMoment(
            kind = StoryBeaconMomentKind.FACT_EXPIRY,
            at = startAt + 10_000L,
            factors = listOf(SignalFactor.LOAD)
        )
        val check = StoryBeaconMoment(
            kind = StoryBeaconMomentKind.CHECK_WINDOW,
            at = startAt + 20_000L,
            factors = listOf(SignalFactor.LINEUP)
        )
        val review = StoryBeaconMoment(
            kind = StoryBeaconMomentKind.REVIEW_OPEN,
            at = startAt + 40_000L
        )
        val beacon = beacon(listOf(expiry, check, review))

        assertEquals(
            check,
            StoryThreadPolicy.relevantMoment(
                EventStoryChapter.FACTS,
                beacon
            )
        )
        assertEquals(
            review,
            StoryThreadPolicy.relevantMoment(
                EventStoryChapter.REVIEW,
                beacon
            )
        )
    }

    private fun listed(
        eventId: String,
        order: Int,
        status: StoryThreadStatus,
        nextAt: Long? = null
    ): StoryThreadMapCandidate {
        val thread = thread(eventId)
        val result = result(thread, status)
        return StoryThreadMapCandidate(
            eventId = eventId,
            match = "Команда $eventId - Соперник",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = order,
            read = StoryThreadReadResult(
                StoryThreadIntegrity.VALID,
                thread
            ),
            result = result,
            nextMoment = nextAt?.let(::timedMoment)
        )
    }

    private fun detached(eventId: String): StoryThreadMapCandidate {
        val thread = thread(eventId)
        return StoryThreadMapCandidate(
            eventId = eventId,
            match = null,
            sport = null,
            region = null,
            catalogOrder = null,
            read = StoryThreadReadResult(
                StoryThreadIntegrity.VALID,
                thread
            ),
            result = null,
            nextMoment = null
        )
    }

    private fun tampered(eventId: String): StoryThreadMapCandidate {
        return StoryThreadMapCandidate(
            eventId = eventId,
            match = null,
            sport = null,
            region = null,
            catalogOrder = null,
            read = StoryThreadReadResult(
                StoryThreadIntegrity.TAMPERED,
                null
            ),
            result = null,
            nextMoment = null
        )
    }

    private fun result(
        thread: StoryThread,
        status: StoryThreadStatus
    ): StoryThreadResult {
        val current = when (status) {
            StoryThreadStatus.OPEN -> thread.initialState
            StoryThreadStatus.MOVED ->
                EventStoryChapterState.ATTENTION
            StoryThreadStatus.RESOLVED ->
                EventStoryChapterState.COMPLETE
            StoryThreadStatus.MISSED ->
                EventStoryChapterState.MISSED
        }
        return StoryThreadEngine.evaluate(
            thread = thread,
            story = story(thread.eventId, current)
        )
    }

    private fun thread(eventId: String): StoryThread {
        return StoryThreadFactory.create(
            story = story(
                eventId,
                EventStoryChapterState.ACTIVE
            ),
            chapter = EventStoryChapter.FACTS,
            startedAt = startAt
        )
    }

    private fun story(
        eventId: String,
        factState: EventStoryChapterState
    ): EventStoryResult {
        val states = listOf(
            EventStoryChapterState.CONTEXT,
            factState,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.LOCKED,
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.LOCKED
        )
        return EventStoryResult(
            eventId = eventId,
            eventLabel = "Команда - Соперник",
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = states[chapter.ordinal],
                    summary = "Состояние"
                )
            },
            phase = EventStoryPhase.PREPARING,
            action = EventStoryAction.OPEN_FACTS,
            actionFactor = SignalFactor.LOAD,
            startAt = startAt + 86_400_000L,
            reviewOpensAt = startAt + 100_800_000L,
            fingerprint = "a".repeat(64)
        )
    }

    private fun timedMoment(at: Long): StoryBeaconMoment {
        return StoryBeaconMoment(
            kind = StoryBeaconMomentKind.START,
            at = at
        )
    }

    private fun beacon(
        moments: List<StoryBeaconMoment>
    ): StoryBeaconResult {
        return StoryBeaconResult(
            eventId = "beacon-event",
            evaluatedAtMinute = startAt / 60_000L,
            state = StoryBeaconState.WATCHING,
            moments = moments,
            fingerprint = "b".repeat(64)
        )
    }
}
