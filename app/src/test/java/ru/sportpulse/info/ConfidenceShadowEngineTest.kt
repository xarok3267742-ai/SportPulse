package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfidenceShadowEngineTest {
    @Test
    fun quorumLeavesNoConfidenceShadow() {
        val assessment = SignalAssessment(listOf(82, 74, 91, 68, 77))
        val evidence = EvidenceAssessment(
            List(SignalFactor.values().size) { EvidenceLevel.QUORUM }
        )

        val result = ConfidenceShadowEngine.evaluate(assessment, evidence)

        assertEquals(ConfidenceShadowStatus.CLEAR, result.status)
        assertEquals(0, result.readinessGap)
        assertEquals(assessment, result.supportedAssessment)
        assertEquals(emptyList<ConfidenceShadowFactor>(), result.shadowedFactors)
        assertNull(result.criticalFactor)
    }

    @Test
    fun unsupportedPointsCanRemainInsideTheSameVerdict() {
        val assessment = SignalAssessment(listOf(70, 60, 60, 60, 60))

        val result = ConfidenceShadowEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(ConfidenceShadowStatus.CONTAINED, result.status)
        assertEquals(SignalVerdict.OBSERVE, result.claimedSignal.verdict)
        assertEquals(SignalVerdict.OBSERVE, result.supportedSignal.verdict)
        assertEquals(listOf(SignalFactor.FORM), result.shadowedFactors.map { it.factor })
        assertEquals(10, result.criticalFactor?.unsupportedPoints)
    }

    @Test
    fun shadowReportsWhenEvidenceChangesTheVerdict() {
        val assessment = SignalAssessment(List(5) { 90 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.UNCONFIRMED }
        )

        val result = ConfidenceShadowEngine.evaluate(assessment, evidence)

        assertEquals(ConfidenceShadowStatus.VERDICT_SHIFT, result.status)
        assertEquals(SignalVerdict.READY, result.claimedSignal.verdict)
        assertEquals(SignalVerdict.SKIP, result.supportedSignal.verdict)
        assertEquals(65, result.readinessGap)
        assertEquals(5, result.shadowedFactors.size)
    }

    @Test
    fun criticalFactorUsesLargestReadinessContribution() {
        val assessment = SignalAssessment(listOf(100, 80, 30, 30, 30))

        val result = ConfidenceShadowEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(SignalFactor.FORM, result.criticalFactor?.factor)
        assertEquals(40, result.criticalFactor?.unsupportedPoints)
        assertEquals(
            result.shadowedFactors.maxOf { it.readinessImpact },
            result.criticalFactor?.readinessImpact
        )
    }

    @Test
    fun equalFactorsUseStableSignalOrder() {
        val assessment = SignalAssessment(List(5) { 80 })

        val result = ConfidenceShadowEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(SignalFactor.FORM, result.criticalFactor?.factor)
    }

    @Test
    fun roundedReadinessCanStayEqualWhileShadowStillExists() {
        val assessment = SignalAssessment(listOf(61, 40, 40, 40, 40))

        val result = ConfidenceShadowEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(ConfidenceShadowStatus.CONTAINED, result.status)
        assertEquals(0, result.readinessGap)
        assertEquals(listOf(SignalFactor.FORM), result.shadowedFactors.map { it.factor })
        assertEquals(61, result.claimedAssessment.value(SignalFactor.FORM))
        assertEquals(60, result.supportedAssessment.value(SignalFactor.FORM))
    }
}
