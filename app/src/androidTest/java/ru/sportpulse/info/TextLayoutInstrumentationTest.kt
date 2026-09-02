package ru.sportpulse.info

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SearchView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextLayoutInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        instrumentation.targetContext
            .getSharedPreferences(
                "sport_pulse_state",
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .putBoolean("product_tour_seen_v1", true)
            .putBoolean("age_confirmed_v1", true)
            .putString("selected_event", EVENT_ID)
            .putStringSet(
                "bookmarks",
                setOf(
                    "rpl_zenit_krasnodar",
                    "khl_ska_dinamo_minsk",
                    "kpl_astana_kairat",
                    "vtb_cska_unics"
                )
            )
            .commit()
        UserStateStore(instrumentation.targetContext).saveFactReceipt(
            FactReceiptFactory.create(
                eventId = EVENT_ID,
                factor = SignalFactor.FORM,
                statement =
                    "Подтверждены результаты сопоставимых матчей с длинным описанием выборки, домашнего поля и временной границы анализа.",
                primarySource =
                    "https://historical-club-source.example/${"timelinepoint".repeat(7)}",
                secondarySource =
                    "https://independent-form-source.example/${"comparison".repeat(7)}",
                sourceAuditState = SourceAuditState.INDEPENDENT,
                coverage = FactReceiptCoverage.DETAILS,
                checkedAt = System.currentTimeMillis() - 20L *
                    FreshnessPolicy.HOUR_MILLIS
            )
        )
        UserStateStore(instrumentation.targetContext).saveFactReceipt(
            FactReceiptFactory.create(
                eventId = EVENT_ID,
                factor = SignalFactor.SOURCES,
                statement =
                    "Подтверждена версия регламента серии с длинным описанием условий замены игрока, выбора карт и переноса начала матча после независимой сверки документов.",
                primarySource =
                    "https://official-club-source.example/${"verylongsegment".repeat(7)}",
                secondarySource =
                    "https://independent-league-source.example/${"matchdocument".repeat(7)}",
                sourceAuditState = SourceAuditState.INDEPENDENT,
                coverage = FactReceiptCoverage.COUNTERCHECKED,
                checkedAt = System.currentTimeMillis()
            )
        )
        UserStateStore(instrumentation.targetContext).saveFactReceipt(
            FactReceiptFactory.create(
                eventId = EVENT_ID,
                factor = SignalFactor.CONTEXT,
                statement =
                    "Подтверждены формат серии, правила переноса и условия замены участника по двум опубликованным документам.",
                primarySource =
                    "https://official-club-source.example/${"anothersegment".repeat(7)}",
                secondarySource =
                    "https://independent-regulation.example/${"rulebookitem".repeat(7)}",
                sourceAuditState = SourceAuditState.INDEPENDENT,
                coverage = FactReceiptCoverage.DETAILS,
                checkedAt = System.currentTimeMillis() - 2L *
                    FreshnessPolicy.validForMillis(
                        SignalFactor.CONTEXT
                    )
            )
        )
        seedApiUpdateHistory()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun bundledTypographyRolesRenderCyrillicText() {
        scenario.onActivity { activity ->
            val heading = findTextView(activity, "Матч-день") { true }
            val body = findTextView(
                activity,
                "Россия и СНГ • следующий шаг проверки."
            ) { true }

            assertEquals(
                AppTypography.display(activity, bold = true),
                heading.typeface
            )
            assertEquals(AppTypography.body(activity), body.typeface)
            assertNotEquals(heading.typeface, body.typeface)

            listOf(heading.typeface, body.typeface).forEach { typeface ->
                val paint = Paint().apply { this.typeface = typeface }
                assertTrue(paint.hasGlyph("Ж"))
                assertTrue(paint.hasGlyph("ё"))
            }
        }
    }

    @Test
    fun primaryScreensDoNotClipText() {
        val failures = mutableListOf<String>()

        assertTextPresent("КОГДА • МОСКВА")
        assertTextPresent("Ближайшие матчи")
        var firstEventViewport = false
        var firstActionViewport = false
        scenario.onActivity { activity ->
            val configuration = activity.resources.configuration
            firstEventViewport =
                configuration.fontScale < 1.3f &&
                configuration.screenHeightDp >= 720
            firstActionViewport =
                firstEventViewport &&
                configuration.screenWidthDp >= 390 &&
                configuration.screenHeightDp >= 840
        }
        if (firstEventViewport) {
            assertTextVisibleOnScreen(
                "Академия спортивных технологий 9101 - " +
                    "Объединённая молодёжная команда 9101",
                "на первом экране матч-центра"
            )
        }
        if (firstActionViewport) {
            assertTextCompletelyVisibleOnScreen(
                "Открыть анализ ›",
                "на первом экране матч-центра"
            )
        }
        audit("Матчи / Матч-центр", failures)
        clickTextStartingWith("Проверить ")
        assertTextPresent("СОБЫТИЯ • 1")
        assertTextPresent("ПОЧЕМУ ЗДЕСЬ • ПЕРЕНОС")
        assertTextPresent(
            "Поставщик пометил событие как перенесённое. Сверьте новую дату в официальном источнике."
        )
        audit("Лента / Требуют проверки", failures)
        clickText("Открыть короткий разбор")
        assertTextPresent("Штаб решения")
        assertTextPresent("Глубина разбора")
        assertContentDescriptionStartingWith(
            "Два режима глубины: общий обзор и подробная проверка"
        )
        assertTextPresent("Карта данных")
        assertContentDescriptionStartingWith("Схема доказательств. ")
        assertTextAndDescriptionDoNotOverlap(
            text = "ОНЛАЙН • ДОСЬЕ",
            contentDescription = "Сменить событие анализа"
        )
        if (firstActionViewport) {
            assertTextVisibleOnScreen(
                "ОНЛАЙН • ДОСЬЕ",
                "на первом экране события"
            )
        }
        assertTextPresent("ПОЧЕМУ ЗДЕСЬ • ПЕРЕНОС")
        assertTextPresent("История и контроль")
        assertTextPresent("Открыть историю и контроль")
        assertTextPresent("Показать 3 шага проверки")
        assertTextAbsent("Первичный факт")
        assertTextAbsent("Что изменилось с прошлого визита")
        audit("Пульс / Причина переноса", failures)
        clickText("Показать 3 шага проверки")
        assertTextPresent("Свернуть шаги проверки")
        assertTextPresent("Первичный факт")
        audit("Пульс / Протокол раскрыт", failures)
        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextPresent("Свернуть шаги проверки")
        assertTextPresent("Первичный факт")
        clickText("Свернуть шаги проверки")
        assertTextPresent("Показать 3 шага проверки")
        assertTextAbsent("Первичный факт")
        clickText("Открыть историю и контроль")
        assertTextPresent("Свернуть историю и контроль")
        assertTextPresent("Что изменилось с прошлого визита")
        audit("Пульс / История раскрыта", failures)
        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextPresent("Свернуть историю и контроль")
        clickText("Свернуть историю и контроль")
        assertTextAbsent("Что изменилось с прошлого визита")
        clickText("Показать 3 шага проверки")
        assertTextPresent("Первичный факт")
        clickTab("Матчи")
        submitEventSearch("akademiya 9101")
        assertTextPresent("ПЕРИОД ПУСТ")
        assertTextPresent("Нет событий в этой группе")
        assertTextPresent("Показать все даты")
        audit("Лента / Пустая временная группа", failures)
        clickText("Показать все даты")
        assertTextPresent("НАЙДЕНО • 1 • ПОРЯДОК КАТАЛОГА")
        assertTextPresent("СОБЫТИЯ • 1")
        assertTextPresent("СОВПАДЕНИЕ • КОМАНДА")
        audit("Лента / Поиск латиницей", failures)
        clickText("Открыть короткий разбор")
        assertTextPresent("Штаб решения")
        assertTextPresent("Карта данных")
        assertTextPresent("Показать 3 шага проверки")
        assertTextAbsent("Первичный факт")
        clickTab("Матчи")
        submitEventSearch("несуществующая команда")
        assertTextPresent("ПОИСК ПУСТ")
        assertTextPresent("Ничего не найдено")
        audit("Лента / Пустой поиск", failures)
        clearEventSearch()
        clickText("Инструменты")
        assertTextPresent("Можно ли начинать разбор?")
        assertTextPresent("РАСПИСАНИЕ СВЕЖЕЕ")
        assertTextPresent("ГРАНИЦА ВЫВОДА")
        assertTextAbsent("API-Sports")
        assertTextAbsent("осталось запросов")
        clickText("Показать технические детали")
        assertTextStartingWithPresent("ОНЛАЙН-РАСПИСАНИЕ")
        assertTextContainingPresent(
            "Канал: HTTPS через сервер приложения"
        )
        assertTextAbsent("API-Sports")
        assertTextAbsent("осталось запросов")
        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextPresent("Скрыть технические детали")
        clickText("Скрыть технические детали")
        assertTextStartingWithAbsent("ОНЛАЙН-РАСПИСАНИЕ")
        submitEventSearch("akademiya 9101")
        assertTextPresent("СОВПАДЕНИЕ • КОМАНДА")
        audit("Лента / Инструменты / Поиск", failures)
        clearEventSearch()
        audit("Лента / Инструменты", failures)
        clickText("Показать ещё 1")
        audit("Лента / Пульс обновления", failures)
        clickTab("Штаб")
        assertTextPresent("Замысел ещё не проверяем")
        assertTextPresent("ЗАМЫСЕЛ • 0/3")
        assertTextPresent("Карта данных")
        assertTextAbsent("Что изменилось?")
        assertTextAbsent("Развилка матча")
        audit("Штаб / Краткое табло", failures)
        clickText("Сформулировать тезис")
        assertTextPresent("Рабочая форма")
        assertTextPresent("Что изменилось?")
        assertTextPresent("Развилка матча")
        assertContentDescriptionStartingWith(
            "Два равноправных сценария матча"
        )
        audit("Штаб / Рабочая форма", failures)
        clickContentDescription("Штаб: История")
        assertTextPresent("История процесса, а не выигрышей")
        audit("Штаб / История", failures)
        clickContentDescription("Штаб: Профиль")
        assertTextPresent("Профиль процесса")
        audit("Штаб / Профиль", failures)
        clickContentDescription("Штаб: Решение")
        assertTextPresent("КОРОТКО • ИТОГ И ОДИН ШАГ")
        audit("Пульс / Сюжет", failures)
        assertContextFixtureExpired()
        assertTextPresent("ИСТЁК СРОК")
        clickContentDescription("Как читать карту данных")
        assertTextPresent("Как читать карту данных")
        assertTextPresent(
            "Нет источника — тезис пока не подтверждён.\n\n" +
                "1 источник — факт записан, но независимой сверки нет.\n\n" +
                "2 источника — факт сверён по двум независимым цепочкам.\n\n" +
                "Истёк срок — старый факт больше не участвует в выводе.\n\n" +
                "Выделенная строка показывает только следующий шаг проверки. Статусы не являются вероятностью исхода или советом по ставке."
        )
        audit("Пульс / Справка карты данных", failures)
        clickText("Понятно")
        clickText("Открыть реестр фактов")
        assertTextPresent("СРОК ИСТЁК")
        assertTextPresent("ЕДИНЫЙ СРЕЗ • РАЗНЫЕ МОМЕНТЫ")
        audit("Пульс / Реестр фактов", failures)
        clickTextStartingWith("Продолжить маршрут:")
        audit("Пульс / Факт-маршрут", failures)
        clickText("К реестру")
        audit("Пульс / Реестр после возврата", failures)
        clickText("Закрыть")
        clickText("Записать факт и источники")
        assertTextPresent(
            "Ссылки не открываются автоматически. Приложение проверяет структуру записи, а не истинность публикации."
        )
        assertTextPresent("ШАГ 1 ИЗ 2")
        audit("Пульс / Факт-квитанция", failures)
        clickText("Отмена")
        clickText("Подробно")
        assertTextPresent("ПОДРОБНО • ПОЛНЫЙ АУДИТ ФАКТОВ")
        assertTextPresent("Навигатор подробного разбора")
        assertTextPresent("От источника до разбора")
        assertTextAbsent("Ручная оценка проверки")
        assertTextAbsent("Журнал решения")
        audit("Пульс / Подробно / Маршрут", failures)
        clickTextStartingWith("Факты\n")
        assertTextVisibleOnScreen(
            "Навигатор подробного разбора",
            "после открытия Фактов"
        )
        assertTextPresent("Ручная оценка проверки")
        assertTextAbsent("От источника до разбора")
        assertTextAbsent("Журнал решения")
        audit("Пульс / Подробно / Факты", failures)
        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextPresent("Ручная оценка проверки")
        assertTextAbsent("От источника до разбора")
        clickTextStartingWith("Решение\n")
        assertTextVisibleOnScreen(
            "Навигатор подробного разбора",
            "после открытия Решения"
        )
        assertTextPresent("Журнал решения")
        assertTextPresent("Выберите честный итог")
        assertTextPresent("Выберите итог")
        assertContentDescriptionStartingWith(
            "Три варианта решения и отдельный механизм фиксации"
        )
        assertLargeTextHeaderStacked(
            title = "После свистка",
            badge = "НУЖЕН СНИМОК"
        )
        assertTextAbsent("Ручная оценка проверки")
        assertTextAbsent("От источника до разбора")
        audit("Пульс / Подробно / Решение", failures)
        clickTextStartingWith("Маршрут\n")
        assertTextVisibleOnScreen(
            "Навигатор подробного разбора",
            "после возврата в Маршрут"
        )
        assertTextPresent("От источника до разбора")
        assertTextAbsent("Журнал решения")
        clickTab("Чек-листы")
        audit("Чек-листы", failures)
        clickTab("Гид")
        audit("Гид", failures)
        clickTab("18+")
        audit("18+", failures)

        assertTrue(
            failures.joinToString(
                separator = "\n",
                prefix = "Text layout failures:\n"
            ),
            failures.isEmpty()
        )
    }

    @Test
    fun globalNavigationStaysVisibleAfterDeepScrolling() {
        NAVIGATION_TITLES.forEach { title ->
            clickTab(title)
            scrollMainToBottom()
            assertNavigationDockVisible("Раздел $title")
        }
    }

    @Test
    fun adaptiveOptionGroupsKeepEveryChildInsideTheirWidth() {
        assertAdaptiveGroupContained(
            AdaptiveGroupTags.SPORT_FILTERS,
            expectedChildren = 2
        )
        assertAdaptiveGroupContained(
            AdaptiveGroupTags.TIME_FILTERS,
            expectedChildren = 2
        )
        clickText("Инструменты")
        assertAdaptiveGroupContained(
            AdaptiveGroupTags.EVENT_TAGS,
            expectedChildren = 1
        )

        clickTab("Штаб")
        clickText("Сформулировать тезис")
        assertAdaptiveGroupContained(
            AdaptiveGroupTags.DECISION_MARKETS,
            expectedChildren = MarketKind.entries.size
        )

        clickTab("Чек-листы")
        assertAdaptiveGroupContained(
            AdaptiveGroupTags.MARKET_TEMPLATES,
            expectedChildren = MarketKind.entries.size
        )
    }

    private fun clickTab(title: String) {
        scenario.onActivity { activity ->
            findTextView(activity, title) {
                it.contentDescription == "Раздел $title"
            }.performClick()
        }
        instrumentation.waitForIdleSync()
    }

    private fun scrollMainToBottom() {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<ScrollView>()
                .maxByOrNull { it.height }
                ?.fullScroll(View.FOCUS_DOWN)
                ?: error("Main scroll view not found")
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertNavigationDockVisible(screen: String) {
        scenario.onActivity { activity ->
            val roots = windowRoots(activity).toList()
            val views = roots.flatMap { descendants(it).toList() }
            val scroll = views
                .filterIsInstance<ScrollView>()
                .maxByOrNull { it.height }
                ?: error("Main scroll view not found")
            val scrollBounds = Rect()
            assertTrue(
                "$screen: content viewport is not visible",
                scroll.getGlobalVisibleRect(scrollBounds)
            )

            val tabBounds = mutableListOf<Rect>()
            NAVIGATION_TITLES.forEach { title ->
                val tab = views
                    .filterIsInstance<TextView>()
                    .firstOrNull {
                        it.contentDescription == "Раздел $title"
                    } ?: error("Navigation tab not found: $title")
                val visible = Rect()
                assertTrue(
                    "$screen: navigation tab is outside the window: $title",
                    tab.getGlobalVisibleRect(visible)
                )
                assertTrue(
                    "$screen: navigation tab is clipped: $title " +
                        "visible=${visible.width()}x${visible.height()} " +
                        "actual=${tab.width}x${tab.height}",
                    visible.width() >= tab.width - TOLERANCE_PX &&
                        visible.height() >= tab.height - TOLERANCE_PX
                )
                var parent = tab.parent
                while (parent is View) {
                    assertTrue(
                        "$screen: navigation tab is inside the scrolling content: $title",
                        parent !is ScrollView
                    )
                    parent = parent.parent
                }
                tabBounds += visible
            }
            assertTrue(
                "$screen: content viewport overlaps the navigation dock",
                scrollBounds.bottom <=
                    tabBounds.minOf { it.top } + TOLERANCE_PX
            )
        }
    }

    private fun assertAdaptiveGroupContained(
        groupTag: String,
        expectedChildren: Int
    ) {
        scenario.onActivity { activity ->
            val group = windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<AdaptiveWrapLayout>()
                .firstOrNull {
                    it.tag == groupTag
                }
                ?: error("Adaptive group not found: $groupTag")
            assertTrue(
                "$groupTag: expected at least $expectedChildren children, " +
                    "actual ${group.childCount}",
                group.childCount >= expectedChildren
            )
            repeat(group.childCount) { index ->
                val child = group.getChildAt(index)
                assertTrue(
                    "$groupTag: child $index has no measured size",
                    child.width > 0 && child.height > 0
                )
                assertTrue(
                    "$groupTag: child $index crosses the left edge",
                    child.left >= group.paddingLeft - TOLERANCE_PX
                )
                assertTrue(
                    "$groupTag: child $index crosses the right edge " +
                        "(${child.right}>${group.width - group.paddingRight})",
                    child.right <= group.width -
                        group.paddingRight + TOLERANCE_PX
                )
                assertTrue(
                    "$groupTag: child $index crosses the bottom edge " +
                        "(${child.bottom}>${group.height - group.paddingBottom})",
                    child.bottom <= group.height -
                        group.paddingBottom + TOLERANCE_PX
                )
            }
        }
    }

    private fun clickContentDescription(value: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .firstOrNull {
                    it.contentDescription?.toString() == value &&
                        it.isClickable
                }
                ?.performClick()
                ?: error("Clickable control not found: $value")
        }
        instrumentation.waitForIdleSync()
    }

    private fun submitEventSearch(query: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<SearchView>()
                .firstOrNull()
                ?.setQuery(query, true)
                ?: error("Event search not found")
        }
        instrumentation.waitForIdleSync()
    }

    private fun clearEventSearch() {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<SearchView>()
                .firstOrNull()
                ?.setQuery("", false)
                ?: error("Event search not found")
        }
        instrumentation.waitForIdleSync()
    }

    private fun clickText(title: String) {
        scenario.onActivity { activity ->
            findTextView(activity, title) { it.isClickable }
                .performClick()
        }
        instrumentation.waitForIdleSync()
    }

    private fun clickTextStartingWith(prefix: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString().startsWith(prefix) &&
                        it.isClickable
                }
                ?.performClick()
                ?: error("Text control not found by prefix: $prefix")
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertTextPresent(title: String) {
        scenario.onActivity { activity ->
            findTextView(activity, title) { true }
        }
    }

    private fun assertTextAbsent(title: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Unexpected text is visible: $title",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .none { it.text.toString() == title }
            )
        }
    }

    private fun assertTextStartingWithPresent(prefix: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Text not found by prefix: $prefix",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString().startsWith(prefix) }
            )
        }
    }

    private fun assertTextStartingWithAbsent(prefix: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Unexpected text is visible by prefix: $prefix",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .none { it.text.toString().startsWith(prefix) }
            )
        }
    }

    private fun assertTextContainingPresent(fragment: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Text not found by fragment: $fragment",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString().contains(fragment) }
            )
        }
    }

    private fun assertContentDescriptionStartingWith(prefix: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Content description not found by prefix: $prefix",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .any {
                        it.contentDescription?.toString()
                            ?.startsWith(prefix) == true
                    }
            )
        }
    }

    private fun assertTextAndDescriptionDoNotOverlap(
        text: String,
        contentDescription: String
    ) {
        scenario.onActivity { activity ->
            val textView = findTextView(activity, text) { true }
            val describedView = windowRoots(activity)
                .flatMap(::descendants)
                .firstOrNull {
                    it.contentDescription?.toString() ==
                        contentDescription
                }
                ?: error(
                    "Content description not found: $contentDescription"
                )
            val textBounds = Rect()
            val describedBounds = Rect()
            textView.getGlobalVisibleRect(textBounds)
            describedView.getGlobalVisibleRect(describedBounds)
            assertTrue(
                "Views overlap: $text and $contentDescription",
                !Rect.intersects(textBounds, describedBounds)
            )
        }
    }

    private fun assertTextVisibleOnScreen(
        title: String,
        context: String
    ) {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var visible = false
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val view = findTextView(activity, title) { true }
                val visibleBounds = Rect()
                visible = view.isShown &&
                    view.getGlobalVisibleRect(visibleBounds) &&
                    visibleBounds.width() > 0 &&
                    visibleBounds.height() > 0
            }
            if (!visible) Thread.sleep(16L)
        } while (
            !visible &&
            SystemClock.uptimeMillis() < deadline
        )
        assertTrue(
            "Text is outside the visible screen $context: $title",
            visible
        )
    }

    private fun assertTextCompletelyVisibleOnScreen(
        title: String,
        context: String
    ) {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var completelyVisible = false
        var details = ""
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val view = findTextView(activity, title) { true }
                val visibleBounds = Rect()
                val visible = view.isShown &&
                    view.getGlobalVisibleRect(visibleBounds)
                completelyVisible = visible &&
                    visibleBounds.width() >=
                    view.width - TOLERANCE_PX &&
                    visibleBounds.height() >=
                    view.height - TOLERANCE_PX
                details = if (visible) {
                    "visible=${visibleBounds.width()}x${visibleBounds.height()} " +
                        "actual=${view.width}x${view.height}"
                } else {
                    "outside window"
                }
            }
            if (!completelyVisible) Thread.sleep(16L)
        } while (
            !completelyVisible &&
            SystemClock.uptimeMillis() < deadline
        )
        assertTrue(
            "Text is not completely visible $context: $title ($details)",
            completelyVisible
        )
    }

    private fun assertContextFixtureExpired() {
        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            assertEquals(EVENT_ID, store.selectedEventId)
            val read = store.factReceipt(
                eventId = EVENT_ID,
                factor = SignalFactor.CONTEXT
            )
            assertEquals(FactReceiptIntegrity.VALID, read.integrity)
            val receipt = requireNotNull(read.receipt)
            val freshness = FreshnessEngine.evaluateFactor(
                factor = receipt.factor,
                claimedLevel = receipt.effectiveEvidence,
                checkedAt = receipt.checkedAt,
                now = System.currentTimeMillis()
            )
            assertEquals(FreshnessStatus.EXPIRED, freshness.status)
        }
    }

    private fun findTextView(
        activity: MainActivity,
        title: String,
        predicate: (TextView) -> Boolean
    ): TextView {
        return windowRoots(activity)
            .flatMap(::descendants)
            .filterIsInstance<TextView>()
            .firstOrNull {
                it.text.toString() == title && predicate(it)
            }
            ?: error("Text control not found: $title")
    }

    private fun assertLargeTextHeaderStacked(
        title: String,
        badge: String
    ) {
        scenario.onActivity { activity ->
            if (activity.resources.configuration.fontScale < 1.3f) {
                return@onActivity
            }
            val titleView = findTextView(activity, title) { true }
            val badgeView = findTextView(activity, badge) { true }
            val parent = titleView.parent
            assertTrue(
                "$title and $badge must share one adaptive header",
                parent === badgeView.parent
            )
            assertTrue(
                "$title header must stack at large text",
                parent is LinearLayout &&
                    parent.orientation == LinearLayout.VERTICAL
            )
            assertTrue(
                "$badge must be below $title",
                badgeView.top >= titleView.bottom
            )
        }
    }

    private fun audit(
        screen: String,
        failures: MutableList<String>
    ) {
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .filter {
                    it.visibility == View.VISIBLE &&
                        it.text.isNotBlank() &&
                        it.width > 0 &&
                        it.height > 0
                }
                .forEach { view ->
                    auditTextView(view, screen, failures)
                }
        }
    }

    private fun auditTextView(
        view: TextView,
        screen: String,
        failures: MutableList<String>
    ) {
        val layout = view.layout ?: return
        if (layout.lineCount == 0) return
        val label = view.text.toString()
            .replace('\n', ' ')
            .take(72)
        val availableHeight = view.height -
            view.compoundPaddingTop -
            view.compoundPaddingBottom
        if (layout.height > availableHeight + TOLERANCE_PX) {
            failures +=
                "$screen: height ${layout.height}>$availableHeight " +
                    "lines=${layout.lineCount} size=${view.width}x${view.height} " +
                    "at=${view.left},${view.top} " +
                    "${viewPath(view)}: " +
                    label
        }
        val lastLine = layout.lineCount - 1
        if (
            layout.getLineEnd(lastLine) <
            layout.text.toString().trimEnd().length
        ) {
            failures += "$screen: hidden tail: $label"
        }
        repeat(layout.lineCount) { line ->
            val lineWidth =
                layout.getLineRight(line) - layout.getLineLeft(line)
            if (lineWidth > layout.width + TOLERANCE_PX) {
                failures +=
                    "$screen: width ${lineWidth.toInt()}>${layout.width}: $label"
            }
            if (layout.getEllipsisCount(line) > 0) {
                failures += "$screen: ellipsis: $label"
            }
        }
    }

    private fun seedApiUpdateHistory() {
        val context = instrumentation.targetContext
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()
        val now = System.currentTimeMillis()
        val from = Instant.ofEpochMilli(now)
            .atZone(ZoneId.of("Europe/Moscow"))
            .toLocalDate()
        val baseStart = now + 3L * 60L * 60L * 1000L
        val before = listOf(
            apiFixture(9101L, baseStart),
            apiFixture(9102L, baseStart + 60L * 60L * 1000L),
            apiFixture(9103L, baseStart + 2L * 60L * 60L * 1000L),
            apiFixture(9104L, baseStart + 3L * 60L * 60L * 1000L),
            apiFixture(9105L, baseStart + 4L * 60L * 60L * 1000L)
        )
        val current = listOf(
            before[0].copy(
                startAt = before[0].startAt + 30L * 60L * 1000L,
                statusCode = "1H",
                statusLabel = "First Half",
                homeScore = 1,
                awayScore = 0
            ),
            before[1].copy(
                statusCode = "PST",
                statusLabel = "Postponed"
            ),
            before[3],
            before[4].copy(
                startAt = before[4].startAt + 90L * 60L * 1000L
            ),
            apiFixture(9106L, baseStart + 5L * 60L * 60L * 1000L)
        )
        val cache = ApiFootballCache(context)
        cache.write(
            ApiFootballFeed(
                fixtures = before,
                fetchedAt = now - 10L * 60L * 1000L,
                fromDate = from.toString(),
                toDate = from.plusDays(2).toString(),
                remainingRequests = 101
            )
        )
        cache.write(
            ApiFootballFeed(
                fixtures = current,
                fetchedAt = now - 5L * 60L * 1000L,
                fromDate = from.toString(),
                toDate = from.plusDays(2).toString(),
                remainingRequests = 98
            )
        )
    }

    private fun apiFixture(
        id: Long,
        startAt: Long
    ): ApiFootballFixture {
        return ApiFootballFixture(
            fixtureId = id,
            country = "Kazakhstan",
            league = "Международная лига развития",
            home = "Академия спортивных технологий $id",
            away = "Объединённая молодёжная команда $id",
            startAt = startAt,
            statusCode = "NS",
            statusLabel = "Not Started",
            homeScore = null,
            awayScore = null
        )
    }

    private fun descendants(root: View): Sequence<View> {
        return sequence {
            yield(root)
            if (root is ViewGroup) {
                for (index in 0 until root.childCount) {
                    yieldAll(descendants(root.getChildAt(index)))
                }
            }
        }
    }

    private fun windowRoots(activity: MainActivity): Sequence<View> {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews().asSequence()
        } else {
            sequenceOf(activity.window.decorView)
        }
    }

    private fun viewPath(view: View): String {
        val nodes = mutableListOf<String>()
        var current: View? = view
        repeat(5) {
            val node = current ?: return@repeat
            val orientation = if (node is android.widget.LinearLayout) {
                if (node.orientation == android.widget.LinearLayout.HORIZONTAL) "H" else "V"
            } else {
                ""
            }
            nodes += "${node.javaClass.simpleName}$orientation(${node.width}x${node.height})"
            current = node.parent as? View
        }
        return nodes.joinToString("<-")
    }

    private companion object {
        const val EVENT_ID = "api_football_9101"
        const val TOLERANCE_PX = 2
        val NAVIGATION_TITLES = listOf(
            "Матчи",
            "Штаб",
            "Чек-листы",
            "Гид",
            "18+"
        )
    }
}
