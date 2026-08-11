package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFootballParserTest {
    @Test
    fun decodeKeepsOnlyRussiaAndCisFixturesAndSortsThem() {
        val feed = ApiFootballParser.decode(
            json = sampleResponse(),
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = 87
        )

        assertEquals(listOf(102L, 101L), feed.fixtures.map { it.fixtureId })
        assertEquals("2026-08-05", feed.fromDate)
        assertEquals("2026-08-12", feed.toDate)
        assertEquals(87, feed.remainingRequests)
        assertEquals("Kazakhstan", feed.fixtures.first().country)
    }

    @Test
    fun fixtureBecomesUnconfirmedApiEventWithoutInventedAnalytics() {
        val fixture = ApiFootballParser.decode(
            json = sampleResponse(),
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = null
        ).fixtures.first()

        val event = fixture.toSportEvent(
            imageRes = 42,
            fetchedAt = 1_700_000_000_000L
        )

        assertEquals(SportEventOrigin.API_SPORTS, event.origin)
        assertEquals(EvidenceLevel.UNCONFIRMED, event.defaultEvidenceLevel)
        assertTrue(event.seedAssessment.values.all { it == 0 })
        assertEquals("api_football_102", event.id)
    }

    @Test
    fun decodeIncludesTurkmenistanInCisCatalog() {
        val feed = ApiFootballParser.decode(
            json = """{
                "parameters":{"date":"2026-08-07"},
                "errors":[],
                "response":[{
                  "fixture":{"id":104,"timestamp":1786104000,"status":{"short":"NS"}},
                  "league":{"name":"Yokary Liga","country":"Turkmenistan"},
                  "teams":{"home":{"name":"Arkadag"},"away":{"name":"Ahal"}},
                  "goals":{"home":null,"away":null}
                }]
            }""",
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = null
        )

        assertEquals(1, feed.fixtures.size)
        assertEquals("Туркменистан", ApiFootballText.country("Turkmenistan"))
    }

    @Test
    fun uncommonProviderStatusesAreLocalized() {
        assertEquals(
            "Матч прекращён",
            ApiFootballText.status("ABD", "Abandoned")
        )
        assertEquals(
            "Результат присуждён",
            ApiFootballText.status("AWD", "Awarded")
        )
        assertEquals(
            "Технический исход",
            ApiFootballText.status("WO", "Walkover")
        )
        assertEquals(
            "Время уточняется",
            ApiFootballText.status("TBD", "Time to be defined")
        )
    }

    @Test
    fun providerErrorsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiFootballParser.decode(
                json = """{
                    "errors":{"token":"Invalid key"},
                    "response":[]
                }""",
                fetchedAt = 1L,
                remainingRequests = null
            )
        }
    }

    @Test
    fun invalidTimestampCannotOverflowIntoCatalog() {
        val feed = ApiFootballParser.decode(
            json = """{
                "parameters":{"date":"2026-08-06"},
                "errors":[],
                "response":[{
                  "fixture":{"id":999,"timestamp":${Long.MAX_VALUE},"status":{"short":"NS"}},
                  "league":{"name":"Test League","country":"Russia"},
                  "teams":{"home":{"name":"Home"},"away":{"name":"Away"}},
                  "goals":{"home":null,"away":null}
                }]
            }""",
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = null
        )

        assertTrue(feed.fixtures.isEmpty())
    }

    @Test
    fun overlongProviderTextIsSkippedWithoutBreakingFeed() {
        val feed = ApiFootballParser.decode(
            json = """{
                "parameters":{"date":"2026-08-06"},
                "errors":[],
                "response":[
                  {
                    "fixture":{"id":998,"timestamp":1785952800,"status":{"short":"NS"}},
                    "league":{"name":"${"L".repeat(81)}","country":"Russia"},
                    "teams":{"home":{"name":"Home"},"away":{"name":"Away"}},
                    "goals":{"home":null,"away":null}
                  },
                  {
                    "fixture":{"id":999,"timestamp":1785956400,"status":{"short":"NS"}},
                    "league":{"name":"Premier League","country":"Russia"},
                    "teams":{"home":{"name":"Zenit"},"away":{"name":"Krasnodar"}},
                    "goals":{"home":null,"away":null}
                  }
                ]
            }""",
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = null
        )

        assertEquals(listOf(999L), feed.fixtures.map { it.fixtureId })
    }

    @Test
    fun normalizedCacheRoundTrips() {
        val original = ApiFootballParser.decode(
            json = sampleResponse(),
            fetchedAt = 1_700_000_000_000L,
            remainingRequests = 87
        )

        val restored = ApiFootballFeedCodec.decode(
            ApiFootballFeedCodec.encode(original)
        )

        assertEquals(original, restored)
    }

    private fun sampleResponse(): String {
        return """{
          "parameters":{"from":"2026-08-05","to":"2026-08-12"},
          "errors":[],
          "results":3,
          "response":[
            {
              "fixture":{"id":101,"timestamp":1785952800,"status":{"short":"NS","long":"Not Started"}},
              "league":{"name":"Premier League","country":"Russia"},
              "teams":{"home":{"name":"Zenit"},"away":{"name":"Krasnodar"}},
              "goals":{"home":null,"away":null}
            },
            {
              "fixture":{"id":102,"timestamp":1785945600,"status":{"short":"NS","long":"Not Started"}},
              "league":{"name":"Premier League","country":"Kazakhstan"},
              "teams":{"home":{"name":"Astana"},"away":{"name":"Kairat"}},
              "goals":{"home":null,"away":null}
            },
            {
              "fixture":{"id":103,"timestamp":1785938400,"status":{"short":"FT","long":"Match Finished"}},
              "league":{"name":"Premier League","country":"England"},
              "teams":{"home":{"name":"Arsenal"},"away":{"name":"Chelsea"}},
              "goals":{"home":2,"away":1}
            }
          ]
        }"""
    }
}
