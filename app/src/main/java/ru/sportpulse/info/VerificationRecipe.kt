package ru.sportpulse.info

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class VerificationRecipeStep(
    val title: String,
    val body: String
) {
    init {
        require(title.isNotBlank())
        require(body.isNotBlank())
    }
}

internal data class VerificationRecipe(
    val factor: SignalFactor,
    val evidenceLevel: EvidenceLevel,
    val question: String,
    val steps: List<VerificationRecipeStep>,
    val completionRule: String,
    val fingerprint: String
) {
    init {
        require(question.isNotBlank())
        require(steps.size == VerificationRecipePolicy.STEP_COUNT)
        require(steps.map(VerificationRecipeStep::title).distinct().size == steps.size)
        require(completionRule.isNotBlank())
        require(fingerprint.length == 64)
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()
}

internal object VerificationRecipePolicy {
    const val STEP_COUNT = 3
}

internal object VerificationRecipeEngine {
    private const val VERSION = "sport-pulse-verification-recipe-v1"
    private val hex = "0123456789abcdef".toCharArray()

    fun create(
        factor: SignalFactor,
        evidenceLevel: EvidenceLevel
    ): VerificationRecipe {
        val question = question(factor)
        val steps = listOf(
            VerificationRecipeStep(
                title = "Первичный факт",
                body = primaryFact(factor)
            ),
            VerificationRecipeStep(
                title = "Независимая сверка",
                body = independentCheck(factor)
            ),
            VerificationRecipeStep(
                title = "Стоп-правило",
                body = stopRule(factor)
            )
        )
        val completionRule = completionRule(evidenceLevel)
        val fingerprint = digest(
            buildString {
                append(VERSION)
                append('|')
                append(factor.name)
                append('|')
                append(evidenceLevel.name)
                append('|')
                append(question)
                steps.forEach { step ->
                    append('|')
                    append(step.title)
                    append(':')
                    append(step.body)
                }
                append('|')
                append(completionRule)
            }
        )
        return VerificationRecipe(
            factor = factor,
            evidenceLevel = evidenceLevel,
            question = question,
            steps = steps,
            completionRule = completionRule,
            fingerprint = fingerprint
        )
    }

    private fun question(factor: SignalFactor): String {
        return when (factor) {
            SignalFactor.FORM ->
                "Сохраняется ли текущая форма против сопоставимых соперников?"
            SignalFactor.LINEUP ->
                "Кто действительно доступен и кто может начать матч?"
            SignalFactor.LOAD ->
                "Есть ли подтверждённый дефицит отдыха, перелёт или перегрузка?"
            SignalFactor.CONTEXT ->
                "Какие условия турнира и места реально меняют контекст матча?"
            SignalFactor.SOURCES ->
                "Сколько независимых первоисточников стоит за ключевыми фактами?"
        }
    }

    private fun primaryFact(factor: SignalFactor): String {
        return when (factor) {
            SignalFactor.FORM ->
                "Откройте официальные протоколы последних матчей: даты, соперники, составы и формат турнира."
            SignalFactor.LINEUP ->
                "Начните с официальной заявки, дисквалификаций и сообщений клуба или лиги с точным временем публикации."
            SignalFactor.LOAD ->
                "Соберите официальный календарь, интервалы отдыха, города матчей и подтверждённые перелёты."
            SignalFactor.CONTEXT ->
                "Проверьте регламент, таблицу, место, покрытие и официальное время начала события."
            SignalFactor.SOURCES ->
                "Проследите каждый важный тезис до самой ранней оригинальной публикации, а не до пересказа."
        }
    }

    private fun independentCheck(factor: SignalFactor): String {
        return when (factor) {
            SignalFactor.FORM ->
                "Сверьте ту же серию в независимой базе и найдите матч, который противоречит общей картине."
            SignalFactor.LINEUP ->
                "Сопоставьте официальную информацию с независимым репортёром или базой травм; ищите расхождения."
            SignalFactor.LOAD ->
                "Пересчитайте интервалы по датам и часовым поясам во втором источнике; не полагайтесь на готовую подпись «усталость»."
            SignalFactor.CONTEXT ->
                "Сверьте условия с независимыми данными о погоде, арене или турнирной ситуации и найдите контраргумент."
            SignalFactor.SOURCES ->
                "Сравните автора, время, цитаты и ссылки: одинаковый текст или общий первоисточник не образуют кворум."
        }
    }

    private fun stopRule(factor: SignalFactor): String {
        return when (factor) {
            SignalFactor.FORM ->
                "Не переносите общую серию на этот матч, если уровень соперников, состав или формат заметно отличаются."
            SignalFactor.LINEUP ->
                "Не считайте слух подтверждением и не приравнивайте заявку к стартовому составу."
            SignalFactor.LOAD ->
                "Не выводите усталость только из расстояния или числа матчей без подтверждённых дат и маршрута."
            SignalFactor.CONTEXT ->
                "Не превращайте мотивацию, турнирную вывеску или мнение эксперта в проверенный факт."
            SignalFactor.SOURCES ->
                "Не считайте репосты и публикации с одной информационной цепочкой независимыми источниками."
        }
    }

    private fun completionRule(
        evidenceLevel: EvidenceLevel
    ): String {
        return when (evidenceLevel) {
            EvidenceLevel.UNCONFIRMED ->
                "После первого подтверждённого факта отметьте «один источник»; до этого вывод остаётся неподтверждённым."
            EvidenceLevel.SINGLE_SOURCE ->
                "Повышайте до «2+» только после второго источника с независимым происхождением."
            EvidenceLevel.QUORUM ->
                "Обновляйте время проверки только после повторной сверки свежести и расхождений обоих источников."
        }
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
}
