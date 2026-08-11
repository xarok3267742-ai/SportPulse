package ru.sportpulse.info

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FactReceiptFlowInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        context
            .getSharedPreferences(
                "sport_pulse_state",
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .putBoolean("product_tour_seen_v1", true)
            .putBoolean("age_confirmed_v1", true)
            .putString("selected_event", "rpl_zenit_krasnodar")
            .commit()
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun receiptSavesFactorEvidenceAndSourceAuditTogether() {
        clickText("Анализ") { it.contentDescription == "Раздел Анализ" }
        clickText("Записать факт и источники") { it.isClickable }

        setField(
            hint = "Например: в последних трёх матчах использовалась одна схема",
            value = "Подтверждён опубликованный стартовый состав события"
        )
        setField(
            hint = "Название или ссылка на первичную публикацию",
            value = "https://club.example/report"
        )
        setField(
            hint = "Название или ссылка; можно оставить пустым",
            value = "https://league.example/matches"
        )
        clickRadio("Независимы •")
        clickRadio("Полная проверка •")
        clickText("СОХРАНИТЬ") { it.isClickable }

        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            val eventId = requireNotNull(store.selectedEventId)
            val read = store.factReceipt(
                eventId = eventId,
                factor = SignalFactor.LINEUP
            )
            val receipt = requireNotNull(read.receipt)

            assertEquals(FactReceiptIntegrity.VALID, read.integrity)
            assertEquals(
                FactReceiptCoverage.COUNTERCHECKED,
                receipt.coverage
            )
            assertEquals(
                SourceAuditState.INDEPENDENT,
                receipt.sourceAuditState
            )
            assertEquals(
                EvidenceLevel.QUORUM,
                receipt.effectiveEvidence
            )
            assertEquals(
                FactReceiptCoverage.COUNTERCHECKED.score,
                store.assessment(
                    DemoCatalog.events.first().copy(id = eventId)
                ).value(SignalFactor.LINEUP)
            )
            assertEquals(
                EvidenceLevel.QUORUM,
                store.claimedEvidence(eventId).level(SignalFactor.LINEUP)
            )
            assertEquals(
                SourceAuditState.INDEPENDENT,
                store.sourceAudit(eventId).state(SignalFactor.LINEUP)
            )
        }

        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            val eventId = requireNotNull(store.selectedEventId)
            store.saveFactReceipt(
                FactReceiptFactory.create(
                    eventId = eventId,
                    factor = SignalFactor.FORM,
                    statement =
                        "Подтверждены матчи с сопоставимыми соперниками",
                    primarySource =
                        "https://club.example/form",
                    secondarySource =
                        "https://press.example/form",
                    sourceAuditState = SourceAuditState.INDEPENDENT,
                    coverage = FactReceiptCoverage.DETAILS,
                    checkedAt = System.currentTimeMillis()
                )
            )
        }

        clickText("Открыть реестр фактов") { it.isClickable }
        scenario.onActivity { activity ->
            val visibleText = windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .map { it.text.toString() }
                .toList()
            assertTrue(
                visibleText.contains(
                    "2 из 5 квитанций • независимо сверено: 2"
                )
            )
            assertTrue(
                visibleText.contains(
                    "Подтверждены матчи с сопоставимыми соперниками"
                )
            )
            assertTrue(
                visibleText.contains(
                    "КРОСС-ЭХО • ОБЩАЯ ЗАВИСИМОСТЬ"
                )
            )
            assertTrue(
                visibleText.contains("club.example")
            )
        }
        clickText("Добавить другое происхождение: Форма") {
            it.isClickable
        }
        scenario.onActivity { activity ->
            val visibleText = windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .map { it.text.toString() }
                .toList()
            assertTrue(visibleText.contains("Факт-квитанция: Форма"))
            assertTrue(
                visibleText.contains(
                    "ФАКТ-МАРШРУТ • ФАКТОР 1 ИЗ 5"
                )
            )
        }
        clickText("К реестру") { it.isClickable }
        assertTextVisible("Реестр фактов")

        clickText("Продолжить маршрут: Нагрузка") {
            it.isClickable
        }
        assertTextVisible("Факт-квитанция: Нагрузка")
        setField(
            hint = "Например: в последних трёх матчах использовалась одна схема",
            value = "Подтверждён интервал отдыха перед выбранным событием"
        )
        setField(
            hint = "Название или ссылка на первичную публикацию",
            value = "https://calendar.example/rest"
        )
        clickText("СОХРАНИТЬ") { it.isClickable }
        assertTextVisible(
            "3 из 5 квитанций • независимо сверено: 2"
        )

        clickDescription("Обновить факт-квитанцию: Нагрузка")
        assertTextVisible("Факт-квитанция: Нагрузка")
        clickText("Удалить") { it.isClickable }
        clickDialogButton(
            dialogTitle = "Удалить факт-квитанцию?",
            buttonTitle = "Удалить"
        )
        assertTextVisible(
            "2 из 5 квитанций • независимо сверено: 2"
        )
        clickText("Закрыть") { it.isClickable }
    }

    private fun clickText(
        title: String,
        predicate: (TextView) -> Boolean
    ) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString().equals(
                        title,
                        ignoreCase = true
                    ) && predicate(it)
                }
                ?.performClick()
                ?: error("Text control not found: $title")
        }
        instrumentation.waitForIdleSync()
    }

    private fun setField(hint: String, value: String) {
        scenario.onActivity { activity ->
            val field = windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<EditText>()
                .firstOrNull { it.hint.toString() == hint }
                ?: error("Input not found: $hint")
            field.setText(value)
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertTextVisible(title: String) {
        scenario.onActivity { activity ->
            val found = windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .any { it.text.toString() == title }
            assertTrue("Text not visible: $title", found)
        }
    }

    private fun clickDescription(description: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .firstOrNull {
                    it.contentDescription?.toString() == description &&
                        it.isClickable
                }
                ?.performClick()
                ?: error("Control not found: $description")
        }
        instrumentation.waitForIdleSync()
    }

    private fun clickDialogButton(
        dialogTitle: String,
        buttonTitle: String
    ) {
        scenario.onActivity { activity ->
            val dialogRoot = windowRoots(activity).firstOrNull { root ->
                descendants(root)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString() == dialogTitle }
            } ?: error("Dialog not found: $dialogTitle")
            descendants(dialogRoot)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString().equals(
                        buttonTitle,
                        ignoreCase = true
                    ) && it.isClickable
                }
                ?.performClick()
                ?: error("Dialog button not found: $buttonTitle")
        }
        instrumentation.waitForIdleSync()
    }

    private fun clickRadio(prefix: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<RadioButton>()
                .firstOrNull {
                    it.text.toString().startsWith(prefix)
                }
                ?.let { it.isChecked = true }
                ?: error("Radio option not found: $prefix")
        }
        instrumentation.waitForIdleSync()
    }

    private fun windowRoots(activity: MainActivity): Sequence<View> {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews().asSequence()
        } else {
            sequenceOf(activity.window.decorView)
        }
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
}
