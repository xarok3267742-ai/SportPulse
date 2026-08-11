package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal enum class DecisionLedgerIntegrity {
    EMPTY,
    INTACT,
    TAMPERED
}

internal data class DecisionLedgerRecord(
    val sequence: Long,
    val eventId: String,
    val eventLabel: String,
    val decision: SavedDecision,
    val savedAt: Long,
    val snapshotFingerprint: String,
    val previousFingerprint: String,
    val fingerprint: String
) {
    init {
        require(sequence > 0L)
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(eventLabel.length <= MAX_EVENT_LABEL_LENGTH)
        require(savedAt >= 0L)
        require(HEX_64.matches(snapshotFingerprint.lowercase()))
        require(HEX_64.matches(previousFingerprint.lowercase()))
        require(
            fingerprint.isEmpty() ||
                HEX_64.matches(fingerprint.lowercase())
        )
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        const val MAX_EVENT_LABEL_LENGTH = 200
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal data class DecisionLedger(
    val anchorSequence: Long,
    val anchorFingerprint: String,
    val records: List<DecisionLedgerRecord>,
    val fingerprint: String
) {
    init {
        require(anchorSequence >= 0L)
        require(HEX_64.matches(anchorFingerprint.lowercase()))
        require(records.size <= DecisionLedgerFactory.MAX_RECORDS)
        require(
            fingerprint.isEmpty() ||
                HEX_64.matches(fingerprint.lowercase())
        )
    }

    val totalRecordCount: Long
        get() = anchorSequence + records.size

    val headFingerprint: String
        get() = records.lastOrNull()?.fingerprint
            ?: anchorFingerprint

    val shortFingerprint: String
        get() = fingerprint.take(10).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal data class DecisionLedgerReadResult(
    val integrity: DecisionLedgerIntegrity,
    val ledger: DecisionLedger?
) {
    init {
        when (integrity) {
            DecisionLedgerIntegrity.EMPTY,
            DecisionLedgerIntegrity.INTACT -> requireNotNull(ledger)
            DecisionLedgerIntegrity.TAMPERED -> require(ledger == null)
        }
        if (integrity == DecisionLedgerIntegrity.EMPTY) {
            require(requireNotNull(ledger).records.isEmpty())
        }
        if (integrity == DecisionLedgerIntegrity.INTACT) {
            require(requireNotNull(ledger).records.isNotEmpty())
        }
    }
}

internal object DecisionLedgerFactory {
    const val MAX_RECORDS = 50
    val GENESIS_FINGERPRINT: String = "0".repeat(64)

    fun empty(): DecisionLedger {
        return create(
            anchorSequence = 0L,
            anchorFingerprint = GENESIS_FINGERPRINT,
            records = emptyList()
        )
    }

    fun append(
        ledger: DecisionLedger,
        snapshot: DecisionSnapshot,
        eventLabel: String
    ): DecisionLedger {
        require(
            MessageDigest.isEqual(
                DecisionLedgerCodec.fingerprintFor(ledger)
                    .toByteArray(StandardCharsets.US_ASCII),
                ledger.fingerprint.lowercase()
                    .toByteArray(StandardCharsets.US_ASCII)
            )
        )
        require(ledger.totalRecordCount < Long.MAX_VALUE)
        val record = DecisionLedgerRecordFactory.create(
            sequence = ledger.totalRecordCount + 1L,
            eventId = snapshot.eventId,
            eventLabel = eventLabel,
            decision = snapshot.decision,
            savedAt = snapshot.savedAt,
            snapshotFingerprint = snapshot.fingerprint,
            previousFingerprint = ledger.headFingerprint
        )
        val expanded = ledger.records + record
        return if (expanded.size <= MAX_RECORDS) {
            create(
                anchorSequence = ledger.anchorSequence,
                anchorFingerprint = ledger.anchorFingerprint,
                records = expanded
            )
        } else {
            val dropped = expanded.first()
            create(
                anchorSequence = dropped.sequence,
                anchorFingerprint = dropped.fingerprint,
                records = expanded.drop(1)
            )
        }
    }

    internal fun create(
        anchorSequence: Long,
        anchorFingerprint: String,
        records: List<DecisionLedgerRecord>
    ): DecisionLedger {
        require(anchorSequence >= 0L)
        require(HEX_64.matches(anchorFingerprint.lowercase()))
        require(records.size <= MAX_RECORDS)
        if (anchorSequence == 0L) {
            require(
                anchorFingerprint.equals(
                    GENESIS_FINGERPRINT,
                    ignoreCase = true
                )
            )
        }
        if (records.isEmpty()) {
            require(anchorSequence == 0L)
        }
        var expectedSequence = anchorSequence + 1L
        var expectedPrevious = anchorFingerprint.lowercase()
        records.forEach { record ->
            require(record.sequence == expectedSequence)
            require(
                record.previousFingerprint.equals(
                    expectedPrevious,
                    ignoreCase = true
                )
            )
            require(
                MessageDigest.isEqual(
                    DecisionLedgerRecordCodec
                        .fingerprintFor(record)
                        .toByteArray(StandardCharsets.US_ASCII),
                    record.fingerprint.lowercase()
                        .toByteArray(StandardCharsets.US_ASCII)
                )
            )
            expectedSequence += 1L
            expectedPrevious = record.fingerprint.lowercase()
        }
        val draft = DecisionLedger(
            anchorSequence = anchorSequence,
            anchorFingerprint = anchorFingerprint.lowercase(),
            records = records,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = DecisionLedgerCodec.fingerprintFor(draft)
        )
    }

    private val HEX_64 = Regex("[0-9a-f]{64}")
}

internal object DecisionLedgerRecordFactory {
    fun create(
        sequence: Long,
        eventId: String,
        eventLabel: String,
        decision: SavedDecision,
        savedAt: Long,
        snapshotFingerprint: String,
        previousFingerprint: String
    ): DecisionLedgerRecord {
        val draft = DecisionLedgerRecord(
            sequence = sequence,
            eventId = eventId,
            eventLabel = eventLabel,
            decision = decision,
            savedAt = savedAt,
            snapshotFingerprint = snapshotFingerprint.lowercase(),
            previousFingerprint = previousFingerprint.lowercase(),
            fingerprint = ""
        )
        return draft.copy(
            fingerprint =
                DecisionLedgerRecordCodec.fingerprintFor(draft)
        )
    }
}

internal object DecisionLedgerRecordCodec {
    private const val VERSION =
        "sport-pulse-decision-ledger-record-v1"
    private const val PART_COUNT = 9

    fun encode(record: DecisionLedgerRecord): String {
        val expected = fingerprintFor(record)
        require(
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.US_ASCII),
                record.fingerprint.lowercase()
                    .toByteArray(StandardCharsets.US_ASCII)
            )
        )
        return "${payload(record)}|$expected"
    }

    fun decode(encoded: String): DecisionLedgerRecord? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val record = DecisionLedgerRecordFactory.create(
                sequence = parts[1].toLong(),
                eventId = decodeText(parts[2]),
                eventLabel = decodeText(parts[3]),
                decision = SavedDecision.valueOf(parts[4]),
                savedAt = parts[5].toLong(),
                snapshotFingerprint = parts[6],
                previousFingerprint = parts[7]
            )
            require(
                MessageDigest.isEqual(
                    record.fingerprint.toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    parts[8].lowercase().toByteArray(
                        StandardCharsets.US_ASCII
                    )
                )
            )
            record
        }.getOrNull()
    }

    internal fun fingerprintFor(
        record: DecisionLedgerRecord
    ): String {
        return sha256(payload(record))
    }

    private fun payload(record: DecisionLedgerRecord): String {
        return listOf(
            VERSION,
            record.sequence.toString(),
            encodeText(record.eventId),
            encodeText(record.eventLabel),
            record.decision.name,
            record.savedAt.toString(),
            record.snapshotFingerprint.lowercase(),
            record.previousFingerprint.lowercase()
        ).joinToString("|")
    }

    private fun encodeText(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                value.toByteArray(StandardCharsets.UTF_8)
            )
    }

    private fun decodeText(value: String): String {
        return String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        )
    }
}

internal object DecisionLedgerCodec {
    private const val VERSION =
        "sport-pulse-decision-ledger-v1"
    private const val PART_COUNT = 6
    private const val EMPTY = "-"

    fun encode(ledger: DecisionLedger): String {
        val expected = fingerprintFor(ledger)
        require(
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.US_ASCII),
                ledger.fingerprint.lowercase()
                    .toByteArray(StandardCharsets.US_ASCII)
            )
        )
        return "${payload(ledger)}|$expected"
    }

    fun decode(encoded: String): DecisionLedger? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val expectedCount = parts[3].toInt()
            val records = if (parts[4] == EMPTY) {
                emptyList()
            } else {
                parts[4].split(',').map { token ->
                    val recordEncoded = String(
                        Base64.getUrlDecoder().decode(token),
                        StandardCharsets.UTF_8
                    )
                    requireNotNull(
                        DecisionLedgerRecordCodec.decode(
                            recordEncoded
                        )
                    )
                }
            }
            require(records.size == expectedCount)
            val ledger = DecisionLedgerFactory.create(
                anchorSequence = parts[1].toLong(),
                anchorFingerprint = parts[2],
                records = records
            )
            require(
                MessageDigest.isEqual(
                    ledger.fingerprint.toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    parts[5].lowercase().toByteArray(
                        StandardCharsets.US_ASCII
                    )
                )
            )
            ledger
        }.getOrNull()
    }

    internal fun fingerprintFor(ledger: DecisionLedger): String {
        return sha256(payload(ledger))
    }

    private fun payload(ledger: DecisionLedger): String {
        val records = ledger.records
            .joinToString(",") { record ->
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                        DecisionLedgerRecordCodec.encode(record)
                            .toByteArray(StandardCharsets.UTF_8)
                    )
            }
            .ifEmpty { EMPTY }
        return listOf(
            VERSION,
            ledger.anchorSequence.toString(),
            ledger.anchorFingerprint.lowercase(),
            ledger.records.size.toString(),
            records
        ).joinToString("|")
    }
}

private val hex = "0123456789abcdef".toCharArray()

private fun sha256(payload: String): String {
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
