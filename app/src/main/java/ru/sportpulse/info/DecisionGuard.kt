package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class DecisionGuardStatus {
    SEALED_SKIP,
    ARMED,
    TRIGGERED
}

internal enum class DecisionGuardCause {
    DECISION_ABOVE_SIGNAL,
    SIGNAL_BELOW_CONTRACT,
    FACTOR_FLOOR,
    EVIDENCE_LOSS,
    COUNTERVIEW_LIMIT
}

internal data class DecisionGuardCondition(
    val factor: SignalFactor,
    val baselineValue: Int,
    val scoreFloor: Int?,
    val requiredEvidence: EvidenceLevel?,
    val evidenceValidUntil: Long?
)

internal data class DecisionGuardPlan(
    val eventId: String,
    val armedAt: Long,
    val decision: SavedDecision,
    val requiredVerdict: SignalVerdict,
    val condition: DecisionGuardCondition?,
    val snapshotFingerprint: String,
    val seal: String
) {
    val shortSeal: String
        get() = seal.take(10).uppercase()
}

internal data class DecisionGuardResult(
    val plan: DecisionGuardPlan,
    val status: DecisionGuardStatus,
    val baselineResult: EvidenceResult,
    val currentResult: EvidenceResult,
    val currentFreshness: FreshnessResult,
    val causes: List<DecisionGuardCause>,
    val breach: DecisionGuardBreach? = null
) {
    val isTriggered: Boolean
        get() = status == DecisionGuardStatus.TRIGGERED

    val isRecoveredAfterBreach: Boolean
        get() = breach != null && causes.isEmpty()

    val effectiveCauses: List<DecisionGuardCause>
        get() = breach?.causes ?: causes

    val currentFactorValue: Int?
        get() = plan.condition?.let {
            currentResult.rawAssessment.value(it.factor)
        }

    val currentEvidence: EvidenceLevel?
        get() = plan.condition?.let {
            currentFreshness.effectiveEvidence.level(it.factor)
        }

    fun withBreach(
        value: DecisionGuardBreach
    ): DecisionGuardResult {
        require(value.eventId == plan.eventId)
        require(value.triggeredAt >= plan.armedAt)
        require(
            MessageDigest.isEqual(
                value.planSeal.lowercase().toByteArray(
                    StandardCharsets.US_ASCII
                ),
                plan.seal.lowercase().toByteArray(
                    StandardCharsets.US_ASCII
                )
            )
        )
        return copy(
            status = DecisionGuardStatus.TRIGGERED,
            breach = value
        )
    }
}

internal object DecisionGuardEngine {
    private const val VERSION = "sport-pulse-decision-guard-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        snapshot: DecisionSnapshot,
        currentAssessment: SignalAssessment,
        currentEvidence: EvidenceAssessment,
        currentTimeline: EvidenceTimeline,
        currentCounterReview: CounterReviewAssessment =
            snapshot.counterReview,
        now: Long
    ): DecisionGuardResult {
        require(now >= 0L)
        val baselineFreshness = FreshnessEngine.evaluate(
            evidence = snapshot.evidence,
            timeline = snapshot.timeline,
            now = snapshot.savedAt
        )
        val baselineResult = EvidenceEngine.evaluate(
            assessment = snapshot.assessment,
            evidence = baselineFreshness.effectiveEvidence
        )
        val plan = createPlan(
            snapshot = snapshot,
            baselineFreshness = baselineFreshness,
            baselineResult = baselineResult
        )
        val currentFreshness = FreshnessEngine.evaluate(
            evidence = currentEvidence,
            timeline = currentTimeline,
            now = now
        )
        val currentResult = EvidenceEngine.evaluate(
            assessment = currentAssessment,
            evidence = currentFreshness.effectiveEvidence
        )
        val baselineCounterView = CounterViewEngine.evaluate(
            assessment = snapshot.assessment,
            evidence = baselineFreshness.effectiveEvidence,
            review = snapshot.counterReview
        )
        val currentCounterView = CounterViewEngine.evaluate(
            assessment = currentAssessment,
            evidence = currentFreshness.effectiveEvidence,
            review = currentCounterReview
        )

        if (snapshot.decision == SavedDecision.SKIP) {
            return DecisionGuardResult(
                plan = plan,
                status = DecisionGuardStatus.SEALED_SKIP,
                baselineResult = baselineResult,
                currentResult = currentResult,
                currentFreshness = currentFreshness,
                causes = emptyList()
            )
        }

        val causes = buildList {
            if (
                baselineResult.effectiveSignal.verdict.ordinal <
                plan.requiredVerdict.ordinal
            ) {
                add(DecisionGuardCause.DECISION_ABOVE_SIGNAL)
            } else if (
                currentResult.effectiveSignal.verdict.ordinal <
                plan.requiredVerdict.ordinal
            ) {
                add(DecisionGuardCause.SIGNAL_BELOW_CONTRACT)
            }
            if (
                snapshot.decision.ordinal >
                baselineCounterView.decisionCeiling.ordinal ||
                snapshot.decision.ordinal >
                currentCounterView.decisionCeiling.ordinal
            ) {
                add(DecisionGuardCause.COUNTERVIEW_LIMIT)
            }
            plan.condition?.let { condition ->
                if (
                    condition.scoreFloor != null &&
                    currentAssessment.value(condition.factor) <=
                    condition.scoreFloor
                ) {
                    add(DecisionGuardCause.FACTOR_FLOOR)
                }
                if (
                    condition.requiredEvidence != null &&
                    currentFreshness.effectiveEvidence
                        .level(condition.factor)
                        .ordinal <
                    condition.requiredEvidence.ordinal
                ) {
                    add(DecisionGuardCause.EVIDENCE_LOSS)
                }
            }
        }
        return DecisionGuardResult(
            plan = plan,
            status = if (causes.isEmpty()) {
                DecisionGuardStatus.ARMED
            } else {
                DecisionGuardStatus.TRIGGERED
            },
            baselineResult = baselineResult,
            currentResult = currentResult,
            currentFreshness = currentFreshness,
            causes = causes,
        )
    }

    private fun createPlan(
        snapshot: DecisionSnapshot,
        baselineFreshness: FreshnessResult,
        baselineResult: EvidenceResult
    ): DecisionGuardPlan {
        val requiredVerdict = requiredVerdict(snapshot.decision)
        val condition = if (snapshot.decision == SavedDecision.SKIP) {
            null
        } else {
            val boundary = findBoundary(
                assessment = snapshot.assessment,
                evidence = baselineFreshness.effectiveEvidence,
                requiredVerdict = requiredVerdict
            )
            val factor = boundary?.factor ?: criticalFactor(
                assessment = snapshot.assessment,
                evidence = baselineFreshness.effectiveEvidence,
                baselineResult = baselineResult
            )
            val factorFreshness = baselineFreshness.factor(factor)
            DecisionGuardCondition(
                factor = factor,
                baselineValue = snapshot.assessment.value(factor),
                scoreFloor = boundary?.scoreFloor,
                requiredEvidence = factorFreshness.effectiveLevel
                    .takeUnless { it == EvidenceLevel.UNCONFIRMED },
                evidenceValidUntil = factorFreshness.nextTransitionAt
            )
        }
        val draft = DecisionGuardPlan(
            eventId = snapshot.eventId,
            armedAt = snapshot.savedAt,
            decision = snapshot.decision,
            requiredVerdict = requiredVerdict,
            condition = condition,
            snapshotFingerprint = snapshot.fingerprint,
            seal = ""
        )
        return draft.copy(seal = sealFor(draft))
    }

    private fun findBoundary(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        requiredVerdict: SignalVerdict
    ): GuardBoundary? {
        val baseline = EvidenceEngine.evaluate(
            assessment,
            evidence
        )
        if (
            baseline.effectiveSignal.verdict.ordinal <
            requiredVerdict.ordinal
        ) {
            return null
        }
        return SignalFactor.values().mapNotNull { factor ->
            val baselineValue = assessment.value(factor)
            for (candidate in baselineValue - 1 downTo 0) {
                val result = EvidenceEngine.evaluate(
                    assessment.withValue(factor, candidate),
                    evidence
                )
                if (
                    result.effectiveSignal.verdict.ordinal <
                    requiredVerdict.ordinal
                ) {
                    return@mapNotNull GuardBoundary(
                        factor = factor,
                        scoreFloor = candidate,
                        change = baselineValue - candidate
                    )
                }
            }
            null
        }.minWithOrNull(
            compareBy<GuardBoundary> { it.change }
                .thenBy { it.factor.ordinal }
        )
    }

    private fun criticalFactor(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        baselineResult: EvidenceResult
    ): SignalFactor {
        val evidenceRisk = SignalFactor.values().mapNotNull { factor ->
            val downgraded = downgrade(
                evidence.level(factor)
            ) ?: return@mapNotNull null
            val result = EvidenceEngine.evaluate(
                assessment,
                evidence.withLevel(factor, downgraded)
            )
            FactorRisk(
                factor = factor,
                verdictChanged = (
                    result.effectiveSignal.verdict !=
                        baselineResult.effectiveSignal.verdict
                    ),
                readinessDrop = (
                    baselineResult.effectiveSignal.readiness -
                        result.effectiveSignal.readiness
                    ).coerceAtLeast(0)
            )
        }.maxWithOrNull(
            compareBy<FactorRisk> { it.verdictChanged }
                .thenBy { it.readinessDrop }
                .thenBy { -it.factor.ordinal }
        )
        return evidenceRisk?.factor
            ?: baselineResult.effectiveSignal.weakestFactor
    }

    private fun downgrade(
        level: EvidenceLevel
    ): EvidenceLevel? {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> null
            EvidenceLevel.SINGLE_SOURCE ->
                EvidenceLevel.UNCONFIRMED
            EvidenceLevel.QUORUM ->
                EvidenceLevel.SINGLE_SOURCE
        }
    }

    private fun requiredVerdict(
        decision: SavedDecision
    ): SignalVerdict {
        return when (decision) {
            SavedDecision.SKIP -> SignalVerdict.SKIP
            SavedDecision.OBSERVE -> SignalVerdict.OBSERVE
            SavedDecision.DATA_READY -> SignalVerdict.READY
        }
    }

    private fun sealFor(plan: DecisionGuardPlan): String {
        val condition = plan.condition
        val payload = listOf(
            VERSION,
            plan.eventId,
            plan.armedAt.toString(),
            plan.decision.name,
            plan.requiredVerdict.name,
            condition?.factor?.name.orEmpty(),
            condition?.baselineValue?.toString().orEmpty(),
            condition?.scoreFloor?.toString().orEmpty(),
            condition?.requiredEvidence?.name.orEmpty(),
            condition?.evidenceValidUntil?.toString().orEmpty(),
            plan.snapshotFingerprint.lowercase()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private data class GuardBoundary(
        val factor: SignalFactor,
        val scoreFloor: Int,
        val change: Int
    )

    private data class FactorRisk(
        val factor: SignalFactor,
        val verdictChanged: Boolean,
        val readinessDrop: Int
    )
}
