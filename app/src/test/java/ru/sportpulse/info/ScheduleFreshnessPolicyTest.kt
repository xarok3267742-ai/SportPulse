package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleFreshnessPolicyTest {
    @Test
    fun recentScheduleIsFresh() {
        val result = ScheduleFreshnessPolicy.evaluate(
            syncedAt = NOW - ScheduleFreshnessPolicy.VERIFY_AFTER_MILLIS,
            now = NOW
        )

        assertEquals(ScheduleFreshnessStatus.FRESH, result.status)
        assertEquals(
            ScheduleFreshnessPolicy.VERIFY_AFTER_MILLIS,
            result.ageMillis
        )
    }

    @Test
    fun scheduleOlderThanSixHoursNeedsVerification() {
        val result = ScheduleFreshnessPolicy.evaluate(
            syncedAt = NOW -
                ScheduleFreshnessPolicy.VERIFY_AFTER_MILLIS - 1L,
            now = NOW
        )

        assertEquals(ScheduleFreshnessStatus.VERIFY, result.status)
    }

    @Test
    fun scheduleOlderThanDayIsStale() {
        val result = ScheduleFreshnessPolicy.evaluate(
            syncedAt = NOW -
                ScheduleFreshnessPolicy.STALE_AFTER_MILLIS - 1L,
            now = NOW
        )

        assertEquals(ScheduleFreshnessStatus.STALE, result.status)
    }

    @Test
    fun futureSyncTimestampIsInvalid() {
        val result = ScheduleFreshnessPolicy.evaluate(
            syncedAt = NOW + 1L,
            now = NOW
        )

        assertEquals(ScheduleFreshnessStatus.INVALID, result.status)
        assertNull(result.ageMillis)
    }

    private companion object {
        const val NOW = 2_000_000_000L
    }
}
