package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.roundToInt

internal enum class PostEventOutcome(
    val title: String,
    val shortTitle: String,
    val credit: Int?
) {
    UNREVIEWED(
        title = "Не оценено",
        shortTitle = "—",
        credit = null
    ),
    CONFIRMED(
        title = "Подтвердилось",
        shortTitle = "Да",
        credit = 100
    ),
    PARTIAL(
        title = "Частично",
        shortTitle = "Часть",
        credit = 50
    ),
    DISPROVED(
        title = "Не подтвердилось",
        shortTitle = "Нет",
        credit = 0
    ),
    UNKNOWN(
        title = "Не удалось проверить",
        shortTitle = "Нет данных",
        credit = null
    )
}

internal data class PostEventReview(
    val eventId: String,
    val decisionFingerprint: String,
    val updatedAt: Long,
    val finalizedAt: Long?,
    val outcomes: List<PostEventOutcome>,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(
            decisionFingerprint.matches(
                Regex("[0-9a-f]{64}")
            )
        )
        require(updatedAt >= 0L)
        require(finalizedAt == null || finalizedAt >= updatedAt)
        require(outcomes.size == SignalFactor.values().size)
        require(
            finalizedAt == null ||
                outcomes.none {
                    it == PostEventOutcome.UNREVIEWED
                }
        )
    }

    val isFinalized: Boolean
        get() = finalizedAt != null

    val answeredCount: Int
        get() = outcomes.count {
            it != PostEventOutcome.UNREVIEWED
        }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun outcome(factor: SignalFactor): PostEventOutcome {
        return outcomes[factor.ordinal]
    }
}

internal object PostEventReviewFactory {
    fun start(
        snapshot: DecisionSnapshot,
        now: Long
    ): PostEventReview {
        require(now >= snapshot.savedAt)
        return seal(
            eventId = snapshot.eventId,
            decisionFingerprint = snapshot.fingerprint,
            updatedAt = now,
            finalizedAt = null,
            outcomes = List(SignalFactor.values().size) {
                PostEventOutcome.UNREVIEWED
            }
        )
    }

    fun setOutcome(
        review: PostEventReview,
        snapshot: DecisionSnapshot,
        factor: SignalFactor,
        outcome: PostEventOutcome,
        now: Long
    ): PostEventReview {
        requireLinked(review, snapshot)
        require(!review.isFinalized)
        require(outcome != PostEventOutcome.UNREVIEWED)
        require(now >= review.updatedAt)
        val outcomes = review.outcomes.toMutableList()
        outcomes[factor.ordinal] = outcome
        return seal(
            eventId = review.eventId,
            decisionFingerprint = review.decisionFingerprint,
            updatedAt = now,
            finalizedAt = null,
            outcomes = outcomes
        )
    }

    fun finalize(
        review: PostEventReview,
        snapshot: DecisionSnapshot,
        now: Long
    ): PostEventReview {
        requireLinked(review, snapshot)
        require(!review.isFinalized)
        require(
            review.outcomes.none {
                it == PostEventOutcome.UNREVIEWED
            }
        )
        require(now >= review.updatedAt)
        return seal(
            eventId = review.eventId,
            decisionFingerprint = review.decisionFingerprint,
            updatedAt = now,
            finalizedAt = now,
            outcomes = review.outcomes
        )
    }

    private fun requireLinked(
        review: PostEventReview,
        snapshot: DecisionSnapshot
    ) {
        require(review.eventId == snapshot.eventId)
        require(
            MessageDigest.isEqual(
                review.decisionFingerprint.toByteArray(
                    StandardCharsets.US_ASCII
                ),
                snapshot.fingerprint.toByteArray(
                    StandardCharsets.US_ASCII
                )
            )
        )
    }

    private fun seal(
        eventId: String,
        decisionFingerprint: String,
        updatedAt: Long,
        finalizedAt: Long?,
        outcomes: List<PostEventOutcome>
    ): PostEventReview {
        val draft = PostEventReview(
            eventId = eventId,
            decisionFingerprint = decisionFingerprint.lowercase(),
            updatedAt = updatedAt,
            finalizedAt = finalizedAt,
            outcomes = outcomes,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = PostEventReviewCodec.fingerprintFor(draft)
        )
    }
}

internal object PostEventReviewCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 7
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(review: PostEventReview): String {
        val expectedFingerprint = fingerprintFor(review)
        require(
            MessageDigest.isEqual(
                expectedFingerprint.toByteArray(
                    StandardCharsets.US_ASCII
                ),
                review.fingerprint.lowercase().toByteArray(
                    StandardCharsets.US_ASCII
                )
            )
        )
        return "${payload(review)}|$expectedFingerprint"
    }

    fun decode(encoded: String): PostEventReview? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val eventId = String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            )
            val decisionFingerprint = parts[2].lowercase()
            val updatedAt = parts[3].toLong()
            val finalizedAt = parts[4]
                .takeUnless { it == "-" }
                ?.toLong()
            val outcomes = parts[5]
                .split(',')
                .also {
                    require(
                        it.size == SignalFactor.values().size
                    )
                }
                .map(PostEventOutcome::valueOf)
            val draft = PostEventReview(
                eventId = eventId,
                decisionFingerprint = decisionFingerprint,
                updatedAt = updatedAt,
                finalizedAt = finalizedAt,
                outcomes = outcomes,
                fingerprint = ""
            )
            val expectedFingerprint = fingerprintFor(draft)
            require(
                MessageDigest.isEqual(
                    expectedFingerprint.toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    parts[6].lowercase().toByteArray(
                        StandardCharsets.US_ASCII
                    )
                )
            )
            draft.copy(fingerprint = expectedFingerprint)
        }.getOrNull()
    }

    internal fun fingerprintFor(
        review: PostEventReview
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                payload(review).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun payload(review: PostEventReview): String {
        val eventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                review.eventId.toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        return listOf(
            VERSION,
            eventId,
            review.decisionFingerprint.lowercase(),
            review.updatedAt.toString(),
            review.finalizedAt?.toString() ?: "-",
            review.outcomes.joinToString(",") { it.name }
        ).joinToString("|")
    }
}

internal enum class PostEventReviewStatus {
    NOT_ENOUGH_DATA,
    RELIABLE,
    MIXED,
    FRAGILE
}

internal data class PostEventFactorResult(
    val factor: SignalFactor,
    val outcome: PostEventOutcome,
    val baselineValue: Int,
    val baselineEvidence: EvidenceLevel,
    val isCriticalMiss: Boolean
)

internal data class PostEventReviewResult(
    val review: PostEventReview,
    val status: PostEventReviewStatus,
    val reliabilityScore: Int?,
    val answeredCount: Int,
    val verifiedCount: Int,
    val factorResults: List<PostEventFactorResult>,
    val focusFactor: SignalFactor?
) {
    val criticalMisses: List<PostEventFactorResult>
        get() = factorResults.filter(
            PostEventFactorResult::isCriticalMiss
        )
}

internal object PostEventReviewEngine {
    fun evaluate(
        snapshot: DecisionSnapshot,
        review: PostEventReview
    ): PostEventReviewResult {
        require(snapshot.eventId == review.eventId)
        require(
            MessageDigest.isEqual(
                snapshot.fingerprint.toByteArray(
                    StandardCharsets.US_ASCII
                ),
                review.decisionFingerprint.toByteArray(
                    StandardCharsets.US_ASCII
                )
            )
        )
        require(review.updatedAt >= snapshot.savedAt)

        val freshness = FreshnessEngine.evaluate(
            evidence = snapshot.evidence,
            timeline = snapshot.timeline,
            now = snapshot.savedAt
        )
        val evidenceResult = EvidenceEngine.evaluate(
            assessment = snapshot.assessment,
            evidence = freshness.effectiveEvidence
        )
        val factorResults = SignalFactor.values().map { factor ->
            val outcome = review.outcome(factor)
            val baselineValue = evidenceResult
                .effectiveAssessment
                .value(factor)
            val baselineEvidence = freshness
                .effectiveEvidence
                .level(factor)
            PostEventFactorResult(
                factor = factor,
                outcome = outcome,
                baselineValue = baselineValue,
                baselineEvidence = baselineEvidence,
                isCriticalMiss =
                    outcome == PostEventOutcome.DISPROVED &&
                        baselineEvidence == EvidenceLevel.QUORUM &&
                        baselineValue >= SignalThresholds.OBSERVE
            )
        }
        val credits = factorResults.mapNotNull {
            it.outcome.credit
        }
        val reliabilityScore = credits
            .takeIf(List<Int>::isNotEmpty)
            ?.average()
            ?.roundToInt()
        val criticalMisses = factorResults.filter(
            PostEventFactorResult::isCriticalMiss
        )
        val status = when {
            credits.size < MIN_VERIFIED_FACTORS ->
                PostEventReviewStatus.NOT_ENOUGH_DATA
            criticalMisses.isNotEmpty() ||
                requireNotNull(reliabilityScore) < FRAGILE_SCORE ->
                PostEventReviewStatus.FRAGILE
            reliabilityScore < RELIABLE_SCORE ->
                PostEventReviewStatus.MIXED
            else ->
                PostEventReviewStatus.RELIABLE
        }
        val focusFactor = (
            criticalMisses.ifEmpty {
                factorResults.filter {
                    it.outcome == PostEventOutcome.DISPROVED
                }
            }.ifEmpty {
                factorResults.filter {
                    it.outcome == PostEventOutcome.PARTIAL
                }
            }.ifEmpty {
                factorResults.filter {
                    it.outcome == PostEventOutcome.UNKNOWN
                }
            }
        ).maxWithOrNull(
            compareBy<PostEventFactorResult> {
                it.baselineValue
            }.thenBy {
                -it.factor.ordinal
            }
        )?.factor

        return PostEventReviewResult(
            review = review,
            status = status,
            reliabilityScore = reliabilityScore,
            answeredCount = review.answeredCount,
            verifiedCount = credits.size,
            factorResults = factorResults,
            focusFactor = focusFactor
        )
    }

    private const val MIN_VERIFIED_FACTORS = 3
    private const val FRAGILE_SCORE = 45
    private const val RELIABLE_SCORE = 75
}
