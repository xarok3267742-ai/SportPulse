package ru.sportpulse.info

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class ApiFootballFixture(
    val fixtureId: Long,
    val country: String,
    val league: String,
    val home: String,
    val away: String,
    val startAt: Long,
    val statusCode: String,
    val statusLabel: String,
    val homeScore: Int?,
    val awayScore: Int?
) {
    init {
        require(fixtureId > 0L)
        require(country.isNotBlank())
        require(league.isNotBlank())
        require(home.isNotBlank())
        require(away.isNotBlank())
        require(country.length <= SportEventContentPolicy.MAX_REGION_LENGTH)
        require(league.length <= SportEventContentPolicy.MAX_TOURNAMENT_LENGTH)
        require(
            "$home - $away".length <=
                SportEventContentPolicy.MAX_MATCH_LENGTH
        )
        require(statusCode.length <= ApiFootballPolicy.MAX_STATUS_CODE_LENGTH)
        require(statusLabel.length <= ApiFootballPolicy.MAX_STATUS_LENGTH)
        require(startAt >= 0L)
    }

    fun toSportEvent(
        imageRes: Int,
        fetchedAt: Long
    ): SportEvent {
        val localizedStatus = ApiFootballText.status(
            code = statusCode,
            fallback = statusLabel
        )
        val score = if (homeScore != null && awayScore != null) {
            " Счёт: $homeScore:$awayScore."
        } else {
            ""
        }
        return SportEvent(
            id = "api_football_$fixtureId",
            sport = "Футбол",
            tournament = league,
            region = ApiFootballText.country(country),
            match = "$home - $away",
            time = "Онлайн",
            focus = "Составы, форма команд и календарная нагрузка",
            note = "$localizedStatus.$score " +
                "Расписание получено из внешнего источника по HTTPS; " +
                "аналитические факторы нужно проверить отдельно.",
            tags = listOf("Расписание", localizedStatus),
            imageRes = imageRes,
            seedAssessment = SignalAssessment(
                List(SignalFactor.values().size) { 0 }
            ),
            startAt = startAt,
            origin = SportEventOrigin.API_SPORTS,
            defaultEvidenceLevel = EvidenceLevel.UNCONFIRMED,
            providerRef = fixtureId.toString(),
            providerStatus = localizedStatus,
            providerStatusCode = statusCode,
            syncedAt = fetchedAt
        )
    }
}

internal data class ApiFootballFeed(
    val fixtures: List<ApiFootballFixture>,
    val fetchedAt: Long,
    val fromDate: String,
    val toDate: String,
    val remainingRequests: Int?
) {
    init {
        require(fetchedAt >= 0L)
        require(fromDate.isNotBlank())
        require(toDate.isNotBlank())
        require(remainingRequests == null || remainingRequests >= 0)
    }

    fun isFresh(
        now: Long,
        maxAgeMillis: Long = ApiFootballPolicy.CACHE_TTL_MILLIS
    ): Boolean {
        require(now >= 0L)
        require(maxAgeMillis >= 0L)
        return fetchedAt <= now && now - fetchedAt <= maxAgeMillis
    }
}

internal data class ApiFootballHistory(
    val current: ApiFootballFeed?,
    val previous: ApiFootballFeed?
) {
    init {
        require(previous == null || current != null)
        require(
            previous == null ||
                previous.fetchedAt < checkNotNull(current).fetchedAt
        )
    }
}

internal object ApiFootballPolicy {
    const val CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L
    const val STALE_CACHE_MILLIS = 48L * 60L * 60L * 1000L
    const val MANUAL_REFRESH_MIN_AGE_MILLIS =
        60L * 60L * 1000L
    const val WINDOW_DAYS = 2L
    const val MAX_REGION_FIXTURES = 60
    const val MAX_STATUS_CODE_LENGTH = 16
    const val MAX_STATUS_LENGTH = 80

    val regionCountries = setOf(
        "russia",
        "belarus",
        "kazakhstan",
        "armenia",
        "azerbaijan",
        "kyrgyzstan",
        "uzbekistan",
        "tajikistan",
        "turkmenistan",
        "moldova"
    )
}

internal object ApiFootballParser {
    fun decode(
        json: String,
        fetchedAt: Long,
        remainingRequests: Int?
    ): ApiFootballFeed {
        require(fetchedAt >= 0L)
        val root = JSONObject(json)
        val errors = errorText(root.opt("errors"))
        if (errors.isNotBlank()) {
            throw IllegalArgumentException(errors)
        }
        val parameters = root.optJSONObject("parameters")
        val response = root.optJSONArray("response") ?: JSONArray()
        val fixtures = buildList {
            repeat(response.length()) { index ->
                val item = response.optJSONObject(index) ?: return@repeat
                parseFixture(item)?.let(::add)
            }
        }
            .filter {
                it.country.lowercase(Locale.ROOT) in
                    ApiFootballPolicy.regionCountries
            }
            .distinctBy(ApiFootballFixture::fixtureId)
            .sortedWith(
                compareBy<ApiFootballFixture> { it.startAt }
                    .thenBy { it.fixtureId }
            )
            .take(ApiFootballPolicy.MAX_REGION_FIXTURES)

        return ApiFootballFeed(
            fixtures = fixtures,
            fetchedAt = fetchedAt,
            fromDate = parameters?.optString("from")
                ?.takeIf(String::isNotBlank)
                ?: parameters?.optString("date")
                    ?.takeIf(String::isNotBlank)
                ?: Instant.ofEpochMilli(fetchedAt)
                    .atZone(ZoneId.of("Europe/Moscow"))
                    .toLocalDate()
                    .toString(),
            toDate = parameters?.optString("to")
                ?.takeIf(String::isNotBlank)
                ?: parameters?.optString("date")
                    ?.takeIf(String::isNotBlank)
                ?: Instant.ofEpochMilli(fetchedAt)
                    .atZone(ZoneId.of("Europe/Moscow"))
                    .toLocalDate()
                    .plusDays(ApiFootballPolicy.WINDOW_DAYS)
                    .toString(),
            remainingRequests = remainingRequests
        )
    }

    private fun parseFixture(item: JSONObject): ApiFootballFixture? {
        val fixture = item.optJSONObject("fixture") ?: return null
        val league = item.optJSONObject("league") ?: return null
        val teams = item.optJSONObject("teams") ?: return null
        val home = teams.optJSONObject("home") ?: return null
        val away = teams.optJSONObject("away") ?: return null
        val id = fixture.optLong("id", -1L)
        val timestampSeconds = fixture.optLong("timestamp", -1L)
        if (
            id <= 0L ||
            timestampSeconds < 0L ||
            timestampSeconds > Long.MAX_VALUE / 1000L
        ) return null
        val status = fixture.optJSONObject("status")
        val goals = item.optJSONObject("goals")
        val country = league.optString("country").trim()
        val leagueName = league.optString("name").trim()
        val homeName = home.optString("name").trim()
        val awayName = away.optString("name").trim()
        val statusCode = status?.optString("short").orEmpty().trim()
        val statusLabel = status?.optString("long").orEmpty().trim()
        if (
            country.isBlank() ||
            country.length > SportEventContentPolicy.MAX_REGION_LENGTH ||
            leagueName.isBlank() ||
            leagueName.length >
            SportEventContentPolicy.MAX_TOURNAMENT_LENGTH ||
            homeName.isBlank() ||
            awayName.isBlank() ||
            "$homeName - $awayName".length >
            SportEventContentPolicy.MAX_MATCH_LENGTH ||
            statusCode.length >
            ApiFootballPolicy.MAX_STATUS_CODE_LENGTH ||
            statusLabel.length > ApiFootballPolicy.MAX_STATUS_LENGTH
        ) return null
        return ApiFootballFixture(
            fixtureId = id,
            country = country,
            league = leagueName,
            home = homeName,
            away = awayName,
            startAt = timestampSeconds * 1000L,
            statusCode = statusCode,
            statusLabel = statusLabel,
            homeScore = goals.nullableInt("home"),
            awayScore = goals.nullableInt("away")
        )
    }

    private fun JSONObject?.nullableInt(key: String): Int? {
        if (this == null || isNull(key)) return null
        return optInt(key)
    }

    private fun errorText(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONArray -> buildList {
                repeat(value.length()) { index ->
                    value.opt(index)?.toString()?.takeIf {
                        it.isNotBlank()
                    }?.let(::add)
                }
            }.joinToString("; ")
            is JSONObject -> value.keys().asSequence().map { key ->
                "$key: ${value.opt(key)}"
            }.joinToString("; ")
            else -> value.toString()
        }
    }
}

internal object ApiFootballFeedCodec {
    fun encode(feed: ApiFootballFeed): String {
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("fetchedAt", feed.fetchedAt)
            put("fromDate", feed.fromDate)
            put("toDate", feed.toDate)
            put("remainingRequests", feed.remainingRequests)
            put(
                "fixtures",
                JSONArray().apply {
                    feed.fixtures.forEach { fixture ->
                        put(JSONObject().apply {
                            put("fixtureId", fixture.fixtureId)
                            put("country", fixture.country)
                            put("league", fixture.league)
                            put("home", fixture.home)
                            put("away", fixture.away)
                            put("startAt", fixture.startAt)
                            put("statusCode", fixture.statusCode)
                            put("statusLabel", fixture.statusLabel)
                            put("homeScore", fixture.homeScore)
                            put("awayScore", fixture.awayScore)
                        })
                    }
                }
            )
        }.toString()
    }

    fun decode(json: String): ApiFootballFeed {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") == 1)
        val fixturesJson = root.getJSONArray("fixtures")
        val fixtures = buildList {
            repeat(fixturesJson.length()) { index ->
                val item = fixturesJson.getJSONObject(index)
                add(
                    ApiFootballFixture(
                        fixtureId = item.getLong("fixtureId"),
                        country = item.getString("country"),
                        league = item.getString("league"),
                        home = item.getString("home"),
                        away = item.getString("away"),
                        startAt = item.getLong("startAt"),
                        statusCode = item.optString("statusCode"),
                        statusLabel = item.optString("statusLabel"),
                        homeScore = item.nullableInt("homeScore"),
                        awayScore = item.nullableInt("awayScore")
                    )
                )
            }
        }
        return ApiFootballFeed(
            fixtures = fixtures,
            fetchedAt = root.getLong("fetchedAt"),
            fromDate = root.getString("fromDate"),
            toDate = root.getString("toDate"),
            remainingRequests = root.nullableInt("remainingRequests")
        )
    }

    private fun JSONObject.nullableInt(key: String): Int? {
        return if (isNull(key) || !has(key)) null else getInt(key)
    }
}

internal object SportsScheduleProxyPolicy {
    fun isConfigured(endpoint: String): Boolean {
        if (endpoint.isBlank()) return false
        return runCatching { requireValid(endpoint) }.isSuccess
    }

    fun requireValid(endpoint: String) {
        val uri = runCatching { URI(endpoint) }
            .getOrElse { throw IllegalArgumentException("Invalid proxy URL", it) }
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        require(uri.scheme.equals("https", ignoreCase = true))
        require(host.isNotBlank())
        require(uri.userInfo == null)
        require(uri.fragment == null)
        require(!host.endsWith(".rapidapi.com"))
        require(host != "api-sports.io" && !host.endsWith(".api-sports.io"))
    }
}

internal object SportsScheduleProxyRequest {
    val headers: Map<String, String> = mapOf(
        "Accept" to "application/json"
    )

    fun url(endpoint: String, date: LocalDate): URL {
        SportsScheduleProxyPolicy.requireValid(endpoint)
        val separator = if ('?' in endpoint) '&' else '?'
        val query = listOf(
            "date" to date.toString(),
            "timezone" to "Europe/Moscow"
        ).joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        return URL(endpoint + separator + query)
    }
}

internal class ApiFootballClient(
    private val endpoint: String
) {
    init {
        SportsScheduleProxyPolicy.requireValid(endpoint)
    }

    fun fetch(now: Long = System.currentTimeMillis()): ApiFootballFeed {
        require(now >= 0L)
        val moscow = ZoneId.of("Europe/Moscow")
        val from = Instant.ofEpochMilli(now)
            .atZone(moscow)
            .toLocalDate()
        val to = from.plusDays(ApiFootballPolicy.WINDOW_DAYS)
        val dailyFeeds = (0L..ApiFootballPolicy.WINDOW_DAYS).map { day ->
            fetchDate(
                date = from.plusDays(day),
                fetchedAt = now
            )
        }
        return ApiFootballFeed(
            fixtures = dailyFeeds
                .flatMap(ApiFootballFeed::fixtures)
                .distinctBy(ApiFootballFixture::fixtureId)
                .sortedWith(
                    compareBy<ApiFootballFixture> { it.startAt }
                        .thenBy { it.fixtureId }
                )
                .take(ApiFootballPolicy.MAX_REGION_FIXTURES),
            fetchedAt = now,
            fromDate = from.toString(),
            toDate = to.toString(),
            remainingRequests = dailyFeeds
                .mapNotNull(ApiFootballFeed::remainingRequests)
                .lastOrNull()
        )
    }

    private fun fetchDate(
        date: LocalDate,
        fetchedAt: Long
    ): ApiFootballFeed {
        val connection = SportsScheduleProxyRequest
            .url(endpoint, date)
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            SportsScheduleProxyRequest.headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).opt("errors")?.toString()
                }.getOrNull()?.takeIf(String::isNotBlank)
                throw ApiFootballException(
                    statusCode = status,
                    message = detail ?: "HTTP $status"
                )
            }
            ApiFootballParser.decode(
                json = body,
                fetchedAt = fetchedAt,
                remainingRequests = null
            )
        } finally {
            connection.disconnect()
        }
    }
}

internal class ApiFootballException(
    val statusCode: Int,
    message: String
) : Exception(message)

internal class ApiFootballCache(context: Context) {
    private val cacheFile = File(context.filesDir, "api_football_feed.json")
    private val previousCacheFile =
        File(context.filesDir, "api_football_feed_previous.json")

    fun read(now: Long = System.currentTimeMillis()): ApiFootballFeed? {
        return readHistory(now).current
    }

    fun readHistory(
        now: Long = System.currentTimeMillis()
    ): ApiFootballHistory {
        val current = readValid(cacheFile, now)
            ?: return ApiFootballHistory(
                current = null,
                previous = null
            )
        val previous = readValid(previousCacheFile, now)
            ?.takeIf { it.fetchedAt < current.fetchedAt }
        return ApiFootballHistory(
            current = current,
            previous = previous
        )
    }

    fun write(feed: ApiFootballFeed) {
        val existing = readRaw(cacheFile)
        if (existing != null && existing.fetchedAt > feed.fetchedAt) {
            return
        }
        if (existing != null && existing.fetchedAt < feed.fetchedAt) {
            writeAtomic(previousCacheFile, existing)
        }
        writeAtomic(cacheFile, feed)
    }

    private fun readValid(file: File, now: Long): ApiFootballFeed? {
        return readRaw(file)?.takeIf { feed ->
            feed.fetchedAt <= now &&
                now - feed.fetchedAt <=
                ApiFootballPolicy.STALE_CACHE_MILLIS
        }
    }

    private fun readRaw(file: File): ApiFootballFeed? {
        if (!file.isFile) return null
        return runCatching {
            ApiFootballFeedCodec.decode(file.readText())
        }.getOrNull()
    }

    private fun writeAtomic(file: File, feed: ApiFootballFeed) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(ApiFootballFeedCodec.encode(feed))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}

internal object ApiFootballText {
    fun country(country: String): String {
        return when (country.lowercase(Locale.ROOT)) {
            "russia" -> "Россия"
            "belarus" -> "Беларусь"
            "kazakhstan" -> "Казахстан"
            "armenia" -> "Армения"
            "azerbaijan" -> "Азербайджан"
            "kyrgyzstan" -> "Кыргызстан"
            "uzbekistan" -> "Узбекистан"
            "tajikistan" -> "Таджикистан"
            "turkmenistan" -> "Туркменистан"
            "moldova" -> "Молдова"
            else -> country
        }
    }

    fun status(code: String, fallback: String): String {
        return when (code.uppercase(Locale.ROOT)) {
            "NS" -> "Матч не начался"
            "1H", "2H", "ET", "P", "LIVE" -> "Матч идёт"
            "HT", "BT" -> "Перерыв"
            "FT", "AET", "PEN" -> "Матч завершён"
            "PST" -> "Матч отложен"
            "CANC" -> "Матч отменён"
            "SUSP", "INT" -> "Матч приостановлен"
            "ABD" -> "Матч прекращён"
            "AWD" -> "Результат присуждён"
            "WO" -> "Технический исход"
            "TBD" -> "Время уточняется"
            else -> fallback.takeIf(String::isNotBlank) ?: "Статус уточняется"
        }
    }
}
