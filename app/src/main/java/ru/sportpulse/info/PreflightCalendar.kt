package ru.sportpulse.info

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object PreflightCalendarEncoder {
    private const val VERSION = "sport-pulse-preflight-calendar-v2"
    private const val MINUTE_MILLIS = 60_000L
    private const val SLOT_DURATION_MILLIS = 15L * MINUTE_MILLIS
    private const val KICKOFF_MARKER_MILLIS = 15L * MINUTE_MILLIS
    private const val MAX_LINE_OCTETS = 75
    private val utcFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun encode(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone
    ): String {
        val receipt = PreflightExportReceiptFactory.create(
            protocol = protocol,
            selectedZone = selectedZone,
            sequence = 1,
            exportedAt = protocol.evaluatedAt
        )
        return encode(
            protocol = protocol,
            selectedZone = selectedZone,
            receipt = receipt,
            previousReceipt = null
        )
    }

    fun encode(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        receipt: PreflightExportReceipt,
        previousReceipt: PreflightExportReceipt?
    ): String {
        require(!receipt.withdrawn)
        require(receipt.eventId == protocol.eventId)
        require(receipt.eventLabel == protocol.eventLabel)
        require(receipt.startAt == protocol.start.startAt)
        require(receipt.selectedZone == selectedZone)
        require(secureEquals(
            receipt.protocolFingerprint,
            protocol.fingerprint
        ))
        require(
            secureEquals(
                receipt.scheduleFingerprint,
                PreflightScheduleFingerprint.forProtocol(
                    protocol,
                    selectedZone
                )
            )
        )
        require(secureEquals(
            receipt.fingerprint,
            PreflightExportReceiptCodec.fingerprintFor(receipt)
        ))
        require(
            previousReceipt == null ||
                previousReceipt.eventId == protocol.eventId &&
                previousReceipt.sequence < receipt.sequence &&
                secureEquals(
                    previousReceipt.scheduleFingerprint,
                    PreflightScheduleFingerprint.forReceipt(
                        previousReceipt
                    )
                ) &&
                secureEquals(
                    previousReceipt.fingerprint,
                    PreflightExportReceiptCodec.fingerprintFor(
                        previousReceipt
                    )
                )
        )
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//Sport Pulse//Preflight Protocol 2.0//RU",
            "VERSION:2.0",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escapeText(calendarName(protocol))}",
            "X-WR-TIMEZONE:${selectedZone.zoneIdValue}",
            "X-SPORT-PULSE-EVENT-ID:${escapeText(protocol.eventId)}",
            "X-SPORT-PULSE-SEQUENCE:${receipt.sequence}",
            "X-SPORT-PULSE-SCHEDULE-SHA256:${
                receipt.scheduleFingerprint
            }",
            "X-SPORT-PULSE-PROTOCOL-SHA256:${
                receipt.protocolFingerprint
            }"
        )
        val currentFactorKeys = protocol.slots
            .map { slotFactorKey(it.factors) }
            .toSet()
        previousReceipt?.slots
            ?.filter { it.factorKey !in currentFactorKeys }
            ?.forEach { previousSlot ->
                lines += cancelledSlotEvent(
                    currentReceipt = receipt,
                    previousReceipt = previousReceipt,
                    previousSlot = previousSlot
                )
            }
        protocol.slots.forEach { slot ->
            lines += slotEvent(
                protocol = protocol,
                slot = slot,
                selectedZone = selectedZone,
                receipt = receipt
            )
        }
        lines += kickoffEvent(
            protocol = protocol,
            selectedZone = selectedZone,
            receipt = receipt
        )
        lines += "END:VCALENDAR"
        return lines
            .flatMap(::foldLine)
            .joinToString(separator = "\r\n", postfix = "\r\n")
    }

    fun encodeWithdrawal(
        receipt: PreflightExportReceipt
    ): String {
        require(receipt.withdrawn)
        require(receipt.sequence > 1)
        require(secureEquals(
            receipt.scheduleFingerprint,
            PreflightScheduleFingerprint.forReceipt(receipt)
        ))
        require(secureEquals(
            receipt.fingerprint,
            PreflightExportReceiptCodec.fingerprintFor(receipt)
        ))
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//Sport Pulse//Preflight Protocol 2.0//RU",
            "VERSION:2.0",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escapeText(
                "Спорт Пульс • ${receipt.eventLabel}"
            )}",
            "X-WR-TIMEZONE:${receipt.selectedZone.zoneIdValue}",
            "X-SPORT-PULSE-EVENT-ID:${escapeText(receipt.eventId)}",
            "X-SPORT-PULSE-SEQUENCE:${receipt.sequence}",
            "X-SPORT-PULSE-SCHEDULE-SHA256:${
                receipt.scheduleFingerprint
            }",
            "X-SPORT-PULSE-PROTOCOL-SHA256:${
                receipt.protocolFingerprint
            }",
            "X-SPORT-PULSE-WITHDRAWN:TRUE"
        )
        receipt.slots.forEach { slot ->
            lines += cancelledSlotEvent(
                currentReceipt = receipt,
                previousReceipt = receipt,
                previousSlot = slot
            )
        }
        lines += cancelledKickoffEvent(receipt)
        lines += "END:VCALENDAR"
        return lines
            .flatMap(::foldLine)
            .joinToString(separator = "\r\n", postfix = "\r\n")
    }

    fun fileName(receipt: PreflightExportReceipt): String {
        return "sport-pulse-preflight-${
            receipt.scheduleFingerprint.take(12)
        }.ics"
    }

    internal fun escapeText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")
    }

    internal fun foldLine(value: String): List<String> {
        require(!value.contains('\r') && !value.contains('\n'))
        if (value.toByteArray(StandardCharsets.UTF_8).size <=
            MAX_LINE_OCTETS
        ) {
            return listOf(value)
        }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var currentOctets = 0
        var firstLine = true
        val codePoints = value.codePoints().toArray()
        codePoints.forEach { codePoint ->
            val glyph = String(Character.toChars(codePoint))
            val glyphOctets = glyph
                .toByteArray(StandardCharsets.UTF_8)
                .size
            val limit = if (firstLine) {
                MAX_LINE_OCTETS
            } else {
                MAX_LINE_OCTETS - 1
            }
            if (current.isNotEmpty() &&
                currentOctets + glyphOctets > limit
            ) {
                result += if (firstLine) {
                    current.toString()
                } else {
                    " $current"
                }
                current.setLength(0)
                currentOctets = 0
                firstLine = false
            }
            current.append(glyph)
            currentOctets += glyphOctets
        }
        if (current.isNotEmpty()) {
            result += if (firstLine) {
                current.toString()
            } else {
                " $current"
            }
        }
        return result
    }

    private fun slotEvent(
        protocol: PreflightProtocol,
        slot: PreflightSlot,
        selectedZone: RegionalZone,
        receipt: PreflightExportReceipt
    ): List<String> {
        val factorTitles = slot.factors.joinToString(", ") {
            it.title
        }
        val summary = if (slot.immediate) {
            "Спорт Пульс: проверить сейчас — $factorTitles"
        } else {
            "Спорт Пульс: окно проверки — $factorTitles"
        }
        val description = buildString {
            append("Факторы: ")
            append(factorTitles)
            append("\nОкно: ")
            append(
                TimeBridgeEngine.formatInstant(
                    startAt = slot.scheduledAt,
                    selectedZone = selectedZone
                )
            )
            append("\nСтарт: ")
            append(
                TimeBridgeEngine.formatInstant(
                    startAt = protocol.start.startAt,
                    selectedZone = selectedZone
                )
            )
            append(
                "\nЭто окно свежести данных, а не прогноз исхода. " +
                    "Проверка не выполняется автоматически."
            )
            append("\nSHA-256 ")
            append(receipt.scheduleFingerprint)
        }
        val slotEnd = minOf(
            slot.scheduledAt + SLOT_DURATION_MILLIS,
            protocol.start.startAt
        ).coerceAtLeast(slot.scheduledAt + 1_000L)
        val uid = slotUid(protocol.eventId, slot.factors)
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:$uid")
            add("DTSTAMP:${utc(receipt.exportedAt)}")
            add("DTSTART:${utc(slot.scheduledAt)}")
            add("DTEND:${utc(slotEnd)}")
            add("SEQUENCE:${receipt.sequence}")
            add("SUMMARY:${escapeText(summary)}")
            add("DESCRIPTION:${escapeText(description)}")
            add("TRANSP:TRANSPARENT")
            add("CATEGORIES:SPORT PULSE,VERIFICATION")
            add(
                "X-SPORT-PULSE-FACTORS:" +
                    slot.factors.joinToString(",") { it.name }
            )
            add(
                "X-SPORT-PULSE-SCHEDULE-SHA256:" +
                    receipt.scheduleFingerprint
            )
            add(
                "X-SPORT-PULSE-PROTOCOL-SHA256:" +
                    receipt.protocolFingerprint
            )
            add("BEGIN:VALARM")
            add("TRIGGER:PT0S")
            add("ACTION:DISPLAY")
            add("DESCRIPTION:${escapeText(summary)}")
            add("END:VALARM")
            add("END:VEVENT")
        }
    }

    private fun kickoffEvent(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        receipt: PreflightExportReceipt
    ): List<String> {
        val source = when (protocol.start.source) {
            EventStartSource.EVENT_PACK -> "Event Pack"
            EventStartSource.DEMO_SCHEDULE -> "Демо-расписание"
        }
        val description = buildString {
            append("Источник времени: ")
            append(source)
            append("\nЛокальное время: ")
            append(
                TimeBridgeEngine.formatInstant(
                    startAt = protocol.start.startAt,
                    selectedZone = selectedZone
                )
            )
            append(
                "\nКонтрольный момент предстартового протокола. " +
                    "Не прогноз исхода и не напоминание о ставке."
            )
            append("\nSHA-256 ")
            append(receipt.scheduleFingerprint)
        }
        return listOf(
            "BEGIN:VEVENT",
            "UID:${kickoffUid(protocol.eventId)}",
            "DTSTAMP:${utc(receipt.exportedAt)}",
            "DTSTART:${utc(protocol.start.startAt)}",
            "DTEND:${utc(
                protocol.start.startAt + KICKOFF_MARKER_MILLIS
            )}",
            "SEQUENCE:${receipt.sequence}",
            "SUMMARY:${escapeText(
                "Старт: ${protocol.eventLabel}"
            )}",
            "DESCRIPTION:${escapeText(description)}",
            "TRANSP:TRANSPARENT",
            "CATEGORIES:SPORT PULSE,EVENT START",
            "X-SPORT-PULSE-SCHEDULE-SHA256:${
                receipt.scheduleFingerprint
            }",
            "X-SPORT-PULSE-PROTOCOL-SHA256:${
                receipt.protocolFingerprint
            }",
            "END:VEVENT"
        )
    }

    private fun cancelledSlotEvent(
        currentReceipt: PreflightExportReceipt,
        previousReceipt: PreflightExportReceipt,
        previousSlot: PreflightReceiptSlot
    ): List<String> {
        val factorTitles = previousSlot.factors.joinToString(", ") {
            it.title
        }
        val previousEnd = minOf(
            previousSlot.scheduledAt + SLOT_DURATION_MILLIS,
            previousReceipt.startAt
        ).coerceAtLeast(previousSlot.scheduledAt + 1_000L)
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:${slotUid(
                previousReceipt.eventId,
                previousSlot.factors
            )}")
            add("DTSTAMP:${utc(currentReceipt.exportedAt)}")
            add("DTSTART:${utc(previousSlot.scheduledAt)}")
            add("DTEND:${utc(previousEnd)}")
            add("SEQUENCE:${currentReceipt.sequence}")
            add("STATUS:CANCELLED")
            add("SUMMARY:${escapeText(
                "Спорт Пульс: слот отменен — $factorTitles"
            )}")
            add("DESCRIPTION:${escapeText(
                if (currentReceipt.withdrawn) {
                    "Календарный план отозван новой ревизией."
                } else {
                    "Слот заменен новой ревизией предстартового протокола."
                }
            )}")
            add("TRANSP:TRANSPARENT")
            add("CATEGORIES:SPORT PULSE,VERIFICATION")
            add("X-SPORT-PULSE-SCHEDULE-SHA256:${
                currentReceipt.scheduleFingerprint
            }")
            add("X-SPORT-PULSE-PROTOCOL-SHA256:${
                currentReceipt.protocolFingerprint
            }")
            if (currentReceipt.withdrawn) {
                add("X-SPORT-PULSE-WITHDRAWN:TRUE")
            }
            add("END:VEVENT")
        }
    }

    private fun cancelledKickoffEvent(
        receipt: PreflightExportReceipt
    ): List<String> {
        return listOf(
            "BEGIN:VEVENT",
            "UID:${kickoffUid(receipt.eventId)}",
            "DTSTAMP:${utc(receipt.exportedAt)}",
            "DTSTART:${utc(receipt.startAt)}",
            "DTEND:${utc(
                receipt.startAt + KICKOFF_MARKER_MILLIS
            )}",
            "SEQUENCE:${receipt.sequence}",
            "STATUS:CANCELLED",
            "SUMMARY:${escapeText(
                "Спорт Пульс: план отозван — ${receipt.eventLabel}"
            )}",
            "DESCRIPTION:${escapeText(
                "Событие исчезло из текущего каталога или потеряло точное время."
            )}",
            "TRANSP:TRANSPARENT",
            "CATEGORIES:SPORT PULSE,EVENT START",
            "X-SPORT-PULSE-SCHEDULE-SHA256:${
                receipt.scheduleFingerprint
            }",
            "X-SPORT-PULSE-PROTOCOL-SHA256:${
                receipt.protocolFingerprint
            }",
            "X-SPORT-PULSE-WITHDRAWN:TRUE",
            "END:VEVENT"
        )
    }

    private fun calendarName(protocol: PreflightProtocol): String {
        return "Спорт Пульс • ${protocol.eventLabel}"
    }

    internal fun slotUid(
        eventId: String,
        factors: List<SignalFactor>
    ): String {
        require(eventId.isNotBlank())
        require(factors.isNotEmpty())
        return uidFor(
            "$VERSION|event=$eventId|slot=${
                slotFactorKey(factors)
            }"
        )
    }

    internal fun kickoffUid(eventId: String): String {
        require(eventId.isNotBlank())
        return uidFor("$VERSION|event=$eventId|kickoff")
    }

    private fun uidFor(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        return "preflight-${digest.take(24)}@sportpulse.local"
    }

    private fun slotFactorKey(factors: List<SignalFactor>): String {
        return factors
            .sortedBy { it.ordinal }
            .joinToString(".") { it.name }
    }

    private fun utc(value: Long): String {
        return utcFormatter.format(Instant.ofEpochMilli(value))
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.lowercase().toByteArray(StandardCharsets.US_ASCII),
            right.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }
}

internal class PreflightCalendarExporter(
    private val context: Context
) {
    fun export(
        protocol: PreflightProtocol,
        selectedZone: RegionalZone,
        receipt: PreflightExportReceipt,
        previousReceipt: PreflightExportReceipt?
    ): File {
        val directory = File(
            context.cacheDir,
            PreflightCalendarProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create preflight calendar directory"
        }
        val file = File(
            directory,
            PreflightCalendarEncoder.fileName(receipt)
        )
        file.writeText(
            PreflightCalendarEncoder.encode(
                protocol = protocol,
                selectedZone = selectedZone,
                receipt = receipt,
                previousReceipt = previousReceipt
            ),
            StandardCharsets.UTF_8
        )
        prune(directory)
        return file
    }

    fun exportWithdrawal(
        receipt: PreflightExportReceipt
    ): File {
        require(receipt.withdrawn)
        val directory = File(
            context.cacheDir,
            PreflightCalendarProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create preflight calendar directory"
        }
        val file = File(
            directory,
            PreflightCalendarEncoder.fileName(receipt)
        )
        file.writeText(
            PreflightCalendarEncoder.encodeWithdrawal(receipt),
            StandardCharsets.UTF_8
        )
        prune(directory)
        return file
    }

    private fun prune(directory: File) {
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith("sport-pulse-preflight-") &&
                    it.extension == "ics"
            }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_CACHED_FILES)
            ?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_CACHED_FILES = 8
    }
}
