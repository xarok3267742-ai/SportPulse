package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionDeskTest {
    private val now = 1_750_000_000_000L

    @Test
    fun incompleteDraftFailsClosed() {
        val draft = draft(thesis = "")
        val result = evaluate(
            draft = draft,
            evidence = quorumEvidence(),
            review = CounterReviewAssessment.cleared()
        )

        assertEquals(DecisionDeskStatus.STOP, result.status)
        assertEquals(
            listOf(DecisionDeskField.THESIS),
            result.missingFields
        )
        assertEquals("Записать идею матча", result.actionTitle)
    }

    @Test
    fun openCounterviewFailsClosedEvenWithQuorumFacts() {
        val result = evaluate(
            draft = draft(),
            evidence = quorumEvidence(),
            review = CounterReviewAssessment.unchecked()
        )

        assertEquals(DecisionDeskStatus.STOP, result.status)
        assertTrue(result.explanation.contains("альтернативную"))
    }

    @Test
    fun oneSourceMarketRemainsObserveAfterCountercheck() {
        val result = evaluate(
            draft = draft(),
            evidence = EvidenceAssessment.singleSource(),
            review = CounterReviewAssessment.cleared()
        )

        assertEquals(DecisionDeskStatus.OBSERVE, result.status)
        assertEquals(MarketLensStatus.CHECK, result.marketStatus)
    }

    @Test
    fun verifiedMarketAndCounterviewAreFactsReady() {
        val result = evaluate(
            draft = draft(),
            evidence = quorumEvidence(),
            review = CounterReviewAssessment.cleared()
        )

        assertEquals(DecisionDeskStatus.FACTS_READY, result.status)
        assertNull(result.nextFactor)
        assertTrue(result.explanation.contains("не прогноз"))
    }

    @Test
    fun counterfactStopsCompleteDraft() {
        val review = CounterReviewAssessment.cleared().withState(
            SignalFactor.LINEUP,
            CounterReviewState.REFUTED
        )
        val result = evaluate(
            draft = draft(),
            evidence = quorumEvidence(),
            review = review
        )

        assertEquals(DecisionDeskStatus.STOP, result.status)
        assertEquals(CounterViewVerdict.REFUTED, result.counterVerdict)
    }

    @Test
    fun draftCodecRoundTripsAndRejectsTampering() {
        val draft = draft()
        val encoded = DecisionDeskDraftCodec.encode(draft)

        assertEquals(draft, DecisionDeskDraftCodec.decode(encoded))
        assertNull(
            DecisionDeskDraftCodec.decode(
                encoded.replace("Т", "X") + "x"
            )
        )
    }

    @Test
    fun profileCountsVisibleLedgerWindow() {
        var ledger = DecisionLedgerFactory.empty()
        val snapshots = listOf(
            SavedDecision.SKIP,
            SavedDecision.OBSERVE,
            SavedDecision.DATA_READY
        ).mapIndexed { index, decision ->
            DecisionSnapshotFactory.create(
                eventId = "event-$index",
                decision = decision,
                savedAt = now + index,
                assessment = assessment(),
                evidence = quorumEvidence(),
                timeline = EvidenceTimeline(List(5) { now }),
                counterReview = CounterReviewAssessment.cleared()
            )
        }
        snapshots.forEach { snapshot ->
            ledger = DecisionLedgerFactory.append(
                ledger = ledger,
                snapshot = snapshot,
                eventLabel = "Матч ${snapshot.eventId}"
            )
        }

        val profile = DecisionDeskProfileEngine.create(
            ledger = ledger,
            calibrationRecords = snapshots.take(2).map(
                ::calibrationRecord
            )
        )

        assertEquals(3L, profile.totalDecisions)
        assertEquals(1, profile.stopCount)
        assertEquals(1, profile.observeCount)
        assertEquals(1, profile.readyCount)
        assertEquals(66, profile.cautiousShare)
        assertEquals(2, profile.reviewedEvents)
        assertEquals(2, profile.linkedReviewCount)
        assertEquals(1, profile.openCycleCount)
        assertEquals(67, profile.reviewCoveragePercent)
        assertEquals(2, profile.calibrationMemory.reviewCount)
    }

    @Test
    fun profileLinksReviewToExactDecisionSnapshot() {
        val original = DecisionSnapshotFactory.create(
            eventId = "same-event",
            decision = SavedDecision.OBSERVE,
            savedAt = now,
            assessment = assessment(),
            evidence = quorumEvidence(),
            timeline = EvidenceTimeline(List(5) { now }),
            counterReview = CounterReviewAssessment.cleared()
        )
        val revised = DecisionSnapshotFactory.create(
            eventId = original.eventId,
            decision = SavedDecision.SKIP,
            savedAt = now + 1L,
            assessment = assessment(),
            evidence = quorumEvidence(),
            timeline = EvidenceTimeline(List(5) { now + 1L }),
            counterReview = CounterReviewAssessment.cleared()
        )
        val ledger = DecisionLedgerFactory.append(
            ledger = DecisionLedgerFactory.empty(),
            snapshot = original,
            eventLabel = "Матч"
        )

        val profile = DecisionDeskProfileEngine.create(
            ledger = ledger,
            calibrationRecords = listOf(
                calibrationRecord(revised)
            )
        )

        assertEquals(1, profile.reviewedEvents)
        assertEquals(0, profile.linkedReviewCount)
        assertEquals(1, profile.openCycleCount)
        assertEquals(0, profile.reviewCoveragePercent)
    }

    @Test
    fun profileCoverageUsesOnlyAccessibleLedgerWindow() {
        var ledger = DecisionLedgerFactory.empty()
        var droppedSnapshot: DecisionSnapshot? = null
        repeat(DecisionLedgerFactory.MAX_RECORDS + 1) { index ->
            val snapshot = DecisionSnapshotFactory.create(
                eventId = "rotating-event-$index",
                decision = SavedDecision.SKIP,
                savedAt = now + index,
                assessment = assessment(),
                evidence = quorumEvidence(),
                timeline = EvidenceTimeline(List(5) { now }),
                counterReview = CounterReviewAssessment.cleared()
            )
            if (index == 0) droppedSnapshot = snapshot
            ledger = DecisionLedgerFactory.append(
                ledger = ledger,
                snapshot = snapshot,
                eventLabel = "Матч $index"
            )
        }

        val profile = DecisionDeskProfileEngine.create(
            ledger = ledger,
            calibrationRecords = listOf(
                calibrationRecord(requireNotNull(droppedSnapshot))
            )
        )

        assertEquals(51L, profile.totalDecisions)
        assertEquals(50, profile.visibleDecisionCount)
        assertEquals(1, profile.reviewedEvents)
        assertEquals(0, profile.linkedReviewCount)
        assertEquals(50, profile.openCycleCount)
        assertEquals(0, profile.reviewCoveragePercent)
    }

    private fun evaluate(
        draft: DecisionDeskDraft,
        evidence: EvidenceAssessment,
        review: CounterReviewAssessment
    ): DecisionDeskResult {
        val assessment = assessment()
        val timeline = EvidenceTimeline(List(5) { now })
        val lens = MarketLensEngine.evaluate(
            sport = "Футбол",
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = now
        )
        val counterView = CounterViewEngine.evaluate(
            assessment = assessment,
            evidence = evidence,
            review = review
        )
        return DecisionDeskEngine.evaluate(
            draft = draft,
            market = lens.item(draft.marketKind),
            counterView = counterView
        )
    }

    private fun draft(
        thesis: String = "Хозяева сохраняют темп после перерыва"
    ): DecisionDeskDraft {
        return DecisionDeskDraftFactory.create(
            eventId = "event-1",
            marketKind = MarketKind.TOTAL,
            thesis = thesis,
            counterargument = "Ротация может снизить темп",
            stopCondition = "Два игрока основы не выходят",
            updatedAt = now
        )
    }

    private fun assessment(): SignalAssessment {
        return SignalAssessment(List(5) { 85 })
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
    }

    private fun calibrationRecord(
        snapshot: DecisionSnapshot
    ): CalibrationRecord {
        var review = PostEventReviewFactory.start(
            snapshot = snapshot,
            now = snapshot.savedAt + 1L
        )
        SignalFactor.values().forEach { factor ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = factor,
                outcome = PostEventOutcome.CONFIRMED,
                now = review.updatedAt + 1L
            )
        }
        review = PostEventReviewFactory.finalize(
            review = review,
            snapshot = snapshot,
            now = review.updatedAt + 1L
        )
        return CalibrationRecord(snapshot, review)
    }
}
