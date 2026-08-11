package ru.sportpulse.info

internal data class AnalysisPassportSnapshot(
    val event: SportEvent,
    val assessment: SignalAssessment,
    val result: SignalResult,
    val evidenceResult: EvidenceResult?,
    val confidenceShadow: ConfidenceShadowResult?,
    val decisionCorridor: DecisionCorridor?,
    val freshnessResult: FreshnessResult?,
    val verificationRoute: VerificationRoute?,
    val signalStress: SignalStressResult?,
    val decisionTrace: DecisionTraceResult?,
    val sourceIntegrity: SourceIntegrityResult?,
    val counterView: CounterViewResult?,
    val postEventReviewResult: PostEventReviewResult?,
    val decision: SavedDecision?,
    val generatedAt: Long
)

internal object AnalysisPassportFactory {
    private val unsafeFileCharacters = Regex("[^A-Za-z0-9_-]+")

    fun create(
        event: SportEvent,
        assessment: SignalAssessment,
        decision: SavedDecision?,
        evidence: EvidenceAssessment? = null,
        timeline: EvidenceTimeline? = null,
        sourceIntegrity: SourceIntegrityResult? = null,
        counterReview: CounterReviewAssessment? = null,
        decisionSnapshot: DecisionSnapshot? = null,
        postEventReview: PostEventReview? = null,
        generatedAt: Long = System.currentTimeMillis()
    ): AnalysisPassportSnapshot {
        val calculationEvidence =
            sourceIntegrity?.effectiveEvidence ?: evidence
        val freshnessResult = if (
            calculationEvidence != null &&
            timeline != null
        ) {
            FreshnessEngine.evaluate(
                calculationEvidence,
                timeline,
                generatedAt
            )
        } else {
            null
        }
        val effectiveEvidence =
            freshnessResult?.effectiveEvidence ?: calculationEvidence
        val evidenceResult = effectiveEvidence?.let {
            EvidenceEngine.evaluate(assessment, it)
        }
        val counterView = if (
            effectiveEvidence != null &&
            counterReview != null
        ) {
            CounterViewEngine.evaluate(
                assessment = assessment,
                evidence = effectiveEvidence,
                review = counterReview
            )
        } else {
            null
        }
        val confidenceShadow = effectiveEvidence?.let {
            ConfidenceShadowEngine.evaluate(assessment, it)
        }
        val decisionCorridor = effectiveEvidence?.let {
            DecisionCorridorEngine.evaluate(assessment, it)
        }
        val verificationRoute = effectiveEvidence?.let {
            VerificationRouteEngine.evaluate(assessment, it)
        }
        val signalStress = if (
            calculationEvidence != null &&
            timeline != null
        ) {
            SignalStressEngine.evaluate(
                assessment = assessment,
                evidence = calculationEvidence,
                timeline = timeline,
                now = generatedAt
            )
        } else {
            null
        }
        val decisionTrace = if (
            decisionSnapshot != null &&
            decisionSnapshot.eventId == event.id &&
            calculationEvidence != null &&
            timeline != null
        ) {
            DecisionTraceEngine.compare(
                snapshot = decisionSnapshot,
                currentAssessment = assessment,
                currentEvidence = calculationEvidence,
                currentTimeline = timeline,
                currentCounterReview =
                    counterReview
                        ?: decisionSnapshot.counterReview,
                now = generatedAt
            )
        } else {
            null
        }
        val postEventReviewResult = if (
            decisionSnapshot != null &&
            postEventReview?.isFinalized == true &&
            postEventReview.eventId == event.id &&
            postEventReview.decisionFingerprint ==
            decisionSnapshot.fingerprint
        ) {
            PostEventReviewEngine.evaluate(
                snapshot = decisionSnapshot,
                review = postEventReview
            )
        } else {
            null
        }
        val effectiveAssessment = evidenceResult?.effectiveAssessment ?: assessment
        return AnalysisPassportSnapshot(
            event = event,
            assessment = effectiveAssessment,
            result = evidenceResult?.effectiveSignal
                ?: SignalEngine.evaluate(effectiveAssessment),
            evidenceResult = evidenceResult,
            confidenceShadow = confidenceShadow,
            decisionCorridor = decisionCorridor,
            freshnessResult = freshnessResult,
            verificationRoute = verificationRoute,
            signalStress = signalStress,
            decisionTrace = decisionTrace,
            sourceIntegrity = sourceIntegrity,
            counterView = counterView,
            postEventReviewResult = postEventReviewResult,
            decision = decision,
            generatedAt = generatedAt
        )
    }

    fun fileName(snapshot: AnalysisPassportSnapshot): String {
        val safeEventId = unsafeFileCharacters
            .replace(snapshot.event.id, "_")
            .trim('_')
            .take(48)
            .ifBlank { "event" }
        return "sport_pulse_${safeEventId}_${snapshot.generatedAt}.png"
    }

    fun shareText(snapshot: AnalysisPassportSnapshot): String {
        return buildString {
            append("Паспорт события «")
            append(snapshot.event.match)
            append("». Ручная оценка проверки: ")
            append(snapshot.result.readiness)
            append("/100. Статус: ")
            append(verdictLabel(snapshot.result.verdict).lowercase())
            snapshot.evidenceResult?.let {
                append(". Кворум фактов: ")
                append(it.quorumCount)
                append("/5")
            }
            snapshot.sourceIntegrity?.let { integrity ->
                append(". Антиэхо: ")
                if (integrity.claimedQuorumCount == 0) {
                    append("заявленных кворумов нет")
                } else {
                    append("принято ")
                    append(integrity.acceptedQuorumCount)
                    append(" из ")
                    append(integrity.claimedQuorumCount)
                    append(" заявленных кворумов")
                }
                when (integrity.verdict) {
                    SourceIntegrityVerdict.ECHO ->
                        append(", обнаружена общая цепочка")
                    SourceIntegrityVerdict.CONFLICT ->
                        append(", обнаружено расхождение")
                    SourceIntegrityVerdict.OPEN ->
                        append(", независимость не проверена")
                    SourceIntegrityVerdict.AUDITED ->
                        append(", независимость подтверждена пользователем")
                    SourceIntegrityVerdict.NO_QUORUM -> Unit
                }
                append(", метка ")
                append(integrity.shortFingerprint)
            }
            snapshot.counterView?.let { counterView ->
                append(". Контрракурс: проверено ")
                append(counterView.reviewedCount)
                append("/5, предел «")
                append(
                    decisionLabel(
                        counterView.decisionCeiling
                    ).lowercase()
                )
                append("»")
                when (counterView.verdict) {
                    CounterViewVerdict.OPEN ->
                        append(", проверка не завершена")
                    CounterViewVerdict.BALANCED ->
                        append(", альтернативная версия проверена")
                    CounterViewVerdict.MIXED ->
                        append(", есть спорные факты")
                    CounterViewVerdict.REFUTED ->
                        append(", найден контрфакт")
                }
                append(", метка ")
                append(counterView.shortFingerprint)
            }
            snapshot.confidenceShadow?.let { shadow ->
                when (shadow.status) {
                    ConfidenceShadowStatus.CLEAR ->
                        append(". Тень уверенности: отсутствует")
                    ConfidenceShadowStatus.CONTAINED -> {
                        if (shadow.readinessGap == 0) {
                            append(
                                ". Тень уверенности: контур отличается, полнота без снижения"
                            )
                        } else {
                            append(". Тень уверенности: -")
                            append(shadow.readinessGap)
                            append(" пунктов, статус сохранен")
                        }
                    }
                    ConfidenceShadowStatus.VERDICT_SHIFT -> {
                        append(". Тень уверенности: -")
                        append(shadow.readinessGap)
                        append(" пунктов и меняет статус")
                    }
                }
            }
            snapshot.decisionCorridor?.let { corridor ->
                val boundary = corridor.nearestBoundary
                if (boundary == null) {
                    append(
                        ". Коридор решения: одного фактора недостаточно для смены статуса"
                    )
                } else {
                    append(". Коридор решения: ближайшая граница «")
                    append(boundary.factor.title)
                    append("» ")
                    append(boundary.claimedBefore)
                    append("→")
                    append(boundary.claimedAfter)
                    append(", статус «")
                    append(
                        verdictLabel(
                            boundary.result.effectiveSignal.verdict
                        ).lowercase()
                    )
                    append("»")
                }
            }
            snapshot.freshnessResult?.let { freshness ->
                if (
                    freshness.degradedFactors.isNotEmpty() ||
                    freshness.expiredFactors.isNotEmpty()
                ) {
                    append(". Есть устаревшие факторы")
                } else {
                    freshness.nextTransitionAt?.let { transitionAt ->
                        append(". Сигнал обновить через ")
                        append(
                            FreshnessFormatter.duration(
                                transitionAt - snapshot.generatedAt
                            )
                        )
                    }
                }
            }
            snapshot.verificationRoute?.let { route ->
                when (route.status) {
                    VerificationRouteStatus.REACHABLE -> {
                        append(". Минимальная проверка: ")
                        append(
                            route.steps.joinToString(", ") {
                                it.factor.title.lowercase()
                            }
                        )
                    }
                    VerificationRouteStatus.FACTS_LIMIT -> {
                        append(". Предел текущих фактов: ")
                        append(
                            route.allQuorumResult
                                .effectiveSignal
                                .readiness
                        )
                        append("/")
                        append(route.targetReadiness)
                    }
                    VerificationRouteStatus.READY_MAINTAIN ->
                        append(". Статус данных достигнут")
                }
            }
            snapshot.signalStress?.let { stress ->
                when (stress.status) {
                    SignalStressStatus.ROBUST ->
                        append(". Стресс-тест: один сбой не меняет статус")
                    SignalStressStatus.FRAGILE -> {
                        append(". Стресс-тест: потеря подтверждения «")
                        append(stress.criticalShock?.factor?.title)
                        append("» меняет статус")
                    }
                    SignalStressStatus.NO_BUFFER ->
                        append(". Стресс-тест: запас подтверждений отсутствует")
                }
                stress.firstVerdictChange?.let { point ->
                    append(". Без обновлений статус изменится через ")
                    append(
                        FreshnessFormatter.duration(
                            point.at - snapshot.generatedAt
                        )
                    )
                }
            }
            snapshot.decisionTrace?.let { trace ->
                append(". След решения: ")
                if (trace.changedFactors.isEmpty()) {
                    append("изменений нет")
                } else {
                    append(
                        trace.baselineEvidenceResult
                            .effectiveSignal
                            .readiness
                    )
                    append("→")
                    append(
                        trace.currentEvidenceResult
                            .effectiveSignal
                            .readiness
                    )
                    append(", факторов ")
                    append(trace.changedFactors.size)
                }
                append(", метка ")
                append(trace.snapshot.shortFingerprint)
            }
            snapshot.postEventReviewResult?.let { review ->
                append(". После свистка: ")
                when (review.status) {
                    PostEventReviewStatus.RELIABLE ->
                        append("данные устойчивы")
                    PostEventReviewStatus.MIXED ->
                        append("данные подтвердились частично")
                    PostEventReviewStatus.FRAGILE ->
                        append("исходная картина хрупкая")
                    PostEventReviewStatus.NOT_ENOUGH_DATA ->
                        append("недостаточно проверяемых факторов")
                }
                review.reliabilityScore?.let {
                    if (
                        review.status !=
                        PostEventReviewStatus.NOT_ENOUGH_DATA
                    ) {
                        append(", ")
                        append(it)
                        append("/100")
                    }
                }
                append(", проверено ")
                append(review.verifiedCount)
                append("/5, метка ")
                append(review.review.shortFingerprint)
            }
            append(". Информационный материал 18+, не прогноз.")
        }
    }

    fun verdictLabel(verdict: SignalVerdict): String {
        return when (verdict) {
            SignalVerdict.SKIP -> "ПРОПУСТИТЬ"
            SignalVerdict.OBSERVE -> "НАБЛЮДАТЬ"
            SignalVerdict.READY -> "ФАКТЫ СВЕРЕНЫ"
        }
    }

    fun decisionLabel(decision: SavedDecision?): String {
        return when (decision) {
            SavedDecision.SKIP -> "ПРОПУСТИТЬ"
            SavedDecision.OBSERVE -> "НАБЛЮДАТЬ"
            SavedDecision.DATA_READY -> "ФАКТЫ СВЕРЕНЫ"
            null -> "НЕ ЗАФИКСИРОВАНО"
        }
    }
}
