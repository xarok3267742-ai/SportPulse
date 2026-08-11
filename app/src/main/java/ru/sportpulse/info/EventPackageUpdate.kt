package ru.sportpulse.info

import java.security.MessageDigest
import java.util.Locale

internal enum class EventPackageUpdateStatus {
    FIRST_VERSION,
    IDENTICAL,
    ACCEPTED,
    ROLLBACK,
    CONFLICT,
    TRUST_DOWNGRADE
}

internal object EventPackageUpdatePolicy {
    fun evaluate(
        current: SportEventPackage?,
        candidate: SportEventPackage
    ): EventPackageUpdateStatus {
        if (current == null) {
            return EventPackageUpdateStatus.FIRST_VERSION
        }
        if (
            trustRank(candidate.authenticity) <
            trustRank(current.authenticity)
        ) {
            return EventPackageUpdateStatus.TRUST_DOWNGRADE
        }
        if (current.fingerprint == candidate.fingerprint) {
            if (
                !current.authenticity.isAuthenticated &&
                candidate.authenticity.isAuthenticated
            ) {
                return EventPackageUpdateStatus.ACCEPTED
            }
            if (
                current.authenticity.keyId !=
                candidate.authenticity.keyId
            ) {
                return EventPackageUpdateStatus.ACCEPTED
            }
            return EventPackageUpdateStatus.IDENTICAL
        }
        if (
            !EventPackageIdentity.isSameSource(
                current.sourceLabel,
                candidate.sourceLabel
            )
        ) {
            return EventPackageUpdateStatus.ACCEPTED
        }
        return when {
            candidate.generatedAt < current.generatedAt ->
                EventPackageUpdateStatus.ROLLBACK
            candidate.generatedAt == current.generatedAt ->
                EventPackageUpdateStatus.CONFLICT
            else ->
                EventPackageUpdateStatus.ACCEPTED
        }
    }

    fun requireImportable(
        current: SportEventPackage?,
        candidate: SportEventPackage
    ): EventPackageUpdateStatus {
        val status = evaluate(current, candidate)
        when (status) {
            EventPackageUpdateStatus.ROLLBACK ->
                throw EventPackageValidationException(
                    "Пакет старше текущей версии. Откат заблокирован."
                )
            EventPackageUpdateStatus.CONFLICT ->
                throw EventPackageValidationException(
                    "Найдены две разные версии с одинаковым временем выпуска."
                )
            EventPackageUpdateStatus.TRUST_DOWNGRADE ->
                throw EventPackageValidationException(
                    "Снятие подтвержденной подписи заблокировано. " +
                        "Сначала явно вернитесь к демо-каталогу."
                )
            else -> Unit
        }
        return status
    }

    private fun trustRank(
        authenticity: EventPackageAuthenticity
    ): Int {
        return when (authenticity.keyEnvironment) {
            EventPackageKeyEnvironment.PRODUCTION -> 2
            EventPackageKeyEnvironment.DEVELOPMENT -> 1
            null -> 0
        }
    }
}

internal object EventPackageIdentity {
    fun isSameSource(
        first: String,
        second: String
    ): Boolean = sourceKey(first) == sourceKey(second)

    fun runtimeEventId(
        sourceLabel: String,
        eventId: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceKey(sourceLabel).toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { "%02x".format(it) }
        return "pack_${digest}_$eventId"
    }

    private fun sourceKey(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }
}

internal enum class EventPresenceChange {
    ADDED,
    REMOVED
}

internal enum class EventDetailField(
    val title: String
) {
    SPORT("вид спорта"),
    TOURNAMENT("турнир"),
    REGION("регион"),
    MATCH("название"),
    FOCUS("фокус"),
    NOTE("заметка"),
    TAGS("теги")
}

internal data class EventAssessmentChange(
    val factor: SignalFactor,
    val previousValue: Int,
    val currentValue: Int
) {
    val delta: Int
        get() = currentValue - previousValue
}

internal data class EventPackageEventChange(
    val eventId: String,
    val match: String,
    val presence: EventPresenceChange?,
    val previousStartAt: Long?,
    val currentStartAt: Long?,
    val assessmentChanges: List<EventAssessmentChange>,
    val detailChanges: Set<EventDetailField>
) {
    val isRescheduled: Boolean
        get() = presence == null &&
            previousStartAt != null &&
            currentStartAt != null &&
            previousStartAt != currentStartAt
}

internal data class EventPackageDelta(
    val previousPackage: SportEventPackage,
    val currentPackage: SportEventPackage,
    val sourceChanged: Boolean,
    val changes: List<EventPackageEventChange>
) {
    val addedCount: Int
        get() = changes.count {
            it.presence == EventPresenceChange.ADDED
        }
    val removedCount: Int
        get() = changes.count {
            it.presence == EventPresenceChange.REMOVED
        }
    val rescheduledCount: Int
        get() = changes.count(EventPackageEventChange::isRescheduled)
    val assessmentChangedCount: Int
        get() = changes.count { it.assessmentChanges.isNotEmpty() }
    val detailsChangedCount: Int
        get() = changes.count { it.detailChanges.isNotEmpty() }
    val trustChanged: Boolean
        get() =
            previousPackage.authenticity.level !=
                currentPackage.authenticity.level ||
                previousPackage.authenticity.keyId !=
                currentPackage.authenticity.keyId
    val changeCount: Int
        get() = changes.size + if (trustChanged) 1 else 0
    val hasChanges: Boolean
        get() = sourceChanged || changes.isNotEmpty() || trustChanged
}

internal object EventPackageDeltaEngine {
    fun compare(
        previous: SportEventPackage,
        current: SportEventPackage
    ): EventPackageDelta {
        val sourceChanged = !EventPackageIdentity.isSameSource(
            previous.sourceLabel,
            current.sourceLabel
        )
        val changes = if (sourceChanged) {
            previous.events
                .sortedBy(PackagedSportEvent::id)
                .map { removed(it) } +
                current.events
                    .sortedBy(PackagedSportEvent::id)
                    .map { added(it) }
        } else {
            compareSameSource(previous.events, current.events)
        }
        return EventPackageDelta(
            previousPackage = previous,
            currentPackage = current,
            sourceChanged = sourceChanged,
            changes = changes
        )
    }

    private fun compareSameSource(
        previousEvents: List<PackagedSportEvent>,
        currentEvents: List<PackagedSportEvent>
    ): List<EventPackageEventChange> {
        val previousById = previousEvents.associateBy(PackagedSportEvent::id)
        val currentById = currentEvents.associateBy(PackagedSportEvent::id)
        return (previousById.keys + currentById.keys)
            .sorted()
            .mapNotNull { eventId ->
                val previous = previousById[eventId]
                val current = currentById[eventId]
                when {
                    previous == null && current != null -> added(current)
                    previous != null && current == null -> removed(previous)
                    previous != null && current != null ->
                        changed(previous, current)
                    else -> null
                }
            }
    }

    private fun added(
        event: PackagedSportEvent
    ): EventPackageEventChange {
        return EventPackageEventChange(
            eventId = event.id,
            match = event.match,
            presence = EventPresenceChange.ADDED,
            previousStartAt = null,
            currentStartAt = event.startAt,
            assessmentChanges = emptyList(),
            detailChanges = emptySet()
        )
    }

    private fun removed(
        event: PackagedSportEvent
    ): EventPackageEventChange {
        return EventPackageEventChange(
            eventId = event.id,
            match = event.match,
            presence = EventPresenceChange.REMOVED,
            previousStartAt = event.startAt,
            currentStartAt = null,
            assessmentChanges = emptyList(),
            detailChanges = emptySet()
        )
    }

    private fun changed(
        previous: PackagedSportEvent,
        current: PackagedSportEvent
    ): EventPackageEventChange? {
        val assessmentChanges = SignalFactor.values()
            .mapNotNull { factor ->
                val previousValue = previous.seedAssessment.value(factor)
                val currentValue = current.seedAssessment.value(factor)
                if (previousValue == currentValue) {
                    null
                } else {
                    EventAssessmentChange(
                        factor = factor,
                        previousValue = previousValue,
                        currentValue = currentValue
                    )
                }
            }
        val detailChanges = buildSet {
            if (previous.sport != current.sport) {
                add(EventDetailField.SPORT)
            }
            if (previous.tournament != current.tournament) {
                add(EventDetailField.TOURNAMENT)
            }
            if (previous.region != current.region) {
                add(EventDetailField.REGION)
            }
            if (previous.match != current.match) {
                add(EventDetailField.MATCH)
            }
            if (previous.focus != current.focus) {
                add(EventDetailField.FOCUS)
            }
            if (previous.note != current.note) {
                add(EventDetailField.NOTE)
            }
            if (previous.tags != current.tags) {
                add(EventDetailField.TAGS)
            }
        }
        val rescheduled = previous.startAt != current.startAt
        if (
            !rescheduled &&
            assessmentChanges.isEmpty() &&
            detailChanges.isEmpty()
        ) {
            return null
        }
        return EventPackageEventChange(
            eventId = current.id,
            match = current.match,
            presence = null,
            previousStartAt = previous.startAt,
            currentStartAt = current.startAt,
            assessmentChanges = assessmentChanges,
            detailChanges = detailChanges
        )
    }
}
