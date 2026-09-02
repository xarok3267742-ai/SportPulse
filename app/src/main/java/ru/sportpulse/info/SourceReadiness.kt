package ru.sportpulse.info

internal enum class SourceReadinessLevel {
    READY,
    VERIFY,
    STOP,
    DEMO
}

internal enum class SourceReadinessMode {
    SIGNED_PACKAGE,
    DEVELOPMENT_PACKAGE,
    LOCAL_PACKAGE,
    ONLINE,
    REFRESHING,
    EXPIRED_PACKAGE,
    EMPTY_ONLINE_WINDOW,
    OFFLINE,
    DEMO
}

internal data class SourceReadinessCheck(
    val title: String,
    val value: String,
    val detail: String
) {
    init {
        require(title.isNotBlank())
        require(value.isNotBlank())
        require(detail.isNotBlank())
    }
}

internal data class SourceReadinessResult(
    val level: SourceReadinessLevel,
    val mode: SourceReadinessMode,
    val badge: String,
    val verdict: String,
    val summary: String,
    val checks: List<SourceReadinessCheck>
) {
    init {
        require(badge.isNotBlank())
        require(verdict.isNotBlank())
        require(summary.isNotBlank())
        require(checks.size == 3)
        require(checks.map(SourceReadinessCheck::title).distinct().size == 3)
    }
}

internal object SourceReadinessEngine {
    fun evaluate(
        now: Long,
        eventPackage: SportEventPackage?,
        apiFeed: ApiFootballFeed?,
        apiActive: Boolean,
        apiConfigured: Boolean,
        refreshing: Boolean,
        apiError: String?
    ): SourceReadinessResult {
        require(now >= 0L)
        val activePackage = eventPackage?.takeUnless { it.isExpired(now) }
        if (activePackage != null) {
            return packageResult(activePackage)
        }
        if (apiActive && apiFeed != null) {
            return onlineResult(
                feed = apiFeed,
                now = now,
                updateFailed = apiError != null
            )
        }
        if (apiActive) {
            return if (refreshing) {
                refreshingResult()
            } else {
                offlineResult()
            }
        }
        return when {
            refreshing -> refreshingResult()
            eventPackage != null -> expiredPackageResult()
            apiError != null -> offlineResult()
            apiFeed != null -> emptyOnlineWindowResult(apiFeed, now)
            !apiConfigured -> demoResult(
                summary = "Онлайн-каталог не подключён в этой сборке."
            )
            else -> demoResult(
                summary = "Онлайн-каталог ещё не загружен."
            )
        }
    }

    private fun packageResult(
        eventPackage: SportEventPackage
    ): SourceReadinessResult {
        val environment = eventPackage.authenticity.keyEnvironment
        return when (environment) {
            EventPackageKeyEnvironment.PRODUCTION -> result(
                level = SourceReadinessLevel.READY,
                mode = SourceReadinessMode.SIGNED_PACKAGE,
                badge = "ИСТОЧНИК ПОДТВЕРЖДЁН",
                verdict = "Можно начинать разбор",
                summary = "Автор файла, целостность и срок действия проверены.",
                sourceValue = "Подписанный пакет",
                sourceDetail =
                    "Подпись подтверждает автора и неизменность файла.",
                freshnessValue = "Действует сейчас",
                freshnessDetail = "Срок пакета ещё не истёк."
            )
            EventPackageKeyEnvironment.DEVELOPMENT -> result(
                level = SourceReadinessLevel.VERIFY,
                mode = SourceReadinessMode.DEVELOPMENT_PACKAGE,
                badge = "ТЕСТОВЫЙ ИСТОЧНИК",
                verdict = "Сначала проверьте источник",
                summary = "Файл подписан тестовым, а не рабочим ключом.",
                sourceValue = "Тестовая подпись",
                sourceDetail =
                    "Целостность подтверждена только для разработки.",
                freshnessValue = "Действует сейчас",
                freshnessDetail = "Срок пакета ещё не истёк."
            )
            null -> result(
                level = SourceReadinessLevel.VERIFY,
                mode = SourceReadinessMode.LOCAL_PACKAGE,
                badge = "НУЖНА СВЕРКА",
                verdict = "Автор пакета не подтверждён",
                summary = "Структура файла проверена, но подписи источника нет.",
                sourceValue = "Локальный файл",
                sourceDetail = "Автор и происхождение файла не подтверждены.",
                freshnessValue = "Действует сейчас",
                freshnessDetail = "Срок пакета ещё не истёк."
            )
        }
    }

    private fun onlineResult(
        feed: ApiFootballFeed,
        now: Long,
        updateFailed: Boolean
    ): SourceReadinessResult {
        val freshness = ScheduleFreshnessPolicy.evaluate(
            syncedAt = feed.fetchedAt,
            now = now
        )
        return when (freshness.status) {
            ScheduleFreshnessStatus.FRESH -> result(
                level = SourceReadinessLevel.READY,
                mode = SourceReadinessMode.ONLINE,
                badge = "РАСПИСАНИЕ СВЕЖЕЕ",
                verdict = "Можно начинать разбор",
                summary = if (updateFailed) {
                    "Сохранённый снимок ещё свежий, но повторное обновление не удалось."
                } else {
                    "Расписание и статусы получены недавно."
                },
                sourceValue = "Защищённый канал",
                sourceDetail =
                    "Сервер передал расписание и статус по HTTPS.",
                freshnessValue = "Свежесть в норме",
                freshnessDetail = "Снимок моложе 6 часов."
            )
            ScheduleFreshnessStatus.VERIFY -> result(
                level = SourceReadinessLevel.VERIFY,
                mode = SourceReadinessMode.ONLINE,
                badge = "НУЖНА СВЕРКА",
                verdict = "Перепроверьте расписание",
                summary = if (updateFailed) {
                    "Снимку больше 6 часов, а повторное обновление не удалось."
                } else {
                    "После последнего обновления прошло больше 6 часов."
                },
                sourceValue = "Защищённый канал",
                sourceDetail =
                    "Сервер передал расписание и статус по HTTPS.",
                freshnessValue = "Свежесть снижается",
                freshnessDetail = "Обновите данные перед разбором."
            )
            ScheduleFreshnessStatus.STALE -> result(
                level = SourceReadinessLevel.STOP,
                mode = SourceReadinessMode.ONLINE,
                badge = "СНАЧАЛА ОБНОВИТЕ",
                verdict = "Расписание устарело",
                summary = if (updateFailed) {
                    "Снимку больше суток, а повторное обновление не удалось."
                } else {
                    "Снимку больше суток; начинать разбор рано."
                },
                sourceValue = "Защищённый канал",
                sourceDetail =
                    "Соединение подтверждено, но данные уже старые.",
                freshnessValue = "Срок превышен",
                freshnessDetail = "Сначала получите новый снимок."
            )
            ScheduleFreshnessStatus.INVALID -> result(
                level = SourceReadinessLevel.STOP,
                mode = SourceReadinessMode.ONLINE,
                badge = "ВРЕМЯ НЕВЕРНО",
                verdict = "Нельзя оценить свежесть",
                summary = "Время обновления находится в будущем.",
                sourceValue = "Защищённый канал",
                sourceDetail =
                    "Источник доступен, но метка времени некорректна.",
                freshnessValue = "Проверка не пройдена",
                freshnessDetail = "Сверьте часы устройства и обновите данные."
            )
        }
    }

    private fun refreshingResult(): SourceReadinessResult = result(
        level = SourceReadinessLevel.VERIFY,
        mode = SourceReadinessMode.REFRESHING,
        badge = "ИДЁТ ОБНОВЛЕНИЕ",
        verdict = "Дождитесь свежего снимка",
        summary = "Получаем расписание России и СНГ.",
        sourceValue = "Защищённый канал",
        sourceDetail = "Запрос выполняется через сервер приложения.",
        freshnessValue = "Проверяется",
        freshnessDetail = "Вердикт появится после загрузки."
    )

    private fun expiredPackageResult(): SourceReadinessResult = result(
        level = SourceReadinessLevel.STOP,
        mode = SourceReadinessMode.EXPIRED_PACKAGE,
        badge = "ПАКЕТ ОТКЛЮЧЁН",
        verdict = "Срок данных истёк",
        summary = "Истёкший пакет не участвует в текущем каталоге.",
        sourceValue = "Локальный пакет",
        sourceDetail = "Файл сохранён только для контроля истории.",
        freshnessValue = "Срок истёк",
        freshnessDetail = "Удалите пакет или импортируйте новую версию."
    )

    private fun emptyOnlineWindowResult(
        feed: ApiFootballFeed,
        now: Long
    ): SourceReadinessResult {
        val freshness = ScheduleFreshnessPolicy.evaluate(
            syncedAt = feed.fetchedAt,
            now = now
        )
        return result(
            level = when (freshness.status) {
                ScheduleFreshnessStatus.FRESH,
                ScheduleFreshnessStatus.VERIFY ->
                    SourceReadinessLevel.VERIFY
                ScheduleFreshnessStatus.STALE,
                ScheduleFreshnessStatus.INVALID ->
                    SourceReadinessLevel.STOP
            },
            mode = SourceReadinessMode.EMPTY_ONLINE_WINDOW,
            badge = "В ОКНЕ НЕТ СОБЫТИЙ",
            verdict = "Показан учебный каталог",
            summary = "Источник обновился, но не вернул матчи выбранного региона.",
            sourceValue = "Защищённый канал",
            sourceDetail = "Ответ получен без подходящих событий.",
            freshnessValue = when (freshness.status) {
                ScheduleFreshnessStatus.FRESH -> "Снимок свежий"
                ScheduleFreshnessStatus.VERIFY -> "Нужна сверка"
                ScheduleFreshnessStatus.STALE -> "Снимок старый"
                ScheduleFreshnessStatus.INVALID -> "Время неверно"
            },
            freshnessDetail = "Учебные события не заменяют актуальное расписание."
        )
    }

    private fun offlineResult(): SourceReadinessResult = result(
        level = SourceReadinessLevel.STOP,
        mode = SourceReadinessMode.OFFLINE,
        badge = "ОНЛАЙН НЕДОСТУПЕН",
        verdict = "Показан учебный каталог",
        summary = "Последнее обновление не удалось.",
        sourceValue = "Нет свежего ответа",
        sourceDetail = "Проверьте сеть и повторите обновление.",
        freshnessValue = "Не подтверждена",
        freshnessDetail = "Текущие матчи не загружены."
    )

    private fun demoResult(summary: String): SourceReadinessResult = result(
        level = SourceReadinessLevel.DEMO,
        mode = SourceReadinessMode.DEMO,
        badge = "УЧЕБНЫЙ РЕЖИМ",
        verdict = "Не используйте как расписание",
        summary = summary,
        sourceValue = "Демонстрационные данные",
        sourceDetail = "События нужны только для знакомства с интерфейсом.",
        freshnessValue = "Не применяется",
        freshnessDetail = "Демо-каталог не отражает текущие матчи."
    )

    private fun result(
        level: SourceReadinessLevel,
        mode: SourceReadinessMode,
        badge: String,
        verdict: String,
        summary: String,
        sourceValue: String,
        sourceDetail: String,
        freshnessValue: String,
        freshnessDetail: String
    ): SourceReadinessResult {
        return SourceReadinessResult(
            level = level,
            mode = mode,
            badge = badge,
            verdict = verdict,
            summary = summary,
            checks = listOf(
                SourceReadinessCheck(
                    title = "Источник",
                    value = sourceValue,
                    detail = sourceDetail
                ),
                SourceReadinessCheck(
                    title = "Свежесть",
                    value = freshnessValue,
                    detail = freshnessDetail
                ),
                SourceReadinessCheck(
                    title = "Граница вывода",
                    value = "Не является прогнозом",
                    detail =
                        "Даже подтверждённый источник не доказывает исход или выгодность ставки."
                )
            )
        )
    }
}
