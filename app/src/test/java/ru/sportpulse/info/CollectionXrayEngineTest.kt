package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionXrayEngineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun emptySingleAndOversizedSelectionsStayExplicit() {
        assertEquals(
            CollectionXrayState.EMPTY,
            CollectionXrayEngine.evaluate(emptyList(), now).state
        )
        assertEquals(
            CollectionXrayState.NEED_MORE,
            CollectionXrayEngine.evaluate(listOf(candidate("one", 0)), now).state
        )
        val oversized = CollectionXrayEngine.evaluate(
            List(9) { candidate("event-$it", it) },
            now
        )

        assertEquals(CollectionXrayState.TOO_MANY, oversized.state)
        assertEquals(9, oversized.candidateCount)
        assertEquals(emptyList<CollectionXrayEntry>(), oversized.entries)
        assertNull(oversized.focus)
    }

    @Test
    fun fullySupportedSelectionIsClearWithoutArtificialFocus() {
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate(
                    id = "a",
                    order = 0,
                    score = 72,
                    level = EvidenceLevel.QUORUM,
                    audit = SourceAuditState.INDEPENDENT
                ),
                candidate(
                    id = "b",
                    order = 1,
                    score = 54,
                    level = EvidenceLevel.QUORUM,
                    audit = SourceAuditState.INDEPENDENT
                )
            ),
            now
        )

        assertEquals(CollectionXrayState.CLEAR, result.state)
        assertNull(result.focus)
        assertNull(result.leadingFactor)
        assertTrue(
            result.entries.flatMap(CollectionXrayEntry::cells).all {
                it.state == CollectionXrayCellState.SUPPORTED
            }
        )
    }

    @Test
    fun containedEvidenceGapsDoNotBecomeVerdictShift() {
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate("a", 0, score = 70),
                candidate("b", 1, score = 68)
            ),
            now
        )

        assertEquals(CollectionXrayState.GAPS, result.state)
        assertTrue(result.focus?.unsupportedPoints ?: 0 > 0)
        assertTrue(result.entries.all {
            it.shadowStatus == ConfidenceShadowStatus.CONTAINED
        })
        assertTrue(result.entries.flatMap(CollectionXrayEntry::cells).none {
            it.state == CollectionXrayCellState.CRITICAL
        })
    }

    @Test
    fun unsupportedPartCanChangeVerdictAndMarksOneCellPerEvent() {
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate("a", 0, score = 90),
                candidate("b", 1, score = 88)
            ),
            now
        )

        assertEquals(CollectionXrayState.VERDICT_SHIFT, result.state)
        assertEquals(
            2,
            result.entries.flatMap(CollectionXrayEntry::cells).count {
                it.state == CollectionXrayCellState.CRITICAL
            }
        )
        assertEquals(CollectionXrayCellState.CRITICAL, result.focus?.state)
        assertTrue(result.entries.all {
            it.claimedVerdict != it.supportedVerdict
        })
    }

    @Test
    fun sourceConflictHasPriorityAsGapCause() {
        val audits = SourceAuditAssessment(
            SignalFactor.values().map { factor ->
                if (factor == SignalFactor.FORM) {
                    SourceAuditState.CONFLICT
                } else {
                    SourceAuditState.INDEPENDENT
                }
            }
        )
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate(
                    "a",
                    0,
                    score = 70,
                    level = EvidenceLevel.QUORUM,
                    audits = audits
                ),
                candidate(
                    "b",
                    1,
                    score = 70,
                    level = EvidenceLevel.QUORUM,
                    audit = SourceAuditState.INDEPENDENT
                )
            ),
            now
        )
        val cell = result.entries.first().cells[SignalFactor.FORM.ordinal]

        assertEquals(CollectionXrayGapCause.SOURCE_CONFLICT, cell.cause)
        assertEquals(EvidenceLevel.UNCONFIRMED, cell.effectiveEvidence)
    }

    @Test
    fun freshnessLossIsSeparatedFromSourceLimit() {
        val checkedAt = SignalFactor.values().map { factor ->
            if (factor == SignalFactor.FORM) {
                now - 73L * HOUR
            } else {
                now
            }
        }
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate(
                    "a",
                    0,
                    score = 70,
                    level = EvidenceLevel.QUORUM,
                    audit = SourceAuditState.INDEPENDENT,
                    checkedAt = checkedAt
                ),
                candidate(
                    "b",
                    1,
                    score = 70,
                    level = EvidenceLevel.QUORUM,
                    audit = SourceAuditState.INDEPENDENT
                )
            ),
            now
        )
        val cell = result.entries.first().cells[SignalFactor.FORM.ordinal]

        assertEquals(CollectionXrayGapCause.FRESHNESS_LOSS, cell.cause)
        assertEquals(EvidenceLevel.SINGLE_SOURCE, cell.effectiveEvidence)
    }

    @Test
    fun unauditedQuorumAndSharedLineageRemainDistinct() {
        val unaudited = candidate(
            "a",
            0,
            score = 75,
            level = EvidenceLevel.QUORUM,
            audit = SourceAuditState.UNAUDITED
        )
        val shared = candidate(
            "b",
            1,
            score = 75,
            level = EvidenceLevel.QUORUM,
            audit = SourceAuditState.SHARED_LINEAGE
        )
        val result = CollectionXrayEngine.evaluate(
            listOf(unaudited, shared),
            now
        )

        assertEquals(
            CollectionXrayGapCause.UNAUDITED_QUORUM,
            result.entries[0].cells.first().cause
        )
        assertEquals(
            CollectionXrayGapCause.SHARED_LINEAGE,
            result.entries[1].cells.first().cause
        )
    }

    @Test
    fun entriesUseCatalogOrderRatherThanInputOrder() {
        val first = candidate("first", 1, score = 70)
        val second = candidate("second", 5, score = 70)
        val normal = CollectionXrayEngine.evaluate(
            listOf(first, second),
            now
        )
        val reversed = CollectionXrayEngine.evaluate(
            listOf(second, first),
            now
        )

        assertEquals(listOf("first", "second"), normal.entries.map { it.eventId })
        assertEquals(normal, reversed)
    }

    @Test
    fun focusTieBreaksByCatalogThenFactorOrder() {
        val result = CollectionXrayEngine.evaluate(
            listOf(
                candidate("later", 4, score = 90),
                candidate("earlier", 2, score = 90)
            ),
            now
        )

        assertEquals("earlier", result.focus?.eventId)
        assertEquals(SignalFactor.FORM, result.focus?.factor)
        assertEquals(SignalFactor.FORM, result.leadingFactor?.factor)
    }

    @Test
    fun inputTamperingAndTimeChangeFingerprint() {
        val source = listOf(
            candidate("a", 0, score = 70),
            candidate("b", 1, score = 70)
        )
        val baseline = CollectionXrayEngine.evaluate(source, now)
        val changedScore = CollectionXrayEngine.evaluate(
            listOf(
                candidate("a", 0, score = 71),
                source[1]
            ),
            now
        )
        val nextMinute = CollectionXrayEngine.evaluate(
            source,
            now + 60_000L
        )

        assertNotEquals(baseline.fingerprint, changedScore.fingerprint)
        assertNotEquals(baseline.fingerprint, nextMinute.fingerprint)
        assertEquals(64, baseline.fingerprint.length)
    }

    @Test
    fun duplicateIdentityAndCatalogOrderFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionXrayEngine.evaluate(
                listOf(candidate("same", 0), candidate("same", 1)),
                now
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionXrayEngine.evaluate(
                listOf(candidate("a", 0), candidate("b", 0)),
                now
            )
        }
    }

    private fun candidate(
        id: String,
        order: Int,
        score: Int = 70,
        level: EvidenceLevel = EvidenceLevel.SINGLE_SOURCE,
        audit: SourceAuditState = SourceAuditState.UNAUDITED,
        audits: SourceAuditAssessment = SourceAuditAssessment(
            List(SignalFactor.values().size) { audit }
        ),
        checkedAt: List<Long> = List(SignalFactor.values().size) { now }
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
            sourceAudit = audits,
            timeline = EvidenceTimeline(checkedAt)
        )
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
    }
}
