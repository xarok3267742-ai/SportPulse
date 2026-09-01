package ru.sportpulse.info

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsScheduleSecurityTest {
    @Test
    fun onlyDedicatedHttpsProxyCanBeConfigured() {
        assertTrue(
            SportsScheduleProxyPolicy.isConfigured(
                "https://schedule.example.ru/fixtures"
            )
        )
        assertFalse(SportsScheduleProxyPolicy.isConfigured(""))
        assertFalse(
            SportsScheduleProxyPolicy.isConfigured(
                "http://schedule.example.ru/fixtures"
            )
        )
        assertFalse(
            SportsScheduleProxyPolicy.isConfigured(
                "https://v3.football.api-sports.io/fixtures"
            )
        )
        assertFalse(
            SportsScheduleProxyPolicy.isConfigured(
                "https://football-data.p.rapidapi.com/fixtures"
            )
        )
        assertFalse(
            SportsScheduleProxyPolicy.isConfigured(
                "https://user:secret@schedule.example.ru/fixtures"
            )
        )
        assertFalse(
            SportsScheduleProxyPolicy.isConfigured(
                "https://schedule.example.ru/fixtures#secret"
            )
        )
    }

    @Test
    fun appRequestContainsNoProviderCredentials() {
        val url = SportsScheduleProxyRequest.url(
            endpoint = "https://schedule.example.ru/fixtures?client=android",
            date = LocalDate.of(2026, 8, 7)
        )

        assertEquals(
            "https://schedule.example.ru/fixtures?client=android&date=2026-08-07&timezone=Europe%2FMoscow",
            url.toString()
        )
        assertEquals(
            mapOf("Accept" to "application/json"),
            SportsScheduleProxyRequest.headers
        )
        assertFalse(
            SportsScheduleProxyRequest.headers.keys.any { name ->
                name.contains("key", ignoreCase = true) ||
                    name.contains("token", ignoreCase = true) ||
                    name.contains("authorization", ignoreCase = true)
            }
        )
    }

    @Test
    fun generatedBuildConfigHasNoCredentialFields() {
        val fieldNames = BuildConfig::class.java.declaredFields
            .map { it.name.lowercase() }

        assertTrue("sports_schedule_proxy_url" in fieldNames)
        assertFalse(
            fieldNames.any { name ->
                "key" in name || "token" in name || "secret" in name
            }
        )
    }
}
