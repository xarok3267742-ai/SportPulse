package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class CollectionXrayState {
    EMPTY,
    NEED_MORE,
    CLEAR,
    GAPS,
    VERDICT_SHIFT,
    TOO_MANY
}

internal enum class CollectionXrayCellState {
    SUPPORTED,
    GAP,
    CRITICAL
}

internal enum class CollectionXrayGapCause {
    SOURCE_CONFLICT,
    FRESHNESS_LOSS,
    SHARED_LINEAGE,
    UNAUDITED_QUORUM,
    UNCONFIRMED,
    SINGLE_SOURCE_LIMIT,
    EVIDENCE_LIMIT
}

internal data class CollectionXrayCandidate(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
    }
}

internal data class CollectionXrayCell(
    val eventId: String,
    val catalogOrder: Int,
    val factor: SignalFactor,
    val claimedScore: Int,
    val supportedScore: Int,
    val unsupportedPoints: Int,
    val readinessImpact: Int,
    val effectiveEvidence: EvidenceLevel,
    val state: CollectionXrayCellState,
    val cause: CollectionXrayGapCause?
) {
    init {
        require(eventId.isNotBlank())
        require(catalogOrder >= 0)
        require(claimedScore in 0..100)
        require(supportedScore in 0..100)
        require(unsupportedPoints == claimedScore - supportedScore)
        require(unsupportedPoints >= 0)
        require(readinessImpact >= 0)
        require(
            (state == CollectionXrayCellState.SUPPORTED) ==
                (unsupportedPoints == 0)
        )
        require((cause == null) == (unsupportedPoints == 0))
    }
}

internal data class CollectionXrayEntry(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int,
    val claimedReadiness: Int,
    val supportedReadiness: Int,
    val readinessGap: Int,
    val claimedVerdict: SignalVerdict,
    val supportedVerdict: SignalVerdict,
    val shadowStatus: ConfidenceShadowStatus,
    val cells: List<CollectionXrayCell>
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
        require(claimedReadiness in 0..100)
        require(supportedReadiness in 0..100)
        require(readinessGap == claimedReadiness - supportedReadiness)
        require(readinessGap >= 0)
        require(cells.size == SignalFactor.values().size)
        require(cells.map(CollectionXrayCell::factor) == SignalFactor.values().toList())
        require(cells.all { it.eventId == eventId })
        require(cells.count { it.state == CollectionXrayCellState.CRITICAL } <= 1)
        require(
            (shadowStatus == ConfidenceShadowStatus.VERDICT_SHIFT) ==
                cells.any { it.state == CollectionXrayCellState.CRITICAL }
        )
    }
}

internal data class CollectionXrayFactorSummary(
    val factor: SignalFactor,
    val affectedEventCount: Int,
    val criticalEventCount: Int,
    val totalUnsupportedPoints: Int,
    val totalReadinessImpact: Int
) {
    init {
        require(affectedEventCount >= 0)
        require(criticalEventCount in 0..affectedEventCount)
        require(totalUnsupportedPoints >= 0)
        require(totalReadinessImpact >= 0)
    }
}

internal data class CollectionXrayResult(
    val state: CollectionXrayState,
    val candidateCount: Int,
    val entries: List<CollectionXrayEntry>,
    val factors: List<CollectionXrayFactorSummary>,
    val focus: CollectionXrayCell?,
    val leadingFactor: CollectionXrayFactorSummary?,
    val evaluatedAtMinute: Long,
    val fingerprint: String
) {
    init {
        require(candidateCount >= 0)
        require(evaluatedAtMinute >= 0L)
        require(HEX_64.matches(fingerprint))
        require(entries.map(CollectionXrayEntry::eventId).distinct().size == entries.size)
        require(entries.zipWithNext().all { (left, right) ->
            left.catalogOrder < right.catalogOrder
        })
        if (state in READY_STATES) {
            require(candidateCount in CollectionXrayPolicy.MIN_EVENTS..CollectionXrayPolicy.MAX_EVENTS)
            require(entries.size == candidateCount)
            require(factors.size == SignalFactor.values().size)
            require(
                focus == entries.flatMap(CollectionXrayEntry::cells)
                    .filter {
                        it.state != CollectionXrayCellState.SUPPORTED
                    }
                    .maxWithOrNull(FOCUS_COMPARATOR)
            )
            require(
                leadingFactor == factors
                    .filter { it.affectedEventCount > 0 }
                    .maxWithOrNull(FACTOR_COMPARATOR)
            )
        } else {
            require(entries.isEmpty())
            require(factors.isEmpty())
            require(focus == null)
            require(leadingFactor == null)
        }
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    val isReady: Boolean
        get() = state in READY_STATES

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
        val READY_STATES = setOf(
            CollectionXrayState.CLEAR,
            CollectionXrayState.GAPS,
            CollectionXrayState.VERDICT_SHIFT
        )
        val FOCUS_COMPARATOR = compareBy<CollectionXrayCell> {
            when (it.state) {
                CollectionXrayCellState.SUPPORTED -> 0
                CollectionXrayCellState.GAP -> 1
                CollectionXrayCellState.CRITICAL -> 2
            }
        }.thenBy(CollectionXrayCell::readinessImpact)
            .thenBy(CollectionXrayCell::unsupportedPoints)
            .thenBy { -it.catalogOrder }
            .thenBy { -it.factor.ordinal }
        val FACTOR_COMPARATOR = compareBy<CollectionXrayFactorSummary> {
            it.criticalEventCount
        }.thenBy(CollectionXrayFactorSummary::affectedEventCount)
            .thenBy(CollectionXrayFactorSummary::totalReadinessImpact)
            .thenBy(CollectionXrayFactorSummary::totalUnsupportedPoints)
            .thenBy { -it.factor.ordinal }
    }
}

internal object CollectionXrayPolicy {
    const val MIN_EVENTS = 2
    const val MAX_EVENTS = 8
}

internal object CollectionXrayEngine {
    private const val VERSION = "sport-pulse-collection-xray-v1"
    private const val MINUTE_MILLIS = 60_000L
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        candidates: List<CollectionXrayCandidate>,
        now: Long
    ): CollectionXrayResult {
        require(now >= 0L)
        require(candidates.map(CollectionXrayCandidate::eventId).distinct().size == candidates.size)
        require(candidates.map(CollectionXrayCandidate::catalogOrder).distinct().size == candidates.size)
        val ordered = candidates.sortedBy(CollectionXrayCandidate::catalogOrder)
        val evaluatedAtMinute = now / MINUTE_MILLIS
        val boundaryState = when {
            ordered.isEmpty() -> CollectionXrayState.EMPTY
            ordered.size < CollectionXrayPolicy.MIN_EVENTS -> CollectionXrayState.NEED_MORE
            ordered.size > CollectionXrayPolicy.MAX_EVENTS -> CollectionXrayState.TOO_MANY
            else -> null
        }
        if (boundaryState != null) {
            return CollectionXrayResult(
                state = boundaryState,
                candidateCount = ordered.size,
                entries = emptyList(),
                factors = emptyList(),
                focus = null,
                leadingFactor = null,
                evaluatedAtMinute = evaluatedAtMinute,
                fingerprint = fingerprint(
                    state = boundaryState,
                    candidates = ordered,
                    entries = emptyList(),
                    evaluatedAtMinute = evaluatedAtMinute
                )
            )
        }

        val entries = ordered.map { candidate ->
            entry(candidate, now)
        }
        val factors = SignalFactor.values().map { factor ->
            val cells = entries.map { it.cells[factor.ordinal] }
            CollectionXrayFactorSummary(
                factor = factor,
                affectedEventCount = cells.count {
                    it.state != CollectionXrayCellState.SUPPORTED
                },
                criticalEventCount = cells.count {
                    it.state == CollectionXrayCellState.CRITICAL
                },
                totalUnsupportedPoints = cells.sumOf(
                    CollectionXrayCell::unsupportedPoints
                ),
                totalReadinessImpact = cells.sumOf(
                    CollectionXrayCell::readinessImpact
                )
            )
        }
        val state = when {
            entries.any {
                it.shadowStatus == ConfidenceShadowStatus.VERDICT_SHIFT
            } -> CollectionXrayState.VERDICT_SHIFT
            entries.any { entry ->
                entry.cells.any {
                    it.state == CollectionXrayCellState.GAP
                }
            } -> CollectionXrayState.GAPS
            else -> CollectionXrayState.CLEAR
        }
        val focus = entries.flatMap(CollectionXrayEntry::cells)
            .filter {
                it.state != CollectionXrayCellState.SUPPORTED
            }
            .maxWithOrNull(focusComparator)
        val leadingFactor = factors
            .filter { it.affectedEventCount > 0 }
            .maxWithOrNull(factorComparator)
        return CollectionXrayResult(
            state = state,
            candidateCount = ordered.size,
            entries = entries,
            factors = factors,
            focus = focus,
            leadingFactor = leadingFactor,
            evaluatedAtMinute = evaluatedAtMinute,
            fingerprint = fingerprint(
                state = state,
                candidates = ordered,
                entries = entries,
                evaluatedAtMinute = evaluatedAtMinute
            )
        )
    }

    private fun entry(
        candidate: CollectionXrayCandidate,
        now: Long
    ): CollectionXrayEntry {
        val integrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = candidate.claimedEvidence,
            audit = candidate.sourceAudit
        )
        val freshness = FreshnessEngine.evaluate(
            evidence = integrity.effectiveEvidence,
            timeline = candidate.timeline,
            now = now
        )
        val shadow = ConfidenceShadowEngine.evaluate(
            assessment = candidate.assessment,
            evidence = freshness.effectiveEvidence
        )
        val shadowByFactor = shadow.shadowedFactors.associateBy(
            ConfidenceShadowFactor::factor
        )
        val criticalFactor = shadow.criticalFactor?.factor
        val cells = SignalFactor.values().map { factor ->
            val claimedScore = candidate.assessment.value(factor)
            val supportedScore = shadow.supportedAssessment.value(factor)
            val unsupportedPoints = claimedScore - supportedScore
            val shadowFactor = shadowByFactor[factor]
            val state = when {
                unsupportedPoints == 0 ->
                    CollectionXrayCellState.SUPPORTED
                shadow.status == ConfidenceShadowStatus.VERDICT_SHIFT &&
                    factor == criticalFactor ->
                    CollectionXrayCellState.CRITICAL
                else -> CollectionXrayCellState.GAP
            }
            CollectionXrayCell(
                eventId = candidate.eventId,
                catalogOrder = candidate.catalogOrder,
                factor = factor,
                claimedScore = claimedScore,
                supportedScore = supportedScore,
                unsupportedPoints = unsupportedPoints,
                readinessImpact = shadowFactor?.readinessImpact ?: 0,
                effectiveEvidence = freshness.effectiveEvidence.level(factor),
                state = state,
                cause = if (unsupportedPoints == 0) {
                    null
                } else {
                    gapCause(
                        factor = factor,
                        integrity = integrity,
                        freshness = freshness
                    )
                }
            )
        }
        return CollectionXrayEntry(
            eventId = candidate.eventId,
            match = candidate.match,
            sport = candidate.sport,
            region = candidate.region,
            catalogOrder = candidate.catalogOrder,
            claimedReadiness = shadow.claimedSignal.readiness,
            supportedReadiness = shadow.supportedSignal.readiness,
            readinessGap = shadow.readinessGap,
            claimedVerdict = shadow.claimedSignal.verdict,
            supportedVerdict = shadow.supportedSignal.verdict,
            shadowStatus = shadow.status,
            cells = cells
        )
    }

    private fun gapCause(
        factor: SignalFactor,
        integrity: SourceIntegrityResult,
        freshness: FreshnessResult
    ): CollectionXrayGapCause {
        val integrityFactor = integrity.factors[factor.ordinal]
        val freshnessFactor = freshness.factor(factor)
        return when {
            integrityFactor.auditState == SourceAuditState.CONFLICT ->
                CollectionXrayGapCause.SOURCE_CONFLICT
            freshnessFactor.effectiveLevel.scoreCap <
                freshnessFactor.claimedLevel.scoreCap ->
                CollectionXrayGapCause.FRESHNESS_LOSS
            integrityFactor.isQuorumClaim &&
                integrityFactor.auditState == SourceAuditState.SHARED_LINEAGE ->
                CollectionXrayGapCause.SHARED_LINEAGE
            integrityFactor.isQuorumClaim &&
                integrityFactor.auditState == SourceAuditState.UNAUDITED ->
                CollectionXrayGapCause.UNAUDITED_QUORUM
            freshnessFactor.effectiveLevel == EvidenceLevel.UNCONFIRMED ->
                CollectionXrayGapCause.UNCONFIRMED
            freshnessFactor.effectiveLevel == EvidenceLevel.SINGLE_SOURCE ->
                CollectionXrayGapCause.SINGLE_SOURCE_LIMIT
            else -> CollectionXrayGapCause.EVIDENCE_LIMIT
        }
    }

    private fun fingerprint(
        state: CollectionXrayState,
        candidates: List<CollectionXrayCandidate>,
        entries: List<CollectionXrayEntry>,
        evaluatedAtMinute: Long
    ): String {
        val fields = buildList {
            add(VERSION)
            add(state.name)
            add(evaluatedAtMinute.toString())
            add(candidates.size.toString())
            candidates.forEach { candidate ->
                add(candidate.eventId)
                add(candidate.match)
                add(candidate.sport)
                add(candidate.region)
                add(candidate.catalogOrder.toString())
                add(candidate.assessment.values.joinToString(","))
                add(candidate.claimedEvidence.levels.joinToString(",") { it.name })
                add(candidate.sourceAudit.states.joinToString(",") { it.name })
                add(candidate.timeline.checkedAt.joinToString(","))
            }
            entries.forEach { entry ->
                add(entry.eventId)
                add(entry.claimedReadiness.toString())
                add(entry.supportedReadiness.toString())
                add(entry.shadowStatus.name)
                entry.cells.forEach { cell ->
                    add(cell.factor.name)
                    add(cell.claimedScore.toString())
                    add(cell.supportedScore.toString())
                    add(cell.readinessImpact.toString())
                    add(cell.effectiveEvidence.name)
                    add(cell.state.name)
                    add(cell.cause?.name.orEmpty())
                }
            }
        }
        val payload = buildString {
            fields.forEach { field ->
                append(field.length)
                append(':')
                append(field)
                append('|')
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private val focusComparator = compareBy<CollectionXrayCell> {
        when (it.state) {
            CollectionXrayCellState.SUPPORTED -> 0
            CollectionXrayCellState.GAP -> 1
            CollectionXrayCellState.CRITICAL -> 2
        }
    }.thenBy(CollectionXrayCell::readinessImpact)
        .thenBy(CollectionXrayCell::unsupportedPoints)
        .thenBy { -it.catalogOrder }
        .thenBy { -it.factor.ordinal }

    private val factorComparator = compareBy<CollectionXrayFactorSummary> {
        it.criticalEventCount
    }.thenBy(CollectionXrayFactorSummary::affectedEventCount)
        .thenBy(CollectionXrayFactorSummary::totalReadinessImpact)
        .thenBy(CollectionXrayFactorSummary::totalUnsupportedPoints)
        .thenBy { -it.factor.ordinal }
}
