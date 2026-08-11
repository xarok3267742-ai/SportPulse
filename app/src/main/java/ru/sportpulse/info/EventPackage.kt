package ru.sportpulse.info

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal data class PackagedSportEvent(
    val id: String,
    val sport: String,
    val tournament: String,
    val region: String,
    val match: String,
    val startAt: Long,
    val focus: String,
    val note: String,
    val tags: List<String>,
    val seedAssessment: SignalAssessment
) {
    fun toSportEvent(
        imageRes: Int,
        formattedTime: String,
        runtimeId: String = id
    ): SportEvent {
        return SportEvent(
            id = runtimeId,
            sport = sport,
            tournament = tournament,
            region = region,
            match = match,
            time = formattedTime,
            focus = focus,
            note = note,
            tags = tags,
            imageRes = imageRes,
            seedAssessment = seedAssessment,
            startAt = startAt,
            origin = SportEventOrigin.EVENT_PACKAGE
        )
    }
}

internal data class SportEventPackage(
    val schemaVersion: Int,
    val packageId: String,
    val sourceLabel: String,
    val generatedAt: Long,
    val validUntil: Long,
    val events: List<PackagedSportEvent>,
    val fingerprint: String,
    val authenticity: EventPackageAuthenticity =
        EventPackageAuthenticity.LOCAL
) {
    val shortFingerprint: String
        get() = fingerprint.take(12).uppercase()

    fun isExpired(now: Long): Boolean = validUntil <= now
}

internal class EventPackageValidationException(
    message: String
) : IllegalArgumentException(message)

internal object EventPackageCodec {
    const val MAX_JSON_BYTES = 256 * 1024
    const val MAX_EVENTS = 50
    const val SCHEMA_VERSION = 1

    private const val MINUTE_MILLIS = 60L * 1000L
    private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
    private const val DAY_MILLIS = 24L * HOUR_MILLIS
    private const val MAX_VALIDITY_MILLIS = 7L * DAY_MILLIS
    private const val MAX_FUTURE_SKEW_MILLIS = 15L * MINUTE_MILLIS
    private const val MAX_EVENT_HORIZON_MILLIS = 30L * DAY_MILLIS
    private val idPattern = Regex("[A-Za-z0-9_-]{3,64}")

    fun decode(
        json: String,
        now: Long,
        requireFresh: Boolean = true
    ): SportEventPackage {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) fail("Файл пакета пуст.")
        if (bytes.size > MAX_JSON_BYTES) {
            fail("Пакет превышает лимит 256 КБ.")
        }

        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            fail("Файл не является корректным JSON-пакетом.")
        }
        requireOnlyKeys(
            root,
            setOf(
                "schemaVersion",
                "packageId",
                "source",
                "generatedAt",
                "validUntil",
                "events"
            ),
            "Пакет"
        )
        val schemaVersion = int(root, "schemaVersion")
        if (schemaVersion != SCHEMA_VERSION) {
            fail("Версия схемы $schemaVersion не поддерживается.")
        }
        val packageId = id(root, "packageId")
        val source = string(root, "source", 2, 80)
        val generatedAt = long(root, "generatedAt")
        val validUntil = long(root, "validUntil")

        if (generatedAt > now + MAX_FUTURE_SKEW_MILLIS) {
            fail("Дата генерации пакета находится в будущем.")
        }
        if (validUntil <= generatedAt) {
            fail("Срок действия должен быть позже даты генерации.")
        }
        if (validUntil - generatedAt > MAX_VALIDITY_MILLIS) {
            fail("Срок действия пакета не может превышать 7 дней.")
        }
        if (requireFresh && validUntil <= now) {
            fail("Срок действия пакета уже истек.")
        }

        val rawEvents = array(root, "events")
        if (rawEvents.length() !in 1..MAX_EVENTS) {
            fail("Пакет должен содержать от 1 до $MAX_EVENTS событий.")
        }
        val seenIds = mutableSetOf<String>()
        val events = List(rawEvents.length()) { index ->
            val event = event(
                objectValue(rawEvents, index, "Событие ${index + 1}"),
                generatedAt = generatedAt
            )
            if (!seenIds.add(event.id)) {
                fail("ID события «${event.id}» повторяется.")
            }
            event
        }

        return SportEventPackage(
            schemaVersion = schemaVersion,
            packageId = packageId,
            sourceLabel = source,
            generatedAt = generatedAt,
            validUntil = validUntil,
            events = events,
            fingerprint = sha256(bytes)
        )
    }

    private fun event(
        value: JSONObject,
        generatedAt: Long
    ): PackagedSportEvent {
        requireOnlyKeys(
            value,
            setOf(
                "id",
                "sport",
                "tournament",
                "region",
                "match",
                "startAt",
                "focus",
                "note",
                "tags",
                "assessment"
            ),
            "Событие"
        )
        val id = id(value, "id")
        val startAt = long(value, "startAt")
        val earliest = generatedAt - DAY_MILLIS
        val latest = generatedAt + MAX_EVENT_HORIZON_MILLIS
        if (startAt !in earliest..latest) {
            fail("Время события «$id» выходит за допустимый горизонт.")
        }

        val rawTags = array(value, "tags")
        if (rawTags.length() > 5) {
            fail("У события «$id» может быть не более 5 тегов.")
        }
        val tags = List(rawTags.length()) { index ->
            stringValue(rawTags, index, "Тег ${index + 1}", 1, 24)
        }
        if (
            tags.map { it.lowercase(Locale.ROOT) }
                .distinct()
                .size != tags.size
        ) {
            fail("Теги события «$id» не должны повторяться.")
        }

        val assessment = objectValue(
            value,
            "assessment"
        )
        requireOnlyKeys(
            assessment,
            setOf("form", "lineup", "load", "context", "sources"),
            "Оценка события «$id»"
        )
        return PackagedSportEvent(
            id = id,
            sport = string(
                value,
                "sport",
                2,
                SportEventContentPolicy.MAX_SPORT_LENGTH
            ),
            tournament = string(
                value,
                "tournament",
                2,
                SportEventContentPolicy.MAX_TOURNAMENT_LENGTH
            ),
            region = string(
                value,
                "region",
                2,
                SportEventContentPolicy.MAX_REGION_LENGTH
            ),
            match = string(
                value,
                "match",
                3,
                SportEventContentPolicy.MAX_MATCH_LENGTH
            ),
            startAt = startAt,
            focus = string(
                value,
                "focus",
                3,
                SportEventContentPolicy.MAX_FOCUS_LENGTH
            ),
            note = string(
                value,
                "note",
                3,
                SportEventContentPolicy.MAX_NOTE_LENGTH
            ),
            tags = tags,
            seedAssessment = SignalAssessment(
                listOf(
                    score(assessment, "form"),
                    score(assessment, "lineup"),
                    score(assessment, "load"),
                    score(assessment, "context"),
                    score(assessment, "sources")
                )
            )
        )
    }

    private fun score(value: JSONObject, name: String): Int {
        val score = int(value, name)
        if (score !in 0..100) {
            fail("Оценка «$name» должна быть от 0 до 100.")
        }
        return score
    }

    private fun id(value: JSONObject, name: String): String {
        val id = string(value, name, 3, 64)
        if (!idPattern.matches(id)) {
            fail("Поле «$name» содержит недопустимый ID.")
        }
        return id
    }

    private fun string(
        value: JSONObject,
        name: String,
        minLength: Int,
        maxLength: Int
    ): String {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует строковое поле «$name».")
        }
        val raw = value.opt(name)
        if (raw !is String) {
            fail("Поле «$name» должно быть строкой.")
        }
        return checkedString(raw, name, minLength, maxLength)
    }

    private fun stringValue(
        value: JSONArray,
        index: Int,
        label: String,
        minLength: Int,
        maxLength: Int
    ): String {
        val raw = value.opt(index)
        if (raw !is String) {
            fail("$label должен быть строкой.")
        }
        return checkedString(raw, label, minLength, maxLength)
    }

    private fun checkedString(
        raw: String,
        label: String,
        minLength: Int,
        maxLength: Int
    ): String {
        val normalized = raw.trim()
        if (normalized.length !in minLength..maxLength) {
            fail(
                "Длина поля «$label» должна быть от $minLength до $maxLength символов."
            )
        }
        if (normalized.any { it.isISOControl() }) {
            fail("Поле «$label» содержит управляющие символы.")
        }
        return normalized
    }

    private fun int(value: JSONObject, name: String): Int {
        val number = number(value, name)
        val long = integralLong(number, name)
        if (long !in Int.MIN_VALUE..Int.MAX_VALUE) {
            fail("Поле «$name» выходит за диапазон целого числа.")
        }
        return long.toInt()
    }

    private fun long(value: JSONObject, name: String): Long {
        return integralLong(number(value, name), name)
    }

    private fun number(value: JSONObject, name: String): Number {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует числовое поле «$name».")
        }
        return value.opt(name) as? Number
            ?: fail("Поле «$name» должно быть числом.")
    }

    private fun integralLong(number: Number, name: String): Long {
        val double = number.toDouble()
        val long = number.toLong()
        if (!double.isFinite() || double != long.toDouble()) {
            fail("Поле «$name» должно быть целым числом.")
        }
        return long
    }

    private fun array(value: JSONObject, name: String): JSONArray {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует массив «$name».")
        }
        return value.opt(name) as? JSONArray
            ?: fail("Поле «$name» должно быть массивом.")
    }

    private fun objectValue(
        value: JSONObject,
        name: String
    ): JSONObject {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует объект «$name».")
        }
        return value.opt(name) as? JSONObject
            ?: fail("Поле «$name» должно быть объектом.")
    }

    private fun objectValue(
        value: JSONArray,
        index: Int,
        label: String
    ): JSONObject {
        return value.opt(index) as? JSONObject
            ?: fail("$label должно быть объектом.")
    }

    private fun requireOnlyKeys(
        value: JSONObject,
        allowed: Set<String>,
        label: String
    ) {
        val unsupported = value.keys().asSequence()
            .firstOrNull { it !in allowed }
        if (unsupported != null) {
            fail("$label содержит неподдерживаемое поле «$unsupported».")
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun fail(message: String): Nothing {
        throw EventPackageValidationException(message)
    }
}
