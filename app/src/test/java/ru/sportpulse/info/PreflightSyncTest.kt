package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightSyncTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-03T10:00:00Z"
    ).toEpochMilli()

    @Test
    fun receiptCodecRoundTripsUnicodeAndSlots() {
        val protocol = protocol(
            startAt = now + 48L * hour,
            match = "Минск • Команда А — Команда Б"
        )
        val receipt = receipt(
            protocol = protocol,
            zone = RegionalZone.MINSK,
            sequence = 7
        )

        val decoded = PreflightExportReceiptCodec.decode(
            PreflightExportReceiptCodec.encode(receipt)
        )

        assertEquals(receipt, decoded)
    }

    @Test
    fun receiptCodecRejectsSingleCharacterTampering() {
        val encoded = PreflightExportReceiptCodec.encode(
            receipt(protocol(now + 48L * hour))
        )
        val replacement = if (encoded.last() == '0') '1' else '0'

        assertNull(
            PreflightExportReceiptCodec.decode(
                encoded.dropLast(1) + replacement
            )
        )
    }

    @Test
    fun scheduleSealIgnoresOrdinaryMinuteProgression() {
        val first = protocol(
            startAt = now + 48L * hour,
            evaluatedAt = now
        )
        val minuteLater = first.copy(
            evaluatedAtMinute = first.evaluatedAtMinute + 1L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(first.slots, minuteLater.slots)
        assertNotEquals(first.fingerprint, minuteLater.fingerprint)
        assertEquals(
            PreflightScheduleFingerprint.forProtocol(
                first,
                RegionalZone.MOSCOW
            ),
            PreflightScheduleFingerprint.forProtocol(
                minuteLater,
                RegionalZone.MOSCOW
            )
        )
    }

    @Test
    fun syncDistinguishesMissingAndCurrentReceipt() {
        val protocol = protocol(now + 48L * hour)
        val missing = PreflightSyncEngine.evaluate(
            protocol,
            RegionalZone.MOSCOW,
            emptyStored()
        )
        val exportedReceipt = receipt(protocol)
        val current = PreflightSyncEngine.evaluate(
            protocol,
            RegionalZone.MOSCOW,
            validStored(exportedReceipt)
        )

        assertEquals(PreflightSyncState.NOT_EXPORTED, missing.state)
        assertEquals(PreflightSyncState.CURRENT, current.state)
        assertEquals(exportedReceipt, current.receipt)
        assertTrue(current.drift.isEmpty())
    }

    @Test
    fun syncExplainsEveryCalendarSignificantChangeInOrder() {
        val original = protocol(now + 48L * hour)
        val exportedReceipt = receipt(original)
        val changed = original.copy(
            eventLabel = "Новая афиша",
            start = original.start.copy(
                startAt = original.start.startAt + 5L * minute
            ),
            slots = original.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(scheduledAt = slot.scheduledAt + minute)
                } else {
                    slot
                }
            },
            fingerprint = "b".repeat(64)
        )

        val result = PreflightSyncEngine.evaluate(
            changed,
            RegionalZone.MINSK,
            validStored(exportedReceipt)
        )

        assertEquals(PreflightSyncState.STALE, result.state)
        assertEquals(
            listOf(
                PreflightDriftKind.START,
                PreflightDriftKind.WINDOWS,
                PreflightDriftKind.ZONE,
                PreflightDriftKind.LABEL
            ),
            result.drift
        )
    }

    @Test
    fun syncFailsClosedForTamperedReceipt() {
        val result = PreflightSyncEngine.evaluate(
            protocol(now + 48L * hour),
            RegionalZone.MOSCOW,
            PreflightReceiptReadResult(
                integrity = PreflightReceiptIntegrity.TAMPERED,
                receipt = null
            )
        )

        assertEquals(PreflightSyncState.TAMPERED, result.state)
        assertNull(result.receipt)
    }

    @Test
    fun exportRevisionIncrementsOnlyFromValidReceipt() {
        val protocol = protocol(now + 48L * hour)
        val previous = receipt(protocol, sequence = 7)

        val next = PreflightSyncEngine.receiptForExport(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            stored = validStored(previous),
            exportedAt = now + minute
        )
        val reset = PreflightSyncEngine.receiptForExport(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            stored = PreflightReceiptReadResult(
                PreflightReceiptIntegrity.TAMPERED,
                null
            ),
            exportedAt = now + minute
        )

        assertEquals(8, next.sequence)
        assertEquals(1, reset.sequence)
    }

    @Test
    fun exportRevisionRefusesIntegerOverflow() {
        val protocol = protocol(now + 48L * hour)
        val maximum = receipt(
            protocol = protocol,
            sequence = Int.MAX_VALUE
        )

        assertThrows(IllegalArgumentException::class.java) {
            PreflightSyncEngine.receiptForExport(
                protocol = protocol,
                selectedZone = RegionalZone.MOSCOW,
                stored = validStored(maximum),
                exportedAt = now + minute
            )
        }
    }

    @Test
    fun withdrawalReceiptPreservesPlanAndIncrementsSequence() {
        val active = receipt(
            protocol = protocol(now + 48L * hour),
            zone = RegionalZone.MINSK,
            sequence = 4
        )

        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = active,
            exportedAt = now + minute
        )

        assertTrue(withdrawal.withdrawn)
        assertEquals(5, withdrawal.sequence)
        assertEquals(active.eventId, withdrawal.eventId)
        assertEquals(active.startAt, withdrawal.startAt)
        assertEquals(active.selectedZone, withdrawal.selectedZone)
        assertEquals(active.slots, withdrawal.slots)
        assertEquals(
            active.scheduleFingerprint,
            withdrawal.scheduleFingerprint
        )
        assertNotEquals(active.fingerprint, withdrawal.fingerprint)
    }

    @Test
    fun withdrawalRejectsTamperedActiveReceipt() {
        val active = receipt(protocol(now + 48L * hour))
        val tampered = active.copy(
            eventLabel = "Подмененная афиша"
        )

        assertThrows(IllegalArgumentException::class.java) {
            PreflightExportReceiptFactory.withdraw(
                previous = tampered,
                exportedAt = now + minute
            )
        }
    }

    @Test
    fun withdrawnReceiptRoundTripsAndFlagIsSealed() {
        val active = receipt(protocol(now + 48L * hour))
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = active,
            exportedAt = now + minute
        )
        val encoded = PreflightExportReceiptCodec.encode(withdrawal)
        val parts = encoded.split('|').toMutableList()
        parts[10] = "0"

        assertEquals(
            withdrawal,
            PreflightExportReceiptCodec.decode(encoded)
        )
        assertNull(
            PreflightExportReceiptCodec.decode(
                parts.joinToString("|")
            )
        )
    }

    @Test
    fun legacyReceiptMigratesWithoutLosingRevision() {
        val active = receipt(
            protocol = protocol(now + 48L * hour),
            sequence = 9
        )
        val legacy = PreflightExportReceiptCodec
            .encodeLegacyV1(active)

        val migrated = PreflightExportReceiptCodec.decode(legacy)

        assertEquals(active, migrated)
        assertFalse(checkNotNull(migrated).withdrawn)
        assertTrue(
            PreflightExportReceiptCodec.encode(migrated)
                .startsWith(
                    "sport-pulse-preflight-export-receipt-v2|"
                )
        )
    }

    @Test
    fun syncKeepsWithdrawalExplicitAcrossScheduleChanges() {
        val original = protocol(now + 48L * hour)
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = receipt(original),
            exportedAt = now + minute
        )
        val renamed = original.copy(
            eventLabel = "Новая афиша",
            fingerprint = "c".repeat(64)
        )

        val result = PreflightSyncEngine.evaluate(
            protocol = renamed,
            selectedZone = RegionalZone.MINSK,
            stored = validStored(withdrawal)
        )

        assertEquals(PreflightSyncState.WITHDRAWN, result.state)
        assertEquals(withdrawal, result.receipt)
        assertTrue(result.drift.isEmpty())
    }

    private fun receipt(
        protocol: PreflightProtocol,
        zone: RegionalZone = RegionalZone.MOSCOW,
        sequence: Int = 1
    ): PreflightExportReceipt {
        return PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = zone,
            sequence = sequence,
            exportedAt = now
        )
    }

    private fun validStored(
        receipt: PreflightExportReceipt
    ): PreflightReceiptReadResult {
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.VALID,
            receipt = receipt
        )
    }

    private fun emptyStored(): PreflightReceiptReadResult {
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.EMPTY,
            receipt = null
        )
    }

    private fun protocol(
        startAt: Long,
        match: String = "Команда А - Команда Б",
        evaluatedAt: Long = now
    ): PreflightProtocol {
        val event = SportEvent(
            id = "sync-event",
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
            startAt = startAt
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
                now = evaluatedAt
            )
        )
        return PreflightProtocolEngine.evaluate(event, relay)
    }
}

class PreflightCalendarRevisionTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-03T10:00:00Z"
    ).toEpochMilli()

    @Test
    fun stableUidsIgnoreMovedTimes() {
        val factors = listOf(
            SignalFactor.FORM,
            SignalFactor.SOURCES
        )

        assertEquals(
            PreflightCalendarEncoder.slotUid("event", factors),
            PreflightCalendarEncoder.slotUid("event", factors)
        )
        assertEquals(
            PreflightCalendarEncoder.kickoffUid("event"),
            PreflightCalendarEncoder.kickoffUid("event")
        )
        assertNotEquals(
            PreflightCalendarEncoder.slotUid("event", factors),
            PreflightCalendarEncoder.slotUid(
                "other-event",
                factors
            )
        )
    }

    @Test
    fun movedFactorGroupUpdatesWithoutCancellation() {
        val previous = protocol()
        val movedSlot = previous.slots.first()
        val current = previous.copy(
            slots = previous.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(scheduledAt = slot.scheduledAt + minute)
                } else {
                    slot
                }
            },
            fingerprint = "b".repeat(64)
        )
        val encoded = encodeRevision(previous, current)
        val logicalLines = unfold(encoded)

        assertFalse(encoded.contains("STATUS:CANCELLED"))
        assertTrue(logicalLines.contains(
            "UID:${PreflightCalendarEncoder.slotUid(
                previous.eventId,
                movedSlot.factors
            )}"
        ))
        assertEquals(
            current.slots.size + 1,
            logicalLines.count { it == "SEQUENCE:2" }
        )
    }

    @Test
    fun removedFactorGroupEmitsCancellationWithoutAlarm() {
        val previous = protocol()
        val removed = previous.slots.first()
        val current = previous.copy(
            slots = previous.slots.drop(1),
            fingerprint = "c".repeat(64)
        )
        val encoded = encodeRevision(previous, current)
        val cancelled = encoded
            .split("BEGIN:VEVENT")
            .drop(1)
            .map { it.substringBefore("END:VEVENT") }
            .single { it.contains("STATUS:CANCELLED") }

        assertTrue(cancelled.contains(
            "UID:${PreflightCalendarEncoder.slotUid(
                previous.eventId,
                removed.factors
            )}"
        ))
        assertTrue(cancelled.contains("SEQUENCE:2"))
        assertFalse(cancelled.contains("BEGIN:VALARM"))
        assertEquals(
            current.slots.size,
            encoded.countOccurrences("BEGIN:VALARM")
        )
    }

    @Test
    fun revisionCarriesCalendarAndEventIntegrityProperties() {
        val previous = protocol()
        val current = previous.copy(
            eventLabel = "Обновлённая афиша",
            fingerprint = "d".repeat(64)
        )
        val receipt = PreflightExportReceiptFactory.create(
            protocol = current,
            selectedZone = RegionalZone.YEKATERINBURG,
            sequence = 2,
            exportedAt = now + minute
        )
        val encoded = PreflightCalendarEncoder.encode(
            protocol = current,
            selectedZone = RegionalZone.YEKATERINBURG,
            receipt = receipt,
            previousReceipt = PreflightExportReceiptFactory.create(
                protocol = previous,
                selectedZone = RegionalZone.YEKATERINBURG,
                sequence = 1,
                exportedAt = now
            )
        )
        val logicalLines = unfold(encoded)

        assertTrue(logicalLines.contains(
            "X-SPORT-PULSE-SEQUENCE:2"
        ))
        assertTrue(logicalLines.contains(
            "X-SPORT-PULSE-SCHEDULE-SHA256:${
                receipt.scheduleFingerprint
            }"
        ))
        assertTrue(logicalLines.contains(
            "X-SPORT-PULSE-PROTOCOL-SHA256:${
                current.fingerprint
            }"
        ))
    }

    @Test
    fun withdrawalCancelsEveryEventWithoutAlarms() {
        val protocol = protocol()
        val active = PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            sequence = 1,
            exportedAt = now
        )
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = active,
            exportedAt = now + minute
        )
        val encoded = PreflightCalendarEncoder.encodeWithdrawal(
            withdrawal
        )
        val logicalLines = unfold(encoded)
        val eventCount = protocol.slots.size + 1

        assertEquals(
            eventCount,
            logicalLines.count { it == "BEGIN:VEVENT" }
        )
        assertEquals(
            eventCount,
            logicalLines.count { it == "STATUS:CANCELLED" }
        )
        assertEquals(
            eventCount,
            logicalLines.count { it == "SEQUENCE:2" }
        )
        assertFalse(encoded.contains("BEGIN:VALARM"))
        assertTrue(logicalLines.contains(
            "X-SPORT-PULSE-WITHDRAWN:TRUE"
        ))
    }

    @Test
    fun withdrawalAndRestorationKeepStableUids() {
        val protocol = protocol()
        val active = PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            sequence = 1,
            exportedAt = now
        )
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = active,
            exportedAt = now + minute
        )
        val restored = PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = RegionalZone.MOSCOW,
            sequence = 3,
            exportedAt = now + 2L * minute
        )
        val activeUids = unfold(
            PreflightCalendarEncoder.encode(
                protocol,
                RegionalZone.MOSCOW,
                active,
                null
            )
        ).filter { it.startsWith("UID:") }
        val withdrawalUids = unfold(
            PreflightCalendarEncoder.encodeWithdrawal(withdrawal)
        ).filter { it.startsWith("UID:") }
        val restoredCalendar = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.MOSCOW,
            restored,
            withdrawal
        )
        val restoredLines = unfold(restoredCalendar)

        assertEquals(activeUids, withdrawalUids)
        assertEquals(
            activeUids,
            restoredLines.filter { it.startsWith("UID:") }
        )
        assertFalse(restoredCalendar.contains("STATUS:CANCELLED"))
        assertEquals(
            activeUids.size,
            restoredLines.count { it == "SEQUENCE:3" }
        )
    }

    @Test
    fun activeEncoderRejectsWithdrawnReceipt() {
        val protocol = protocol()
        val withdrawal = PreflightExportReceiptFactory.withdraw(
            previous = PreflightExportReceiptFactory.create(
                protocol = protocol,
                selectedZone = RegionalZone.MOSCOW,
                sequence = 1,
                exportedAt = now
            ),
            exportedAt = now + minute
        )

        assertThrows(IllegalArgumentException::class.java) {
            PreflightCalendarEncoder.encode(
                protocol,
                RegionalZone.MOSCOW,
                withdrawal,
                null
            )
        }
    }

    private fun encodeRevision(
        previous: PreflightProtocol,
        current: PreflightProtocol
    ): String {
        return PreflightCalendarEncoder.encode(
            protocol = current,
            selectedZone = RegionalZone.MOSCOW,
            receipt = PreflightExportReceiptFactory.create(
                protocol = current,
                selectedZone = RegionalZone.MOSCOW,
                sequence = 2,
                exportedAt = now + minute
            ),
            previousReceipt = PreflightExportReceiptFactory.create(
                protocol = previous,
                selectedZone = RegionalZone.MOSCOW,
                sequence = 1,
                exportedAt = now
            )
        )
    }

    private fun protocol(): PreflightProtocol {
        val event = SportEvent(
            id = "calendar-revision-event",
            sport = "Футбол",
            tournament = "Тест",
            region = "Россия",
            match = "Команда А - Команда Б",
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

    private fun unfold(value: String): List<String> {
        val result = mutableListOf<String>()
        value.split("\r\n").forEach { line ->
            if (line.startsWith(" ") && result.isNotEmpty()) {
                result[result.lastIndex] =
                    result.last() + line.drop(1)
            } else if (line.isNotEmpty()) {
                result += line
            }
        }
        return result
    }

    private fun String.countOccurrences(value: String): Int {
        return windowed(value.length).count { it == value }
    }
}
