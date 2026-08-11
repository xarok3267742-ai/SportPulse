package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDuelEngineTest {
    private val now = 20L * FreshnessPolicy.HOUR_MILLIS

    @Test
    fun strongerProfileLeadsAllTransparentLanes() {
        val left = input(
            id = "left",
            assessment = SignalAssessment(List(5) { 72 })
        )
        val right = input(
            id = "right",
            assessment = SignalAssessment(
                listOf(90, 18, 75, 22, 54)
            ),
            evidence = EvidenceAssessment.singleSource(),
            audit = SourceAuditAssessment.unaudited(),
            timeline = timeline(
                now - 7L * FreshnessPolicy.HOUR_MILLIS
            ),
            review = CounterReviewAssessment.unchecked()
        )

        val result = DataDuelEngine.evaluate(left, right, now)

        assertEquals(DataDuelSide.LEFT, result.balance)
        assertEquals(6, result.leftWins)
        assertEquals(0, result.rightWins)
        assertEquals(0, result.ties)
    }

    @Test
    fun unauditedQuorumCannotWinFreshQuorumLane() {
        val unaudited = input(
            id = "unaudited",
            audit = SourceAuditAssessment.unaudited()
        )
        val audited = input(id = "audited")

        val result = DataDuelEngine.evaluate(
            unaudited,
            audited,
            now
        )

        assertEquals(0, result.left.quorumCount)
        assertEquals(5, result.right.quorumCount)
        assertEquals(
            DataDuelSide.RIGHT,
            result.metric(DataDuelMetricKind.QUORUMS).leader
        )
    }

    @Test
    fun freshnessIsAppliedBeforeComparison() {
        val fresh = input(id = "fresh")
        val older = input(
            id = "older",
            timeline = timeline(
                now - 7L * FreshnessPolicy.HOUR_MILLIS
            )
        )

        val result = DataDuelEngine.evaluate(
            fresh,
            older,
            now
        )

        assertEquals(5, result.left.quorumCount)
        assertEquals(4, result.right.quorumCount)
        assertTrue(
            result.left.freshnessReserveMinutes >
                result.right.freshnessReserveMinutes
        )
    }

    @Test
    fun freshnessDifferenceBelowFiveMinutesIsATie() {
        val left = input(
            id = "left",
            timeline = timeline(now)
        )
        val right = input(
            id = "right",
            timeline = timeline(now - 4L * 60_000L)
        )

        val result = DataDuelEngine.evaluate(
            left,
            right,
            now
        )

        assertEquals(
            DataDuelSide.TIE,
            result.metric(
                DataDuelMetricKind.FRESHNESS_RESERVE
            ).leader
        )
    }

    @Test
    fun counterReviewCompletenessAndCeilingStayVisible() {
        val cleared = input(id = "cleared")
        val unchecked = input(
            id = "unchecked",
            review = CounterReviewAssessment.unchecked()
        )

        val result = DataDuelEngine.evaluate(
            cleared,
            unchecked,
            now
        )

        assertEquals(5, result.left.counterReviewedCount)
        assertEquals(0, result.right.counterReviewedCount)
        assertEquals(
            SavedDecision.DATA_READY,
            result.left.decisionCeiling
        )
        assertEquals(
            SavedDecision.SKIP,
            result.right.decisionCeiling
        )
    }

    @Test
    fun equalProfilesProduceSixTiesWithoutInventingWinner() {
        val result = DataDuelEngine.evaluate(
            input(id = "first"),
            input(id = "second"),
            now
        )

        assertEquals(DataDuelSide.TIE, result.balance)
        assertEquals(0, result.leftWins)
        assertEquals(0, result.rightWins)
        assertEquals(6, result.ties)
    }

    @Test
    fun swappingSidesFlipsBalanceAndBindsFingerprint() {
        val strong = input(id = "strong")
        val weak = input(
            id = "weak",
            assessment = SignalAssessment(List(5) { 24 }),
            evidence = EvidenceAssessment.singleSource(),
            audit = SourceAuditAssessment.unaudited(),
            review = CounterReviewAssessment.unchecked()
        )

        val forward = DataDuelEngine.evaluate(
            strong,
            weak,
            now
        )
        val reversed = DataDuelEngine.evaluate(
            weak,
            strong,
            now
        )

        assertEquals(DataDuelSide.LEFT, forward.balance)
        assertEquals(DataDuelSide.RIGHT, reversed.balance)
        assertNotEquals(forward.fingerprint, reversed.fingerprint)
    }

    @Test
    fun fingerprintIsStableInsideSameEvaluationMinute() {
        val checkedAt = 10L * FreshnessPolicy.HOUR_MILLIS
        val left = input(
            id = "left",
            timeline = timeline(checkedAt)
        )
        val right = input(
            id = "right",
            timeline = timeline(checkedAt)
        )
        val firstNow =
            checkedAt + 10_000L
        val secondNow =
            checkedAt + 30_000L

        val first = DataDuelEngine.evaluate(
            left,
            right,
            firstNow
        )
        val second = DataDuelEngine.evaluate(
            left,
            right,
            secondNow
        )

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(8, first.shortFingerprint.length)
    }

    @Test
    fun fingerprintChangesWhenAuditChanges() {
        val original = DataDuelEngine.evaluate(
            input(id = "left"),
            input(id = "right"),
            now
        )
        val changed = DataDuelEngine.evaluate(
            input(
                id = "left",
                audit = SourceAuditAssessment.unaudited()
            ),
            input(id = "right"),
            now
        )

        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    @Test
    fun sameEventCannotBeComparedWithItself() {
        assertThrows(IllegalArgumentException::class.java) {
            DataDuelEngine.evaluate(
                input(id = "same"),
                input(id = "same"),
                now
            )
        }
    }

    private fun input(
        id: String,
        assessment: SignalAssessment =
            SignalAssessment(List(5) { 68 }),
        evidence: EvidenceAssessment =
            EvidenceAssessment(
                List(5) { EvidenceLevel.QUORUM }
            ),
        audit: SourceAuditAssessment =
            SourceAuditAssessment(
                List(5) { SourceAuditState.INDEPENDENT }
            ),
        timeline: EvidenceTimeline = timeline(now),
        review: CounterReviewAssessment =
            CounterReviewAssessment.cleared()
    ): DataDuelInput {
        return DataDuelInput(
            eventId = id,
            assessment = assessment,
            claimedEvidence = evidence,
            sourceAudit = audit,
            timeline = timeline,
            counterReview = review
        )
    }

    private fun timeline(checkedAt: Long): EvidenceTimeline {
        return EvidenceTimeline(List(5) { checkedAt })
    }
}
