package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class StoryBeaconState {
    NO_TIMELINE,
    ACTION_NOW,
    WATCHING,
    EVENT_ACTIVE,
    REVIEW_DUE,
    COMPLETE,
    INCOMPLETE
}

internal enum class StoryBeaconMomentKind {
    ACTION_NOW,
    CHECK_WINDOW,
    FACT_EXPIRY,
    START,
    REVIEW_OPEN,
    COMPLETE
}

internal data class StoryBeaconFactorTransition(
    val factor: SignalFactor,
    val at: Long
) {
    init {
        require(at >= 0L)
    }
}

internal data class StoryBeaconMoment(
    val kind: StoryBeaconMomentKind,
    val at: Long?,
    val factors: List<SignalFactor> = emptyList(),
    val action: EventStoryAction? = null
) {
    init {
        require(factors.distinct().size == factors.size)
        require(factors.zipWithNext().all { (left, right) ->
            left.ordinal < right.ordinal
        })
        when (kind) {
            StoryBeaconMomentKind.ACTION_NOW -> {
                require(at == null)
                require(action != null && action != EventStoryAction.NONE)
                require(factors.size <= 1)
            }
            StoryBeaconMomentKind.CHECK_WINDOW,
            StoryBeaconMomentKind.FACT_EXPIRY -> {
                require(at != null && at >= 0L)
                require(factors.isNotEmpty())
                require(action == null)
            }
            StoryBeaconMomentKind.START,
            StoryBeaconMomentKind.REVIEW_OPEN -> {
                require(at != null && at >= 0L)
                require(factors.isEmpty())
                require(action == null)
            }
            StoryBeaconMomentKind.COMPLETE -> {
                require(at == null)
                require(factors.isEmpty())
                require(action == null)
            }
        }
    }
}

internal data class StoryBeaconInput(
    val story: EventStoryResult,
    val checkSlots: List<PreflightSlot>,
    val factorTransitions: List<StoryBeaconFactorTransition>,
    val now: Long
) {
    init {
        require(now >= 0L)
        require(checkSlots.zipWithNext().all { (left, right) ->
            left.scheduledAt < right.scheduledAt
        })
        require(
            factorTransitions.distinctBy { it.factor }.size ==
                factorTransitions.size
        )
    }
}

internal data class StoryBeaconResult(
    val eventId: String,
    val evaluatedAtMinute: Long,
    val state: StoryBeaconState,
    val moments: List<StoryBeaconMoment>,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(evaluatedAtMinute >= 0L)
        require(moments.size <= MAX_MOMENTS)
        require(
            moments.dropWhile {
                it.kind == StoryBeaconMomentKind.ACTION_NOW
            }.mapNotNull { it.at }.zipWithNext().all { (left, right) ->
                left <= right
            }
        )
        require(HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    val timedCount: Int
        get() = moments.count { it.at != null }

    val primaryMoment: StoryBeaconMoment?
        get() = moments.firstOrNull()

    private companion object {
        const val MAX_MOMENTS = 5
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryBeaconEngine {
    private const val VERSION = "sport-pulse-story-beacon-v1"
    private const val MINUTE_MILLIS = 60_000L
    private const val MAX_MOMENTS = 5
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(input: StoryBeaconInput): StoryBeaconResult {
        val story = input.story
        val startAt = story.startAt
        val reviewOpensAt = story.reviewOpensAt
        val state = stateFor(
            story = story,
            now = input.now
        )
        val moments = buildList {
            if (state == StoryBeaconState.COMPLETE) {
                add(
                    StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.COMPLETE,
                        at = null
                    )
                )
                return@buildList
            }

            if (story.action != EventStoryAction.NONE) {
                add(
                    StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.ACTION_NOW,
                        at = null,
                        factors = story.actionFactor
                            ?.let(::listOf)
                            .orEmpty(),
                        action = story.action
                    )
                )
            }

            val timed = mutableListOf<StoryBeaconMoment>()
            if (
                startAt != null &&
                reviewOpensAt != null &&
                input.now < startAt
            ) {
                input.checkSlots.firstOrNull {
                    !it.immediate &&
                        it.scheduledAt > input.now &&
                        it.scheduledAt < startAt
                }?.let { slot ->
                    timed += StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.CHECK_WINDOW,
                        at = slot.scheduledAt,
                        factors = slot.factors
                    )
                }

                val nextTransitionAt = input.factorTransitions
                    .asSequence()
                    .map { it.at }
                    .filter { it > input.now && it <= startAt }
                    .minOrNull()
                nextTransitionAt?.let { transitionAt ->
                    timed += StoryBeaconMoment(
                        kind = StoryBeaconMomentKind.FACT_EXPIRY,
                        at = transitionAt,
                        factors = input.factorTransitions
                            .filter { it.at == transitionAt }
                            .map { it.factor }
                            .sortedBy { it.ordinal }
                    )
                }
                timed += StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.START,
                    at = startAt
                )
                timed += StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.REVIEW_OPEN,
                    at = reviewOpensAt
                )
            } else if (
                startAt != null &&
                reviewOpensAt != null &&
                input.now < reviewOpensAt
            ) {
                timed += StoryBeaconMoment(
                    kind = StoryBeaconMomentKind.REVIEW_OPEN,
                    at = reviewOpensAt
                )
            }

            addAll(
                timed.sortedWith(
                    compareBy<StoryBeaconMoment> {
                        checkNotNull(it.at)
                    }.thenBy { it.kind.ordinal }
                ).take((MAX_MOMENTS - size).coerceAtLeast(0))
            )
        }
        val evaluatedAtMinute = input.now / MINUTE_MILLIS
        val fingerprint = fingerprintFor(
            story = story,
            evaluatedAtMinute = evaluatedAtMinute,
            state = state,
            moments = moments
        )
        return StoryBeaconResult(
            eventId = story.eventId,
            evaluatedAtMinute = evaluatedAtMinute,
            state = state,
            moments = moments,
            fingerprint = fingerprint
        )
    }

    private fun stateFor(
        story: EventStoryResult,
        now: Long
    ): StoryBeaconState {
        val startAt = story.startAt
            ?: return StoryBeaconState.NO_TIMELINE
        val reviewOpensAt = checkNotNull(story.reviewOpensAt)
        return when {
            story.phase == EventStoryPhase.COMPLETE ->
                StoryBeaconState.COMPLETE
            now < startAt && story.action != EventStoryAction.NONE ->
                StoryBeaconState.ACTION_NOW
            now < startAt -> StoryBeaconState.WATCHING
            now < reviewOpensAt -> StoryBeaconState.EVENT_ACTIVE
            story.action == EventStoryAction.OPEN_REVIEW ->
                StoryBeaconState.REVIEW_DUE
            else -> StoryBeaconState.INCOMPLETE
        }
    }

    private fun fingerprintFor(
        story: EventStoryResult,
        evaluatedAtMinute: Long,
        state: StoryBeaconState,
        moments: List<StoryBeaconMoment>
    ): String {
        val payload = buildString {
            append(VERSION)
            appendField(story.eventId)
            appendField(story.fingerprint)
            appendField(evaluatedAtMinute.toString())
            appendField(state.name)
            moments.forEach { moment ->
                appendField(moment.kind.name)
                appendField(
                    moment.at?.div(MINUTE_MILLIS)?.toString()
                        ?: "now"
                )
                appendField(moment.action?.name ?: "none")
                appendField(
                    moment.factors.joinToString(",") { it.name }
                )
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
