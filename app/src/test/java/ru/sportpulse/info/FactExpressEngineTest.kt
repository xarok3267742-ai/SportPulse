package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FactExpressEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun emptyAndSingleSelectionStayDistinct() {
        val empty = evaluate(emptyList())
        val single = evaluate(listOf(candidate("one", 0)))

        assertEquals(FactExpressState.EMPTY, empty.state)
        assertEquals(FactExpressState.NEED_MORE, single.state)
        assertTrue(!empty.isReady)
        assertTrue(!single.isReady)
        assertEquals(64, empty.fingerprint.length)
    }

    @Test
    fun twoAndFourEventsAreBothReadyBoundaries() {
        val two = evaluate(
            listOf(candidate("one", 0), candidate("two", 1))
        )
        val four = evaluate(
            List(4) { candidate("event-$it", it) }
        )

        assertEquals(FactExpressState.READY, two.state)
        assertEquals(FactExpressState.READY, four.state)
        assertTrue(two.isReady)
        assertTrue(four.isReady)
    }

    @Test
    fun fifthEventIsNeverSilentlyDropped() {
        val result = evaluate(
            List(5) { candidate("event-$it", it) }
        )

        assertEquals(FactExpressState.TOO_MANY, result.state)
        assertEquals(5, result.entries.size)
        assertEquals(1, result.overLimitCount)
        assertTrue(!result.isReady)
    }

    @Test
    fun publishedOrderIsNowThenTimeThenUnknownThenComplete() {
        val result = evaluate(
            listOf(
                candidate(
                    id = "complete",
                    order = 0,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.COMPLETE,
                    nextAt = null
                ),
                candidate(
                    id = "unknown",
                    order = 1,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.INCOMPLETE,
                    nextAt = null
                ),
                candidate(
                    id = "wait-later",
                    order = 2,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.READY,
                    nextAt = now + 2L * HOUR
                ),
                candidate(
                    id = "act-now",
                    order = 3,
                    action = EventStoryAction.OPEN_FACTS,
                    phase = EventStoryPhase.PREPARING,
                    nextAt = now + 4L * HOUR
                ),
                candidate(
                    id = "wait-earlier",
                    order = 4,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.READY,
                    nextAt = now + HOUR
                )
            )
        )

        assertEquals(
            listOf(
                "act-now",
                "wait-earlier",
                "wait-later",
                "unknown",
                "complete"
            ),
            result.entries.map(FactExpressEntry::eventId)
        )
    }

    @Test
    fun countsSeparateActionsPointsUnknownAndComplete() {
        val result = evaluate(
            listOf(
                candidate("action", 0),
                candidate(
                    id = "waiting",
                    order = 1,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.READY
                ),
                candidate(
                    id = "unknown",
                    order = 2,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.INCOMPLETE,
                    nextAt = null
                ),
                candidate(
                    id = "complete",
                    order = 3,
                    action = EventStoryAction.NONE,
                    phase = EventStoryPhase.COMPLETE,
                    nextAt = null
                )
            )
        )

        assertEquals(1, result.actionNowCount)
        assertEquals(2, result.scheduledCount)
        assertEquals(1, result.unscheduledCount)
        assertEquals(1, result.completeCount)
    }

    @Test
    fun zoneAndMembershipAreBoundByFingerprint() {
        val source = listOf(candidate("one", 0), candidate("two", 1))
        val moscow = evaluate(source, RegionalZone.MOSCOW)
        val minsk = evaluate(source, RegionalZone.MINSK)
        val changed = evaluate(
            listOf(source[0], candidate("three", 2)),
            RegionalZone.MOSCOW
        )

        assertNotEquals(moscow.fingerprint, minsk.fingerprint)
        assertNotEquals(moscow.fingerprint, changed.fingerprint)
    }

    @Test
    fun resultDoesNotRetainFullStoriesOrBeacons() {
        val result = evaluate(
            listOf(candidate("one", 0), candidate("two", 1))
        )
        val retainedTypes = FactExpressEntry::class.java.declaredFields
            .map { it.type }

        assertTrue(EventStoryResult::class.java !in retainedTypes)
        assertTrue(StoryBeaconResult::class.java !in retainedTypes)
        assertEquals(2, result.entries.size)
    }

    @Test
    fun duplicateIdsAndCatalogPositionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            evaluate(
                listOf(candidate("same", 0), candidate("same", 1))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            evaluate(
                listOf(candidate("one", 0), candidate("two", 0))
            )
        }
    }

    @Test
    fun posterRequiresReadyResultFromTheSameMinute() {
        val ready = evaluate(
            listOf(candidate("one", 0), candidate("two", 1))
        )
        val notReady = evaluate(listOf(candidate("one", 0)))

        assertThrows(IllegalArgumentException::class.java) {
            FactExpressPosterFactory.create(notReady, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FactExpressPosterFactory.create(ready, now + 60_000L)
        }
    }

    @Test
    fun posterNameAndShareTextCarryRouteAndDisclaimer() {
        val result = evaluate(
            listOf(candidate("one", 0), candidate("two", 1))
        )
        val poster = FactExpressPosterFactory.create(result, now)
        val text = FactExpressPosterFactory.shareText(poster)

        assertEquals(
            "sport_pulse_fact_express_${
                result.shortFingerprint.lowercase()
            }_${now}.png",
            FactExpressPosterFactory.fileName(poster)
        )
        assertTrue(text.contains("Матч one"))
        assertTrue(text.contains("Матч two"))
        assertTrue(text.contains(result.shortFingerprint))
        assertTrue(text.contains("не ставка"))
        assertTrue(text.contains("без коэффициентов"))
        assertTrue(text.contains("без расчета выплаты"))
    }

    private fun evaluate(
        candidates: List<FactExpressCandidate>,
        zone: RegionalZone = RegionalZone.MOSCOW
    ): FactExpressResult {
        return FactExpressEngine.evaluate(
            candidates = candidates,
            selectedZone = zone,
            now = now
        )
    }

    private fun candidate(
        id: String,
        order: Int,
        action: EventStoryAction = EventStoryAction.OPEN_FACTS,
        phase: EventStoryPhase = EventStoryPhase.PREPARING,
        nextAt: Long? = now + HOUR
    ): FactExpressCandidate {
        val label = "Матч $id"
        val story = EventStoryResult(
            eventId = id,
            eventLabel = label,
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = if (
                        phase == EventStoryPhase.COMPLETE
                    ) {
                        EventStoryChapterState.COMPLETE
                    } else {
                        EventStoryChapterState.ACTIVE
                    },
                    summary = "Тестовая глава"
                )
            },
            phase = phase,
            action = action,
            actionFactor = if (
                action == EventStoryAction.OPEN_FACTS
            ) {
                SignalFactor.LOAD
            } else {
                null
            },
            startAt = if (nextAt == null) null else now + 8L * HOUR,
            reviewOpensAt = if (nextAt == null) {
                null
            } else {
                now + 12L * HOUR
            },
            fingerprint = fingerprintFor(id, 'a')
        )
        val moments = buildList {
            if (phase == EventStoryPhase.COMPLETE) {
                add(
                    StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.COMPLETE,
                        at = null
                    )
                )
            } else {
                if (action != EventStoryAction.NONE) {
                    add(
                        StoryBeaconMoment(
                            kind = StoryBeaconMomentKind.ACTION_NOW,
                            at = null,
                            factors = if (
                                action == EventStoryAction.OPEN_FACTS
                            ) {
                                listOf(SignalFactor.LOAD)
                            } else {
                                emptyList()
                            },
                            action = action
                        )
                    )
                }
                nextAt?.let {
                    add(
                        StoryBeaconMoment(
                            kind = StoryBeaconMomentKind.CHECK_WINDOW,
                            at = it,
                            factors = listOf(SignalFactor.LOAD)
                        )
                    )
                }
            }
        }
        val beacon = StoryBeaconResult(
            eventId = id,
            evaluatedAtMinute = now / 60_000L,
            state = when {
                phase == EventStoryPhase.COMPLETE ->
                    StoryBeaconState.COMPLETE
                action != EventStoryAction.NONE ->
                    StoryBeaconState.ACTION_NOW
                nextAt != null -> StoryBeaconState.WATCHING
                else -> StoryBeaconState.INCOMPLETE
            },
            moments = moments,
            fingerprint = fingerprintFor(id, 'b')
        )
        return FactExpressCandidate(
            eventId = id,
            match = label,
            sport = "Футбол",
            region = "Россия",
            catalogOrder = order,
            story = story,
            beacon = beacon
        )
    }

    private fun fingerprintFor(id: String, fill: Char): String {
        val suffix = id.length.toString(16).padStart(2, '0')
        return fill.toString().repeat(62) + suffix
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
    }
}
