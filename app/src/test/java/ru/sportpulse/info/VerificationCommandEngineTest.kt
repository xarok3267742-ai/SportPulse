package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationCommandEngineTest {
    private val hour = FreshnessPolicy.HOUR_MILLIS
    private val now = 1_800_000_000_000L

    @Test
    fun sourceConflictCreatesStopTaskForTheExactFactor() {
        val result = evaluate(
            input = input(
                evidence = levels(EvidenceLevel.SINGLE_SOURCE),
                audit = audits(SourceAuditState.UNAUDITED)
                    .withState(
                        SignalFactor.FORM,
                        SourceAuditState.CONFLICT
                    )
            )
        )

        val task = result.tasks.first()
        assertEquals(VerificationCommandStatus.STOP, result.status)
        assertEquals(VerificationCommandPriority.STOP, task.priority)
        assertEquals(SignalFactor.FORM, task.factor)
        assertTrue(
            VerificationCommandKind.SOURCE_CONFLICT in task.kinds
        )
        assertTrue(
            VerificationCommandModule.SOURCES in task.modules
        )
        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            result.freshness.effectiveEvidence.level(
                SignalFactor.FORM
            )
        )
    }

    @Test
    fun guardBreachAlwaysLeadsTheStopTier() {
        val baselineAssessment = assessment(86)
        val baselineEvidence = levels(EvidenceLevel.QUORUM)
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "event-1",
            decision = SavedDecision.DATA_READY,
            savedAt = now - 1_000L,
            assessment = baselineAssessment,
            evidence = baselineEvidence,
            timeline = timeline(now - 1_000L),
            counterReview = CounterReviewAssessment.cleared()
        )
        val result = evaluate(
            input = input(
                assessment = assessment(20),
                evidence = baselineEvidence,
                audit = audits(SourceAuditState.INDEPENDENT),
                timeline = timeline(now - 1_000L),
                snapshot = snapshot
            )
        )

        assertEquals(VerificationCommandStatus.STOP, result.status)
        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            result.decisionGuard?.status
        )
        assertEquals(
            VerificationCommandKind.GUARD_BREACH,
            result.tasks.first().kinds.first()
        )
        assertTrue(
            VerificationCommandModule.GUARD in
                result.tasks.first().modules
        )
    }

    @Test
    fun storedGuardBreachRemainsFirstAfterLiveRecovery() {
        val baselineAssessment = assessment(86)
        val baselineEvidence = levels(EvidenceLevel.QUORUM)
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "event-1",
            decision = SavedDecision.DATA_READY,
            savedAt = now - 1_000L,
            assessment = baselineAssessment,
            evidence = baselineEvidence,
            timeline = timeline(now - 1_000L),
            counterReview = CounterReviewAssessment.cleared()
        )
        val triggered = evaluate(
            input = input(
                assessment = assessment(20),
                evidence = baselineEvidence,
                audit = audits(SourceAuditState.INDEPENDENT),
                timeline = timeline(now - 1_000L),
                snapshot = snapshot
            )
        )
        val breach = DecisionGuardBreachFactory.create(
            result = requireNotNull(triggered.decisionGuard),
            triggeredAt = now
        )

        val recovered = evaluate(
            input = input(
                assessment = baselineAssessment,
                evidence = baselineEvidence,
                audit = audits(SourceAuditState.INDEPENDENT),
                timeline = timeline(now - 1_000L),
                snapshot = snapshot,
                breach = breach
            )
        )

        assertEquals(VerificationCommandStatus.STOP, recovered.status)
        assertEquals(
            DecisionGuardStatus.TRIGGERED,
            recovered.decisionGuard?.status
        )
        assertTrue(
            recovered.decisionGuard?.isRecoveredAfterBreach == true
        )
        assertEquals(
            VerificationCommandKind.GUARD_BREACH,
            recovered.tasks.first().kinds.first()
        )
    }

    @Test
    fun unauditedQuorumIsRepairedBeforeOtherWork() {
        val result = evaluate(
            input = input(
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.UNAUDITED)
            )
        )

        assertEquals(
            VerificationCommandStatus.ATTENTION,
            result.status
        )
        assertEquals(
            VerificationCommandPriority.REPAIR,
            result.tasks.first().priority
        )
        assertEquals(5, result.tasks.count {
            VerificationCommandKind.SOURCE_AUDIT in it.kinds
        })
        assertEquals(3, result.visibleTasks.size)
    }

    @Test
    fun earliestFreshnessDeadlineWinsInsideRefreshTier() {
        val timestamps = EvidenceTimeline(
            listOf(
                now,
                now - 5L * hour,
                now - 25L * hour,
                now,
                now - 10L * hour
            )
        )
        val result = evaluate(
            input = input(
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.INDEPENDENT),
                timeline = timestamps
            )
        )
        val refreshTasks = result.tasks.filter {
            it.priority == VerificationCommandPriority.REFRESH
        }

        assertTrue(refreshTasks.isNotEmpty())
        assertEquals(SignalFactor.LINEUP, refreshTasks.first().factor)
        assertEquals(now + hour, refreshTasks.first().dueAt)
        assertTrue(
            VerificationCommandKind.EVIDENCE_EXPIRING in
                refreshTasks.first().kinds
        )
    }

    @Test
    fun sourceConflictAndCounterfactMergeIntoOneAction() {
        val review = CounterReviewAssessment.cleared().withState(
            SignalFactor.SOURCES,
            CounterReviewState.REFUTED
        )
        val result = evaluate(
            input = input(
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.INDEPENDENT)
                    .withState(
                        SignalFactor.SOURCES,
                        SourceAuditState.CONFLICT
                    ),
                review = review
            )
        )
        val matching = result.tasks.filter {
            it.priority == VerificationCommandPriority.STOP &&
                it.factor == SignalFactor.SOURCES
        }

        assertEquals(1, matching.size)
        assertTrue(
            VerificationCommandKind.SOURCE_CONFLICT in
                matching.single().kinds
        )
        assertTrue(
            VerificationCommandKind.COUNTERFACT in
                matching.single().kinds
        )
        assertTrue(
            VerificationCommandModule.SOURCES in
                matching.single().modules
        )
        assertTrue(
            VerificationCommandModule.COUNTERVIEW in
                matching.single().modules
        )
    }

    @Test
    fun uncheckedCounterviewAddsOnlyItsHighestImpactFactor() {
        val result = evaluate(
            input = input(
                assessment = SignalAssessment(
                    listOf(90, 75, 65, 55, 45)
                ),
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.INDEPENDENT),
                review = CounterReviewAssessment.unchecked()
            )
        )
        val tasks = result.tasks.filter {
            VerificationCommandKind.COUNTER_OPEN in it.kinds
        }

        assertEquals(1, tasks.size)
        assertEquals(result.counterView.nextFactor, tasks.single().factor)
        assertEquals(
            result.counterView.factor(
                requireNotNull(result.counterView.nextFactor)
            ).readinessImpact,
            tasks.single().readinessImpact
        )
    }

    @Test
    fun stableStateContainsOnlyTransparentMaintenance() {
        val result = evaluate(
            input = input(
                assessment = assessment(86),
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.INDEPENDENT),
                review = CounterReviewAssessment.cleared()
            )
        )

        assertEquals(VerificationCommandStatus.STABLE, result.status)
        assertTrue(result.tasks.all {
            it.priority == VerificationCommandPriority.MAINTAIN
        })
        assertEquals(
            result.freshness.nextTransitionFactor,
            result.tasks.first().factor
        )
        assertEquals(
            result.freshness.nextTransitionAt,
            result.tasks.first().dueAt
        )
    }

    @Test
    fun queueIsOrderedByPublishedPriorityTiers() {
        val review = CounterReviewAssessment.unchecked().withState(
            SignalFactor.CONTEXT,
            CounterReviewState.MIXED
        )
        val result = evaluate(
            input = input(
                evidence = levels(EvidenceLevel.QUORUM),
                audit = audits(SourceAuditState.UNAUDITED),
                timeline = EvidenceTimeline(
                    listOf(
                        now,
                        now - 5L * hour,
                        now,
                        now,
                        now
                    )
                ),
                review = review
            )
        )
        val ordinals = result.tasks.map { it.priority.ordinal }

        assertEquals(ordinals.sorted(), ordinals)
        assertEquals(
            VerificationCommandPriority.REPAIR,
            result.tasks.first().priority
        )
        assertTrue(
            result.tasks.any {
                it.priority == VerificationCommandPriority.REFRESH
            }
        )
        assertTrue(
            result.tasks.any {
                it.priority == VerificationCommandPriority.CHALLENGE
            }
        )
    }

    @Test
    fun sameInputAndMomentProduceTheSameFingerprints() {
        val input = input(
            evidence = levels(EvidenceLevel.SINGLE_SOURCE),
            audit = audits(SourceAuditState.UNAUDITED),
            review = CounterReviewAssessment.unchecked()
        )

        val first = evaluate(input)
        val second = evaluate(input)

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(
            first.tasks.map(VerificationCommandTask::fingerprint),
            second.tasks.map(VerificationCommandTask::fingerprint)
        )
    }

    @Test
    fun auditChangeProducesANewQueueFingerprintWithoutMutation() {
        val claimed = levels(EvidenceLevel.QUORUM)
        val originalAudit = audits(SourceAuditState.UNAUDITED)
        val original = input(
            evidence = claimed,
            audit = originalAudit
        )
        val changed = original.copy(
            sourceAudit = originalAudit.withState(
                SignalFactor.FORM,
                SourceAuditState.INDEPENDENT
            )
        )

        val first = evaluate(original)
        val second = evaluate(changed)

        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(EvidenceLevel.QUORUM, claimed.level(SignalFactor.FORM))
        assertEquals(
            SourceAuditState.UNAUDITED,
            originalAudit.state(SignalFactor.FORM)
        )
        assertFalse(first.tasks === second.tasks)
    }

    private fun evaluate(
        input: VerificationCommandInput
    ): VerificationCommandResult {
        return VerificationCommandEngine.evaluate(
            input = input,
            now = now
        )
    }

    private fun input(
        assessment: SignalAssessment = assessment(80),
        evidence: EvidenceAssessment =
            levels(EvidenceLevel.SINGLE_SOURCE),
        audit: SourceAuditAssessment =
            audits(SourceAuditState.UNAUDITED),
        timeline: EvidenceTimeline = timeline(now),
        review: CounterReviewAssessment =
            CounterReviewAssessment.cleared(),
        snapshot: DecisionSnapshot? = null,
        breach: DecisionGuardBreach? = null
    ): VerificationCommandInput {
        return VerificationCommandInput(
            eventId = "event-1",
            sport = "Футбол",
            assessment = assessment,
            claimedEvidence = evidence,
            sourceAudit = audit,
            timeline = timeline,
            counterReview = review,
            decisionSnapshot = snapshot,
            decisionGuardBreach = breach
        )
    }

    private fun assessment(value: Int): SignalAssessment {
        return SignalAssessment(List(5) { value })
    }

    private fun levels(level: EvidenceLevel): EvidenceAssessment {
        return EvidenceAssessment(List(5) { level })
    }

    private fun audits(
        state: SourceAuditState
    ): SourceAuditAssessment {
        return SourceAuditAssessment(List(5) { state })
    }

    private fun timeline(timestamp: Long): EvidenceTimeline {
        return EvidenceTimeline(List(5) { timestamp })
    }
}
