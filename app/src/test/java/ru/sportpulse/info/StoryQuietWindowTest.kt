package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryQuietWindowTest {
    private val now = 1_785_840_000_000L

    @Test
    fun emptyMapHasNoQuietWindow() {
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(emptyList()),
            now = now
        )

        assertEquals(StoryQuietWindowState.EMPTY, result.state)
        assertEquals(0, result.activeCount)
        assertNull(result.returnAt)
        assertEquals(64, result.fingerprint.length)
    }

    @Test
    fun integrityOnlyMapHasNoActiveQuestion() {
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(listOf(tamperedEntry())),
            now = now
        )

        assertEquals(StoryQuietWindowState.NO_ACTIVE, result.state)
        assertEquals(0, result.activeCount)
        assertNull(result.entry)
    }

    @Test
    fun activeThreadsWithoutFutureMomentsStayUnscheduled() {
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(
                listOf(
                    activeEntry("moved", StoryThreadMapState.MOVED),
                    activeEntry("open", StoryThreadMapState.OPEN)
                )
            ),
            now = now
        )

        assertEquals(
            StoryQuietWindowState.UNSCHEDULED,
            result.state
        )
        assertEquals(2, result.activeCount)
        assertEquals(0, result.scheduledCount)
        assertEquals(2, result.unscheduledCount)
    }

    @Test
    fun earliestFutureMomentWinsAcrossMapStates() {
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(
                listOf(
                    activeEntry(
                        "moved-later",
                        StoryThreadMapState.MOVED,
                        now + 20_000L
                    ),
                    activeEntry(
                        "open-earlier",
                        StoryThreadMapState.OPEN,
                        now + 10_000L
                    )
                )
            ),
            now = now
        )

        assertEquals(StoryQuietWindowState.AVAILABLE, result.state)
        assertEquals("open-earlier", result.entry?.eventId)
        assertEquals(now + 10_000L, result.returnAt)
        assertEquals(2, result.scheduledCount)
    }

    @Test
    fun equalMomentsPreservePublishedMapOrder() {
        val target = now + 10_000L
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(
                listOf(
                    activeEntry(
                        "moved-first",
                        StoryThreadMapState.MOVED,
                        target
                    ),
                    activeEntry(
                        "open-second",
                        StoryThreadMapState.OPEN,
                        target
                    )
                )
            ),
            now = now
        )

        assertEquals("moved-first", result.entry?.eventId)
    }

    @Test
    fun expiredMomentIsNeverOfferedAsReturnPoint() {
        val result = StoryQuietWindowEngine.evaluate(
            threadMap = threadMap(
                listOf(
                    activeEntry(
                        "expired",
                        StoryThreadMapState.OPEN,
                        now
                    )
                )
            ),
            now = now
        )

        assertEquals(
            StoryQuietWindowState.UNSCHEDULED,
            result.state
        )
        assertNull(result.returnAt)
    }

    @Test
    fun fingerprintIgnoresClockDriftUntilMeaningfulBoundary() {
        val target = now + 60_000L
        val map = threadMap(
            listOf(
                activeEntry(
                    "stable",
                    StoryThreadMapState.OPEN,
                    target
                )
            )
        )
        val first = StoryQuietWindowEngine.evaluate(map, now)
        val later = StoryQuietWindowEngine.evaluate(
            map,
            now + 30_000L
        )
        val crossed = StoryQuietWindowEngine.evaluate(
            map,
            target
        )

        assertEquals(first.fingerprint, later.fingerprint)
        assertNotEquals(first.fingerprint, crossed.fingerprint)
        assertEquals(
            StoryQuietWindowState.UNSCHEDULED,
            crossed.state
        )
    }

    @Test
    fun pauseReachesNearbyReturnPointExactly() {
        val returnAt = now + 60L * 60L * 1000L

        assertEquals(
            returnAt,
            StoryQuietWindowPolicy.pauseUntil(now, returnAt)
        )
        assertTrue(
            StoryQuietWindowPolicy.reachesReturnPoint(now, returnAt)
        )
    }

    @Test
    fun pauseIsCappedAtExistingTwentyFourHourContract() {
        val returnAt = now + 7L * 24L * 60L * 60L * 1000L
        val expected = now +
            StoryQuietWindowPolicy.MAX_PAUSE_MILLIS

        assertEquals(
            expected,
            StoryQuietWindowPolicy.pauseUntil(now, returnAt)
        )
        assertTrue(
            !StoryQuietWindowPolicy.reachesReturnPoint(now, returnAt)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun pauseRejectsNonFutureReturnPoint() {
        StoryQuietWindowPolicy.pauseUntil(now, now)
    }

    private fun threadMap(
        entries: List<StoryThreadMapEntry>
    ): StoryThreadMapResult {
        return StoryThreadMapResult(
            entries = entries,
            leadingState = entries.firstOrNull()?.state
                ?: StoryThreadMapState.EMPTY,
            tamperedCount = entries.count {
                it.state == StoryThreadMapState.TAMPERED
            },
            detachedCount = entries.count {
                it.state == StoryThreadMapState.DETACHED
            },
            movedCount = entries.count {
                it.state == StoryThreadMapState.MOVED
            },
            missedCount = entries.count {
                it.state == StoryThreadMapState.MISSED
            },
            openCount = entries.count {
                it.state == StoryThreadMapState.OPEN
            },
            resolvedCount = entries.count {
                it.state == StoryThreadMapState.RESOLVED
            },
            fingerprint = "f".repeat(64)
        )
    }

    private fun activeEntry(
        eventId: String,
        state: StoryThreadMapState,
        nextAt: Long? = null
    ): StoryThreadMapEntry {
        require(
            state == StoryThreadMapState.OPEN ||
                state == StoryThreadMapState.MOVED
        )
        val thread = StoryThread(
            eventId = eventId,
            chapter = EventStoryChapter.FACTS,
            startedAt = now - 1_000L,
            initialState = EventStoryChapterState.ACTIVE,
            initialStoryFingerprint = "a".repeat(64),
            fingerprint = "b".repeat(64)
        )
        val status = if (state == StoryThreadMapState.OPEN) {
            StoryThreadStatus.OPEN
        } else {
            StoryThreadStatus.MOVED
        }
        val result = StoryThreadResult(
            thread = thread,
            currentState = if (status == StoryThreadStatus.OPEN) {
                EventStoryChapterState.ACTIVE
            } else {
                EventStoryChapterState.ATTENTION
            },
            status = status,
            fingerprint = "c".repeat(64)
        )
        return StoryThreadMapEntry(
            eventId = eventId,
            match = "Команда $eventId - Соперник",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = if (state == StoryThreadMapState.MOVED) 0 else 1,
            state = state,
            thread = thread,
            result = result,
            nextMoment = nextAt?.let {
                StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.START,
                    at = it
                )
            }
        )
    }

    private fun tamperedEntry(): StoryThreadMapEntry {
        return StoryThreadMapEntry(
            eventId = "tampered",
            match = null,
            sport = null,
            region = null,
            catalogOrder = null,
            state = StoryThreadMapState.TAMPERED,
            thread = null,
            result = null,
            nextMoment = null
        )
    }
}
