package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class RevisionRadarState {
    TAMPERED,
    REMOVED,
    UNRESOLVED,
    STALE,
    WITHDRAWN,
    CURRENT,
    EXPIRED
}

internal enum class RevisionRadarAction {
    OPEN_EVENT,
    WITHDRAW,
    FORGET,
    NONE
}

internal data class RevisionRadarEvent(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int,
    val protocol: PreflightProtocol?
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require(catalogOrder >= 0)
        require(protocol == null || protocol.eventId == eventId)
        require(protocol == null || protocol.eventLabel == match)
    }
}

internal data class RevisionRadarEntry(
    val eventId: String,
    val match: String,
    val sport: String,
    val region: String,
    val catalogOrder: Int?,
    val presentInCatalog: Boolean,
    val state: RevisionRadarState,
    val action: RevisionRadarAction,
    val startAt: Long?,
    val drift: List<PreflightDriftKind>,
    val receipt: PreflightExportReceipt?,
    val currentScheduleFingerprint: String?
) {
    init {
        require(eventId.isNotBlank())
        require(match.isNotBlank())
        require(sport.isNotBlank())
        require(region.isNotBlank())
        require((catalogOrder != null) == presentInCatalog)
        require(catalogOrder == null || catalogOrder >= 0)
        require(startAt == null || startAt >= 0L)
        require(drift.distinct().size == drift.size)
        require(receipt == null || receipt.eventId == eventId)
        require(
            currentScheduleFingerprint == null ||
                HEX_64.matches(currentScheduleFingerprint)
        )
        when (state) {
            RevisionRadarState.TAMPERED -> {
                require(receipt == null)
                require(drift.isEmpty())
                require(
                    action == if (presentInCatalog) {
                        RevisionRadarAction.OPEN_EVENT
                    } else {
                        RevisionRadarAction.FORGET
                    }
                )
            }
            RevisionRadarState.REMOVED,
            RevisionRadarState.UNRESOLVED -> {
                requireNotNull(receipt)
                require(!receipt.withdrawn)
                require(action == RevisionRadarAction.WITHDRAW)
                require(drift.isEmpty())
            }
            RevisionRadarState.STALE -> {
                requireNotNull(receipt)
                require(!receipt.withdrawn)
                require(presentInCatalog)
                require(action == RevisionRadarAction.OPEN_EVENT)
                require(drift.isNotEmpty())
                requireNotNull(currentScheduleFingerprint)
            }
            RevisionRadarState.WITHDRAWN -> {
                requireNotNull(receipt)
                require(receipt.withdrawn)
                require(drift.isEmpty())
                require(
                    action == if (presentInCatalog) {
                        RevisionRadarAction.OPEN_EVENT
                    } else {
                        RevisionRadarAction.NONE
                    }
                )
            }
            RevisionRadarState.CURRENT -> {
                requireNotNull(receipt)
                require(!receipt.withdrawn)
                require(presentInCatalog)
                require(action == RevisionRadarAction.OPEN_EVENT)
                require(drift.isEmpty())
                requireNotNull(currentScheduleFingerprint)
            }
            RevisionRadarState.EXPIRED -> {
                requireNotNull(receipt)
                require(action == RevisionRadarAction.FORGET)
                require(drift.isEmpty())
            }
        }
    }

    val sequence: Int?
        get() = receipt?.sequence

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal data class RevisionRadarResult(
    val entries: List<RevisionRadarEntry>,
    val attentionCount: Int,
    val withdrawalCount: Int,
    val currentCount: Int,
    val withdrawnCount: Int,
    val expiredCount: Int,
    val fingerprint: String
) {
    init {
        require(entries.isNotEmpty())
        require(
            entries.map(RevisionRadarEntry::eventId)
                .distinct().size == entries.size
        )
        require(attentionCount >= 0)
        require(withdrawalCount >= 0)
        require(currentCount >= 0)
        require(withdrawnCount >= 0)
        require(expiredCount >= 0)
        require(withdrawalCount <= attentionCount)
        require(attentionCount == entries.count { it.isAttention })
        require(withdrawalCount == entries.count {
            it.action == RevisionRadarAction.WITHDRAW
        })
        require(currentCount == entries.count {
            it.state == RevisionRadarState.CURRENT
        })
        require(withdrawnCount == entries.count {
            it.state == RevisionRadarState.WITHDRAWN
        })
        require(expiredCount == entries.count {
            it.state == RevisionRadarState.EXPIRED
        })
        require(
            attentionCount + currentCount + withdrawnCount +
                expiredCount == entries.size
        )
        require(fingerprint.length == 64)
    }

    val visibleEntries: List<RevisionRadarEntry>
        get() {
            val attention = entries.filter { it.isAttention }
            if (attention.isNotEmpty()) {
                return attention.take(RevisionRadarPolicy.VISIBLE_EVENTS)
            }
            val active = entries.filter {
                it.state != RevisionRadarState.EXPIRED
            }
            return (active.ifEmpty { entries })
                .take(RevisionRadarPolicy.VISIBLE_EVENTS)
        }

    val leadingState: RevisionRadarState
        get() = entries.first().state

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal val RevisionRadarEntry.isAttention: Boolean
    get() = when (state) {
        RevisionRadarState.TAMPERED,
        RevisionRadarState.REMOVED,
        RevisionRadarState.UNRESOLVED,
        RevisionRadarState.STALE -> true
        RevisionRadarState.WITHDRAWN,
        RevisionRadarState.CURRENT,
        RevisionRadarState.EXPIRED -> false
    }

internal object RevisionRadarPolicy {
    const val VISIBLE_EVENTS = 3
}

internal object RevisionRadarEngine {
    private const val VERSION = "sport-pulse-revision-radar-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        events: List<RevisionRadarEvent>,
        storedReceipts: Map<String, PreflightReceiptReadResult>,
        selectedZone: RegionalZone,
        now: Long
    ): RevisionRadarResult? {
        require(now >= 0L)
        require(
            events.map(RevisionRadarEvent::eventId)
                .distinct().size == events.size
        )
        require(
            events.map(RevisionRadarEvent::catalogOrder)
                .distinct().size == events.size
        )
        require(storedReceipts.keys.all { it.isNotBlank() })
        storedReceipts.forEach { (eventId, stored) ->
            require(
                stored.receipt == null ||
                    stored.receipt.eventId == eventId
            )
        }
        val activeStored = storedReceipts.filterValues {
            it.integrity != PreflightReceiptIntegrity.EMPTY
        }
        if (activeStored.isEmpty()) return null

        val eventsById = events.associateBy(RevisionRadarEvent::eventId)
        val entries = buildList {
            events.forEach { event ->
                activeStored[event.eventId]?.let { stored ->
                    add(
                        currentEntry(
                            event = event,
                            stored = stored,
                            selectedZone = selectedZone,
                            now = now
                        )
                    )
                }
            }
            activeStored.forEach { (eventId, stored) ->
                if (eventId !in eventsById) {
                    add(
                        removedEntry(
                            eventId = eventId,
                            stored = stored,
                            now = now
                        )
                    )
                }
            }
        }.sortedWith(
            compareBy<RevisionRadarEntry> {
                stateRank(it.state)
            }.thenBy {
                it.startAt ?: Long.MAX_VALUE
            }.thenBy {
                it.catalogOrder ?: Int.MAX_VALUE
            }.thenBy(RevisionRadarEntry::eventId)
        )
        val attentionCount = entries.count { it.isAttention }
        val withdrawalCount = entries.count {
            it.action == RevisionRadarAction.WITHDRAW
        }
        val currentCount = entries.count {
            it.state == RevisionRadarState.CURRENT
        }
        val withdrawnCount = entries.count {
            it.state == RevisionRadarState.WITHDRAWN
        }
        val expiredCount = entries.count {
            it.state == RevisionRadarState.EXPIRED
        }
        return RevisionRadarResult(
            entries = entries,
            attentionCount = attentionCount,
            withdrawalCount = withdrawalCount,
            currentCount = currentCount,
            withdrawnCount = withdrawnCount,
            expiredCount = expiredCount,
            fingerprint = fingerprintFor(entries, selectedZone)
        )
    }

    private fun currentEntry(
        event: RevisionRadarEvent,
        stored: PreflightReceiptReadResult,
        selectedZone: RegionalZone,
        now: Long
    ): RevisionRadarEntry {
        if (stored.integrity == PreflightReceiptIntegrity.TAMPERED) {
            return entry(
                event = event,
                state = RevisionRadarState.TAMPERED,
                action = RevisionRadarAction.OPEN_EVENT,
                startAt = event.protocol?.start?.startAt
            )
        }
        val receipt = checkNotNull(stored.receipt)
        val protocol = event.protocol
        if (protocol == null) {
            val state = when {
                receipt.startAt <= now -> RevisionRadarState.EXPIRED
                receipt.withdrawn -> RevisionRadarState.WITHDRAWN
                else -> RevisionRadarState.UNRESOLVED
            }
            return entry(
                event = event,
                state = state,
                action = when (state) {
                    RevisionRadarState.UNRESOLVED ->
                        RevisionRadarAction.WITHDRAW
                    RevisionRadarState.WITHDRAWN ->
                        RevisionRadarAction.OPEN_EVENT
                    else -> RevisionRadarAction.FORGET
                },
                startAt = receipt.startAt,
                receipt = receipt
            )
        }
        val sync = PreflightSyncEngine.evaluate(
            protocol = protocol,
            selectedZone = selectedZone,
            stored = stored
        )
        val state = when (sync.state) {
            PreflightSyncState.CURRENT -> RevisionRadarState.CURRENT
            PreflightSyncState.STALE -> RevisionRadarState.STALE
            PreflightSyncState.WITHDRAWN ->
                RevisionRadarState.WITHDRAWN
            PreflightSyncState.NOT_EXPORTED,
            PreflightSyncState.TAMPERED ->
                error("Stored receipt changed during radar evaluation")
        }
        return entry(
            event = event,
            state = state,
            action = RevisionRadarAction.OPEN_EVENT,
            startAt = protocol.start.startAt,
            drift = sync.drift,
            receipt = receipt,
            currentScheduleFingerprint =
                sync.currentScheduleFingerprint
        )
    }

    private fun removedEntry(
        eventId: String,
        stored: PreflightReceiptReadResult,
        now: Long
    ): RevisionRadarEntry {
        if (stored.integrity == PreflightReceiptIntegrity.TAMPERED) {
            return RevisionRadarEntry(
                eventId = eventId,
                match = eventId,
                sport = "Вне каталога",
                region = "Локальная квитанция",
                catalogOrder = null,
                presentInCatalog = false,
                state = RevisionRadarState.TAMPERED,
                action = RevisionRadarAction.FORGET,
                startAt = null,
                drift = emptyList(),
                receipt = null,
                currentScheduleFingerprint = null
            )
        }
        val receipt = checkNotNull(stored.receipt)
        val state = when {
            receipt.startAt <= now -> RevisionRadarState.EXPIRED
            receipt.withdrawn -> RevisionRadarState.WITHDRAWN
            else -> RevisionRadarState.REMOVED
        }
        return RevisionRadarEntry(
            eventId = eventId,
            match = receipt.eventLabel,
            sport = "Вне каталога",
            region = receipt.selectedZone.city,
            catalogOrder = null,
            presentInCatalog = false,
            state = state,
            action = if (state == RevisionRadarState.REMOVED) {
                RevisionRadarAction.WITHDRAW
            } else if (state == RevisionRadarState.EXPIRED) {
                RevisionRadarAction.FORGET
            } else {
                RevisionRadarAction.NONE
            },
            startAt = receipt.startAt,
            drift = emptyList(),
            receipt = receipt,
            currentScheduleFingerprint = null
        )
    }

    private fun entry(
        event: RevisionRadarEvent,
        state: RevisionRadarState,
        action: RevisionRadarAction,
        startAt: Long?,
        drift: List<PreflightDriftKind> = emptyList(),
        receipt: PreflightExportReceipt? = null,
        currentScheduleFingerprint: String? = null
    ): RevisionRadarEntry {
        return RevisionRadarEntry(
            eventId = event.eventId,
            match = event.match,
            sport = event.sport,
            region = event.region,
            catalogOrder = event.catalogOrder,
            presentInCatalog = true,
            state = state,
            action = action,
            startAt = startAt,
            drift = drift,
            receipt = receipt,
            currentScheduleFingerprint = currentScheduleFingerprint
        )
    }

    private fun stateRank(state: RevisionRadarState): Int {
        return when (state) {
            RevisionRadarState.TAMPERED -> 0
            RevisionRadarState.REMOVED -> 1
            RevisionRadarState.UNRESOLVED -> 2
            RevisionRadarState.STALE -> 3
            RevisionRadarState.WITHDRAWN -> 4
            RevisionRadarState.CURRENT -> 5
            RevisionRadarState.EXPIRED -> 6
        }
    }

    private fun fingerprintFor(
        entries: List<RevisionRadarEntry>,
        selectedZone: RegionalZone
    ): String {
        return digest(
            buildString {
                append(VERSION)
                append("|zone=")
                append(selectedZone.name)
                entries.forEach { entry ->
                    append("|event=")
                    appendField(entry.eventId)
                    appendField(entry.match)
                    appendField(entry.sport)
                    appendField(entry.region)
                    appendField(entry.catalogOrder?.toString() ?: "orphan")
                    appendField(entry.presentInCatalog.toString())
                    appendField(entry.state.name)
                    appendField(entry.action.name)
                    appendField(entry.startAt?.toString() ?: "unknown")
                    appendField(
                        entry.receipt?.fingerprint ?: "tampered"
                    )
                    appendField(
                        entry.currentScheduleFingerprint ?: "none"
                    )
                    appendField(
                        entry.drift.joinToString(".") { it.name }
                    )
                }
            }
        )
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length)
        append(':')
        append(value)
    }

    private fun digest(payload: String): String {
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
}
