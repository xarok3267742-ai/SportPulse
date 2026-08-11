package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceEngineTest {
    @Test
    fun quorumPreservesEveryClaimedValue() {
        val assessment = SignalAssessment(listOf(82, 74, 91, 68, 77))
        val evidence = EvidenceAssessment(
            List(SignalFactor.values().size) { EvidenceLevel.QUORUM }
        )

        val result = EvidenceEngine.evaluate(assessment, evidence)

        assertEquals(assessment, result.effectiveAssessment)
        assertEquals(result.rawSignal, result.effectiveSignal)
        assertEquals(5, result.quorumCount)
        assertEquals(0, result.readinessLoss)
        assertTrue(result.cappedFactors.isEmpty())
    }

    @Test
    fun singleSourceCapsClaimsAtSixty() {
        val assessment = SignalAssessment(listOf(100, 80, 60, 40, 20))

        val result = EvidenceEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(
            listOf(60, 60, 60, 40, 20),
            result.effectiveAssessment.values
        )
        assertEquals(
            listOf(SignalFactor.FORM, SignalFactor.LINEUP),
            result.cappedFactors
        )
    }

    @Test
    fun unconfirmedEvidenceCannotContributeMoreThanTwentyFive() {
        val assessment = SignalAssessment(listOf(90, 90, 90, 90, 90))
        val evidence = EvidenceAssessment(
            List(SignalFactor.values().size) {
                EvidenceLevel.UNCONFIRMED
            }
        )

        val result = EvidenceEngine.evaluate(assessment, evidence)

        assertEquals(listOf(25, 25, 25, 25, 25), result.effectiveAssessment.values)
        assertEquals(SignalVerdict.READY, result.rawSignal.verdict)
        assertEquals(SignalVerdict.SKIP, result.effectiveSignal.verdict)
        assertTrue(result.readinessLoss > 0)
    }

    @Test
    fun mixedEvidenceReportsOnlyActuallyCappedFactors() {
        val assessment = SignalAssessment(listOf(20, 70, 80, 55, 100))
        val evidence = EvidenceAssessment(
            listOf(
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.QUORUM,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.UNCONFIRMED
            )
        )

        val result = EvidenceEngine.evaluate(assessment, evidence)

        assertEquals(listOf(20, 60, 80, 55, 25), result.effectiveAssessment.values)
        assertEquals(
            listOf(SignalFactor.LINEUP, SignalFactor.SOURCES),
            result.cappedFactors
        )
        assertEquals(1, result.quorumCount)
    }
}
