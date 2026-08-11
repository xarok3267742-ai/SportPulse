package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDispatchEngineTest {
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = 1_800_000_000_000L

    @Test
    fun stopEventAlwaysLeadsTheGlobalQueue() {
        val stop = candidate(
            id = "stop",
            order = 2,
            command = command(
                eventId = "stop",
                audit = audits(SourceAuditState.INDEPENDENT)
                    .withState(
                        SignalFactor.FORM,
                        SourceAuditState.CONFLICT
                    )
            )
        )
        val attention = candidate(
            id = "attention",
            order = 0,
            bookmarked = true,
            command = command(
                eventId = "attention",
                audit = audits(SourceAuditState.UNAUDITED)
            )
        )
        val active = candidate(
            id = "active",
            order = 1,
            command = command(
                eventId = "active",
                evidence = levels(EvidenceLevel.SINGLE_SOURCE),
                audit = audits(SourceAuditState.UNAUDITED),
                review = CounterReviewAssessment.unchecked()
            )
        )

        val result = EventDispatchEngine.evaluate(
            listOf(active, attention, stop)
        )

        assertEquals(EventDispatchStatus.STOP, result.status)
        assertEquals("stop", result.entries.first().eventId)
        assertEquals(1, result.stopCount)
        assertEquals(1, result.attentionCount)
        assertEquals(1, result.activeCount)
    }

    @Test
    fun earliestDeadlineWinsInsideOnePriorityTier() {
        val oneHour = candidate(
            id = "one-hour",
            order = 1,
            command = command(
                eventId = "one-hour",
                timeline = timelineWith(
                    SignalFactor.LINEUP,
                    now - 5L * hour
                )
            )
        )
        val twoHours = candidate(
            id = "two-hours",
            order = 0,
            bookmarked = true,
            command = command(
                eventId = "two-hours",
                timeline = timelineWith(
                    SignalFactor.SOURCES,
                    now - 10L * hour
                )
            )
        )

        val result = EventDispatchEngine.evaluate(
            listOf(twoHours, oneHour)
        )

        assertEquals("one-hour", result.entries.first().eventId)
        assertEquals(
            now + hour,
            result.entries.first().primaryTask.dueAt
        )
    }

    @Test
    fun publishedTaskKindPrecedesBookmarkTieBreak() {
        val echo = candidate(
            id = "echo",
            order = 1,
            command = command(
                eventId = "echo",
                audit = audits(SourceAuditState.INDEPENDENT)
                    .withState(
                        SignalFactor.FORM,
                        SourceAuditState.SHARED_LINEAGE
                    )
            )
        )
        val unaudited = candidate(
            id = "unaudited",
            order = 0,
            bookmarked = true,
            command = command(
                eventId = "unaudited",
                audit = audits(SourceAuditState.INDEPENDENT)
                    .withState(
                        SignalFactor.FORM,
                        SourceAuditState.UNAUDITED
                    )
            )
        )

        val result = EventDispatchEngine.evaluate(
            listOf(unaudited, echo)
        )

        assertEquals("echo", result.entries.first().eventId)
        assertEquals(
            VerificationCommandKind.SOURCE_ECHO,
            result.entries.first().primaryTask.kinds.first()
        )
    }

    @Test
    fun bookmarkPrecedesInitializationThenCatalogOrder() {
        val plain = activeCandidate(
            id = "plain",
            order = 0,
            initialized = false
        )
        val started = activeCandidate(
            id = "started",
            order = 2,
            initialized = true
        )
        val saved = activeCandidate(
            id = "saved",
            order = 3,
            bookmarked = true,
            initialized = false
        )
        val earlierStarted = activeCandidate(
            id = "earlier-started",
            order = 1,
            initialized = true
        )

        val result = EventDispatchEngine.evaluate(
            listOf(plain, started, saved, earlierStarted)
        )

        assertEquals(
            listOf(
                "saved",
                "earlier-started",
                "started",
                "plain"
            ),
            result.entries.map(EventDispatchEntry::eventId)
        )
    }

    @Test
    fun onlyFirstThreeEntriesAreVisibleWithoutDiscardingTheRest() {
        val candidates = List(6) { index ->
            activeCandidate(
                id = "event-$index",
                order = index,
                bookmarked = index == 4
            )
        }

        val result = EventDispatchEngine.evaluate(candidates)

        assertEquals(6, result.entries.size)
        assertEquals(3, result.visibleEntries.size)
        assertEquals("event-4", result.visibleEntries.first().eventId)
        assertEquals(1, result.bookmarkedCount)
        assertEquals(0, result.initializedCount)
    }

    @Test
    fun stableCommandsProduceStableDispatcherState() {
        val result = EventDispatchEngine.evaluate(
            listOf(
                candidate(
                    id = "stable",
                    order = 0,
                    initialized = true,
                    command = command(eventId = "stable")
                )
            )
        )

        assertEquals(EventDispatchStatus.STABLE, result.status)
        assertEquals(1, result.stableCount)
        assertEquals(
            VerificationCommandPriority.MAINTAIN,
            result.entries.single().primaryTask.priority
        )
    }

    @Test
    fun emptyScopeHasAValidDeterministicFingerprint() {
        val first = EventDispatchEngine.evaluate(emptyList())
        val second = EventDispatchEngine.evaluate(emptyList())

        assertEquals(EventDispatchStatus.EMPTY, first.status)
        assertTrue(first.entries.isEmpty())
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(64, first.fingerprint.length)
    }

    @Test
    fun bookmarkChangeUpdatesFingerprintAndOrder() {
        val firstInput = listOf(
            activeCandidate("first", 0),
            activeCandidate("second", 1)
        )
        val secondInput = listOf(
            firstInput[0],
            firstInput[1].copy(bookmarked = true)
        )

        val first = EventDispatchEngine.evaluate(firstInput)
        val second = EventDispatchEngine.evaluate(secondInput)

        assertEquals("first", first.entries.first().eventId)
        assertEquals("second", second.entries.first().eventId)
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateEventIdsAreRejected() {
        EventDispatchEngine.evaluate(
            listOf(
                activeCandidate("same", 0),
                activeCandidate("same", 1)
            )
        )
    }

    private fun activeCandidate(
        id: String,
        order: Int,
        bookmarked: Boolean = false,
        initialized: Boolean = false
    ): EventDispatchCandidate {
        return candidate(
            id = id,
            order = order,
            bookmarked = bookmarked,
            initialized = initialized,
            command = command(
                eventId = id,
                evidence = levels(EvidenceLevel.SINGLE_SOURCE),
                audit = audits(SourceAuditState.UNAUDITED),
                review = CounterReviewAssessment.unchecked()
            )
        )
    }

    private fun candidate(
        id: String,
        order: Int,
        bookmarked: Boolean = false,
        initialized: Boolean = false,
        command: VerificationCommandResult
    ): EventDispatchCandidate {
        return EventDispatchCandidate(
            eventId = id,
            sport = "Футбол",
            match = "Событие $id",
            region = "Россия",
            bookmarked = bookmarked,
            initialized = initialized,
            catalogOrder = order,
            command = command
        )
    }

    private fun command(
        eventId: String,
        assessment: SignalAssessment = assessment(86),
        evidence: EvidenceAssessment = levels(EvidenceLevel.QUORUM),
        audit: SourceAuditAssessment =
            audits(SourceAuditState.INDEPENDENT),
        timeline: EvidenceTimeline = timeline(now),
        review: CounterReviewAssessment =
            CounterReviewAssessment.cleared()
    ): VerificationCommandResult {
        return VerificationCommandEngine.evaluate(
            input = VerificationCommandInput(
                eventId = eventId,
                sport = "Футбол",
                assessment = assessment,
                claimedEvidence = evidence,
                sourceAudit = audit,
                timeline = timeline,
                counterReview = review
            ),
            now = now
        )
    }

    private fun assessment(value: Int): SignalAssessment {
        return SignalAssessment(List(5) { value })
    }

    private fun levels(level: EvidenceLevel): EvidenceAssessment {
        return EvidenceAssessment(List(5) { level })
    }

    private fun audits(
        state: SourceAuditState
    ): SourceAuditAssessment {
        return SourceAuditAssessment(List(5) { state })
    }

    private fun timeline(timestamp: Long): EvidenceTimeline {
        return EvidenceTimeline(List(5) { timestamp })
    }

    private fun timelineWith(
        factor: SignalFactor,
        timestamp: Long
    ): EvidenceTimeline {
        return timeline(now).withCheckedAt(factor, timestamp)
    }
}
