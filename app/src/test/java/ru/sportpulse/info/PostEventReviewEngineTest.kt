package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEventReviewEngineTest {
    private val savedAt = 100L * FreshnessPolicy.HOUR_MILLIS
    private val quorum = EvidenceAssessment(
        List(5) { EvidenceLevel.QUORUM }
    )
    private val timeline = EvidenceTimeline(
        List(5) { savedAt }
    )

    @Test
    fun codecRoundTripPreservesDraftAndFingerprint() {
        val review = PostEventReviewFactory.start(
            snapshot(),
            savedAt + 1L
        )

        val decoded = PostEventReviewCodec.decode(
            PostEventReviewCodec.encode(review)
        )

        assertEquals(review, decoded)
        assertFalse(review.isFinalized)
        assertEquals(8, review.shortFingerprint.length)
    }

    @Test
    fun codecRejectsTamperedOutcome() {
        val review = completedReview(
            List(5) { PostEventOutcome.CONFIRMED }
        )
        val encoded = PostEventReviewCodec.encode(review)
        val tampered = encoded.replaceFirst(
            PostEventOutcome.CONFIRMED.name,
            PostEventOutcome.DISPROVED.name
        )

        assertNull(PostEventReviewCodec.decode(tampered))
    }

    @Test
    fun fingerprintChangesWithOneOutcome() {
        val confirmed = completedReview(
            List(5) { PostEventOutcome.CONFIRMED }
        )
        val partial = completedReview(
            List(5) { index ->
                if (index == 0) {
                    PostEventOutcome.PARTIAL
                } else {
                    PostEventOutcome.CONFIRMED
                }
            }
        )

        assertNotEquals(
            confirmed.fingerprint,
            partial.fingerprint
        )
    }

    @Test
    fun finalizationRequiresEveryFactorAnswered() {
        val snapshot = snapshot()
        val draft = PostEventReviewFactory.start(
            snapshot,
            savedAt + 1L
        )

        assertThrows(IllegalArgumentException::class.java) {
            PostEventReviewFactory.finalize(
                draft,
                snapshot,
                savedAt + 2L
            )
        }
    }

    @Test
    fun finalizedReviewCannotBeEdited() {
        val snapshot = snapshot()
        val review = completedReview(
            List(5) { PostEventOutcome.CONFIRMED }
        )

        assertThrows(IllegalArgumentException::class.java) {
            PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = SignalFactor.FORM,
                outcome = PostEventOutcome.DISPROVED,
                now = review.updatedAt + 1L
            )
        }
    }

    @Test
    fun allConfirmedProducesReliableStatus() {
        val snapshot = snapshot()
        val result = PostEventReviewEngine.evaluate(
            snapshot,
            completedReview(
                List(5) { PostEventOutcome.CONFIRMED }
            )
        )

        assertEquals(PostEventReviewStatus.RELIABLE, result.status)
        assertEquals(100, result.reliabilityScore)
        assertEquals(5, result.verifiedCount)
        assertTrue(result.criticalMisses.isEmpty())
    }

    @Test
    fun quorumFailureOverridesHighAverage() {
        val snapshot = snapshot(
            assessment = SignalAssessment(
                listOf(80, 80, 80, 80, 80)
            )
        )
        val result = PostEventReviewEngine.evaluate(
            snapshot,
            completedReview(
                outcomes = listOf(
                    PostEventOutcome.DISPROVED,
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.CONFIRMED
                ),
                snapshot = snapshot
            )
        )

        assertEquals(80, result.reliabilityScore)
        assertEquals(PostEventReviewStatus.FRAGILE, result.status)
        assertEquals(
            listOf(SignalFactor.FORM),
            result.criticalMisses.map { it.factor }
        )
        assertEquals(SignalFactor.FORM, result.focusFactor)
    }

    @Test
    fun unknownFactorsDoNotDiluteReliabilityScore() {
        val result = PostEventReviewEngine.evaluate(
            snapshot(),
            completedReview(
                listOf(
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.PARTIAL,
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.UNKNOWN,
                    PostEventOutcome.UNKNOWN
                )
            )
        )

        assertEquals(83, result.reliabilityScore)
        assertEquals(3, result.verifiedCount)
        assertEquals(PostEventReviewStatus.RELIABLE, result.status)
    }

    @Test
    fun fewerThanThreeVerifiedFactorsIsNotEnough() {
        val result = PostEventReviewEngine.evaluate(
            snapshot(),
            completedReview(
                listOf(
                    PostEventOutcome.CONFIRMED,
                    PostEventOutcome.UNKNOWN,
                    PostEventOutcome.UNKNOWN,
                    PostEventOutcome.UNKNOWN,
                    PostEventOutcome.CONFIRMED
                )
            )
        )

        assertEquals(
            PostEventReviewStatus.NOT_ENOUGH_DATA,
            result.status
        )
        assertEquals(100, result.reliabilityScore)
        assertEquals(2, result.verifiedCount)
    }

    @Test
    fun reviewMustBelongToDecisionSnapshot() {
        val review = completedReview(
            List(5) { PostEventOutcome.CONFIRMED }
        )
        val other = DecisionSnapshotFactory.create(
            eventId = "other_event",
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum,
            timeline = timeline
        )

        assertThrows(IllegalArgumentException::class.java) {
            PostEventReviewEngine.evaluate(other, review)
        }
    }

    private fun completedReview(
        outcomes: List<PostEventOutcome>,
        snapshot: DecisionSnapshot = snapshot()
    ): PostEventReview {
        var review = PostEventReviewFactory.start(
            snapshot,
            savedAt + 1L
        )
        outcomes.forEachIndexed { index, outcome ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = SignalFactor.values()[index],
                outcome = outcome,
                now = review.updatedAt + 1L
            )
        }
        return PostEventReviewFactory.finalize(
            review,
            snapshot,
            review.updatedAt + 1L
        )
    }

    private fun snapshot(
        assessment: SignalAssessment =
            SignalAssessment(List(5) { 60 })
    ): DecisionSnapshot {
        return DecisionSnapshotFactory.create(
            eventId = "rpl_test",
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = assessment,
            evidence = quorum,
            timeline = timeline
        )
    }
}
