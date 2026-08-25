package ru.sportpulse.info

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FactFreshnessFlowInstrumentationTest {
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
            .putString("selected_event", EVENT_ID)
            .commit()
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()

        val now = System.currentTimeMillis()
        val store = UserStateStore(context)
        store.saveFactReceipt(
            receipt(
                factor = SignalFactor.FORM,
                checkedAt = now,
                suffix = "form"
            )
        )
        store.saveFactReceipt(
            receipt(
                factor = SignalFactor.LINEUP,
                checkedAt = now - 2L *
                    FreshnessPolicy.validForMillis(SignalFactor.LINEUP),
                suffix = "lineup"
            )
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun expiredReceiptStopsCountingAndBecomesFirstRouteStep() {
        clickText("Штаб") {
            it.contentDescription == "Раздел Штаб"
        }
        clickText("Открыть реестр фактов") { it.isClickable }

        assertTextVisible("НУЖНА ПЕРЕПРОВЕРКА")
        assertTextVisible(
            "2 из 5 квитанций • независимо сверено: 1"
        )
        assertTextVisible(
            "Истёк срок: 1 • нужна новая проверка"
        )
        assertTextVisible("СРОК ИСТЁК")
        assertTextVisible(
            "ФАКТ-МАРШРУТ • СЛЕДУЮЩИЙ ШАГ\n" +
                "Повторно проверьте истёкший факт «Состав»."
        )

        clickText("Продолжить маршрут: Состав") {
            it.isClickable
        }
        assertTextVisible("Факт-квитанция: Состав")
        assertTextVisible("ФАКТ-МАРШРУТ • ФАКТОР 2 ИЗ 5")
    }

    private fun receipt(
        factor: SignalFactor,
        checkedAt: Long,
        suffix: String
    ): FactReceipt {
        return FactReceiptFactory.create(
            eventId = EVENT_ID,
            factor = factor,
            statement =
                "Подтверждён проверяемый факт выбранного события",
            primarySource = "https://club.example/$suffix",
            secondarySource = "https://league.example/$suffix",
            sourceAuditState = SourceAuditState.INDEPENDENT,
            coverage = FactReceiptCoverage.DETAILS,
            checkedAt = checkedAt
        )
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
                    it.text.toString() == title && predicate(it)
                }
                ?.performClick()
                ?: error("Text control not found: $title")
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

    private companion object {
        const val EVENT_ID = "rpl_zenit_krasnodar"
    }
}
