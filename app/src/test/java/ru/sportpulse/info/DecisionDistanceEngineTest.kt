package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionDistanceEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun unansweredCheckIsIncompleteWithoutRiskScore() {
        val result = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.unanswered(),
            checkedAt = now
        )

        assertEquals(
            DecisionDistanceStatus.INCOMPLETE,
            result.status
        )
        assertEquals(0, result.answeredCount)
        assertEquals(4, result.unansweredFactors.size)
        assertTrue(result.riskFactors.isEmpty())
    }

    @Test
    fun oneRiskAnswerStopsEvenBeforeOtherAnswers() {
        val assessment = DecisionDistanceAssessment.unanswered()
            .withAnswer(
                DecisionDistanceFactor.CONDITION,
                DecisionDistanceAnswer.YES
            )

        val result = DecisionDistanceEngine.evaluate(
            assessment,
            checkedAt = now
        )

        assertEquals(DecisionDistanceStatus.STOP, result.status)
        assertEquals(
            listOf(DecisionDistanceFactor.CONDITION),
            result.riskFactors
        )
        assertEquals(1, result.answeredCount)
    }

    @Test
    fun riskReasonsAlwaysFollowPublishedFactorOrder() {
        val assessment = DecisionDistanceAssessment.clear()
            .withAnswer(
                DecisionDistanceFactor.LIMITS,
                DecisionDistanceAnswer.YES
            )
            .withAnswer(
                DecisionDistanceFactor.CHASING,
                DecisionDistanceAnswer.YES
            )

        val result = DecisionDistanceEngine.evaluate(
            assessment,
            checkedAt = now
        )

        assertEquals(
            listOf(
                DecisionDistanceFactor.CHASING,
                DecisionDistanceFactor.LIMITS
            ),
            result.riskFactors
        )
    }

    @Test
    fun fourNoAnswersProduceClearStatus() {
        val result = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear(),
            checkedAt = now
        )

        assertEquals(DecisionDistanceStatus.CLEAR, result.status)
        assertEquals(4, result.answeredCount)
        assertTrue(result.riskFactors.isEmpty())
        assertTrue(result.unansweredFactors.isEmpty())
    }

    @Test
    fun resultFingerprintChangesWithAnswerAndTime() {
        val clear = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear(),
            checkedAt = now
        )
        val changedAnswer = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear().withAnswer(
                DecisionDistanceFactor.MONEY,
                DecisionDistanceAnswer.YES
            ),
            checkedAt = now
        )
        val changedTime = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear(),
            checkedAt = now + 1L
        )

        assertNotEquals(clear.fingerprint, changedAnswer.fingerprint)
        assertNotEquals(clear.fingerprint, changedTime.fingerprint)
        assertEquals(64, clear.fingerprint.length)
    }

    @Test
    fun clearanceHasExactThirtyMinuteWindow() {
        val result = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear(),
            checkedAt = now
        )
        val clearance = DecisionDistanceEngine.clearanceFor(result)

        assertEquals(
            DecisionDistancePolicy.CLEARANCE_MILLIS,
            clearance.expiresAt - clearance.checkedAt
        )
        assertTrue(clearance.isValidAt(now))
        assertTrue(clearance.isValidAt(clearance.expiresAt - 1L))
        assertFalse(clearance.isValidAt(clearance.expiresAt))
    }

    @Test(expected = IllegalArgumentException::class)
    fun stoppedCheckCannotProduceClearance() {
        val result = DecisionDistanceEngine.evaluate(
            DecisionDistanceAssessment.clear().withAnswer(
                DecisionDistanceFactor.CHASING,
                DecisionDistanceAnswer.YES
            ),
            checkedAt = now
        )

        DecisionDistanceEngine.clearanceFor(result)
    }

    @Test
    fun skipAndObserveNeverRequireClearance() {
        assertTrue(
            DecisionDistancePolicy.allows(
                SavedDecision.SKIP,
                clearance = null,
                now = now
            )
        )
        assertTrue(
            DecisionDistancePolicy.allows(
                SavedDecision.OBSERVE,
                clearance = null,
                now = now
            )
        )
    }

    @Test
    fun dataReadyRequiresFreshClearance() {
        val clearance = clearClearance()

        assertFalse(
            DecisionDistancePolicy.allows(
                SavedDecision.DATA_READY,
                clearance = null,
                now = now
            )
        )
        assertTrue(
            DecisionDistancePolicy.allows(
                SavedDecision.DATA_READY,
                clearance = clearance,
                now = now
            )
        )
        assertFalse(
            DecisionDistancePolicy.allows(
                SavedDecision.DATA_READY,
                clearance = clearance,
                now = clearance.expiresAt
            )
        )
    }

    @Test
    fun clearanceCodecRoundTripsAndRejectsTampering() {
        val clearance = clearClearance()
        val encoded = DecisionDistanceClearanceCodec.encode(clearance)

        assertEquals(
            clearance,
            DecisionDistanceClearanceCodec.decode(encoded)
        )
        assertNull(
            DecisionDistanceClearanceCodec.decode(
                encoded.replace(
                    clearance.expiresAt.toString(),
                    (clearance.expiresAt + 1L).toString()
                )
            )
        )
        assertNull(
            DecisionDistanceClearanceCodec.decode("2|bad")
        )
    }

    private fun clearClearance(): DecisionDistanceClearance {
        return DecisionDistanceEngine.clearanceFor(
            DecisionDistanceEngine.evaluate(
                DecisionDistanceAssessment.clear(),
                checkedAt = now
            )
        )
    }
}
