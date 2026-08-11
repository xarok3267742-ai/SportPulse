package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class EventStorySourceState {
    PRODUCTION_SIGNED,
    DEVELOPMENT_SIGNED,
    UNSIGNED,
    API_PROVIDER,
    DEMO
}

internal enum class EventStoryChapter {
    SOURCE,
    FACTS,
    PLAN,
    DECISION,
    START,
    REVIEW
}

internal enum class EventStoryChapterState {
    COMPLETE,
    ACTIVE,
    ATTENTION,
    LOCKED,
    MISSED,
    CONTEXT
}

internal enum class EventStoryAction {
    OPEN_SOURCE,
    OPEN_FACTS,
    OPEN_PLAN,
    OPEN_DECISION,
    OPEN_REVIEW,
    NONE
}

internal enum class EventStoryPhase {
    PREPARING,
    READY,
    IN_PROGRESS,
    REVIEW_DUE,
    COMPLETE,
    INCOMPLETE
}

internal data class EventStoryChapterResult(
    val chapter: EventStoryChapter,
    val state: EventStoryChapterState,
    val summary: String
) {
    init {
        require(summary.isNotBlank())
    }
}

internal data class EventStoryInput(
    val event: SportEvent,
    val sourceState: EventStorySourceState,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline,
    val selectedZone: RegionalZone,
    val storedReceipt: PreflightReceiptReadResult,
    val snapshot: DecisionSnapshot?,
    val review: PostEventReview?,
    val now: Long
) {
    init {
        require(event.id.isNotBlank())
        require(event.match.isNotBlank())
        require(now >= 0L)
        require(
            storedReceipt.receipt == null ||
                storedReceipt.receipt.eventId == event.id
        )
        require(snapshot == null || snapshot.eventId == event.id)
        require(review == null || review.eventId == event.id)
    }
}

internal data class EventStoryWindow(
    val startAt: Long,
    val reviewOpensAt: Long
) {
    init {
        require(startAt >= 0L)
        require(reviewOpensAt >= startAt)
    }
}

internal object EventStoryPolicy {
    const val REVIEW_DELAY_MILLIS = 4L * 60L * 60L * 1000L
}

internal object EventStoryTiming {
    fun window(
        event: SportEvent,
        snapshot: DecisionSnapshot?,
        now: Long
    ): EventStoryWindow? {
        require(now >= 0L)
        val startAt = event.startAt ?: run {
            val anchor = snapshot?.savedAt
                ?.coerceAtMost(now)
                ?: now
            EventStartResolver.resolve(event, anchor)?.startAt
        } ?: return null
        val reviewOpensAt = if (
            startAt > Long.MAX_VALUE -
            EventStoryPolicy.REVIEW_DELAY_MILLIS
        ) {
            Long.MAX_VALUE
        } else {
            startAt + EventStoryPolicy.REVIEW_DELAY_MILLIS
        }
        return EventStoryWindow(
            startAt = startAt,
            reviewOpensAt = reviewOpensAt
        )
    }

    fun decisionWindowOpen(
        event: SportEvent,
        snapshot: DecisionSnapshot?,
        now: Long
    ): Boolean {
        val window = window(event, snapshot, now)
            ?: return true
        return now < window.startAt
    }
}

internal data class EventStoryResult(
    val eventId: String,
    val eventLabel: String,
    val sourceState: EventStorySourceState,
    val chapters: List<EventStoryChapterResult>,
    val phase: EventStoryPhase,
    val action: EventStoryAction,
    val actionFactor: SignalFactor?,
    val startAt: Long?,
    val reviewOpensAt: Long?,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(
            chapters.map(EventStoryChapterResult::chapter) ==
                EventStoryChapter.values().toList()
        )
        require(chapters.distinctBy { it.chapter }.size == chapters.size)
        require(startAt == null || startAt >= 0L)
        require((startAt == null) == (reviewOpensAt == null))
        require(reviewOpensAt == null || reviewOpensAt >= startAt!!)
        require(
            actionFactor == null ||
                action == EventStoryAction.OPEN_FACTS
        )
        require(HEX_64.matches(fingerprint))
    }

    val currentChapter: EventStoryChapter
        get() = when (action) {
            EventStoryAction.OPEN_SOURCE -> EventStoryChapter.SOURCE
            EventStoryAction.OPEN_FACTS -> EventStoryChapter.FACTS
            EventStoryAction.OPEN_PLAN -> EventStoryChapter.PLAN
            EventStoryAction.OPEN_DECISION ->
                EventStoryChapter.DECISION
            EventStoryAction.OPEN_REVIEW -> EventStoryChapter.REVIEW
            EventStoryAction.NONE -> when (phase) {
                EventStoryPhase.PREPARING,
                EventStoryPhase.READY,
                EventStoryPhase.IN_PROGRESS ->
                    EventStoryChapter.START
                EventStoryPhase.REVIEW_DUE,
                EventStoryPhase.COMPLETE,
                EventStoryPhase.INCOMPLETE ->
                    EventStoryChapter.REVIEW
            }
        }

    val currentChapterNumber: Int
        get() = currentChapter.ordinal + 1

    val completedCount: Int
        get() = chapters.count {
            it.state == EventStoryChapterState.COMPLETE
        }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    fun chapter(chapter: EventStoryChapter): EventStoryChapterResult {
        return chapters[chapter.ordinal]
    }

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object EventStoryEngine {
    private const val VERSION = "sport-pulse-event-story-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(input: EventStoryInput): EventStoryResult {
        val window = EventStoryTiming.window(
            event = input.event,
            snapshot = input.snapshot,
            now = input.now
        )
        val startAt = window?.startAt
        val reviewOpensAt = window?.reviewOpensAt
        val started = startAt != null && input.now >= startAt
        val reviewWindowOpen = reviewOpensAt != null &&
            input.now >= reviewOpensAt
        val snapshotBeforeStart = input.snapshot != null &&
            startAt != null &&
            input.snapshot.savedAt < startAt

        val relay = if (startAt != null && input.now < startAt) {
            EvidenceRelayEngine.evaluate(
                input = EvidenceRelayInput(
                    event = input.event,
                    assessment = input.assessment,
                    claimedEvidence = input.claimedEvidence,
                    sourceAudit = input.sourceAudit,
                    timeline = input.timeline
                ),
                now = input.now
            )
        } else {
            null
        }
        val protocol = relay?.let {
            PreflightProtocolEngine.evaluate(input.event, it)
        }
        val sync = protocol?.let {
            PreflightSyncEngine.evaluate(
                protocol = it,
                selectedZone = input.selectedZone,
                stored = input.storedReceipt
            )
        }

        val sourceChapter = sourceChapter(input.sourceState)
        val factsChapter = factsChapter(
            startAt = startAt,
            started = started,
            snapshotBeforeStart = snapshotBeforeStart,
            relay = relay
        )
        val planChapter = planChapter(
            startAt = startAt,
            started = started,
            stored = input.storedReceipt,
            protocol = protocol,
            sync = sync
        )
        val decisionChapter = decisionChapter(
            startAt = startAt,
            started = started,
            snapshot = input.snapshot
        )
        val startChapter = startChapter(
            window = window,
            now = input.now
        )
        val reviewLinked = reviewLinked(
            snapshot = input.snapshot,
            review = input.review
        )
        val reviewChapter = reviewChapter(
            window = window,
            now = input.now,
            decision = decisionChapter,
            review = input.review,
            reviewLinked = reviewLinked
        )
        val chapters = listOf(
            sourceChapter,
            factsChapter,
            planChapter,
            decisionChapter,
            startChapter,
            reviewChapter
        )
        val action = nextAction(
            input = input,
            window = window,
            facts = factsChapter,
            plan = planChapter,
            decision = decisionChapter,
            review = reviewChapter
        )
        val actionFactor = if (
            action == EventStoryAction.OPEN_FACTS
        ) {
            relay?.priorityFactor ?: relay?.factors
                ?.firstOrNull {
                    it.state ==
                        EvidenceRelayFactorState.UNCONFIRMED
                }
                ?.factor
        } else {
            null
        }
        val phase = phase(
            window = window,
            now = input.now,
            source = sourceChapter,
            facts = factsChapter,
            plan = planChapter,
            decision = decisionChapter,
            review = reviewChapter
        )
        val fingerprint = fingerprintFor(
            input = input,
            window = window,
            relay = relay,
            protocol = protocol,
            sync = sync,
            chapters = chapters,
            phase = phase,
            action = action,
            actionFactor = actionFactor
        )
        return EventStoryResult(
            eventId = input.event.id,
            eventLabel = input.event.match,
            sourceState = input.sourceState,
            chapters = chapters,
            phase = phase,
            action = action,
            actionFactor = actionFactor,
            startAt = startAt,
            reviewOpensAt = reviewOpensAt,
            fingerprint = fingerprint
        )
    }

    private fun sourceChapter(
        source: EventStorySourceState
    ): EventStoryChapterResult {
        val state = when (source) {
            EventStorySourceState.PRODUCTION_SIGNED ->
                EventStoryChapterState.COMPLETE
            EventStorySourceState.UNSIGNED ->
                EventStoryChapterState.ATTENTION
            EventStorySourceState.DEVELOPMENT_SIGNED,
            EventStorySourceState.API_PROVIDER,
            EventStorySourceState.DEMO ->
                EventStoryChapterState.CONTEXT
        }
        val summary = when (source) {
            EventStorySourceState.PRODUCTION_SIGNED ->
                "Production-подпись афиши проверена."
            EventStorySourceState.DEVELOPMENT_SIGNED ->
                "Проверена демонстрационная подпись источника."
            EventStorySourceState.UNSIGNED ->
                "Структура афиши проверена, но подписи источника нет."
            EventStorySourceState.API_PROVIDER ->
                "Расписание загружено по HTTPS из внешнего источника; аналитические факторы ещё не подтверждены."
            EventStorySourceState.DEMO ->
                "Используется встроенная демонстрационная афиша."
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.SOURCE,
            state = state,
            summary = summary
        )
    }

    private fun factsChapter(
        startAt: Long?,
        started: Boolean,
        snapshotBeforeStart: Boolean,
        relay: EvidenceRelayResult?
    ): EventStoryChapterResult {
        val state: EventStoryChapterState
        val summary: String
        when {
            startAt == null -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Точный момент старта не подтвержден."
            }
            started && snapshotBeforeStart -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Предстартовые данные заморожены снимком решения."
            }
            started -> {
                state = EventStoryChapterState.MISSED
                summary = "Предстартовый снимок фактов не был создан."
            }
            relay == null -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Проекция фактов к старту недоступна."
            }
            relay.unconfirmedCount > 0 -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Без подтверждения: ${relay.unconfirmedCount} из 5."
            }
            relay.recheckCount > 0 -> {
                state = EventStoryChapterState.ACTIVE
                summary = "До старта нужно повторить: ${relay.recheckCount} из 5."
            }
            else -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Все 5 подтверждений держат срок до старта."
            }
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.FACTS,
            state = state,
            summary = summary
        )
    }

    private fun planChapter(
        startAt: Long?,
        started: Boolean,
        stored: PreflightReceiptReadResult,
        protocol: PreflightProtocol?,
        sync: PreflightSyncResult?
    ): EventStoryChapterResult {
        val state: EventStoryChapterState
        val summary: String
        when {
            startAt == null -> {
                state = EventStoryChapterState.LOCKED
                summary = "Календарный план ждёт точного старта."
            }
            started -> {
                val receipt = stored.receipt
                if (
                    stored.integrity == PreflightReceiptIntegrity.VALID &&
                    receipt != null &&
                    !receipt.withdrawn &&
                    receipt.startAt == startAt &&
                    receipt.exportedAt <= startAt
                ) {
                    state = EventStoryChapterState.COMPLETE
                    summary = "Ревизия ${receipt.sequence} передана до старта."
                } else {
                    state = EventStoryChapterState.MISSED
                    summary = "Целый активный план до старта не подтвержден."
                }
            }
            protocol == null || sync == null -> {
                state = EventStoryChapterState.LOCKED
                summary = "План нельзя построить без будущего старта."
            }
            sync.state == PreflightSyncState.CURRENT -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Календарная ревизия ${sync.receipt?.sequence} актуальна."
            }
            sync.state == PreflightSyncState.NOT_EXPORTED -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Предстартовый план ещё не передан."
            }
            sync.state == PreflightSyncState.WITHDRAWN -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Последняя календарная ревизия отозвана."
            }
            sync.state == PreflightSyncState.STALE -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Календарный план требует новой ревизии."
            }
            else -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Квитанция календарного плана повреждена."
            }
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.PLAN,
            state = state,
            summary = summary
        )
    }

    private fun decisionChapter(
        startAt: Long?,
        started: Boolean,
        snapshot: DecisionSnapshot?
    ): EventStoryChapterResult {
        val state: EventStoryChapterState
        val summary: String
        when {
            startAt == null && snapshot == null -> {
                state = EventStoryChapterState.LOCKED
                summary = "Хронологию решения нельзя проверить без старта."
            }
            startAt == null -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Снимок есть, но его предстартовый порядок не доказан."
            }
            snapshot == null && started -> {
                state = EventStoryChapterState.MISSED
                summary = "Решение не было зафиксировано до старта."
            }
            snapshot == null -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Предстартовый снимок решения ещё не создан."
            }
            snapshot.savedAt >= startAt -> {
                state = EventStoryChapterState.MISSED
                summary = "Снимок создан в момент старта или позже и не считается предстартовым."
            }
            else -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Решение запечатано до старта: ${snapshot.shortFingerprint}."
            }
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.DECISION,
            state = state,
            summary = summary
        )
    }

    private fun startChapter(
        window: EventStoryWindow?,
        now: Long
    ): EventStoryChapterResult {
        val state: EventStoryChapterState
        val summary: String
        when {
            window == null -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Точный старт неизвестен."
            }
            now < window.startAt -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Старт впереди; маршрут ещё можно подготовить."
            }
            now < window.reviewOpensAt -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Идёт минимальное четырёхчасовое окно события."
            }
            else -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Минимальное окно события завершено; итог нужно сверить."
            }
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.START,
            state = state,
            summary = summary
        )
    }

    private fun reviewChapter(
        window: EventStoryWindow?,
        now: Long,
        decision: EventStoryChapterResult,
        review: PostEventReview?,
        reviewLinked: Boolean
    ): EventStoryChapterResult {
        val state: EventStoryChapterState
        val summary: String
        when {
            window == null -> {
                state = EventStoryChapterState.LOCKED
                summary = "Разбор закрыт без проверяемого времени старта."
            }
            now < window.reviewOpensAt -> {
                state = EventStoryChapterState.LOCKED
                summary = "Разбор откроется не раньше чем через 4 часа после старта."
            }
            decision.state != EventStoryChapterState.COMPLETE -> {
                state = EventStoryChapterState.MISSED
                summary = "Нет честного предстартового снимка для сравнения."
            }
            review != null && !reviewLinked -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Ретроспектива связана с другой ревизией решения."
            }
            review != null && review.updatedAt < window.reviewOpensAt -> {
                state = EventStoryChapterState.ATTENTION
                summary = "Ретроспектива начата раньше минимального окна события."
            }
            review?.isFinalized == true -> {
                state = EventStoryChapterState.COMPLETE
                summary = "Ретроспектива закрыта: ${review.shortFingerprint}."
            }
            review != null -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Разобрано факторов: ${review.answeredCount} из 5."
            }
            else -> {
                state = EventStoryChapterState.ACTIVE
                summary = "Окно разбора открыто; итог события нужно подтвердить."
            }
        }
        return EventStoryChapterResult(
            chapter = EventStoryChapter.REVIEW,
            state = state,
            summary = summary
        )
    }

    private fun reviewLinked(
        snapshot: DecisionSnapshot?,
        review: PostEventReview?
    ): Boolean {
        if (review == null) return true
        if (snapshot == null) return false
        return MessageDigest.isEqual(
            review.decisionFingerprint.toByteArray(
                StandardCharsets.US_ASCII
            ),
            snapshot.fingerprint.toByteArray(
                StandardCharsets.US_ASCII
            )
        )
    }

    private fun nextAction(
        input: EventStoryInput,
        window: EventStoryWindow?,
        facts: EventStoryChapterResult,
        plan: EventStoryChapterResult,
        decision: EventStoryChapterResult,
        review: EventStoryChapterResult
    ): EventStoryAction {
        if (window == null) return EventStoryAction.OPEN_SOURCE
        if (input.now >= window.reviewOpensAt) {
            return if (
                review.state == EventStoryChapterState.ACTIVE ||
                review.state == EventStoryChapterState.ATTENTION
            ) {
                EventStoryAction.OPEN_REVIEW
            } else {
                EventStoryAction.NONE
            }
        }
        if (input.now >= window.startAt) {
            return EventStoryAction.NONE
        }
        if (input.sourceState == EventStorySourceState.UNSIGNED) {
            return EventStoryAction.OPEN_SOURCE
        }
        if (
            facts.state == EventStoryChapterState.ACTIVE ||
            facts.state == EventStoryChapterState.ATTENTION
        ) {
            return EventStoryAction.OPEN_FACTS
        }
        if (plan.state != EventStoryChapterState.COMPLETE) {
            return EventStoryAction.OPEN_PLAN
        }
        if (decision.state != EventStoryChapterState.COMPLETE) {
            return EventStoryAction.OPEN_DECISION
        }
        return EventStoryAction.NONE
    }

    private fun phase(
        window: EventStoryWindow?,
        now: Long,
        source: EventStoryChapterResult,
        facts: EventStoryChapterResult,
        plan: EventStoryChapterResult,
        decision: EventStoryChapterResult,
        review: EventStoryChapterResult
    ): EventStoryPhase {
        if (review.state == EventStoryChapterState.COMPLETE) {
            return EventStoryPhase.COMPLETE
        }
        if (window == null) return EventStoryPhase.INCOMPLETE
        if (now >= window.reviewOpensAt) {
            return if (
                review.state == EventStoryChapterState.MISSED
            ) {
                EventStoryPhase.INCOMPLETE
            } else {
                EventStoryPhase.REVIEW_DUE
            }
        }
        if (now >= window.startAt) {
            return EventStoryPhase.IN_PROGRESS
        }
        val sourceReady = source.state !=
            EventStoryChapterState.ATTENTION
        val ready = sourceReady &&
            facts.state == EventStoryChapterState.COMPLETE &&
            plan.state == EventStoryChapterState.COMPLETE &&
            decision.state == EventStoryChapterState.COMPLETE
        return if (ready) {
            EventStoryPhase.READY
        } else {
            EventStoryPhase.PREPARING
        }
    }

    private fun fingerprintFor(
        input: EventStoryInput,
        window: EventStoryWindow?,
        relay: EvidenceRelayResult?,
        protocol: PreflightProtocol?,
        sync: PreflightSyncResult?,
        chapters: List<EventStoryChapterResult>,
        phase: EventStoryPhase,
        action: EventStoryAction,
        actionFactor: SignalFactor?
    ): String {
        val payload = buildString {
            append(VERSION)
            appendField(input.event.id)
            appendField(input.event.match)
            appendField(input.sourceState.name)
            appendField(window?.startAt?.toString() ?: "unknown")
            appendField(
                window?.reviewOpensAt?.toString() ?: "unknown"
            )
            appendField(relay?.fingerprint ?: "none")
            appendField(protocol?.fingerprint ?: "none")
            appendField(sync?.state?.name ?: "none")
            appendField(
                sync?.currentScheduleFingerprint ?: "none"
            )
            appendField(input.storedReceipt.integrity.name)
            appendField(
                input.storedReceipt.receipt?.fingerprint ?: "none"
            )
            appendField(input.snapshot?.fingerprint ?: "none")
            appendField(input.review?.fingerprint ?: "none")
            chapters.forEach { chapter ->
                appendField(chapter.chapter.name)
                appendField(chapter.state.name)
            }
            appendField(phase.name)
            appendField(action.name)
            appendField(actionFactor?.name ?: "none")
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
