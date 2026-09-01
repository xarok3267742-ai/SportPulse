package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportFilterPolicyTest {
    @Test
    fun onlyCatalogSportsAreVisibleInStableOrder() {
        val result = evaluate(
            sports = listOf("Киберспорт", "Футбол", "Футбол")
        )

        assertEquals(
            listOf("Все", "Футбол", "Киберспорт"),
            result.filters
        )
    }

    @Test
    fun unknownCatalogSportUsesSingleOtherFilter() {
        val result = evaluate(
            sports = listOf("Теннис", "Гандбол", "Футбол")
        )

        assertEquals(
            listOf("Все", "Футбол", "Другие"),
            result.filters
        )
    }

    @Test
    fun unavailableSelectionFallsBackToAll() {
        val result = evaluate(
            sports = listOf("Футбол"),
            selected = "Хоккей"
        )

        assertEquals("Все", result.selectedFilter)
    }

    @Test
    fun savedFilterRequiresBookmarkFromCurrentCatalog() {
        val hidden = evaluate(
            sports = listOf("Футбол"),
            eventIds = setOf("current"),
            bookmarks = setOf("old"),
            savedOnly = true
        )
        val visible = evaluate(
            sports = listOf("Футбол"),
            eventIds = setOf("current"),
            bookmarks = setOf("current", "old"),
            savedOnly = true
        )

        assertFalse(hidden.showSavedFilter)
        assertFalse(hidden.savedOnly)
        assertTrue(visible.showSavedFilter)
        assertTrue(visible.savedOnly)
    }

    private fun evaluate(
        sports: List<String>,
        eventIds: Set<String> = setOf("current"),
        bookmarks: Set<String> = emptySet(),
        selected: String = "Все",
        savedOnly: Boolean = false
    ): SportFilterResult {
        return SportFilterPolicy.evaluate(
            catalogSports = sports,
            catalogEventIds = eventIds,
            bookmarkedIds = bookmarks,
            selectedFilter = selected,
            savedOnly = savedOnly
        )
    }
}
