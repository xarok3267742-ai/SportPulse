package ru.sportpulse.info

internal enum class GuideNavigatorStage {
    EVENT,
    PLAN,
    FACT,
    DECISION,
    REVIEW,
    COMPLETE
}

internal data class GuideNavigatorInput(
    val hasSelectedEvent: Boolean,
    val missingPlanFields: List<DecisionDeskField>,
    val recordedFactCount: Int,
    val hasDecision: Boolean,
    val reviewFinalized: Boolean,
    val nextFactor: SignalFactor
) {
    init {
        require(
            missingPlanFields.distinct().size ==
                missingPlanFields.size
        )
        require(
            missingPlanFields.all {
                it in DecisionDeskField.values()
            }
        )
        require(recordedFactCount in 0..SignalFactor.values().size)
        require(!hasDecision || hasSelectedEvent)
        require(!reviewFinalized || hasDecision)
    }
}

internal data class GuideNavigatorMission(
    val stage: GuideNavigatorStage,
    val completedSteps: Int,
    val badge: String,
    val title: String,
    val explanation: String,
    val actionTitle: String,
    val planField: DecisionDeskField? = null,
    val factor: SignalFactor? = null
) {
    init {
        require(completedSteps in 0..TOTAL_STEPS)
        require(badge.isNotBlank())
        require(title.isNotBlank())
        require(explanation.isNotBlank())
        require(actionTitle.isNotBlank())
        require((stage == GuideNavigatorStage.PLAN) == (planField != null))
        require((stage == GuideNavigatorStage.FACT) == (factor != null))
    }

    val progressDescription: String
        get() = "Пройдено $completedSteps из $TOTAL_STEPS шагов"

    companion object {
        const val TOTAL_STEPS = 5
    }
}

internal object GuideNavigatorEngine {
    fun evaluate(input: GuideNavigatorInput): GuideNavigatorMission {
        if (!input.hasSelectedEvent) {
            return mission(
                stage = GuideNavigatorStage.EVENT,
                completedSteps = 0,
                title = "Сначала выберите событие",
                explanation =
                    "Нужен конкретный матч, чтобы все факты, решения и разборы относились к одному контексту.",
                actionTitle = "Открыть Матчи"
            )
        }
        if (input.reviewFinalized) {
            return GuideNavigatorMission(
                stage = GuideNavigatorStage.COMPLETE,
                completedSteps = GuideNavigatorMission.TOTAL_STEPS,
                badge = "ЦИКЛ ЗАМКНУТ",
                title = "Разбор связан с решением",
                explanation =
                    "Цикл завершён. Посмотрите, какой навык проверки стоит повторить в следующем событии.",
                actionTitle = "Открыть Профиль проверки"
            )
        }
        if (input.hasDecision) {
            return mission(
                stage = GuideNavigatorStage.REVIEW,
                completedSteps = 4,
                title = "Вернитесь после события",
                explanation =
                    "Предстартовый снимок сохранён. После завершения события сравните с ним те же пять факторов, а не счёт или память о решении.",
                actionTitle = "Открыть «После свистка»"
            )
        }
        val planField = input.missingPlanFields.firstOrNull()
        if (planField != null) {
            val completed = DecisionDeskField.values().size -
                input.missingPlanFields.size
            return mission(
                stage = GuideNavigatorStage.PLAN,
                completedSteps = 1,
                title = "Соберите план из трёх вопросов",
                explanation =
                    "Готово $completed из 3. Следующий вопрос — ${planField.title}.",
                actionTitle = planField.actionTitle,
                planField = planField
            )
        }
        if (input.recordedFactCount == 0) {
            return mission(
                stage = GuideNavigatorStage.FACT,
                completedSteps = 2,
                title = "Зафиксируйте один проверяемый факт",
                explanation =
                    "План готов. Начните с фактора «${input.nextFactor.title}»: запишите тезис и первичный источник.",
                actionTitle = "Открыть «${input.nextFactor.title}»",
                factor = input.nextFactor
            )
        }
        return mission(
            stage = GuideNavigatorStage.DECISION,
            completedSteps = 3,
            title = "Зафиксируйте решение до матча",
            explanation =
                "Факт записан. Выберите «Пропустить», «Наблюдать» или «Факты сверены», затем отдельно подтвердите запись.",
            actionTitle = "Перейти к решению"
        )
    }

    private fun mission(
        stage: GuideNavigatorStage,
        completedSteps: Int,
        title: String,
        explanation: String,
        actionTitle: String,
        planField: DecisionDeskField? = null,
        factor: SignalFactor? = null
    ): GuideNavigatorMission {
        return GuideNavigatorMission(
            stage = stage,
            completedSteps = completedSteps,
            badge = "ШАГ ${completedSteps + 1} ИЗ ${GuideNavigatorMission.TOTAL_STEPS}",
            title = title,
            explanation = explanation,
            actionTitle = actionTitle,
            planField = planField,
            factor = factor
        )
    }
}
