package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionRadarEngineTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-03T10:00:00Z"
    ).toEpochMilli()

    @Test
    fun radarIsAbsentWithoutCalendarReceipts() {
        val result = RevisionRadarEngine.evaluate(
            events = listOf(radarEvent(protocol("event"))),
            storedReceipts = emptyMap(),
            selectedZone = RegionalZone.MOSCOW,
            now = now
        )

        assertNull(result)
    }

    @Test
    fun matchingReceiptIsCurrentAndOpensEvent() {
        val protocol = protocol("current")
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(radarEvent(protocol)),
                storedReceipts = mapOf(
                    protocol.eventId to valid(receipt(protocol))
                ),
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.CURRENT, entry.state)
        assertEquals(
            RevisionRadarAction.OPEN_EVENT,
            entry.action
        )
        assertEquals(0, result.attentionCount)
        assertEquals(1, result.currentCount)
    }

    @Test
    fun cityChangeBecomesExplainedStalePlan() {
        val protocol = protocol("stale")
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(radarEvent(protocol)),
                storedReceipts = mapOf(
                    protocol.eventId to valid(receipt(protocol))
                ),
                selectedZone = RegionalZone.MINSK,
                now = now
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.STALE, entry.state)
        assertEquals(listOf(PreflightDriftKind.ZONE), entry.drift)
        assertEquals(1, result.attentionCount)
    }

    @Test
    fun futureReceiptWithoutExactCurrentStartCanBeWithdrawn() {
        val protocol = protocol("unresolved")
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(
                    radarEvent(protocol).copy(protocol = null)
                ),
                storedReceipts = mapOf(
                    protocol.eventId to valid(receipt(protocol))
                ),
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.UNRESOLVED, entry.state)
        assertEquals(RevisionRadarAction.WITHDRAW, entry.action)
        assertEquals(1, result.withdrawalCount)
    }

    @Test
    fun removedFutureEventKeepsLabelAndOffersWithdrawal() {
        val protocol = protocol(
            id = "removed",
            match = "Удаленная афиша"
        )
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = emptyList(),
                storedReceipts = mapOf(
                    protocol.eventId to valid(receipt(protocol))
                ),
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.REMOVED, entry.state)
        assertEquals("Удаленная афиша", entry.match)
        assertEquals(RevisionRadarAction.WITHDRAW, entry.action)
        assertTrue(!entry.presentInCatalog)
    }

    @Test
    fun removedPastEventCanBeForgottenLocally() {
        val protocol = protocol("expired")
        val exported = receipt(protocol)
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = emptyList(),
                storedReceipts = mapOf(
                    protocol.eventId to valid(exported)
                ),
                selectedZone = RegionalZone.MOSCOW,
                now = exported.startAt + minute
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.EXPIRED, entry.state)
        assertEquals(RevisionRadarAction.FORGET, entry.action)
        assertEquals(1, result.expiredCount)
    }

    @Test
    fun removedWithdrawnPlanStaysExplicitAndInactive() {
        val protocol = protocol("withdrawn")
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = receipt(protocol),
            exportedAt = now + minute
        )
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = emptyList(),
                storedReceipts = mapOf(
                    protocol.eventId to valid(withdrawal)
                ),
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )
        val entry = result.entries.single()

        assertEquals(RevisionRadarState.WITHDRAWN, entry.state)
        assertEquals(RevisionRadarAction.NONE, entry.action)
        assertEquals(1, result.withdrawnCount)
    }

    @Test
    fun attentionStatesLeadAndCountsStayExact() {
        val tampered = protocol("tampered")
        val stale = protocol("stale")
        val current = protocol("current")
        val removed = protocol("removed")
        val result = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(
                    radarEvent(current, 0),
                    radarEvent(stale, 1),
                    radarEvent(tampered, 2)
                ),
                storedReceipts = mapOf(
                    tampered.eventId to PreflightReceiptReadResult(
                        PreflightReceiptIntegrity.TAMPERED,
                        null
                    ),
                    stale.eventId to valid(receipt(stale)),
                    current.eventId to valid(
                        receipt(current, RegionalZone.MINSK)
                    ),
                    removed.eventId to valid(receipt(removed))
                ),
                selectedZone = RegionalZone.MINSK,
                now = now
            )
        )

        assertEquals(
            listOf(
                RevisionRadarState.TAMPERED,
                RevisionRadarState.REMOVED,
                RevisionRadarState.STALE,
                RevisionRadarState.CURRENT
            ),
            result.entries.map { it.state }
        )
        assertEquals(3, result.attentionCount)
        assertEquals(1, result.withdrawalCount)
        assertEquals(1, result.currentCount)
        assertEquals(3, result.visibleEntries.size)
        assertTrue(result.visibleEntries.all { it.isAttention })
    }

    @Test
    fun radarFingerprintIgnoresNonCalendarMinuteProgression() {
        val firstProtocol = protocol("stable-fingerprint")
        val minuteLater = firstProtocol.copy(
            evaluatedAtMinute = firstProtocol.evaluatedAtMinute + 1L,
            fingerprint = "e".repeat(64)
        )
        val stored = mapOf(
            firstProtocol.eventId to valid(receipt(firstProtocol))
        )
        val first = RevisionRadarEngine.evaluate(
            events = listOf(radarEvent(firstProtocol)),
            storedReceipts = stored,
            selectedZone = RegionalZone.MOSCOW,
            now = now
        )
        val second = RevisionRadarEngine.evaluate(
            events = listOf(radarEvent(minuteLater)),
            storedReceipts = stored,
            selectedZone = RegionalZone.MOSCOW,
            now = now + minute
        )

        assertNotNull(first)
        assertEquals(first?.fingerprint, second?.fingerprint)
    }

    @Test
    fun radarFingerprintCoversVisibleTamperedEventMetadata() {
        val protocol = protocol("tampered-metadata")
        val stored = mapOf(
            protocol.eventId to PreflightReceiptReadResult(
                PreflightReceiptIntegrity.TAMPERED,
                null
            )
        )
        val firstEvent = radarEvent(protocol).copy(protocol = null)
        val first = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(firstEvent),
                storedReceipts = stored,
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )
        val renamed = requireNotNull(
            RevisionRadarEngine.evaluate(
                events = listOf(
                    firstEvent.copy(match = "Новая афиша")
                ),
                storedReceipts = stored,
                selectedZone = RegionalZone.MOSCOW,
                now = now
            )
        )

        assertTrue(first.fingerprint != renamed.fingerprint)
    }

    private fun radarEvent(
        protocol: PreflightProtocol,
        catalogOrder: Int = 0
    ): RevisionRadarEvent {
        return RevisionRadarEvent(
            eventId = protocol.eventId,
            match = protocol.eventLabel,
            sport = "Футбол",
            region = "Россия",
            catalogOrder = catalogOrder,
            protocol = protocol
        )
    }

    private fun receipt(
        protocol: PreflightProtocol,
        zone: RegionalZone = RegionalZone.MOSCOW
    ): PreflightExportReceipt {
        return PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = zone,
            sequence = 1,
            exportedAt = now
        )
    }

    private fun valid(
        receipt: PreflightExportReceipt
    ): PreflightReceiptReadResult {
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.VALID,
            receipt = receipt
        )
    }

    private fun protocol(
        id: String,
        match: String = "Команда А - Команда Б"
    ): PreflightProtocol {
        val event = SportEvent(
            id = id,
            sport = "Футбол",
            tournament = "Тест",
            region = "Россия",
            match = match,
            time = "Тест",
            focus = "Факты",
            note = "Тест",
            tags = emptyList(),
            imageRes = 0,
            seedAssessment = SignalAssessment(List(5) { 70 }),
            startAt = now + 48L * hour
        )
        val relay = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = event.seedAssessment,
                    claimedEvidence =
                        EvidenceAssessment.singleSource(),
                    sourceAudit = SourceAuditAssessment.unaudited(),
                    timeline = EvidenceTimeline(List(5) { now })
                ),
                now = now
            )
        )
        return PreflightProtocolEngine.evaluate(event, relay)
    }
}
