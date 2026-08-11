package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal enum class RegionalZone(
    val city: String,
    val zoneIdValue: String
) {
    MOSCOW("Москва", "Europe/Moscow"),
    KALININGRAD("Калининград", "Europe/Kaliningrad"),
    YEKATERINBURG("Екатеринбург", "Asia/Yekaterinburg"),
    NOVOSIBIRSK("Новосибирск", "Asia/Novosibirsk"),
    VLADIVOSTOK("Владивосток", "Asia/Vladivostok"),
    MINSK("Минск", "Europe/Minsk"),
    ASTANA("Астана", "Asia/Almaty"),
    TASHKENT("Ташкент", "Asia/Tashkent"),
    BISHKEK("Бишкек", "Asia/Bishkek"),
    BAKU("Баку", "Asia/Baku"),
    YEREVAN("Ереван", "Asia/Yerevan"),
    TBILISI("Тбилиси", "Asia/Tbilisi"),
    DUSHANBE("Душанбе", "Asia/Dushanbe");

    val zoneId: ZoneId
        get() = ZoneId.of(zoneIdValue)

    companion object {
        fun fromStored(value: String?): RegionalZone {
            return entries.firstOrNull { it.name == value }
                ?: MOSCOW
        }
    }
}

internal data class DemoSchedule(
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val minute: Int
) {
    init {
        require(hour in 0..23)
        require(minute in 0..59)
    }

    val localTime: LocalTime
        get() = LocalTime.of(hour, minute)
}

internal data class TimeBridgeResult(
    val selectedZone: RegionalZone,
    val evaluatedAtMinute: Long,
    val moscowDateTime: ZonedDateTime,
    val selectedDateTime: ZonedDateTime,
    val offsetDifferenceMinutes: Int,
    val dayShift: Int,
    val moscowOffsetLabel: String,
    val selectedOffsetLabel: String,
    val fingerprint: String
) {
    init {
        require(evaluatedAtMinute >= 0L)
        require(dayShift in -1..1)
        require(HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    val differenceLabel: String
        get() = TimeBridgeEngine.differenceLabel(
            offsetDifferenceMinutes
        )

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object TimeBridgeEngine {
    private const val VERSION = "sport-pulse-time-bridge-v1"
    private const val MINUTE_MILLIS = 60L * 1000L
    private val moscow = RegionalZone.MOSCOW.zoneId
    private val hex = "0123456789abcdef".toCharArray()
    private val weekdays = arrayOf(
        "пн",
        "вт",
        "ср",
        "чт",
        "пт",
        "сб",
        "вс"
    )
    private val months = arrayOf(
        "января",
        "февраля",
        "марта",
        "апреля",
        "мая",
        "июня",
        "июля",
        "августа",
        "сентября",
        "октября",
        "ноября",
        "декабря"
    )

    fun evaluate(
        selectedZone: RegionalZone,
        nowMillis: Long
    ): TimeBridgeResult {
        require(nowMillis >= 0L)
        val evaluatedAtMinute = nowMillis / MINUTE_MILLIS
        val instant = Instant.ofEpochMilli(
            evaluatedAtMinute * MINUTE_MILLIS
        )
        val moscowDateTime = instant.atZone(moscow)
        val selectedDateTime = instant.atZone(
            selectedZone.zoneId
        )
        val moscowOffsetSeconds =
            moscowDateTime.offset.totalSeconds
        val selectedOffsetSeconds =
            selectedDateTime.offset.totalSeconds
        val differenceMinutes =
            (selectedOffsetSeconds - moscowOffsetSeconds) / 60
        val dayShift = ChronoUnit.DAYS.between(
            moscowDateTime.toLocalDate(),
            selectedDateTime.toLocalDate()
        ).toInt()
        val fingerprint = sha256(
            listOf(
                VERSION,
                selectedZone.name,
                selectedZone.zoneIdValue,
                evaluatedAtMinute.toString(),
                moscowOffsetSeconds.toString(),
                selectedOffsetSeconds.toString(),
                dayShift.toString()
            ).joinToString("|")
        )
        return TimeBridgeResult(
            selectedZone = selectedZone,
            evaluatedAtMinute = evaluatedAtMinute,
            moscowDateTime = moscowDateTime,
            selectedDateTime = selectedDateTime,
            offsetDifferenceMinutes = differenceMinutes,
            dayShift = dayShift,
            moscowOffsetLabel = offsetLabel(
                moscowOffsetSeconds
            ),
            selectedOffsetLabel = offsetLabel(
                selectedOffsetSeconds
            ),
            fingerprint = fingerprint
        )
    }

    fun formatInstant(
        startAt: Long,
        selectedZone: RegionalZone
    ): String {
        require(startAt >= 0L)
        val local = Instant.ofEpochMilli(startAt)
            .atZone(selectedZone.zoneId)
        return "${local.dayOfMonth} ${months[local.monthValue - 1]}, " +
            "${weekday(local.dayOfWeek)} • ${clock(local)} • " +
            selectedZone.city
    }

    fun formatDemo(
        schedule: DemoSchedule,
        selectedZone: RegionalZone,
        referenceMillis: Long
    ): String {
        require(referenceMillis >= 0L)
        val referenceDate = Instant.ofEpochMilli(referenceMillis)
            .atZone(moscow)
            .toLocalDate()
        val monday = referenceDate.with(
            TemporalAdjusters.previousOrSame(
                DayOfWeek.MONDAY
            )
        )
        val moscowDate = monday.plusDays(
            schedule.dayOfWeek.ordinal.toLong()
        )
        val selected = ZonedDateTime.of(
            moscowDate,
            schedule.localTime,
            moscow
        ).withZoneSameInstant(selectedZone.zoneId)
        return "Демо · ${weekday(selected.dayOfWeek)}, " +
            "${clock(selected)} • ${selectedZone.city}"
    }

    fun formatEventTime(
        event: SportEvent,
        selectedZone: RegionalZone,
        referenceMillis: Long
    ): String {
        return when {
            event.startAt != null -> formatInstant(
                startAt = event.startAt,
                selectedZone = selectedZone
            )
            event.demoSchedule != null -> formatDemo(
                schedule = event.demoSchedule,
                selectedZone = selectedZone,
                referenceMillis = referenceMillis
            )
            else -> event.time
        }
    }

    internal fun differenceLabel(
        differenceMinutes: Int
    ): String {
        if (differenceMinutes == 0) {
            return "одно время с Москвой"
        }
        val absolute = kotlin.math.abs(differenceMinutes)
        val hours = absolute / 60
        val minutes = absolute % 60
        val duration = buildString {
            if (hours > 0) append("$hours ч")
            if (hours > 0 && minutes > 0) append(" ")
            if (minutes > 0) append("$minutes мин")
        }
        return if (differenceMinutes > 0) {
            "на $duration позже Москвы"
        } else {
            "на $duration раньше Москвы"
        }
    }

    private fun offsetLabel(totalSeconds: Int): String {
        val totalMinutes = totalSeconds / 60
        if (totalMinutes == 0) return "UTC"
        val sign = if (totalMinutes > 0) "+" else "-"
        val absolute = kotlin.math.abs(totalMinutes)
        val hours = absolute / 60
        val minutes = absolute % 60
        return if (minutes == 0) {
            "UTC$sign$hours"
        } else {
            "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
        }
    }

    private fun weekday(dayOfWeek: DayOfWeek): String {
        return weekdays[dayOfWeek.ordinal]
    }

    private fun clock(value: ZonedDateTime): String {
        return value.hour.toString().padStart(2, '0') +
            ":" + value.minute.toString().padStart(2, '0')
    }

    private fun sha256(payload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(
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
