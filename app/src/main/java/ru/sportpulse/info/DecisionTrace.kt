package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal data class DecisionSnapshot(
    val eventId: String,
    val decision: SavedDecision,
    val savedAt: Long,
    val assessment: SignalAssessment,
    val evidence: EvidenceAssessment,
    val timeline: EvidenceTimeline,
    val counterReview: CounterReviewAssessment,
    val distanceClearanceFingerprint: String?,
    val attentionBudgetFingerprint: String?,
    val formatVersion: Int,
    val fingerprint: String
) {
    init {
        require(formatVersion in 1..4)
        require(
            distanceClearanceFingerprint == null ||
                (
                    distanceClearanceFingerprint.length == 64 &&
                        distanceClearanceFingerprint.all {
                            it in '0'..'9' || it in 'a'..'f'
                        }
                    )
        )
        if (formatVersion < 3) {
            require(distanceClearanceFingerprint == null)
        }
        require(
            attentionBudgetFingerprint == null ||
                (
                    attentionBudgetFingerprint.length == 64 &&
                        attentionBudgetFingerprint.all {
                            it in '0'..'9' || it in 'a'..'f'
                        }
                    )
        )
        if (formatVersion < 4) {
            require(attentionBudgetFingerprint == null)
        }
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object DecisionSnapshotFactory {
    fun create(
        eventId: String,
        decision: SavedDecision,
        savedAt: Long,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        counterReview: CounterReviewAssessment =
            CounterReviewAssessment.cleared(),
        distanceClearanceFingerprint: String? = null,
        attentionBudgetFingerprint: String? = null
    ): DecisionSnapshot {
        require(eventId.isNotBlank())
        require(savedAt >= 0L)
        val draft = DecisionSnapshot(
            eventId = eventId,
            decision = decision,
            savedAt = savedAt,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            counterReview = counterReview,
            distanceClearanceFingerprint =
                distanceClearanceFingerprint,
            attentionBudgetFingerprint =
                attentionBudgetFingerprint,
            formatVersion = 4,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = DecisionSnapshotCodec.fingerprintFor(draft)
        )
    }
}

internal object DecisionSnapshotCodec {
    private const val LEGACY_VERSION = "1"
    private const val PREVIOUS_VERSION = "2"
    private const val DISTANCE_VERSION = "3"
    private const val CURRENT_VERSION = "4"
    private const val LEGACY_PART_COUNT = 8
    private const val PREVIOUS_PART_COUNT = 9
    private const val DISTANCE_PART_COUNT = 10
    private const val CURRENT_PART_COUNT = 11
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(snapshot: DecisionSnapshot): String {
        val expectedFingerprint = fingerprintFor(snapshot)
        require(
            MessageDigest.isEqual(
                expectedFingerprint.toByteArray(StandardCharsets.US_ASCII),
                snapshot.fingerprint.lowercase()
                    .toByteArray(StandardCharsets.US_ASCII)
            )
        )
        return "${payload(snapshot)}|$expectedFingerprint"
    }

    fun decode(encoded: String): DecisionSnapshot? {
        return runCatching {
            val parts = encoded.split('|')
            val formatVersion = when (parts.firstOrNull()) {
                LEGACY_VERSION -> {
                    require(parts.size == LEGACY_PART_COUNT)
                    1
                }
                PREVIOUS_VERSION -> {
                    require(parts.size == PREVIOUS_PART_COUNT)
                    2
                }
                DISTANCE_VERSION -> {
                    require(parts.size == DISTANCE_PART_COUNT)
                    3
                }
                CURRENT_VERSION -> {
                    require(parts.size == CURRENT_PART_COUNT)
                    4
                }
                else -> error("Unsupported snapshot version")
            }
            val eventId = String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            )
            val decision = SavedDecision.valueOf(parts[2])
            val savedAt = parts[3].toLong()
            val assessment = SignalAssessment(
                parseInts(parts[4], SignalFactor.values().size)
            )
            val evidence = EvidenceAssessment(
                parseStrings(parts[5], SignalFactor.values().size)
                    .map(EvidenceLevel::valueOf)
            )
            val timeline = EvidenceTimeline(
                parseLongs(parts[6], SignalFactor.values().size)
            )
            val counterReview = if (formatVersion == 1) {
                CounterReviewAssessment.unchecked()
            } else {
                CounterReviewAssessment(
                    parseStrings(
                        parts[7],
                        SignalFactor.values().size
                    ).map(CounterReviewState::valueOf)
                )
            }
            val distanceClearanceFingerprint =
                if (formatVersion < 3 || parts[8] == "-") {
                    null
                } else {
                    parts[8].lowercase().also {
                        require(it.length == 64)
                    }
                }
            val attentionBudgetFingerprint =
                if (formatVersion < 4 || parts[9] == "-") {
                    null
                } else {
                    parts[9].lowercase().also {
                        require(it.length == 64)
                    }
                }
            val fingerprintIndex = when (formatVersion) {
                1 -> 7
                2 -> 8
                3 -> 9
                else -> 10
            }
            val snapshot = DecisionSnapshot(
                eventId = eventId,
                decision = decision,
                savedAt = savedAt,
                assessment = assessment,
                evidence = evidence,
                timeline = timeline,
                counterReview = counterReview,
                distanceClearanceFingerprint =
                    distanceClearanceFingerprint,
                attentionBudgetFingerprint =
                    attentionBudgetFingerprint,
                formatVersion = formatVersion,
                fingerprint = parts[fingerprintIndex].lowercase()
            )
            require(
                MessageDigest.isEqual(
                    fingerprintFor(snapshot).toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    snapshot.fingerprint.toByteArray(
                        StandardCharsets.US_ASCII
                    )
                )
            )
            snapshot
        }.getOrNull()
    }

    internal fun fingerprintFor(snapshot: DecisionSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            payload(snapshot).toByteArray(StandardCharsets.UTF_8)
        )
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun payload(snapshot: DecisionSnapshot): String {
        val encodedEventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                snapshot.eventId.toByteArray(StandardCharsets.UTF_8)
            )
        val common = listOf(
            snapshot.formatVersion.toString(),
            encodedEventId,
            snapshot.decision.name,
            snapshot.savedAt.toString(),
            snapshot.assessment.values.joinToString(","),
            snapshot.evidence.levels.joinToString(",") { it.name },
            snapshot.timeline.checkedAt.joinToString(",")
        )
        if (snapshot.formatVersion == 1) {
            return common.joinToString("|")
        }
        val withCounterReview =
            common + snapshot.counterReview.states.joinToString(",") {
                it.name
            }
        if (snapshot.formatVersion == 2) {
            return withCounterReview.joinToString("|")
        }
        val withDistance =
            withCounterReview +
                (snapshot.distanceClearanceFingerprint ?: "-")
        return if (snapshot.formatVersion == 3) {
            withDistance.joinToString("|")
        } else {
            (
                withDistance +
                    (snapshot.attentionBudgetFingerprint ?: "-")
                ).joinToString("|")
        }
    }

    private fun parseInts(value: String, expectedSize: Int): List<Int> {
        return value.split(',')
            .also { require(it.size == expectedSize) }
            .map(String::toInt)
    }

    private fun parseLongs(value: String, expectedSize: Int): List<Long> {
        return value.split(',')
            .also { require(it.size == expectedSize) }
            .map(String::toLong)
    }

    private fun parseStrings(
        value: String,
        expectedSize: Int
    ): List<String> {
        return value.split(',')
            .also { require(it.size == expectedSize) }
    }
}

internal enum class DecisionChangeCause {
    FACTS,
    CONFIRMATION,
    FRESHNESS,
    COUNTERVIEW
}

internal data class DecisionFactorDelta(
    val factor: SignalFactor,
    val beforeValue: Int,
    val currentValue: Int,
    val beforeEvidence: EvidenceLevel,
    val currentEvidence: EvidenceLevel,
    val beforeCounterReview: CounterReviewState,
    val currentCounterReview: CounterReviewState,
    val causes: Set<DecisionChangeCause>
) {
    val valueDelta: Int
        get() = currentValue - beforeValue

    val hasChanged: Boolean
        get() = causes.isNotEmpty()
}

internal data class DecisionTraceResult(
    val snapshot: DecisionSnapshot,
    val baselineFreshness: FreshnessResult,
    val currentFreshness: FreshnessResult,
    val baselineEvidenceResult: EvidenceResult,
    val currentEvidenceResult: EvidenceResult,
    val baselineCounterView: CounterViewResult,
    val currentCounterView: CounterViewResult,
    val factorDeltas: List<DecisionFactorDelta>
) {
    val changedFactors: List<DecisionFactorDelta>
        get() = factorDeltas.filter(DecisionFactorDelta::hasChanged)

    val readinessDelta: Int
        get() = currentEvidenceResult.effectiveSignal.readiness -
            baselineEvidenceResult.effectiveSignal.readiness

    val verdictChanged: Boolean
        get() = baselineEvidenceResult.effectiveSignal.verdict !=
            currentEvidenceResult.effectiveSignal.verdict

    val counterViewChanged: Boolean
        get() = baselineCounterView.fingerprint !=
            currentCounterView.fingerprint
}

internal object DecisionTraceEngine {
    fun compare(
        snapshot: DecisionSnapshot,
        currentAssessment: SignalAssessment,
        currentEvidence: EvidenceAssessment,
        currentTimeline: EvidenceTimeline,
        currentCounterReview: CounterReviewAssessment =
            snapshot.counterReview,
        now: Long
    ): DecisionTraceResult {
        require(now >= 0L)
        val baselineFreshness = FreshnessEngine.evaluate(
            evidence = snapshot.evidence,
            timeline = snapshot.timeline,
            now = snapshot.savedAt
        )
        val currentFreshness = FreshnessEngine.evaluate(
            evidence = currentEvidence,
            timeline = currentTimeline,
            now = now
        )
        val baselineEvidenceResult = EvidenceEngine.evaluate(
            snapshot.assessment,
            baselineFreshness.effectiveEvidence
        )
        val currentEvidenceResult = EvidenceEngine.evaluate(
            currentAssessment,
            currentFreshness.effectiveEvidence
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
        val factorDeltas = SignalFactor.values().map { factor ->
            val causes = linkedSetOf<DecisionChangeCause>()
            if (
                currentAssessment.value(factor) !=
                snapshot.assessment.value(factor)
            ) {
                causes += DecisionChangeCause.FACTS
            }
            val confirmationChanged =
                currentEvidence.level(factor) !=
                    snapshot.evidence.level(factor) ||
                    currentTimeline.checkedAt(factor) !=
                    snapshot.timeline.checkedAt(factor)
            if (confirmationChanged) {
                causes += DecisionChangeCause.CONFIRMATION
            } else if (
                currentFreshness.effectiveEvidence.level(factor) !=
                baselineFreshness.effectiveEvidence.level(factor)
            ) {
                causes += DecisionChangeCause.FRESHNESS
            }
            if (
                currentCounterReview.state(factor) !=
                snapshot.counterReview.state(factor)
            ) {
                causes += DecisionChangeCause.COUNTERVIEW
            }
            DecisionFactorDelta(
                factor = factor,
                beforeValue = baselineEvidenceResult
                    .effectiveAssessment
                    .value(factor),
                currentValue = currentEvidenceResult
                    .effectiveAssessment
                    .value(factor),
                beforeEvidence = baselineFreshness
                    .effectiveEvidence
                    .level(factor),
                currentEvidence = currentFreshness
                    .effectiveEvidence
                    .level(factor),
                beforeCounterReview = snapshot.counterReview
                    .state(factor),
                currentCounterReview = currentCounterReview
                    .state(factor),
                causes = causes
            )
        }
        return DecisionTraceResult(
            snapshot = snapshot,
            baselineFreshness = baselineFreshness,
            currentFreshness = currentFreshness,
            baselineEvidenceResult = baselineEvidenceResult,
            currentEvidenceResult = currentEvidenceResult,
            baselineCounterView = baselineCounterView,
            currentCounterView = currentCounterView,
            factorDeltas = factorDeltas
        )
    }
}
