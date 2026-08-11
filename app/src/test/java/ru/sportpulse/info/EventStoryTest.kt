package ru.sportpulse.info

import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStoryEngineTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-03T10:00:00Z"
    ).toEpochMilli()

    @Test
    fun missingStartRoutesBackToSourceAndLocksReview() {
        val event = event(startAt = null, demoSchedule = null)
        val result = evaluate(event = event)

        assertNull(result.startAt)
        assertEquals(EventStoryPhase.INCOMPLETE, result.phase)
        assertEquals(EventStoryAction.OPEN_SOURCE, result.action)
        assertEquals(
            EventStoryChapterState.ATTENTION,
            result.chapter(EventStoryChapter.FACTS).state
        )
        assertEquals(
            EventStoryChapterState.LOCKED,
            result.chapter(EventStoryChapter.REVIEW).state
        )
    }

    @Test
    fun unsignedSourceLeadsBeforeOtherwiseReadyFacts() {
        val event = event(startAt = now + 2L * hour)
        val result = evaluate(
            event = event,
            sourceState = EventStorySourceState.UNSIGNED,
            evidence = quorumEvidence(),
            audit = independentAudit()
        )

        assertEquals(EventStoryAction.OPEN_SOURCE, result.action)
        assertEquals(EventStoryChapter.SOURCE, result.currentChapter)
        assertEquals(
            EventStoryChapterState.ATTENTION,
            result.chapter(EventStoryChapter.SOURCE).state
        )
    }

    @Test
    fun unconfirmedFactsBecomeTheNextChapter() {
        val event = event(startAt = now + 2L * hour)
        val unconfirmed = EvidenceAssessment(
            List(SignalFactor.values().size) {
                EvidenceLevel.UNCONFIRMED
            }
        )
        val result = evaluate(event = event, evidence = unconfirmed)

        assertEquals(EventStoryAction.OPEN_FACTS, result.action)
        assertEquals(EventStoryChapter.FACTS, result.currentChapter)
        assertEquals(SignalFactor.FORM, result.actionFactor)
    }

    @Test
    fun currentPlanAndPreStartSnapshotMakeStoryReady() {
        val event = event(startAt = now + 2L * hour)
        val protocol = protocol(event)
        val receipt = receipt(protocol)
        val snapshot = snapshot(event, savedAt = now)
        val result = evaluate(
            event = event,
            evidence = quorumEvidence(),
            audit = independentAudit(),
            receipt = valid(receipt),
            snapshot = snapshot
        )

        assertEquals(EventStoryPhase.READY, result.phase)
        assertEquals(EventStoryAction.NONE, result.action)
        assertEquals(EventStoryChapter.START, result.currentChapter)
        assertEquals(3, result.completedCount)
    }

    @Test
    fun staleCalendarRevisionRoutesToPlan() {
        val event = event(startAt = now + 2L * hour)
        val protocol = protocol(event)
        val result = evaluate(
            event = event,
            evidence = quorumEvidence(),
            audit = independentAudit(),
            receipt = valid(receipt(protocol)),
            selectedZone = RegionalZone.MINSK
        )

        assertEquals(EventStoryAction.OPEN_PLAN, result.action)
        assertEquals(
            EventStoryChapterState.ATTENTION,
            result.chapter(EventStoryChapter.PLAN).state
        )
    }

    @Test
    fun reviewStaysLockedDuringFourHourEventWindow() {
        val event = event(startAt = now)
        val snapshot = snapshot(event, savedAt = now - minute)
        val result = evaluate(
            event = event,
            snapshot = snapshot,
            evaluationTime = now + 2L * hour
        )

        assertEquals(EventStoryPhase.IN_PROGRESS, result.phase)
        assertEquals(EventStoryAction.NONE, result.action)
        assertEquals(
            EventStoryChapterState.LOCKED,
            result.chapter(EventStoryChapter.REVIEW).state
        )
    }

    @Test
    fun reviewBecomesDueAfterSafetyWindow() {
        val event = event(startAt = now)
        val snapshot = snapshot(event, savedAt = now - minute)
        val result = evaluate(
            event = event,
            snapshot = snapshot,
            evaluationTime = now +
                EventStoryPolicy.REVIEW_DELAY_MILLIS
        )

        assertEquals(EventStoryPhase.REVIEW_DUE, result.phase)
        assertEquals(EventStoryAction.OPEN_REVIEW, result.action)
        assertEquals(EventStoryChapter.REVIEW, result.currentChapter)
    }

    @Test
    fun finalizedReviewClosesTheSixChapterStory() {
        val event = event(startAt = now)
        val snapshot = snapshot(event, savedAt = now - minute)
        val reviewAt = now + EventStoryPolicy.REVIEW_DELAY_MILLIS
        val review = finalizedReview(snapshot, reviewAt)
        val result = evaluate(
            event = event,
            snapshot = snapshot,
            review = review,
            evaluationTime = reviewAt + minute
        )

        assertEquals(EventStoryPhase.COMPLETE, result.phase)
        assertEquals(EventStoryAction.NONE, result.action)
        assertEquals(
            EventStoryChapterState.COMPLETE,
            result.chapter(EventStoryChapter.REVIEW).state
        )
    }

    @Test
    fun snapshotCreatedAfterStartCannotUnlockReview() {
        val event = event(startAt = now)
        val lateSnapshot = snapshot(
            event,
            savedAt = now + minute
        )
        val result = evaluate(
            event = event,
            snapshot = lateSnapshot,
            evaluationTime = now +
                EventStoryPolicy.REVIEW_DELAY_MILLIS
        )

        assertEquals(EventStoryPhase.INCOMPLETE, result.phase)
        assertEquals(EventStoryAction.NONE, result.action)
        assertEquals(
            EventStoryChapterState.MISSED,
            result.chapter(EventStoryChapter.DECISION).state
        )
        assertEquals(
            EventStoryChapterState.MISSED,
            result.chapter(EventStoryChapter.REVIEW).state
        )
    }

    @Test
    fun snapshotAtExactStartIsAlreadyTooLate() {
        val event = event(startAt = now)
        val atStart = snapshot(event, savedAt = now)
        val result = evaluate(
            event = event,
            snapshot = atStart,
            evaluationTime = now +
                EventStoryPolicy.REVIEW_DELAY_MILLIS
        )

        assertFalse(
            EventStoryTiming.decisionWindowOpen(
                event = event,
                snapshot = null,
                now = now
            )
        )
        assertEquals(
            EventStoryChapterState.MISSED,
            result.chapter(EventStoryChapter.DECISION).state
        )
    }

    @Test
    fun exactPastStartKeepsDecisionWindowClosed() {
        val event = event(startAt = now - minute)

        assertFalse(
            EventStoryTiming.decisionWindowOpen(
                event = event,
                snapshot = null,
                now = now
            )
        )
    }

    @Test
    fun earlyReviewIsDetectedInsteadOfAccepted() {
        val event = event(startAt = now)
        val snapshot = snapshot(event, savedAt = now - minute)
        val early = finalizedReview(snapshot, now + hour)
        val result = evaluate(
            event = event,
            snapshot = snapshot,
            review = early,
            evaluationTime = now +
                EventStoryPolicy.REVIEW_DELAY_MILLIS
        )

        assertEquals(EventStoryAction.OPEN_REVIEW, result.action)
        assertEquals(
            EventStoryChapterState.ATTENTION,
            result.chapter(EventStoryChapter.REVIEW).state
        )
    }

    @Test
    fun subMinuteProgressDoesNotChangeStableStoryFingerprint() {
        val event = event(startAt = now + 2L * hour)
        val first = evaluate(
            event = event,
            evidence = quorumEvidence(),
            audit = independentAudit(),
            evaluationTime = now
        )
        val second = evaluate(
            event = event,
            evidence = quorumEvidence(),
            audit = independentAudit(),
            evaluationTime = now + 30_000L
        )

        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun demoStoryAnchorsToFirstOccurrenceAfterSnapshot() {
        val demo = event(
            startAt = null,
            demoSchedule = DemoSchedule(
                dayOfWeek = DayOfWeek.MONDAY,
                hour = 14,
                minute = 0
            )
        )
        val snapshot = snapshot(demo, savedAt = now - 2L * hour)
        val window = EventStoryTiming.window(
            event = demo,
            snapshot = snapshot,
            now = now + 7L * 24L * hour
        )

        assertTrue(requireNotNull(window).startAt < now + 7L * 24L * hour)
    }

    @Test
    fun demoWithoutSnapshotUsesTheNextOccurrence() {
        val demo = event(
            startAt = null,
            demoSchedule = DemoSchedule(
                dayOfWeek = DayOfWeek.MONDAY,
                hour = 14,
                minute = 0
            )
        )
        val afterThisWeeksStart = now + 5L * hour
        val window = requireNotNull(
            EventStoryTiming.window(
                event = demo,
                snapshot = null,
                now = afterThisWeeksStart
            )
        )

        assertTrue(window.startAt > afterThisWeeksStart)
        assertTrue(
            EventStoryTiming.decisionWindowOpen(
                event = demo,
                snapshot = null,
                now = afterThisWeeksStart
            )
        )
    }

    private fun evaluate(
        event: SportEvent,
        sourceState: EventStorySourceState =
            EventStorySourceState.DEMO,
        evidence: EvidenceAssessment =
            EvidenceAssessment.singleSource(),
        audit: SourceAuditAssessment =
            SourceAuditAssessment.unaudited(),
        receipt: PreflightReceiptReadResult = emptyReceipt(),
        snapshot: DecisionSnapshot? = null,
        review: PostEventReview? = null,
        selectedZone: RegionalZone = RegionalZone.MOSCOW,
        evaluationTime: Long = now
    ): EventStoryResult {
        return EventStoryEngine.evaluate(
            EventStoryInput(
                event = event,
                sourceState = sourceState,
                assessment = event.seedAssessment,
                claimedEvidence = evidence,
                sourceAudit = audit,
                timeline = EvidenceTimeline(
                    List(SignalFactor.values().size) { now }
                ),
                selectedZone = selectedZone,
                storedReceipt = receipt,
                snapshot = snapshot,
                review = review,
                now = evaluationTime
            )
        )
    }

    private fun protocol(event: SportEvent): PreflightProtocol {
        val relay = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = event.seedAssessment,
                    claimedEvidence = quorumEvidence(),
                    sourceAudit = independentAudit(),
                    timeline = EvidenceTimeline(
                        List(SignalFactor.values().size) { now }
                    )
                ),
                now = now
            )
        )
        return PreflightProtocolEngine.evaluate(event, relay)
    }

    private fun receipt(
        protocol: PreflightProtocol
    ): PreflightExportReceipt {
        return PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            sequence = 1,
            exportedAt = now
        )
    }

    private fun snapshot(
        event: SportEvent,
        savedAt: Long
    ): DecisionSnapshot {
        return DecisionSnapshotFactory.create(
            eventId = event.id,
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = event.seedAssessment,
            evidence = EvidenceAssessment.singleSource(),
            timeline = EvidenceTimeline(
                List(SignalFactor.values().size) { savedAt }
            )
        )
    }

    private fun finalizedReview(
        snapshot: DecisionSnapshot,
        startedAt: Long
    ): PostEventReview {
        var review = PostEventReviewFactory.start(snapshot, startedAt)
        SignalFactor.values().forEachIndexed { index, factor ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = factor,
                outcome = PostEventOutcome.CONFIRMED,
                now = startedAt + index
            )
        }
        return PostEventReviewFactory.finalize(
            review = review,
            snapshot = snapshot,
            now = startedAt + SignalFactor.values().size
        )
    }

    private fun event(
        startAt: Long?,
        demoSchedule: DemoSchedule? = null
    ): SportEvent {
        return SportEvent(
            id = "story_event",
            sport = "Футбол",
            tournament = "Тест",
            region = "Россия",
            match = "Север - Столица",
            time = "Тест",
            focus = "Факты",
            note = "Тест",
            tags = emptyList(),
            imageRes = 0,
            seedAssessment = SignalAssessment(List(5) { 72 }),
            startAt = startAt,
            demoSchedule = demoSchedule
        )
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(SignalFactor.values().size) {
                EvidenceLevel.QUORUM
            }
        )
    }

    private fun independentAudit(): SourceAuditAssessment {
        return SourceAuditAssessment(
            List(SignalFactor.values().size) {
                SourceAuditState.INDEPENDENT
            }
        )
    }

    private fun emptyReceipt(): PreflightReceiptReadResult {
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.EMPTY,
            receipt = null
        )
    }

    private fun valid(
        receipt: PreflightExportReceipt
    ): PreflightReceiptReadResult {
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.VALID,
            receipt = receipt
        )
    }
}
