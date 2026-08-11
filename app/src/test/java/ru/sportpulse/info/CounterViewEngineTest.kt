package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterViewEngineTest {
    private val assessment = SignalAssessment(
        listOf(82, 78, 68, 64, 72)
    )
    private val quorum = EvidenceAssessment(
        List(5) { EvidenceLevel.QUORUM }
    )

    @Test
    fun uncheckedReviewFailsClosedAtSkip() {
        val result = evaluate(
            CounterReviewAssessment.unchecked()
        )

        assertEquals(CounterViewVerdict.OPEN, result.verdict)
        assertEquals(0, result.reviewedCount)
        assertEquals(5, result.openCount)
        assertEquals(SavedDecision.SKIP, result.decisionCeiling)
        assertEquals(SignalVerdict.SKIP, result.defensibleVerdict)
        assertFalse(result.allows(SavedDecision.OBSERVE))
    }

    @Test
    fun threeClearChecksOpenObserveCeiling() {
        val review = CounterReviewAssessment.unchecked()
            .withState(SignalFactor.FORM, CounterReviewState.CLEAR)
            .withState(SignalFactor.LINEUP, CounterReviewState.CLEAR)
            .withState(SignalFactor.LOAD, CounterReviewState.CLEAR)

        val result = evaluate(review)

        assertEquals(CounterViewVerdict.OPEN, result.verdict)
        assertEquals(3, result.reviewedCount)
        assertEquals(SavedDecision.OBSERVE, result.decisionCeiling)
        assertTrue(result.allows(SavedDecision.OBSERVE))
        assertFalse(result.allows(SavedDecision.DATA_READY))
    }

    @Test
    fun allClearChecksAllowDataReady() {
        val result = evaluate(
            CounterReviewAssessment.cleared()
        )

        assertEquals(CounterViewVerdict.BALANCED, result.verdict)
        assertEquals(5, result.reviewedCount)
        assertEquals(SavedDecision.DATA_READY, result.decisionCeiling)
        assertEquals(
            result.evidenceResult.effectiveSignal.verdict,
            result.defensibleVerdict
        )
        assertTrue(result.allows(SavedDecision.DATA_READY))
        assertNull(result.nextFactor)
    }

    @Test
    fun mixedFactsKeepObserveCeiling() {
        val review = CounterReviewAssessment.cleared()
            .withState(
                SignalFactor.CONTEXT,
                CounterReviewState.MIXED
            )

        val result = evaluate(review)

        assertEquals(CounterViewVerdict.MIXED, result.verdict)
        assertEquals(1, result.mixedCount)
        assertEquals(SavedDecision.OBSERVE, result.decisionCeiling)
        assertEquals(SignalVerdict.OBSERVE, result.defensibleVerdict)
    }

    @Test
    fun counterfactForcesSkip() {
        val review = CounterReviewAssessment.cleared()
            .withState(
                SignalFactor.LINEUP,
                CounterReviewState.REFUTED
            )

        val result = evaluate(review)

        assertEquals(CounterViewVerdict.REFUTED, result.verdict)
        assertEquals(1, result.refutedCount)
        assertEquals(SavedDecision.SKIP, result.decisionCeiling)
        assertEquals(SignalVerdict.SKIP, result.defensibleVerdict)
    }

    @Test
    fun counterViewNeverRaisesDataVerdict() {
        val lowAssessment = SignalAssessment(List(5) { 10 })

        val result = CounterViewEngine.evaluate(
            assessment = lowAssessment,
            evidence = quorum,
            review = CounterReviewAssessment.cleared()
        )

        assertEquals(SignalVerdict.SKIP, result.defensibleVerdict)
        assertEquals(SavedDecision.DATA_READY, result.decisionCeiling)
    }

    @Test
    fun nextFactorUsesLargestReadinessImpact() {
        val result = evaluate(
            CounterReviewAssessment.unchecked()
        )

        assertEquals(SignalFactor.FORM, result.nextFactor)
        assertTrue(
            result.factor(SignalFactor.FORM).readinessImpact >
                result.factor(SignalFactor.CONTEXT).readinessImpact
        )
    }

    @Test
    fun fingerprintIsStableAndBindsReview() {
        val first = evaluate(
            CounterReviewAssessment.unchecked()
        )
        val same = evaluate(
            CounterReviewAssessment.unchecked()
        )
        val changed = evaluate(
            CounterReviewAssessment.unchecked().withState(
                SignalFactor.FORM,
                CounterReviewState.CLEAR
            )
        )

        assertEquals(first.fingerprint, same.fingerprint)
        assertEquals(8, first.shortFingerprint.length)
        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    private fun evaluate(
        review: CounterReviewAssessment
    ): CounterViewResult {
        return CounterViewEngine.evaluate(
            assessment = assessment,
            evidence = quorum,
            review = review
        )
    }
}
