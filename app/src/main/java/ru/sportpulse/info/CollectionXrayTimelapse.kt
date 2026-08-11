package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class CollectionXrayTimelapseHorizon(
    val offsetHours: Int
) {
    NOW(0),
    PLUS_6_HOURS(6),
    PLUS_12_HOURS(12),
    PLUS_24_HOURS(24);

    val offsetMillis: Long
        get() = offsetHours * FreshnessPolicy.HOUR_MILLIS
}

internal enum class CollectionXrayTimelapseState {
    NOT_AVAILABLE,
    STABLE,
    GAPS_GROW,
    VERDICT_SHIFT
}

internal enum class CollectionXrayTimelapseChangeKind {
    WORSENED,
    NEW_GAP,
    NEW_CRITICAL
}

internal data class CollectionXrayTimelapseChange(
    val eventId: String,
    val match: String,
    val catalogOrder: Int,
    val factor: SignalFactor,
    val beforeState: CollectionXrayCellState,
    val afterState: CollectionXrayCellState,
    val beforeSupportedScore: Int,
    val afterSupportedScore: Int,
    val supportedScoreLoss: Int,
    val afterReadinessImpact: Int,
    val causesNewVerdictShift: Boolean,
    val kind: CollectionXrayTimelapseChangeKind,
    val cause: CollectionXrayGapCause
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(catalogOrder >= 0)
        require(beforeSupportedScore in 0..100)
        require(afterSupportedScore in 0..100)
        require(supportedScoreLoss == beforeSupportedScore - afterSupportedScore)
        require(supportedScoreLoss > 0)
        require(afterReadinessImpact >= 0)
        require(afterState != CollectionXrayCellState.SUPPORTED)
        require(
            (kind == CollectionXrayTimelapseChangeKind.NEW_GAP) ==
                (beforeState == CollectionXrayCellState.SUPPORTED &&
                    afterState != CollectionXrayCellState.CRITICAL)
        )
        require(
            (kind == CollectionXrayTimelapseChangeKind.NEW_CRITICAL) ==
                (afterState == CollectionXrayCellState.CRITICAL &&
                    beforeState != CollectionXrayCellState.CRITICAL)
        )
    }
}

internal data class CollectionXrayTimelapseFrame(
    val horizon: CollectionXrayTimelapseHorizon,
    val evaluatedAt: Long,
    val xray: CollectionXrayResult,
    val changes: List<CollectionXrayTimelapseChange>,
    val focus: CollectionXrayTimelapseChange?,
    val changedEventCount: Int,
    val newGapCellCount: Int,
    val newlyShiftedEventCount: Int,
    val totalSupportedScoreLoss: Int,
    val totalReadinessLoss: Int,
    val state: CollectionXrayTimelapseState
) {
    init {
        require(evaluatedAt >= 0L)
        require(xray.isReady)
        require(changedEventCount == changes.map { it.eventId }.distinct().size)
        require(newGapCellCount == changes.count {
            it.beforeState == CollectionXrayCellState.SUPPORTED
        })
        require(newlyShiftedEventCount >= 0)
        require(totalSupportedScoreLoss == changes.sumOf {
            it.supportedScoreLoss
        })
        require(totalReadinessLoss >= 0)
        require(focus == changes.maxWithOrNull(
            CollectionXrayTimelapseOrdering.focusComparator
        ))
        require(
            state == when {
                newlyShiftedEventCount > 0 ->
                    CollectionXrayTimelapseState.VERDICT_SHIFT
                changes.isNotEmpty() ->
                    CollectionXrayTimelapseState.GAPS_GROW
                else -> CollectionXrayTimelapseState.STABLE
            }
        )
        if (horizon == CollectionXrayTimelapseHorizon.NOW) {
            require(changes.isEmpty())
            require(focus == null)
            require(state == CollectionXrayTimelapseState.STABLE)
        }
    }
}

internal data class CollectionXrayTimelapseResult(
    val state: CollectionXrayTimelapseState,
    val candidateCount: Int,
    val now: Long,
    val baseline: CollectionXrayResult,
    val frames: List<CollectionXrayTimelapseFrame>,
    val fingerprint: String
) {
    init {
        require(candidateCount >= 0)
        require(now >= 0L)
        require(HEX_64.matches(fingerprint))
        require(baseline.candidateCount == candidateCount)
        if (state == CollectionXrayTimelapseState.NOT_AVAILABLE) {
            require(!baseline.isReady)
            require(frames.isEmpty())
        } else {
            require(baseline.isReady)
            require(
                frames.map(CollectionXrayTimelapseFrame::horizon) ==
                    CollectionXrayTimelapseHorizon.values().toList()
            )
            require(frames.first().xray == baseline)
            require(state == frames.last().state)
            require(frames.zipWithNext().all { (left, right) ->
                left.evaluatedAt < right.evaluatedAt
            })
        }
    }

    val isAvailable: Boolean
        get() = state != CollectionXrayTimelapseState.NOT_AVAILABLE

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun frame(
        horizon: CollectionXrayTimelapseHorizon
    ): CollectionXrayTimelapseFrame {
        require(isAvailable)
        return frames[horizon.ordinal]
    }

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

private object CollectionXrayTimelapseOrdering {
    val focusComparator = compareBy<CollectionXrayTimelapseChange> {
        if (it.causesNewVerdictShift) 1 else 0
    }.thenBy {
        when (it.kind) {
            CollectionXrayTimelapseChangeKind.WORSENED -> 0
            CollectionXrayTimelapseChangeKind.NEW_GAP -> 1
            CollectionXrayTimelapseChangeKind.NEW_CRITICAL -> 2
        }
    }.thenBy(CollectionXrayTimelapseChange::supportedScoreLoss)
        .thenBy(CollectionXrayTimelapseChange::afterReadinessImpact)
        .thenBy { -it.catalogOrder }
        .thenBy { -it.factor.ordinal }
}

internal object CollectionXrayTimelapseEngine {
    private const val VERSION =
        "sport-pulse-collection-xray-timelapse-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        candidates: List<CollectionXrayCandidate>,
        now: Long
    ): CollectionXrayTimelapseResult {
        require(now >= 0L)
        val baseline = CollectionXrayEngine.evaluate(
            candidates = candidates,
            now = now
        )
        if (!baseline.isReady) {
            return CollectionXrayTimelapseResult(
                state = CollectionXrayTimelapseState.NOT_AVAILABLE,
                candidateCount = baseline.candidateCount,
                now = now,
                baseline = baseline,
                frames = emptyList(),
                fingerprint = fingerprint(
                    now = now,
                    baseline = baseline,
                    frames = emptyList()
                )
            )
        }

        val frames = CollectionXrayTimelapseHorizon.values().map {
                horizon ->
            val evaluatedAt = safeAdd(now, horizon.offsetMillis)
            val xray = if (horizon == CollectionXrayTimelapseHorizon.NOW) {
                baseline
            } else {
                CollectionXrayEngine.evaluate(
                    candidates = candidates,
                    now = evaluatedAt
                )
            }
            frame(
                horizon = horizon,
                evaluatedAt = evaluatedAt,
                baseline = baseline,
                xray = xray
            )
        }
        return CollectionXrayTimelapseResult(
            state = frames.last().state,
            candidateCount = baseline.candidateCount,
            now = now,
            baseline = baseline,
            frames = frames,
            fingerprint = fingerprint(
                now = now,
                baseline = baseline,
                frames = frames
            )
        )
    }

    private fun frame(
        horizon: CollectionXrayTimelapseHorizon,
        evaluatedAt: Long,
        baseline: CollectionXrayResult,
        xray: CollectionXrayResult
    ): CollectionXrayTimelapseFrame {
        require(xray.entries.map(CollectionXrayEntry::eventId) ==
            baseline.entries.map(CollectionXrayEntry::eventId))
        val changes = baseline.entries.flatMapIndexed {
                eventIndex,
                beforeEntry ->
            val afterEntry = xray.entries[eventIndex]
            beforeEntry.cells.mapNotNull { beforeCell ->
                val afterCell = afterEntry.cells[beforeCell.factor.ordinal]
                val loss = beforeCell.supportedScore -
                    afterCell.supportedScore
                require(loss >= 0)
                if (loss == 0) {
                    null
                } else {
                    val newlyShifted =
                        beforeEntry.shadowStatus !=
                            ConfidenceShadowStatus.VERDICT_SHIFT &&
                            afterEntry.shadowStatus ==
                            ConfidenceShadowStatus.VERDICT_SHIFT
                    CollectionXrayTimelapseChange(
                        eventId = beforeEntry.eventId,
                        match = beforeEntry.match,
                        catalogOrder = beforeEntry.catalogOrder,
                        factor = beforeCell.factor,
                        beforeState = beforeCell.state,
                        afterState = afterCell.state,
                        beforeSupportedScore = beforeCell.supportedScore,
                        afterSupportedScore = afterCell.supportedScore,
                        supportedScoreLoss = loss,
                        afterReadinessImpact = afterCell.readinessImpact,
                        causesNewVerdictShift = newlyShifted,
                        kind = changeKind(
                            before = beforeCell.state,
                            after = afterCell.state
                        ),
                        cause = checkNotNull(afterCell.cause)
                    )
                }
            }
        }
        val newlyShifted = baseline.entries.zip(xray.entries).count {
                (before, after) ->
            before.shadowStatus != ConfidenceShadowStatus.VERDICT_SHIFT &&
                after.shadowStatus == ConfidenceShadowStatus.VERDICT_SHIFT
        }
        val readinessLoss = baseline.entries.zip(xray.entries).sumOf {
                (before, after) ->
            val loss = before.supportedReadiness - after.supportedReadiness
            require(loss >= 0)
            loss
        }
        val state = when {
            newlyShifted > 0 ->
                CollectionXrayTimelapseState.VERDICT_SHIFT
            changes.isNotEmpty() ->
                CollectionXrayTimelapseState.GAPS_GROW
            else -> CollectionXrayTimelapseState.STABLE
        }
        return CollectionXrayTimelapseFrame(
            horizon = horizon,
            evaluatedAt = evaluatedAt,
            xray = xray,
            changes = changes,
            focus = changes.maxWithOrNull(
                CollectionXrayTimelapseOrdering.focusComparator
            ),
            changedEventCount = changes.map { it.eventId }.distinct().size,
            newGapCellCount = changes.count {
                it.beforeState == CollectionXrayCellState.SUPPORTED
            },
            newlyShiftedEventCount = newlyShifted,
            totalSupportedScoreLoss = changes.sumOf {
                it.supportedScoreLoss
            },
            totalReadinessLoss = readinessLoss,
            state = state
        )
    }

    private fun changeKind(
        before: CollectionXrayCellState,
        after: CollectionXrayCellState
    ): CollectionXrayTimelapseChangeKind {
        return when {
            after == CollectionXrayCellState.CRITICAL &&
                before != CollectionXrayCellState.CRITICAL ->
                CollectionXrayTimelapseChangeKind.NEW_CRITICAL
            before == CollectionXrayCellState.SUPPORTED ->
                CollectionXrayTimelapseChangeKind.NEW_GAP
            else -> CollectionXrayTimelapseChangeKind.WORSENED
        }
    }

    private fun fingerprint(
        now: Long,
        baseline: CollectionXrayResult,
        frames: List<CollectionXrayTimelapseFrame>
    ): String {
        val fields = buildList {
            add(VERSION)
            add((now / 60_000L).toString())
            add(baseline.fingerprint)
            frames.forEach { frame ->
                add(frame.horizon.name)
                add((frame.evaluatedAt / 60_000L).toString())
                add(frame.xray.fingerprint)
                add(frame.state.name)
                add(frame.changedEventCount.toString())
                add(frame.newGapCellCount.toString())
                add(frame.newlyShiftedEventCount.toString())
                add(frame.totalSupportedScoreLoss.toString())
                add(frame.totalReadinessLoss.toString())
                frame.changes.forEach { change ->
                    add(change.eventId)
                    add(change.factor.name)
                    add(change.beforeState.name)
                    add(change.afterState.name)
                    add(change.beforeSupportedScore.toString())
                    add(change.afterSupportedScore.toString())
                    add(change.afterReadinessImpact.toString())
                    add(change.causesNewVerdictShift.toString())
                    add(change.kind.name)
                    add(change.cause.name)
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

    private fun safeAdd(base: Long, delta: Long): Long {
        require(base >= 0L)
        require(delta >= 0L)
        require(base <= Long.MAX_VALUE - delta)
        return base + delta
    }
}
