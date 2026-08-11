package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTraceEngineTest {
    private val savedAt = 100L * FreshnessPolicy.HOUR_MILLIS
    private val quorum = EvidenceAssessment(
        List(5) { EvidenceLevel.QUORUM }
    )
    private val timeline = EvidenceTimeline(List(5) { savedAt })

    @Test
    fun codecRoundTripPreservesSnapshotAndFingerprint() {
        val snapshot = snapshot(
            assessment = SignalAssessment(listOf(62, 38, 58, 70, 46)),
            evidence = EvidenceAssessment.singleSource()
        )

        val decoded = DecisionSnapshotCodec.decode(
            DecisionSnapshotCodec.encode(snapshot)
        )

        assertEquals(snapshot, decoded)
        assertEquals(8, snapshot.shortFingerprint.length)
    }

    @Test
    fun codecRejectsPayloadChangedAfterSealing() {
        val snapshot = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum
        )
        val encoded = DecisionSnapshotCodec.encode(snapshot)
        val tampered = encoded.replace("60,60,60", "61,60,60")

        assertNull(DecisionSnapshotCodec.decode(tampered))
    }

    @Test
    fun fingerprintChangesWhenOneFactChanges() {
        val first = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum
        )
        val second = snapshot(
            assessment = SignalAssessment(
                listOf(61, 60, 60, 60, 60)
            ),
            evidence = quorum
        )

        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun fingerprintChangesWhenCounterViewChanges() {
        val first = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum,
            counterReview = CounterReviewAssessment.cleared()
        )
        val second = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum,
            counterReview =
                CounterReviewAssessment.cleared().withState(
                    SignalFactor.FORM,
                    CounterReviewState.MIXED
                )
        )

        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun fingerprintBindsDistanceClearanceToNewSnapshot() {
        val first = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum
        )
        val second = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum,
            distanceClearanceFingerprint = "a".repeat(64)
        )

        assertEquals(4, first.formatVersion)
        assertEquals(
            "a".repeat(64),
            DecisionSnapshotCodec.decode(
                DecisionSnapshotCodec.encode(second)
            )?.distanceClearanceFingerprint
        )
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun fingerprintBindsAttentionBudgetToNewSnapshot() {
        val first = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum
        )
        val second = snapshot(
            assessment = SignalAssessment(List(5) { 60 }),
            evidence = quorum,
            attentionBudgetFingerprint = "b".repeat(64)
        )

        assertEquals(
            "b".repeat(64),
            DecisionSnapshotCodec.decode(
                DecisionSnapshotCodec.encode(second)
            )?.attentionBudgetFingerprint
        )
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun legacySnapshotMigratesWithoutInventingCounterReview() {
        val eventId = "legacy_event"
        val encodedEventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                eventId.toByteArray(StandardCharsets.UTF_8)
            )
        val payload = listOf(
            "1",
            encodedEventId,
            SavedDecision.OBSERVE.name,
            savedAt.toString(),
            "60,60,60,60,60",
            List(5) { EvidenceLevel.QUORUM.name }
                .joinToString(","),
            List(5) { savedAt }.joinToString(",")
        ).joinToString("|")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }

        val decoded = DecisionSnapshotCodec.decode(
            "$payload|$fingerprint"
        )

        assertNotNull(decoded)
        assertEquals(1, decoded?.formatVersion)
        assertEquals(
            CounterReviewAssessment.unchecked(),
            decoded?.counterReview
        )
        assertNull(decoded?.distanceClearanceFingerprint)
        assertNull(decoded?.attentionBudgetFingerprint)
    }

    @Test
    fun versionTwoSnapshotMigratesWithoutInventingClearance() {
        val encodedEventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "version_two".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        val payload = listOf(
            "2",
            encodedEventId,
            SavedDecision.OBSERVE.name,
            savedAt.toString(),
            "60,60,60,60,60",
            List(5) { EvidenceLevel.QUORUM.name }
                .joinToString(","),
            List(5) { savedAt }.joinToString(","),
            List(5) { CounterReviewState.CLEAR.name }
                .joinToString(",")
        ).joinToString("|")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }

        val decoded = DecisionSnapshotCodec.decode(
            "$payload|$fingerprint"
        )

        assertNotNull(decoded)
        assertEquals(2, decoded?.formatVersion)
        assertNull(decoded?.distanceClearanceFingerprint)
        assertNull(decoded?.attentionBudgetFingerprint)
    }

    @Test
    fun versionThreeSnapshotMigratesWithoutInventingBudget() {
        val encodedEventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "version_three".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        val payload = listOf(
            "3",
            encodedEventId,
            SavedDecision.DATA_READY.name,
            savedAt.toString(),
            "60,60,60,60,60",
            List(5) { EvidenceLevel.QUORUM.name }
                .joinToString(","),
            List(5) { savedAt }.joinToString(","),
            List(5) { CounterReviewState.CLEAR.name }
                .joinToString(","),
            "a".repeat(64)
        ).joinToString("|")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }

        val decoded = DecisionSnapshotCodec.decode(
            "$payload|$fingerprint"
        )

        assertNotNull(decoded)
        assertEquals(3, decoded?.formatVersion)
        assertEquals(
            "a".repeat(64),
            decoded?.distanceClearanceFingerprint
        )
        assertNull(decoded?.attentionBudgetFingerprint)
    }

    @Test
    fun traceLabelsChangedAssessmentAsNewFacts() {
        val baseline = SignalAssessment(List(5) { 60 })
        val current = baseline.withValue(SignalFactor.LINEUP, 80)

        val result = DecisionTraceEngine.compare(
            snapshot = snapshot(baseline, quorum),
            currentAssessment = current,
            currentEvidence = quorum,
            currentTimeline = timeline,
            now = savedAt
        )

        val lineup = result.factorDeltas[SignalFactor.LINEUP.ordinal]
        assertEquals(20, lineup.valueDelta)
        assertEquals(
            setOf(DecisionChangeCause.FACTS),
            lineup.causes
        )
        assertTrue(result.changedFactors.contains(lineup))
    }

    @Test
    fun traceLabelsRefreshedSourceAsConfirmation() {
        val assessment = SignalAssessment(List(5) { 90 })
        val evidence = EvidenceAssessment.singleSource()
        val refreshedEvidence = evidence.withLevel(
            SignalFactor.FORM,
            EvidenceLevel.QUORUM
        )
        val refreshedTimeline = timeline.withCheckedAt(
            SignalFactor.FORM,
            savedAt + 1L
        )

        val result = DecisionTraceEngine.compare(
            snapshot = snapshot(assessment, evidence),
            currentAssessment = assessment,
            currentEvidence = refreshedEvidence,
            currentTimeline = refreshedTimeline,
            now = savedAt + 1L
        )

        val form = result.factorDeltas[SignalFactor.FORM.ordinal]
        assertEquals(30, form.valueDelta)
        assertEquals(
            setOf(DecisionChangeCause.CONFIRMATION),
            form.causes
        )
    }

    @Test
    fun traceSeparatesExpiryFromUserChanges() {
        val assessment = SignalAssessment(List(5) { 90 })

        val result = DecisionTraceEngine.compare(
            snapshot = snapshot(assessment, quorum),
            currentAssessment = assessment,
            currentEvidence = quorum,
            currentTimeline = timeline,
            now = savedAt + 7L * FreshnessPolicy.HOUR_MILLIS
        )

        val lineup = result.factorDeltas[SignalFactor.LINEUP.ordinal]
        assertEquals(-30, lineup.valueDelta)
        assertEquals(
            setOf(DecisionChangeCause.FRESHNESS),
            lineup.causes
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            lineup.currentEvidence
        )
    }

    @Test
    fun unchangedStateProducesNoFalseDelta() {
        val assessment = SignalAssessment(listOf(62, 38, 58, 70, 46))
        val evidence = EvidenceAssessment.singleSource()

        val result = DecisionTraceEngine.compare(
            snapshot = snapshot(assessment, evidence),
            currentAssessment = assessment,
            currentEvidence = evidence,
            currentTimeline = timeline,
            now = savedAt
        )

        assertTrue(result.changedFactors.isEmpty())
        assertEquals(0, result.readinessDelta)
        assertFalse(result.verdictChanged)
    }

    @Test
    fun traceLabelsChangedCounterViewSeparately() {
        val baseline = SignalAssessment(List(5) { 70 })
        val snapshot = snapshot(
            assessment = baseline,
            evidence = quorum,
            counterReview = CounterReviewAssessment.cleared()
        )
        val currentReview = snapshot.counterReview.withState(
            SignalFactor.LINEUP,
            CounterReviewState.REFUTED
        )

        val result = DecisionTraceEngine.compare(
            snapshot = snapshot,
            currentAssessment = baseline,
            currentEvidence = quorum,
            currentTimeline = timeline,
            currentCounterReview = currentReview,
            now = savedAt
        )

        val lineup = result.factorDeltas[
            SignalFactor.LINEUP.ordinal
        ]
        assertEquals(
            setOf(DecisionChangeCause.COUNTERVIEW),
            lineup.causes
        )
        assertTrue(result.counterViewChanged)
        assertEquals(
            CounterViewVerdict.REFUTED,
            result.currentCounterView.verdict
        )
    }

    private fun snapshot(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        counterReview: CounterReviewAssessment =
            CounterReviewAssessment.cleared(),
        distanceClearanceFingerprint: String? = null,
        attentionBudgetFingerprint: String? = null
    ): DecisionSnapshot {
        return DecisionSnapshotFactory.create(
            eventId = "rpl_test",
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            counterReview = counterReview,
            distanceClearanceFingerprint =
                distanceClearanceFingerprint,
            attentionBudgetFingerprint =
                attentionBudgetFingerprint
        )
    }
}
