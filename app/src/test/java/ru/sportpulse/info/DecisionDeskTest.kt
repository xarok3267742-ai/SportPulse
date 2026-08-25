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
        assertEquals("Сформулировать тезис", result.actionTitle)
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
        listOf(
            SavedDecision.SKIP,
            SavedDecision.OBSERVE,
            SavedDecision.DATA_READY
        ).forEachIndexed { index, decision ->
            val snapshot = DecisionSnapshotFactory.create(
                eventId = "event-$index",
                decision = decision,
                savedAt = now + index,
                assessment = assessment(),
                evidence = quorumEvidence(),
                timeline = EvidenceTimeline(List(5) { now }),
                counterReview = CounterReviewAssessment.cleared()
            )
            ledger = DecisionLedgerFactory.append(
                ledger = ledger,
                snapshot = snapshot,
                eventLabel = "Матч $index"
            )
        }

        val profile = DecisionDeskProfileEngine.create(
            ledger = ledger,
            reviewedEvents = 2
        )

        assertEquals(3L, profile.totalDecisions)
        assertEquals(1, profile.stopCount)
        assertEquals(1, profile.observeCount)
        assertEquals(1, profile.readyCount)
        assertEquals(66, profile.cautiousShare)
        assertEquals(2, profile.reviewedEvents)
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
}
