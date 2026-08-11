package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalStressEngineTest {
    private val now = 100L * FreshnessPolicy.HOUR_MILLIS
    private val freshTimeline = EvidenceTimeline(
        List(SignalFactor.values().size) { now }
    )

    @Test
    fun robustSignalKeepsVerdictAfterAnySingleEvidenceLoss() {
        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 100 }),
            evidence = EvidenceAssessment(List(5) { EvidenceLevel.QUORUM }),
            timeline = freshTimeline,
            now = now
        )

        assertEquals(SignalStressStatus.ROBUST, result.status)
        assertEquals(5, result.shocks.size)
        assertFalse(result.shocks.any(EvidenceShock::verdictChanged))
        assertEquals(SignalFactor.FORM, result.criticalShock?.factor)
        assertEquals(17, result.criticalShock?.readinessDrop)
    }

    @Test
    fun fragileSignalFindsSingleLossThatChangesVerdict() {
        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 72 }),
            evidence = EvidenceAssessment(List(5) { EvidenceLevel.QUORUM }),
            timeline = freshTimeline,
            now = now
        )

        assertEquals(SignalStressStatus.FRAGILE, result.status)
        assertTrue(result.criticalShock?.verdictChanged == true)
        assertEquals(SignalFactor.FORM, result.criticalShock?.factor)
        assertEquals(SignalVerdict.OBSERVE, result.criticalShock?.result
            ?.effectiveSignal
            ?.verdict)
    }

    @Test
    fun fullyUnconfirmedSignalHasNoArtificialBuffer() {
        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 90 }),
            evidence = EvidenceAssessment(
                List(5) { EvidenceLevel.UNCONFIRMED }
            ),
            timeline = freshTimeline,
            now = now
        )

        assertEquals(SignalStressStatus.NO_BUFFER, result.status)
        assertTrue(result.shocks.isEmpty())
        assertNull(result.criticalShock)
        assertTrue(result.timeline.isEmpty())
    }

    @Test
    fun lowFactsDoNotCreateAFalseDropWhenEvidenceDegrades() {
        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 20 }),
            evidence = EvidenceAssessment.singleSource(),
            timeline = freshTimeline,
            now = now
        )

        assertEquals(SignalStressStatus.ROBUST, result.status)
        assertEquals(0, result.criticalShock?.readinessDrop)
        assertEquals(
            result.baselineResult.effectiveSignal,
            result.criticalShock?.result?.effectiveSignal
        )
    }

    @Test
    fun stressBaselineUsesEvidenceAlreadyDegradedByTime() {
        val oldTimeline = EvidenceTimeline(
            List(5) { now - 80L * FreshnessPolicy.HOUR_MILLIS }
        )

        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 90 }),
            evidence = EvidenceAssessment.singleSource(),
            timeline = oldTimeline,
            now = now
        )

        assertEquals(SignalStressStatus.NO_BUFFER, result.status)
        assertTrue(
            result.baselineFreshness.effectiveEvidence.levels.all {
                it == EvidenceLevel.UNCONFIRMED
            }
        )
    }

    @Test
    fun timelineFindsWhenFreshnessAloneChangesVerdict() {
        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 80 }),
            evidence = EvidenceAssessment.singleSource(),
            timeline = EvidenceTimeline(List(5) { 0L }),
            now = 0L
        )

        assertEquals(SignalVerdict.OBSERVE, result.baselineResult
            .effectiveSignal
            .verdict)
        assertEquals(
            24L * FreshnessPolicy.HOUR_MILLIS,
            result.firstVerdictChange?.at
        )
        assertEquals(
            listOf(SignalFactor.LOAD),
            result.firstVerdictChange?.changedFactors
        )
        assertEquals(
            SignalVerdict.SKIP,
            result.firstVerdictChange?.result?.effectiveSignal?.verdict
        )
    }

    @Test
    fun simultaneousDeadlinesAreConsolidatedIntoOnePoint() {
        val evidence = EvidenceAssessment(
            listOf(
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.QUORUM,
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.SINGLE_SOURCE
            )
        )

        val result = SignalStressEngine.evaluate(
            assessment = SignalAssessment(List(5) { 80 }),
            evidence = evidence,
            timeline = EvidenceTimeline(List(5) { 0L }),
            now = 0L
        )

        val twelveHours = result.timeline.single {
            it.at == 12L * FreshnessPolicy.HOUR_MILLIS
        }
        assertEquals(
            listOf(SignalFactor.LINEUP, SignalFactor.SOURCES),
            twelveHours.changedFactors
        )
    }
}
