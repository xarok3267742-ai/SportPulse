package ru.sportpulse.info

import java.security.MessageDigest
import kotlin.math.roundToInt

internal data class CalibrationRecord(
    val snapshot: DecisionSnapshot,
    val review: PostEventReview
)

internal object StoredCalibrationRecordCatalog {
    private const val PREFIX = "post_event_review_"

    fun keyFor(eventId: String): String = PREFIX + eventId

    fun decode(
        stored: Map<String, *>,
        snapshotFor: (String) -> DecisionSnapshot?
    ): List<CalibrationRecord> {
        return stored.asSequence()
            .filter { (key, _) -> key.startsWith(PREFIX) }
            .mapNotNull { (key, value) ->
                val review = (value as? String)
                    ?.let(PostEventReviewCodec::decode)
                    ?.takeIf(PostEventReview::isFinalized)
                    ?: return@mapNotNull null
                if (key != keyFor(review.eventId)) {
                    return@mapNotNull null
                }
                val snapshot = snapshotFor(review.eventId)
                    ?.takeIf {
                        it.fingerprint ==
                            review.decisionFingerprint
                    }
                    ?: return@mapNotNull null
                CalibrationRecord(snapshot, review)
            }
            .sortedWith(
                compareBy<CalibrationRecord> {
                    requireNotNull(it.review.finalizedAt)
                }.thenBy {
                    it.review.fingerprint
                }
            )
            .toList()
    }
}

internal enum class CalibrationMemoryStatus {
    LEARNING,
    STABLE,
    UNEVEN,
    BLIND_SPOT
}

internal enum class CalibrationTrendStatus {
    INSUFFICIENT,
    IMPROVING,
    STABLE,
    DECLINING
}

internal data class CalibrationTrend(
    val status: CalibrationTrendStatus,
    val previousScore: Int?,
    val recentScore: Int?,
    val delta: Int?
)

internal data class CalibrationFactorProfile(
    val factor: SignalFactor,
    val score: Int?,
    val verifiedCount: Int,
    val confirmedCount: Int,
    val partialCount: Int,
    val disprovedCount: Int,
    val unknownCount: Int,
    val criticalMissCount: Int
)

internal data class CalibrationMemory(
    val status: CalibrationMemoryStatus,
    val overallScore: Int?,
    val reviewCount: Int,
    val verifiedFactorCount: Int,
    val coveragePercent: Int,
    val criticalMissCount: Int,
    val factorProfiles: List<CalibrationFactorProfile>,
    val focusProfile: CalibrationFactorProfile?,
    val trend: CalibrationTrend,
    val reviewResults: List<PostEventReviewResult>,
    val fingerprint: String
) {
    val shortFingerprint: String
        get() = fingerprint.take(10).uppercase()
}

internal object CalibrationMemoryEngine {
    fun evaluate(
        records: List<CalibrationRecord>
    ): CalibrationMemory {
        val sorted = records.sortedWith(
            compareBy<CalibrationRecord> {
                requireNotNull(it.review.finalizedAt)
            }.thenBy {
                it.review.fingerprint
            }
        )
        require(
            sorted.map { it.review.eventId }
                .distinct()
                .size == sorted.size
        )
        val results = sorted.map { record ->
            require(record.review.isFinalized)
            PostEventReviewEngine.evaluate(
                snapshot = record.snapshot,
                review = record.review
            )
        }
        val allCredits = results.flatMap(::credits)
        val overallScore = score(allCredits)
        val factorProfiles = SignalFactor.values().map { factor ->
            factorProfile(results, factor)
        }
        val criticalProfiles = factorProfiles.filter {
            it.criticalMissCount > 0
        }
        val repeatedProfiles = factorProfiles
            .filter { it.verifiedCount >= REPEATED_FACTOR_SAMPLE }
        val focusCandidates = when {
            criticalProfiles.isNotEmpty() -> criticalProfiles
            repeatedProfiles.isNotEmpty() -> repeatedProfiles
            else ->
                factorProfiles.filter { it.verifiedCount > 0 }
        }
        val focusProfile = focusCandidates.sortedWith(
            compareByDescending<CalibrationFactorProfile> {
                it.criticalMissCount
            }.thenBy {
                it.score ?: Int.MAX_VALUE
            }.thenByDescending {
                it.verifiedCount
            }.thenBy {
                it.factor.ordinal
            }
        ).firstOrNull()
        val criticalMissCount = results.sumOf {
            it.criticalMisses.size
        }
        val recentCriticalMiss = results
            .takeLast(RECENT_CRITICAL_WINDOW)
            .any { it.criticalMisses.isNotEmpty() }
        val repeatedBlindSpot = factorProfiles.any {
            it.verifiedCount >= REPEATED_FACTOR_SAMPLE &&
                requireNotNull(it.score) < FRAGILE_SCORE
        }
        val verifiedFactorCount = allCredits.size
        val coveragePercent = if (results.isEmpty()) {
            0
        } else {
            (
                verifiedFactorCount * 100.0 /
                    (
                        results.size *
                            SignalFactor.values().size
                        )
                ).roundToInt()
        }
        val status = when {
            results.size < MIN_PROFILE_REVIEWS ||
                verifiedFactorCount < MIN_PROFILE_FACTORS ->
                CalibrationMemoryStatus.LEARNING
            recentCriticalMiss || repeatedBlindSpot ||
                requireNotNull(overallScore) < FRAGILE_SCORE ->
                CalibrationMemoryStatus.BLIND_SPOT
            overallScore < STABLE_SCORE ->
                CalibrationMemoryStatus.UNEVEN
            else ->
                CalibrationMemoryStatus.STABLE
        }
        return CalibrationMemory(
            status = status,
            overallScore = overallScore,
            reviewCount = results.size,
            verifiedFactorCount = verifiedFactorCount,
            coveragePercent = coveragePercent,
            criticalMissCount = criticalMissCount,
            factorProfiles = factorProfiles,
            focusProfile = focusProfile,
            trend = trend(results),
            reviewResults = results,
            fingerprint = fingerprint(sorted)
        )
    }

    private fun factorProfile(
        results: List<PostEventReviewResult>,
        factor: SignalFactor
    ): CalibrationFactorProfile {
        val factorResults = results.map {
            it.factorResults[factor.ordinal]
        }
        val credits = factorResults.mapNotNull {
            it.outcome.credit
        }
        return CalibrationFactorProfile(
            factor = factor,
            score = score(credits),
            verifiedCount = credits.size,
            confirmedCount = factorResults.count {
                it.outcome == PostEventOutcome.CONFIRMED
            },
            partialCount = factorResults.count {
                it.outcome == PostEventOutcome.PARTIAL
            },
            disprovedCount = factorResults.count {
                it.outcome == PostEventOutcome.DISPROVED
            },
            unknownCount = factorResults.count {
                it.outcome == PostEventOutcome.UNKNOWN
            },
            criticalMissCount = factorResults.count {
                it.isCriticalMiss
            }
        )
    }

    private fun trend(
        results: List<PostEventReviewResult>
    ): CalibrationTrend {
        val scored = results.filter { credits(it).isNotEmpty() }
        if (scored.size < MIN_TREND_REVIEWS) {
            return CalibrationTrend(
                status = CalibrationTrendStatus.INSUFFICIENT,
                previousScore = null,
                recentScore = null,
                delta = null
            )
        }
        val latest = scored.takeLast(MIN_TREND_REVIEWS)
        val previousScore = requireNotNull(
            score(latest.take(2).flatMap(::credits))
        )
        val recentScore = requireNotNull(
            score(latest.takeLast(2).flatMap(::credits))
        )
        val delta = recentScore - previousScore
        val status = when {
            delta >= TREND_THRESHOLD ->
                CalibrationTrendStatus.IMPROVING
            delta <= -TREND_THRESHOLD ->
                CalibrationTrendStatus.DECLINING
            else ->
                CalibrationTrendStatus.STABLE
        }
        return CalibrationTrend(
            status = status,
            previousScore = previousScore,
            recentScore = recentScore,
            delta = delta
        )
    }

    private fun credits(
        result: PostEventReviewResult
    ): List<Int> {
        return result.factorResults.mapNotNull {
            it.outcome.credit
        }
    }

    private fun score(credits: List<Int>): Int? {
        return credits
            .takeIf(List<Int>::isNotEmpty)
            ?.average()
            ?.roundToInt()
    }

    private fun fingerprint(
        records: List<CalibrationRecord>
    ): String {
        var chain = sha256(FINGERPRINT_VERSION)
        records.forEach {
            chain = sha256(
                "$chain|${it.review.fingerprint.lowercase()}"
            )
        }
        return chain
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private const val FINGERPRINT_VERSION = "1"
    private const val MIN_PROFILE_REVIEWS = 3
    private const val MIN_PROFILE_FACTORS = 9
    private const val REPEATED_FACTOR_SAMPLE = 2
    private const val RECENT_CRITICAL_WINDOW = 5
    private const val MIN_TREND_REVIEWS = 4
    private const val TREND_THRESHOLD = 8
    private const val FRAGILE_SCORE = 45
    private const val STABLE_SCORE = 75
}
