package ru.sportpulse.info

internal enum class DecisionReceiptStatus {
    CHOICE_REQUIRED,
    READY_SKIP,
    READY_OBSERVE,
    READY_DATA,
    COUNTERVIEW_LIMIT,
    DISTANCE_REQUIRED,
    ATTENTION_EXHAUSTED,
    LEDGER_TAMPERED,
    WINDOW_CLOSED,
    REVIEW_FINALIZED
}

internal enum class DecisionReceiptAction {
    NONE,
    COMMIT,
    OPEN_DISTANCE,
    SHOW_ATTENTION,
    SHOW_LEDGER
}

internal data class DecisionReceiptComposerResult(
    val status: DecisionReceiptStatus,
    val badge: String,
    val headline: String,
    val body: String,
    val actionTitle: String,
    val action: DecisionReceiptAction
) {
    val canAct: Boolean
        get() = action != DecisionReceiptAction.NONE
}

internal object DecisionReceiptComposer {
    fun evaluate(
        selectedDecision: SavedDecision?,
        reviewFinalized: Boolean,
        decisionWindowOpen: Boolean,
        ledgerIntegrity: DecisionLedgerIntegrity,
        decisionCeiling: SavedDecision,
        attentionBudgetStatus: AttentionBudgetStatus,
        distanceClearanceValid: Boolean
    ): DecisionReceiptComposerResult {
        if (reviewFinalized) {
            return result(
                status = DecisionReceiptStatus.REVIEW_FINALIZED,
                badge = "ЗАКРЫТО • РЕТРОСПЕКТИВА",
                headline = "Решение уже завершено",
                body = "После финального разбора новый предстартовый снимок не создаётся.",
                actionTitle = "Запись закрыта"
            )
        }
        if (!decisionWindowOpen) {
            return result(
                status = DecisionReceiptStatus.WINDOW_CLOSED,
                badge = "ЗАКРЫТО • СТАРТ НАСТУПИЛ",
                headline = "Предстартовое окно закрыто",
                body = "Создать решение задним числом нельзя. Перейдите к разбору фактов после события.",
                actionTitle = "Предстартовая запись закрыта"
            )
        }
        if (ledgerIntegrity == DecisionLedgerIntegrity.TAMPERED) {
            return result(
                status = DecisionReceiptStatus.LEDGER_TAMPERED,
                badge = "СТОП • ЦЕПОЧКА НАРУШЕНА",
                headline = "Сначала проверьте журнал",
                body = "Новая запись не добавляется поверх повреждённой цепочки решений.",
                actionTitle = "Проверить журнал",
                action = DecisionReceiptAction.SHOW_LEDGER
            )
        }
        if (selectedDecision == null) {
            return result(
                status = DecisionReceiptStatus.CHOICE_REQUIRED,
                badge = "ШАГ 1 ИЗ 2",
                headline = "Выберите честный итог",
                body = "Выбор только подготовит квитанцию. Запись появится после отдельной команды фиксации.",
                actionTitle = "Выберите итог"
            )
        }
        if (selectedDecision.ordinal > decisionCeiling.ordinal) {
            return result(
                status = DecisionReceiptStatus.COUNTERVIEW_LIMIT,
                badge = "ОГРАНИЧЕНИЕ • КОНТРРАКУРС",
                headline = "Итог выше допустимого",
                body = "Сейчас можно зафиксировать максимум «${title(decisionCeiling)}». Выберите этот или более осторожный итог.",
                actionTitle = "Выберите допустимый итог"
            )
        }
        if (
            selectedDecision == SavedDecision.DATA_READY &&
            attentionBudgetStatus == AttentionBudgetStatus.EXHAUSTED
        ) {
            return result(
                status = DecisionReceiptStatus.ATTENTION_EXHAUSTED,
                badge = "ПАУЗА • ЛИМИТ ВНИМАНИЯ",
                headline = "Сильный вывод сегодня закрыт",
                body = "«Пропустить» и «Наблюдать» остаются доступны. Лимит нельзя обойти новой записью.",
                actionTitle = "Проверить бюджет внимания",
                action = DecisionReceiptAction.SHOW_ATTENTION
            )
        }
        if (
            selectedDecision == SavedDecision.DATA_READY &&
            !distanceClearanceValid
        ) {
            return result(
                status = DecisionReceiptStatus.DISTANCE_REQUIRED,
                badge = "ШАГ 2 ИЗ 2 • НУЖНА ПАУЗА",
                headline = "Пройдите Контур дистанции",
                body = "Перед самым сильным итогом нужна свежая самопроверка. Она не требуется для двух безопасных выходов.",
                actionTitle = "Пройти Контур дистанции",
                action = DecisionReceiptAction.OPEN_DISTANCE
            )
        }
        return when (selectedDecision) {
            SavedDecision.SKIP -> result(
                status = DecisionReceiptStatus.READY_SKIP,
                badge = "ШАГ 2 ИЗ 2 • БЕЗ ВЫВОДА",
                headline = "Готово: пропустить",
                body = "Снимок зафиксирует, что данных недостаточно или сработало стоп-условие.",
                actionTitle = "Зафиксировать: Пропустить",
                action = DecisionReceiptAction.COMMIT
            )
            SavedDecision.OBSERVE -> result(
                status = DecisionReceiptStatus.READY_OBSERVE,
                badge = "ШАГ 2 ИЗ 2 • НАБЛЮДЕНИЕ",
                headline = "Готово: наблюдать",
                body = "Версия останется открытой, но приложение не назовёт факты достаточными.",
                actionTitle = "Зафиксировать: Наблюдать",
                action = DecisionReceiptAction.COMMIT
            )
            SavedDecision.DATA_READY -> result(
                status = DecisionReceiptStatus.READY_DATA,
                badge = "ШАГ 2 ИЗ 2 • ФАКТЫ СВЕРЕНЫ",
                headline = "Готово: факты сверены",
                body = if (
                    attentionBudgetStatus == AttentionBudgetStatus.WARNING
                ) {
                    "Все проверки допускают сильный итог. Бюджет внимания близок к лимиту; это по-прежнему не прогноз исхода."
                } else {
                    "Все проверки допускают сильный итог. Это оценка процесса, а не прогноз исхода или совет сделать ставку."
                },
                actionTitle = "Зафиксировать: Факты сверены",
                action = DecisionReceiptAction.COMMIT
            )
        }
    }

    private fun result(
        status: DecisionReceiptStatus,
        badge: String,
        headline: String,
        body: String,
        actionTitle: String,
        action: DecisionReceiptAction = DecisionReceiptAction.NONE
    ): DecisionReceiptComposerResult {
        return DecisionReceiptComposerResult(
            status = status,
            badge = badge,
            headline = headline,
            body = body,
            actionTitle = actionTitle,
            action = action
        )
    }

    private fun title(decision: SavedDecision): String {
        return when (decision) {
            SavedDecision.SKIP -> "пропустить"
            SavedDecision.OBSERVE -> "наблюдать"
            SavedDecision.DATA_READY -> "факты сверены"
        }
    }
}
