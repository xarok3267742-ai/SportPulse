package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainAnalyticsEngineTest {
    private val now = 1_700_000_000_000L

    @Test
    fun unconfirmedFactorsStopTheConclusion() {
        val result = evaluate(
            values = listOf(80, 80, 80, 80, 80),
            levels = List(5) { EvidenceLevel.UNCONFIRMED }
        )

        assertEquals(PlainAnalyticsStatus.STOP, result.status)
        assertEquals(0, result.confirmedFactorCount)
        assertEquals(0, result.independentlyVerifiedCount)
        assertEquals(SignalFactor.FORM, result.actionFactor)
        assertTrue(result.gapSummary.startsWith("Без подтверждения"))
        assertEquals(
            List(5) { EvidenceLevel.UNCONFIRMED },
            result.factorSummaries.map { it.effectiveLevel }
        )
        assertEquals(
            listOf(SignalFactor.FORM),
            result.factorSummaries.filter { it.isNextAction }
                .map { it.factor }
        )
    }

    @Test
    fun oneSourceRequestsIndependentConfirmation() {
        val result = evaluate(
            values = listOf(70, 45, 70, 70, 70),
            levels = List(5) { EvidenceLevel.SINGLE_SOURCE }
        )

        assertEquals(PlainAnalyticsStatus.CHECK, result.status)
        assertEquals(5, result.confirmedFactorCount)
        assertEquals(0, result.independentlyVerifiedCount)
        assertEquals(SignalFactor.LINEUP, result.actionFactor)
        assertTrue(result.actionSummary.contains("второй источник"))
        assertTrue(
            result.factorSummaries.all {
                it.freshnessStatus == FreshnessStatus.FRESH
            }
        )
    }

    @Test
    fun quorumIsReadyRegardlessOfSubjectiveScores() {
        val result = evaluate(
            values = listOf(5, 10, 15, 20, 25),
            levels = List(5) { EvidenceLevel.QUORUM }
        )

        assertEquals(PlainAnalyticsStatus.READY, result.status)
        assertEquals(5, result.confirmedFactorCount)
        assertEquals(5, result.independentlyVerifiedCount)
    }

    @Test
    fun expiredEvidenceReturnsToStop() {
        val timeline = EvidenceTimeline(
            List(5) { 1L }
        )
        val result = PlainAnalyticsEngine.evaluate(
            assessment = SignalAssessment(List(5) { 90 }),
            evidence = EvidenceAssessment(
                List(5) { EvidenceLevel.QUORUM }
            ),
            timeline = timeline,
            now = now
        )

        assertEquals(PlainAnalyticsStatus.STOP, result.status)
        assertTrue(
            result.factorSummaries.all {
                it.freshnessStatus == FreshnessStatus.EXPIRED
            }
        )
    }

    private fun evaluate(
        values: List<Int>,
        levels: List<EvidenceLevel>
    ): PlainAnalyticsResult {
        return PlainAnalyticsEngine.evaluate(
            assessment = SignalAssessment(values),
            evidence = EvidenceAssessment(levels),
            timeline = EvidenceTimeline(List(5) { now }),
            now = now
        )
    }
}
