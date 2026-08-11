package ru.sportpulse.info

import java.time.ZoneId

internal data class MatchdayBriefing(
    val totalCount: Int,
    val savedCount: Int,
    val liveCount: Int,
    val todayCount: Int,
    val verifyCount: Int
) {
    init {
        require(totalCount >= 0)
        require(savedCount in 0..totalCount)
        require(liveCount in 0..totalCount)
        require(todayCount in 0..totalCount)
        require(verifyCount in 0..totalCount)
    }

    fun timelineText(): String {
        return "Сейчас $liveCount  •  Сегодня $todayCount  •  " +
            "Проверить $verifyCount"
    }

    fun catalogText(): String {
        return "${eventCountText(totalCount)} • $savedCount сохранено"
    }
}

internal object MatchdayBriefingEngine {
    fun evaluate(
        events: List<SportEvent>,
        bookmarkedIds: Set<String>,
        now: Long,
        zoneId: ZoneId
    ): MatchdayBriefing {
        val summary = FeedTimelinePolicy.summary(
            events = events,
            now = now,
            zoneId = zoneId
        )
        val catalogIds = events.mapTo(mutableSetOf()) { it.id }
        return MatchdayBriefing(
            totalCount = summary.totalCount,
            savedCount = bookmarkedIds.count { it in catalogIds },
            liveCount = summary.count(FeedTimelineFilter.LIVE),
            todayCount = summary.count(FeedTimelineFilter.TODAY),
            verifyCount = summary.count(FeedTimelineFilter.VERIFY)
        )
    }
}
