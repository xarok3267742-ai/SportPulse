package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCenterPolicyTest {
    @Test
    fun keepsTheNearestEventsInTheirExistingOrder() {
        val events = List(6) { event("event-$it") }

        val result = MatchCenterPolicy.select(
            events = events,
            leadEventId = "event-1"
        )

        assertEquals(
            listOf("event-0", "event-1", "event-2", "event-3"),
            result.events.map(SportEvent::id)
        )
        assertEquals(2, result.hiddenCount)
        assertTrue(result.isLead(events[1]))
        assertFalse(result.isLead(events[0]))
    }

    @Test
    fun keepsTheLeadEventVisibleWithoutGrowingTheWindow() {
        val events = List(7) { event("event-$it") }

        val result = MatchCenterPolicy.select(
            events = events,
            leadEventId = "event-6"
        )

        assertEquals(
            listOf("event-0", "event-1", "event-2", "event-6"),
            result.events.map(SportEvent::id)
        )
        assertEquals("event-6", result.leadEventId)
        assertEquals(3, result.hiddenCount)
    }

    @Test
    fun ignoresAnUnknownLeadInsteadOfInventingAnEvent() {
        val events = List(3) { event("event-$it") }

        val result = MatchCenterPolicy.select(
            events = events,
            leadEventId = "missing"
        )

        assertEquals(events, result.events)
        assertNull(result.leadEventId)
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun emptyInputProducesAnEmptySelection() {
        val result = MatchCenterPolicy.select(
            events = emptyList(),
            leadEventId = "missing"
        )

        assertTrue(result.events.isEmpty())
        assertNull(result.leadEventId)
        assertEquals(0, result.hiddenCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateEventIdsAreRejected() {
        MatchCenterPolicy.select(
            events = listOf(event("same"), event("same")),
            leadEventId = null
        )
    }

    private fun event(id: String): SportEvent {
        return SportEvent(
            id = id,
            sport = "Футбол",
            tournament = "Турнир",
            region = "Россия",
            match = "Команда $id - Соперник $id",
            time = "Сегодня, 19:00",
            focus = "Факты",
            note = "Проверка",
            tags = emptyList(),
            imageRes = 1,
            seedAssessment = SignalAssessment(
                List(SignalFactor.values().size) { 0 }
            )
        )
    }
}
