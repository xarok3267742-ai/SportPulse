package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.max

internal data class ChronoLensInput(
    val eventId: String,
    val sport: String,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline,
    val counterReview: CounterReviewAssessment,
    val decisionSnapshot: DecisionSnapshot? = null
) {
    init {
        require(eventId.isNotBlank())
        require(
            decisionSnapshot == null ||
                decisionSnapshot.eventId == eventId
        )
    }
}

internal enum class ChronoLensChangeKind {
    EXPIRING,
    LEVEL_DROP
}

internal data class ChronoLensChange(
    val factor: SignalFactor,
    val kind: ChronoLensChangeKind,
    val fromLevel: EvidenceLevel,
    val toLevel: EvidenceLevel
)

internal data class ChronoLensCheckpoint(
    val at: Long,
    val changes: List<ChronoLensChange>
) {
    init {
        require(at >= 0L)
        require(changes.isNotEmpty())
    }
}

internal data class ChronoLensSnapshot(
    val evaluatedAt: Long,
    val freshness: FreshnessResult,
    val evidenceResult: EvidenceResult,
    val counterView: CounterViewResult,
    val marketLens: MarketLensResult,
    val decisionGuard: DecisionGuardResult?
) {
    val readiness: Int
        get() = evidenceResult.effectiveSignal.readiness

    val verdict: SignalVerdict
        get() = evidenceResult.effectiveSignal.verdict
}

internal enum class ChronoLensState {
    STABLE,
    NARROWING,
    DOWNGRADE,
    STOP
}

internal data class ChronoLensResult(
    val eventId: String,
    val now: Long,
    val selectedAt: Long,
    val horizonAt: Long,
    val baseline: ChronoLensSnapshot,
    val selected: ChronoLensSnapshot,
    val checkpoints: List<ChronoLensCheckpoint>,
    val nextCheckpoint: ChronoLensCheckpoint?,
    val changedFactors: List<SignalFactor>,
    val state: ChronoLensState,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(now >= 0L)
        require(selectedAt in now..horizonAt)
        require(checkpoints.zipWithNext().all { (left, right) ->
            left.at < right.at
        })
        require(fingerprint.length == 64)
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object ChronoLensPolicy {
    const val MIN_HORIZON_MILLIS =
        24L * FreshnessPolicy.HOUR_MILLIS
    const val MAX_HORIZON_MILLIS =
        144L * FreshnessPolicy.HOUR_MILLIS
    const val MINUTE_MILLIS = 60_000L
}

internal object ChronoLensEngine {
    private const val VERSION = "sport-pulse-chrono-lens-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        input: ChronoLensInput,
        now: Long,
        selectedAt: Long
    ): ChronoLensResult {
        require(now >= 0L)
        require(selectedAt >= 0L)

        val sourceIntegrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = input.claimedEvidence,
            audit = input.sourceAudit
        )
        val hardHorizon = safeAdd(
            now,
            ChronoLensPolicy.MAX_HORIZON_MILLIS
        )
        val candidates = checkpointCandidates(
            evidence = sourceIntegrity.effectiveEvidence,
            timeline = input.timeline
        ).filter {
            it.at > now && it.at <= hardHorizon
        }
        val minimumHorizon = safeAdd(
            now,
            ChronoLensPolicy.MIN_HORIZON_MILLIS
        )
        val latestCandidate = candidates.maxOfOrNull(
            ChronoLensCheckpoint::at
        )
        val horizonAt = max(
            minimumHorizon,
            latestCandidate?.let {
                safeAdd(
                    it,
                    ChronoLensPolicy.MINUTE_MILLIS
                )
            } ?: minimumHorizon
        ).coerceAtMost(hardHorizon)
        val checkpoints = candidates
            .filter { it.at <= horizonAt }
            .sortedBy(ChronoLensCheckpoint::at)
        val safeSelectedAt = selectedAt.coerceIn(
            now,
            horizonAt
        )
        val baseline = snapshot(
            input = input,
            effectiveEvidence =
                sourceIntegrity.effectiveEvidence,
            evaluatedAt = now
        )
        val selected = if (safeSelectedAt == now) {
            baseline
        } else {
            snapshot(
                input = input,
                effectiveEvidence =
                    sourceIntegrity.effectiveEvidence,
                evaluatedAt = safeSelectedAt
            )
        }
        val changedFactors = SignalFactor.values().filter {
            baseline.freshness.effectiveEvidence.level(it) !=
                selected.freshness.effectiveEvidence.level(it)
        }
        val state = stateFor(
            baseline = baseline,
            selected = selected,
            changedFactors = changedFactors
        )
        val nextCheckpoint = checkpoints.firstOrNull {
            it.at > safeSelectedAt
        }
        val fingerprint = fingerprintFor(
            input = input,
            sourceIntegrity = sourceIntegrity,
            now = now,
            selectedAt = safeSelectedAt,
            horizonAt = horizonAt,
            baseline = baseline,
            selected = selected,
            checkpoints = checkpoints,
            changedFactors = changedFactors,
            state = state
        )

        return ChronoLensResult(
            eventId = input.eventId,
            now = now,
            selectedAt = safeSelectedAt,
            horizonAt = horizonAt,
            baseline = baseline,
            selected = selected,
            checkpoints = checkpoints,
            nextCheckpoint = nextCheckpoint,
            changedFactors = changedFactors,
            state = state,
            fingerprint = fingerprint
        )
    }

    private fun snapshot(
        input: ChronoLensInput,
        effectiveEvidence: EvidenceAssessment,
        evaluatedAt: Long
    ): ChronoLensSnapshot {
        val freshness = FreshnessEngine.evaluate(
            evidence = effectiveEvidence,
            timeline = input.timeline,
            now = evaluatedAt
        )
        val evidenceResult = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence
        )
        val counterView = CounterViewEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence,
            review = input.counterReview
        )
        val marketLens = MarketLensEngine.evaluate(
            sport = input.sport,
            assessment = input.assessment,
            evidence = effectiveEvidence,
            timeline = input.timeline,
            now = evaluatedAt
        )
        val guard = input.decisionSnapshot?.let { decision ->
            DecisionGuardEngine.evaluate(
                snapshot = decision,
                currentAssessment = input.assessment,
                currentEvidence = effectiveEvidence,
                currentTimeline = input.timeline,
                currentCounterReview = input.counterReview,
                now = evaluatedAt
            )
        }
        return ChronoLensSnapshot(
            evaluatedAt = evaluatedAt,
            freshness = freshness,
            evidenceResult = evidenceResult,
            counterView = counterView,
            marketLens = marketLens,
            decisionGuard = guard
        )
    }

    private fun checkpointCandidates(
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline
    ): List<ChronoLensCheckpoint> {
        val changes = buildList {
            SignalFactor.values().forEach { factor ->
                val level = evidence.level(factor)
                if (level == EvidenceLevel.UNCONFIRMED) {
                    return@forEach
                }
                val checkedAt = timeline.checkedAt(factor)
                val validFor =
                    FreshnessPolicy.validForMillis(factor)
                add(
                    safeAdd(
                        checkedAt,
                        validFor * 3L / 4L
                    ) to ChronoLensChange(
                        factor = factor,
                        kind = ChronoLensChangeKind.EXPIRING,
                        fromLevel = level,
                        toLevel = level
                    )
                )
                when (level) {
                    EvidenceLevel.QUORUM -> {
                        add(
                            safeAdd(
                                checkedAt,
                                validFor
                            ) to ChronoLensChange(
                                factor = factor,
                                kind =
                                    ChronoLensChangeKind.LEVEL_DROP,
                                fromLevel = EvidenceLevel.QUORUM,
                                toLevel =
                                    EvidenceLevel.SINGLE_SOURCE
                            )
                        )
                        add(
                            safeAdd(
                                checkedAt,
                                validFor * 2L
                            ) to ChronoLensChange(
                                factor = factor,
                                kind =
                                    ChronoLensChangeKind.LEVEL_DROP,
                                fromLevel =
                                    EvidenceLevel.SINGLE_SOURCE,
                                toLevel =
                                    EvidenceLevel.UNCONFIRMED
                            )
                        )
                    }
                    EvidenceLevel.SINGLE_SOURCE -> {
                        add(
                            safeAdd(
                                checkedAt,
                                validFor
                            ) to ChronoLensChange(
                                factor = factor,
                                kind =
                                    ChronoLensChangeKind.LEVEL_DROP,
                                fromLevel =
                                    EvidenceLevel.SINGLE_SOURCE,
                                toLevel =
                                    EvidenceLevel.UNCONFIRMED
                            )
                        )
                    }
                    EvidenceLevel.UNCONFIRMED -> Unit
                }
            }
        }
        return changes
            .groupBy { it.first }
            .map { (at, values) ->
                ChronoLensCheckpoint(
                    at = at,
                    changes = values
                        .map { it.second }
                        .sortedWith(
                            compareBy<ChronoLensChange>(
                                { it.factor.ordinal },
                                { it.kind.ordinal }
                            )
                        )
                )
            }
            .sortedBy(ChronoLensCheckpoint::at)
    }

    private fun stateFor(
        baseline: ChronoLensSnapshot,
        selected: ChronoLensSnapshot,
        changedFactors: List<SignalFactor>
    ): ChronoLensState {
        if (selected.evaluatedAt == baseline.evaluatedAt) {
            return ChronoLensState.STABLE
        }
        val guardTriggered =
            baseline.decisionGuard?.status !=
                DecisionGuardStatus.TRIGGERED &&
                selected.decisionGuard?.status ==
                DecisionGuardStatus.TRIGGERED
        val verdictDropped =
            selected.verdict.ordinal <
                baseline.verdict.ordinal
        val ceilingDropped =
            selected.counterView.decisionCeiling.ordinal <
                baseline.counterView.decisionCeiling.ordinal
        val coveredDropped =
            selected.marketLens.coveredCount <
                baseline.marketLens.coveredCount
        val closedIncreased =
            selected.marketLens.closedCount >
                baseline.marketLens.closedCount

        return when {
            guardTriggered ||
                (
                    verdictDropped &&
                        selected.verdict == SignalVerdict.SKIP
                    ) ||
                (
                    ceilingDropped &&
                        selected.counterView.decisionCeiling ==
                        SavedDecision.SKIP
                    ) ->
                ChronoLensState.STOP
            verdictDropped ||
                ceilingDropped ||
                coveredDropped ||
                closedIncreased ->
                ChronoLensState.DOWNGRADE
            changedFactors.isNotEmpty() ||
                selected.freshness.expiringFactors !=
                baseline.freshness.expiringFactors ->
                ChronoLensState.NARROWING
            else ->
                ChronoLensState.STABLE
        }
    }

    private fun fingerprintFor(
        input: ChronoLensInput,
        sourceIntegrity: SourceIntegrityResult,
        now: Long,
        selectedAt: Long,
        horizonAt: Long,
        baseline: ChronoLensSnapshot,
        selected: ChronoLensSnapshot,
        checkpoints: List<ChronoLensCheckpoint>,
        changedFactors: List<SignalFactor>,
        state: ChronoLensState
    ): String {
        val payload = buildString {
            append(VERSION)
            append("|event=")
            append(input.eventId)
            append("|sport=")
            append(input.sport)
            append("|now_min=")
            append(now / ChronoLensPolicy.MINUTE_MILLIS)
            append("|selected_min=")
            append(
                selectedAt /
                    ChronoLensPolicy.MINUTE_MILLIS
            )
            append("|horizon_min=")
            append(
                horizonAt /
                    ChronoLensPolicy.MINUTE_MILLIS
            )
            append("|assessment=")
            append(input.assessment.values.joinToString(","))
            append("|source=")
            append(sourceIntegrity.fingerprint)
            append("|checked=")
            append(input.timeline.checkedAt.joinToString(","))
            append("|counter=")
            append(
                input.counterReview.states.joinToString(",") {
                    it.name
                }
            )
            append("|decision=")
            append(
                input.decisionSnapshot?.fingerprint ?: "NONE"
            )
            append("|base=")
            append(snapshotToken(baseline))
            append("|selected=")
            append(snapshotToken(selected))
            append("|changed=")
            append(
                changedFactors.joinToString(",") {
                    it.name
                }
            )
            append("|state=")
            append(state.name)
            checkpoints.forEach { checkpoint ->
                append("|checkpoint=")
                append(
                    checkpoint.at /
                        ChronoLensPolicy.MINUTE_MILLIS
                )
                append(':')
                append(
                    checkpoint.changes.joinToString(",") {
                        "${it.factor.name}-${
                            it.kind.name
                        }-${it.fromLevel.name}-${
                            it.toLevel.name
                        }"
                    }
                )
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                payload.toByteArray(StandardCharsets.UTF_8)
            )
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun snapshotToken(
        snapshot: ChronoLensSnapshot
    ): String {
        return buildString {
            append(snapshot.readiness)
            append(':')
            append(snapshot.verdict.name)
            append(':')
            append(
                snapshot.freshness.effectiveEvidence.levels
                    .joinToString(",") { it.name }
            )
            append(':')
            append(snapshot.marketLens.coveredCount)
            append(':')
            append(snapshot.marketLens.checkCount)
            append(':')
            append(snapshot.marketLens.closedCount)
            append(':')
            append(snapshot.counterView.decisionCeiling.name)
            append(':')
            append(
                snapshot.decisionGuard?.status?.name ?: "NONE"
            )
        }
    }

    private fun safeAdd(
        value: Long,
        delta: Long
    ): Long {
        require(value >= 0L)
        require(delta >= 0L)
        return if (value > Long.MAX_VALUE - delta) {
            Long.MAX_VALUE
        } else {
            value + delta
        }
    }
}
