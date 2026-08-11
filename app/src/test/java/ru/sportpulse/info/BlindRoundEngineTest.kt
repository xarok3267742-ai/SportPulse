package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindRoundEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun onlyReadyFactExpressCanStartRound() {
        assertThrows(IllegalArgumentException::class.java) {
            BlindRoundEngine.prepare(result(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlindRoundEngine.prepare(result(5))
        }
    }

    @Test
    fun twoThreeAndFourCardsKeepStableCodes() {
        (2..4).forEach { size ->
            val session = BlindRoundEngine.prepare(result(size))

            assertEquals(size, session.cards.size)
            assertEquals(
                listOf("A", "B", "C", "D").take(size),
                session.cards.map(BlindRoundCard::code)
            )
            assertEquals(64, session.fingerprint.length)
        }
    }

    @Test
    fun everyBlindCardMovesAwayFromItsSourcePosition() {
        (2..4).forEach { size ->
            val source = result(size)
            val session = BlindRoundEngine.prepare(source)
            val reveal = BlindRoundEngine.reveal(
                session = session,
                result = source,
                selectedToken = session.cards.first().token
            )

            reveal.cards.forEachIndexed { index, card ->
                assertNotEquals(index + 1, card.sourceRank)
            }
        }
    }

    @Test
    fun preparationIsDeterministicForSameSource() {
        val source = result(4)

        assertEquals(
            BlindRoundEngine.prepare(source),
            BlindRoundEngine.prepare(source)
        )
    }

    @Test
    fun blindCardDoesNotRetainEventIdentityOrFullEntry() {
        val names = BlindRoundCard::class.java.declaredFields
            .map { it.name }
        val types = BlindRoundCard::class.java.declaredFields
            .map { it.type }

        assertTrue("eventId" !in names)
        assertTrue("match" !in names)
        assertTrue("sport" !in names)
        assertTrue("region" !in names)
        assertTrue("catalogOrder" !in names)
        assertTrue(FactExpressEntry::class.java !in types)
        assertTrue(FactExpressResult::class.java !in types)
    }

    @Test
    fun sourceZoneAndMembershipChangeSessionSeal() {
        val moscow = BlindRoundEngine.prepare(
            result(3, RegionalZone.MOSCOW)
        )
        val minsk = BlindRoundEngine.prepare(
            result(3, RegionalZone.MINSK)
        )
        val changed = BlindRoundEngine.prepare(
            result(3, RegionalZone.MOSCOW, prefix = "other")
        )

        assertNotEquals(moscow.fingerprint, minsk.fingerprint)
        assertNotEquals(moscow.fingerprint, changed.fingerprint)
    }

    @Test
    fun exactlyOneChoiceAlignsWithPublishedFactOrder() {
        val source = result(4)
        val session = BlindRoundEngine.prepare(source)
        val reveals = session.cards.map { card ->
            BlindRoundEngine.reveal(
                session = session,
                result = source,
                selectedToken = card.token
            )
        }

        assertEquals(
            1,
            reveals.count {
                it.alignment == BlindRoundAlignment.ALIGNED
            }
        )
        val aligned = reveals.single {
            it.alignment == BlindRoundAlignment.ALIGNED
        }
        assertEquals(aligned.selectedCode, aligned.firstByFactsCode)
        assertEquals(
            aligned.selectedCard.eventId,
            source.entries.first().eventId
        )
    }

    @Test
    fun differentChoiceIsDescriptiveNotFailure() {
        val source = result(3)
        val session = BlindRoundEngine.prepare(source)
        val reveal = session.cards.asSequence()
            .map { card ->
                BlindRoundEngine.reveal(
                    session = session,
                    result = source,
                    selectedToken = card.token
                )
            }
            .first {
                it.alignment == BlindRoundAlignment.DIFFERENT
            }

        assertNotEquals(reveal.selectedCode, reveal.firstByFactsCode)
        assertTrue(
            BlindRoundText.resultSummary(reveal)
                .contains("Это не ошибка")
        )
    }

    @Test
    fun staleTamperedAndUnknownSessionsFailClosed() {
        val source = result(3)
        val session = BlindRoundEngine.prepare(source)
        val selected = session.cards.first().token

        assertThrows(IllegalArgumentException::class.java) {
            BlindRoundEngine.reveal(
                session = session,
                result = result(3, prefix = "changed"),
                selectedToken = selected
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlindRoundEngine.reveal(
                session = session.copy(
                    fingerprint = "f".repeat(64)
                ),
                result = source,
                selectedToken = selected
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlindRoundEngine.reveal(
                session = session,
                result = source,
                selectedToken = "e".repeat(64)
            )
        }
    }

    @Test
    fun revealNamesAllCardsAndBindsTheChoice() {
        val source = result(4)
        val session = BlindRoundEngine.prepare(source)
        val first = BlindRoundEngine.reveal(
            session,
            source,
            session.cards[0].token
        )
        val second = BlindRoundEngine.reveal(
            session,
            source,
            session.cards[1].token
        )

        assertEquals(
            source.entries.map(FactExpressEntry::eventId).toSet(),
            first.cards.map(BlindRoundRevealCard::eventId).toSet()
        )
        assertEquals(source.fingerprint, first.sourceFingerprint)
        assertEquals(session.fingerprint, first.sessionFingerprint)
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    private fun result(
        size: Int,
        zone: RegionalZone = RegionalZone.MOSCOW,
        prefix: String = "event"
    ): FactExpressResult {
        return FactExpressEngine.evaluate(
            candidates = List(size) { index ->
                candidate("$prefix-$index", index)
            },
            selectedZone = zone,
            now = now
        )
    }

    private fun candidate(
        id: String,
        order: Int
    ): FactExpressCandidate {
        val label = "Матч $id"
        val nextAt = now + (order + 1L) * HOUR
        val story = EventStoryResult(
            eventId = id,
            eventLabel = label,
            sourceState = EventStorySourceState.DEMO,
            chapters = EventStoryChapter.values().map { chapter ->
                EventStoryChapterResult(
                    chapter = chapter,
                    state = EventStoryChapterState.ACTIVE,
                    summary = "Тестовая глава"
                )
            },
            phase = EventStoryPhase.PREPARING,
            action = EventStoryAction.OPEN_FACTS,
            actionFactor = SignalFactor.LOAD,
            startAt = now + 8L * HOUR,
            reviewOpensAt = now + 12L * HOUR,
            fingerprint = fingerprintFor(id, 'a')
        )
        val beacon = StoryBeaconResult(
            eventId = id,
            evaluatedAtMinute = now / 60_000L,
            state = StoryBeaconState.ACTION_NOW,
            moments = listOf(
                StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.ACTION_NOW,
                    at = null,
                    factors = listOf(SignalFactor.LOAD),
                    action = EventStoryAction.OPEN_FACTS
                ),
                StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.CHECK_WINDOW,
                    at = nextAt,
                    factors = listOf(SignalFactor.LOAD)
                )
            ),
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
