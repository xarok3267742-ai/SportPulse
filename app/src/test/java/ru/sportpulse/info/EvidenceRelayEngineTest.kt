package ru.sportpulse.info

import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceRelayEngineTest {
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val minute = 60_000L
    private val monday = instant("2026-08-03T10:00:00Z")

    @Test
    fun exactFutureStartHasPriority() {
        val event = event(
            startAt = monday + 8L * hour,
            schedule = DemoSchedule(
                DayOfWeek.FRIDAY,
                20,
                0
            )
        )

        val result = EventStartResolver.resolve(event, monday)

        assertEquals(monday + 8L * hour, result?.startAt)
        assertEquals(EventStartSource.EVENT_PACK, result?.source)
    }

    @Test
    fun pastExactStartDoesNotFallBackToDemo() {
        val event = event(
            startAt = monday - minute,
            schedule = DemoSchedule(
                DayOfWeek.FRIDAY,
                20,
                0
            )
        )

        assertNull(EventStartResolver.resolve(event, monday))
    }

    @Test
    fun futureDemoStartUsesCurrentMoscowWeek() {
        val event = event(
            schedule = DemoSchedule(
                DayOfWeek.WEDNESDAY,
                19,
                30
            )
        )

        val result = EventStartResolver.resolve(event, monday)

        assertEquals(
            instant("2026-08-05T16:30:00Z"),
            result?.startAt
        )
        assertEquals(
            EventStartSource.DEMO_SCHEDULE,
            result?.source
        )
    }

    @Test
    fun elapsedDemoStartRollsToNextWeek() {
        val afterKickoff = instant("2026-08-05T17:00:00Z")
        val event = event(
            schedule = DemoSchedule(
                DayOfWeek.WEDNESDAY,
                19,
                30
            )
        )

        val result = EventStartResolver.resolve(
            event,
            afterKickoff
        )

        assertEquals(
            instant("2026-08-12T16:30:00Z"),
            result?.startAt
        )
    }

    @Test
    fun eventWithoutScheduleHasNoRelay() {
        val input = input(event = event())

        assertNull(EvidenceRelayEngine.evaluate(input, monday))
    }

    @Test
    fun lineupExpiresBeforeEightHourStart() {
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + 8L * hour
                    )
                ),
                monday
            )
        )
        val lineup = result.factors[SignalFactor.LINEUP.ordinal]

        assertEquals(
            EvidenceRelayFactorState.RECHECK_REQUIRED,
            lineup.state
        )
        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            lineup.startLevel
        )
        assertEquals(
            monday + 6L * hour,
            lineup.firstTransitionAt
        )
        assertEquals(
            monday + 2L * hour + minute,
            lineup.safeRecheckAt
        )
        assertEquals(SignalFactor.LINEUP, result.priorityFactor)
    }

    @Test
    fun formSurvivesFortyEightHourStart() {
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + 48L * hour
                    )
                ),
                monday
            )
        )
        val form = result.factors[SignalFactor.FORM.ordinal]

        assertEquals(
            EvidenceRelayFactorState.SURVIVES,
            form.state
        )
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            form.startLevel
        )
        assertNull(form.safeRecheckAt)
    }

    @Test
    fun sourceAuditCapsQuorumBeforeRelay() {
        val claimed = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        val audit = SourceAuditAssessment(
            listOf(
                SourceAuditState.SHARED_LINEAGE,
                SourceAuditState.INDEPENDENT,
                SourceAuditState.INDEPENDENT,
                SourceAuditState.INDEPENDENT,
                SourceAuditState.INDEPENDENT
            )
        )
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + hour
                    ),
                    evidence = claimed,
                    audit = audit
                ),
                monday
            )
        )

        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            result.factors[SignalFactor.FORM.ordinal].sourceLevel
        )
    }

    @Test
    fun readinessDropGetsExplicitState() {
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + 13L * hour
                    ),
                    assessment = SignalAssessment(List(5) { 90 }),
                    evidence = EvidenceAssessment(
                        List(5) { EvidenceLevel.QUORUM }
                    ),
                    audit = SourceAuditAssessment(
                        List(5) {
                            SourceAuditState.INDEPENDENT
                        }
                    )
                ),
                monday
            )
        )

        assertEquals(
            SignalVerdict.READY,
            result.currentEvidenceResult.effectiveSignal.verdict
        )
        assertEquals(
            SignalVerdict.OBSERVE,
            result.startEvidenceResult.effectiveSignal.verdict
        )
        assertEquals(
            EvidenceRelayState.READINESS_DROP,
            result.state
        )
    }

    @Test
    fun unconfirmedFactorHasNoInventedRecheckWindow() {
        val evidence = EvidenceAssessment(
            listOf(
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.SINGLE_SOURCE
            )
        )
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + 8L * hour
                    ),
                    evidence = evidence
                ),
                monday
            )
        )
        val form = result.factors[SignalFactor.FORM.ordinal]

        assertEquals(
            EvidenceRelayFactorState.UNCONFIRMED,
            form.state
        )
        assertNull(form.safeRecheckAt)
    }

    @Test
    fun allUnconfirmedHasDedicatedState() {
        val result = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event(
                        startAt = monday + hour
                    ),
                    evidence = EvidenceAssessment(
                        List(5) { EvidenceLevel.UNCONFIRMED }
                    )
                ),
                monday
            )
        )

        assertEquals(
            EvidenceRelayState.NO_CONFIRMED_FACTS,
            result.state
        )
        assertEquals(5, result.unconfirmedCount)
        assertNull(result.priorityFactor)
    }

    @Test
    fun sameMinuteIsDeterministicAndStartChangesFingerprint() {
        val event = event(startAt = monday + 8L * hour)
        val input = input(event = event)
        val first = requireNotNull(
            EvidenceRelayEngine.evaluate(input, monday)
        )
        val sameMinute = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input,
                monday + 30_000L
            )
        )
        val changed = requireNotNull(
            EvidenceRelayEngine.evaluate(
                input(
                    event = event.copy(
                        startAt = monday + 9L * hour
                    )
                ),
                monday
            )
        )

        assertEquals(first.fingerprint, sameMinute.fingerprint)
        assertNotEquals(first.fingerprint, changed.fingerprint)
        assertTrue(
            Regex("[0-9a-f]{64}").matches(first.fingerprint)
        )
    }

    @Test
    fun evaluationDoesNotMutateInput() {
        val timeline = EvidenceTimeline(List(5) { monday })
        val input = input(
            event = event(startAt = monday + 8L * hour),
            timeline = timeline
        )

        EvidenceRelayEngine.evaluate(input, monday)

        assertEquals(List(5) { monday }, timeline.checkedAt)
        assertFalse(input.assessment.values.any { it != 80 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeNowIsRejected() {
        EventStartResolver.resolve(event(), -1L)
    }

    private fun input(
        event: SportEvent,
        assessment: SignalAssessment =
            SignalAssessment(List(5) { 80 }),
        evidence: EvidenceAssessment =
            EvidenceAssessment.singleSource(),
        audit: SourceAuditAssessment =
            SourceAuditAssessment.unaudited(),
        timeline: EvidenceTimeline =
            EvidenceTimeline(List(5) { monday })
    ): EvidenceRelayInput {
        return EvidenceRelayInput(
            event = event,
            assessment = assessment,
            claimedEvidence = evidence,
            sourceAudit = audit,
            timeline = timeline
        )
    }

    private fun event(
        startAt: Long? = null,
        schedule: DemoSchedule? = null
    ): SportEvent {
        return SportEvent(
            id = "relay_event",
            sport = "Футбол",
            tournament = "Тест",
            region = "СНГ",
            match = "Событие",
            time = "по расписанию",
            focus = "Факты",
            note = "Тест",
            tags = emptyList(),
            imageRes = 0,
            seedAssessment = SignalAssessment(List(5) { 80 }),
            startAt = startAt,
            demoSchedule = schedule
        )
    }

    private fun instant(value: String): Long {
        return Instant.parse(value).toEpochMilli()
    }
}
