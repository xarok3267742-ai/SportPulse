package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FactReceiptTest {
    @Test
    fun oneSourceCreatesSingleSourceReceipt() {
        val receipt = receipt(
            secondarySource = null,
            audit = SourceAuditState.INDEPENDENT
        )

        assertEquals(1, receipt.sourceCount)
        assertNull(receipt.secondarySource)
        assertEquals(
            SourceAuditState.UNAUDITED,
            receipt.sourceAuditState
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            receipt.claimedEvidence
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            receipt.effectiveEvidence
        )
    }

    @Test
    fun distinctIndependentSourcesCreateEffectiveQuorum() {
        val receipt = receipt(
            secondarySource = "https://league.example/report",
            audit = SourceAuditState.INDEPENDENT
        )

        assertEquals(2, receipt.sourceCount)
        assertEquals(
            EvidenceLevel.QUORUM,
            receipt.claimedEvidence
        )
        assertEquals(
            EvidenceLevel.QUORUM,
            receipt.effectiveEvidence
        )
    }

    @Test
    fun duplicateHostIsAlwaysCappedAsSharedLineage() {
        val receipt = receipt(
            primarySource = "https://www.club.example/news/1",
            secondarySource = "https://club.example/news/2",
            audit = SourceAuditState.INDEPENDENT
        )

        assertEquals(
            SourceAuditState.SHARED_LINEAGE,
            receipt.sourceAuditState
        )
        assertEquals(
            EvidenceLevel.QUORUM,
            receipt.claimedEvidence
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            receipt.effectiveEvidence
        )
    }

    @Test
    fun conflictFailsClosedToUnconfirmed() {
        val receipt = receipt(
            secondarySource = "Независимая база",
            audit = SourceAuditState.CONFLICT
        )

        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            receipt.effectiveEvidence
        )
    }

    @Test
    fun codecRoundTripPreservesSealedReceipt() {
        val original = receipt(
            secondarySource = "Независимая база",
            audit = SourceAuditState.INDEPENDENT,
            coverage = FactReceiptCoverage.COUNTERCHECKED
        )

        val decoded = FactReceiptCodec.decode(
            FactReceiptCodec.encode(original)
        )

        assertEquals(original, decoded)
    }

    @Test
    fun codecRejectsChangedStatement() {
        val original = receipt()
        val changed = FactReceiptCodec.encode(original).replace(
            original.statement,
            "Изменённый тезис без новой пломбы"
        )

        assertThrows(IllegalArgumentException::class.java) {
            FactReceiptCodec.decode(changed)
        }
    }

    @Test
    fun fingerprintChangesWithCoverageAndRemainsStable() {
        val core = receipt(coverage = FactReceiptCoverage.CORE)
        val coreAgain = receipt(coverage = FactReceiptCoverage.CORE)
        val detailed = receipt(
            coverage = FactReceiptCoverage.DETAILS
        )

        assertEquals(core.fingerprint, coreAgain.fingerprint)
        assertNotEquals(core.fingerprint, detailed.fingerprint)
    }

    @Test
    fun tooShortStatementIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            receipt(statement = "Коротко")
        }
    }

    private fun receipt(
        statement: String =
            "Подтверждён основной факт для выбранного события",
        primarySource: String = "Официальный сайт клуба",
        secondarySource: String? = null,
        audit: SourceAuditState = SourceAuditState.UNAUDITED,
        coverage: FactReceiptCoverage = FactReceiptCoverage.CORE
    ): FactReceipt {
        return FactReceiptFactory.create(
            eventId = "api_football_9101",
            factor = SignalFactor.LINEUP,
            statement = statement,
            primarySource = primarySource,
            secondarySource = secondarySource,
            sourceAuditState = audit,
            coverage = coverage,
            checkedAt = 1_780_000_000_000L
        )
    }
}
