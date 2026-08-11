package ru.sportpulse.info

internal enum class ScheduleFreshnessStatus {
    FRESH,
    VERIFY,
    STALE,
    INVALID
}

internal data class ScheduleFreshnessResult(
    val status: ScheduleFreshnessStatus,
    val ageMillis: Long?
) {
    init {
        require(ageMillis == null || ageMillis >= 0L)
        require(
            status == ScheduleFreshnessStatus.INVALID ||
                ageMillis != null
        )
    }
}

internal object ScheduleFreshnessPolicy {
    const val VERIFY_AFTER_MILLIS = 6L * 60L * 60L * 1000L
    const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L

    fun evaluate(
        syncedAt: Long,
        now: Long
    ): ScheduleFreshnessResult {
        require(syncedAt >= 0L)
        require(now >= 0L)
        if (syncedAt > now) {
            return ScheduleFreshnessResult(
                status = ScheduleFreshnessStatus.INVALID,
                ageMillis = null
            )
        }
        val age = now - syncedAt
        val status = when {
            age <= VERIFY_AFTER_MILLIS ->
                ScheduleFreshnessStatus.FRESH
            age <= STALE_AFTER_MILLIS ->
                ScheduleFreshnessStatus.VERIFY
            else -> ScheduleFreshnessStatus.STALE
        }
        return ScheduleFreshnessResult(status, age)
    }
}
