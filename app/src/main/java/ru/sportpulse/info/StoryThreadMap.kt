package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class StoryThreadMapState {
    EMPTY,
    TAMPERED,
    DETACHED,
    MOVED,
    MISSED,
    OPEN,
    RESOLVED
}

internal data class StoryThreadMapCandidate(
    val eventId: String,
    val match: String?,
    val sport: String?,
    val region: String?,
    val catalogOrder: Int?,
    val read: StoryThreadReadResult,
    val result: StoryThreadResult?,
    val nextMoment: StoryBeaconMoment?
) {
    init {
        require(eventId.isNotBlank())
        require(catalogOrder == null || catalogOrder >= 0)
        val presentInCatalog = catalogOrder != null
        require(presentInCatalog == (match != null))
        require(presentInCatalog == (sport != null))
        require(presentInCatalog == (region != null))
        require(match == null || match.isNotBlank())
        require(sport == null || sport.isNotBlank())
        require(region == null || region.isNotBlank())
        require(read.integrity != StoryThreadIntegrity.EMPTY)

        when (read.integrity) {
            StoryThreadIntegrity.EMPTY -> Unit
            StoryThreadIntegrity.TAMPERED -> {
                require(result == null)
                require(nextMoment == null)
            }
            StoryThreadIntegrity.VALID -> {
                val thread = checkNotNull(read.thread)
                require(thread.eventId == eventId)
                require(presentInCatalog == (result != null))
                require(result == null || result.thread == thread)
                require(
                    nextMoment == null || result?.status ==
                        StoryThreadStatus.OPEN || result?.status ==
                        StoryThreadStatus.MOVED
                )
                require(nextMoment == null || nextMoment.at != null)
            }
        }
    }
}

internal data class StoryThreadMapEntry(
    val eventId: String,
    val match: String?,
    val sport: String?,
    val region: String?,
    val catalogOrder: Int?,
    val state: StoryThreadMapState,
    val thread: StoryThread?,
    val result: StoryThreadResult?,
    val nextMoment: StoryBeaconMoment?
) {
    init {
        require(eventId.isNotBlank())
        require(state != StoryThreadMapState.EMPTY)
        require(catalogOrder == null || catalogOrder >= 0)
        val presentInCatalog = catalogOrder != null
        require(presentInCatalog == (match != null))
        require(presentInCatalog == (sport != null))
        require(presentInCatalog == (region != null))
        when (state) {
            StoryThreadMapState.EMPTY -> Unit
            StoryThreadMapState.TAMPERED -> {
                require(thread == null)
                require(result == null)
                require(nextMoment == null)
            }
            StoryThreadMapState.DETACHED -> {
                require(!presentInCatalog)
                require(thread != null)
                require(result == null)
                require(nextMoment == null)
            }
            StoryThreadMapState.MOVED -> {
                require(presentInCatalog)
                require(thread != null)
                require(result?.thread == thread)
                require(result.status == StoryThreadStatus.MOVED)
                require(nextMoment == null || nextMoment.at != null)
            }
            StoryThreadMapState.OPEN -> {
                require(presentInCatalog)
                require(thread != null)
                require(result?.thread == thread)
                require(result.status == StoryThreadStatus.OPEN)
                require(nextMoment == null || nextMoment.at != null)
            }
            StoryThreadMapState.MISSED -> {
                require(presentInCatalog)
                require(thread != null)
                require(result?.thread == thread)
                require(result.status == StoryThreadStatus.MISSED)
                require(nextMoment == null)
            }
            StoryThreadMapState.RESOLVED -> {
                require(presentInCatalog)
                require(thread != null)
                require(result?.thread == thread)
                require(result.status == StoryThreadStatus.RESOLVED)
                require(nextMoment == null)
            }
        }
    }

    val presentInCatalog: Boolean
        get() = catalogOrder != null
}

internal data class StoryThreadMapResult(
    val entries: List<StoryThreadMapEntry>,
    val leadingState: StoryThreadMapState,
    val tamperedCount: Int,
    val detachedCount: Int,
    val movedCount: Int,
    val missedCount: Int,
    val openCount: Int,
    val resolvedCount: Int,
    val fingerprint: String
) {
    init {
        require(
            entries.map(StoryThreadMapEntry::eventId)
                .distinct().size == entries.size
        )
        require(tamperedCount >= 0)
        require(detachedCount >= 0)
        require(movedCount >= 0)
        require(missedCount >= 0)
        require(openCount >= 0)
        require(resolvedCount >= 0)
        require(
            tamperedCount == entries.count {
                it.state == StoryThreadMapState.TAMPERED
            }
        )
        require(
            detachedCount == entries.count {
                it.state == StoryThreadMapState.DETACHED
            }
        )
        require(
            movedCount == entries.count {
                it.state == StoryThreadMapState.MOVED
            }
        )
        require(
            missedCount == entries.count {
                it.state == StoryThreadMapState.MISSED
            }
        )
        require(
            openCount == entries.count {
                it.state == StoryThreadMapState.OPEN
            }
        )
        require(
            resolvedCount == entries.count {
                it.state == StoryThreadMapState.RESOLVED
            }
        )
        require(
            tamperedCount + detachedCount + movedCount +
                missedCount + openCount + resolvedCount ==
                entries.size
        )
        require(
            (entries.isEmpty() &&
                leadingState == StoryThreadMapState.EMPTY) ||
                (entries.isNotEmpty() &&
                    leadingState == entries.first().state)
        )
        require(HEX_64.matches(fingerprint))
    }

    val visibleEntries: List<StoryThreadMapEntry>
        get() = entries.take(StoryThreadMapPolicy.VISIBLE_THREADS)

    val linkIssueCount: Int
        get() = tamperedCount + detachedCount

    val settledCount: Int
        get() = missedCount + resolvedCount

    val outsideCatalogCount: Int
        get() = entries.count { !it.presentInCatalog }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryThreadMapPolicy {
    const val VISIBLE_THREADS = 4
}

internal object StoryThreadMapEngine {
    private const val VERSION = "sport-pulse-story-thread-map-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        candidates: List<StoryThreadMapCandidate>
    ): StoryThreadMapResult {
        require(
            candidates.map(StoryThreadMapCandidate::eventId)
                .distinct().size == candidates.size
        )
        require(
            candidates.mapNotNull {
                it.catalogOrder
            }.distinct().size == candidates.count {
                it.catalogOrder != null
            }
        )

        val entries = candidates.map(::entryFor).sortedWith(
            compareBy<StoryThreadMapEntry> {
                rank(it.state)
            }.thenBy {
                it.nextMoment?.at ?: Long.MAX_VALUE
            }.thenBy {
                it.catalogOrder ?: Int.MAX_VALUE
            }.thenBy {
                it.eventId
            }
        )
        val fingerprint = fingerprintFor(entries)
        return StoryThreadMapResult(
            entries = entries,
            leadingState = entries.firstOrNull()?.state
                ?: StoryThreadMapState.EMPTY,
            tamperedCount = entries.count {
                it.state == StoryThreadMapState.TAMPERED
            },
            detachedCount = entries.count {
                it.state == StoryThreadMapState.DETACHED
            },
            movedCount = entries.count {
                it.state == StoryThreadMapState.MOVED
            },
            missedCount = entries.count {
                it.state == StoryThreadMapState.MISSED
            },
            openCount = entries.count {
                it.state == StoryThreadMapState.OPEN
            },
            resolvedCount = entries.count {
                it.state == StoryThreadMapState.RESOLVED
            },
            fingerprint = fingerprint
        )
    }

    private fun entryFor(
        candidate: StoryThreadMapCandidate
    ): StoryThreadMapEntry {
        val state = when (candidate.read.integrity) {
            StoryThreadIntegrity.EMPTY -> error("Empty thread candidate")
            StoryThreadIntegrity.TAMPERED ->
                StoryThreadMapState.TAMPERED
            StoryThreadIntegrity.VALID -> when {
                candidate.catalogOrder == null ->
                    StoryThreadMapState.DETACHED
                candidate.result?.status == StoryThreadStatus.MOVED ->
                    StoryThreadMapState.MOVED
                candidate.result?.status == StoryThreadStatus.MISSED ->
                    StoryThreadMapState.MISSED
                candidate.result?.status == StoryThreadStatus.OPEN ->
                    StoryThreadMapState.OPEN
                candidate.result?.status == StoryThreadStatus.RESOLVED ->
                    StoryThreadMapState.RESOLVED
                else -> error("Valid catalog thread has no result")
            }
        }
        val keepMoment = state == StoryThreadMapState.OPEN ||
            state == StoryThreadMapState.MOVED
        return StoryThreadMapEntry(
            eventId = candidate.eventId,
            match = candidate.match,
            sport = candidate.sport,
            region = candidate.region,
            catalogOrder = candidate.catalogOrder,
            state = state,
            thread = candidate.read.thread,
            result = candidate.result,
            nextMoment = candidate.nextMoment.takeIf { keepMoment }
        )
    }

    private fun rank(state: StoryThreadMapState): Int {
        return when (state) {
            StoryThreadMapState.TAMPERED -> 0
            StoryThreadMapState.DETACHED -> 1
            StoryThreadMapState.MOVED -> 2
            StoryThreadMapState.MISSED -> 3
            StoryThreadMapState.OPEN -> 4
            StoryThreadMapState.RESOLVED -> 5
            StoryThreadMapState.EMPTY -> 6
        }
    }

    private fun fingerprintFor(
        entries: List<StoryThreadMapEntry>
    ): String {
        val payload = buildString {
            appendToken(VERSION)
            entries.forEach { entry ->
                appendToken(entry.eventId)
                appendToken(entry.match.orEmpty())
                appendToken(entry.sport.orEmpty())
                appendToken(entry.region.orEmpty())
                appendToken(entry.catalogOrder?.toString().orEmpty())
                appendToken(entry.state.name)
                appendToken(entry.thread?.fingerprint.orEmpty())
                appendToken(entry.result?.fingerprint.orEmpty())
                appendToken(entry.nextMoment?.kind?.name.orEmpty())
                appendToken(entry.nextMoment?.at?.toString().orEmpty())
                appendToken(entry.nextMoment?.action?.name.orEmpty())
                appendToken(
                    entry.nextMoment?.factors
                        ?.joinToString(",") { it.name }
                        .orEmpty()
                )
            }
        }
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

    private fun StringBuilder.appendToken(value: String) {
        append(value.length)
        append(':')
        append(value)
        append('|')
    }
}
