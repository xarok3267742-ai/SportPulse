package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal data class PreflightReceiptSlot(
    val scheduledAt: Long,
    val immediate: Boolean,
    val factors: List<SignalFactor>
) {
    init {
        require(scheduledAt >= 0L)
        require(factors.isNotEmpty())
        require(factors.distinct().size == factors.size)
        require(factors.zipWithNext().all { (left, right) ->
            left.ordinal < right.ordinal
        })
    }

    val factorKey: String
        get() = factors.joinToString(".") { it.name }
}

internal data class PreflightExportReceipt(
    val eventId: String,
    val eventLabel: String,
    val protocolFingerprint: String,
    val scheduleFingerprint: String,
    val startAt: Long,
    val selectedZone: RegionalZone,
    val sequence: Int,
    val exportedAt: Long,
    val slots: List<PreflightReceiptSlot>,
    val withdrawn: Boolean,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(eventLabel.length <= MAX_EVENT_LABEL_LENGTH)
        require(HEX_64.matches(protocolFingerprint.lowercase()))
        require(HEX_64.matches(scheduleFingerprint.lowercase()))
        require(startAt >= 0L)
        require(sequence > 0)
        require(exportedAt >= 0L)
        require(slots.size <= SignalFactor.values().size)
        require(slots.zipWithNext().all { (left, right) ->
            left.scheduledAt < right.scheduledAt
        })
        val allFactors = slots.flatMap { it.factors }
        require(allFactors.distinct().size == allFactors.size)
        require(
            fingerprint.isEmpty() ||
                HEX_64.matches(fingerprint.lowercase())
        )
    }

    val shortScheduleFingerprint: String
        get() = scheduleFingerprint.take(8).uppercase()

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        const val MAX_EVENT_LABEL_LENGTH = 200
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal enum class PreflightReceiptIntegrity {
    EMPTY,
    VALID,
    TAMPERED
}

internal data class PreflightReceiptReadResult(
    val integrity: PreflightReceiptIntegrity,
    val receipt: PreflightExportReceipt?
) {
    init {
        when (integrity) {
            PreflightReceiptIntegrity.EMPTY,
            PreflightReceiptIntegrity.TAMPERED ->
                require(receipt == null)
            PreflightReceiptIntegrity.VALID ->
                requireNotNull(receipt)
        }
    }
}

internal object PreflightScheduleFingerprint {
    private const val VERSION =
        "sport-pulse-preflight-schedule-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun forProtocol(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone
    ): String {
        return forValues(
            eventId = protocol.eventId,
            eventLabel = protocol.eventLabel,
            startAt = protocol.start.startAt,
            selectedZone = selectedZone,
            slots = protocol.slots.map {
                PreflightReceiptSlot(
                    scheduledAt = it.scheduledAt,
                    immediate = it.immediate,
                    factors = it.factors
                )
            }
        )
    }

    fun forReceipt(receipt: PreflightExportReceipt): String {
        return forValues(
            eventId = receipt.eventId,
            eventLabel = receipt.eventLabel,
            startAt = receipt.startAt,
            selectedZone = receipt.selectedZone,
            slots = receipt.slots
        )
    }

    private fun forValues(
        eventId: String,
        eventLabel: String,
        startAt: Long,
        selectedZone: RegionalZone,
        slots: List<PreflightReceiptSlot>
    ): String {
        val payload = buildString {
            append(VERSION)
            append("|event=")
            append(eventId)
            append(':')
            append(eventLabel)
            append("|start=")
            append(startAt)
            append("|zone=")
            append(selectedZone.name)
            slots.forEach { slot ->
                append("|slot=")
                append(slot.scheduledAt)
                append(':')
                append(slot.immediate)
                append(':')
                append(slot.factorKey)
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

internal object PreflightExportReceiptFactory {
    fun create(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        sequence: Int,
        exportedAt: Long
    ): PreflightExportReceipt {
        require(sequence > 0)
        require(exportedAt >= 0L)
        return restore(
            eventId = protocol.eventId,
            eventLabel = protocol.eventLabel,
            protocolFingerprint = protocol.fingerprint,
            startAt = protocol.start.startAt,
            selectedZone = selectedZone,
            sequence = sequence,
            exportedAt = exportedAt,
            slots = protocol.slots.map { slot ->
                PreflightReceiptSlot(
                    scheduledAt = slot.scheduledAt,
                    immediate = slot.immediate,
                    factors = slot.factors
                )
            },
            withdrawn = false
        )
    }

    fun withdraw(
        previous: PreflightExportReceipt,
        exportedAt: Long
    ): PreflightExportReceipt {
        require(!previous.withdrawn)
        require(previous.sequence < Int.MAX_VALUE)
        require(exportedAt >= 0L)
        require(secureEquals(
            previous.scheduleFingerprint,
            PreflightScheduleFingerprint.forReceipt(previous)
        ))
        require(secureEquals(
            previous.fingerprint,
            PreflightExportReceiptCodec.fingerprintFor(previous)
        ))
        return restore(
            eventId = previous.eventId,
            eventLabel = previous.eventLabel,
            protocolFingerprint = previous.protocolFingerprint,
            startAt = previous.startAt,
            selectedZone = previous.selectedZone,
            sequence = previous.sequence + 1,
            exportedAt = exportedAt,
            slots = previous.slots,
            withdrawn = true
        )
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.lowercase().toByteArray(StandardCharsets.US_ASCII),
            right.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }

    internal fun restore(
        eventId: String,
        eventLabel: String,
        protocolFingerprint: String,
        startAt: Long,
        selectedZone: RegionalZone,
        sequence: Int,
        exportedAt: Long,
        slots: List<PreflightReceiptSlot>,
        withdrawn: Boolean = false
    ): PreflightExportReceipt {
        val draft = PreflightExportReceipt(
            eventId = eventId,
            eventLabel = eventLabel,
            protocolFingerprint = protocolFingerprint.lowercase(),
            scheduleFingerprint = "0".repeat(64),
            startAt = startAt,
            selectedZone = selectedZone,
            sequence = sequence,
            exportedAt = exportedAt,
            slots = slots,
            withdrawn = withdrawn,
            fingerprint = ""
        )
        val scheduleFingerprint =
            PreflightScheduleFingerprint.forReceipt(draft)
        val scheduledDraft = draft.copy(
            scheduleFingerprint = scheduleFingerprint
        )
        return scheduledDraft.copy(
            fingerprint = PreflightExportReceiptCodec
                .fingerprintFor(scheduledDraft)
        )
    }
}

internal object PreflightExportReceiptCodec {
    private const val VERSION_V2 =
        "sport-pulse-preflight-export-receipt-v2"
    private const val LEGACY_VERSION_V1 =
        "sport-pulse-preflight-export-receipt-v1"
    private const val PART_COUNT_V2 = 12
    private const val PART_COUNT_V1 = 11
    private const val NO_SLOTS = "-"
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(receipt: PreflightExportReceipt): String {
        val expectedSchedule =
            PreflightScheduleFingerprint.forReceipt(receipt)
        val expectedFingerprint = fingerprintFor(receipt)
        require(
            secureEquals(
                expectedSchedule,
                receipt.scheduleFingerprint
            )
        )
        require(
            secureEquals(
                expectedFingerprint,
                receipt.fingerprint
            )
        )
        return "${payloadV2(receipt)}|$expectedFingerprint"
    }

    fun decode(encoded: String): PreflightExportReceipt? {
        return runCatching {
            val parts = encoded.split('|')
            when (parts.firstOrNull()) {
                VERSION_V2 -> decodeV2(parts)
                LEGACY_VERSION_V1 -> decodeV1(parts)
                else -> error("Unknown preflight receipt version")
            }
        }.getOrNull()
    }

    internal fun encodeLegacyV1(
        receipt: PreflightExportReceipt
    ): String {
        require(!receipt.withdrawn)
        require(secureEquals(
            receipt.scheduleFingerprint,
            PreflightScheduleFingerprint.forReceipt(receipt)
        ))
        require(secureEquals(
            receipt.fingerprint,
            fingerprintFor(receipt)
        ))
        val payload = payloadV1(receipt)
        return "$payload|${digest(payload)}"
    }

    private fun decodeV2(
        parts: List<String>
    ): PreflightExportReceipt {
        require(parts.size == PART_COUNT_V2)
        val receipt = restore(
            parts = parts,
            withdrawn = when (parts[10]) {
                "1" -> true
                "0" -> false
                else -> error("Invalid withdrawn flag")
            }
        )
        require(secureEquals(
            receipt.scheduleFingerprint,
            parts[4]
        ))
        require(secureEquals(receipt.fingerprint, parts[11]))
        return receipt
    }

    private fun decodeV1(
        parts: List<String>
    ): PreflightExportReceipt {
        require(parts.size == PART_COUNT_V1)
        val receipt = restore(parts = parts, withdrawn = false)
        require(secureEquals(
            receipt.scheduleFingerprint,
            parts[4]
        ))
        require(secureEquals(digest(payloadV1(receipt)), parts[10]))
        return receipt
    }

    private fun restore(
        parts: List<String>,
        withdrawn: Boolean
    ): PreflightExportReceipt {
        return PreflightExportReceiptFactory.restore(
            eventId = decodeText(parts[1]),
            eventLabel = decodeText(parts[2]),
            protocolFingerprint = parts[3],
            startAt = parts[5].toLong(),
            selectedZone = RegionalZone.valueOf(parts[6]),
            sequence = parts[7].toInt(),
            exportedAt = parts[8].toLong(),
            slots = decodeSlots(parts[9]),
            withdrawn = withdrawn
        )
    }

    internal fun fingerprintFor(
        receipt: PreflightExportReceipt
    ): String {
        return digest(payloadV2(receipt))
    }

    private fun digest(payload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun payloadV2(receipt: PreflightExportReceipt): String {
        return listOf(
            VERSION_V2,
            encodeText(receipt.eventId),
            encodeText(receipt.eventLabel),
            receipt.protocolFingerprint.lowercase(),
            receipt.scheduleFingerprint.lowercase(),
            receipt.startAt.toString(),
            receipt.selectedZone.name,
            receipt.sequence.toString(),
            receipt.exportedAt.toString(),
            encodeSlots(receipt.slots),
            if (receipt.withdrawn) "1" else "0"
        ).joinToString("|")
    }

    private fun payloadV1(receipt: PreflightExportReceipt): String {
        return listOf(
            LEGACY_VERSION_V1,
            encodeText(receipt.eventId),
            encodeText(receipt.eventLabel),
            receipt.protocolFingerprint.lowercase(),
            receipt.scheduleFingerprint.lowercase(),
            receipt.startAt.toString(),
            receipt.selectedZone.name,
            receipt.sequence.toString(),
            receipt.exportedAt.toString(),
            encodeSlots(receipt.slots)
        ).joinToString("|")
    }

    private fun encodeSlots(slots: List<PreflightReceiptSlot>): String {
        if (slots.isEmpty()) return NO_SLOTS
        return slots.joinToString(";") { slot ->
            listOf(
                slot.scheduledAt.toString(),
                if (slot.immediate) "1" else "0",
                slot.factorKey
            ).joinToString(",")
        }
    }

    private fun decodeSlots(value: String): List<PreflightReceiptSlot> {
        if (value == NO_SLOTS) return emptyList()
        return value.split(';').map { encodedSlot ->
            val parts = encodedSlot.split(',')
            require(parts.size == 3)
            val factors = parts[2].split('.').map {
                SignalFactor.valueOf(it)
            }
            PreflightReceiptSlot(
                scheduledAt = parts[0].toLong(),
                immediate = when (parts[1]) {
                    "1" -> true
                    "0" -> false
                    else -> error("Invalid immediate flag")
                },
                factors = factors
            )
        }
    }

    private fun encodeText(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeText(value: String): String {
        return String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        )
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.lowercase().toByteArray(StandardCharsets.US_ASCII),
            right.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }
}

internal enum class PreflightDriftKind {
    START,
    WINDOWS,
    ZONE,
    LABEL,
    METADATA
}

internal enum class PreflightSyncState {
    NOT_EXPORTED,
    CURRENT,
    STALE,
    WITHDRAWN,
    TAMPERED
}

internal data class PreflightSyncResult(
    val state: PreflightSyncState,
    val currentScheduleFingerprint: String,
    val receipt: PreflightExportReceipt?,
    val drift: List<PreflightDriftKind>
) {
    init {
        require(HEX_64.matches(currentScheduleFingerprint))
        require(drift.distinct().size == drift.size)
        when (state) {
            PreflightSyncState.NOT_EXPORTED,
            PreflightSyncState.TAMPERED -> require(receipt == null)
            PreflightSyncState.CURRENT -> {
                require(!requireNotNull(receipt).withdrawn)
                require(drift.isEmpty())
            }
            PreflightSyncState.STALE -> {
                require(!requireNotNull(receipt).withdrawn)
                require(drift.isNotEmpty())
            }
            PreflightSyncState.WITHDRAWN -> {
                require(requireNotNull(receipt).withdrawn)
                require(drift.isEmpty())
            }
        }
    }

    val shortScheduleFingerprint: String
        get() = currentScheduleFingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object PreflightSyncEngine {
    fun evaluate(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        stored: PreflightReceiptReadResult
    ): PreflightSyncResult {
        val currentFingerprint =
            PreflightScheduleFingerprint.forProtocol(
                protocol,
                selectedZone
            )
        if (stored.integrity == PreflightReceiptIntegrity.EMPTY) {
            return PreflightSyncResult(
                state = PreflightSyncState.NOT_EXPORTED,
                currentScheduleFingerprint = currentFingerprint,
                receipt = null,
                drift = emptyList()
            )
        }
        if (stored.integrity == PreflightReceiptIntegrity.TAMPERED) {
            return PreflightSyncResult(
                state = PreflightSyncState.TAMPERED,
                currentScheduleFingerprint = currentFingerprint,
                receipt = null,
                drift = emptyList()
            )
        }
        val receipt = requireNotNull(stored.receipt)
        require(receipt.eventId == protocol.eventId)
        if (receipt.withdrawn) {
            return PreflightSyncResult(
                state = PreflightSyncState.WITHDRAWN,
                currentScheduleFingerprint = currentFingerprint,
                receipt = receipt,
                drift = emptyList()
            )
        }
        if (secureEquals(
                receipt.scheduleFingerprint,
                currentFingerprint
            )
        ) {
            return PreflightSyncResult(
                state = PreflightSyncState.CURRENT,
                currentScheduleFingerprint = currentFingerprint,
                receipt = receipt,
                drift = emptyList()
            )
        }
        val currentSlots = protocol.slots.map { slot ->
            PreflightReceiptSlot(
                scheduledAt = slot.scheduledAt,
                immediate = slot.immediate,
                factors = slot.factors
            )
        }
        val drift = buildList {
            if (receipt.startAt != protocol.start.startAt) {
                add(PreflightDriftKind.START)
            }
            if (receipt.slots != currentSlots) {
                add(PreflightDriftKind.WINDOWS)
            }
            if (receipt.selectedZone != selectedZone) {
                add(PreflightDriftKind.ZONE)
            }
            if (receipt.eventLabel != protocol.eventLabel) {
                add(PreflightDriftKind.LABEL)
            }
            if (isEmpty()) add(PreflightDriftKind.METADATA)
        }
        return PreflightSyncResult(
            state = PreflightSyncState.STALE,
            currentScheduleFingerprint = currentFingerprint,
            receipt = receipt,
            drift = drift
        )
    }

    fun receiptForExport(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        stored: PreflightReceiptReadResult,
        exportedAt: Long
    ): PreflightExportReceipt {
        val previousSequence = stored.receipt?.sequence ?: 0
        require(previousSequence < Int.MAX_VALUE)
        return PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = selectedZone,
            sequence = previousSequence + 1,
            exportedAt = exportedAt
        )
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.toByteArray(StandardCharsets.US_ASCII),
            right.toByteArray(StandardCharsets.US_ASCII)
        )
    }
}
