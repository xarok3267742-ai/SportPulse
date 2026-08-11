package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketLeverageEngineTest {
    @Test
    fun freshLineupCheckImprovesAllFootballTemplates() {
        val evidence = quorumEvidence().withLevel(
            SignalFactor.LINEUP,
            EvidenceLevel.UNCONFIRMED
        )

        val result = evaluate(
            sport = "Футбол",
            assessment = assessment(80),
            evidence = evidence
        )

        assertEquals(
            MarketLeverageMode.IMPROVE,
            result.mode
        )
        assertEquals(
            SignalFactor.LINEUP,
            result.factor
        )
        assertEquals(6, result.affectedMarketCount)
        assertEquals(11, result.conditionGain)
        assertEquals(11, result.statusGain)
        assertEquals(5, result.reopenedCount)
        assertEquals(6, result.coveredCount)
        assertTrue(result.requiresNewData)
        assertTrue(result.requiresFreshQuorum)
    }

    @Test
    fun sharedSourceCheckWinsWhenEveryFactorHasOneSource() {
        val result = evaluate(
            sport = "Футбол",
            assessment = assessment(90),
            evidence = EvidenceAssessment.singleSource()
        )

        assertEquals(
            SignalFactor.SOURCES,
            result.factor
        )
        assertEquals(6, result.affectedMarketCount)
        assertEquals(6, result.conditionGain)
        assertEquals(0, result.statusTransitionCount)
        assertEquals(6, result.criticalMarketCount)
        assertTrue(result.requiresFreshQuorum)
    }

    @Test
    fun lowRawValuesDoNotCreateFakeEvidenceWork() {
        val assessment = assessment(30)
        val evidence = quorumEvidence()
        val timeline = timeline(NOW)

        val result = MarketLeverageEngine.evaluate(
            sport = "Футбол",
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            now = NOW
        )

        assertEquals(MarketLeverageMode.MAINTAIN, result.mode)
        assertEquals(SignalFactor.LINEUP, result.factor)
        assertFalse(result.requiresNewData)
        assertFalse(result.requiresFreshQuorum)
        assertEquals(30, result.currentRawValue)
        assertEquals(30, result.targetValue)
        assertEquals(0, result.conditionGain)
        assertEquals(List(5) { 30 }, assessment.values)
        assertEquals(
            List(5) { EvidenceLevel.QUORUM },
            evidence.levels
        )
        assertEquals(List(5) { NOW }, timeline.checkedAt)
    }

    @Test
    fun basketballImpactExcludesInapplicableTemplates() {
        val evidence = quorumEvidence().withLevel(
            SignalFactor.LINEUP,
            EvidenceLevel.UNCONFIRMED
        )

        val result = evaluate(
            sport = "Баскетбол",
            assessment = assessment(80),
            evidence = evidence
        )

        assertEquals(
            SignalFactor.LINEUP,
            result.factor
        )
        assertEquals(
            listOf(
                MarketKind.HANDICAP,
                MarketKind.TOTAL,
                MarketKind.INDIVIDUAL_TOTAL,
                MarketKind.PERIOD
            ),
            result.impacts.map(
                MarketLeverageImpact::kind
            )
        )
        assertEquals(3, result.reopenedCount)
        assertEquals(4, result.coveredCount)
    }

    @Test
    fun coveredLensSelectsEarliestCriticalMaintenance() {
        val assessment = assessment(80)
        val result = evaluate(
            sport = "Футбол",
            assessment = assessment,
            evidence = quorumEvidence()
        )

        assertEquals(
            MarketLeverageMode.MAINTAIN,
            result.mode
        )
        assertEquals(
            SignalFactor.LINEUP,
            result.factor
        )
        assertEquals(5, result.affectedMarketCount)
        assertEquals(0, result.conditionGain)
        assertEquals(
            NOW + FreshnessPolicy.validForMillis(
                SignalFactor.LINEUP
            ),
            result.nextTransitionAt
        )
        assertSame(result.baseline, result.projected)
        assertEquals(
            assessment.value(SignalFactor.LINEUP),
            result.targetValue
        )
    }

    @Test
    fun projectedStatusesMatchRecalculatedLens() {
        val evidence = quorumEvidence().withLevel(
            SignalFactor.SOURCES,
            EvidenceLevel.SINGLE_SOURCE
        )
        val result = evaluate(
            sport = "Хоккей",
            assessment = assessment(75),
            evidence = evidence
        )

        result.impacts.forEach { impact ->
            assertEquals(
                impact.projectedStatus,
                result.projected
                    .item(impact.kind)
                    ?.status
            )
            assertTrue(
                impact.statusGain > 0 ||
                    impact.conditionGain > 0
            )
        }
    }

    private fun evaluate(
        sport: String,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): MarketLeverageResult {
        return MarketLeverageEngine.evaluate(
            sport = sport,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline(NOW),
            now = NOW
        )
    }

    private fun assessment(
        value: Int
    ): SignalAssessment {
        return SignalAssessment(List(5) { value })
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
    }

    private fun timeline(
        timestamp: Long
    ): EvidenceTimeline {
        return EvidenceTimeline(
            List(5) { timestamp }
        )
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
