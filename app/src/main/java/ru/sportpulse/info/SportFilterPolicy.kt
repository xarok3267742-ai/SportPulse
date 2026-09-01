package ru.sportpulse.info

internal data class SportFilterResult(
    val filters: List<String>,
    val selectedFilter: String,
    val savedOnly: Boolean,
    val showSavedFilter: Boolean
)

internal object SportFilterPolicy {
    private val primarySports = listOf(
        "Футбол",
        "Хоккей",
        "Баскетбол",
        "Киберспорт"
    )

    fun evaluate(
        catalogSports: List<String>,
        catalogEventIds: Set<String>,
        bookmarkedIds: Set<String>,
        selectedFilter: String,
        savedOnly: Boolean
    ): SportFilterResult {
        val sports = catalogSports.toSet()
        val filters = buildList {
            add("Все")
            addAll(primarySports.filter(sports::contains))
            if (sports.any { it !in primarySports }) {
                add("Другие")
            }
        }
        val showSavedFilter = bookmarkedIds.any {
            it in catalogEventIds
        }
        return SportFilterResult(
            filters = filters,
            selectedFilter = selectedFilter.takeIf {
                it in filters
            } ?: "Все",
            savedOnly = savedOnly && showSavedFilter,
            showSavedFilter = showSavedFilter
        )
    }
}
