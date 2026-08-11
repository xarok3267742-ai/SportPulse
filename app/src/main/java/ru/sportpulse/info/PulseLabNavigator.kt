package ru.sportpulse.info

internal enum class PulseLabSection(
    val title: String
) {
    ROUTE("Маршрут"),
    FACTS("Факты"),
    DECISION("Решение");

    companion object {
        fun fromStored(value: String?): PulseLabSection {
            return value?.let {
                runCatching { valueOf(it) }.getOrNull()
            } ?: ROUTE
        }
    }
}

internal data class PulseLabNavigatorInput(
    val confirmedFactorCount: Int,
    val independentlyVerifiedCount: Int,
    val totalFactorCount: Int,
    val hasDecisionSnapshot: Boolean,
    val eventStarted: Boolean,
    val reviewFinalized: Boolean
) {
    init {
        require(totalFactorCount > 0)
        require(confirmedFactorCount in 0..totalFactorCount)
        require(independentlyVerifiedCount in 0..confirmedFactorCount)
        require(!reviewFinalized || hasDecisionSnapshot)
    }
}

internal data class PulseLabSectionStatus(
    val section: PulseLabSection,
    val status: String
) {
    init {
        require(status.isNotBlank())
    }
}

internal data class PulseLabNavigatorSummary(
    val recommendedSection: PulseLabSection,
    val badge: String,
    val headline: String,
    val body: String,
    val sectionStatuses: List<PulseLabSectionStatus>
) {
    init {
        require(badge.isNotBlank())
        require(headline.isNotBlank())
        require(body.isNotBlank())
        require(sectionStatuses.map { it.section } == PulseLabSection.values().toList())
    }

    fun status(section: PulseLabSection): String {
        return sectionStatuses.first { it.section == section }.status
    }
}

internal object PulseLabNavigatorEngine {
    fun evaluate(input: PulseLabNavigatorInput): PulseLabNavigatorSummary {
        val recommended = when {
            input.reviewFinalized -> PulseLabSection.ROUTE
            input.eventStarted -> PulseLabSection.DECISION
            input.independentlyVerifiedCount < input.totalFactorCount ->
                PulseLabSection.FACTS
            !input.hasDecisionSnapshot -> PulseLabSection.DECISION
            else -> PulseLabSection.ROUTE
        }
        val headline = when (recommended) {
            PulseLabSection.FACTS ->
                "Сначала закройте доказательный пробел"
            PulseLabSection.DECISION -> when {
                input.eventStarted && input.hasDecisionSnapshot ->
                    "Событие началось: проверьте исходные факты"
                input.eventStarted ->
                    "Событие началось без зафиксированного решения"
                else ->
                    "Факты сверены: зафиксируйте решение"
            }
            PulseLabSection.ROUTE -> when {
                input.reviewFinalized ->
                    "Цикл проверки завершён"
                else ->
                    "Следите за изменениями до старта"
            }
        }
        val body = when (recommended) {
            PulseLabSection.FACTS ->
                "Независимо сверено ${input.independentlyVerifiedCount} из " +
                    "${input.totalFactorCount}. Раздел «Факты» соберёт " +
                    "источники, свежесть и контрпроверку в одном месте."
            PulseLabSection.DECISION -> when {
                input.eventStarted && input.hasDecisionSnapshot ->
                    "Откройте «Решение» и сравните исходную запись с тем, " +
                        "что стало известно после старта."
                input.eventStarted ->
                    "Не восстанавливайте уверенность задним числом. " +
                        "В «Решении» приложение явно покажет отсутствие записи."
                else ->
                    "Откройте «Решение» и сохраните вывод до начала события. " +
                        "Это не прогноз и не рекомендация ставки."
            }
            PulseLabSection.ROUTE -> when {
                input.reviewFinalized ->
                    "Вернитесь в «Маршрут», чтобы увидеть итог цикла и " +
                        "ближайшее доказуемое действие."
                else ->
                    "Решение уже зафиксировано. В «Маршруте» проверяйте " +
                        "только новые сигналы и сроки обновления."
            }
        }
        val decisionStatus = when {
            input.reviewFinalized -> "РАЗБОР ГОТОВ"
            input.hasDecisionSnapshot -> "РЕШЕНИЕ ЕСТЬ"
            input.eventStarted -> "НЕТ ЗАПИСИ"
            else -> "НЕ СОЗДАНО"
        }
        return PulseLabNavigatorSummary(
            recommendedSection = recommended,
            badge = "СЛЕДУЮЩИЙ РАЗДЕЛ • ${recommended.title.uppercase()}",
            headline = headline,
            body = body,
            sectionStatuses = listOf(
                PulseLabSectionStatus(
                    PulseLabSection.ROUTE,
                    "ОБЗОР СЮЖЕТА"
                ),
                PulseLabSectionStatus(
                    PulseLabSection.FACTS,
                    "СВЕРЕНО ${input.independentlyVerifiedCount} ИЗ " +
                        input.totalFactorCount
                ),
                PulseLabSectionStatus(
                    PulseLabSection.DECISION,
                    decisionStatus
                )
            )
        )
    }
}
