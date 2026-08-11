package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal data class DecisionGuardBreach(
    val eventId: String,
    val planSeal: String,
    val triggeredAt: Long,
    val causes: List<DecisionGuardCause>,
    val factorValue: Int?,
    val evidence: EvidenceLevel?,
    val readiness: Int,
    val verdict: SignalVerdict,
    val fingerprint: String
) {
    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object DecisionGuardBreachFactory {
    fun create(
        result: DecisionGuardResult,
        triggeredAt: Long
    ): DecisionGuardBreach {
        require(result.causes.isNotEmpty())
        return create(
            eventId = result.plan.eventId,
            planSeal = result.plan.seal,
            triggeredAt = triggeredAt,
            causes = result.causes,
            factorValue = result.currentFactorValue,
            evidence = result.currentEvidence,
            readiness = result.currentResult
                .effectiveSignal
                .readiness,
            verdict = result.currentResult
                .effectiveSignal
                .verdict
        )
    }

    internal fun create(
        eventId: String,
        planSeal: String,
        triggeredAt: Long,
        causes: List<DecisionGuardCause>,
        factorValue: Int?,
        evidence: EvidenceLevel?,
        readiness: Int,
        verdict: SignalVerdict
    ): DecisionGuardBreach {
        require(eventId.isNotBlank())
        require(HEX_64.matches(planSeal.lowercase()))
        require(triggeredAt >= 0L)
        require(causes.isNotEmpty())
        require(causes.distinct().size == causes.size)
        require(factorValue == null || factorValue in 0..100)
        require(readiness in 0..100)
        val draft = DecisionGuardBreach(
            eventId = eventId,
            planSeal = planSeal.lowercase(),
            triggeredAt = triggeredAt,
            causes = causes.sortedBy(DecisionGuardCause::ordinal),
            factorValue = factorValue,
            evidence = evidence,
            readiness = readiness,
            verdict = verdict,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint =
                DecisionGuardBreachCodec.fingerprintFor(
                    draft
                )
        )
    }

    private val HEX_64 = Regex("[0-9a-f]{64}")
}

internal object DecisionGuardBreachCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 10
    private const val EMPTY = "-"
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(
        breach: DecisionGuardBreach
    ): String {
        val expected = fingerprintFor(breach)
        require(
            MessageDigest.isEqual(
                expected.toByteArray(
                    StandardCharsets.US_ASCII
                ),
                breach.fingerprint.lowercase().toByteArray(
                    StandardCharsets.US_ASCII
                )
            )
        )
        return "${payload(breach)}|$expected"
    }

    fun decode(
        encoded: String
    ): DecisionGuardBreach? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val eventId = String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            )
            val causes = parts[4]
                .split(',')
                .map(DecisionGuardCause::valueOf)
            val breach = DecisionGuardBreachFactory.create(
                eventId = eventId,
                planSeal = parts[2],
                triggeredAt = parts[3].toLong(),
                causes = causes,
                factorValue = parts[5]
                    .takeUnless { it == EMPTY }
                    ?.toInt(),
                evidence = parts[6]
                    .takeUnless { it == EMPTY }
                    ?.let(EvidenceLevel::valueOf),
                readiness = parts[7].toInt(),
                verdict = SignalVerdict.valueOf(parts[8])
            )
            require(
                MessageDigest.isEqual(
                    breach.fingerprint.toByteArray(
                        StandardCharsets.US_ASCII
                    ),
                    parts[9].lowercase().toByteArray(
                        StandardCharsets.US_ASCII
                    )
                )
            )
            breach
        }.getOrNull()
    }

    internal fun fingerprintFor(
        breach: DecisionGuardBreach
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                payload(breach).toByteArray(
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

    private fun payload(
        breach: DecisionGuardBreach
    ): String {
        val encodedEventId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                breach.eventId.toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        return listOf(
            VERSION,
            encodedEventId,
            breach.planSeal.lowercase(),
            breach.triggeredAt.toString(),
            breach.causes.joinToString(",") { it.name },
            breach.factorValue?.toString() ?: EMPTY,
            breach.evidence?.name ?: EMPTY,
            breach.readiness.toString(),
            breach.verdict.name
        ).joinToString("|")
    }
}
