package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class DecisionDistanceFactor(
    val question: String,
    val stopReason: String
) {
    CHASING(
        question = "Пытаетесь отыграться?",
        stopReason = "Есть желание отыграться."
    ),
    MONEY(
        question = "Используются заемные или необходимые деньги?",
        stopReason = "Затронуты заемные или необходимые деньги."
    ),
    CONDITION(
        question = "Есть стресс, сильная усталость или алкоголь?",
        stopReason = "Состояние может мешать спокойному решению."
    ),
    LIMITS(
        question = "Личный лимит времени или средств превышен?",
        stopReason = "Личный лимит уже превышен."
    )
}

internal enum class DecisionDistanceAnswer {
    UNANSWERED,
    NO,
    YES
}

internal data class DecisionDistanceAssessment(
    val answers: List<DecisionDistanceAnswer>
) {
    init {
        require(answers.size == DecisionDistanceFactor.values().size)
    }

    fun answer(
        factor: DecisionDistanceFactor
    ): DecisionDistanceAnswer = answers[factor.ordinal]

    fun withAnswer(
        factor: DecisionDistanceFactor,
        answer: DecisionDistanceAnswer
    ): DecisionDistanceAssessment {
        val updated = answers.toMutableList()
        updated[factor.ordinal] = answer
        return copy(answers = updated)
    }

    companion object {
        fun unanswered(): DecisionDistanceAssessment {
            return DecisionDistanceAssessment(
                List(DecisionDistanceFactor.values().size) {
                    DecisionDistanceAnswer.UNANSWERED
                }
            )
        }

        fun clear(): DecisionDistanceAssessment {
            return DecisionDistanceAssessment(
                List(DecisionDistanceFactor.values().size) {
                    DecisionDistanceAnswer.NO
                }
            )
        }
    }
}

internal enum class DecisionDistanceStatus {
    INCOMPLETE,
    STOP,
    CLEAR
}

internal data class DecisionDistanceResult(
    val assessment: DecisionDistanceAssessment,
    val checkedAt: Long,
    val status: DecisionDistanceStatus,
    val riskFactors: List<DecisionDistanceFactor>,
    val unansweredFactors: List<DecisionDistanceFactor>,
    val fingerprint: String
) {
    init {
        require(checkedAt >= 0L)
        require(
            fingerprint.length == 64 &&
                fingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        )
        require(
            riskFactors == DecisionDistanceFactor.values().filter {
                assessment.answer(it) == DecisionDistanceAnswer.YES
            }
        )
        require(
            unansweredFactors == DecisionDistanceFactor.values().filter {
                assessment.answer(it) ==
                    DecisionDistanceAnswer.UNANSWERED
            }
        )
    }

    val answeredCount: Int
        get() = DecisionDistanceFactor.values().size -
            unansweredFactors.size

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal data class DecisionDistanceClearance(
    val checkedAt: Long,
    val expiresAt: Long,
    val fingerprint: String
) {
    init {
        require(checkedAt >= 0L)
        require(expiresAt > checkedAt)
        require(
            fingerprint.length == 64 &&
                fingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        )
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun isValidAt(now: Long): Boolean {
        require(now >= 0L)
        return now in checkedAt until expiresAt
    }
}

internal object DecisionDistancePolicy {
    const val CLEARANCE_MILLIS = 30L * 60L * 1000L

    fun requiresClearance(decision: SavedDecision): Boolean {
        return decision == SavedDecision.DATA_READY
    }

    fun allows(
        decision: SavedDecision,
        clearance: DecisionDistanceClearance?,
        now: Long
    ): Boolean {
        require(now >= 0L)
        return !requiresClearance(decision) ||
            clearance?.isValidAt(now) == true
    }
}

internal object DecisionDistanceEngine {
    private const val RESULT_VERSION =
        "sport-pulse-decision-distance-v1"
    private const val CLEARANCE_VERSION =
        "sport-pulse-decision-distance-clearance-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        assessment: DecisionDistanceAssessment,
        checkedAt: Long
    ): DecisionDistanceResult {
        require(checkedAt >= 0L)
        val riskFactors = DecisionDistanceFactor.values().filter {
            assessment.answer(it) == DecisionDistanceAnswer.YES
        }
        val unansweredFactors = DecisionDistanceFactor.values().filter {
            assessment.answer(it) ==
                DecisionDistanceAnswer.UNANSWERED
        }
        val status = when {
            riskFactors.isNotEmpty() -> DecisionDistanceStatus.STOP
            unansweredFactors.isNotEmpty() ->
                DecisionDistanceStatus.INCOMPLETE
            else -> DecisionDistanceStatus.CLEAR
        }
        return DecisionDistanceResult(
            assessment = assessment,
            checkedAt = checkedAt,
            status = status,
            riskFactors = riskFactors,
            unansweredFactors = unansweredFactors,
            fingerprint = digest(
                listOf(
                    RESULT_VERSION,
                    checkedAt.toString(),
                    assessment.answers.joinToString(",") {
                        it.name
                    }
                ).joinToString("|")
            )
        )
    }

    fun clearanceFor(
        result: DecisionDistanceResult
    ): DecisionDistanceClearance {
        require(result.status == DecisionDistanceStatus.CLEAR)
        require(
            result.checkedAt <= Long.MAX_VALUE -
                DecisionDistancePolicy.CLEARANCE_MILLIS
        )
        val expiresAt = result.checkedAt +
            DecisionDistancePolicy.CLEARANCE_MILLIS
        return DecisionDistanceClearance(
            checkedAt = result.checkedAt,
            expiresAt = expiresAt,
            fingerprint = clearanceFingerprint(
                checkedAt = result.checkedAt,
                expiresAt = expiresAt,
                resultFingerprint = result.fingerprint
            )
        )
    }

    internal fun expectedClearanceFingerprint(
        checkedAt: Long,
        expiresAt: Long
    ): String {
        require(checkedAt >= 0L)
        require(
            expiresAt - checkedAt ==
                DecisionDistancePolicy.CLEARANCE_MILLIS
        )
        val result = evaluate(
            assessment = DecisionDistanceAssessment.clear(),
            checkedAt = checkedAt
        )
        return clearanceFingerprint(
            checkedAt = checkedAt,
            expiresAt = expiresAt,
            resultFingerprint = result.fingerprint
        )
    }

    private fun clearanceFingerprint(
        checkedAt: Long,
        expiresAt: Long,
        resultFingerprint: String
    ): String {
        return digest(
            listOf(
                CLEARANCE_VERSION,
                checkedAt.toString(),
                expiresAt.toString(),
                resultFingerprint
            ).joinToString("|")
        )
    }

    private fun digest(payload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
            payload.toByteArray(StandardCharsets.UTF_8)
        )
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

internal object DecisionDistanceClearanceCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 4

    fun encode(clearance: DecisionDistanceClearance): String {
        require(
            DecisionDistanceEngine.expectedClearanceFingerprint(
                checkedAt = clearance.checkedAt,
                expiresAt = clearance.expiresAt
            ) == clearance.fingerprint
        )
        return listOf(
            VERSION,
            clearance.checkedAt.toString(),
            clearance.expiresAt.toString(),
            clearance.fingerprint
        ).joinToString("|")
    }

    fun decode(encoded: String): DecisionDistanceClearance? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val checkedAt = parts[1].toLong()
            val expiresAt = parts[2].toLong()
            val fingerprint = parts[3].lowercase()
            require(
                DecisionDistanceEngine.expectedClearanceFingerprint(
                    checkedAt = checkedAt,
                    expiresAt = expiresAt
                ) == fingerprint
            )
            DecisionDistanceClearance(
                checkedAt = checkedAt,
                expiresAt = expiresAt,
                fingerprint = fingerprint
            )
        }.getOrNull()
    }
}
