package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

internal enum class AttentionBudgetStatus {
    OPEN,
    WARNING,
    EXHAUSTED
}

internal data class AttentionBudgetResult(
    val dayEpoch: Long,
    val usedMillis: Long,
    val limitMinutes: Int,
    val status: AttentionBudgetStatus,
    val fingerprint: String
) {
    init {
        require(dayEpoch >= 0L)
        require(usedMillis in 0L..AttentionBudgetPolicy.DAY_MILLIS)
        require(AttentionBudgetPolicy.isValidLimit(limitMinutes))
        require(
            fingerprint.length == 64 &&
                fingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        )
    }

    val limitMillis: Long
        get() = limitMinutes.toLong() * 60L * 1000L

    val remainingMillis: Long
        get() = (limitMillis - usedMillis).coerceAtLeast(0L)

    val overrunMillis: Long
        get() = (usedMillis - limitMillis).coerceAtLeast(0L)

    val progressPercent: Int
        get() = (
            usedMillis * 100L / limitMillis
            ).toInt().coerceIn(0, 100)

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object AttentionBudgetPolicy {
    const val MIN_LIMIT_MINUTES = 15
    const val MAX_LIMIT_MINUTES = 120
    const val LIMIT_STEP_MINUTES = 15
    const val DEFAULT_LIMIT_MINUTES = 60
    const val WARNING_PERCENT = 75
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun isValidLimit(limitMinutes: Int): Boolean {
        return limitMinutes in
            MIN_LIMIT_MINUTES..MAX_LIMIT_MINUTES &&
            (
                limitMinutes - MIN_LIMIT_MINUTES
                ) % LIMIT_STEP_MINUTES == 0
    }

    fun canChangeLimit(
        currentMinutes: Int,
        proposedMinutes: Int,
        usedMillis: Long
    ): Boolean {
        require(isValidLimit(currentMinutes))
        require(isValidLimit(proposedMinutes))
        require(usedMillis >= 0L)
        return usedMillis == 0L ||
            proposedMinutes <= currentMinutes
    }

    fun requiresBudget(decision: SavedDecision): Boolean {
        return decision == SavedDecision.DATA_READY
    }

    fun allows(
        decision: SavedDecision,
        budget: AttentionBudgetResult
    ): Boolean {
        return !requiresBudget(decision) ||
            budget.status != AttentionBudgetStatus.EXHAUSTED
    }
}

internal object AttentionBudgetDay {
    private val moscowZone = ZoneId.of("Europe/Moscow")

    fun epochDay(nowMillis: Long): Long {
        require(nowMillis >= 0L)
        return Instant.ofEpochMilli(nowMillis)
            .atZone(moscowZone)
            .toLocalDate()
            .toEpochDay()
    }

    fun millisSinceStart(nowMillis: Long): Long {
        require(nowMillis >= 0L)
        val current = Instant.ofEpochMilli(nowMillis)
            .atZone(moscowZone)
        val start = current.toLocalDate()
            .atStartOfDay(moscowZone)
        return (
            nowMillis - start.toInstant().toEpochMilli()
            ).coerceIn(0L, AttentionBudgetPolicy.DAY_MILLIS)
    }
}

internal object AttentionBudgetEngine {
    private const val VERSION =
        "sport-pulse-attention-budget-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        dayEpoch: Long,
        usedMillis: Long,
        limitMinutes: Int
    ): AttentionBudgetResult {
        require(dayEpoch >= 0L)
        require(usedMillis in 0L..AttentionBudgetPolicy.DAY_MILLIS)
        require(AttentionBudgetPolicy.isValidLimit(limitMinutes))
        val limitMillis = limitMinutes.toLong() * 60L * 1000L
        val status = when {
            usedMillis >= limitMillis ->
                AttentionBudgetStatus.EXHAUSTED
            usedMillis * 100L >=
                limitMillis * AttentionBudgetPolicy.WARNING_PERCENT ->
                AttentionBudgetStatus.WARNING
            else -> AttentionBudgetStatus.OPEN
        }
        val fingerprint = digest(
            listOf(
                VERSION,
                dayEpoch.toString(),
                usedMillis.toString(),
                limitMinutes.toString()
            ).joinToString("|")
        )
        return AttentionBudgetResult(
            dayEpoch = dayEpoch,
            usedMillis = usedMillis,
            limitMinutes = limitMinutes,
            status = status,
            fingerprint = fingerprint
        )
    }

    private fun digest(payload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
            payload.toByteArray(StandardCharsets.UTF_8)
        )
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
