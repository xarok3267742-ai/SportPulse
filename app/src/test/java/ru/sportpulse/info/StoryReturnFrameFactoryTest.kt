package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryReturnFrameFactoryTest {
    private val now = 1_785_840_000_000L
    private val returnAt = now + 60L * 60L * 1000L

    @Test
    fun sealedCapsuleCannotBecomeShareableFrame() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(openEntry()),
            now = now
        )

        assertThrows(IllegalArgumentException::class.java) {
            StoryReturnFrameFactory.create(
                result = result,
                selectedZone = RegionalZone.MOSCOW,
                generatedAt = now
            )
        }
    }

    @Test
    fun openedFrameKeepsOnlyMinimalReturnSnapshot() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(openEntry(nextAt = null)),
            now = returnAt
        )
        val frame = StoryReturnFrameFactory.create(
            result = result,
            selectedZone = RegionalZone.MOSCOW,
            generatedAt = returnAt
        )

        assertEquals(StoryReturnCapsuleState.UNCHANGED, frame.outcome)
        assertEquals(StoryThreadMapState.OPEN, frame.beforeState)
        assertEquals(StoryThreadMapState.OPEN, frame.currentState)
        assertNull(frame.movedPoint)
        assertEquals(result.fingerprint, frame.resultFingerprint)
        assertEquals(64, frame.fingerprint.length)
        val retainedTypes = StoryReturnFrame::class.java.declaredFields
            .map { it.type }
        assertTrue(StoryReturnCapsuleResult::class.java !in retainedTypes)
        assertTrue(StoryThreadMapResult::class.java !in retainedTypes)
        assertTrue(StoryThreadMapEntry::class.java !in retainedTypes)
    }

    @Test
    fun limitFrameNeverClaimsThatOriginalPointWasReached() {
        val distantReturn = now + 7L * DAY
        val capsule = capsule(distantReturn)
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule,
            currentMap = mapOf(openEntry(nextAt = distantReturn)),
            now = capsule.pauseUntil
        )
        val frame = StoryReturnFrameFactory.create(
            result = result,
            selectedZone = RegionalZone.MOSCOW,
            generatedAt = capsule.pauseUntil
        )

        assertEquals(
            StoryReturnCapsuleState.LIMIT_REACHED,
            frame.outcome
        )
        assertNull(frame.currentState)
        assertEquals(
            "НЕ ВСКРЫТО",
            StoryReturnFrameFactory.currentStateTitle(frame)
        )
        val text = StoryReturnFrameFactory.shareText(frame)
        assertTrue(text.contains("точка еще впереди"))
        assertTrue(text.contains("сейчас: не вскрыто"))
    }

    @Test
    fun movedPointIsCopiedAsASealedAbsolutePoint() {
        val nextAt = returnAt + 60_000L
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(openEntry(nextAt = nextAt)),
            now = returnAt
        )
        val frame = StoryReturnFrameFactory.create(
            result = result,
            selectedZone = RegionalZone.MOSCOW,
            generatedAt = returnAt
        )

        assertEquals(StoryReturnCapsuleState.POINT_MOVED, frame.outcome)
        assertEquals(nextAt, frame.movedPoint?.at)
        assertEquals(
            StoryBeaconMomentKind.CHECK_WINDOW,
            frame.movedPoint?.kind
        )
        assertEquals(listOf(SignalFactor.LOAD), frame.movedPoint?.factors)
    }

    @Test
    fun frameFingerprintBindsExportTimeAndRegionalZone() {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(),
            currentMap = mapOf(openEntry(nextAt = null)),
            now = returnAt
        )
        val moscow = StoryReturnFrameFactory.create(
            result,
            RegionalZone.MOSCOW,
            returnAt
        )
        val later = StoryReturnFrameFactory.create(
            result,
            RegionalZone.MOSCOW,
            returnAt + 1L
        )
        val kaliningrad = StoryReturnFrameFactory.create(
            result,
            RegionalZone.KALININGRAD,
            returnAt
        )

        assertNotEquals(moscow.fingerprint, later.fingerprint)
        assertNotEquals(moscow.fingerprint, kaliningrad.fingerprint)
    }

    @Test
    fun fileNameIsStableAndFilesystemSafe() {
        val frame = openedFrame(
            eventId = "return/event with spaces",
            generatedAt = returnAt
        )

        assertEquals(
            "sport_pulse_return_return_event_with_spaces_" +
                "${returnAt}.png",
            StoryReturnFrameFactory.fileName(frame)
        )
    }

    @Test
    fun shareTextCarriesPointTransitionSealAndDisclaimer() {
        val frame = openedFrame()
        val text = StoryReturnFrameFactory.shareText(frame)

        assertTrue(text.contains("Хватит ли подтвержденных фактов?"))
        assertTrue(text.contains("окно проверки: нагрузка"))
        assertTrue(text.contains("Тогда: открыта"))
        assertTrue(text.contains("сейчас: открыта"))
        assertTrue(text.contains(frame.shortFingerprint))
        assertTrue(text.contains("не прогноз"))
        assertTrue(text.contains("не ставка"))
        assertTrue(text.contains("не подтверждение внешнего результата"))
    }

    private fun openedFrame(
        eventId: String = "rpl_zenit_krasnodar",
        generatedAt: Long = returnAt
    ): StoryReturnFrame {
        val result = StoryReturnCapsuleEngine.evaluate(
            capsule = capsule(eventId = eventId),
            currentMap = mapOf(
                openEntry(eventId = eventId, nextAt = null)
            ),
            now = returnAt
        )
        return StoryReturnFrameFactory.create(
            result = result,
            selectedZone = RegionalZone.MOSCOW,
            generatedAt = generatedAt
        )
    }

    private fun capsule(
        target: Long = returnAt,
        eventId: String = "rpl_zenit_krasnodar"
    ): StoryReturnCapsule {
        val map = mapOf(openEntry(eventId = eventId, nextAt = target))
        val quiet = StoryQuietWindowEngine.evaluate(map, now)
        return StoryReturnCapsuleFactory.create(
            quietWindow = quiet,
            activatedAt = now,
            pauseUntil = StoryQuietWindowPolicy.pauseUntil(now, target)
        )
    }

    private fun openEntry(
        eventId: String = "rpl_zenit_krasnodar",
        nextAt: Long? = returnAt
    ): StoryThreadMapEntry {
        val thread = thread(eventId)
        return StoryThreadMapEntry(
            eventId = eventId,
            match = "Зенит - Краснодар",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = 0,
            state = StoryThreadMapState.OPEN,
            thread = thread,
            result = StoryThreadResult(
                thread = thread,
                currentState = EventStoryChapterState.ACTIVE,
                status = StoryThreadStatus.OPEN,
                fingerprint = "c".repeat(64)
            ),
            nextMoment = nextAt?.let {
                StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.CHECK_WINDOW,
                    at = it,
                    factors = listOf(SignalFactor.LOAD)
                )
            }
        )
    }

    private fun thread(eventId: String): StoryThread {
        return StoryThread(
            eventId = eventId,
            chapter = EventStoryChapter.FACTS,
            startedAt = now - 1_000L,
            initialState = EventStoryChapterState.ACTIVE,
            initialStoryFingerprint = "a".repeat(64),
            fingerprint = "b".repeat(64)
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
            tamperedCount = 0,
            detachedCount = 0,
            movedCount = 0,
            missedCount = 0,
            openCount = list.size,
            resolvedCount = 0,
            fingerprint = if (list.isEmpty()) {
                "0".repeat(64)
            } else {
                "1".repeat(64)
            }
        )
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
