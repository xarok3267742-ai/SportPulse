package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionLedgerCodecTest {
    @Test
    fun emptyLedgerHasDeterministicGenesis() {
        val first = DecisionLedgerFactory.empty()
        val second = DecisionLedgerFactory.empty()

        assertEquals(0L, first.totalRecordCount)
        assertEquals(
            DecisionLedgerFactory.GENESIS_FINGERPRINT,
            first.headFingerprint
        )
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(
            first,
            DecisionLedgerCodec.decode(
                DecisionLedgerCodec.encode(first)
            )
        )
    }

    @Test
    fun appendedRecordsFormOrderedChain() {
        val first = append(
            DecisionLedgerFactory.empty(),
            index = 1
        )
        val second = append(first, index = 2)

        assertEquals(2L, second.totalRecordCount)
        assertEquals(listOf(1L, 2L), second.records.map {
            it.sequence
        })
        assertEquals(
            first.records.last().fingerprint,
            second.records.last().previousFingerprint
        )
        assertEquals(
            second.records.last().fingerprint,
            second.headFingerprint
        )
    }

    @Test
    fun unicodeLabelsRoundTrip() {
        val ledger = DecisionLedgerFactory.append(
            ledger = DecisionLedgerFactory.empty(),
            snapshot = snapshot(1),
            eventLabel = "Зенит — Кайрат"
        )

        val decoded = DecisionLedgerCodec.decode(
            DecisionLedgerCodec.encode(ledger)
        )

        assertEquals(ledger, decoded)
        assertEquals(
            "Зенит — Кайрат",
            decoded?.records?.single()?.eventLabel
        )
    }

    @Test
    fun sameSnapshotAppendedTwiceCreatesDistinctRecords() {
        val snapshot = snapshot(1)
        val first = DecisionLedgerFactory.append(
            DecisionLedgerFactory.empty(),
            snapshot,
            "Матч"
        )
        val second = DecisionLedgerFactory.append(
            first,
            snapshot,
            "Матч"
        )

        assertNotEquals(
            first.records.single().fingerprint,
            second.records.last().fingerprint
        )
        assertEquals(2L, second.records.last().sequence)
    }

    @Test
    fun changedRecordPayloadIsRejected() {
        val ledger = append(
            DecisionLedgerFactory.empty(),
            index = 1
        )
        val encoded = DecisionLedgerCodec.encode(ledger)
        val parts = encoded.split('|').toMutableList()
        val token = parts[4]
        parts[4] = flip(token)

        assertNull(
            DecisionLedgerCodec.decode(parts.joinToString("|"))
        )
    }

    @Test
    fun changedEnvelopeSealIsRejected() {
        val ledger = append(
            DecisionLedgerFactory.empty(),
            index = 1
        )
        val encoded = DecisionLedgerCodec.encode(ledger)
        val parts = encoded.split('|').toMutableList()
        parts[5] = flip(parts[5])

        assertNull(
            DecisionLedgerCodec.decode(parts.joinToString("|"))
        )
    }

    @Test
    fun missingLastRecordIsRejectedByEnvelope() {
        val ledger = append(
            append(DecisionLedgerFactory.empty(), 1),
            2
        )
        val encoded = DecisionLedgerCodec.encode(ledger)
        val parts = encoded.split('|').toMutableList()
        val tokens = parts[4].split(',')
        parts[3] = "1"
        parts[4] = tokens.first()

        assertNull(
            DecisionLedgerCodec.decode(parts.joinToString("|"))
        )
    }

    @Test
    fun reorderedRecordsAreRejected() {
        val ledger = append(
            append(DecisionLedgerFactory.empty(), 1),
            2
        )
        val encoded = DecisionLedgerCodec.encode(ledger)
        val parts = encoded.split('|').toMutableList()
        parts[4] = parts[4].split(',').reversed()
            .joinToString(",")

        assertNull(
            DecisionLedgerCodec.decode(parts.joinToString("|"))
        )
    }

    @Test
    fun boundedWindowCarriesDroppedRecordIntoAnchor() {
        var ledger = DecisionLedgerFactory.empty()
        repeat(DecisionLedgerFactory.MAX_RECORDS + 5) { index ->
            ledger = append(ledger, index + 1)
        }

        assertEquals(
            DecisionLedgerFactory.MAX_RECORDS,
            ledger.records.size
        )
        assertEquals(5L, ledger.anchorSequence)
        assertEquals(55L, ledger.totalRecordCount)
        assertEquals(6L, ledger.records.first().sequence)
        assertEquals(
            ledger.anchorFingerprint,
            ledger.records.first().previousFingerprint
        )
        assertEquals(
            ledger,
            DecisionLedgerCodec.decode(
                DecisionLedgerCodec.encode(ledger)
            )
        )
    }

    @Test
    fun changingSnapshotChangesRecordAndLedgerSeals() {
        val base = append(
            DecisionLedgerFactory.empty(),
            index = 1
        )
        val changed = DecisionLedgerFactory.append(
            ledger = DecisionLedgerFactory.empty(),
            snapshot = snapshot(
                index = 1,
                decision = SavedDecision.OBSERVE
            ),
            eventLabel = "Событие 1"
        )

        assertNotEquals(
            base.records.single().fingerprint,
            changed.records.single().fingerprint
        )
        assertNotEquals(base.fingerprint, changed.fingerprint)
    }

    @Test
    fun unsupportedStoreVersionIsRejected() {
        val encoded = DecisionLedgerCodec.encode(
            DecisionLedgerFactory.empty()
        )

        assertNull(
            DecisionLedgerCodec.decode(
                encoded.replaceFirst(
                    "sport-pulse-decision-ledger-v1|",
                    "sport-pulse-decision-ledger-v2|"
                )
            )
        )
    }

    @Test
    fun everyGeneratedFingerprintIsLowerHex() {
        val ledger = append(
            DecisionLedgerFactory.empty(),
            index = 1
        )
        val lowerHex = Regex("[0-9a-f]{64}")

        assertTrue(lowerHex.matches(ledger.fingerprint))
        assertTrue(
            lowerHex.matches(
                ledger.records.single().fingerprint
            )
        )
    }

    private fun append(
        ledger: DecisionLedger,
        index: Int
    ): DecisionLedger {
        return DecisionLedgerFactory.append(
            ledger = ledger,
            snapshot = snapshot(index),
            eventLabel = "Событие $index"
        )
    }

    private fun snapshot(
        index: Int,
        decision: SavedDecision = SavedDecision.SKIP
    ): DecisionSnapshot {
        val savedAt = 1_000_000L + index
        return DecisionSnapshotFactory.create(
            eventId = "event_$index",
            decision = decision,
            savedAt = savedAt,
            assessment = SignalAssessment(List(5) { 45 + index }),
            evidence = EvidenceAssessment(
                List(5) { EvidenceLevel.SINGLE_SOURCE }
            ),
            timeline = EvidenceTimeline(List(5) { savedAt })
        )
    }

    private fun flip(value: String): String {
        val first = if (value.first() == 'A') 'B' else 'A'
        return first + value.drop(1)
    }
}
