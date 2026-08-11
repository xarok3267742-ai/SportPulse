package ru.sportpulse.info

internal data class MatchCenterSelection(
    val events: List<SportEvent>,
    val leadEventId: String?,
    val hiddenCount: Int
) {
    init {
        require(hiddenCount >= 0)
        require(events.map(SportEvent::id).distinct().size == events.size)
        require(leadEventId == null || events.any { it.id == leadEventId })
    }

    fun isLead(event: SportEvent): Boolean {
        return event.id == leadEventId
    }
}

internal object MatchCenterPolicy {
    const val DEFAULT_VISIBLE_COUNT = 4

    fun select(
        events: List<SportEvent>,
        leadEventId: String?,
        visibleCount: Int = DEFAULT_VISIBLE_COUNT
    ): MatchCenterSelection {
        require(visibleCount > 0)
        require(events.map(SportEvent::id).distinct().size == events.size)

        if (events.isEmpty()) {
            return MatchCenterSelection(
                events = emptyList(),
                leadEventId = null,
                hiddenCount = 0
            )
        }

        val lead = events.firstOrNull { it.id == leadEventId }
        val visible = events.take(visibleCount).toMutableList()
        if (lead != null && visible.none { it.id == lead.id }) {
            if (visible.size == visibleCount) {
                visible[visible.lastIndex] = lead
            } else {
                visible += lead
            }
        }

        return MatchCenterSelection(
            events = visible,
            leadEventId = lead?.id,
            hiddenCount = events.size - visible.size
        )
    }
}
