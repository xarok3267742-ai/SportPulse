package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationRouteEngineTest {
    @Test
    fun oneQuorumCheckCanReachReadyWithoutChangingScores() {
        val assessment = SignalAssessment(listOf(100, 75, 75, 75, 75))
        val evidence = EvidenceAssessment(
            listOf(
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.QUORUM,
                EvidenceLevel.QUORUM,
                EvidenceLevel.QUORUM,
                EvidenceLevel.QUORUM
            )
        )

        val route = VerificationRouteEngine.evaluate(assessment, evidence)

        assertEquals(VerificationRouteStatus.REACHABLE, route.status)
        assertEquals(SignalVerdict.READY, route.targetVerdict)
        assertEquals(SignalThresholds.READY, route.targetReadiness)
        assertEquals(69, route.baselineResult.effectiveSignal.readiness)
        assertEquals(79, route.projectedResult.effectiveSignal.readiness)
        assertEquals(listOf(SignalFactor.FORM), route.steps.map { it.factor })
    }

    @Test
    fun exhaustiveSearchFindsMinimumNumberOfChecks() {
        val assessment = SignalAssessment(List(5) { 60 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.UNCONFIRMED }
        )

        val route = VerificationRouteEngine.evaluate(assessment, evidence)

        assertEquals(VerificationRouteStatus.REACHABLE, route.status)
        assertEquals(SignalVerdict.OBSERVE, route.targetVerdict)
        assertEquals(3, route.steps.size)
        assertEquals(40, route.projectedResult.effectiveSignal.readiness)
    }

    @Test
    fun reportsFactsLimitWhenEvenFullQuorumCannotReachTarget() {
        val assessment = SignalAssessment(listOf(62, 38, 58, 70, 46))

        val route = VerificationRouteEngine.evaluate(
            assessment,
            EvidenceAssessment.singleSource()
        )

        assertEquals(VerificationRouteStatus.FACTS_LIMIT, route.status)
        assertEquals(48, route.baselineResult.effectiveSignal.readiness)
        assertEquals(50, route.allQuorumResult.effectiveSignal.readiness)
        assertEquals(22, route.remainingGap)
        assertEquals(SignalFactor.CONTEXT, route.bestCheck?.factor)
        assertEquals(2, route.bestCheck?.readinessGain)
    }

    @Test
    fun factsLimitHasNoFakeActionWhenEvidenceCannotUnlockPoints() {
        val assessment = SignalAssessment(listOf(50, 50, 50, 50, 50))
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )

        val route = VerificationRouteEngine.evaluate(assessment, evidence)

        assertEquals(VerificationRouteStatus.FACTS_LIMIT, route.status)
        assertEquals(22, route.remainingGap)
        assertNull(route.bestCheck)
    }

    @Test
    fun readyRouteOnlyMaintainsCurrentStatus() {
        val assessment = SignalAssessment(listOf(80, 80, 80, 80, 80))

        val route = VerificationRouteEngine.evaluate(
            assessment,
            EvidenceAssessment(
                List(5) { EvidenceLevel.QUORUM }
            )
        )

        assertEquals(VerificationRouteStatus.READY_MAINTAIN, route.status)
        assertNull(route.targetVerdict)
        assertNull(route.targetReadiness)
        assertEquals(
            route.baselineResult.effectiveSignal,
            route.projectedResult.effectiveSignal
        )
    }
}
