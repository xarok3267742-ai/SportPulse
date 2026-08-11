package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal enum class FactTimeSliceStatus {
    INSUFFICIENT,
    ALIGNED,
    DRIFTING,
    SPLIT
}

internal data class FactTimeSlicePoint(
    val factor: SignalFactor,
    val checkedAt: Long,
    val validForMillis: Long,
    val effectiveLevel: EvidenceLevel,
    val freshnessStatus: FreshnessStatus,
    val receiptFingerprint: String
) {
    init {
        require(checkedAt >= 0L)
        require(validForMillis > 0L)
        require(effectiveLevel != EvidenceLevel.UNCONFIRMED)
        require(freshnessStatus != FreshnessStatus.EXPIRED)
        require(freshnessStatus != FreshnessStatus.UNCONFIRMED)
        require(receiptFingerprint.matches(Regex("[0-9a-f]{64}")))
    }
}

internal data class FactTimeSlice(
    val eventId: String,
    val points: List<FactTimeSlicePoint>,
    val status: FactTimeSliceStatus,
    val spreadMillis: Long?,
    val syncWindowMillis: Long?,
    val referenceFactor: SignalFactor?,
    val oldestFactor: SignalFactor?,
    val newestFactor: SignalFactor?,
    val suggestedFactor: SignalFactor?,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(points == points.sortedBy { it.factor.ordinal })
        require(points.map { it.factor }.distinct().size == points.size)
        require((status == FactTimeSliceStatus.INSUFFICIENT) == (points.size < 2))
        val hasWindow = points.size >= 2
        require((spreadMillis != null) == hasWindow)
        require((syncWindowMillis != null) == hasWindow)
        require((referenceFactor != null) == hasWindow)
        require((oldestFactor != null) == hasWindow)
        require((newestFactor != null) == hasWindow)
        require(spreadMillis == null || spreadMillis >= 0L)
        require(syncWindowMillis == null || syncWindowMillis > 0L)
        require(
            (suggestedFactor != null) ==
                (status == FactTimeSliceStatus.DRIFTING ||
                    status == FactTimeSliceStatus.SPLIT)
        )
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    val activeCount: Int
        get() = points.size

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase(Locale.ROOT)
}

internal object FactTimeSliceEngine {
    private const val VERSION = "sport-pulse-fact-time-slice-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun create(register: FactRegister): FactTimeSlice {
        val points = register.entries.mapNotNull { entry ->
            val receipt = entry.receipt ?: return@mapNotNull null
            val freshness = entry.freshness
                ?: return@mapNotNull null
            if (freshness.effectiveLevel == EvidenceLevel.UNCONFIRMED) {
                return@mapNotNull null
            }
            FactTimeSlicePoint(
                factor = entry.factor,
                checkedAt = receipt.checkedAt,
                validForMillis = freshness.validForMillis,
                effectiveLevel = freshness.effectiveLevel,
                freshnessStatus = freshness.status,
                receiptFingerprint = receipt.fingerprint
            )
        }.sortedBy { it.factor.ordinal }

        val window = if (points.size < 2) {
            null
        } else {
            calculateWindow(points)
        }
        val payload = buildList {
            add(VERSION)
            add(register.eventId)
            add(window?.status?.name ?: FactTimeSliceStatus.INSUFFICIENT.name)
            points.forEach { point ->
                add(point.factor.name)
                add(point.checkedAt.toString())
                add(point.validForMillis.toString())
                add(point.effectiveLevel.name)
                add(point.freshnessStatus.name)
                add(point.receiptFingerprint)
            }
            add(window?.syncWindowMillis?.toString().orEmpty())
        }.joinToString("|")

        return FactTimeSlice(
            eventId = register.eventId,
            points = points,
            status = window?.status ?: FactTimeSliceStatus.INSUFFICIENT,
            spreadMillis = window?.spreadMillis,
            syncWindowMillis = window?.syncWindowMillis,
            referenceFactor = window?.referenceFactor,
            oldestFactor = window?.oldestFactor,
            newestFactor = window?.newestFactor,
            suggestedFactor = window?.suggestedFactor,
            fingerprint = digest(payload)
        )
    }

    private fun calculateWindow(
        points: List<FactTimeSlicePoint>
    ): SliceWindow {
        val earliest = points.minOf { it.checkedAt }
        val latest = points.maxOf { it.checkedAt }
        val spread = latest - earliest
        val reference = points.minWith(
            compareBy<FactTimeSlicePoint> {
                it.validForMillis
            }.thenBy { it.factor.ordinal }
        )
        val syncWindow = (reference.validForMillis / 4L)
            .coerceAtLeast(1L)
        val status = when {
            spread <= syncWindow -> FactTimeSliceStatus.ALIGNED
            spread <= syncWindow * 2L -> FactTimeSliceStatus.DRIFTING
            else -> FactTimeSliceStatus.SPLIT
        }
        val oldest = points
            .filter { it.checkedAt == earliest }
            .minBy { it.factor.ordinal }
        val newest = points
            .filter { it.checkedAt == latest }
            .minBy { it.factor.ordinal }
        val suggested = if (
            status == FactTimeSliceStatus.DRIFTING ||
            status == FactTimeSliceStatus.SPLIT
        ) {
            oldest.factor
        } else {
            null
        }
        return SliceWindow(
            status = status,
            spreadMillis = spread,
            syncWindowMillis = syncWindow,
            referenceFactor = reference.factor,
            oldestFactor = oldest.factor,
            newestFactor = newest.factor,
            suggestedFactor = suggested
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

    private data class SliceWindow(
        val status: FactTimeSliceStatus,
        val spreadMillis: Long,
        val syncWindowMillis: Long,
        val referenceFactor: SignalFactor,
        val oldestFactor: SignalFactor,
        val newestFactor: SignalFactor,
        val suggestedFactor: SignalFactor?
    )
}
