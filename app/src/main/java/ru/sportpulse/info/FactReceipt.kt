package ru.sportpulse.info

import org.json.JSONObject
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal enum class FactReceiptCoverage(
    val title: String,
    val score: Int,
    val explanation: String
) {
    CORE(
        title = "Базовый факт",
        score = 35,
        explanation = "Подтверждён основной тезис без всех деталей"
    ),
    DETAILS(
        title = "Факт и детали",
        score = 60,
        explanation = "Проверены ключевые условия и границы тезиса"
    ),
    COUNTERCHECKED(
        title = "Полная проверка",
        score = 85,
        explanation = "Проверены детали и найден возможный контраргумент"
    )
}

internal data class FactReceipt(
    val eventId: String,
    val factor: SignalFactor,
    val statement: String,
    val primarySource: String,
    val secondarySource: String?,
    val sourceAuditState: SourceAuditState,
    val coverage: FactReceiptCoverage,
    val checkedAt: Long,
    val claimedEvidence: EvidenceLevel,
    val effectiveEvidence: EvidenceLevel,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(statement.length in FactReceiptPolicy.MIN_STATEMENT_LENGTH..FactReceiptPolicy.MAX_STATEMENT_LENGTH)
        require(primarySource.length in FactReceiptPolicy.MIN_SOURCE_LENGTH..FactReceiptPolicy.MAX_SOURCE_LENGTH)
        require(
            secondarySource == null ||
                secondarySource.length in
                FactReceiptPolicy.MIN_SOURCE_LENGTH..FactReceiptPolicy.MAX_SOURCE_LENGTH
        )
        require(checkedAt >= 0L)
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(
            (secondarySource == null) ==
                (claimedEvidence == EvidenceLevel.SINGLE_SOURCE)
        )
    }

    val sourceCount: Int
        get() = if (secondarySource == null) 1 else 2

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase(Locale.ROOT)
}

internal enum class FactReceiptIntegrity {
    EMPTY,
    VALID,
    TAMPERED
}

internal data class FactReceiptReadResult(
    val integrity: FactReceiptIntegrity,
    val receipt: FactReceipt?
) {
    init {
        require(
            (integrity == FactReceiptIntegrity.VALID) ==
                (receipt != null)
        )
    }
}

internal object FactReceiptPolicy {
    const val MIN_STATEMENT_LENGTH = 8
    const val MAX_STATEMENT_LENGTH = 220
    const val MIN_SOURCE_LENGTH = 3
    const val MAX_SOURCE_LENGTH = 160
}

internal object FactReceiptFactory {
    private const val VERSION = "sport-pulse-fact-receipt-v1"
    private val whitespace = Regex("\\s+")
    private val identityNoise = Regex("[^\\p{L}\\p{N}]+")
    private val hex = "0123456789abcdef".toCharArray()

    fun create(
        eventId: String,
        factor: SignalFactor,
        statement: String,
        primarySource: String,
        secondarySource: String?,
        sourceAuditState: SourceAuditState,
        coverage: FactReceiptCoverage,
        checkedAt: Long
    ): FactReceipt {
        require(checkedAt >= 0L)
        val cleanEventId = eventId.trim()
        val cleanStatement = clean(statement)
        val cleanPrimary = clean(primarySource)
        val cleanSecondary = secondarySource
            ?.let(::clean)
            ?.takeIf(String::isNotBlank)
        require(cleanEventId.isNotBlank())
        require(
            cleanStatement.length in
                FactReceiptPolicy.MIN_STATEMENT_LENGTH..FactReceiptPolicy.MAX_STATEMENT_LENGTH
        )
        require(
            cleanPrimary.length in
                FactReceiptPolicy.MIN_SOURCE_LENGTH..FactReceiptPolicy.MAX_SOURCE_LENGTH
        )
        require(
            cleanSecondary == null ||
                cleanSecondary.length in
                FactReceiptPolicy.MIN_SOURCE_LENGTH..FactReceiptPolicy.MAX_SOURCE_LENGTH
        )

        val actualAudit = when {
            cleanSecondary == null -> SourceAuditState.UNAUDITED
            sourceIdentity(cleanPrimary) ==
                sourceIdentity(cleanSecondary) ->
                SourceAuditState.SHARED_LINEAGE
            else -> sourceAuditState
        }
        val claimedEvidence = if (cleanSecondary == null) {
            EvidenceLevel.SINGLE_SOURCE
        } else {
            EvidenceLevel.QUORUM
        }
        val effectiveEvidence = effectiveEvidence(
            factor = factor,
            claimed = claimedEvidence,
            audit = actualAudit
        )
        val fingerprint = digest(
            listOf(
                VERSION,
                cleanEventId,
                factor.name,
                cleanStatement,
                cleanPrimary,
                cleanSecondary.orEmpty(),
                actualAudit.name,
                coverage.name,
                coverage.score.toString(),
                checkedAt.toString(),
                claimedEvidence.name,
                effectiveEvidence.name
            ).joinToString("|")
        )
        return FactReceipt(
            eventId = cleanEventId,
            factor = factor,
            statement = cleanStatement,
            primarySource = cleanPrimary,
            secondarySource = cleanSecondary,
            sourceAuditState = actualAudit,
            coverage = coverage,
            checkedAt = checkedAt,
            claimedEvidence = claimedEvidence,
            effectiveEvidence = effectiveEvidence,
            fingerprint = fingerprint
        )
    }

    internal fun sourceIdentity(source: String): String {
        val cleanSource = clean(source)
        val candidate = if ("://" in cleanSource) {
            cleanSource
        } else {
            "https://$cleanSource"
        }
        val host = runCatching { URI(candidate).host }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.trim('.')
            ?.takeIf(String::isNotBlank)
        if (host != null) return host
        return Normalizer.normalize(cleanSource, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(identityNoise, "")
    }

    private fun effectiveEvidence(
        factor: SignalFactor,
        claimed: EvidenceLevel,
        audit: SourceAuditState
    ): EvidenceLevel {
        val claimedAssessment = EvidenceAssessment(
            SignalFactor.values().map { current ->
                if (current == factor) {
                    claimed
                } else {
                    EvidenceLevel.UNCONFIRMED
                }
            }
        )
        val auditAssessment = SourceAuditAssessment(
            SignalFactor.values().map { current ->
                if (current == factor) {
                    audit
                } else {
                    SourceAuditState.UNAUDITED
                }
            }
        )
        return SourceIntegrityEngine.evaluate(
            claimedEvidence = claimedAssessment,
            audit = auditAssessment
        ).effectiveEvidence.level(factor)
    }

    private fun clean(value: String): String {
        return value.trim().replace(whitespace, " ")
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

internal object FactReceiptCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(receipt: FactReceipt): String {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("eventId", receipt.eventId)
            put("factor", receipt.factor.name)
            put("statement", receipt.statement)
            put("primarySource", receipt.primarySource)
            put("secondarySource", receipt.secondarySource)
            put("sourceAuditState", receipt.sourceAuditState.name)
            put("coverage", receipt.coverage.name)
            put("checkedAt", receipt.checkedAt)
            put("fingerprint", receipt.fingerprint)
        }.toString()
    }

    fun decode(json: String): FactReceipt {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        val receipt = FactReceiptFactory.create(
            eventId = root.getString("eventId"),
            factor = SignalFactor.valueOf(root.getString("factor")),
            statement = root.getString("statement"),
            primarySource = root.getString("primarySource"),
            secondarySource = root.optString("secondarySource")
                .takeIf(String::isNotBlank),
            sourceAuditState = SourceAuditState.valueOf(
                root.getString("sourceAuditState")
            ),
            coverage = FactReceiptCoverage.valueOf(
                root.getString("coverage")
            ),
            checkedAt = root.getLong("checkedAt")
        )
        require(root.getString("fingerprint") == receipt.fingerprint)
        return receipt
    }
}
