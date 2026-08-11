package ru.sportpulse.info

import java.time.DayOfWeek

internal object DemoCatalog {
    val events = listOf(
        SportEvent(
            id = "rpl_zenit_krasnodar",
            sport = "Футбол",
            tournament = "Мир РПЛ",
            region = "Россия",
            match = "Зенит - Краснодар",
            time = "Демо · ср, 19:30 МСК",
            focus = "Темп, угловые, ротация состава",
            note = "Сверьте стартовые составы, график выездов и свежие новости по травмам.",
            tags = listOf("топ-матч", "домашняя серия"),
            imageRes = R.drawable.event_football,
            seedAssessment = assessment(62, 38, 58, 70, 46),
            demoSchedule = DemoSchedule(
                DayOfWeek.WEDNESDAY,
                19,
                30
            )
        ),
        SportEvent(
            id = "khl_ska_dinamo_minsk",
            sport = "Хоккей",
            tournament = "КХЛ",
            region = "Россия / Беларусь",
            match = "СКА - Динамо Минск",
            time = "Демо · чт, 20:00 МСК",
            focus = "Тотал шайб, большинство, вратари",
            note = "Для хоккея критичны спецбригады и подтверждение основного вратаря в день матча.",
            tags = listOf("КХЛ", "составы"),
            imageRes = R.drawable.event_hockey,
            seedAssessment = assessment(54, 28, 66, 57, 41),
            demoSchedule = DemoSchedule(
                DayOfWeek.THURSDAY,
                20,
                0
            )
        ),
        SportEvent(
            id = "kpl_astana_kairat",
            sport = "Футбол",
            tournament = "Qazaqstan Premier League",
            region = "Казахстан",
            match = "Астана - Кайрат",
            time = "Демо · сб, 16:00 МСК",
            focus = "Исход, фора, календарь",
            note = "Учитывайте перелеты, тип покрытия и плотность календаря при сравнении формы.",
            tags = listOf("дерби", "СНГ"),
            imageRes = R.drawable.event_football,
            seedAssessment = assessment(68, 51, 43, 74, 56),
            demoSchedule = DemoSchedule(
                DayOfWeek.SATURDAY,
                16,
                0
            )
        ),
        SportEvent(
            id = "blr_dinamo_neman",
            sport = "Футбол",
            tournament = "Высшая лига",
            region = "Беларусь",
            match = "Динамо Минск - Неман",
            time = "Демо · вс, 18:30 МСК",
            focus = "Индивидуальный тотал, первый тайм",
            note = "Разделяйте домашние и гостевые показатели, а не ориентируйтесь только на общую таблицу.",
            tags = listOf("форма", "голы"),
            imageRes = R.drawable.event_football,
            seedAssessment = assessment(58, 47, 61, 63, 52),
            demoSchedule = DemoSchedule(
                DayOfWeek.SUNDAY,
                18,
                30
            )
        ),
        SportEvent(
            id = "vtb_cska_unics",
            sport = "Баскетбол",
            tournament = "Единая лига ВТБ",
            region = "Россия / СНГ",
            match = "ЦСКА - УНИКС",
            time = "Демо · пт, 19:00 МСК",
            focus = "Тотал очков, фора, темп владений",
            note = "Сравните темп владений, глубину ротации и долю дальних бросков.",
            tags = listOf("темп", "плей-офф"),
            imageRes = R.drawable.hero_sport_pulse,
            seedAssessment = assessment(72, 63, 60, 68, 59),
            demoSchedule = DemoSchedule(
                DayOfWeek.FRIDAY,
                19,
                0
            )
        ),
        SportEvent(
            id = "cis_esports_week",
            sport = "Киберспорт",
            tournament = "CS2 / Dota 2",
            region = "СНГ",
            match = "Команды региона в международной сетке",
            time = "Демо · по расписанию серии",
            focus = "Карты, пики, замены, формат серии",
            note = "Для BO1 и BO3 нужны разные допуски риска. Проверьте маппул, замены и актуальный патч.",
            tags = listOf("киберспорт", "патч"),
            imageRes = R.drawable.pulse_workspace,
            seedAssessment = assessment(64, 34, 52, 46, 38)
        ),
        SportEvent(
            id = "mma_main_card",
            sport = "ММА",
            tournament = "Лиги России и СНГ",
            region = "Россия / Центральная Азия",
            match = "Главный кард недели",
            time = "Демо · сб, 22:00 МСК",
            focus = "Стиль боя, дистанция, весогонка",
            note = "Не оценивайте бой только по рекорду: важны уровень оппозиции, простой и поздние замены.",
            tags = listOf("единоборства", "риск"),
            imageRes = R.drawable.hero_sport_pulse,
            seedAssessment = assessment(45, 24, 31, 55, 33),
            demoSchedule = DemoSchedule(
                DayOfWeek.SATURDAY,
                22,
                0
            )
        ),
        SportEvent(
            id = "tennis_region_draw",
            sport = "Теннис",
            tournament = "ATP / WTA",
            region = "Россия / Казахстан / Беларусь",
            match = "Игроки региона в основной сетке",
            time = "Демо · по расписанию турнира",
            focus = "Покрытие, удержание подачи, усталость",
            note = "Сравните показатели на конкретном покрытии и нагрузку после предыдущих матчей.",
            tags = listOf("покрытие", "форма"),
            imageRes = R.drawable.hero_sport_pulse,
            seedAssessment = assessment(66, 71, 42, 62, 60)
        )
    )

    val markets = listOf(
        MarketGuide(
            kind = MarketKind.ONE_X_TWO,
            title = "Исход 1X2",
            summary = "Победа первой команды, ничья или победа второй.",
            check = "Форма против сопоставимых соперников, состав и мотивация.",
            stopSignal = "Неясен формат учета овертайма или нет подтвержденного состава."
        ),
        MarketGuide(
            kind = MarketKind.HANDICAP,
            title = "Фора",
            summary = "Преимущество или отставание, добавленное к итоговому счету.",
            check = "Разница в классе, темп матча и устойчивость команды в концовках.",
            stopSignal = "Вывод держится только на названии фаворита."
        ),
        MarketGuide(
            kind = MarketKind.TOTAL,
            title = "Тотал",
            summary = "Количество голов, шайб, очков, карт или других событий.",
            check = "Темп, стиль, судейство, погода и важность матча.",
            stopSignal = "Среднее посчитано без учета дома, выезда или уровня соперников."
        ),
        MarketGuide(
            kind = MarketKind.BOTH_SCORE,
            title = "Обе забьют",
            summary = "Обе команды должны забить хотя бы один гол.",
            check = "Качество моментов, травмы в обороне и игровые сценарии.",
            stopSignal = "Вывод основан только на последних счетах."
        ),
        MarketGuide(
            kind = MarketKind.INDIVIDUAL_TOTAL,
            title = "Индивидуальный тотал",
            summary = "Результат одной команды или спортсмена.",
            check = "Стилистическое преимущество и роль конкретного участника.",
            stopSignal = "Ключевой игрок или стартовый состав пока не подтвержден."
        ),
        MarketGuide(
            kind = MarketKind.PERIOD,
            title = "Период или тайм",
            summary = "Оценка отдельного отрезка матча.",
            check = "Стартовые сочетания, привычный темп начала и качество скамейки.",
            stopSignal = "Вывод переносит статистику полного матча на короткий отрезок."
        )
    )

    val guideSteps = listOf(
        "Сверить официальный календарь и точное время начала в выбранной зоне.",
        "Проверить травмы, дисквалификации, ротацию и стартовый состав.",
        "Сравнить форму против соперников похожего уровня.",
        "Разделить домашние и гостевые показатели.",
        "Учесть мотивацию, перелеты и плотность календаря.",
        "Записать причину выбора и причину, по которой событие стоит пропустить.",
        "После события проверить пять исходных факторов без оглядки на счет."
    )

    fun event(id: String?): SportEvent {
        return events.firstOrNull { it.id == id } ?: events.first()
    }

    private fun assessment(
        form: Int,
        lineup: Int,
        load: Int,
        context: Int,
        sources: Int
    ): SignalAssessment {
        return SignalAssessment(listOf(form, lineup, load, context, sources))
    }
}
