package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketLensEngineTest {
    @Test
    fun classifierRecognizesRussianAndCommonProviderLabels() {
        assertEquals(
            SportFamily.FOOTBALL,
            SportFamilyClassifier.classify("Футбол")
        )
        assertEquals(
            SportFamily.HOCKEY,
            SportFamilyClassifier.classify("Ice Hockey")
        )
        assertEquals(
            SportFamily.ESPORTS,
            SportFamilyClassifier.classify("CS2 esports")
        )
        assertEquals(
            SportFamily.COMBAT,
            SportFamilyClassifier.classify("ММА")
        )
        assertEquals(
            SportFamily.OTHER,
            SportFamilyClassifier.classify("Гандбол")
        )
    }

    @Test
    fun basketballHidesFootballOnlyTemplates() {
        val lens = evaluate(
            sport = "Баскетбол",
            assessment = assessment(80),
            evidence = quorumEvidence()
        )

        assertEquals(
            MarketLensStatus.NOT_APPLICABLE,
            lens.item(MarketKind.ONE_X_TWO)?.status
        )
        assertEquals(
            MarketLensStatus.NOT_APPLICABLE,
            lens.item(MarketKind.BOTH_SCORE)?.status
        )
        assertEquals(
            MarketLensStatus.COVERED,
            lens.item(MarketKind.HANDICAP)?.status
        )
    }

    @Test
    fun unknownSportKeepsOnlyGenericTemplatesApplicable() {
        val lens = evaluate(
            sport = "Гандбол",
            assessment = assessment(80),
            evidence = quorumEvidence()
        )

        assertEquals(
            MarketLensStatus.NOT_APPLICABLE,
            lens.item(MarketKind.ONE_X_TWO)?.status
        )
        assertEquals(
            MarketLensStatus.COVERED,
            lens.item(MarketKind.HANDICAP)?.status
        )
        assertEquals(
            MarketLensStatus.COVERED,
            lens.item(MarketKind.TOTAL)?.status
        )
        assertEquals(2, lens.applicableCount)
    }

    @Test
    fun unconfirmedHighClaimsCloseCriticalDataGate() {
        val lens = evaluate(
            sport = "Футбол",
            assessment = assessment(100),
            evidence = EvidenceAssessment(
                List(5) { EvidenceLevel.UNCONFIRMED }
            )
        )
        val handicap = requireNotNull(
            lens.item(MarketKind.HANDICAP)
        )

        assertEquals(
            MarketLensStatus.CLOSED,
            handicap.status
        )
        assertEquals(
            listOf(
                SignalFactor.FORM,
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            handicap.blockingFactors
        )
        assertEquals(
            SignalFactor.FORM,
            handicap.nextCheck?.factor
        )
        assertEquals(
            MarketNextCheckReason.BLOCKER,
            handicap.nextCheck?.reason
        )
    }

    @Test
    fun oneSourceCanCoverValuesButNotCriticalQuorum() {
        val lens = evaluate(
            sport = "Футбол",
            assessment = assessment(90),
            evidence = EvidenceAssessment.singleSource()
        )
        val handicap = requireNotNull(
            lens.item(MarketKind.HANDICAP)
        )

        assertEquals(
            MarketLensStatus.CHECK,
            handicap.status
        )
        assertEquals(5, handicap.metConditions)
        assertEquals(8, handicap.conditionCount)
        assertEquals(
            MarketNextCheckReason.QUORUM,
            handicap.nextCheck?.reason
        )
        assertEquals(
            SignalFactor.FORM,
            handicap.nextCheck?.factor
        )
        assertEquals(
            MarketFactorState.PARTIAL,
            handicap.factor(SignalFactor.FORM).state
        )
        assertEquals(
            MarketFactorState.COVERED,
            handicap.factor(SignalFactor.LOAD).state
        )
    }

    @Test
    fun freshQuorumCoversChecklist() {
        val lens = evaluate(
            sport = "Футбол",
            assessment = assessment(80),
            evidence = quorumEvidence()
        )
        val handicap = requireNotNull(
            lens.item(MarketKind.HANDICAP)
        )

        assertEquals(
            MarketLensStatus.COVERED,
            handicap.status
        )
        assertEquals(
            handicap.conditionCount,
            handicap.metConditions
        )
        assertEquals(
            MarketNextCheckReason.MAINTENANCE,
            handicap.nextCheck?.reason
        )
        assertEquals(
            MarketFactorState.COVERED,
            handicap.factor(SignalFactor.FORM).state
        )
    }

    @Test
    fun expiringCriticalQuorumReturnsMarketToCheck() {
        val lineupValidFor = FreshnessPolicy.validForMillis(
            SignalFactor.LINEUP
        )
        val checkedAt = NOW -
            lineupValidFor * 3L / 4L
        val timeline = timeline(NOW).withCheckedAt(
            SignalFactor.LINEUP,
            checkedAt
        )
        val lens = MarketLensEngine.evaluate(
            sport = "Футбол",
            assessment = assessment(80),
            evidence = quorumEvidence(),
            timeline = timeline,
            now = NOW
        )
        val handicap = requireNotNull(
            lens.item(MarketKind.HANDICAP)
        )

        assertEquals(
            MarketLensStatus.CHECK,
            handicap.status
        )
        assertEquals(
            MarketNextCheckReason.FRESHNESS,
            handicap.nextCheck?.reason
        )
        assertEquals(
            SignalFactor.LINEUP,
            handicap.nextCheck?.factor
        )
        assertEquals(
            MarketFactorState.PARTIAL,
            handicap.factor(SignalFactor.LINEUP).state
        )
    }

    @Test
    fun lowSubjectiveValuesDoNotCloseVerifiedChecklist() {
        val values = SignalAssessment(
            listOf(39, 12, 80, 80, 20)
        )
        val lens = evaluate(
            sport = "Футбол",
            assessment = values,
            evidence = quorumEvidence()
        )
        val handicap = requireNotNull(
            lens.item(MarketKind.HANDICAP)
        )

        assertEquals(MarketLensStatus.COVERED, handicap.status)
        assertEquals(
            MarketFactorState.COVERED,
            handicap.factor(SignalFactor.LINEUP).state
        )
        assertEquals(
            MarketNextCheckReason.MAINTENANCE,
            handicap.nextCheck?.reason
        )
        assertFalse(
            handicap.factor(SignalFactor.LOAD).critical
        )
    }

    @Test
    fun resultKeepsGuideOrderAndDoesNotMutateInputs() {
        val assessment = assessment(80)
        val evidence = quorumEvidence()
        val lens = evaluate(
            sport = "Футбол",
            assessment = assessment,
            evidence = evidence
        )

        assertEquals(
            DemoCatalog.markets.map(MarketGuide::kind),
            lens.items.map { it.guide.kind }
        )
        assertEquals(List(5) { 80 }, assessment.values)
        assertEquals(
            List(5) { EvidenceLevel.QUORUM },
            evidence.levels
        )
        assertSame(
            lens.items.first().factors.first().freshness,
            lens.freshness.factor(SignalFactor.FORM)
        )
        assertTrue(lens.coveredCount > 0)
    }

    private fun evaluate(
        sport: String,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): MarketLensResult {
        return MarketLensEngine.evaluate(
            sport = sport,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline(NOW),
            now = NOW
        )
    }

    private fun assessment(value: Int): SignalAssessment {
        return SignalAssessment(List(5) { value })
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
    }

    private fun timeline(timestamp: Long): EvidenceTimeline {
        return EvidenceTimeline(List(5) { timestamp })
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
