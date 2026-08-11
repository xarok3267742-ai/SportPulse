package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class VerificationCommandPriority(
    val title: String,
    val shortTitle: String
) {
    STOP("Остановить", "СТОП"),
    REPAIR("Исправить", "РЕМОНТ"),
    REFRESH("Обновить", "СВЕЖЕСТЬ"),
    CHALLENGE("Оспорить", "ПРОВЕРКА"),
    UNBLOCK("Разблокировать", "МАРШРУТ"),
    MAINTAIN("Поддерживать", "КОНТРОЛЬ")
}

internal enum class VerificationCommandKind {
    GUARD_BREACH,
    SOURCE_CONFLICT,
    COUNTERFACT,
    SOURCE_ECHO,
    SOURCE_AUDIT,
    EVIDENCE_EXPIRED,
    EVIDENCE_EXPIRING,
    COUNTER_DISPUTE,
    COUNTER_OPEN,
    ROUTE_CHECK,
    MARKET_CHECK,
    MARKET_FRESHNESS,
    MAINTENANCE
}

internal enum class VerificationCommandModule(
    val title: String
) {
    GUARD("Стоп-контракт"),
    SOURCES("Источники"),
    FRESHNESS("Свежесть"),
    COUNTERVIEW("Контрракурс"),
    MARKETS("Рынки"),
    ROUTE("Маршрут")
}

internal enum class VerificationCommandStatus {
    STOP,
    ATTENTION,
    ACTIVE,
    STABLE
}

internal data class VerificationCommandInput(
    val eventId: String,
    val sport: String,
    val assessment: SignalAssessment,
    val claimedEvidence: EvidenceAssessment,
    val sourceAudit: SourceAuditAssessment,
    val timeline: EvidenceTimeline,
    val counterReview: CounterReviewAssessment,
    val decisionSnapshot: DecisionSnapshot? = null,
    val decisionGuardBreach: DecisionGuardBreach? = null
) {
    init {
        require(eventId.isNotBlank())
        require(
            decisionSnapshot == null ||
                decisionSnapshot.eventId == eventId
        )
        require(
            decisionGuardBreach == null ||
                decisionGuardBreach.eventId == eventId
        )
    }
}

internal data class VerificationCommandTask(
    val priority: VerificationCommandPriority,
    val kinds: List<VerificationCommandKind>,
    val factor: SignalFactor?,
    val title: String,
    val reason: String,
    val modules: List<VerificationCommandModule>,
    val dueAt: Long?,
    val readinessImpact: Int,
    val fingerprint: String
) {
    init {
        require(kinds.isNotEmpty())
        require(kinds.distinct().size == kinds.size)
        require(title.isNotBlank())
        require(reason.isNotBlank())
        require(modules.isNotEmpty())
        require(modules.distinct().size == modules.size)
        require(dueAt == null || dueAt >= 0L)
        require(readinessImpact >= 0)
        require(fingerprint.length == 64)
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal data class VerificationCommandResult(
    val input: VerificationCommandInput,
    val sourceIntegrity: SourceIntegrityResult,
    val freshness: FreshnessResult,
    val evidenceResult: EvidenceResult,
    val counterView: CounterViewResult,
    val marketLens: MarketLensResult,
    val route: VerificationRoute,
    val decisionGuard: DecisionGuardResult?,
    val tasks: List<VerificationCommandTask>,
    val status: VerificationCommandStatus,
    val fingerprint: String
) {
    init {
        require(tasks.isNotEmpty())
        require(fingerprint.length == 64)
    }

    val visibleTasks: List<VerificationCommandTask>
        get() = tasks.take(VerificationCommandPolicy.VISIBLE_TASKS)

    val urgentCount: Int
        get() = tasks.count {
            it.priority == VerificationCommandPriority.STOP ||
                it.priority == VerificationCommandPriority.REPAIR
        }

    val nextDeadlineAt: Long?
        get() = tasks.mapNotNull(VerificationCommandTask::dueAt)
            .minOrNull()

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object VerificationCommandPolicy {
    const val VISIBLE_TASKS = 3
}

internal object VerificationCommandEngine {
    private const val VERSION = "sport-pulse-verification-command-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun evaluate(
        input: VerificationCommandInput,
        now: Long
    ): VerificationCommandResult {
        require(now >= 0L)
        val sourceIntegrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = input.claimedEvidence,
            audit = input.sourceAudit
        )
        val freshness = FreshnessEngine.evaluate(
            evidence = sourceIntegrity.effectiveEvidence,
            timeline = input.timeline,
            now = now
        )
        val evidenceResult = EvidenceEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence
        )
        val counterView = CounterViewEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence,
            review = input.counterReview
        )
        val marketLens = MarketLensEngine.evaluate(
            sport = input.sport,
            assessment = input.assessment,
            evidence = sourceIntegrity.effectiveEvidence,
            timeline = input.timeline,
            now = now
        )
        val route = VerificationRouteEngine.evaluate(
            assessment = input.assessment,
            evidence = freshness.effectiveEvidence
        )
        val decisionGuard = input.decisionSnapshot?.let { snapshot ->
            DecisionGuardEngine.evaluate(
                snapshot = snapshot,
                currentAssessment = input.assessment,
                currentEvidence = sourceIntegrity.effectiveEvidence,
                currentTimeline = input.timeline,
                currentCounterReview = input.counterReview,
                now = now
            )
        }?.let { live ->
            input.decisionGuardBreach?.let { breach ->
                runCatching {
                    live.withBreach(breach)
                }.getOrNull()
            } ?: live
        }
        val drafts = buildList {
            addGuardTasks(decisionGuard)
            addSourceTasks(sourceIntegrity)
            addFreshnessTasks(freshness)
            addCounterTasks(counterView)
            addMarketTasks(marketLens)
            addRouteTasks(route, freshness)
        }
        val tasks = mergeAndSort(
            drafts.ifEmpty {
                listOf(
                    TaskDraft(
                        priority =
                            VerificationCommandPriority.MAINTAIN,
                        kind = VerificationCommandKind.MAINTENANCE,
                        factor = evidenceResult.effectiveSignal
                            .weakestFactor,
                        title = "Сохранить контрольный срез",
                        reason =
                            "Активных блокеров нет; продолжайте контролировать самый слабый фактор.",
                        modules = listOf(
                            VerificationCommandModule.FRESHNESS,
                            VerificationCommandModule.ROUTE
                        )
                    )
                )
            }
        )
        val status = when {
            tasks.any {
                it.priority == VerificationCommandPriority.STOP
            } -> VerificationCommandStatus.STOP
            tasks.any {
                it.priority == VerificationCommandPriority.REPAIR ||
                    it.priority == VerificationCommandPriority.REFRESH
            } -> VerificationCommandStatus.ATTENTION
            tasks.any {
                it.priority == VerificationCommandPriority.CHALLENGE ||
                    it.priority == VerificationCommandPriority.UNBLOCK
            } -> VerificationCommandStatus.ACTIVE
            else -> VerificationCommandStatus.STABLE
        }
        val fingerprint = digest(
            listOf(
                VERSION,
                input.eventId,
                input.sport,
                (now / 60_000L).toString(),
                input.assessment.values.joinToString(","),
                input.timeline.checkedAt.joinToString(","),
                sourceIntegrity.fingerprint,
                counterView.fingerprint,
                decisionGuard?.plan?.seal.orEmpty(),
                decisionGuard?.status?.name.orEmpty(),
                tasks.joinToString(",") {
                    it.fingerprint
                }
            ).joinToString("|")
        )

        return VerificationCommandResult(
            input = input,
            sourceIntegrity = sourceIntegrity,
            freshness = freshness,
            evidenceResult = evidenceResult,
            counterView = counterView,
            marketLens = marketLens,
            route = route,
            decisionGuard = decisionGuard,
            tasks = tasks,
            status = status,
            fingerprint = fingerprint
        )
    }

    private fun MutableList<TaskDraft>.addGuardTasks(
        guard: DecisionGuardResult?
    ) {
        if (guard?.status != DecisionGuardStatus.TRIGGERED) return
        val causes = guard.causes.ifEmpty {
            guard.effectiveCauses
        }
        add(
            TaskDraft(
                priority = VerificationCommandPriority.STOP,
                kind = VerificationCommandKind.GUARD_BREACH,
                factor = guard.plan.condition?.factor,
                title = "Остановить сохранённое решение",
                reason = causes.joinToString(" ") {
                    guardCauseExplanation(it)
                }.ifBlank {
                    "Стоп-контракт нарушен; решение нельзя считать действующим."
                },
                modules = buildList {
                    add(VerificationCommandModule.GUARD)
                    if (
                        DecisionGuardCause.COUNTERVIEW_LIMIT in causes
                    ) {
                        add(VerificationCommandModule.COUNTERVIEW)
                    }
                    if (
                        DecisionGuardCause.EVIDENCE_LOSS in causes
                    ) {
                        add(VerificationCommandModule.FRESHNESS)
                    }
                },
                dueAt = guard.plan.condition?.evidenceValidUntil,
                readinessImpact = Int.MAX_VALUE
            )
        )
    }

    private fun MutableList<TaskDraft>.addSourceTasks(
        sourceIntegrity: SourceIntegrityResult
    ) {
        sourceIntegrity.factors.forEach { item ->
            when {
                item.auditState == SourceAuditState.CONFLICT -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.STOP,
                        kind = VerificationCommandKind.SOURCE_CONFLICT,
                        factor = item.factor,
                        title =
                            "Разобрать расхождение: ${item.factor.shortTitle}",
                        reason =
                            "Источники противоречат друг другу; подтверждение фактора сброшено до «не подтверждено».",
                        modules = listOf(
                            VerificationCommandModule.SOURCES,
                            VerificationCommandModule.ROUTE,
                            VerificationCommandModule.MARKETS
                        )
                    )
                )
                item.isQuorumClaim &&
                    item.auditState ==
                    SourceAuditState.SHARED_LINEAGE -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.REPAIR,
                        kind = VerificationCommandKind.SOURCE_ECHO,
                        factor = item.factor,
                        title =
                            "Развести цепочки: ${item.factor.shortTitle}",
                        reason =
                            "Заявленный кворум оказался эхом одного первичного материала и ограничен одним источником.",
                        modules = listOf(
                            VerificationCommandModule.SOURCES,
                            VerificationCommandModule.ROUTE,
                            VerificationCommandModule.MARKETS
                        )
                    )
                )
                item.isQuorumClaim &&
                    item.auditState ==
                    SourceAuditState.UNAUDITED -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.REPAIR,
                        kind = VerificationCommandKind.SOURCE_AUDIT,
                        factor = item.factor,
                        title =
                            "Проверить кворум: ${item.factor.shortTitle}",
                        reason =
                            "Независимость заявленных подтверждений ещё не доказана; уровень временно ограничен одним источником.",
                        modules = listOf(
                            VerificationCommandModule.SOURCES,
                            VerificationCommandModule.ROUTE,
                            VerificationCommandModule.MARKETS
                        )
                    )
                )
            }
        }
    }

    private fun MutableList<TaskDraft>.addFreshnessTasks(
        freshness: FreshnessResult
    ) {
        freshness.factors.forEach { item ->
            when (item.status) {
                FreshnessStatus.EXPIRED,
                FreshnessStatus.DEGRADED -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.REFRESH,
                        kind = VerificationCommandKind.EVIDENCE_EXPIRED,
                        factor = item.factor,
                        title =
                            "Обновить данные: ${item.factor.shortTitle}",
                        reason = if (
                            item.status == FreshnessStatus.EXPIRED
                        ) {
                            "Срок подтверждения истёк; фактор больше не поддерживает сигнал."
                        } else {
                            "Подтверждение потеряло один уровень из-за возраста данных."
                        },
                        modules = listOf(
                            VerificationCommandModule.FRESHNESS,
                            VerificationCommandModule.ROUTE,
                            VerificationCommandModule.MARKETS
                        )
                    )
                )
                FreshnessStatus.EXPIRING -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.REFRESH,
                        kind = VerificationCommandKind.EVIDENCE_EXPIRING,
                        factor = item.factor,
                        title =
                            "Освежить до снижения: ${item.factor.shortTitle}",
                        reason =
                            "Текущий уровень подтверждения скоро снизится автоматически.",
                        modules = listOf(
                            VerificationCommandModule.FRESHNESS
                        ),
                        dueAt = item.nextTransitionAt
                    )
                )
                FreshnessStatus.FRESH,
                FreshnessStatus.UNCONFIRMED -> Unit
            }
        }
    }

    private fun MutableList<TaskDraft>.addCounterTasks(
        counterView: CounterViewResult
    ) {
        counterView.factors.forEach { item ->
            when (item.reviewState) {
                CounterReviewState.REFUTED -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.STOP,
                        kind = VerificationCommandKind.COUNTERFACT,
                        factor = item.factor,
                        title =
                            "Проверить контрфакт: ${item.factor.shortTitle}",
                        reason =
                            "Найден факт против исходной версии; допустимое решение ограничено уровнем «Пропустить».",
                        modules = listOf(
                            VerificationCommandModule.COUNTERVIEW,
                            VerificationCommandModule.GUARD
                        ),
                        readinessImpact = item.readinessImpact
                    )
                )
                CounterReviewState.MIXED -> add(
                    TaskDraft(
                        priority = VerificationCommandPriority.CHALLENGE,
                        kind = VerificationCommandKind.COUNTER_DISPUTE,
                        factor = item.factor,
                        title =
                            "Разрешить спор: ${item.factor.shortTitle}",
                        reason =
                            "Независимые факты допускают разные трактовки; сильное решение пока недоступно.",
                        modules = listOf(
                            VerificationCommandModule.COUNTERVIEW,
                            VerificationCommandModule.GUARD
                        ),
                        readinessImpact = item.readinessImpact
                    )
                )
                CounterReviewState.UNCHECKED,
                CounterReviewState.CLEAR -> Unit
            }
        }
        counterView.nextFactor?.let { factor ->
            val item = counterView.factor(factor)
            add(
                TaskDraft(
                    priority = VerificationCommandPriority.CHALLENGE,
                    kind = VerificationCommandKind.COUNTER_OPEN,
                    factor = factor,
                    title =
                        "Искать опровержение: ${factor.shortTitle}",
                    reason =
                        "Контрпроверка ещё не выполнена; выбран фактор с наибольшим влиянием на полноту.",
                    modules = listOf(
                        VerificationCommandModule.COUNTERVIEW,
                        VerificationCommandModule.GUARD
                    ),
                    readinessImpact = item.readinessImpact
                )
            )
        }
    }

    private fun MutableList<TaskDraft>.addMarketTasks(
        marketLens: MarketLensResult
    ) {
        val candidates = marketLens.items.mapNotNull { item ->
            val nextCheck = item.nextCheck
                ?: return@mapNotNull null
            if (
                nextCheck.reason ==
                MarketNextCheckReason.MAINTENANCE
            ) {
                return@mapNotNull null
            }
            val priority = if (
                nextCheck.reason ==
                MarketNextCheckReason.FRESHNESS
            ) {
                VerificationCommandPriority.REFRESH
            } else {
                VerificationCommandPriority.UNBLOCK
            }
            MarketTaskCandidate(
                item = item,
                nextCheck = nextCheck,
                priority = priority,
                kind = if (
                    priority ==
                    VerificationCommandPriority.REFRESH
                ) {
                    VerificationCommandKind.MARKET_FRESHNESS
                } else {
                    VerificationCommandKind.MARKET_CHECK
                },
                dueAt = if (
                    nextCheck.reason ==
                    MarketNextCheckReason.FRESHNESS
                ) {
                    item.factor(nextCheck.factor)
                        .freshness.nextTransitionAt
                } else {
                    null
                }
            )
        }
        candidates.groupBy { candidate ->
            MarketTaskKey(
                priority = candidate.priority,
                kind = candidate.kind,
                factor = candidate.nextCheck.factor
            )
        }.values.forEach { group ->
            val primary = group.first()
            val factor = primary.nextCheck.factor
            add(
                TaskDraft(
                    priority = primary.priority,
                    kind = primary.kind,
                    factor = factor,
                    title = when (primary.priority) {
                        VerificationCommandPriority.REFRESH ->
                            "Удержать рынки: ${factor.shortTitle}"
                        else ->
                            "Разблокировать: ${factor.shortTitle}"
                    },
                    reason = marketGroupReason(group),
                    modules = listOf(
                        VerificationCommandModule.MARKETS
                    ),
                    dueAt = group.mapNotNull(
                        MarketTaskCandidate::dueAt
                    ).minOrNull()
                )
            )
        }
    }

    private fun MutableList<TaskDraft>.addRouteTasks(
        route: VerificationRoute,
        freshness: FreshnessResult
    ) {
        when (route.status) {
            VerificationRouteStatus.REACHABLE,
            VerificationRouteStatus.FACTS_LIMIT -> {
                route.bestCheck?.let { step ->
                    add(
                        TaskDraft(
                            priority =
                                VerificationCommandPriority.UNBLOCK,
                            kind =
                                VerificationCommandKind.ROUTE_CHECK,
                            factor = step.factor,
                            title =
                                "Следующий факт: ${step.factor.shortTitle}",
                            reason = if (step.readinessGain > 0) {
                                "Свежий независимый кворум даст +${step.readinessGain} к полноте по открытому маршруту."
                            } else {
                                "Это лучший доступный шаг проверки, хотя одних источников недостаточно для следующего вердикта."
                            },
                            modules = listOf(
                                VerificationCommandModule.ROUTE
                            ),
                            readinessImpact = step.readinessGain
                        )
                    )
                }
            }
            VerificationRouteStatus.READY_MAINTAIN -> Unit
        }
        freshness.nextTransitionFactor?.let { factor ->
            add(
                TaskDraft(
                    priority = VerificationCommandPriority.MAINTAIN,
                    kind = VerificationCommandKind.MAINTENANCE,
                    factor = factor,
                    title =
                        "Следить за сроком: ${factor.shortTitle}",
                    reason =
                        "Это ближайший автоматический переход уровня подтверждения.",
                    modules = listOf(
                        VerificationCommandModule.FRESHNESS,
                        VerificationCommandModule.ROUTE
                    ),
                    dueAt = freshness.nextTransitionAt
                )
            )
        }
    }

    private fun mergeAndSort(
        drafts: List<TaskDraft>
    ): List<VerificationCommandTask> {
        return drafts.groupBy {
            TaskKey(it.priority, it.factor)
        }.values.map { group ->
            val ordered = group.sortedBy { it.kind.ordinal }
            val primary = ordered.first()
            val kinds = ordered.map(TaskDraft::kind).distinct()
            val modules = ordered.flatMap(TaskDraft::modules)
                .distinct()
                .sortedBy(VerificationCommandModule::ordinal)
            val reason = ordered.map(TaskDraft::reason)
                .distinct()
                .joinToString(" ")
            val dueAt = ordered.mapNotNull(TaskDraft::dueAt)
                .minOrNull()
            val readinessImpact = ordered.maxOf {
                it.readinessImpact
            }
            val fingerprint = digest(
                listOf(
                    primary.priority.name,
                    primary.factor?.name.orEmpty(),
                    kinds.joinToString(",") { it.name },
                    modules.joinToString(",") { it.name },
                    dueAt?.toString().orEmpty(),
                    readinessImpact.toString(),
                    primary.title,
                    reason
                ).joinToString("|")
            )
            VerificationCommandTask(
                priority = primary.priority,
                kinds = kinds,
                factor = primary.factor,
                title = primary.title,
                reason = reason,
                modules = modules,
                dueAt = dueAt,
                readinessImpact = readinessImpact,
                fingerprint = fingerprint
            )
        }.sortedWith(
            compareBy<VerificationCommandTask> {
                it.priority.ordinal
            }.thenBy {
                it.dueAt ?: Long.MAX_VALUE
            }.thenBy {
                it.kinds.first().ordinal
            }.thenByDescending {
                it.readinessImpact
            }.thenBy {
                it.factor?.ordinal ?: Int.MAX_VALUE
            }
        )
    }

    private fun guardCauseExplanation(
        cause: DecisionGuardCause
    ): String {
        return when (cause) {
            DecisionGuardCause.DECISION_ABOVE_SIGNAL ->
                "Сохранённое решение было выше исходного сигнала."
            DecisionGuardCause.SIGNAL_BELOW_CONTRACT ->
                "Текущий сигнал ниже уровня сохранённого решения."
            DecisionGuardCause.FACTOR_FLOOR ->
                "Критический фактор пересёк нижнюю границу."
            DecisionGuardCause.EVIDENCE_LOSS ->
                "Критическое подтверждение потеряло требуемый уровень."
            DecisionGuardCause.COUNTERVIEW_LIMIT ->
                "Контрпроверка ограничила допустимое решение."
        }
    }

    private fun marketGroupReason(
        group: List<MarketTaskCandidate>
    ): String {
        val reasons = group.map {
            it.nextCheck.reason
        }.distinct()
        val examples = group.take(2).joinToString(", ") {
            "«${it.item.guide.title}»"
        } + if (group.size > 2) {
            " и ещё ${group.size - 2}"
        } else {
            ""
        }
        val cause = when {
            reasons.size > 1 ->
                "по фактору остаются открытые проверки"
            reasons.single() == MarketNextCheckReason.BLOCKER ->
                "нет подтверждения критического фактора"
            reasons.single() == MarketNextCheckReason.QUORUM ->
                "нужен свежий независимый кворум"
            reasons.single() == MarketNextCheckReason.EVIDENCE_GAP ->
                "нет проверяемого источника"
            reasons.single() == MarketNextCheckReason.FRESHNESS ->
                "сверка устареет при ближайшем переходе"
            else ->
                "требуется контроль срока"
        }
        return "Затронуто чек-листов: ${group.size}; $cause: $examples."
    }

    private fun digest(payload: String): String {
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

    private data class TaskDraft(
        val priority: VerificationCommandPriority,
        val kind: VerificationCommandKind,
        val factor: SignalFactor?,
        val title: String,
        val reason: String,
        val modules: List<VerificationCommandModule>,
        val dueAt: Long? = null,
        val readinessImpact: Int = 0
    )

    private data class TaskKey(
        val priority: VerificationCommandPriority,
        val factor: SignalFactor?
    )

    private data class MarketTaskCandidate(
        val item: MarketLensItem,
        val nextCheck: MarketNextCheck,
        val priority: VerificationCommandPriority,
        val kind: VerificationCommandKind,
        val dueAt: Long?
    )

    private data class MarketTaskKey(
        val priority: VerificationCommandPriority,
        val kind: VerificationCommandKind,
        val factor: SignalFactor
    )
}
