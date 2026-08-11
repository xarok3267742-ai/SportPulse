package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionCorridorEngineTest {
    @Test
    fun observeSignalCanExposeBothAdjacentBoundaries() {
        val assessment = SignalAssessment(List(5) { 70 })
        val evidence = quorumEvidence()

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            evidence
        )

        assertEquals(SignalVerdict.SKIP, corridor.lowerTarget)
        assertEquals(SignalVerdict.READY, corridor.upperTarget)
        assertNotNull(corridor.lowerBoundary)
        assertNotNull(corridor.upperBoundary)
        assertEquals(
            SignalVerdict.SKIP,
            corridor.lowerBoundary?.result?.effectiveSignal?.verdict
        )
        assertEquals(
            SignalVerdict.READY,
            corridor.upperBoundary?.result?.effectiveSignal?.verdict
        )
    }

    @Test
    fun skipSignalOnlySearchesForReopenBoundary() {
        val assessment = SignalAssessment(List(5) { 38 })

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            quorumEvidence()
        )

        assertNull(corridor.lowerTarget)
        assertNull(corridor.lowerBoundary)
        assertEquals(SignalVerdict.OBSERVE, corridor.upperTarget)
        assertNotNull(corridor.upperBoundary)
    }

    @Test
    fun readySignalOnlySearchesForLowerBoundary() {
        val assessment = SignalAssessment(List(5) { 80 })

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            quorumEvidence()
        )

        assertEquals(SignalVerdict.OBSERVE, corridor.lowerTarget)
        assertNotNull(corridor.lowerBoundary)
        assertNull(corridor.upperTarget)
        assertNull(corridor.upperBoundary)
    }

    @Test
    fun evidenceCapCanMakeHigherStatusUnreachableByOneFact() {
        val assessment = SignalAssessment(List(5) { 30 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.UNCONFIRMED }
        )

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            evidence
        )

        assertEquals(SignalVerdict.SKIP, corridor.baseline.effectiveSignal.verdict)
        assertEquals(SignalVerdict.OBSERVE, corridor.upperTarget)
        assertNull(corridor.upperBoundary)
        assertNull(corridor.nearestBoundary)
    }

    @Test
    fun claimedChangeIncludesHeadroomAboveEvidenceCap() {
        val assessment = SignalAssessment(List(5) { 90 })

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )
        val lower = corridor.lowerBoundary

        assertNotNull(lower)
        assertEquals(90, lower?.claimedBefore)
        assertEquals(60, lower?.supportedBefore)
        assertTrue((lower?.claimedAfter ?: 100) < 60)
        assertEquals(
            90 - (lower?.claimedAfter ?: 90),
            lower?.claimedChange
        )
    }

    @Test
    fun equalBoundariesUseStableFactorOrder() {
        val assessment = SignalAssessment(List(5) { 80 })

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            quorumEvidence()
        )

        assertEquals(SignalFactor.FORM, corridor.lowerBoundary?.factor)
    }

    @Test
    fun chosenBoundaryIsTheFirstValueThatChangesVerdict() {
        val assessment = SignalAssessment(List(5) { 70 })
        val evidence = quorumEvidence()
        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            evidence
        )
        val upper = corridor.upperBoundary

        assertNotNull(upper)
        val valueBeforeBoundary = (upper?.claimedAfter ?: 0) - 1
        val previous = EvidenceEngine.evaluate(
            assessment.withValue(
                upper?.factor ?: SignalFactor.FORM,
                valueBeforeBoundary
            ),
            evidence
        )
        assertEquals(
            corridor.baseline.effectiveSignal.verdict,
            previous.effectiveSignal.verdict
        )
    }

    @Test
    fun evaluationDoesNotReplaceInputAssessment() {
        val assessment = SignalAssessment(List(5) { 70 })

        val corridor = DecisionCorridorEngine.evaluate(
            assessment,
            quorumEvidence()
        )

        assertSame(assessment, corridor.baseline.rawAssessment)
        assertEquals(List(5) { 70 }, assessment.values)
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
    }
}
