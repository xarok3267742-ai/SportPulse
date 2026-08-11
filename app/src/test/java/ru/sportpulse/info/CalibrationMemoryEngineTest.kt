package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationMemoryEngineTest {
    private val allConfirmed = List(5) {
        PostEventOutcome.CONFIRMED
    }

    @Test
    fun emptyMemoryStartsLearningWithoutInventedScore() {
        val memory = CalibrationMemoryEngine.evaluate(emptyList())

        assertEquals(CalibrationMemoryStatus.LEARNING, memory.status)
        assertNull(memory.overallScore)
        assertEquals(0, memory.reviewCount)
        assertEquals(0, memory.coveragePercent)
        assertNull(memory.focusProfile)
    }

    @Test
    fun oneReviewRemainsLearning() {
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(record(1, allConfirmed))
        )

        assertEquals(CalibrationMemoryStatus.LEARNING, memory.status)
        assertEquals(100, memory.overallScore)
        assertEquals(5, memory.verifiedFactorCount)
        assertEquals(100, memory.coveragePercent)
    }

    @Test
    fun overallScoreWeightsEveryVerifiedFactor() {
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(
                    1,
                    listOf(
                        PostEventOutcome.CONFIRMED,
                        PostEventOutcome.UNKNOWN,
                        PostEventOutcome.UNKNOWN,
                        PostEventOutcome.UNKNOWN,
                        PostEventOutcome.UNKNOWN
                    )
                ),
                record(
                    2,
                    List(5) { PostEventOutcome.DISPROVED }
                )
            )
        )

        assertEquals(17, memory.overallScore)
        assertEquals(6, memory.verifiedFactorCount)
        assertEquals(60, memory.coveragePercent)
    }

    @Test
    fun threeStrongReviewsProduceStableProfile() {
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(1, allConfirmed),
                record(2, allConfirmed),
                record(3, allConfirmed)
            )
        )

        assertEquals(CalibrationMemoryStatus.STABLE, memory.status)
        assertEquals(100, memory.overallScore)
        assertEquals(15, memory.verifiedFactorCount)
        assertEquals(0, memory.criticalMissCount)
    }

    @Test
    fun recentCriticalMissOverridesHighAverage() {
        val critical = listOf(
            PostEventOutcome.DISPROVED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED
        )
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(1, critical, quorum = true),
                record(2, allConfirmed, quorum = true),
                record(3, allConfirmed, quorum = true)
            )
        )

        assertEquals(93, memory.overallScore)
        assertEquals(
            CalibrationMemoryStatus.BLIND_SPOT,
            memory.status
        )
        assertEquals(1, memory.criticalMissCount)
        assertEquals(
            SignalFactor.FORM,
            memory.focusProfile?.factor
        )
    }

    @Test
    fun singleCriticalMissOutranksFactorsWithLargerSamples() {
        val criticalOnlyOnce = listOf(
            PostEventOutcome.DISPROVED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED
        )
        val formUnavailable = listOf(
            PostEventOutcome.UNKNOWN,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED
        )
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(1, criticalOnlyOnce, quorum = true),
                record(2, formUnavailable, quorum = true),
                record(3, formUnavailable, quorum = true)
            )
        )

        assertEquals(
            CalibrationMemoryStatus.BLIND_SPOT,
            memory.status
        )
        assertEquals(
            SignalFactor.FORM,
            memory.focusProfile?.factor
        )
        assertEquals(1, memory.focusProfile?.verifiedCount)
        assertEquals(1, memory.focusProfile?.criticalMissCount)
    }

    @Test
    fun repeatedWeakFactorBecomesFocus() {
        val lineupMiss = listOf(
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.DISPROVED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED,
            PostEventOutcome.CONFIRMED
        )
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(1, lineupMiss),
                record(2, lineupMiss),
                record(3, lineupMiss)
            )
        )
        val lineup = memory.factorProfiles[
            SignalFactor.LINEUP.ordinal
        ]

        assertEquals(SignalFactor.LINEUP, memory.focusProfile?.factor)
        assertEquals(0, lineup.score)
        assertEquals(3, lineup.disprovedCount)
        assertEquals(
            CalibrationMemoryStatus.BLIND_SPOT,
            memory.status
        )
    }

    @Test
    fun latestTwoPairsProduceImprovingTrend() {
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(
                    1,
                    List(5) { PostEventOutcome.DISPROVED }
                ),
                record(
                    2,
                    List(5) { PostEventOutcome.PARTIAL }
                ),
                record(3, allConfirmed),
                record(4, allConfirmed)
            )
        )

        assertEquals(
            CalibrationTrendStatus.IMPROVING,
            memory.trend.status
        )
        assertEquals(25, memory.trend.previousScore)
        assertEquals(100, memory.trend.recentScore)
        assertEquals(75, memory.trend.delta)
    }

    @Test
    fun fewerThanFourScoredReviewsHaveNoTrend() {
        val memory = CalibrationMemoryEngine.evaluate(
            listOf(
                record(1, allConfirmed),
                record(2, allConfirmed),
                record(3, allConfirmed)
            )
        )

        assertEquals(
            CalibrationTrendStatus.INSUFFICIENT,
            memory.trend.status
        )
        assertNull(memory.trend.delta)
    }

    @Test
    fun chainFingerprintIsOrderIndependentButDataSensitive() {
        val first = record(1, allConfirmed)
        val second = record(2, allConfirmed)
        val changed = record(
            2,
            listOf(
                PostEventOutcome.PARTIAL,
                PostEventOutcome.CONFIRMED,
                PostEventOutcome.CONFIRMED,
                PostEventOutcome.CONFIRMED,
                PostEventOutcome.CONFIRMED
            )
        )

        val ordered = CalibrationMemoryEngine.evaluate(
            listOf(first, second)
        )
        val reversed = CalibrationMemoryEngine.evaluate(
            listOf(second, first)
        )
        val modified = CalibrationMemoryEngine.evaluate(
            listOf(first, changed)
        )

        assertEquals(ordered.fingerprint, reversed.fingerprint)
        assertNotEquals(ordered.fingerprint, modified.fingerprint)
        assertEquals(10, ordered.shortFingerprint.length)
    }

    @Test
    fun duplicateEventCannotEnterMemoryTwice() {
        val first = record(1, allConfirmed)

        assertThrows(IllegalArgumentException::class.java) {
            CalibrationMemoryEngine.evaluate(
                listOf(first, first)
            )
        }
    }

    @Test
    fun storedCatalogRestoresLinkedFinalReviewsInTimeOrder() {
        val first = record(1, allConfirmed)
        val second = record(2, allConfirmed)
        val snapshots = listOf(first, second).associate {
            it.snapshot.eventId to it.snapshot
        }
        val stored = linkedMapOf<String, Any>(
            StoredCalibrationRecordCatalog.keyFor(
                second.review.eventId
            ) to PostEventReviewCodec.encode(second.review),
            StoredCalibrationRecordCatalog.keyFor(
                first.review.eventId
            ) to PostEventReviewCodec.encode(first.review)
        )

        val decoded = StoredCalibrationRecordCatalog.decode(
            stored
        ) { snapshots[it] }

        assertEquals(
            listOf(first.review, second.review),
            decoded.map { it.review }
        )
    }

    @Test
    fun storedCatalogIgnoresMalformedDraftAndWrongKey() {
        val final = record(1, allConfirmed)
        val draftSnapshot = record(2, allConfirmed).snapshot
        val draft = PostEventReviewFactory.start(
            draftSnapshot,
            draftSnapshot.savedAt + 1L
        )
        val stored = linkedMapOf<String, Any>(
            StoredCalibrationRecordCatalog.keyFor("broken") to
                "not-a-review",
            StoredCalibrationRecordCatalog.keyFor("wrong_event") to
                PostEventReviewCodec.encode(final.review),
            StoredCalibrationRecordCatalog.keyFor(
                draft.eventId
            ) to PostEventReviewCodec.encode(draft)
        )

        val decoded = StoredCalibrationRecordCatalog.decode(
            stored
        ) { eventId ->
            when (eventId) {
                final.snapshot.eventId -> final.snapshot
                draftSnapshot.eventId -> draftSnapshot
                else -> null
            }
        }

        assertTrue(decoded.isEmpty())
    }

    private fun record(
        index: Int,
        outcomes: List<PostEventOutcome>,
        quorum: Boolean = false
    ): CalibrationRecord {
        val savedAt = index * 1_000L
        val evidence = EvidenceAssessment(
            List(5) {
                if (quorum) {
                    EvidenceLevel.QUORUM
                } else {
                    EvidenceLevel.SINGLE_SOURCE
                }
            }
        )
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "event_$index",
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = SignalAssessment(List(5) { 70 }),
            evidence = evidence,
            timeline = EvidenceTimeline(List(5) { savedAt })
        )
        var review = PostEventReviewFactory.start(
            snapshot,
            savedAt + 1L
        )
        outcomes.forEachIndexed { factorIndex, outcome ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = SignalFactor.values()[factorIndex],
                outcome = outcome,
                now = review.updatedAt + 1L
            )
        }
        review = PostEventReviewFactory.finalize(
            review,
            snapshot,
            review.updatedAt + 1L
        )
        return CalibrationRecord(snapshot, review)
    }
}
