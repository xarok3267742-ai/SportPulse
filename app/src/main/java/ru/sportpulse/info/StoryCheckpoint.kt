package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal enum class StoryCheckpointIntegrity {
    EMPTY,
    VALID,
    TAMPERED
}

internal data class StoryCheckpointReadResult(
    val integrity: StoryCheckpointIntegrity,
    val checkpoint: StoryCheckpoint?
) {
    init {
        require(
            (integrity == StoryCheckpointIntegrity.VALID) ==
                (checkpoint != null)
        )
    }
}

internal data class StoryCheckpoint(
    val eventId: String,
    val eventLabel: String,
    val savedAt: Long,
    val sourceState: EventStorySourceState,
    val chapterStates: List<EventStoryChapterState>,
    val phase: EventStoryPhase,
    val action: EventStoryAction,
    val actionFactor: SignalFactor?,
    val startAt: Long?,
    val reviewOpensAt: Long?,
    val storyFingerprint: String,
    val beaconState: StoryBeaconState,
    val beaconMoments: List<StoryBeaconMoment>,
    val beaconFingerprint: String,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(savedAt >= 0L)
        require(
            chapterStates.size == EventStoryChapter.values().size
        )
        require((startAt == null) == (reviewOpensAt == null))
        require(reviewOpensAt == null || reviewOpensAt >= startAt!!)
        require(
            actionFactor == null || action == EventStoryAction.OPEN_FACTS
        )
        require(HEX_64.matches(storyFingerprint))
        require(beaconMoments.size <= MAX_BEACON_MOMENTS)
        require(HEX_64.matches(beaconFingerprint))
        require(fingerprint.isEmpty() || HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun chapterState(
        chapter: EventStoryChapter
    ): EventStoryChapterState = chapterStates[chapter.ordinal]

    private companion object {
        const val MAX_BEACON_MOMENTS = 5
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryCheckpointFactory {
    fun create(
        story: EventStoryResult,
        beacon: StoryBeaconResult,
        savedAt: Long
    ): StoryCheckpoint {
        require(savedAt >= 0L)
        require(story.eventId == beacon.eventId)
        val draft = StoryCheckpoint(
            eventId = story.eventId,
            eventLabel = story.eventLabel,
            savedAt = savedAt,
            sourceState = story.sourceState,
            chapterStates = story.chapters.map { it.state },
            phase = story.phase,
            action = story.action,
            actionFactor = story.actionFactor,
            startAt = story.startAt,
            reviewOpensAt = story.reviewOpensAt,
            storyFingerprint = story.fingerprint,
            beaconState = beacon.state,
            beaconMoments = beacon.moments.map { it.copy() },
            beaconFingerprint = beacon.fingerprint,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = StoryCheckpointCodec.fingerprintFor(draft)
        )
    }
}

internal object StoryCheckpointCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 16
    private const val NONE = "-"
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(checkpoint: StoryCheckpoint): String {
        val expected = fingerprintFor(checkpoint)
        require(secureEquals(expected, checkpoint.fingerprint))
        return "${payload(checkpoint)}|$expected"
    }

    fun decode(encoded: String): StoryCheckpoint? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val draft = StoryCheckpoint(
                eventId = decodeText(parts[1]),
                eventLabel = decodeText(parts[2]),
                savedAt = parts[3].toLong(),
                sourceState = EventStorySourceState.valueOf(parts[4]),
                chapterStates = parseEnums(
                    parts[5],
                    EventStoryChapter.values().size,
                    EventStoryChapterState::valueOf
                ),
                phase = EventStoryPhase.valueOf(parts[6]),
                action = EventStoryAction.valueOf(parts[7]),
                actionFactor = parts[8]
                    .takeUnless { it == NONE }
                    ?.let(SignalFactor::valueOf),
                startAt = parseOptionalLong(parts[9]),
                reviewOpensAt = parseOptionalLong(parts[10]),
                storyFingerprint = parts[11].lowercase(),
                beaconState = StoryBeaconState.valueOf(parts[12]),
                beaconMoments = decodeMoments(parts[13]),
                beaconFingerprint = parts[14].lowercase(),
                fingerprint = ""
            )
            val expected = fingerprintFor(draft)
            require(secureEquals(expected, parts[15]))
            draft.copy(fingerprint = expected)
        }.getOrNull()
    }

    internal fun fingerprintFor(
        checkpoint: StoryCheckpoint
    ): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(
                payload(checkpoint).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun payload(checkpoint: StoryCheckpoint): String {
        return listOf(
            VERSION,
            encodeText(checkpoint.eventId),
            encodeText(checkpoint.eventLabel),
            checkpoint.savedAt.toString(),
            checkpoint.sourceState.name,
            checkpoint.chapterStates.joinToString(",") { it.name },
            checkpoint.phase.name,
            checkpoint.action.name,
            checkpoint.actionFactor?.name ?: NONE,
            checkpoint.startAt?.toString() ?: NONE,
            checkpoint.reviewOpensAt?.toString() ?: NONE,
            checkpoint.storyFingerprint.lowercase(),
            checkpoint.beaconState.name,
            encodeMoments(checkpoint.beaconMoments),
            checkpoint.beaconFingerprint.lowercase()
        ).joinToString("|")
    }

    private fun encodeMoments(
        moments: List<StoryBeaconMoment>
    ): String {
        if (moments.isEmpty()) return NONE
        return moments.joinToString(";") { moment ->
            listOf(
                moment.kind.name,
                moment.at?.toString() ?: NONE,
                moment.action?.name ?: NONE,
                moment.factors.joinToString("+") { it.name }
                    .ifEmpty { NONE }
            ).joinToString(":")
        }
    }

    private fun decodeMoments(value: String): List<StoryBeaconMoment> {
        if (value == NONE) return emptyList()
        return value.split(';').map { encoded ->
            val parts = encoded.split(':')
            require(parts.size == 4)
            StoryBeaconMoment(
                kind = StoryBeaconMomentKind.valueOf(parts[0]),
                at = parseOptionalLong(parts[1]),
                action = parts[2]
                    .takeUnless { it == NONE }
                    ?.let(EventStoryAction::valueOf),
                factors = if (parts[3] == NONE) {
                    emptyList()
                } else {
                    parts[3].split('+')
                        .map(SignalFactor::valueOf)
                }
            )
        }
    }

    private fun parseOptionalLong(value: String): Long? {
        return value.takeUnless { it == NONE }?.toLong()
    }

    private fun <T> parseEnums(
        value: String,
        expectedSize: Int,
        transform: (String) -> T
    ): List<T> {
        return value.split(',')
            .also { require(it.size == expectedSize) }
            .map(transform)
    }

    private fun encodeText(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeText(value: String): String {
        return String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        )
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.lowercase().toByteArray(StandardCharsets.US_ASCII),
            right.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }
}

internal data class StoryCheckpointChapterDelta(
    val chapter: EventStoryChapter,
    val before: EventStoryChapterState,
    val current: EventStoryChapterState
) {
    init {
        require(before != current)
    }
}

internal data class StoryCheckpointComparison(
    val checkpoint: StoryCheckpoint,
    val current: StoryCheckpoint,
    val labelChanged: Boolean,
    val sourceChanged: Boolean,
    val chapterDeltas: List<StoryCheckpointChapterDelta>,
    val phaseChanged: Boolean,
    val actionChanged: Boolean,
    val startChanged: Boolean,
    val reviewChanged: Boolean,
    val beaconStateChanged: Boolean,
    val beaconMomentsChanged: Boolean,
    val fingerprint: String
) {
    init {
        require(checkpoint.eventId == current.eventId)
        require(HEX_64.matches(fingerprint))
    }

    val changeCount: Int
        get() = chapterDeltas.size + listOf(
            labelChanged,
            sourceChanged,
            phaseChanged,
            actionChanged,
            startChanged,
            reviewChanged,
            beaconStateChanged,
            beaconMomentsChanged
        ).count { it }

    val hasChanges: Boolean
        get() = changeCount > 0

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryCheckpointEngine {
    private const val VERSION =
        "sport-pulse-story-checkpoint-comparison-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun compare(
        checkpoint: StoryCheckpoint,
        current: StoryCheckpoint
    ): StoryCheckpointComparison {
        require(checkpoint.eventId == current.eventId)
        val chapterDeltas = EventStoryChapter.values().mapNotNull {
            val before = checkpoint.chapterState(it)
            val now = current.chapterState(it)
            if (before == now) {
                null
            } else {
                StoryCheckpointChapterDelta(it, before, now)
            }
        }
        val labelChanged = checkpoint.eventLabel != current.eventLabel
        val sourceChanged = checkpoint.sourceState != current.sourceState
        val phaseChanged = checkpoint.phase != current.phase
        val actionChanged = checkpoint.action != current.action ||
            checkpoint.actionFactor != current.actionFactor
        val startChanged = checkpoint.startAt != current.startAt
        val reviewChanged = checkpoint.reviewOpensAt !=
            current.reviewOpensAt
        val beaconStateChanged = checkpoint.beaconState !=
            current.beaconState
        val beaconMomentsChanged = checkpoint.beaconMoments !=
            current.beaconMoments
        val fingerprint = fingerprintFor(
            checkpoint = checkpoint,
            current = current,
            labelChanged = labelChanged,
            sourceChanged = sourceChanged,
            chapterDeltas = chapterDeltas,
            phaseChanged = phaseChanged,
            actionChanged = actionChanged,
            startChanged = startChanged,
            reviewChanged = reviewChanged,
            beaconStateChanged = beaconStateChanged,
            beaconMomentsChanged = beaconMomentsChanged
        )
        return StoryCheckpointComparison(
            checkpoint = checkpoint,
            current = current,
            labelChanged = labelChanged,
            sourceChanged = sourceChanged,
            chapterDeltas = chapterDeltas,
            phaseChanged = phaseChanged,
            actionChanged = actionChanged,
            startChanged = startChanged,
            reviewChanged = reviewChanged,
            beaconStateChanged = beaconStateChanged,
            beaconMomentsChanged = beaconMomentsChanged,
            fingerprint = fingerprint
        )
    }

    private fun fingerprintFor(
        checkpoint: StoryCheckpoint,
        current: StoryCheckpoint,
        labelChanged: Boolean,
        sourceChanged: Boolean,
        chapterDeltas: List<StoryCheckpointChapterDelta>,
        phaseChanged: Boolean,
        actionChanged: Boolean,
        startChanged: Boolean,
        reviewChanged: Boolean,
        beaconStateChanged: Boolean,
        beaconMomentsChanged: Boolean
    ): String {
        val payload = buildString {
            appendField(VERSION)
            appendField(checkpoint.fingerprint)
            appendField(current.eventId)
            appendField(current.eventLabel)
            appendField(current.sourceState.name)
            appendField(
                current.chapterStates.joinToString(",") { it.name }
            )
            appendField(current.phase.name)
            appendField(current.action.name)
            appendField(current.actionFactor?.name ?: "-")
            appendField(current.startAt?.toString() ?: "-")
            appendField(current.reviewOpensAt?.toString() ?: "-")
            appendField(current.beaconState.name)
            current.beaconMoments.forEach { moment ->
                appendField(moment.kind.name)
                appendField(moment.at?.toString() ?: "-")
                appendField(moment.action?.name ?: "-")
                appendField(
                    moment.factors.joinToString(",") { it.name }
                        .ifEmpty { "-" }
                )
            }
            appendField(labelChanged.toString())
            appendField(sourceChanged.toString())
            chapterDeltas.forEach { delta ->
                appendField(delta.chapter.name)
                appendField(delta.before.name)
                appendField(delta.current.name)
            }
            listOf(
                phaseChanged,
                actionChanged,
                startChanged,
                reviewChanged,
                beaconStateChanged,
                beaconMomentsChanged
            ).forEach {
                appendField(it.toString())
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun StringBuilder.appendField(value: String) {
        append('|')
        append(value.length)
        append(':')
        append(value)
    }
}
