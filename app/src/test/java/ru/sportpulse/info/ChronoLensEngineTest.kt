package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronoLensEngineTest {
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = 1_800_000_000_000L

    @Test
    fun nowSnapshotMatchesTheSharedProcessingPipeline() {
        val input = input(
            evidence = levels(EvidenceLevel.QUORUM),
            audit = audits(SourceAuditState.INDEPENDENT)
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now
        )
        val freshness = FreshnessEngine.evaluate(
            evidence = input.claimedEvidence,
            timeline = input.timeline,
            now = now
        )
        val expected = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence
        )

        assertEquals(
            expected.effectiveSignal,
            result.baseline.evidenceResult.effectiveSignal
        )
        assertEquals(result.baseline, result.selected)
        assertEquals(ChronoLensState.STABLE, result.state)
        assertTrue(result.changedFactors.isEmpty())
    }

    @Test
    fun sourceAuditIsAppliedBeforeFutureFreshness() {
        val claimed = EvidenceAssessment(
            listOf(
                EvidenceLevel.QUORUM,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE
            )
        )
        val input = input(
            evidence = claimed,
            audit = SourceAuditAssessment(
                listOf(
                    SourceAuditState.SHARED_LINEAGE,
                    SourceAuditState.UNAUDITED,
                    SourceAuditState.UNAUDITED,
                    SourceAuditState.UNAUDITED,
                    SourceAuditState.UNAUDITED
                )
            )
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now
        )

        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            result.baseline.freshness.effectiveEvidence.level(
                SignalFactor.FORM
            )
        )
        assertFalse(
            result.checkpoints.any { checkpoint ->
                checkpoint.changes.any {
                    it.factor == SignalFactor.FORM &&
                        it.fromLevel == EvidenceLevel.QUORUM
                }
            }
        )
    }

    @Test
    fun lineupQuorumDropsAtExactSixAndTwelveHourBoundaries() {
        val input = input(
            evidence = levels(EvidenceLevel.QUORUM),
            audit = audits(SourceAuditState.INDEPENDENT)
        )

        val atSixHours = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 6L * hour
        )
        val atTwelveHours = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 12L * hour
        )

        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            atSixHours.selected.freshness.effectiveEvidence.level(
                SignalFactor.LINEUP
            )
        )
        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            atTwelveHours.selected.freshness.effectiveEvidence.level(
                SignalFactor.LINEUP
            )
        )
    }

    @Test
    fun checkpointsExposeExpiringAndBothLevelDrops() {
        val input = input(
            evidence = EvidenceAssessment(
                listOf(
                    EvidenceLevel.UNCONFIRMED,
                    EvidenceLevel.QUORUM,
                    EvidenceLevel.UNCONFIRMED,
                    EvidenceLevel.UNCONFIRMED,
                    EvidenceLevel.UNCONFIRMED
                )
            ),
            audit = audits(SourceAuditState.INDEPENDENT)
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now
        )
        val lineup = result.checkpoints.flatMap {
            checkpoint -> checkpoint.changes.map {
                checkpoint.at to it
            }
        }.filter { it.second.factor == SignalFactor.LINEUP }

        assertEquals(3, lineup.size)
        assertEquals(now + 4L * hour + 30L * 60_000L, lineup[0].first)
        assertEquals(
            ChronoLensChangeKind.EXPIRING,
            lineup[0].second.kind
        )
        assertEquals(now + 6L * hour, lineup[1].first)
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            lineup[1].second.toLevel
        )
        assertEquals(now + 12L * hour, lineup[2].first)
        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            lineup[2].second.toLevel
        )
    }

    @Test
    fun selectedTimeIsClampedToTheTransparentHorizon() {
        val input = input(
            evidence = levels(EvidenceLevel.UNCONFIRMED),
            audit = audits(SourceAuditState.UNAUDITED)
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = Long.MAX_VALUE
        )

        assertEquals(
            now + ChronoLensPolicy.MIN_HORIZON_MILLIS,
            result.horizonAt
        )
        assertEquals(result.horizonAt, result.selectedAt)
        assertTrue(result.checkpoints.isEmpty())
    }

    @Test
    fun marketCoverageNarrowsWhenCriticalEvidenceStartsExpiring() {
        val input = input(
            assessment = assessment(85),
            evidence = levels(EvidenceLevel.QUORUM),
            audit = audits(SourceAuditState.INDEPENDENT),
            counterReview = CounterReviewAssessment.cleared()
        )

        val before = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 4L * hour
        )
        val expiring = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 4L * hour + 30L * 60_000L
        )

        assertTrue(before.selected.marketLens.coveredCount > 0)
        assertTrue(
            expiring.selected.marketLens.coveredCount <
                before.selected.marketLens.coveredCount
        )
        assertTrue(
            expiring.state == ChronoLensState.DOWNGRADE ||
                expiring.state == ChronoLensState.STOP
        )
    }

    @Test
    fun farProjectionShowsLostLevelsWithoutMutatingInput() {
        val evidence = levels(EvidenceLevel.QUORUM)
        val timeline = timeline(now)
        val input = input(
            evidence = evidence,
            audit = audits(SourceAuditState.INDEPENDENT),
            timeline = timeline
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 144L * hour
        )

        assertEquals(
            SignalFactor.values().toList(),
            result.changedFactors
        )
        assertTrue(
            result.selected.freshness.effectiveEvidence.levels.all {
                it == EvidenceLevel.UNCONFIRMED
            }
        )
        assertEquals(
            List(SignalFactor.values().size) {
                EvidenceLevel.QUORUM
            },
            evidence.levels
        )
        assertEquals(
            List(SignalFactor.values().size) { now },
            timeline.checkedAt
        )
    }

    @Test
    fun decisionGuardCanTriggerInProjectionWithoutCreatingABreach() {
        val assessment = assessment(85)
        val evidence = levels(EvidenceLevel.QUORUM)
        val timeline = timeline(now)
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "event",
            decision = SavedDecision.DATA_READY,
            savedAt = now,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            counterReview = CounterReviewAssessment.cleared()
        )
        val input = input(
            assessment = assessment,
            evidence = evidence,
            audit = audits(SourceAuditState.INDEPENDENT),
            timeline = timeline,
            counterReview = CounterReviewAssessment.cleared(),
            decisionSnapshot = snapshot
        )

        val result = ChronoLensEngine.evaluate(
            input = input,
            now = now,
            selectedAt = now + 144L * hour
        )

        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            result.selected.decisionGuard?.status
        )
        assertEquals(null, result.selected.decisionGuard?.breach)
        assertEquals(ChronoLensState.STOP, result.state)
    }

    @Test
    fun fingerprintIsStableInsideTheSameSelectedMinute() {
        val input = input(
            evidence = levels(EvidenceLevel.SINGLE_SOURCE),
            audit = audits(SourceAuditState.UNAUDITED)
        )

        val first = ChronoLensEngine.evaluate(
            input = input,
            now = now + 5_000L,
            selectedAt = now + hour + 10_000L
        )
        val second = ChronoLensEngine.evaluate(
            input = input,
            now = now + 35_000L,
            selectedAt = now + hour + 40_000L
        )

        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun fingerprintChangesWithSelectedMinuteAndAudit() {
        val base = input(
            evidence = levels(EvidenceLevel.QUORUM),
            audit = audits(SourceAuditState.INDEPENDENT)
        )
        val differentAudit = base.copy(
            sourceAudit = audits(SourceAuditState.SHARED_LINEAGE)
        )

        val first = ChronoLensEngine.evaluate(
            input = base,
            now = now,
            selectedAt = now + hour
        )
        val later = ChronoLensEngine.evaluate(
            input = base,
            now = now,
            selectedAt = now + hour + 60_000L
        )
        val audited = ChronoLensEngine.evaluate(
            input = differentAudit,
            now = now,
            selectedAt = now + hour
        )

        assertNotEquals(first.fingerprint, later.fingerprint)
        assertNotEquals(first.fingerprint, audited.fingerprint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decisionSnapshotMustBelongToTheSameEvent() {
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "other",
            decision = SavedDecision.SKIP,
            savedAt = now,
            assessment = assessment(50),
            evidence = levels(EvidenceLevel.SINGLE_SOURCE),
            timeline = timeline(now)
        )

        input(decisionSnapshot = snapshot)
    }

    private fun input(
        assessment: SignalAssessment = assessment(78),
        evidence: EvidenceAssessment =
            levels(EvidenceLevel.SINGLE_SOURCE),
        audit: SourceAuditAssessment =
            audits(SourceAuditState.UNAUDITED),
        timeline: EvidenceTimeline = timeline(now),
        counterReview: CounterReviewAssessment =
            CounterReviewAssessment.unchecked(),
        decisionSnapshot: DecisionSnapshot? = null
    ): ChronoLensInput {
        return ChronoLensInput(
            eventId = "event",
            sport = "Футбол",
            assessment = assessment,
            claimedEvidence = evidence,
            sourceAudit = audit,
            timeline = timeline,
            counterReview = counterReview,
            decisionSnapshot = decisionSnapshot
        )
    }

    private fun assessment(value: Int): SignalAssessment {
        return SignalAssessment(
            List(SignalFactor.values().size) { value }
        )
    }

    private fun levels(level: EvidenceLevel): EvidenceAssessment {
        return EvidenceAssessment(
            List(SignalFactor.values().size) { level }
        )
    }

    private fun audits(
        state: SourceAuditState
    ): SourceAuditAssessment {
        return SourceAuditAssessment(
            List(SignalFactor.values().size) { state }
        )
    }

    private fun timeline(checkedAt: Long): EvidenceTimeline {
        return EvidenceTimeline(
            List(SignalFactor.values().size) { checkedAt }
        )
    }
}
