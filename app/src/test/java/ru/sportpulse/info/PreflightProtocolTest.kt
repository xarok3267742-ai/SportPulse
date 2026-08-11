package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightProtocolEngineTest {
    private val minute = 60_000L
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = instant("2026-08-03T10:00:00Z")

    @Test
    fun allFreshChecksHoldInsideShortestLifetime() {
        val event = event(startAt = now + 5L * hour)
        val protocol = protocol(event = event)

        assertEquals(PreflightProtocolState.SEALED, protocol.state)
        assertEquals(5, protocol.holdingCount)
        assertEquals(0, protocol.actionCount)
        assertTrue(protocol.slots.isEmpty())
        assertTrue(protocol.checks.all {
            it.state == PreflightFactorState.HOLDS
        })
    }

    @Test
    fun futureChecksAreOrderedIntoSafeSlots() {
        val event = event(startAt = now + 48L * hour)
        val protocol = protocol(event = event)

        assertEquals(PreflightProtocolState.PLANNED, protocol.state)
        assertEquals(4, protocol.actionCount)
        assertEquals(4, protocol.slots.size)
        assertEquals(
            listOf(
                SignalFactor.CONTEXT,
                SignalFactor.LOAD,
                SignalFactor.SOURCES,
                SignalFactor.LINEUP
            ),
            protocol.slots.map { it.factors.single() }
        )
        assertTrue(protocol.slots.all { !it.immediate })
    }

    @Test
    fun alreadyOpenWindowBecomesImmediateSlot() {
        val event = event(startAt = now + 5L * hour)
        val checkedAt = List(5) { index ->
            if (index == SignalFactor.LINEUP.ordinal) {
                now - 2L * hour
            } else {
                now
            }
        }
        val protocol = protocol(
            event = event,
            checkedAt = checkedAt
        )

        assertEquals(
            PreflightProtocolState.ACTION_NOW,
            protocol.state
        )
        assertEquals(1, protocol.slots.size)
        assertTrue(protocol.slots.single().immediate)
        assertEquals(
            listOf(SignalFactor.LINEUP),
            protocol.slots.single().factors
        )
        assertEquals(now, protocol.slots.single().scheduledAt)
    }

    @Test
    fun missingFactorsShareOneImmediateSlot() {
        val event = event(startAt = now + 5L * hour)
        val levels = EvidenceAssessment(
            listOf(
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.UNCONFIRMED
            )
        )
        val protocol = protocol(event = event, levels = levels)

        assertEquals(
            PreflightProtocolState.INCOMPLETE,
            protocol.state
        )
        assertEquals(2, protocol.missingCount)
        assertEquals(1, protocol.slots.size)
        assertEquals(
            listOf(SignalFactor.FORM, SignalFactor.SOURCES),
            protocol.slots.single().factors
        )
        assertTrue(protocol.slots.single().immediate)
    }

    @Test
    fun futureWindowRoundsUpAndNeverSchedulesEarly() {
        val event = event(
            startAt = now + 8L * hour + 30L * 1000L
        )
        val protocol = protocol(event = event)
        val lineup = protocol.checks[
            SignalFactor.LINEUP.ordinal
        ]

        assertEquals(PreflightFactorState.SCHEDULED, lineup.state)
        assertEquals(
            now + 2L * hour + 2L * minute,
            lineup.scheduledAt
        )
    }

    @Test
    fun windowLaterInCurrentMinuteIsNotMarkedImmediate() {
        val event = event(
            startAt = now + 47L * hour +
                59L * minute + 30L * 1000L
        )
        val checkedAt = List(5) { index ->
            if (index == SignalFactor.CONTEXT.ordinal) {
                now - minute
            } else {
                now
            }
        }
        val protocol = protocol(
            event = event,
            checkedAt = checkedAt
        )
        val context = protocol.checks[
            SignalFactor.CONTEXT.ordinal
        ]

        assertEquals(PreflightFactorState.SCHEDULED, context.state)
        assertEquals(now + minute, context.scheduledAt)
        assertFalse(protocol.slots.first().immediate)
    }

    @Test
    fun fingerprintIsDeterministicAndIncludesEventLabel() {
        val firstEvent = event(startAt = now + 48L * hour)
        val first = protocol(event = firstEvent)
        val same = protocol(event = firstEvent)
        val renamed = protocol(
            event = firstEvent.copy(match = "Новая афиша")
        )

        assertEquals(first.fingerprint, same.fingerprint)
        assertNotEquals(first.fingerprint, renamed.fingerprint)
        assertEquals(64, first.fingerprint.length)
    }

    @Test
    fun protocolRejectsRelayFromAnotherEvent() {
        val first = event(
            id = "first",
            startAt = now + 5L * hour
        )
        val second = first.copy(id = "second")
        val relay = relay(first)

        assertThrows(IllegalArgumentException::class.java) {
            PreflightProtocolEngine.evaluate(second, relay)
        }
    }

    private fun protocol(
        event: SportEvent,
        levels: EvidenceAssessment =
            EvidenceAssessment.singleSource(),
        checkedAt: List<Long> = List(5) { now }
    ): PreflightProtocol {
        return PreflightProtocolEngine.evaluate(
            event = event,
            relay = relay(
                event = event,
                levels = levels,
                checkedAt = checkedAt
            )
        )
    }

    private fun relay(
        event: SportEvent,
        levels: EvidenceAssessment =
            EvidenceAssessment.singleSource(),
        checkedAt: List<Long> = List(5) { now }
    ): EvidenceRelayResult {
        return requireNotNull(
            EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = event,
                    assessment = SignalAssessment(List(5) { 70 }),
                    claimedEvidence = levels,
                    sourceAudit = SourceAuditAssessment.unaudited(),
                    timeline = EvidenceTimeline(checkedAt)
                ),
                now = now
            )
        )
    }

    private fun event(
        id: String = "preflight-event",
        startAt: Long
    ): SportEvent {
        return SportEvent(
            id = id,
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
            startAt = startAt
        )
    }

    private fun instant(value: String): Long {
        return Instant.parse(value).toEpochMilli()
    }
}

class PreflightCalendarEncoderTest {
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = Instant.parse(
        "2026-08-03T10:00:00Z"
    ).toEpochMilli()

    @Test
    fun sealedProtocolExportsOnlyKickoffMarker() {
        val protocol = protocol(startAt = now + 5L * hour)
        val encoded = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.MOSCOW
        )

        assertEquals(1, encoded.countOccurrences("BEGIN:VEVENT"))
        assertFalse(encoded.contains("BEGIN:VALARM"))
        assertTrue(encoded.contains("DTSTART:20260803T150000Z"))
        assertTrue(encoded.endsWith("\r\n"))
    }

    @Test
    fun plannedProtocolExportsEverySlotAndKickoff() {
        val protocol = protocol(startAt = now + 48L * hour)
        val encoded = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.MOSCOW
        )

        assertEquals(5, encoded.countOccurrences("BEGIN:VEVENT"))
        assertEquals(4, encoded.countOccurrences("BEGIN:VALARM"))
        assertEquals(5, unfold(encoded).count {
            it.startsWith("UID:")
        })
    }

    @Test
    fun calendarUsesUtcInstantsAndRecordsSelectedZone() {
        val protocol = protocol(startAt = now + 48L * hour)
        val lines = unfold(
            PreflightCalendarEncoder.encode(
                protocol,
                RegionalZone.ASTANA
            )
        )

        assertTrue(lines.contains("X-WR-TIMEZONE:Asia/Almaty"))
        assertTrue(lines.any {
            it == "DTSTART:20260805T100000Z"
        })
        assertTrue(lines.any {
            it.startsWith("DESCRIPTION:") &&
                it.contains("Астана")
        })
    }

    @Test
    fun textValuesAreEscapedWithoutLosingUnicode() {
        val protocol = protocol(
            startAt = now + 5L * hour,
            match = "А,Б;В\\Г\nД"
        )
        val lines = unfold(
            PreflightCalendarEncoder.encode(
                protocol,
                RegionalZone.MOSCOW
            )
        )

        assertTrue(lines.contains(
            "SUMMARY:Старт: А\\,Б\\;В\\\\Г\\nД"
        ))
        assertTrue(lines.contains(
            "X-WR-CALNAME:Спорт Пульс • А\\,Б\\;В\\\\Г\\nД"
        ))
    }

    @Test
    fun everyPhysicalLineFitsRfc5545OctetLimit() {
        val protocol = protocol(
            startAt = now + 48L * hour,
            match = "Очень длинное название события для проверки " +
                "корректного складывания строк календаря"
        )
        val encoded = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.YEKATERINBURG
        )

        assertFalse(encoded.replace("\r\n", "").contains('\n'))
        encoded.split("\r\n")
            .filter { it.isNotEmpty() }
            .forEach { line ->
                assertTrue(
                    "Line exceeds 75 octets: $line",
                    line.toByteArray(StandardCharsets.UTF_8).size <= 75
                )
            }
    }

    @Test
    fun encodingAndFileNameAreDeterministic() {
        val protocol = protocol(startAt = now + 48L * hour)
        val receipt = PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = RegionalZone.MINSK,
            sequence = 1,
            exportedAt = protocol.evaluatedAt
        )
        val first = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.MINSK
        )
        val second = PreflightCalendarEncoder.encode(
            protocol,
            RegionalZone.MINSK
        )

        assertEquals(first, second)
        assertEquals(
            "sport-pulse-preflight-${
                receipt.scheduleFingerprint.take(12)
            }.ics",
            PreflightCalendarEncoder.fileName(receipt)
        )
    }

    @Test
    fun eventUidsAreUnique() {
        val protocol = protocol(startAt = now + 48L * hour)
        val uids = unfold(
            PreflightCalendarEncoder.encode(
                protocol,
                RegionalZone.MOSCOW
            )
        ).filter { it.startsWith("UID:") }

        assertEquals(uids.size, uids.toSet().size)
        assertTrue(uids.all {
            it.endsWith("@sportpulse.local")
        })
    }

    private fun protocol(
        startAt: Long,
        match: String = "Команда А - Команда Б"
    ): PreflightProtocol {
        val event = SportEvent(
            id = "calendar-event",
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
