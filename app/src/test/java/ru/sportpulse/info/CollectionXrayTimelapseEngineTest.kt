package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionXrayTimelapseEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun boundarySelectionsNeverProducePartialFrames() {
        listOf(
            emptyList(),
            listOf(candidate("one", 0)),
            List(9) { candidate("event-$it", it) }
        ).forEach { candidates ->
            val result = CollectionXrayTimelapseEngine.evaluate(
                candidates = candidates,
                now = now
            )

            assertEquals(
                CollectionXrayTimelapseState.NOT_AVAILABLE,
                result.state
            )
            assertEquals(candidates.size, result.candidateCount)
            assertEquals(emptyList<CollectionXrayTimelapseFrame>(), result.frames)
        }
    }

    @Test
    fun fixedFramesShareOneBaselineAndOpenOrder() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate("later", 7),
                candidate("first", 2)
            ),
            now = now
        )

        assertEquals(
            CollectionXrayTimelapseHorizon.values().toList(),
            result.frames.map(CollectionXrayTimelapseFrame::horizon)
        )
        assertEquals(
            listOf("first", "later"),
            result.baseline.entries.map(CollectionXrayEntry::eventId)
        )
        assertEquals(result.baseline, result.frames.first().xray)
        assertEquals(
            listOf(0L, 6L, 12L, 24L),
            result.frames.map { (it.evaluatedAt - now) / HOUR }
        )
    }

    @Test
    fun unconfirmedEvidenceCannotDecayFurther() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate(
                    "a",
                    0,
                    level = EvidenceLevel.UNCONFIRMED
                ),
                candidate(
                    "b",
                    1,
                    level = EvidenceLevel.UNCONFIRMED
                )
            ),
            now = now
        )

        assertEquals(CollectionXrayTimelapseState.STABLE, result.state)
        assertTrue(result.frames.all { it.changes.isEmpty() })
        assertTrue(result.frames.all { it.focus == null })
    }

    @Test
    fun quorumAtModerateScoreOnlyBreaksAtActualCaps() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate("a", 0, score = 50),
                candidate("b", 1, score = 50)
            ),
            now = now
        )

        assertEquals(
            CollectionXrayTimelapseState.STABLE,
            result.frame(
                CollectionXrayTimelapseHorizon.PLUS_6_HOURS
            ).state
        )
        assertEquals(
            CollectionXrayTimelapseState.VERDICT_SHIFT,
            result.frame(
                CollectionXrayTimelapseHorizon.PLUS_12_HOURS
            ).state
        )
        val final = result.frame(
            CollectionXrayTimelapseHorizon.PLUS_24_HOURS
        )
        assertEquals(
            CollectionXrayTimelapseState.VERDICT_SHIFT,
            final.state
        )
        assertEquals(4, final.newGapCellCount)
        assertEquals(2, final.newlyShiftedEventCount)
        assertTrue(final.changes.all {
            it.factor == SignalFactor.LINEUP ||
                it.factor == SignalFactor.SOURCES
        })
    }

    @Test
    fun singleSourceLineupDropsAtSixHours() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate(
                    "a",
                    0,
                    score = 50,
                    level = EvidenceLevel.SINGLE_SOURCE,
                    audit = SourceAuditState.UNAUDITED
                ),
                candidate(
                    "b",
                    1,
                    score = 50,
                    level = EvidenceLevel.SINGLE_SOURCE,
                    audit = SourceAuditState.UNAUDITED
                )
            ),
            now = now
        )
        val frame = result.frame(
            CollectionXrayTimelapseHorizon.PLUS_6_HOURS
        )

        assertEquals(2, frame.changes.size)
        assertTrue(frame.changes.all {
            it.factor == SignalFactor.LINEUP &&
                it.beforeSupportedScore == 50 &&
                it.afterSupportedScore == 25 &&
                it.cause == CollectionXrayGapCause.FRESHNESS_LOSS
        })
    }

    @Test
    fun focusPrefersNewVerdictShiftThenCatalogAndFactor() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate("later", 4, score = 50),
                candidate("earlier", 2, score = 50)
            ),
            now = now
        )
        val focus = result.frame(
            CollectionXrayTimelapseHorizon.PLUS_24_HOURS
        ).focus

        assertEquals("earlier", focus?.eventId)
        assertEquals(SignalFactor.LINEUP, focus?.factor)
        assertTrue(focus?.causesNewVerdictShift == true)
        assertEquals(
            CollectionXrayTimelapseChangeKind.NEW_CRITICAL,
            focus?.kind
        )
    }

    @Test
    fun everyFutureFrameIsMonotonicFromBaseline() {
        val result = CollectionXrayTimelapseEngine.evaluate(
            candidates = listOf(
                candidate("a", 0, score = 82),
                candidate("b", 1, score = 68)
            ),
            now = now
        )

        result.frames.drop(1).forEach { frame ->
            result.baseline.entries.zip(frame.xray.entries).forEach {
                    (before, after) ->
                assertTrue(
                    after.supportedReadiness <= before.supportedReadiness
                )
                before.cells.zip(after.cells).forEach {
                        (beforeCell, afterCell) ->
                    assertTrue(
                        afterCell.supportedScore <=
                            beforeCell.supportedScore
                    )
                }
            }
        }
    }

    @Test
    fun timeAndInputChangesAreSealed() {
        val source = listOf(
            candidate("a", 0, score = 50),
            candidate("b", 1, score = 50)
        )
        val baseline = CollectionXrayTimelapseEngine.evaluate(source, now)
        val nextMinute = CollectionXrayTimelapseEngine.evaluate(
            source,
            now + 60_000L
        )
        val changed = CollectionXrayTimelapseEngine.evaluate(
            listOf(
                candidate("a", 0, score = 51),
                source[1]
            ),
            now
        )

        assertNotEquals(baseline.fingerprint, nextMinute.fingerprint)
        assertNotEquals(baseline.fingerprint, changed.fingerprint)
        assertEquals(64, baseline.fingerprint.length)
    }

    @Test
    fun impossibleClockOverflowFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionXrayTimelapseEngine.evaluate(
                candidates = listOf(
                    candidate("a", 0),
                    candidate("b", 1)
                ),
                now = Long.MAX_VALUE - HOUR
            )
        }
    }

    private fun candidate(
        id: String,
        order: Int,
        score: Int = 70,
        level: EvidenceLevel = EvidenceLevel.QUORUM,
        audit: SourceAuditState = SourceAuditState.INDEPENDENT
    ): CollectionXrayCandidate {
        return CollectionXrayCandidate(
            eventId = id,
            match = "Матч $id",
            sport = "Футбол",
            region = "Россия",
            catalogOrder = order,
            assessment = SignalAssessment(
                List(SignalFactor.values().size) { score }
            ),
            claimedEvidence = EvidenceAssessment(
                List(SignalFactor.values().size) { level }
            ),
            sourceAudit = SourceAuditAssessment(
                List(SignalFactor.values().size) { audit }
            ),
            timeline = EvidenceTimeline(
                List(SignalFactor.values().size) { now }
            )
        )
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
    }
}
