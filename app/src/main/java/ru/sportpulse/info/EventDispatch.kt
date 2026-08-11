package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class EventDispatchStatus {
    EMPTY,
    STOP,
    ATTENTION,
    ACTIVE,
    STABLE
}

internal data class EventDispatchCandidate(
    val eventId: String,
    val sport: String,
    val match: String,
    val region: String,
    val bookmarked: Boolean,
    val initialized: Boolean,
    val catalogOrder: Int,
    val command: VerificationCommandResult
) {
    init {
        require(eventId.isNotBlank())
        require(sport.isNotBlank())
        require(match.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
        require(command.input.eventId == eventId)
    }
}

internal data class EventDispatchEntry(
    val eventId: String,
    val sport: String,
    val match: String,
    val region: String,
    val bookmarked: Boolean,
    val initialized: Boolean,
    val catalogOrder: Int,
    val commandStatus: VerificationCommandStatus,
    val primaryTask: VerificationCommandTask,
    val commandFingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(catalogOrder >= 0)
        require(commandFingerprint.length == 64)
    }
}

internal data class EventDispatchResult(
    val entries: List<EventDispatchEntry>,
    val status: EventDispatchStatus,
    val stopCount: Int,
    val attentionCount: Int,
    val activeCount: Int,
    val stableCount: Int,
    val fingerprint: String
) {
    init {
        require(entries.map(EventDispatchEntry::eventId).distinct().size == entries.size)
        require(stopCount >= 0)
        require(attentionCount >= 0)
        require(activeCount >= 0)
        require(stableCount >= 0)
        require(
            stopCount + attentionCount + activeCount +
                stableCount == entries.size
        )
        require(fingerprint.length == 64)
    }

    val visibleEntries: List<EventDispatchEntry>
        get() = entries.take(EventDispatchPolicy.VISIBLE_EVENTS)

    val bookmarkedCount: Int
        get() = entries.count(EventDispatchEntry::bookmarked)

    val initializedCount: Int
        get() = entries.count(EventDispatchEntry::initialized)

    val nextDeadlineAt: Long?
        get() = entries.mapNotNull {
            it.primaryTask.dueAt
        }.minOrNull()

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object EventDispatchPolicy {
    const val VISIBLE_EVENTS = 3
}

internal object EventDispatchEngine {
    private const val VERSION = "sport-pulse-event-dispatch-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        candidates: List<EventDispatchCandidate>
    ): EventDispatchResult {
        require(
            candidates.map(EventDispatchCandidate::eventId)
                .distinct().size == candidates.size
        )
        require(
            candidates.map(EventDispatchCandidate::catalogOrder)
                .distinct().size == candidates.size
        )
        val entries = candidates.map { candidate ->
            EventDispatchEntry(
                eventId = candidate.eventId,
                sport = candidate.sport,
                match = candidate.match,
                region = candidate.region,
                bookmarked = candidate.bookmarked,
                initialized = candidate.initialized,
                catalogOrder = candidate.catalogOrder,
                commandStatus = candidate.command.status,
                primaryTask = candidate.command.tasks.first(),
                commandFingerprint = candidate.command.fingerprint
            )
        }.sortedWith(
            compareBy<EventDispatchEntry> {
                it.primaryTask.priority.ordinal
            }.thenBy {
                it.primaryTask.dueAt ?: Long.MAX_VALUE
            }.thenBy {
                it.primaryTask.kinds.first().ordinal
            }.thenByDescending {
                it.bookmarked
            }.thenByDescending {
                it.initialized
            }.thenBy {
                it.catalogOrder
            }.thenBy {
                it.eventId
            }
        )
        val stopCount = entries.count {
            it.commandStatus == VerificationCommandStatus.STOP
        }
        val attentionCount = entries.count {
            it.commandStatus == VerificationCommandStatus.ATTENTION
        }
        val activeCount = entries.count {
            it.commandStatus == VerificationCommandStatus.ACTIVE
        }
        val stableCount = entries.count {
            it.commandStatus == VerificationCommandStatus.STABLE
        }
        val status = when {
            entries.isEmpty() -> EventDispatchStatus.EMPTY
            stopCount > 0 -> EventDispatchStatus.STOP
            attentionCount > 0 -> EventDispatchStatus.ATTENTION
            activeCount > 0 -> EventDispatchStatus.ACTIVE
            else -> EventDispatchStatus.STABLE
        }
        val fingerprint = digest(
            buildString {
                append(VERSION)
                entries.forEach { entry ->
                    append('|')
                    append(entry.eventId)
                    append(':')
                    append(entry.catalogOrder)
                    append(':')
                    append(entry.bookmarked)
                    append(':')
                    append(entry.initialized)
                    append(':')
                    append(entry.commandFingerprint)
                    append(':')
                    append(entry.primaryTask.fingerprint)
                }
            }
        )

        return EventDispatchResult(
            entries = entries,
            status = status,
            stopCount = stopCount,
            attentionCount = attentionCount,
            activeCount = activeCount,
            stableCount = stableCount,
            fingerprint = fingerprint
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
