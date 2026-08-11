package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal enum class StoryThreadIntegrity {
    EMPTY,
    VALID,
    TAMPERED
}

internal data class StoryThreadReadResult(
    val integrity: StoryThreadIntegrity,
    val thread: StoryThread?
) {
    init {
        require(
            (integrity == StoryThreadIntegrity.VALID) ==
                (thread != null)
        )
    }
}

internal enum class StoryThreadStatus {
    OPEN,
    MOVED,
    RESOLVED,
    MISSED
}

internal data class StoryThread(
    val eventId: String,
    val chapter: EventStoryChapter,
    val startedAt: Long,
    val initialState: EventStoryChapterState,
    val initialStoryFingerprint: String,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(startedAt >= 0L)
        require(StoryThreadPolicy.isTrackable(initialState))
        require(HEX_64.matches(initialStoryFingerprint))
        require(fingerprint.isEmpty() || HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryThreadPolicy {
    fun isTrackable(state: EventStoryChapterState): Boolean {
        return state != EventStoryChapterState.COMPLETE &&
            state != EventStoryChapterState.MISSED
    }

    fun choices(story: EventStoryResult): List<EventStoryChapter> {
        val current = story.currentChapter
        return story.chapters
            .filter { isTrackable(it.state) }
            .map { it.chapter }
            .sortedWith(
                compareBy<EventStoryChapter> { it != current }
                    .thenBy { it.ordinal }
            )
    }

    fun recommended(
        story: EventStoryResult
    ): EventStoryChapter? = choices(story).firstOrNull()

    fun relevantMoment(
        chapter: EventStoryChapter,
        beacon: StoryBeaconResult
    ): StoryBeaconMoment? {
        val priorities = when (chapter) {
            EventStoryChapter.SOURCE -> listOf(
                StoryBeaconMomentKind.CHECK_WINDOW,
                StoryBeaconMomentKind.FACT_EXPIRY,
                StoryBeaconMomentKind.START,
                StoryBeaconMomentKind.REVIEW_OPEN
            )
            EventStoryChapter.FACTS -> listOf(
                StoryBeaconMomentKind.CHECK_WINDOW,
                StoryBeaconMomentKind.FACT_EXPIRY,
                StoryBeaconMomentKind.START
            )
            EventStoryChapter.PLAN -> listOf(
                StoryBeaconMomentKind.CHECK_WINDOW,
                StoryBeaconMomentKind.START
            )
            EventStoryChapter.DECISION,
            EventStoryChapter.START -> listOf(
                StoryBeaconMomentKind.START
            )
            EventStoryChapter.REVIEW -> listOf(
                StoryBeaconMomentKind.REVIEW_OPEN
            )
        }
        priorities.forEach { kind ->
            beacon.moments.firstOrNull {
                it.kind == kind
            }?.let { return it }
        }
        return null
    }
}

internal object StoryThreadFactory {
    fun create(
        story: EventStoryResult,
        chapter: EventStoryChapter,
        startedAt: Long
    ): StoryThread {
        require(startedAt >= 0L)
        val initialState = story.chapter(chapter).state
        require(StoryThreadPolicy.isTrackable(initialState))
        val draft = StoryThread(
            eventId = story.eventId,
            chapter = chapter,
            startedAt = startedAt,
            initialState = initialState,
            initialStoryFingerprint = story.fingerprint,
            fingerprint = ""
        )
        return draft.copy(
            fingerprint = StoryThreadCodec.fingerprintFor(draft)
        )
    }
}

internal object StoryThreadCodec {
    private const val VERSION = "1"
    private const val PART_COUNT = 7
    private val hex = "0123456789abcdef".toCharArray()

    fun encode(thread: StoryThread): String {
        val expected = fingerprintFor(thread)
        require(secureEquals(expected, thread.fingerprint))
        return "${payload(thread)}|$expected"
    }

    fun decode(encoded: String): StoryThread? {
        return runCatching {
            val parts = encoded.split('|')
            require(parts.size == PART_COUNT)
            require(parts[0] == VERSION)
            val draft = StoryThread(
                eventId = decodeText(parts[1]),
                chapter = EventStoryChapter.valueOf(parts[2]),
                startedAt = parts[3].toLong(),
                initialState =
                    EventStoryChapterState.valueOf(parts[4]),
                initialStoryFingerprint = parts[5].lowercase(),
                fingerprint = ""
            )
            val expected = fingerprintFor(draft)
            require(secureEquals(expected, parts[6]))
            draft.copy(fingerprint = expected)
        }.getOrNull()
    }

    internal fun fingerprintFor(thread: StoryThread): String {
        return sha256(payload(thread))
    }

    private fun payload(thread: StoryThread): String {
        return listOf(
            VERSION,
            encodeText(thread.eventId),
            thread.chapter.name,
            thread.startedAt.toString(),
            thread.initialState.name,
            thread.initialStoryFingerprint.lowercase()
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

internal data class StoryThreadResult(
    val thread: StoryThread,
    val currentState: EventStoryChapterState,
    val status: StoryThreadStatus,
    val fingerprint: String
) {
    init {
        require(HEX_64.matches(fingerprint))
        require(
            (status == StoryThreadStatus.RESOLVED) ==
                (currentState == EventStoryChapterState.COMPLETE)
        )
        require(
            (status == StoryThreadStatus.MISSED) ==
                (currentState == EventStoryChapterState.MISSED)
        )
        require(
            status != StoryThreadStatus.OPEN ||
                currentState == thread.initialState
        )
        require(
            status != StoryThreadStatus.MOVED ||
                currentState != thread.initialState
        )
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryThreadEngine {
    private const val VERSION = "sport-pulse-story-thread-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        thread: StoryThread,
        story: EventStoryResult
    ): StoryThreadResult {
        require(thread.eventId == story.eventId)
        val currentState = story.chapter(thread.chapter).state
        val status = when (currentState) {
            EventStoryChapterState.COMPLETE ->
                StoryThreadStatus.RESOLVED
            EventStoryChapterState.MISSED ->
                StoryThreadStatus.MISSED
            thread.initialState -> StoryThreadStatus.OPEN
            else -> StoryThreadStatus.MOVED
        }
        return StoryThreadResult(
            thread = thread,
            currentState = currentState,
            status = status,
            fingerprint = fingerprintFor(
                thread = thread,
                currentState = currentState,
                status = status
            )
        )
    }

    private fun fingerprintFor(
        thread: StoryThread,
        currentState: EventStoryChapterState,
        status: StoryThreadStatus
    ): String {
        val payload = listOf(
            VERSION,
            thread.fingerprint,
            thread.eventId,
            thread.chapter.name,
            thread.initialState.name,
            currentState.name,
            status.name
        ).joinToString("|")
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
}
