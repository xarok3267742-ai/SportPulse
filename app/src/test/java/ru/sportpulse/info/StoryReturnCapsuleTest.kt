package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryReturnCapsuleTest {
    private val now = 1_785_840_000_000L
    private val returnAt = now + 60L * 60L * 1000L

    @Test
    fun factoryCreatesSealedRoundTripCapsule() {
        val capsule = capsule()
        val encoded = StoryReturnCapsuleCodec.encode(capsule)
        val decoded = StoryReturnCapsuleCodec.decode(encoded)

        assertEquals(capsule, decoded)
        assertEquals(returnAt, capsule.pauseUntil)
        assertTrue(capsule.reachesReturnPoint)
        assertEquals(64, capsule.fingerprint.length)
    }

    @Test
    fun codecRejectsChangedPayloadWithOldSeal() {
        val encoded = StoryReturnCapsuleCodec.encode(capsule())
        val tampered = encoded.replaceFirst("|OPEN|", "|MOVED|")

        assertNotEquals(encoded, tampered)
        assertNull(StoryReturnCapsuleCodec.decode(tampered))
    }

    @Test
    fun capsuleStaysSealedUntilActualPauseBoundary() {
        val capsule = capsule()
        val first = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule,
            currentMap = mapOf(openEntry()),
            now = now
        )
        val later = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule,
            currentMap = mapOf(openEntry()),
            now = returnAt - 1L
        )

        assertEquals(StoryReturnCapsuleState.SEALED, first.state)
        assertEquals(StoryReturnCapsuleState.SEALED, later.state)
        assertNull(first.currentEntry)
        assertEquals(first.fingerprint, later.fingerprint)
    }

    @Test
    fun twentyFourHourLimitNeverPretendsDistantPointWasReached() {
        val distantReturn = now + 7L * 24L * 60L * 60L * 1000L
        val capsule = capsule(distantReturn)
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule,
            currentMap = mapOf(
                openEntry(nextAt = distantReturn)
            ),
            now = capsule.pauseUntil
        )

        assertEquals(
            StoryReturnCapsuleState.LIMIT_REACHED,
            result.state
        )
        assertTrue(capsule.pauseUntil < capsule.returnAt)
        assertNull(result.currentEntry)
    }

    @Test
    fun reachedPointCanHonestlyKeepSameLocalVersion() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(openEntry(nextAt = null)),
            now = returnAt
        )

        assertEquals(
            StoryReturnCapsuleState.UNCHANGED,
            result.state
        )
        assertTrue(result.isOpenable)
    }

    @Test
    fun sameKindWithLaterTimeIsPublishedAsMovedPoint() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(
                openEntry(nextAt = returnAt + 60_000L)
            ),
            now = returnAt
        )

        assertEquals(
            StoryReturnCapsuleState.POINT_MOVED,
            result.state
        )
    }

    @Test
    fun activeSemanticTransitionIsChanged() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(movedEntry()),
            now = returnAt
        )

        assertEquals(StoryReturnCapsuleState.CHANGED, result.state)
        assertEquals(
            StoryThreadMapState.MOVED,
            result.currentEntry?.state
        )
    }

    @Test
    fun resolvedAndMissedRemainDistinctOutcomes() {
        val capsule = capsule()
        val resolved = StoryReturnCapsuleEngine.evaluate(
            capsule,
            mapOf(terminalEntry(StoryThreadMapState.RESOLVED)),
            returnAt
        )
        val missed = StoryReturnCapsuleEngine.evaluate(
            capsule,
            mapOf(terminalEntry(StoryThreadMapState.MISSED)),
            returnAt
        )

        assertEquals(StoryReturnCapsuleState.RESOLVED, resolved.state)
        assertEquals(StoryReturnCapsuleState.MISSED, missed.state)
    }

    @Test
    fun detachedEventDoesNotInventCurrentStatus() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule(),
            mapOf(detachedEntry()),
            returnAt
        )

        assertEquals(StoryReturnCapsuleState.DETACHED, result.state)
        assertTrue(!result.isOpenable)
        assertNull(result.currentEntry?.result)
    }

    @Test
    fun removedThreadAndDamagedThreadAreDifferentFailures() {
        val capsule = capsule()
        val missing = StoryReturnCapsuleEngine.evaluate(
            capsule,
            mapOf(),
            returnAt
        )
        val damaged = StoryReturnCapsuleEngine.evaluate(
            capsule,
            mapOf(tamperedEntry()),
            returnAt
        )

        assertEquals(StoryReturnCapsuleState.MISSING, missing.state)
        assertEquals(
            StoryReturnCapsuleState.CURRENT_TAMPERED,
            damaged.state
        )
        assertNull(missing.currentEntry)
        assertNotNull(damaged.currentEntry)
    }

    @Test
    fun fingerprintChangesOnlyAtMeaningfulBoundaries() {
        val capsule = capsule()
        val map = mapOf(openEntry())
        val sealed = StoryReturnCapsuleEngine.evaluate(
            capsule,
            map,
            now
        )
        val stillSealed = StoryReturnCapsuleEngine.evaluate(
            capsule,
            map,
            returnAt - 1L
        )
        val opened = StoryReturnCapsuleEngine.evaluate(
            capsule,
            mapOf(openEntry(nextAt = null)),
            returnAt
        )

        assertEquals(sealed.fingerprint, stillSealed.fingerprint)
        assertNotEquals(sealed.fingerprint, opened.fingerprint)
    }

    private fun capsule(
        target: Long = returnAt
    ): StoryReturnCapsule {
        val map = mapOf(openEntry(nextAt = target))
        val quiet = StoryQuietWindowEngine.evaluate(map, now)
        val pauseUntil = StoryQuietWindowPolicy.pauseUntil(now, target)
        return StoryReturnCapsuleFactory.create(
            quietWindow = quiet,
            activatedAt = now,
            pauseUntil = pauseUntil
        )
    }

    private fun openEntry(
        nextAt: Long? = returnAt
    ): StoryThreadMapEntry {
        val thread = thread()
        return StoryThreadMapEntry(
            eventId = thread.eventId,
            match = "Зенит - Краснодар",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = 0,
            state = StoryThreadMapState.OPEN,
            thread = thread,
            result = result(
                thread = thread,
                status = StoryThreadStatus.OPEN,
                fingerprint = "c".repeat(64)
            ),
            nextMoment = nextAt?.let(::moment)
        )
    }

    private fun movedEntry(): StoryThreadMapEntry {
        val thread = thread()
        return StoryThreadMapEntry(
            eventId = thread.eventId,
            match = "Зенит - Краснодар",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = 0,
            state = StoryThreadMapState.MOVED,
            thread = thread,
            result = result(
                thread = thread,
                status = StoryThreadStatus.MOVED,
                fingerprint = "d".repeat(64)
            ),
            nextMoment = null
        )
    }

    private fun terminalEntry(
        state: StoryThreadMapState
    ): StoryThreadMapEntry {
        require(
            state == StoryThreadMapState.RESOLVED ||
                state == StoryThreadMapState.MISSED
        )
        val thread = thread()
        val status = if (state == StoryThreadMapState.RESOLVED) {
            StoryThreadStatus.RESOLVED
        } else {
            StoryThreadStatus.MISSED
        }
        return StoryThreadMapEntry(
            eventId = thread.eventId,
            match = "Зенит - Краснодар",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = 0,
            state = state,
            thread = thread,
            result = result(
                thread = thread,
                status = status,
                fingerprint = if (
                    state == StoryThreadMapState.RESOLVED
                ) {
                    "e".repeat(64)
                } else {
                    "f".repeat(64)
                }
            ),
            nextMoment = null
        )
    }

    private fun detachedEntry(): StoryThreadMapEntry {
        val thread = thread()
        return StoryThreadMapEntry(
            eventId = thread.eventId,
            match = null,
            sport = null,
            region = null,
            catalogOrder = null,
            state = StoryThreadMapState.DETACHED,
            thread = thread,
            result = null,
            nextMoment = null
        )
    }

    private fun tamperedEntry(): StoryThreadMapEntry {
        return StoryThreadMapEntry(
            eventId = "rpl_zenit_krasnodar",
            match = "Зенит - Краснодар",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = 0,
            state = StoryThreadMapState.TAMPERED,
            thread = null,
            result = null,
            nextMoment = null
        )
    }

    private fun thread(): StoryThread {
        return StoryThread(
            eventId = "rpl_zenit_krasnodar",
            chapter = EventStoryChapter.FACTS,
            startedAt = now - 1_000L,
            initialState = EventStoryChapterState.ACTIVE,
            initialStoryFingerprint = "a".repeat(64),
            fingerprint = "b".repeat(64)
        )
    }

    private fun result(
        thread: StoryThread,
        status: StoryThreadStatus,
        fingerprint: String
    ): StoryThreadResult {
        val currentState = when (status) {
            StoryThreadStatus.OPEN ->
                EventStoryChapterState.ACTIVE
            StoryThreadStatus.MOVED ->
                EventStoryChapterState.ATTENTION
            StoryThreadStatus.RESOLVED ->
                EventStoryChapterState.COMPLETE
            StoryThreadStatus.MISSED ->
                EventStoryChapterState.MISSED
        }
        return StoryThreadResult(
            thread = thread,
            currentState = currentState,
            status = status,
            fingerprint = fingerprint
        )
    }

    private fun moment(at: Long): StoryBeaconMoment {
        return StoryBeaconMoment(
            kind = StoryBeaconMomentKind.CHECK_WINDOW,
            at = at,
            factors = listOf(SignalFactor.LOAD)
        )
    }

    private fun mapOf(
        vararg entries: StoryThreadMapEntry
    ): StoryThreadMapResult {
        val list = entries.toList()
        return StoryThreadMapResult(
            entries = list,
            leadingState = list.firstOrNull()?.state
                ?: StoryThreadMapState.EMPTY,
            tamperedCount = list.count {
                it.state == StoryThreadMapState.TAMPERED
            },
            detachedCount = list.count {
                it.state == StoryThreadMapState.DETACHED
            },
            movedCount = list.count {
                it.state == StoryThreadMapState.MOVED
            },
            missedCount = list.count {
                it.state == StoryThreadMapState.MISSED
            },
            openCount = list.count {
                it.state == StoryThreadMapState.OPEN
            },
            resolvedCount = list.count {
                it.state == StoryThreadMapState.RESOLVED
            },
            fingerprint = if (list.isEmpty()) {
                "0".repeat(64)
            } else {
                "1".repeat(64)
            }
        )
    }
}
