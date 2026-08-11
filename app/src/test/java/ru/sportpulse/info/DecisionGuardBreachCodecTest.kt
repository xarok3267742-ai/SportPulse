package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionGuardBreachCodecTest {
    @Test
    fun breachRoundTripsWithObservedState() {
        val triggered = triggeredResult()
        val breach = DecisionGuardBreachFactory.create(
            result = triggered,
            triggeredAt = NOW + 5_000L
        )

        val decoded = DecisionGuardBreachCodec.decode(
            DecisionGuardBreachCodec.encode(breach)
        )

        assertEquals(breach, decoded)
        assertEquals(
            triggered.currentFactorValue,
            decoded?.factorValue
        )
        assertEquals(
            triggered.currentResult.effectiveSignal.verdict,
            decoded?.verdict
        )
    }

    @Test
    fun tamperedBreachIsRejected() {
        val breach = DecisionGuardBreachFactory.create(
            result = triggeredResult(),
            triggeredAt = NOW + 5_000L
        )
        val encoded = DecisionGuardBreachCodec.encode(breach)
        val tampered = encoded.replace(
            breach.readiness.toString(),
            (breach.readiness + 1).toString()
        )

        assertNull(DecisionGuardBreachCodec.decode(tampered))
    }

    @Test
    fun observedBreachKeepsRecoveredContractStopped() {
        val triggered = triggeredResult()
        val breach = DecisionGuardBreachFactory.create(
            result = triggered,
            triggeredAt = NOW + 5_000L
        )
        val recovered = unchangedResult()

        val latched = recovered.withBreach(breach)

        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            latched.status
        )
        assertTrue(latched.isRecoveredAfterBreach)
        assertTrue(latched.causes.isEmpty())
        assertEquals(
            breach.causes,
            latched.effectiveCauses
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun breachCannotAttachToAnotherSeal() {
        val breach = DecisionGuardBreachFactory.create(
            result = triggeredResult(),
            triggeredAt = NOW + 5_000L
        )
        val changedSnapshot = snapshot(
            SignalAssessment(
                listOf(70, 70, 70, 70, 71)
            )
        )
        val changed = DecisionGuardEngine.evaluate(
            snapshot = changedSnapshot,
            currentAssessment = changedSnapshot.assessment,
            currentEvidence = changedSnapshot.evidence,
            currentTimeline = changedSnapshot.timeline,
            now = NOW
        )

        changed.withBreach(breach)
    }

    @Test
    fun liveRecoveryAloneWouldArmWithoutPersistedBreach() {
        val recovered = unchangedResult()

        assertEquals(
            DecisionGuardStatus.ARMED,
            recovered.status
        )
        assertFalse(recovered.isTriggered)
        assertNull(recovered.breach)
    }

    private fun triggeredResult(): DecisionGuardResult {
        val snapshot = snapshot()
        val armed = unchangedResult()
        val condition = requireNotNull(armed.plan.condition)
        val floor = requireNotNull(condition.scoreFloor)
        val current = snapshot.assessment.withValue(
            condition.factor,
            floor
        )
        return DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = current,
            currentEvidence = snapshot.evidence,
            currentTimeline = snapshot.timeline,
            now = NOW + 5_000L
        ).also {
            assertTrue(it.causes.isNotEmpty())
        }
    }

    private fun unchangedResult(): DecisionGuardResult {
        val snapshot = snapshot()
        return DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = snapshot.assessment,
            currentEvidence = snapshot.evidence,
            currentTimeline = snapshot.timeline,
            now = NOW
        )
    }

    private fun snapshot(
        assessment: SignalAssessment =
            SignalAssessment(List(5) { 70 })
    ): DecisionSnapshot {
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        return DecisionSnapshotFactory.create(
            eventId = "breach_test",
            decision = SavedDecision.OBSERVE,
            savedAt = NOW,
            assessment = assessment,
            evidence = evidence,
            timeline = EvidenceTimeline(
                List(5) { NOW }
            )
        )
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
