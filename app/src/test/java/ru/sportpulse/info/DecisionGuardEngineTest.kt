package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionGuardEngineTest {
    @Test
    fun skipDecisionStaysSealedInsteadOfReopeningItself() {
        val snapshot = snapshot(
            decision = SavedDecision.SKIP,
            assessment = assessment(80),
            evidence = quorumEvidence()
        )

        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = assessment(100),
            currentEvidence = quorumEvidence(),
            currentTimeline = timeline(NOW + 1_000L),
            now = NOW + 1_000L
        )

        assertEquals(
            DecisionGuardStatus.SEALED_SKIP,
            result.status
        )
        assertNull(result.plan.condition)
        assertTrue(result.causes.isEmpty())
    }

    @Test
    fun readyDecisionCreatesDeterministicArmedContract() {
        val snapshot = snapshot(
            decision = SavedDecision.DATA_READY,
            assessment = assessment(80),
            evidence = quorumEvidence()
        )

        val first = evaluateUnchanged(snapshot)
        val second = evaluateUnchanged(snapshot)

        assertEquals(DecisionGuardStatus.ARMED, first.status)
        assertEquals(SignalVerdict.READY, first.plan.requiredVerdict)
        assertEquals(SignalFactor.FORM, first.plan.condition?.factor)
        assertNotNull(first.plan.condition?.scoreFloor)
        assertEquals(EvidenceLevel.QUORUM, first.plan.condition?.requiredEvidence)
        assertEquals(64, first.plan.seal.length)
        assertEquals(first.plan.seal, second.plan.seal)
        assertFalse(first.isTriggered)
    }

    @Test
    fun decisionAboveSupportedSignalTriggersImmediately() {
        val snapshot = snapshot(
            decision = SavedDecision.DATA_READY,
            assessment = assessment(30),
            evidence = singleEvidence()
        )

        val result = evaluateUnchanged(snapshot)

        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            result.status
        )
        assertEquals(
            listOf(DecisionGuardCause.DECISION_ABOVE_SIGNAL),
            result.causes
        )
    }

    @Test
    fun sealedFactorFloorTriggersEvenWhenOtherFactorsCompensate() {
        val snapshot = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = assessment(70),
            evidence = quorumEvidence()
        )
        val armed = evaluateUnchanged(snapshot)
        val condition = requireNotNull(armed.plan.condition)
        val floor = requireNotNull(condition.scoreFloor)
        var current = assessment(100)
        current = current.withValue(condition.factor, floor)

        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = current,
            currentEvidence = quorumEvidence(),
            currentTimeline = timeline(NOW),
            now = NOW
        )

        assertTrue(
            result.currentResult.effectiveSignal.verdict.ordinal >=
                SignalVerdict.OBSERVE.ordinal
        )
        assertTrue(
            DecisionGuardCause.FACTOR_FLOOR in result.causes
        )
        assertFalse(
            DecisionGuardCause.SIGNAL_BELOW_CONTRACT in result.causes
        )
    }

    @Test
    fun lossOfSealedEvidenceTriggersContract() {
        val snapshot = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = assessment(70),
            evidence = quorumEvidence()
        )
        val armed = evaluateUnchanged(snapshot)
        val factor = requireNotNull(armed.plan.condition).factor
        val currentEvidence = quorumEvidence().withLevel(
            factor,
            EvidenceLevel.SINGLE_SOURCE
        )

        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = currentEvidence,
            currentTimeline = timeline(NOW),
            now = NOW
        )

        assertTrue(
            DecisionGuardCause.EVIDENCE_LOSS in result.causes
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            result.currentEvidence
        )
    }

    @Test
    fun freshnessExpiryCanBreakOnlyTheSealedFactor() {
        val snapshot = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = assessment(70),
            evidence = quorumEvidence()
        )
        val armed = evaluateUnchanged(snapshot)
        val condition = requireNotNull(armed.plan.condition)
        val expiry = requireNotNull(condition.evidenceValidUntil)
        var currentTimeline = timeline(expiry)
        currentTimeline = currentTimeline.withCheckedAt(
            condition.factor,
            NOW
        )

        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = quorumEvidence(),
            currentTimeline = currentTimeline,
            now = expiry
        )

        assertTrue(
            DecisionGuardCause.EVIDENCE_LOSS in result.causes
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            result.currentEvidence
        )
    }

    @Test
    fun strongerEvidenceKeepsContractArmed() {
        val snapshot = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = assessment(58),
            evidence = singleEvidence()
        )
        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = quorumEvidence(),
            currentTimeline = timeline(NOW + 5_000L),
            now = NOW + 5_000L
        )

        assertEquals(DecisionGuardStatus.ARMED, result.status)
        assertTrue(result.causes.isEmpty())
    }

    @Test
    fun counterfactTriggersPreviouslyReadyDecision() {
        val snapshot = snapshot(
            decision = SavedDecision.DATA_READY,
            assessment = assessment(80),
            evidence = quorumEvidence()
        )
        val currentReview = snapshot.counterReview.withState(
            SignalFactor.LINEUP,
            CounterReviewState.REFUTED
        )

        val result = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = snapshot.evidence,
            currentTimeline = snapshot.timeline,
            currentCounterReview = currentReview,
            now = snapshot.savedAt
        )

        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            result.status
        )
        assertTrue(
            DecisionGuardCause.COUNTERVIEW_LIMIT in
                result.causes
        )
        assertFalse(
            DecisionGuardCause.SIGNAL_BELOW_CONTRACT in
                result.causes
        )
    }

    @Test
    fun changingSnapshotChangesSeal() {
        val first = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = assessment(70),
            evidence = quorumEvidence()
        )
        val second = snapshot(
            decision = SavedDecision.OBSERVE,
            assessment = SignalAssessment(
                listOf(70, 70, 70, 70, 71)
            ),
            evidence = quorumEvidence()
        )

        val firstSeal = evaluateUnchanged(first).plan.seal
        val secondSeal = evaluateUnchanged(second).plan.seal

        assertNotEquals(firstSeal, secondSeal)
    }

    private fun evaluateUnchanged(
        snapshot: DecisionSnapshot
    ): DecisionGuardResult {
        return DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = snapshot.evidence,
            currentTimeline = snapshot.timeline,
            now = snapshot.savedAt
        )
    }

    private fun snapshot(
        decision: SavedDecision,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment
    ): DecisionSnapshot {
        return DecisionSnapshotFactory.create(
            eventId = "guard_test",
            decision = decision,
            savedAt = NOW,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline(NOW)
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

    private fun singleEvidence(): EvidenceAssessment {
        return EvidenceAssessment.singleSource()
    }

    private fun timeline(timestamp: Long): EvidenceTimeline {
        return EvidenceTimeline(List(5) { timestamp })
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
