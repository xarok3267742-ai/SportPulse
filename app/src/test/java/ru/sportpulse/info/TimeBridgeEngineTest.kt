package ru.sportpulse.info

import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBridgeEngineTest {
    @Test
    fun everyPublishedZoneIdIsAvailable() {
        RegionalZone.entries.forEach { zone ->
            assertEquals(
                zone.zoneIdValue,
                zone.zoneId.id
            )
        }
    }

    @Test
    fun moscowIsIdentityBridge() {
        val result = TimeBridgeEngine.evaluate(
            RegionalZone.MOSCOW,
            instant("2026-08-03T16:30:00Z")
        )

        assertEquals(0, result.offsetDifferenceMinutes)
        assertEquals(0, result.dayShift)
        assertEquals("UTC+3", result.selectedOffsetLabel)
        assertEquals(
            "одно время с Москвой",
            result.differenceLabel
        )
    }

    @Test
    fun astanaUsesRuntimeIanaOffset() {
        val result = TimeBridgeEngine.evaluate(
            RegionalZone.ASTANA,
            instant("2026-08-03T16:30:00Z")
        )

        assertEquals(120, result.offsetDifferenceMinutes)
        assertEquals(21, result.selectedDateTime.hour)
        assertEquals("UTC+5", result.selectedOffsetLabel)
        assertEquals(
            "на 2 ч позже Москвы",
            result.differenceLabel
        )
    }

    @Test
    fun vladivostokCrossesIntoNextDay() {
        val result = TimeBridgeEngine.evaluate(
            RegionalZone.VLADIVOSTOK,
            instant("2026-08-03T20:30:00Z")
        )

        assertEquals(23, result.moscowDateTime.hour)
        assertEquals(6, result.selectedDateTime.hour)
        assertEquals(1, result.dayShift)
        assertEquals(420, result.offsetDifferenceMinutes)
    }

    @Test
    fun kaliningradCanBeEarlierThanMoscow() {
        val result = TimeBridgeEngine.evaluate(
            RegionalZone.KALININGRAD,
            instant("2026-08-03T12:00:00Z")
        )

        assertEquals(-60, result.offsetDifferenceMinutes)
        assertEquals(
            "на 1 ч раньше Москвы",
            result.differenceLabel
        )
    }

    @Test
    fun importedInstantGetsLocalDateTimeAndCity() {
        val formatted = TimeBridgeEngine.formatInstant(
            startAt = instant("2026-08-03T16:30:00Z"),
            selectedZone = RegionalZone.ASTANA
        )

        assertEquals(
            "3 августа, пн • 21:30 • Астана",
            formatted
        )
    }

    @Test
    fun demoScheduleCanCrossWeekday() {
        val formatted = TimeBridgeEngine.formatDemo(
            schedule = DemoSchedule(
                DayOfWeek.WEDNESDAY,
                19,
                30
            ),
            selectedZone = RegionalZone.VLADIVOSTOK,
            referenceMillis =
                instant("2026-08-03T10:00:00Z")
        )

        assertEquals(
            "Демо · чт, 02:30 • Владивосток",
            formatted
        )
    }

    @Test
    fun eventWithoutExactScheduleKeepsHonestFallback() {
        val event = event(
            time = "Демо · по расписанию серии"
        )

        assertEquals(
            event.time,
            TimeBridgeEngine.formatEventTime(
                event = event,
                selectedZone = RegionalZone.BAKU,
                referenceMillis = 1_000L
            )
        )
    }

    @Test
    fun exactInstantTakesPriorityOverDemoSchedule() {
        val event = event(
            time = "fallback",
            startAt = instant("2026-08-03T16:30:00Z"),
            demoSchedule = DemoSchedule(
                DayOfWeek.FRIDAY,
                1,
                0
            )
        )

        assertEquals(
            "3 августа, пн • 20:30 • Баку",
            TimeBridgeEngine.formatEventTime(
                event = event,
                selectedZone = RegionalZone.BAKU,
                referenceMillis = 1_000L
            )
        )
    }

    @Test
    fun corruptedStoredZoneFallsBackToMoscow() {
        assertEquals(
            RegionalZone.MOSCOW,
            RegionalZone.fromStored("UNKNOWN")
        )
        assertEquals(
            RegionalZone.ASTANA,
            RegionalZone.fromStored("ASTANA")
        )
    }

    @Test
    fun fingerprintChangesWithMinuteOrZone() {
        val base = TimeBridgeEngine.evaluate(
            RegionalZone.MINSK,
            instant("2026-08-03T12:00:00Z")
        )
        val later = TimeBridgeEngine.evaluate(
            RegionalZone.MINSK,
            instant("2026-08-03T12:01:00Z")
        )
        val anotherZone = TimeBridgeEngine.evaluate(
            RegionalZone.BAKU,
            instant("2026-08-03T12:00:00Z")
        )

        assertNotEquals(base.fingerprint, later.fingerprint)
        assertNotEquals(base.fingerprint, anotherZone.fingerprint)
        assertTrue(Regex("[0-9a-f]{64}").matches(base.fingerprint))
    }

    @Test(expected = IllegalArgumentException::class)
    fun demoHourMustBeValid() {
        DemoSchedule(DayOfWeek.MONDAY, 24, 0)
    }

    private fun event(
        time: String,
        startAt: Long? = null,
        demoSchedule: DemoSchedule? = null
    ): SportEvent {
        return SportEvent(
            id = "time_test",
            sport = "Футбол",
            tournament = "Тест",
            region = "СНГ",
            match = "Событие",
            time = time,
            focus = "Факты",
            note = "Тестовое событие",
            tags = emptyList(),
            imageRes = 0,
            seedAssessment = SignalAssessment(List(5) { 50 }),
            startAt = startAt,
            demoSchedule = demoSchedule
        )
    }

    private fun instant(value: String): Long {
        return Instant.parse(value).toEpochMilli()
    }
}
