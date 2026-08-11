package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {
    @Test
    fun completeEvidenceProducesReadyVerdict() {
        val result = SignalEngine.evaluate(
            SignalAssessment(listOf(100, 100, 100, 100, 100))
        )

        assertEquals(100, result.readiness)
        assertEquals(0, result.noise)
        assertEquals(SignalVerdict.READY, result.verdict)
    }

    @Test
    fun missingEvidenceProducesSkipVerdict() {
        val result = SignalEngine.evaluate(
            SignalAssessment(listOf(0, 0, 0, 0, 0))
        )

        assertEquals(0, result.readiness)
        assertEquals(100, result.noise)
        assertEquals(SignalVerdict.SKIP, result.verdict)
    }

    @Test
    fun weakFactorIsVisibleAndPenalizesReadiness() {
        val result = SignalEngine.evaluate(
            SignalAssessment(listOf(90, 90, 15, 90, 90))
        )

        assertEquals(SignalFactor.LOAD, result.weakestFactor)
        assertTrue(result.readiness < 65)
        assertEquals(SignalVerdict.OBSERVE, result.verdict)
    }
}
