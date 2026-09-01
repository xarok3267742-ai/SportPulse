package ru.sportpulse.info

internal enum class FactReceiptComposerStatus(
    val badge: String,
    val headline: String,
    val body: String
) {
    STATEMENT_REQUIRED(
        badge = "ШАГ 1 ИЗ 2",
        headline = "Запишите проверяемый факт",
        body = "Сформулируйте одно наблюдаемое утверждение минимум из 8 символов."
    ),
    PRIMARY_SOURCE_REQUIRED(
        badge = "ШАГ 2 ИЗ 2",
        headline = "Укажите происхождение",
        body = "Добавьте название или ссылку на первичную публикацию."
    ),
    SINGLE_SOURCE_READY(
        badge = "МОЖНО СОХРАНИТЬ",
        headline = "Готово: один источник",
        body = "Запись будет учтена как один источник. Для кворума включите независимую сверку."
    ),
    SECONDARY_SOURCE_REQUIRED(
        badge = "СВЕРКА • 1 ИЗ 2",
        headline = "Добавьте второй источник",
        body = "Укажите второе происхождение или отключите независимую сверку."
    ),
    SOURCE_RELATION_REQUIRED(
        badge = "СВЕРКА • НУЖЕН ВЫБОР",
        headline = "Проверьте связь источников",
        body = "Сохранить можно, но без проверки связи приложение учтёт только один источник."
    ),
    SHARED_LINEAGE(
        badge = "СВЕРКА • ОДНА ЦЕПОЧКА",
        headline = "Два упоминания, одно происхождение",
        body = "Обе записи ведут к одной первичной цепочке, поэтому действует уровень одного источника."
    ),
    INDEPENDENT_QUORUM(
        badge = "СВЕРКА • КВОРУМ 2+",
        headline = "Независимая пара готова",
        body = "Разные первичные цепочки могут участвовать в кворуме, пока обе проверки свежие."
    ),
    CONFLICT(
        badge = "СТОП • РАСХОЖДЕНИЕ",
        headline = "Источники противоречат друг другу",
        body = "Сохраните расхождение как стоп-сигнал или вернитесь к проверке тезиса."
    )
}

internal data class FactReceiptComposerResult(
    val status: FactReceiptComposerStatus,
    val canSave: Boolean,
    val effectiveAudit: SourceAuditState
)

internal object FactReceiptComposer {
    fun evaluate(
        statement: String,
        primarySource: String,
        includeSecondSource: Boolean,
        secondarySource: String,
        selectedAudit: SourceAuditState
    ): FactReceiptComposerResult {
        val cleanStatement = statement.trim()
        val cleanPrimary = primarySource.trim()
        val cleanSecondary = secondarySource.trim()
        val status = when {
            cleanStatement.length <
                FactReceiptPolicy.MIN_STATEMENT_LENGTH ->
                FactReceiptComposerStatus.STATEMENT_REQUIRED
            cleanPrimary.length <
                FactReceiptPolicy.MIN_SOURCE_LENGTH ->
                FactReceiptComposerStatus.PRIMARY_SOURCE_REQUIRED
            !includeSecondSource ->
                FactReceiptComposerStatus.SINGLE_SOURCE_READY
            cleanSecondary.length <
                FactReceiptPolicy.MIN_SOURCE_LENGTH ->
                FactReceiptComposerStatus.SECONDARY_SOURCE_REQUIRED
            FactReceiptFactory.sourceIdentity(cleanPrimary) ==
                FactReceiptFactory.sourceIdentity(cleanSecondary) ->
                FactReceiptComposerStatus.SHARED_LINEAGE
            selectedAudit == SourceAuditState.UNAUDITED ->
                FactReceiptComposerStatus.SOURCE_RELATION_REQUIRED
            selectedAudit == SourceAuditState.SHARED_LINEAGE ->
                FactReceiptComposerStatus.SHARED_LINEAGE
            selectedAudit == SourceAuditState.INDEPENDENT ->
                FactReceiptComposerStatus.INDEPENDENT_QUORUM
            else -> FactReceiptComposerStatus.CONFLICT
        }
        val effectiveAudit = when (status) {
            FactReceiptComposerStatus.SINGLE_SOURCE_READY,
            FactReceiptComposerStatus.STATEMENT_REQUIRED,
            FactReceiptComposerStatus.PRIMARY_SOURCE_REQUIRED,
            FactReceiptComposerStatus.SECONDARY_SOURCE_REQUIRED,
            FactReceiptComposerStatus.SOURCE_RELATION_REQUIRED ->
                SourceAuditState.UNAUDITED
            FactReceiptComposerStatus.SHARED_LINEAGE ->
                SourceAuditState.SHARED_LINEAGE
            FactReceiptComposerStatus.INDEPENDENT_QUORUM ->
                SourceAuditState.INDEPENDENT
            FactReceiptComposerStatus.CONFLICT ->
                SourceAuditState.CONFLICT
        }
        return FactReceiptComposerResult(
            status = status,
            canSave = status !=
                FactReceiptComposerStatus.STATEMENT_REQUIRED &&
                status !=
                FactReceiptComposerStatus.PRIMARY_SOURCE_REQUIRED &&
                status !=
                FactReceiptComposerStatus.SECONDARY_SOURCE_REQUIRED,
            effectiveAudit = effectiveAudit
        )
    }
}
