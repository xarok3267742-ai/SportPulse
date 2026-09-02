package ru.sportpulse.info

internal enum class ScenarioForkState(
    val badgeTitle: String
) {
    PRIMARY_REQUIRED("НУЖНА ВЕРСИЯ A"),
    ALTERNATIVE_REQUIRED("НУЖНА ВЕРСИЯ B"),
    STOP_REQUIRED("НУЖНА СТОП-ЛИНИЯ"),
    EVIDENCE_REQUIRED("НУЖЕН ФАКТ"),
    OPEN("РАЗВИЛКА ОТКРЫТА"),
    BLOCKED("СТОП"),
    VERIFIED("РАЗВИЛКА ПРОЙДЕНА")
}

internal data class ScenarioForkResult(
    val state: ScenarioForkState,
    val headline: String,
    val explanation: String,
    val primaryScenario: String,
    val alternativeScenario: String,
    val stopCondition: String,
    val targetField: DecisionDeskField?,
    val distinguishingFactor: SignalFactor?
)

internal object ScenarioForkEngine {
    fun evaluate(
        draft: DecisionDeskDraft,
        decision: DecisionDeskResult
    ): ScenarioForkResult {
        val targetField = draft.missingFields.firstOrNull()
        val state = when (targetField) {
            DecisionDeskField.THESIS ->
                ScenarioForkState.PRIMARY_REQUIRED
            DecisionDeskField.COUNTERARGUMENT ->
                ScenarioForkState.ALTERNATIVE_REQUIRED
            DecisionDeskField.STOP_CONDITION ->
                ScenarioForkState.STOP_REQUIRED
            null -> when {
                decision.status == DecisionDeskStatus.FACTS_READY ->
                    ScenarioForkState.VERIFIED
                decision.status == DecisionDeskStatus.STOP ->
                    ScenarioForkState.BLOCKED
                decision.nextFactor != null ->
                    ScenarioForkState.EVIDENCE_REQUIRED
                else -> ScenarioForkState.OPEN
            }
        }
        val copy = copyFor(
            state = state,
            targetField = targetField,
            factor = decision.nextFactor
        )
        return ScenarioForkResult(
            state = state,
            headline = copy.first,
            explanation = copy.second,
            primaryScenario = draft.thesis.ifBlank {
                "Сценарий A пока не сформулирован"
            },
            alternativeScenario = draft.counterargument.ifBlank {
                "Сценарий B пока не сформулирован"
            },
            stopCondition = draft.stopCondition.ifBlank {
                "Наблюдаемая стоп-линия пока не задана"
            },
            targetField = targetField,
            distinguishingFactor = if (
                state == ScenarioForkState.EVIDENCE_REQUIRED
            ) {
                decision.nextFactor
            } else {
                null
            }
        )
    }

    private fun copyFor(
        state: ScenarioForkState,
        targetField: DecisionDeskField?,
        factor: SignalFactor?
    ): Pair<String, String> {
        return when (state) {
            ScenarioForkState.PRIMARY_REQUIRED ->
                "Запишите исходную идею" to
                    "Одним предложением опишите, чего ожидаете от матча."
            ScenarioForkState.ALTERNATIVE_REQUIRED ->
                "Найдите сильное возражение" to
                    "Укажите наблюдаемый факт, который действительно может опровергнуть идею."
            ScenarioForkState.STOP_REQUIRED ->
                "Задайте условие отказа" to
                    "Заранее укажите факт, после которого вы откажетесь от исходной идеи."
            ScenarioForkState.EVIDENCE_REQUIRED ->
                "Найдите различающий факт" to
                    if (factor == null) {
                        "Проверьте факт, который разделит две версии."
                    } else {
                        "Сейчас две версии лучше всего различит фактор «${factor.title}»."
                    }
            ScenarioForkState.OPEN ->
                "Обе версии остаются в игре" to
                    "Не выбирайте удобную историю, пока факты не разделят сценарии."
            ScenarioForkState.BLOCKED ->
                "Развилка остановлена" to
                    "Текущая проверка не допускает повышения статуса."
            ScenarioForkState.VERIFIED ->
                "Развилка выдержала проверку" to
                    "Версии и стоп-линия зафиксированы, критические факты сверены. Это не прогноз исхода."
        }.also {
            require(
                targetField != null ||
                    state !in setOf(
                        ScenarioForkState.PRIMARY_REQUIRED,
                        ScenarioForkState.ALTERNATIVE_REQUIRED,
                        ScenarioForkState.STOP_REQUIRED
                    )
            )
        }
    }
}
