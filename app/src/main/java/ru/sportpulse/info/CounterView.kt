package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class CounterReviewState(
    val title: String,
    val selectionTitle: String,
    val explanation: String,
    val marker: String
) {
    UNCHECKED(
        title = "Не проверено",
        selectionTitle = "Не проверено",
        explanation = "Альтернативную трактовку фактора еще не искали.",
        marker = "?"
    ),
    CLEAR(
        title = "Проверено",
        selectionTitle = "Проверено: версия выдержала",
        explanation = "Существенных фактов против исходной версии не найдено.",
        marker = "ОК"
    ),
    MIXED(
        title = "Есть спор",
        selectionTitle = "Есть спор: факты расходятся",
        explanation = "Независимые факты допускают разные трактовки.",
        marker = "≈"
    ),
    REFUTED(
        title = "Контрфакт",
        selectionTitle = "Контрфакт: версия остановлена",
        explanation = "Найден факт, который ломает исходную версию.",
        marker = "!"
    )
}

internal data class CounterReviewAssessment(
    val states: List<CounterReviewState>
) {
    init {
        require(states.size == SignalFactor.values().size)
    }

    fun state(factor: SignalFactor): CounterReviewState {
        return states[factor.ordinal]
    }

    fun withState(
        factor: SignalFactor,
        state: CounterReviewState
    ): CounterReviewAssessment {
        val updated = states.toMutableList()
        updated[factor.ordinal] = state
        return copy(states = updated)
    }

    companion object {
        fun unchecked(): CounterReviewAssessment {
            return CounterReviewAssessment(
                List(SignalFactor.values().size) {
                    CounterReviewState.UNCHECKED
                }
            )
        }

        fun cleared(): CounterReviewAssessment {
            return CounterReviewAssessment(
                List(SignalFactor.values().size) {
                    CounterReviewState.CLEAR
                }
            )
        }
    }
}

internal enum class CounterViewVerdict {
    OPEN,
    BALANCED,
    MIXED,
    REFUTED
}

internal data class CounterViewFactor(
    val factor: SignalFactor,
    val reviewState: CounterReviewState,
    val supportedValue: Int,
    val readinessImpact: Int
) {
    init {
        require(supportedValue in 0..100)
        require(readinessImpact >= 0)
    }
}

internal data class CounterViewResult(
    val review: CounterReviewAssessment,
    val evidenceResult: EvidenceResult,
    val factors: List<CounterViewFactor>,
    val verdict: CounterViewVerdict,
    val reviewedCount: Int,
    val openCount: Int,
    val mixedCount: Int,
    val refutedCount: Int,
    val decisionCeiling: SavedDecision,
    val defensibleVerdict: SignalVerdict,
    val nextFactor: SignalFactor?,
    val fingerprint: String
) {
    init {
        require(factors.size == SignalFactor.values().size)
        require(reviewedCount in 0..factors.size)
        require(openCount in 0..factors.size)
        require(mixedCount in 0..factors.size)
        require(refutedCount in 0..factors.size)
        require(reviewedCount + openCount == factors.size)
        require(
            factors.map(CounterViewFactor::factor).distinct().size ==
                factors.size
        )
        require(fingerprint.length == 64)
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun factor(factor: SignalFactor): CounterViewFactor {
        return factors[factor.ordinal]
    }

    fun allows(decision: SavedDecision): Boolean {
        return decision.ordinal <= decisionCeiling.ordinal
    }
}

internal object CounterViewEngine {
    private const val VERSION = "sport-pulse-counter-view-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        review: CounterReviewAssessment
    ): CounterViewResult {
        val evidenceResult = EvidenceEngine.evaluate(
            assessment = assessment,
            evidence = evidence
        )
        val supported = evidenceResult.effectiveAssessment
        val baselineSignal = evidenceResult.effectiveSignal
        val factors = SignalFactor.values().map { factor ->
            val withoutFactor = SignalEngine.evaluate(
                supported.withValue(factor, 0)
            )
            CounterViewFactor(
                factor = factor,
                reviewState = review.state(factor),
                supportedValue = supported.value(factor),
                readinessImpact = (
                    baselineSignal.readiness -
                        withoutFactor.readiness
                    ).coerceAtLeast(0)
            )
        }
        val reviewedCount = factors.count {
            it.reviewState != CounterReviewState.UNCHECKED
        }
        val openCount = factors.size - reviewedCount
        val mixedCount = factors.count {
            it.reviewState == CounterReviewState.MIXED
        }
        val refutedCount = factors.count {
            it.reviewState == CounterReviewState.REFUTED
        }
        val verdict = when {
            refutedCount > 0 -> CounterViewVerdict.REFUTED
            mixedCount > 0 -> CounterViewVerdict.MIXED
            openCount > 0 -> CounterViewVerdict.OPEN
            else -> CounterViewVerdict.BALANCED
        }
        val decisionCeiling = when {
            refutedCount > 0 -> SavedDecision.SKIP
            reviewedCount < 3 -> SavedDecision.SKIP
            mixedCount > 0 -> SavedDecision.OBSERVE
            reviewedCount < factors.size -> SavedDecision.OBSERVE
            else -> SavedDecision.DATA_READY
        }
        val defensibleVerdict = minVerdict(
            baselineSignal.verdict,
            decisionCeiling.toSignalVerdict()
        )
        val nextFactor = factors
            .filter {
                it.reviewState ==
                    CounterReviewState.UNCHECKED
            }
            .maxWithOrNull(
                compareBy<CounterViewFactor> {
                    it.readinessImpact
                }.thenBy {
                    it.supportedValue
                }.thenBy {
                    -it.factor.ordinal
                }
            )
            ?.factor
        val fingerprint = fingerprint(
            assessment = assessment,
            evidence = evidence,
            review = review
        )

        return CounterViewResult(
            review = review,
            evidenceResult = evidenceResult,
            factors = factors,
            verdict = verdict,
            reviewedCount = reviewedCount,
            openCount = openCount,
            mixedCount = mixedCount,
            refutedCount = refutedCount,
            decisionCeiling = decisionCeiling,
            defensibleVerdict = defensibleVerdict,
            nextFactor = nextFactor,
            fingerprint = fingerprint
        )
    }

    private fun SavedDecision.toSignalVerdict(): SignalVerdict {
        return when (this) {
            SavedDecision.SKIP -> SignalVerdict.SKIP
            SavedDecision.OBSERVE -> SignalVerdict.OBSERVE
            SavedDecision.DATA_READY -> SignalVerdict.READY
        }
    }

    private fun minVerdict(
        first: SignalVerdict,
        second: SignalVerdict
    ): SignalVerdict {
        return if (first.ordinal <= second.ordinal) {
            first
        } else {
            second
        }
    }

    private fun fingerprint(
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        review: CounterReviewAssessment
    ): String {
        val payload = listOf(
            VERSION,
            assessment.values.joinToString(","),
            evidence.levels.joinToString(",") { it.name },
            review.states.joinToString(",") { it.name }
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(
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
}
