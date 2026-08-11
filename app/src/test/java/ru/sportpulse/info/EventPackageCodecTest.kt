package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPackageCodecTest {
    private val now = 1_800_000_000_000L
    private val generatedAt = now - 60L * 60L * 1000L
    private val validUntil = now + 24L * 60L * 60L * 1000L

    @Test
    fun validPackageParsesEveryField() {
        val result = EventPackageCodec.decode(validJson(), now)

        assertEquals(1, result.schemaVersion)
        assertEquals("rpl_week_30", result.packageId)
        assertEquals("Лицензированный поставщик", result.sourceLabel)
        assertEquals(1, result.events.size)
        assertEquals("live_zenit_krasnodar", result.events.single().id)
        assertEquals(listOf(62, 38, 58, 70, 46), result.events.single().seedAssessment.values)
        assertEquals(64, result.fingerprint.length)
        assertEquals(12, result.shortFingerprint.length)
        assertFalse(result.isExpired(now))
    }

    @Test
    fun fingerprintChangesWithFileContent() {
        val original = EventPackageCodec.decode(validJson(), now)
        val changed = EventPackageCodec.decode(
            validJson().replace(
                "Зенит - Краснодар",
                "Зенит - Краснодар 2"
            ),
            now
        )

        assertNotEquals(original.fingerprint, changed.fingerprint)
    }

    @Test
    fun expiredPackageIsRejectedForImport() {
        val expired = validJson(
            generatedAt = now - 2L * DAY,
            validUntil = now - DAY
        )

        val error = failure {
            EventPackageCodec.decode(expired, now)
        }

        assertTrue(error.message.orEmpty().contains("истек"))
    }

    @Test
    fun expiredPackageCanBeReadForFallbackStatus() {
        val expired = validJson(
            generatedAt = now - 2L * DAY,
            validUntil = now - DAY
        )

        val result = EventPackageCodec.decode(
            expired,
            now,
            requireFresh = false
        )

        assertTrue(result.isExpired(now))
    }

    @Test
    fun futureGenerationTimeIsRejected() {
        val future = validJson(
            generatedAt = now + 16L * 60L * 1000L,
            validUntil = now + DAY
        )

        val error = failure {
            EventPackageCodec.decode(future, now)
        }

        assertTrue(error.message.orEmpty().contains("будущем"))
    }

    @Test
    fun validityLongerThanSevenDaysIsRejected() {
        val tooLong = validJson(
            generatedAt = generatedAt,
            validUntil = generatedAt + 8L * DAY
        )

        val error = failure {
            EventPackageCodec.decode(tooLong, now)
        }

        assertTrue(error.message.orEmpty().contains("7 дней"))
    }

    @Test
    fun duplicateEventIdsAreRejected() {
        val event = eventJson()
        val duplicate = validJson(events = "$event,$event")

        val error = failure {
            EventPackageCodec.decode(duplicate, now)
        }

        assertTrue(error.message.orEmpty().contains("повторяется"))
    }

    @Test
    fun scoreOutsideRangeIsRejected() {
        val invalid = validJson(
            events = eventJson().replace(
                "\"form\": 62",
                "\"form\": 101"
            )
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("от 0 до 100"))
    }

    @Test
    fun eventOutsideThirtyDayHorizonIsRejected() {
        val invalid = validJson(
            events = eventJson(
                startAt = generatedAt + 31L * DAY
            )
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("горизонт"))
    }

    @Test
    fun wrongSchemaVersionIsRejected() {
        val invalid = validJson().replace(
            "\"schemaVersion\": 1",
            "\"schemaVersion\": 2"
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("не поддерживается"))
    }

    @Test
    fun unknownPackageFieldIsRejected() {
        val invalid = validJson().replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"unexpected\": true,"
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("неподдерживаемое поле"))
    }

    @Test
    fun unknownEventFieldIsRejected() {
        val invalid = validJson(
            events = eventJson().replace(
                "\"sport\": \"Футбол\",",
                "\"sport\": \"Футбол\", \"odds\": 2.1,"
            )
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("неподдерживаемое поле"))
    }

    @Test
    fun duplicateTagsAreRejectedIgnoringCase() {
        val invalid = validJson(
            events = eventJson().replace(
                "\"топ-матч\", \"РПЛ\"",
                "\"РПЛ\", \"рпл\""
            )
        )

        val error = failure {
            EventPackageCodec.decode(invalid, now)
        }

        assertTrue(error.message.orEmpty().contains("не должны повторяться"))
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        val oversized = " ".repeat(EventPackageCodec.MAX_JSON_BYTES + 1)

        val error = failure {
            EventPackageCodec.decode(oversized, now)
        }

        assertTrue(error.message.orEmpty().contains("256 КБ"))
    }

    @Test
    fun packagedEventMapsToAppEventWithoutLosingAssessment() {
        val packaged = EventPackageCodec.decode(
            validJson(),
            now
        ).events.single()

        val event = packaged.toSportEvent(
            imageRes = 42,
            formattedTime = "30 июля, 19:30 МСК",
            runtimeId = "pack_supplier_${packaged.id}"
        )

        assertEquals("pack_supplier_${packaged.id}", event.id)
        assertEquals(packaged.match, event.match)
        assertEquals(42, event.imageRes)
        assertEquals("30 июля, 19:30 МСК", event.time)
        assertEquals(packaged.seedAssessment, event.seedAssessment)
    }

    private fun validJson(
        generatedAt: Long = this.generatedAt,
        validUntil: Long = this.validUntil,
        events: String = eventJson()
    ): String {
        return """
            {
              "schemaVersion": 1,
              "packageId": "rpl_week_30",
              "source": "Лицензированный поставщик",
              "generatedAt": $generatedAt,
              "validUntil": $validUntil,
              "events": [$events]
            }
        """.trimIndent()
    }

    private fun eventJson(
        startAt: Long = now + 2L * 60L * 60L * 1000L
    ): String {
        return """
            {
              "id": "live_zenit_krasnodar",
              "sport": "Футбол",
              "tournament": "Мир РПЛ",
              "region": "Россия",
              "match": "Зенит - Краснодар",
              "startAt": $startAt,
              "focus": "Темп, угловые, ротация",
              "note": "Сверьте составы и официальные сообщения.",
              "tags": ["топ-матч", "РПЛ"],
              "assessment": {
                "form": 62,
                "lineup": 38,
                "load": 58,
                "context": 70,
                "sources": 46
              }
            }
        """.trimIndent()
    }

    private fun failure(
        block: () -> Unit
    ): EventPackageValidationException {
        return try {
            block()
            throw AssertionError("Expected EventPackageValidationException")
        } catch (error: EventPackageValidationException) {
            error
        }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
