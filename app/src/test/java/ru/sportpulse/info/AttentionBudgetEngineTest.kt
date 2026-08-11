package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionBudgetEngineTest {
    private val day = 20_000L
    private val minute = 60L * 1000L

    @Test
    fun unusedBudgetIsOpenWithExactRemainingTime() {
        val result = budget(usedMinutes = 0)

        assertEquals(AttentionBudgetStatus.OPEN, result.status)
        assertEquals(60L * minute, result.remainingMillis)
        assertEquals(0, result.progressPercent)
    }

    @Test
    fun warningStartsAtExactlySeventyFivePercent() {
        assertEquals(
            AttentionBudgetStatus.OPEN,
            budgetMillis(45L * minute - 1L).status
        )
        assertEquals(
            AttentionBudgetStatus.WARNING,
            budget(usedMinutes = 45).status
        )
    }

    @Test
    fun exactLimitIsExhaustedAndRightBoundaryIsClosed() {
        val result = budget(usedMinutes = 60)

        assertEquals(
            AttentionBudgetStatus.EXHAUSTED,
            result.status
        )
        assertEquals(0L, result.remainingMillis)
        assertEquals(100, result.progressPercent)
    }

    @Test
    fun overrunIsVisibleWithoutProgressOverflow() {
        val result = budget(usedMinutes = 75)

        assertEquals(15L * minute, result.overrunMillis)
        assertEquals(0L, result.remainingMillis)
        assertEquals(100, result.progressPercent)
    }

    @Test
    fun fingerprintChangesWithUsageLimitAndDay() {
        val baseline = budget(usedMinutes = 10)
        val changedUsage = budgetMillis(10L * minute + 1L)
        val changedLimit = AttentionBudgetEngine.evaluate(
            dayEpoch = day,
            usedMillis = 10L * minute,
            limitMinutes = 75
        )
        val changedDay = AttentionBudgetEngine.evaluate(
            dayEpoch = day + 1L,
            usedMillis = 10L * minute,
            limitMinutes = 60
        )

        assertNotEquals(baseline.fingerprint, changedUsage.fingerprint)
        assertNotEquals(baseline.fingerprint, changedLimit.fingerprint)
        assertNotEquals(baseline.fingerprint, changedDay.fingerprint)
        assertEquals(64, baseline.fingerprint.length)
    }

    @Test
    fun allPublishedLimitsUseFifteenMinuteSteps() {
        val valid = listOf(15, 30, 45, 60, 75, 90, 105, 120)

        valid.forEach {
            assertTrue(AttentionBudgetPolicy.isValidLimit(it))
        }
        assertFalse(AttentionBudgetPolicy.isValidLimit(0))
        assertFalse(AttentionBudgetPolicy.isValidLimit(20))
        assertFalse(AttentionBudgetPolicy.isValidLimit(135))
    }

    @Test
    fun startedDayCanOnlyKeepOrLowerLimit() {
        assertTrue(
            AttentionBudgetPolicy.canChangeLimit(60, 120, 0L)
        )
        assertTrue(
            AttentionBudgetPolicy.canChangeLimit(60, 45, 1L)
        )
        assertTrue(
            AttentionBudgetPolicy.canChangeLimit(60, 60, 1L)
        )
        assertFalse(
            AttentionBudgetPolicy.canChangeLimit(60, 75, 1L)
        )
    }

    @Test
    fun onlyDataReadyIsBlockedByExhaustion() {
        val exhausted = budget(usedMinutes = 60)

        assertTrue(
            AttentionBudgetPolicy.allows(
                SavedDecision.SKIP,
                exhausted
            )
        )
        assertTrue(
            AttentionBudgetPolicy.allows(
                SavedDecision.OBSERVE,
                exhausted
            )
        )
        assertFalse(
            AttentionBudgetPolicy.allows(
                SavedDecision.DATA_READY,
                exhausted
            )
        )
    }

    @Test
    fun dataReadyIsAllowedBeforeLimit() {
        assertTrue(
            AttentionBudgetPolicy.allows(
                SavedDecision.DATA_READY,
                budget(usedMinutes = 59)
            )
        )
    }

    @Test
    fun moscowDayChangesAtTwentyOneUtc() {
        val before = Instant.parse("2026-08-03T20:59:59.999Z")
            .toEpochMilli()
        val after = Instant.parse("2026-08-03T21:00:00Z")
            .toEpochMilli()

        assertEquals(
            AttentionBudgetDay.epochDay(before) + 1L,
            AttentionBudgetDay.epochDay(after)
        )
        assertEquals(0L, AttentionBudgetDay.millisSinceStart(after))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitIsRejectedByEngine() {
        AttentionBudgetEngine.evaluate(
            dayEpoch = day,
            usedMillis = 0L,
            limitMinutes = 20
        )
    }

    private fun budget(
        usedMinutes: Int
    ): AttentionBudgetResult {
        return budgetMillis(usedMinutes.toLong() * minute)
    }

    private fun budgetMillis(
        usedMillis: Long
    ): AttentionBudgetResult {
        return AttentionBudgetEngine.evaluate(
            dayEpoch = day,
            usedMillis = usedMillis,
            limitMinutes = 60
        )
    }
}
