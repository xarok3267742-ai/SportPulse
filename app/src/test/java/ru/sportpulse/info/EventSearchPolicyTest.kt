package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSearchPolicyTest {
    private val cyrillic = DemoCatalog.events.first {
        it.match.contains("Зенит")
    }
    private val latin = cyrillic.copy(
        id = "latin_zenit",
        match = "Zenit - Krasnodar",
        tournament = "Premier League",
        region = "Russia"
    )

    @Test
    fun blankQueryKeepsCatalogOrder() {
        val events = listOf(latin, cyrillic)

        assertEquals(events, EventSearchPolicy.filter(events, "   "))
    }

    @Test
    fun latinQueryFindsCyrillicTeam() {
        assertTrue(EventSearchPolicy.matches(cyrillic, "zenit"))
        assertTrue(EventSearchPolicy.matches(cyrillic, "krasnodar"))
    }

    @Test
    fun cyrillicQueryFindsLatinApiTeam() {
        assertTrue(EventSearchPolicy.matches(latin, "Зенит"))
        assertTrue(EventSearchPolicy.matches(latin, "Краснодар"))
    }

    @Test
    fun termsMayMatchDifferentMetadataFields() {
        assertTrue(EventSearchPolicy.matches(cyrillic, "зенит россия рпл"))
        assertFalse(EventSearchPolicy.matches(cyrillic, "зенит казахстан"))
    }

    @Test
    fun explanationNamesFieldsInQueryOrder() {
        val explanation = requireNotNull(
            EventSearchPolicy.explain(
                cyrillic,
                "зенит россия рпл"
            )
        )

        assertEquals(
            listOf(
                EventSearchField.MATCH,
                EventSearchField.REGION,
                EventSearchField.TOURNAMENT
            ),
            explanation.fields
        )
        assertEquals(
            "СОВПАДЕНИЕ • КОМАНДА + РЕГИОН + ТУРНИР",
            explanation.label
        )
    }

    @Test
    fun explanationIncludesProviderStatusAndTag() {
        val event = latin.copy(
            providerStatus = "Live",
            tags = listOf("Special coverage")
        )

        assertEquals(
            listOf(EventSearchField.STATUS, EventSearchField.TAG),
            EventSearchPolicy.explain(
                event,
                "live special"
            )?.fields
        )
    }

    @Test
    fun longMatchDoesNotHideTrailingMetadata() {
        val longEvent = latin.copy(
            match = "Очень длинное название команды ".repeat(4)
                .take(SportEventContentPolicy.MAX_MATCH_LENGTH),
            tournament = "Yokary Liga",
            region = "Turkmenistan"
        )

        assertTrue(EventSearchPolicy.matches(longEvent, "turkmenistan yokary"))
    }

    @Test
    fun punctuationAndCaseDoNotChangeResult() {
        assertTrue(
            EventSearchPolicy.matches(
                cyrillic,
                "  ЗЕНИТ / КРАСНОДАР  "
            )
        )
    }

    @Test
    fun filterPreservesOrderAndRemovesNonMatches() {
        val other = DemoCatalog.events.first { it.id != cyrillic.id }

        assertEquals(
            listOf(cyrillic, latin),
            EventSearchPolicy.filter(
                listOf(other, cyrillic, latin),
                "zenit"
            )
        )
    }
}
