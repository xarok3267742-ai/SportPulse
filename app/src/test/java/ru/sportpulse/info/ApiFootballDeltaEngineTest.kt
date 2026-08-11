package ru.sportpulse.info

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFootballDeltaEngineTest {
    @Test
    fun oneFixtureCombinesTimeStatusAndScoreChanges() {
        val before = fixture(
            id = 10L,
            start = "2026-08-07T14:00:00Z"
        )
        val after = before.copy(
            startAt = instant("2026-08-07T15:30:00Z"),
            statusCode = "1H",
            statusLabel = "First Half",
            homeScore = 1,
            awayScore = 0
        )

        val delta = ApiFootballDeltaEngine.compare(
            previous = feed(100L, listOf(before)),
            current = feed(200L, listOf(after))
        )

        assertEquals(1, delta.changes.size)
        assertEquals(
            setOf(
                ApiFootballChangeKind.START_TIME,
                ApiFootballChangeKind.STATUS,
                ApiFootballChangeKind.SCORE
            ),
            delta.changes.single().kinds
        )
        assertEquals(
            ApiFootballChangeKind.SCORE,
            delta.changes.single().primaryKind
        )
        assertTrue(delta.shortFingerprint.matches(Regex("[0-9A-F]{12}")))
    }

    @Test
    fun rollingWindowEdgesDoNotCreateFalseActivity() {
        val shared = fixture(
            id = 20L,
            start = "2026-08-08T14:00:00Z"
        )
        val leavingWindow = fixture(
            id = 21L,
            start = "2026-08-06T14:00:00Z"
        )
        val enteringWindow = fixture(
            id = 22L,
            start = "2026-08-09T14:00:00Z"
        )

        val delta = ApiFootballDeltaEngine.compare(
            previous = feed(
                fetchedAt = 100L,
                fixtures = listOf(leavingWindow, shared),
                from = "2026-08-06",
                to = "2026-08-08"
            ),
            current = feed(
                fetchedAt = 200L,
                fixtures = listOf(shared, enteringWindow),
                from = "2026-08-07",
                to = "2026-08-09"
            )
        )

        assertTrue(delta.changes.isEmpty())
        assertEquals(1, delta.comparedFixtureCount)
    }

    @Test
    fun membershipChangesAreNamedOnlyInsideSharedWindow() {
        val missing = fixture(
            id = 30L,
            start = "2026-08-07T14:00:00Z"
        )
        val added = fixture(
            id = 31L,
            start = "2026-08-07T16:00:00Z"
        )

        val delta = ApiFootballDeltaEngine.compare(
            previous = feed(100L, listOf(missing)),
            current = feed(200L, listOf(added))
        )

        assertEquals(2, delta.changes.size)
        assertEquals(
            ApiFootballChangeKind.NEW_IN_FEED,
            delta.changes[0].primaryKind
        )
        assertEquals(
            ApiFootballChangeKind.MISSING_FROM_FEED,
            delta.changes[1].primaryKind
        )
    }

    @Test
    fun unchangedFixtureProducesSealedEmptyDelta() {
        val fixture = fixture(
            id = 40L,
            start = "2026-08-07T14:00:00Z"
        )

        val delta = ApiFootballDeltaEngine.compare(
            previous = feed(100L, listOf(fixture)),
            current = feed(200L, listOf(fixture))
        )

        assertEquals(1, delta.comparedFixtureCount)
        assertTrue(delta.changes.isEmpty())
        assertEquals(12, delta.shortFingerprint.length)
    }

    @Test
    fun comparisonRequiresForwardChronology() {
        val feed = feed(200L, emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            ApiFootballDeltaEngine.compare(feed, feed)
        }
    }

    private fun feed(
        fetchedAt: Long,
        fixtures: List<ApiFootballFixture>,
        from: String = "2026-08-06",
        to: String = "2026-08-08"
    ): ApiFootballFeed {
        return ApiFootballFeed(
            fixtures = fixtures,
            fetchedAt = fetchedAt,
            fromDate = from,
            toDate = to,
            remainingRequests = 99
        )
    }

    private fun fixture(
        id: Long,
        start: String
    ): ApiFootballFixture {
        return ApiFootballFixture(
            fixtureId = id,
            country = "Russia",
            league = "Premier League",
            home = "Home $id",
            away = "Away $id",
            startAt = instant(start),
            statusCode = "NS",
            statusLabel = "Not Started",
            homeScore = null,
            awayScore = null
        )
    }

    private fun instant(value: String): Long {
        return Instant.parse(value).toEpochMilli()
    }
}
