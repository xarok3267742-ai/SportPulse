package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal enum class CrossSourceStatus {
    EMPTY,
    BUILDING,
    DISTRIBUTED,
    REUSED
}

internal data class CrossSourceOrigin(
    val identity: String,
    val label: String,
    val factors: List<SignalFactor>,
    val mentionCount: Int
) {
    init {
        require(identity.isNotBlank())
        require(label.isNotBlank())
        require(factors.isNotEmpty())
        require(factors == factors.sortedBy(SignalFactor::ordinal))
        require(mentionCount >= factors.size)
    }

    val factorCount: Int
        get() = factors.size

    val isReusedAcrossFactors: Boolean
        get() = factorCount > 1
}

internal data class CrossSourceMap(
    val eventId: String,
    val status: CrossSourceStatus,
    val origins: List<CrossSourceOrigin>,
    val sourceMentionCount: Int,
    val coveredFactorCount: Int,
    val reusedOriginCount: Int,
    val reusedFactorCount: Int,
    val dominantOrigin: CrossSourceOrigin?,
    val diversificationFactor: SignalFactor?,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(sourceMentionCount >= origins.size)
        require(coveredFactorCount in 0..SignalFactor.values().size)
        require(reusedOriginCount in 0..origins.size)
        require(reusedFactorCount in 0..coveredFactorCount)
        require((status == CrossSourceStatus.EMPTY) == origins.isEmpty())
        require(
            (status == CrossSourceStatus.REUSED) ==
                (reusedOriginCount > 0)
        )
        require(
            (diversificationFactor != null) ==
                (status == CrossSourceStatus.REUSED)
        )
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    val uniqueOriginCount: Int
        get() = origins.size

    val reusedOrigins: List<CrossSourceOrigin>
        get() = origins.filter(CrossSourceOrigin::isReusedAcrossFactors)

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase(Locale.ROOT)
}

internal object CrossSourceMapEngine {
    private const val VERSION = "sport-pulse-cross-source-map-v1"
    private val whitespace = Regex("\\s+")
    private val hex = "0123456789abcdef".toCharArray()

    fun create(register: FactRegister): CrossSourceMap {
        val mentions = register.entries.flatMap { entry ->
            val receipt = entry.receipt ?: return@flatMap emptyList()
            buildList {
                add(sourceMention(receipt.primarySource, entry.factor))
                receipt.secondarySource?.let { source ->
                    add(sourceMention(source, entry.factor))
                }
            }
        }
        val origins = mentions
            .groupBy(SourceMention::identity)
            .map { (identity, grouped) ->
                CrossSourceOrigin(
                    identity = identity,
                    label = grouped
                        .map(SourceMention::label)
                        .sortedWith(
                            compareBy<String>(String::length)
                                .thenBy(String::lowercase)
                        )
                        .first(),
                    factors = grouped
                        .map(SourceMention::factor)
                        .distinct()
                        .sortedBy(SignalFactor::ordinal),
                    mentionCount = grouped.size
                )
            }
            .sortedWith(
                compareByDescending<CrossSourceOrigin> {
                    it.factorCount
                }.thenByDescending {
                    it.mentionCount
                }.thenBy(CrossSourceOrigin::identity)
            )
        val coveredFactors = mentions
            .map(SourceMention::factor)
            .distinct()
        val reusedOrigins = origins.filter {
            it.isReusedAcrossFactors
        }
        val reusedFactors = reusedOrigins
            .flatMap(CrossSourceOrigin::factors)
            .distinct()
        val status = when {
            origins.isEmpty() -> CrossSourceStatus.EMPTY
            reusedOrigins.isNotEmpty() -> CrossSourceStatus.REUSED
            coveredFactors.size < 2 -> CrossSourceStatus.BUILDING
            else -> CrossSourceStatus.DISTRIBUTED
        }
        val dominantOrigin = reusedOrigins.firstOrNull()
        val diversificationFactor = dominantOrigin?.let { origin ->
            val originCountByFactor = mentions
                .groupBy(SourceMention::factor)
                .mapValues { (_, factorMentions) ->
                    factorMentions.map(SourceMention::identity)
                        .distinct()
                        .size
                }
            origin.factors.minWithOrNull(
                compareBy<SignalFactor> {
                    originCountByFactor[it] ?: 0
                }.thenBy { factor ->
                    register.entries.first {
                        it.factor == factor
                    }.state.priority
                }.thenBy(SignalFactor::ordinal)
            )
        }
        val payload = buildList {
            add(VERSION)
            add(register.eventId)
            origins.forEach { origin ->
                add(origin.identity)
                add(origin.mentionCount.toString())
                add(origin.factors.joinToString(",", transform = SignalFactor::name))
            }
        }.joinToString("|")

        return CrossSourceMap(
            eventId = register.eventId,
            status = status,
            origins = origins,
            sourceMentionCount = mentions.size,
            coveredFactorCount = coveredFactors.size,
            reusedOriginCount = reusedOrigins.size,
            reusedFactorCount = reusedFactors.size,
            dominantOrigin = dominantOrigin,
            diversificationFactor = diversificationFactor,
            fingerprint = digest(payload)
        )
    }

    private fun sourceMention(
        source: String,
        factor: SignalFactor
    ): SourceMention {
        val clean = source.trim().replace(whitespace, " ")
        val identity = FactReceiptFactory.sourceIdentity(clean)
        return SourceMention(
            identity = identity,
            label = if ('.' in identity) identity else clean,
            factor = factor
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

    private data class SourceMention(
        val identity: String,
        val label: String,
        val factor: SignalFactor
    )
}
