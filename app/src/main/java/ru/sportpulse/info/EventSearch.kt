package ru.sportpulse.info

import java.util.Locale

internal enum class EventSearchField(
    val title: String
) {
    MATCH("КОМАНДА"),
    TOURNAMENT("ТУРНИР"),
    REGION("РЕГИОН"),
    SPORT("СПОРТ"),
    STATUS("СТАТУС"),
    TAG("ТЕГ")
}

internal data class EventSearchExplanation(
    val fields: List<EventSearchField>
) {
    init {
        require(fields.isNotEmpty())
    }

    val label: String
        get() = "СОВПАДЕНИЕ • " + fields.joinToString(" + ") {
            it.title
        }
}

internal object EventSearchPolicy {
    const val MAX_QUERY_LENGTH = 64

    fun filter(
        events: List<SportEvent>,
        query: String
    ): List<SportEvent> {
        val queryTokens = tokens(query)
        return if (queryTokens.isEmpty()) {
            events
        } else {
            events.filter { explanation(it, queryTokens) != null }
        }
    }

    fun matches(
        event: SportEvent,
        query: String
    ): Boolean {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return true
        return explanation(event, queryTokens) != null
    }

    fun explain(
        event: SportEvent,
        query: String
    ): EventSearchExplanation? {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return null
        return explanation(event, queryTokens)
    }

    internal fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    internal fun transliterate(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                append(
                    when (character) {
                        'а' -> "a"
                        'б' -> "b"
                        'в' -> "v"
                        'г' -> "g"
                        'д' -> "d"
                        'е', 'ё', 'э' -> "e"
                        'ж' -> "zh"
                        'з' -> "z"
                        'и' -> "i"
                        'й' -> "i"
                        'к' -> "k"
                        'л' -> "l"
                        'м' -> "m"
                        'н' -> "n"
                        'о' -> "o"
                        'п' -> "p"
                        'р' -> "r"
                        'с' -> "s"
                        'т' -> "t"
                        'у' -> "u"
                        'ф' -> "f"
                        'х' -> "kh"
                        'ц' -> "ts"
                        'ч' -> "ch"
                        'ш' -> "sh"
                        'щ' -> "shch"
                        'ы' -> "y"
                        'ю' -> "yu"
                        'я' -> "ya"
                        'ъ', 'ь' -> ""
                        else -> character.toString()
                    }
                )
            }
        }
    }

    private fun tokens(query: String): List<String> {
        return normalize(query.take(MAX_QUERY_LENGTH))
            .split(' ')
            .filter(String::isNotBlank)
    }

    private fun explanation(
        event: SportEvent,
        queryTokens: List<String>
    ): EventSearchExplanation? {
        val fields = listOf(
            EventSearchField.MATCH to event.match,
            EventSearchField.TOURNAMENT to event.tournament,
            EventSearchField.REGION to event.region,
            EventSearchField.SPORT to event.sport,
            EventSearchField.STATUS to event.providerStatus.orEmpty(),
            EventSearchField.TAG to event.tags.joinToString(" ")
        ).map { (field, value) ->
            val normalized = normalize(value)
            SearchFieldValue(
                field = field,
                normalized = normalized,
                latin = transliterate(normalized)
            )
        }
        val matched = mutableListOf<EventSearchField>()
        queryTokens.forEach { token ->
            val latinToken = transliterate(token)
            val field = fields.firstOrNull { candidate ->
                token in candidate.normalized ||
                    latinToken in candidate.latin
            }?.field ?: return null
            if (field !in matched) matched += field
        }
        return EventSearchExplanation(matched)
    }

    private data class SearchFieldValue(
        val field: EventSearchField,
        val normalized: String,
        val latin: String
    )

    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
