package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal enum class FactRegisterStatus {
    EMPTY,
    PARTIAL,
    EXPIRING,
    READY,
    ATTENTION
}

internal enum class FactRegisterEntryState(
    val title: String,
    internal val priority: Int
) {
    TAMPERED("Повреждена", 0),
    CONFLICT("Расхождение", 1),
    EMPTY("Не записана", 2),
    SHARED_LINEAGE("Эхо источников", 3),
    UNAUDITED("Связь не проверена", 4),
    SINGLE_SOURCE("Один источник", 5),
    INDEPENDENT("Независимый кворум", 6)
}

internal data class FactRegisterEntry(
    val factor: SignalFactor,
    val state: FactRegisterEntryState,
    val integrity: FactReceiptIntegrity,
    val receipt: FactReceipt?,
    val freshness: FactorFreshness?
) {
    init {
        require(
            (integrity == FactReceiptIntegrity.VALID) ==
                (receipt != null)
        )
        require(receipt == null || receipt.factor == factor)
        require((receipt != null) == (freshness != null))
        require(freshness == null || freshness.factor == factor)
    }
}

internal data class FactRegister(
    val eventId: String,
    val entries: List<FactRegisterEntry>,
    val status: FactRegisterStatus,
    val validCount: Int,
    val quorumCount: Int,
    val issueCount: Int,
    val expiringCount: Int,
    val degradedCount: Int,
    val expiredCount: Int,
    val nextFactor: SignalFactor?,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(entries.size == SignalFactor.values().size)
        require(entries.map(FactRegisterEntry::factor) == SignalFactor.values().toList())
        require(validCount in 0..entries.size)
        require(quorumCount in 0..validCount)
        require(issueCount in 0..entries.size)
        require(expiringCount in 0..validCount)
        require(degradedCount in 0..validCount)
        require(expiredCount in 0..validCount)
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase(Locale.ROOT)
}

internal object FactRegisterEngine {
    private const val VERSION = "sport-pulse-fact-register-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun create(
        eventId: String,
        reads: Map<SignalFactor, FactReceiptReadResult>,
        now: Long
    ): FactRegister {
        val cleanEventId = eventId.trim()
        require(cleanEventId.isNotBlank())
        require(reads.keys == SignalFactor.values().toSet())
        require(now >= 0L)

        val entries = SignalFactor.values().map { factor ->
            val read = checkNotNull(reads[factor])
            require(
                read.receipt == null ||
                    read.receipt.eventId == cleanEventId
            )
            val freshness = read.receipt?.let { receipt ->
                FreshnessEngine.evaluateFactor(
                    factor = factor,
                    claimedLevel = receipt.effectiveEvidence,
                    checkedAt = receipt.checkedAt,
                    now = now
                )
            }
            FactRegisterEntry(
                factor = factor,
                state = entryState(read),
                integrity = read.integrity,
                receipt = read.receipt,
                freshness = freshness
            )
        }
        val validCount = entries.count {
            it.integrity == FactReceiptIntegrity.VALID
        }
        val quorumCount = entries.count {
            it.freshness?.effectiveLevel == EvidenceLevel.QUORUM
        }
        val hardIssueCount = entries.count {
            it.state == FactRegisterEntryState.TAMPERED ||
                it.state == FactRegisterEntryState.CONFLICT
        }
        val expiringCount = entries.count {
            it.freshness?.status == FreshnessStatus.EXPIRING
        }
        val degradedCount = entries.count {
            it.freshness?.status == FreshnessStatus.DEGRADED
        }
        val expiredCount = entries.count {
            it.freshness?.status == FreshnessStatus.EXPIRED
        }
        val issueCount = hardIssueCount + expiredCount
        val status = when {
            issueCount > 0 -> FactRegisterStatus.ATTENTION
            validCount == 0 -> FactRegisterStatus.EMPTY
            quorumCount == entries.size && expiringCount > 0 ->
                FactRegisterStatus.EXPIRING
            quorumCount == entries.size -> FactRegisterStatus.READY
            else -> FactRegisterStatus.PARTIAL
        }
        val nextFactor = if (status == FactRegisterStatus.READY) {
            null
        } else {
            entries.minByOrNull(::routePriority)?.factor
        }
        val payload = buildList {
            add(VERSION)
            add(cleanEventId)
            entries.forEach { entry ->
                add(entry.factor.name)
                add(entry.state.name)
                add(entry.receipt?.fingerprint ?: entry.integrity.name)
                add(entry.freshness?.status?.name.orEmpty())
                add(entry.freshness?.effectiveLevel?.name.orEmpty())
                add(entry.freshness?.nextTransitionAt?.toString().orEmpty())
            }
        }.joinToString("|")

        return FactRegister(
            eventId = cleanEventId,
            entries = entries,
            status = status,
            validCount = validCount,
            quorumCount = quorumCount,
            issueCount = issueCount,
            expiringCount = expiringCount,
            degradedCount = degradedCount,
            expiredCount = expiredCount,
            nextFactor = nextFactor,
            fingerprint = digest(payload)
        )
    }

    private fun routePriority(entry: FactRegisterEntry): Int {
        return when {
            entry.state == FactRegisterEntryState.TAMPERED -> 0
            entry.state == FactRegisterEntryState.CONFLICT -> 1
            entry.freshness?.status == FreshnessStatus.EXPIRED -> 2
            entry.state == FactRegisterEntryState.EMPTY -> 3
            entry.freshness?.status == FreshnessStatus.DEGRADED -> 4
            entry.state == FactRegisterEntryState.SHARED_LINEAGE -> 5
            entry.state == FactRegisterEntryState.UNAUDITED -> 6
            entry.state == FactRegisterEntryState.SINGLE_SOURCE -> 7
            entry.freshness?.status == FreshnessStatus.EXPIRING -> 8
            else -> 9
        }
    }

    private fun entryState(
        read: FactReceiptReadResult
    ): FactRegisterEntryState {
        if (read.integrity == FactReceiptIntegrity.TAMPERED) {
            return FactRegisterEntryState.TAMPERED
        }
        val receipt = read.receipt
            ?: return FactRegisterEntryState.EMPTY
        if (receipt.sourceCount == 1) {
            return FactRegisterEntryState.SINGLE_SOURCE
        }
        return when (receipt.sourceAuditState) {
            SourceAuditState.UNAUDITED ->
                FactRegisterEntryState.UNAUDITED
            SourceAuditState.SHARED_LINEAGE ->
                FactRegisterEntryState.SHARED_LINEAGE
            SourceAuditState.INDEPENDENT ->
                FactRegisterEntryState.INDEPENDENT
            SourceAuditState.CONFLICT ->
                FactRegisterEntryState.CONFLICT
        }
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
