package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class SourceAuditState(
    val title: String,
    val shortTitle: String,
    val explanation: String
) {
    UNAUDITED(
        title = "Не проверено",
        shortTitle = "?",
        explanation = "Независимость источников ещё не проверена"
    ),
    SHARED_LINEAGE(
        title = "Одна цепочка",
        shortTitle = "ЭХО",
        explanation = "Оба подтверждения ведут к одному материалу"
    ),
    INDEPENDENT(
        title = "Независимы",
        shortTitle = "OK",
        explanation = "Разные владельцы и первичные цепочки"
    ),
    CONFLICT(
        title = "Расхождение",
        shortTitle = "!",
        explanation = "По ключевому факту есть противоречие"
    )
}

internal data class SourceAuditAssessment(
    val states: List<SourceAuditState>
) {
    init {
        require(states.size == SignalFactor.values().size)
    }

    fun state(factor: SignalFactor): SourceAuditState {
        return states[factor.ordinal]
    }

    fun withState(
        factor: SignalFactor,
        state: SourceAuditState
    ): SourceAuditAssessment {
        val updated = states.toMutableList()
        updated[factor.ordinal] = state
        return copy(states = updated)
    }

    companion object {
        fun unaudited(): SourceAuditAssessment {
            return SourceAuditAssessment(
                List(SignalFactor.values().size) {
                    SourceAuditState.UNAUDITED
                }
            )
        }
    }
}

internal enum class SourceIntegrityVerdict {
    NO_QUORUM,
    OPEN,
    AUDITED,
    ECHO,
    CONFLICT
}

internal data class SourceIntegrityFactor(
    val factor: SignalFactor,
    val claimedLevel: EvidenceLevel,
    val auditState: SourceAuditState,
    val effectiveLevel: EvidenceLevel
) {
    val isQuorumClaim: Boolean
        get() = claimedLevel == EvidenceLevel.QUORUM

    val isCapped: Boolean
        get() = effectiveLevel.scoreCap < claimedLevel.scoreCap
}

internal data class SourceIntegrityResult(
    val claimedEvidence: EvidenceAssessment,
    val audit: SourceAuditAssessment,
    val effectiveEvidence: EvidenceAssessment,
    val factors: List<SourceIntegrityFactor>,
    val verdict: SourceIntegrityVerdict,
    val claimedQuorumCount: Int,
    val acceptedQuorumCount: Int,
    val unauditedQuorumCount: Int,
    val echoQuorumCount: Int,
    val conflictCount: Int,
    val cappedFactors: List<SignalFactor>,
    val fingerprint: String
) {
    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object SourceIntegrityEngine {
    private const val FINGERPRINT_VERSION = "source-integrity-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        claimedEvidence: EvidenceAssessment,
        audit: SourceAuditAssessment
    ): SourceIntegrityResult {
        val factors = SignalFactor.values().map { factor ->
            val claimed = claimedEvidence.level(factor)
            val auditState = audit.state(factor)
            SourceIntegrityFactor(
                factor = factor,
                claimedLevel = claimed,
                auditState = auditState,
                effectiveLevel = effectiveLevel(
                    claimed = claimed,
                    auditState = auditState
                )
            )
        }
        val claimedQuorumCount = factors.count(
            SourceIntegrityFactor::isQuorumClaim
        )
        val acceptedQuorumCount = factors.count {
            it.effectiveLevel == EvidenceLevel.QUORUM
        }
        val unauditedQuorumCount = factors.count {
            it.isQuorumClaim &&
                it.auditState == SourceAuditState.UNAUDITED
        }
        val echoQuorumCount = factors.count {
            it.isQuorumClaim &&
                it.auditState == SourceAuditState.SHARED_LINEAGE
        }
        val conflictCount = factors.count {
            it.auditState == SourceAuditState.CONFLICT
        }
        val verdict = when {
            conflictCount > 0 -> SourceIntegrityVerdict.CONFLICT
            echoQuorumCount > 0 -> SourceIntegrityVerdict.ECHO
            unauditedQuorumCount > 0 -> SourceIntegrityVerdict.OPEN
            claimedQuorumCount > 0 -> SourceIntegrityVerdict.AUDITED
            else -> SourceIntegrityVerdict.NO_QUORUM
        }
        val effectiveEvidence = EvidenceAssessment(
            factors.map(SourceIntegrityFactor::effectiveLevel)
        )
        val fingerprint = fingerprintFor(factors)

        return SourceIntegrityResult(
            claimedEvidence = claimedEvidence,
            audit = audit,
            effectiveEvidence = effectiveEvidence,
            factors = factors,
            verdict = verdict,
            claimedQuorumCount = claimedQuorumCount,
            acceptedQuorumCount = acceptedQuorumCount,
            unauditedQuorumCount = unauditedQuorumCount,
            echoQuorumCount = echoQuorumCount,
            conflictCount = conflictCount,
            cappedFactors = factors.filter(
                SourceIntegrityFactor::isCapped
            ).map(SourceIntegrityFactor::factor),
            fingerprint = fingerprint
        )
    }

    private fun effectiveLevel(
        claimed: EvidenceLevel,
        auditState: SourceAuditState
    ): EvidenceLevel {
        return when (auditState) {
            SourceAuditState.CONFLICT ->
                EvidenceLevel.UNCONFIRMED
            SourceAuditState.INDEPENDENT ->
                claimed
            SourceAuditState.UNAUDITED,
            SourceAuditState.SHARED_LINEAGE -> {
                if (claimed == EvidenceLevel.QUORUM) {
                    EvidenceLevel.SINGLE_SOURCE
                } else {
                    claimed
                }
            }
        }
    }

    private fun fingerprintFor(
        factors: List<SourceIntegrityFactor>
    ): String {
        val payload = buildString {
            append(FINGERPRINT_VERSION)
            factors.forEach { factor ->
                append('|')
                append(factor.factor.name)
                append(':')
                append(factor.claimedLevel.name)
                append(':')
                append(factor.auditState.name)
                append(':')
                append(factor.effectiveLevel.name)
            }
        }
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
