package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal enum class ApiFootballChangeKind(
    val priority: Int
) {
    SCORE(0),
    STATUS(1),
    START_TIME(2),
    NEW_IN_FEED(3),
    MISSING_FROM_FEED(4)
}

internal data class ApiFootballChange(
    val fixtureId: Long,
    val match: String,
    val league: String,
    val country: String,
    val kinds: Set<ApiFootballChangeKind>,
    val previous: ApiFootballFixture?,
    val current: ApiFootballFixture?
) {
    init {
        require(fixtureId > 0L)
        require(match.isNotBlank())
        require(league.isNotBlank())
        require(country.isNotBlank())
        require(kinds.isNotEmpty())
        require(previous != null || current != null)
        require(previous?.fixtureId == null || previous.fixtureId == fixtureId)
        require(current?.fixtureId == null || current.fixtureId == fixtureId)
    }

    val referenceStartAt: Long
        get() = current?.startAt ?: checkNotNull(previous).startAt

    val primaryKind: ApiFootballChangeKind
        get() = kinds.minBy(ApiFootballChangeKind::priority)
}

internal data class ApiFootballDelta(
    val previousFetchedAt: Long,
    val currentFetchedAt: Long,
    val comparedFixtureCount: Int,
    val changes: List<ApiFootballChange>,
    val shortFingerprint: String
) {
    init {
        require(previousFetchedAt >= 0L)
        require(currentFetchedAt > previousFetchedAt)
        require(comparedFixtureCount >= 0)
        require(shortFingerprint.length == 12)
    }

    fun count(kind: ApiFootballChangeKind): Int {
        return changes.count { kind in it.kinds }
    }
}

internal object ApiFootballDeltaEngine {
    private val moscow = ZoneId.of("Europe/Moscow")

    fun compare(
        previous: ApiFootballFeed,
        current: ApiFootballFeed
    ): ApiFootballDelta {
        require(current.fetchedAt > previous.fetchedAt)
        val previousRange = feedRange(previous)
        val currentRange = feedRange(current)
        val previousById = previous.fixtures.associateBy(
            ApiFootballFixture::fixtureId
        )
        val currentById = current.fixtures.associateBy(
            ApiFootballFixture::fixtureId
        )
        val changes = buildList {
            (previousById.keys intersect currentById.keys)
                .sorted()
                .forEach { fixtureId ->
                    val before = checkNotNull(previousById[fixtureId])
                    val after = checkNotNull(currentById[fixtureId])
                    changedFixture(before, after)?.let(::add)
                }

            (currentById.keys - previousById.keys)
                .sorted()
                .forEach { fixtureId ->
                    val fixture = checkNotNull(currentById[fixtureId])
                    if (fixture.localDate() in previousRange) {
                        add(
                            feedMembershipChange(
                                fixture = fixture,
                                kind = ApiFootballChangeKind.NEW_IN_FEED,
                                previous = null,
                                current = fixture
                            )
                        )
                    }
                }

            (previousById.keys - currentById.keys)
                .sorted()
                .forEach { fixtureId ->
                    val fixture = checkNotNull(previousById[fixtureId])
                    if (fixture.localDate() in currentRange) {
                        add(
                            feedMembershipChange(
                                fixture = fixture,
                                kind = ApiFootballChangeKind.MISSING_FROM_FEED,
                                previous = fixture,
                                current = null
                            )
                        )
                    }
                }
        }.sortedWith(
            compareBy<ApiFootballChange> { it.primaryKind.priority }
                .thenBy(ApiFootballChange::referenceStartAt)
                .thenBy(ApiFootballChange::fixtureId)
        )
        val comparedCount =
            (previousById.keys intersect currentById.keys).size
        return ApiFootballDelta(
            previousFetchedAt = previous.fetchedAt,
            currentFetchedAt = current.fetchedAt,
            comparedFixtureCount = comparedCount,
            changes = changes,
            shortFingerprint = fingerprint(
                previous = previous,
                current = current,
                changes = changes
            )
        )
    }

    private fun changedFixture(
        previous: ApiFootballFixture,
        current: ApiFootballFixture
    ): ApiFootballChange? {
        val kinds = linkedSetOf<ApiFootballChangeKind>()
        if (previous.startAt != current.startAt) {
            kinds += ApiFootballChangeKind.START_TIME
        }
        if (previous.statusKey() != current.statusKey()) {
            kinds += ApiFootballChangeKind.STATUS
        }
        if (previous.score() != current.score()) {
            kinds += ApiFootballChangeKind.SCORE
        }
        if (kinds.isEmpty()) return null
        return ApiFootballChange(
            fixtureId = current.fixtureId,
            match = "${current.home} - ${current.away}",
            league = current.league,
            country = current.country,
            kinds = kinds,
            previous = previous,
            current = current
        )
    }

    private fun feedMembershipChange(
        fixture: ApiFootballFixture,
        kind: ApiFootballChangeKind,
        previous: ApiFootballFixture?,
        current: ApiFootballFixture?
    ): ApiFootballChange {
        return ApiFootballChange(
            fixtureId = fixture.fixtureId,
            match = "${fixture.home} - ${fixture.away}",
            league = fixture.league,
            country = fixture.country,
            kinds = setOf(kind),
            previous = previous,
            current = current
        )
    }

    private fun feedRange(feed: ApiFootballFeed): ClosedRange<LocalDate> {
        val from = LocalDate.parse(feed.fromDate)
        val to = LocalDate.parse(feed.toDate)
        require(!to.isBefore(from))
        return from..to
    }

    private fun ApiFootballFixture.localDate(): LocalDate {
        return Instant.ofEpochMilli(startAt)
            .atZone(moscow)
            .toLocalDate()
    }

    private fun ApiFootballFixture.statusKey(): String {
        return statusCode.trim().uppercase(Locale.ROOT).ifBlank {
            statusLabel.trim().lowercase(Locale.ROOT)
        }
    }

    private fun ApiFootballFixture.score(): Pair<Int?, Int?> {
        return homeScore to awayScore
    }

    private fun fingerprint(
        previous: ApiFootballFeed,
        current: ApiFootballFeed,
        changes: List<ApiFootballChange>
    ): String {
        val payload = buildString {
            append("api-football-delta-v1|")
            append(previous.fetchedAt)
            append('|')
            append(current.fetchedAt)
            changes.forEach { change ->
                append('|')
                append(change.fixtureId)
                append(':')
                append(
                    change.kinds
                        .sortedBy(ApiFootballChangeKind::priority)
                        .joinToString(",") { it.name }
                )
                append(':')
                append(fixtureFingerprint(change.previous))
                append('>')
                append(fixtureFingerprint(change.current))
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(12)
            .uppercase(Locale.ROOT)
    }

    private fun fixtureFingerprint(fixture: ApiFootballFixture?): String {
        if (fixture == null) return "-"
        return listOf(
            fixture.fixtureId,
            fixture.startAt,
            fixture.statusCode,
            fixture.statusLabel,
            fixture.homeScore,
            fixture.awayScore
        ).joinToString("/")
    }
}
