package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class StoryQuietWindowState {
    EMPTY,
    AVAILABLE,
    UNSCHEDULED,
    NO_ACTIVE
}

internal data class StoryQuietWindowResult(
    val mapFingerprint: String,
    val state: StoryQuietWindowState,
    val activeCount: Int,
    val scheduledCount: Int,
    val entry: StoryThreadMapEntry?,
    val moment: StoryBeaconMoment?,
    val returnAt: Long?,
    val fingerprint: String
) {
    init {
        require(HEX_64.matches(mapFingerprint))
        require(activeCount >= 0)
        require(scheduledCount in 0..activeCount)
        require(HEX_64.matches(fingerprint))
        when (state) {
            StoryQuietWindowState.EMPTY,
            StoryQuietWindowState.NO_ACTIVE -> {
                require(activeCount == 0)
                require(scheduledCount == 0)
                require(entry == null)
                require(moment == null)
                require(returnAt == null)
            }
            StoryQuietWindowState.UNSCHEDULED -> {
                require(activeCount > 0)
                require(scheduledCount == 0)
                require(entry == null)
                require(moment == null)
                require(returnAt == null)
            }
            StoryQuietWindowState.AVAILABLE -> {
                require(activeCount > 0)
                require(scheduledCount > 0)
                require(entry != null)
                require(
                    entry.state == StoryThreadMapState.OPEN ||
                        entry.state == StoryThreadMapState.MOVED
                )
                require(moment != null)
                require(entry.nextMoment == moment)
                require(returnAt == moment.at)
                require(returnAt != null && returnAt >= 0L)
            }
        }
    }

    val unscheduledCount: Int
        get() = activeCount - scheduledCount

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryQuietWindowPolicy {
    const val MAX_PAUSE_MILLIS = 24L * 60L * 60L * 1000L

    fun pauseUntil(now: Long, returnAt: Long): Long {
        require(now >= 0L)
        require(returnAt > now)
        val cap = if (now > Long.MAX_VALUE - MAX_PAUSE_MILLIS) {
            Long.MAX_VALUE
        } else {
            now + MAX_PAUSE_MILLIS
        }
        return minOf(returnAt, cap)
    }

    fun reachesReturnPoint(now: Long, returnAt: Long): Boolean {
        return pauseUntil(now, returnAt) == returnAt
    }
}

internal object StoryQuietWindowEngine {
    private const val VERSION = "sport-pulse-story-quiet-window-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        threadMap: StoryThreadMapResult,
        now: Long
    ): StoryQuietWindowResult {
        require(now >= 0L)
        val active = threadMap.entries.withIndex().filter {
            it.value.state == StoryThreadMapState.OPEN ||
                it.value.state == StoryThreadMapState.MOVED
        }
        val scheduled = active.mapNotNull { indexed ->
            indexed.value.nextMoment?.at
                ?.takeIf { it > now }
                ?.let { at ->
                    QuietCandidate(
                        mapIndex = indexed.index,
                        entry = indexed.value,
                        moment = checkNotNull(
                            indexed.value.nextMoment
                        ),
                        at = at
                    )
                }
        }
        val selected = scheduled.minWithOrNull(
            compareBy<QuietCandidate> { it.at }
                .thenBy { it.mapIndex }
        )
        val state = when {
            threadMap.entries.isEmpty() ->
                StoryQuietWindowState.EMPTY
            active.isEmpty() ->
                StoryQuietWindowState.NO_ACTIVE
            selected == null ->
                StoryQuietWindowState.UNSCHEDULED
            else -> StoryQuietWindowState.AVAILABLE
        }
        val fingerprint = fingerprintFor(
            threadMap = threadMap,
            state = state,
            activeCount = active.size,
            scheduledCount = scheduled.size,
            selected = selected
        )
        return StoryQuietWindowResult(
            mapFingerprint = threadMap.fingerprint,
            state = state,
            activeCount = active.size,
            scheduledCount = scheduled.size,
            entry = selected?.entry,
            moment = selected?.moment,
            returnAt = selected?.at,
            fingerprint = fingerprint
        )
    }

    private fun fingerprintFor(
        threadMap: StoryThreadMapResult,
        state: StoryQuietWindowState,
        activeCount: Int,
        scheduledCount: Int,
        selected: QuietCandidate?
    ): String {
        val payload = buildString {
            appendToken(VERSION)
            appendToken(threadMap.fingerprint)
            appendToken(state.name)
            appendToken(activeCount.toString())
            appendToken(scheduledCount.toString())
            appendToken(selected?.entry?.eventId.orEmpty())
            appendToken(selected?.entry?.state?.name.orEmpty())
            appendToken(
                selected?.entry?.result?.fingerprint.orEmpty()
            )
            appendToken(selected?.moment?.kind?.name.orEmpty())
            appendToken(selected?.at?.toString().orEmpty())
            appendToken(
                selected?.moment?.factors
                    ?.joinToString(",") { it.name }
                    .orEmpty()
            )
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

    private data class QuietCandidate(
        val mapIndex: Int,
        val entry: StoryThreadMapEntry,
        val moment: StoryBeaconMoment,
        val at: Long
    )
}
