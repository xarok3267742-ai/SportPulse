package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessEngineTest {
    private val now = 100L * FreshnessPolicy.HOUR_MILLIS

    @Test
    fun freshQuorumKeepsItsLevelUntilFactorDeadline() {
        val checkedAt = now - 5L * FreshnessPolicy.HOUR_MILLIS

        val result = FreshnessEngine.evaluateFactor(
            factor = SignalFactor.LINEUP,
            claimedLevel = EvidenceLevel.QUORUM,
            checkedAt = checkedAt,
            now = now
        )

        assertEquals(EvidenceLevel.QUORUM, result.effectiveLevel)
        assertEquals(FreshnessStatus.EXPIRING, result.status)
        assertEquals(FreshnessPolicy.HOUR_MILLIS, result.remainingMillis)
    }

    @Test
    fun expiredQuorumDegradesOneLevelBeforeFullExpiry() {
        val checkedAt = now - 7L * FreshnessPolicy.HOUR_MILLIS

        val result = FreshnessEngine.evaluateFactor(
            factor = SignalFactor.LINEUP,
            claimedLevel = EvidenceLevel.QUORUM,
            checkedAt = checkedAt,
            now = now
        )

        assertEquals(EvidenceLevel.SINGLE_SOURCE, result.effectiveLevel)
        assertEquals(FreshnessStatus.DEGRADED, result.status)
        assertEquals(5L * FreshnessPolicy.HOUR_MILLIS, result.remainingMillis)
    }

    @Test
    fun quorumEventuallyBecomesUnconfirmed() {
        val checkedAt = now - 12L * FreshnessPolicy.HOUR_MILLIS

        val result = FreshnessEngine.evaluateFactor(
            factor = SignalFactor.LINEUP,
            claimedLevel = EvidenceLevel.QUORUM,
            checkedAt = checkedAt,
            now = now
        )

        assertEquals(EvidenceLevel.UNCONFIRMED, result.effectiveLevel)
        assertEquals(FreshnessStatus.EXPIRED, result.status)
        assertNull(result.nextTransitionAt)
    }

    @Test
    fun nearestTransitionUsesSportSpecificValidity() {
        val evidence = EvidenceAssessment.singleSource()
        val timeline = EvidenceTimeline(
            List(SignalFactor.values().size) { now }
        )

        val result = FreshnessEngine.evaluate(evidence, timeline, now)

        assertEquals(SignalFactor.LINEUP, result.nextTransitionFactor)
        assertEquals(
            now + 6L * FreshnessPolicy.HOUR_MILLIS,
            result.nextTransitionAt
        )
        assertTrue(result.degradedFactors.isEmpty())
    }

    @Test
    fun compactDurationUsesStableDayHourMinuteUnits() {
        assertEquals("2 д", FreshnessFormatter.duration(48L * 60L * 60L * 1000L))
        assertEquals("2 д 3 ч", FreshnessFormatter.duration(51L * 60L * 60L * 1000L))
        assertEquals("6 ч", FreshnessFormatter.duration(6L * 60L * 60L * 1000L))
        assertEquals("4 ч 15 мин", FreshnessFormatter.duration(255L * 60L * 1000L))
        assertEquals("1 мин", FreshnessFormatter.duration(1L))
    }
}
