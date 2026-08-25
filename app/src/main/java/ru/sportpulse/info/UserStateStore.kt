package ru.sportpulse.info

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object StoredEventPackageCodec {
    private const val PREFIX = "base64:"

    fun encode(json: String): String {
        val encoded = Base64.getEncoder().encodeToString(
            json.toByteArray(StandardCharsets.UTF_8)
        )
        return PREFIX + encoded
    }

    fun decode(stored: String): String? {
        if (!stored.startsWith(PREFIX)) {
            return stored
        }
        return runCatching {
            String(
                Base64.getDecoder().decode(
                    stored.removePrefix(PREFIX)
                ),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
    }
}

internal class UserStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "sport_pulse_state",
        Context.MODE_PRIVATE
    )

    var selectedEventId: String?
        get() = preferences.getString(KEY_SELECTED_EVENT, null)
        set(value) {
            preferences.edit().putString(KEY_SELECTED_EVENT, value).apply()
        }

    var hasSeenProductTour: Boolean
        get() = preferences.getBoolean(KEY_PRODUCT_TOUR_SEEN, false)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_PRODUCT_TOUR_SEEN, value)
                .apply()
        }

    var hasConfirmedAge: Boolean
        get() = preferences.getBoolean(KEY_AGE_CONFIRMED, false)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_AGE_CONFIRMED, value)
                .apply()
        }

    var dataDuelOpponentId: String?
        get() = preferences.getString(
            KEY_DATA_DUEL_OPPONENT,
            null
        )
        set(value) {
            preferences.edit()
                .putString(KEY_DATA_DUEL_OPPONENT, value)
                .apply()
        }

    var selectedMarketKind: MarketKind
        get() = preferences
            .getString(KEY_MARKET_LENS_KIND, null)
            ?.let {
                runCatching {
                    MarketKind.valueOf(it)
                }.getOrNull()
            }
            ?: MarketKind.ONE_X_TWO
        set(value) {
            preferences.edit()
                .putString(KEY_MARKET_LENS_KIND, value.name)
                .apply()
        }

    var selectedRegionalZone: RegionalZone
        get() = RegionalZone.fromStored(
            preferences.getString(KEY_REGIONAL_ZONE, null)
        )
        set(value) {
            preferences.edit()
                .putString(KEY_REGIONAL_ZONE, value.name)
                .apply()
        }

    var selectedPulseWorkspaceMode: PulseWorkspaceMode
        get() = PulseWorkspaceMode.fromStored(
            preferences.getString(
                KEY_PULSE_WORKSPACE_MODE,
                null
            )
        )
        set(value) {
            preferences.edit()
                .putString(
                    KEY_PULSE_WORKSPACE_MODE,
                    value.name
                )
                .apply()
        }

    var selectedFeedWorkspaceMode: FeedWorkspaceMode
        get() = FeedWorkspaceMode.fromStored(
            preferences.getString(
                KEY_FEED_WORKSPACE_MODE,
                null
            )
        )
        set(value) {
            preferences.edit()
                .putString(
                    KEY_FEED_WORKSPACE_MODE,
                    value.name
                )
                .apply()
        }

    fun bookmarkedIds(): Set<String> {
        return preferences.getStringSet(KEY_BOOKMARKS, emptySet())
            ?.toSet()
            .orEmpty()
    }

    fun toggleBookmark(eventId: String): Boolean {
        val updated = bookmarkedIds().toMutableSet()
        val isSaved = if (updated.contains(eventId)) {
            updated.remove(eventId)
            false
        } else {
            updated.add(eventId)
            true
        }
        preferences.edit().putStringSet(KEY_BOOKMARKS, updated).apply()
        return isSaved
    }

    fun importedEventPackageJson(): String? {
        return preferences.getString(KEY_EVENT_PACKAGE, null)
            ?.let(StoredEventPackageCodec::decode)
    }

    fun previousImportedEventPackageJson(): String? {
        return preferences.getString(
            KEY_PREVIOUS_EVENT_PACKAGE,
            null
        )?.let(StoredEventPackageCodec::decode)
    }

    fun replaceImportedEventPackage(json: String) {
        val current = preferences.getString(
            KEY_EVENT_PACKAGE,
            null
        )
        preferences.edit().apply {
            if (current == null) {
                remove(KEY_PREVIOUS_EVENT_PACKAGE)
            } else {
                putString(KEY_PREVIOUS_EVENT_PACKAGE, current)
            }
            putString(
                KEY_EVENT_PACKAGE,
                StoredEventPackageCodec.encode(json)
            )
        }.apply()
    }

    fun clearPreviousImportedEventPackage() {
        preferences.edit()
            .remove(KEY_PREVIOUS_EVENT_PACKAGE)
            .apply()
    }

    fun clearImportedEventPackage() {
        preferences.edit()
            .remove(KEY_EVENT_PACKAGE)
            .remove(KEY_PREVIOUS_EVENT_PACKAGE)
            .apply()
    }

    fun assessment(event: SportEvent): SignalAssessment {
        val values = SignalFactor.values().map { factor ->
            if (
                factReceipt(event.id, factor).integrity ==
                FactReceiptIntegrity.TAMPERED
            ) {
                event.seedAssessment.value(factor)
            } else {
                preferences.getInt(
                    signalKey(event.id, factor),
                    event.seedAssessment.value(factor)
                )
            }
        }
        return SignalAssessment(values)
    }

    fun saveAssessment(eventId: String, assessment: SignalAssessment) {
        preferences.edit().apply {
            SignalFactor.values().forEach { factor ->
                putInt(signalKey(eventId, factor), assessment.value(factor))
            }
        }.apply()
    }

    fun claimedEvidence(eventId: String): EvidenceAssessment {
        return claimedEvidence(
            eventId = eventId,
            defaultLevel = EvidenceLevel.SINGLE_SOURCE
        )
    }

    fun claimedEvidence(event: SportEvent): EvidenceAssessment {
        return claimedEvidence(
            eventId = event.id,
            defaultLevel = event.defaultEvidenceLevel
        )
    }

    private fun claimedEvidence(
        eventId: String,
        defaultLevel: EvidenceLevel
    ): EvidenceAssessment {
        val levels = SignalFactor.values().map { factor ->
            if (
                factReceipt(eventId, factor).integrity ==
                FactReceiptIntegrity.TAMPERED
            ) {
                EvidenceLevel.UNCONFIRMED
            } else {
                val stored = preferences.getString(
                    evidenceKey(eventId, factor),
                    null
                )
                stored?.let {
                    runCatching {
                        EvidenceLevel.valueOf(it)
                    }.getOrNull()
                } ?: defaultLevel
            }
        }
        return EvidenceAssessment(levels)
    }

    fun sourceAudit(eventId: String): SourceAuditAssessment {
        val states = SignalFactor.values().map { factor ->
            val stored = preferences.getString(
                sourceAuditKey(eventId, factor),
                null
            )
            stored?.let {
                runCatching {
                    SourceAuditState.valueOf(it)
                }.getOrNull()
            } ?: SourceAuditState.UNAUDITED
        }
        return SourceAuditAssessment(states)
    }

    fun factReceipt(
        eventId: String,
        factor: SignalFactor
    ): FactReceiptReadResult {
        val stored = preferences.getString(
            factReceiptKey(eventId, factor),
            null
        ) ?: return FactReceiptReadResult(
            integrity = FactReceiptIntegrity.EMPTY,
            receipt = null
        )
        val receipt = runCatching {
            FactReceiptCodec.decode(stored)
        }.getOrNull()?.takeIf {
            it.eventId == eventId && it.factor == factor
        }
        return if (receipt == null) {
            FactReceiptReadResult(
                integrity = FactReceiptIntegrity.TAMPERED,
                receipt = null
            )
        } else {
            FactReceiptReadResult(
                integrity = FactReceiptIntegrity.VALID,
                receipt = receipt
            )
        }
    }

    fun factReceiptCount(eventId: String): Int {
        return SignalFactor.values().count { factor ->
            factReceipt(eventId, factor).integrity ==
                FactReceiptIntegrity.VALID
        }
    }

    fun saveFactReceipt(receipt: FactReceipt) {
        preferences.edit()
            .putString(
                factReceiptKey(receipt.eventId, receipt.factor),
                FactReceiptCodec.encode(receipt)
            )
            .putInt(
                signalKey(receipt.eventId, receipt.factor),
                receipt.coverage.score
            )
            .putString(
                evidenceKey(receipt.eventId, receipt.factor),
                receipt.claimedEvidence.name
            )
            .putLong(
                evidenceTimeKey(receipt.eventId, receipt.factor),
                receipt.checkedAt
            )
            .putString(
                sourceAuditKey(receipt.eventId, receipt.factor),
                receipt.sourceAuditState.name
            )
            .apply()
    }

    fun clearFactReceipt(
        event: SportEvent,
        factor: SignalFactor
    ) {
        preferences.edit()
            .remove(factReceiptKey(event.id, factor))
            .putInt(
                signalKey(event.id, factor),
                event.seedAssessment.value(factor)
            )
            .putString(
                evidenceKey(event.id, factor),
                event.defaultEvidenceLevel.name
            )
            .remove(evidenceTimeKey(event.id, factor))
            .putString(
                sourceAuditKey(event.id, factor),
                SourceAuditState.UNAUDITED.name
            )
            .apply()
    }

    fun invalidateFactReceipt(
        eventId: String,
        factor: SignalFactor
    ) {
        preferences.edit()
            .remove(factReceiptKey(eventId, factor))
            .apply()
    }

    fun counterReview(eventId: String): CounterReviewAssessment {
        val states = SignalFactor.values().map { factor ->
            val stored = preferences.getString(
                counterReviewKey(eventId, factor),
                null
            )
            stored?.let {
                runCatching {
                    CounterReviewState.valueOf(it)
                }.getOrNull()
            } ?: CounterReviewState.UNCHECKED
        }
        return CounterReviewAssessment(states)
    }

    fun evidence(eventId: String): EvidenceAssessment {
        return SourceIntegrityEngine.evaluate(
            claimedEvidence = claimedEvidence(eventId),
            audit = sourceAudit(eventId)
        ).effectiveEvidence
    }

    fun evidence(event: SportEvent): EvidenceAssessment {
        return SourceIntegrityEngine.evaluate(
            claimedEvidence = claimedEvidence(event),
            audit = sourceAudit(event.id)
        ).effectiveEvidence
    }

    fun evidenceTimeline(
        eventId: String,
        now: Long = System.currentTimeMillis()
    ): EvidenceTimeline {
        val editor = preferences.edit()
        var changed = false
        val timestamps = SignalFactor.values().map { factor ->
            val stored = preferences.getLong(
                evidenceTimeKey(eventId, factor),
                0L
            )
            if (stored > 0L) {
                stored
            } else {
                changed = true
                editor.putLong(evidenceTimeKey(eventId, factor), now)
                now
            }
        }
        if (changed) editor.apply()
        return EvidenceTimeline(timestamps)
    }

    fun evidenceTimelinePreview(
        eventId: String,
        now: Long = System.currentTimeMillis()
    ): EvidenceTimeline {
        require(now >= 0L)
        return EvidenceTimeline(
            SignalFactor.values().map { factor ->
                preferences.getLong(
                    evidenceTimeKey(eventId, factor),
                    0L
                ).takeIf { it > 0L } ?: now
            }
        )
    }

    fun hasEvidenceHistory(eventId: String): Boolean {
        return SignalFactor.values().any { factor ->
            hasEvidenceHistory(eventId, factor)
        }
    }

    fun hasEvidenceHistory(
        eventId: String,
        factor: SignalFactor
    ): Boolean {
        return preferences.getLong(
            evidenceTimeKey(eventId, factor),
            0L
        ) > 0L
    }

    fun saveEvidenceLevel(
        eventId: String,
        factor: SignalFactor,
        level: EvidenceLevel,
        checkedAt: Long = System.currentTimeMillis()
    ) {
        preferences.edit()
            .putString(evidenceKey(eventId, factor), level.name)
            .putLong(evidenceTimeKey(eventId, factor), checkedAt)
            .apply()
    }

    fun saveSourceAuditState(
        eventId: String,
        factor: SignalFactor,
        auditState: SourceAuditState
    ) {
        preferences.edit()
            .putString(
                sourceAuditKey(eventId, factor),
                auditState.name
            )
            .apply()
    }

    fun saveCounterReviewState(
        eventId: String,
        factor: SignalFactor,
        reviewState: CounterReviewState
    ) {
        preferences.edit()
            .putString(
                counterReviewKey(eventId, factor),
                reviewState.name
            )
            .apply()
    }

    fun completedGuideSteps(): Set<Int> {
        return preferences.getStringSet(KEY_GUIDE_STEPS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()
    }

    fun setGuideStepCompleted(index: Int, completed: Boolean) {
        val updated = completedGuideSteps().toMutableSet()
        if (completed) {
            updated.add(index)
        } else {
            updated.remove(index)
        }
        preferences.edit()
            .putStringSet(KEY_GUIDE_STEPS, updated.map(Int::toString).toSet())
            .apply()
    }

    fun clearGuide() {
        preferences.edit().remove(KEY_GUIDE_STEPS).apply()
    }

    val attentionLimitMinutes: Int
        get() = preferences.getInt(
            KEY_ATTENTION_LIMIT_MINUTES,
            AttentionBudgetPolicy.DEFAULT_LIMIT_MINUTES
        ).takeIf(AttentionBudgetPolicy::isValidLimit)
            ?: AttentionBudgetPolicy.DEFAULT_LIMIT_MINUTES

    fun attentionBudget(
        now: Long = System.currentTimeMillis()
    ): AttentionBudgetResult {
        require(now >= 0L)
        val day = AttentionBudgetDay.epochDay(now)
        val storedDay = preferences.getLong(
            KEY_ATTENTION_DAY,
            Long.MIN_VALUE
        )
        val used = if (storedDay == day) {
            preferences.getLong(
                KEY_ATTENTION_USED_MILLIS,
                0L
            ).coerceIn(0L, AttentionBudgetPolicy.DAY_MILLIS)
        } else {
            0L
        }
        return AttentionBudgetEngine.evaluate(
            dayEpoch = day,
            usedMillis = used,
            limitMinutes = attentionLimitMinutes
        )
    }

    fun addAttentionUsage(
        now: Long,
        durationMillis: Long
    ): AttentionBudgetResult {
        require(now >= 0L)
        require(durationMillis >= 0L)
        val current = attentionBudget(now)
        val updatedMillis = (
            current.usedMillis + durationMillis.coerceAtMost(
                AttentionBudgetPolicy.DAY_MILLIS
            )
            ).coerceAtMost(AttentionBudgetPolicy.DAY_MILLIS)
        preferences.edit()
            .putLong(KEY_ATTENTION_DAY, current.dayEpoch)
            .putLong(
                KEY_ATTENTION_USED_MILLIS,
                updatedMillis
            )
            .apply()
        return AttentionBudgetEngine.evaluate(
            dayEpoch = current.dayEpoch,
            usedMillis = updatedMillis,
            limitMinutes = current.limitMinutes
        )
    }

    fun updateAttentionLimitMinutes(
        proposedMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        require(
            AttentionBudgetPolicy.isValidLimit(proposedMinutes)
        )
        val current = attentionBudget(now)
        if (
            !AttentionBudgetPolicy.canChangeLimit(
                currentMinutes = current.limitMinutes,
                proposedMinutes = proposedMinutes,
                usedMillis = current.usedMillis
            )
        ) {
            return false
        }
        preferences.edit()
            .putInt(
                KEY_ATTENTION_LIMIT_MINUTES,
                proposedMinutes
            )
            .apply()
        return true
    }

    fun distanceClearance(
        now: Long = System.currentTimeMillis()
    ): DecisionDistanceClearance? {
        require(now >= 0L)
        return preferences.getString(
            KEY_DISTANCE_CLEARANCE,
            null
        )?.let(DecisionDistanceClearanceCodec::decode)
            ?.takeIf { it.isValidAt(now) }
    }

    fun saveDistanceClearance(
        clearance: DecisionDistanceClearance
    ) {
        preferences.edit()
            .putString(
                KEY_DISTANCE_CLEARANCE,
                DecisionDistanceClearanceCodec.encode(clearance)
            )
            .apply()
    }

    fun clearDistanceClearance() {
        preferences.edit()
            .remove(KEY_DISTANCE_CLEARANCE)
            .apply()
    }

    fun savedDecision(eventId: String): SavedDecision? {
        val stored = preferences.getString(decisionKey(eventId), null) ?: return null
        return runCatching { SavedDecision.valueOf(stored) }.getOrNull()
    }

    fun savedDecisionTime(eventId: String): Long {
        return preferences.getLong(decisionTimeKey(eventId), 0L)
    }

    fun decisionSnapshot(eventId: String): DecisionSnapshot? {
        val encoded = preferences.getString(
            decisionSnapshotKey(eventId),
            null
        ) ?: return null
        return DecisionSnapshotCodec.decode(encoded)
            ?.takeIf { it.eventId == eventId }
    }

    fun decisionDeskDraft(eventId: String): DecisionDeskDraft? {
        val encoded = preferences.getString(
            decisionDeskDraftKey(eventId),
            null
        ) ?: return null
        return DecisionDeskDraftCodec.decode(encoded)
            ?.takeIf { it.eventId == eventId }
    }

    fun saveDecisionDeskDraft(draft: DecisionDeskDraft) {
        preferences.edit()
            .putString(
                decisionDeskDraftKey(draft.eventId),
                DecisionDeskDraftCodec.encode(draft)
            )
            .apply()
    }

    fun clearDecisionDeskDraft(eventId: String) {
        preferences.edit()
            .remove(decisionDeskDraftKey(eventId))
            .apply()
    }

    fun decisionGuardBreach(
        eventId: String
    ): DecisionGuardBreach? {
        val encoded = preferences.getString(
            decisionGuardBreachKey(eventId),
            null
        ) ?: return null
        return DecisionGuardBreachCodec.decode(encoded)
            ?.takeIf { it.eventId == eventId }
    }

    fun preflightExportReceipt(
        eventId: String
    ): PreflightReceiptReadResult {
        val encoded = preferences.getString(
            preflightExportReceiptKey(eventId),
            null
        ) ?: return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.EMPTY,
            receipt = null
        )
        val receipt = PreflightExportReceiptCodec.decode(encoded)
        if (receipt == null || receipt.eventId != eventId) {
            return PreflightReceiptReadResult(
                integrity = PreflightReceiptIntegrity.TAMPERED,
                receipt = null
            )
        }
        return PreflightReceiptReadResult(
            integrity = PreflightReceiptIntegrity.VALID,
            receipt = receipt
        )
    }

    fun preflightExportReceipts(): Map<
        String,
        PreflightReceiptReadResult
    > {
        return preferences.all.keys
            .asSequence()
            .filter {
                it.startsWith(PREFLIGHT_EXPORT_RECEIPT_PREFIX) &&
                    it.length > PREFLIGHT_EXPORT_RECEIPT_PREFIX.length
            }
            .map {
                it.removePrefix(PREFLIGHT_EXPORT_RECEIPT_PREFIX)
            }
            .sorted()
            .associateWith(::preflightExportReceipt)
    }

    fun savePreflightExportReceipt(
        receipt: PreflightExportReceipt
    ) {
        preferences.edit()
            .putString(
                preflightExportReceiptKey(receipt.eventId),
                PreflightExportReceiptCodec.encode(receipt)
            )
            .apply()
    }

    fun clearPreflightExportReceipt(eventId: String) {
        preferences.edit()
            .remove(preflightExportReceiptKey(eventId))
            .apply()
    }

    fun saveDecisionGuardBreach(
        breach: DecisionGuardBreach
    ) {
        preferences.edit()
            .putString(
                decisionGuardBreachKey(breach.eventId),
                DecisionGuardBreachCodec.encode(breach)
            )
            .apply()
    }

    fun clearDecisionGuardBreach(
        eventId: String
    ) {
        preferences.edit()
            .remove(decisionGuardBreachKey(eventId))
            .apply()
    }

    fun postEventReview(eventId: String): PostEventReview? {
        val encoded = preferences.getString(
            postEventReviewKey(eventId),
            null
        ) ?: return null
        return PostEventReviewCodec.decode(encoded)
            ?.takeIf { it.eventId == eventId }
    }

    fun calibrationRecords(): List<CalibrationRecord> {
        return StoredCalibrationRecordCatalog.decode(
            stored = preferences.all,
            snapshotFor = ::decisionSnapshot
        )
    }

    fun decisionLedger(): DecisionLedgerReadResult {
        val encoded = preferences.getString(
            KEY_DECISION_LEDGER,
            null
        ) ?: return DecisionLedgerReadResult(
            integrity = DecisionLedgerIntegrity.EMPTY,
            ledger = DecisionLedgerFactory.empty()
        )
        val ledger = DecisionLedgerCodec.decode(encoded)
            ?: return DecisionLedgerReadResult(
                integrity = DecisionLedgerIntegrity.TAMPERED,
                ledger = null
            )
        return DecisionLedgerReadResult(
            integrity = if (ledger.records.isEmpty()) {
                DecisionLedgerIntegrity.EMPTY
            } else {
                DecisionLedgerIntegrity.INTACT
            },
            ledger = ledger
        )
    }

    fun resetDecisionLedger() {
        preferences.edit()
            .remove(KEY_DECISION_LEDGER)
            .apply()
    }

    fun savePostEventReview(review: PostEventReview) {
        preferences.edit()
            .putString(
                postEventReviewKey(review.eventId),
                PostEventReviewCodec.encode(review)
            )
            .apply()
    }

    fun clearPostEventReview(eventId: String) {
        preferences.edit()
            .remove(postEventReviewKey(eventId))
            .apply()
    }

    fun storyCheckpoint(eventId: String): StoryCheckpointReadResult {
        val encoded = preferences.getString(
            storyCheckpointKey(eventId),
            null
        ) ?: return StoryCheckpointReadResult(
            integrity = StoryCheckpointIntegrity.EMPTY,
            checkpoint = null
        )
        val checkpoint = StoryCheckpointCodec.decode(encoded)
        if (checkpoint == null || checkpoint.eventId != eventId) {
            return StoryCheckpointReadResult(
                integrity = StoryCheckpointIntegrity.TAMPERED,
                checkpoint = null
            )
        }
        return StoryCheckpointReadResult(
            integrity = StoryCheckpointIntegrity.VALID,
            checkpoint = checkpoint
        )
    }

    fun saveStoryCheckpoint(checkpoint: StoryCheckpoint) {
        preferences.edit()
            .putString(
                storyCheckpointKey(checkpoint.eventId),
                StoryCheckpointCodec.encode(checkpoint)
            )
            .apply()
    }

    fun clearStoryCheckpoint(eventId: String) {
        preferences.edit()
            .remove(storyCheckpointKey(eventId))
            .apply()
    }

    fun storyThread(eventId: String): StoryThreadReadResult {
        val encoded = preferences.getString(
            storyThreadKey(eventId),
            null
        ) ?: return StoryThreadReadResult(
            integrity = StoryThreadIntegrity.EMPTY,
            thread = null
        )
        val thread = StoryThreadCodec.decode(encoded)
        if (thread == null || thread.eventId != eventId) {
            return StoryThreadReadResult(
                integrity = StoryThreadIntegrity.TAMPERED,
                thread = null
            )
        }
        return StoryThreadReadResult(
            integrity = StoryThreadIntegrity.VALID,
            thread = thread
        )
    }

    fun storyThreads(): Map<String, StoryThreadReadResult> {
        return preferences.all.keys
            .asSequence()
            .filter {
                it.startsWith(STORY_THREAD_PREFIX) &&
                    it.length > STORY_THREAD_PREFIX.length
            }
            .map { it.removePrefix(STORY_THREAD_PREFIX) }
            .sorted()
            .associateWith(::storyThread)
    }

    fun saveStoryThread(thread: StoryThread) {
        preferences.edit()
            .putString(
                storyThreadKey(thread.eventId),
                StoryThreadCodec.encode(thread)
            )
            .apply()
    }

    fun clearStoryThread(eventId: String) {
        preferences.edit()
            .remove(storyThreadKey(eventId))
            .apply()
    }

    fun storyReturnCapsule(): StoryReturnCapsuleReadResult {
        val encoded = preferences.getString(
            KEY_STORY_RETURN_CAPSULE,
            null
        ) ?: return StoryReturnCapsuleReadResult(
            integrity = StoryReturnCapsuleIntegrity.EMPTY,
            capsule = null
        )
        val capsule = StoryReturnCapsuleCodec.decode(encoded)
            ?: return StoryReturnCapsuleReadResult(
                integrity = StoryReturnCapsuleIntegrity.TAMPERED,
                capsule = null
            )
        return StoryReturnCapsuleReadResult(
            integrity = StoryReturnCapsuleIntegrity.VALID,
            capsule = capsule
        )
    }

    fun clearStoryReturnCapsule(
        now: Long = System.currentTimeMillis()
    ) {
        require(now >= 0L)
        require(!isPauseActive(now))
        preferences.edit()
            .remove(KEY_STORY_RETURN_CAPSULE)
            .apply()
    }

    fun saveDecision(
        eventId: String,
        eventLabel: String = eventId,
        decision: SavedDecision,
        assessment: SignalAssessment,
        evidence: EvidenceAssessment,
        timeline: EvidenceTimeline,
        counterReview: CounterReviewAssessment,
        distanceClearance: DecisionDistanceClearance? = null,
        savedAt: Long = System.currentTimeMillis()
    ): DecisionSnapshot {
        val ledgerRead = decisionLedger()
        require(
            ledgerRead.integrity !=
                DecisionLedgerIntegrity.TAMPERED
        )
        require(
            DecisionDistancePolicy.allows(
                decision = decision,
                clearance = distanceClearance,
                now = savedAt
            )
        )
        val attentionBudget = attentionBudget(savedAt)
        require(
            AttentionBudgetPolicy.allows(
                decision = decision,
                budget = attentionBudget
            )
        )
        val snapshot = DecisionSnapshotFactory.create(
            eventId = eventId,
            decision = decision,
            savedAt = savedAt,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline,
            counterReview = counterReview,
            distanceClearanceFingerprint =
                distanceClearance?.fingerprint?.takeIf {
                    DecisionDistancePolicy.requiresClearance(
                        decision
                    )
                },
            attentionBudgetFingerprint =
                attentionBudget.fingerprint.takeIf {
                    AttentionBudgetPolicy.requiresBudget(decision)
                }
        )
        val ledger = DecisionLedgerFactory.append(
            ledger = requireNotNull(ledgerRead.ledger),
            snapshot = snapshot,
            eventLabel = eventLabel
        )
        val editor = preferences.edit()
            .putString(decisionKey(eventId), decision.name)
            .putLong(decisionTimeKey(eventId), savedAt)
            .putString(
                decisionSnapshotKey(eventId),
                DecisionSnapshotCodec.encode(snapshot)
            )
            .putString(
                KEY_DECISION_LEDGER,
                DecisionLedgerCodec.encode(ledger)
            )
            .remove(decisionGuardBreachKey(eventId))
            .remove(postEventReviewKey(eventId))
        if (DecisionDistancePolicy.requiresClearance(decision)) {
            editor.remove(KEY_DISTANCE_CLEARANCE)
        }
        editor.apply()
        return snapshot
    }

    fun activatePause(durationMillis: Long = DAY_MILLIS) {
        require(durationMillis > 0L)
        val now = System.currentTimeMillis()
        require(durationMillis <= Long.MAX_VALUE - now)
        activatePauseUntil(
            until = now + durationMillis,
            now = now
        )
    }

    fun activatePauseUntil(
        until: Long,
        now: Long = System.currentTimeMillis()
    ) {
        require(now >= 0L)
        require(until > now)
        val target = maxOf(pauseUntil(), until)
        preferences.edit()
            .putLong(KEY_PAUSE_UNTIL, target)
            .remove(KEY_DISTANCE_CLEARANCE)
            .apply()
    }

    fun activateStoryQuietWindow(
        capsule: StoryReturnCapsule,
        now: Long
    ) {
        require(now >= 0L)
        require(capsule.activatedAt == now)
        require(capsule.pauseUntil > now)
        require(!isPauseActive(now))
        require(
            preferences.getString(
                KEY_STORY_RETURN_CAPSULE,
                null
            ) == null
        )
        preferences.edit()
            .putLong(KEY_PAUSE_UNTIL, capsule.pauseUntil)
            .putString(
                KEY_STORY_RETURN_CAPSULE,
                StoryReturnCapsuleCodec.encode(capsule)
            )
            .remove(KEY_DISTANCE_CLEARANCE)
            .apply()
    }

    fun pauseUntil(): Long = preferences.getLong(KEY_PAUSE_UNTIL, 0L)

    fun isPauseActive(now: Long = System.currentTimeMillis()): Boolean {
        return pauseUntil() > now
    }

    private fun signalKey(eventId: String, factor: SignalFactor): String {
        return "signal_${eventId}_${factor.name}"
    }

    private fun evidenceKey(eventId: String, factor: SignalFactor): String {
        return "evidence_${eventId}_${factor.name}"
    }

    private fun evidenceTimeKey(eventId: String, factor: SignalFactor): String {
        return "evidence_time_${eventId}_${factor.name}"
    }

    private fun sourceAuditKey(
        eventId: String,
        factor: SignalFactor
    ): String {
        return "source_audit_${eventId}_${factor.name}"
    }

    private fun factReceiptKey(
        eventId: String,
        factor: SignalFactor
    ): String {
        return "fact_receipt_${eventId}_${factor.name}"
    }

    private fun counterReviewKey(
        eventId: String,
        factor: SignalFactor
    ): String {
        return "counter_review_${eventId}_${factor.name}"
    }

    private fun decisionKey(eventId: String): String = "decision_$eventId"

    private fun decisionTimeKey(eventId: String): String = "decision_time_$eventId"

    private fun decisionSnapshotKey(eventId: String): String {
        return "decision_snapshot_$eventId"
    }

    private fun decisionDeskDraftKey(eventId: String): String {
        return "$DECISION_DESK_DRAFT_PREFIX$eventId"
    }

    private fun decisionGuardBreachKey(
        eventId: String
    ): String {
        return "decision_guard_breach_$eventId"
    }

    private fun postEventReviewKey(eventId: String): String {
        return StoredCalibrationRecordCatalog.keyFor(eventId)
    }

    private fun preflightExportReceiptKey(eventId: String): String {
        return "$PREFLIGHT_EXPORT_RECEIPT_PREFIX$eventId"
    }

    private fun storyCheckpointKey(eventId: String): String {
        return "$STORY_CHECKPOINT_PREFIX$eventId"
    }

    private fun storyThreadKey(eventId: String): String {
        return "$STORY_THREAD_PREFIX$eventId"
    }

    private companion object {
        const val KEY_SELECTED_EVENT = "selected_event"
        const val KEY_PRODUCT_TOUR_SEEN = "product_tour_seen_v1"
        const val KEY_AGE_CONFIRMED = "age_confirmed_v1"
        const val PREFLIGHT_EXPORT_RECEIPT_PREFIX =
            "preflight_export_receipt_"
        const val STORY_CHECKPOINT_PREFIX =
            "story_checkpoint_"
        const val STORY_THREAD_PREFIX = "story_thread_"
        const val DECISION_DESK_DRAFT_PREFIX =
            "decision_desk_draft_"
        const val KEY_DATA_DUEL_OPPONENT =
            "data_duel_opponent"
        const val KEY_MARKET_LENS_KIND =
            "market_lens_kind"
        const val KEY_REGIONAL_ZONE = "regional_time_zone"
        const val KEY_PULSE_WORKSPACE_MODE =
            "pulse_workspace_mode"
        const val KEY_FEED_WORKSPACE_MODE =
            "feed_workspace_mode"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_GUIDE_STEPS = "guide_steps"
        const val KEY_PAUSE_UNTIL = "pause_until"
        const val KEY_STORY_RETURN_CAPSULE =
            "story_return_capsule"
        const val KEY_DISTANCE_CLEARANCE =
            "decision_distance_clearance"
        const val KEY_DECISION_LEDGER = "decision_ledger"
        const val KEY_ATTENTION_DAY = "attention_budget_day"
        const val KEY_ATTENTION_USED_MILLIS =
            "attention_budget_used_millis"
        const val KEY_ATTENTION_LIMIT_MINUTES =
            "attention_budget_limit_minutes"
        const val KEY_EVENT_PACKAGE = "event_package_json"
        const val KEY_PREVIOUS_EVENT_PACKAGE =
            "previous_event_package_json"
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
