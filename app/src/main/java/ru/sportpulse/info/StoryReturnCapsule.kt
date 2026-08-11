package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal enum class StoryReturnCapsuleIntegrity {
    EMPTY,
    VALID,
    TAMPERED
}

internal data class StoryReturnCapsuleReadResult(
    val integrity: StoryReturnCapsuleIntegrity,
    val capsule: StoryReturnCapsule?
) {
    init {
        require(
            (integrity == StoryReturnCapsuleIntegrity.VALID) ==
                (capsule != null)
        )
    }
}

internal data class StoryReturnCapsule(
    val eventId: String,
    val eventLabel: String,
    val chapter: EventStoryChapter,
    val activatedAt: Long,
    val pauseUntil: Long,
    val returnAt: Long,
    val baselineEntryState: StoryThreadMapState,
    val baselineResultFingerprint: String,
    val mapFingerprint: String,
    val quietWindowFingerprint: String,
    val momentKind: StoryBeaconMomentKind,
    val momentFactors: List<SignalFactor>,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(activatedAt >= 0L)
        require(pauseUntil > activatedAt)
        require(returnAt >= pauseUntil)
        require(
            pauseUntil == StoryQuietWindowPolicy.pauseUntil(
                now = activatedAt,
                returnAt = returnAt
            )
        )
        require(
            baselineEntryState == StoryThreadMapState.OPEN ||
                baselineEntryState == StoryThreadMapState.MOVED
        )
        require(HEX_64.matches(baselineResultFingerprint))
        require(HEX_64.matches(mapFingerprint))
        require(HEX_64.matches(quietWindowFingerprint))
        require(momentFactors.distinct().size == momentFactors.size)
        require(momentFactors.zipWithNext().all { (left, right) ->
            left.ordinal < right.ordinal
        })
        when (momentKind) {
            StoryBeaconMomentKind.CHECK_WINDOW,
            StoryBeaconMomentKind.FACT_EXPIRY ->
                require(momentFactors.isNotEmpty())
            StoryBeaconMomentKind.START,
            StoryBeaconMomentKind.REVIEW_OPEN ->
                require(momentFactors.isEmpty())
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.COMPLETE ->
                error("Return capsule requires an absolute moment")
        }
        require(fingerprint.isEmpty() || HEX_64.matches(fingerprint))
    }

    val reachesReturnPoint: Boolean
        get() = pauseUntil == returnAt

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryReturnCapsuleFactory {
    fun create(
        quietWindow: StoryQuietWindowResult,
        activatedAt: Long,
        pauseUntil: Long
    ): StoryReturnCapsule {
        require(quietWindow.state == StoryQuietWindowState.AVAILABLE)
        val entry = checkNotNull(quietWindow.entry)
        val result = checkNotNull(entry.result)
        val moment = checkNotNull(quietWindow.moment)
        val returnAt = checkNotNull(quietWindow.returnAt)
        require(moment.at == returnAt)
        require(pauseUntil > activatedAt)
        val draft = StoryReturnCapsule(
            eventId = entry.eventId,
            eventLabel = checkNotNull(entry.match),
            chapter = result.thread.chapter,
            activatedAt = activatedAt,
            pauseUntil = pauseUntil,
            returnAt = returnAt,
            baselineEntryState = entry.state,
            baselineResultFingerprint = result.fingerprint,
            mapFingerprint = quietWindow.mapFingerprint,
            quietWindowFingerprint = quietWindow.fingerprint,
            momentKind = moment.kind,
            momentFactors = moment.factors.toList(),
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = StoryReturnCapsuleCodec.fingerprintFor(draft)
        )
    }
}

internal object StoryReturnCapsuleCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 14
    private const val NONE = "-"
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(capsule: StoryReturnCapsule): String {
        val expected = fingerprintFor(capsule)
        require(secureEquals(expected, capsule.fingerprint))
        return "${payload(capsule)}|$expected"
    }

    fun decode(encoded: String): StoryReturnCapsule? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val draft = StoryReturnCapsule(
                eventId = decodeText(parts[1]),
                eventLabel = decodeText(parts[2]),
                chapter = EventStoryChapter.valueOf(parts[3]),
                activatedAt = parts[4].toLong(),
                pauseUntil = parts[5].toLong(),
                returnAt = parts[6].toLong(),
                baselineEntryState =
                    StoryThreadMapState.valueOf(parts[7]),
                baselineResultFingerprint = parts[8].lowercase(),
                mapFingerprint = parts[9].lowercase(),
                quietWindowFingerprint = parts[10].lowercase(),
                momentKind = StoryBeaconMomentKind.valueOf(parts[11]),
                momentFactors = if (parts[12] == NONE) {
                    emptyList()
                } else {
                    parts[12].split(',').map(SignalFactor::valueOf)
                },
                fingerprint = ""
            )
            val expected = fingerprintFor(draft)
            require(secureEquals(expected, parts[13]))
            draft.copy(fingerprint = expected)
        }.getOrNull()
    }

    internal fun fingerprintFor(
        capsule: StoryReturnCapsule
    ): String = sha256(payload(capsule))

    private fun payload(capsule: StoryReturnCapsule): String {
        return listOf(
            VERSION,
            encodeText(capsule.eventId),
            encodeText(capsule.eventLabel),
            capsule.chapter.name,
            capsule.activatedAt.toString(),
            capsule.pauseUntil.toString(),
            capsule.returnAt.toString(),
            capsule.baselineEntryState.name,
            capsule.baselineResultFingerprint.lowercase(),
            capsule.mapFingerprint.lowercase(),
            capsule.quietWindowFingerprint.lowercase(),
            capsule.momentKind.name,
            capsule.momentFactors.joinToString(",") { it.name }
                .ifEmpty { NONE }
        ).joinToString("|")
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

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val number = byte.toInt() and 0xff
                append(hex[number ushr 4])
                append(hex[number and 0x0f])
            }
        }
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.lowercase().toByteArray(StandardCharsets.US_ASCII),
            right.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }
}

internal enum class StoryReturnCapsuleState {
    SEALED,
    LIMIT_REACHED,
    UNCHANGED,
    POINT_MOVED,
    CHANGED,
    RESOLVED,
    MISSED,
    DETACHED,
    MISSING,
    CURRENT_TAMPERED
}

internal data class StoryReturnCapsuleResult(
    val capsule: StoryReturnCapsule,
    val state: StoryReturnCapsuleState,
    val currentEntry: StoryThreadMapEntry?,
    val currentMapFingerprint: String,
    val fingerprint: String
) {
    init {
        require(HEX_64.matches(currentMapFingerprint))
        require(HEX_64.matches(fingerprint))
        when (state) {
            StoryReturnCapsuleState.SEALED,
            StoryReturnCapsuleState.LIMIT_REACHED,
            StoryReturnCapsuleState.MISSING ->
                require(currentEntry == null)
            StoryReturnCapsuleState.UNCHANGED,
            StoryReturnCapsuleState.POINT_MOVED,
            StoryReturnCapsuleState.CHANGED -> {
                require(
                    currentEntry?.state == StoryThreadMapState.OPEN ||
                        currentEntry?.state == StoryThreadMapState.MOVED
                )
            }
            StoryReturnCapsuleState.RESOLVED ->
                require(
                    currentEntry?.state == StoryThreadMapState.RESOLVED
                )
            StoryReturnCapsuleState.MISSED ->
                require(currentEntry?.state == StoryThreadMapState.MISSED)
            StoryReturnCapsuleState.DETACHED ->
                require(currentEntry?.state == StoryThreadMapState.DETACHED)
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                require(currentEntry?.state == StoryThreadMapState.TAMPERED)
        }
        if (state == StoryReturnCapsuleState.POINT_MOVED) {
            val moment = checkNotNull(currentEntry?.nextMoment)
            require(moment.kind == capsule.momentKind)
            require(checkNotNull(moment.at) > capsule.returnAt)
        }
    }

    val isOpenable: Boolean
        get() = currentEntry?.presentInCatalog == true &&
            state != StoryReturnCapsuleState.CURRENT_TAMPERED

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryReturnCapsuleEngine {
    private const val VERSION = "sport-pulse-story-return-capsule-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        capsule: StoryReturnCapsule,
        currentMap: StoryThreadMapResult,
        now: Long
    ): StoryReturnCapsuleResult {
        require(now >= 0L)
        val currentEntry: StoryThreadMapEntry?
        val state = when {
            now < capsule.pauseUntil -> {
                currentEntry = null
                StoryReturnCapsuleState.SEALED
            }
            now < capsule.returnAt -> {
                currentEntry = null
                StoryReturnCapsuleState.LIMIT_REACHED
            }
            else -> {
                currentEntry = currentMap.entries.firstOrNull {
                    it.eventId == capsule.eventId
                }
                stateAfterReturn(capsule, currentEntry)
            }
        }
        return StoryReturnCapsuleResult(
            capsule = capsule,
            state = state,
            currentEntry = currentEntry,
            currentMapFingerprint = currentMap.fingerprint,
            fingerprint = fingerprintFor(
                capsule = capsule,
                state = state,
                currentMap = currentMap,
                currentEntry = currentEntry
            )
        )
    }

    private fun stateAfterReturn(
        capsule: StoryReturnCapsule,
        entry: StoryThreadMapEntry?
    ): StoryReturnCapsuleState {
        if (entry == null) return StoryReturnCapsuleState.MISSING
        return when (entry.state) {
            StoryThreadMapState.EMPTY -> StoryReturnCapsuleState.MISSING
            StoryThreadMapState.TAMPERED ->
                StoryReturnCapsuleState.CURRENT_TAMPERED
            StoryThreadMapState.DETACHED ->
                StoryReturnCapsuleState.DETACHED
            StoryThreadMapState.RESOLVED ->
                StoryReturnCapsuleState.RESOLVED
            StoryThreadMapState.MISSED ->
                StoryReturnCapsuleState.MISSED
            StoryThreadMapState.OPEN,
            StoryThreadMapState.MOVED -> {
                val result = checkNotNull(entry.result)
                when {
                    entry.state != capsule.baselineEntryState ||
                        result.fingerprint !=
                        capsule.baselineResultFingerprint ->
                        StoryReturnCapsuleState.CHANGED
                    entry.nextMoment?.kind == capsule.momentKind &&
                        checkNotNull(entry.nextMoment.at) >
                        capsule.returnAt ->
                        StoryReturnCapsuleState.POINT_MOVED
                    else -> StoryReturnCapsuleState.UNCHANGED
                }
            }
        }
    }

    private fun fingerprintFor(
        capsule: StoryReturnCapsule,
        state: StoryReturnCapsuleState,
        currentMap: StoryThreadMapResult,
        currentEntry: StoryThreadMapEntry?
    ): String {
        val payload = buildString {
            appendToken(VERSION)
            appendToken(capsule.fingerprint)
            appendToken(state.name)
            appendToken(
                if (currentEntry == null) {
                    ""
                } else {
                    currentMap.fingerprint
                }
            )
            appendToken(currentEntry?.eventId.orEmpty())
            appendToken(currentEntry?.state?.name.orEmpty())
            appendToken(currentEntry?.result?.fingerprint.orEmpty())
            appendToken(currentEntry?.nextMoment?.kind?.name.orEmpty())
            appendToken(currentEntry?.nextMoment?.at?.toString().orEmpty())
            appendToken(
                currentEntry?.nextMoment?.factors
                    ?.joinToString(",") { it.name }
                    .orEmpty()
            )
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val number = byte.toInt() and 0xff
                append(hex[number ushr 4])
                append(hex[number and 0x0f])
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
