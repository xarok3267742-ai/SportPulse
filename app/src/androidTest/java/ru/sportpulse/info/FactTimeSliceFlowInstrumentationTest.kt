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
class FactTimeSliceFlowInstrumentationTest {
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
        val checkedAt = mapOf(
            SignalFactor.FORM to
                (now - 10L * FreshnessPolicy.HOUR_MILLIS),
            SignalFactor.LINEUP to now,
            SignalFactor.LOAD to
                (now - 2L * FreshnessPolicy.HOUR_MILLIS),
            SignalFactor.CONTEXT to
                (now - 3L * FreshnessPolicy.HOUR_MILLIS),
            SignalFactor.SOURCES to
                (now - FreshnessPolicy.HOUR_MILLIS)
        )
        val store = UserStateStore(context)
        SignalFactor.values().forEach { factor ->
            store.saveFactReceipt(
                receipt(
                    factor = factor,
                    checkedAt = checkNotNull(checkedAt[factor])
                )
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun readyFactsFromDifferentMomentsOfferOldestRefresh() {
        clickText("Штаб") {
            it.contentDescription == "Раздел Штаб"
        }
        clickText("Открыть реестр фактов") { it.isClickable }

        assertTextVisible("ПЯТЬ ФАКТОРОВ СВЕРЕНЫ")
        assertTextVisible(
            "5 из 5 квитанций • независимо сверено: 5"
        )
        assertTextVisible("ЕДИНЫЙ СРЕЗ • РАЗНЫЕ МОМЕНТЫ")
        assertTextVisible("5 активных • разброс 10 ч")
        assertDescriptionStartsWith(
            "Единый срез. Факты относятся к разным моментам"
        )

        clickText("Обновить старейший факт: Форма") {
            it.isClickable
        }
        assertTextVisible("Факт-квитанция: Форма")
        assertTextVisible("ФАКТ-МАРШРУТ • ФАКТОР 1 ИЗ 5")
    }

    private fun receipt(
        factor: SignalFactor,
        checkedAt: Long
    ): FactReceipt {
        val slug = factor.name.lowercase()
        return FactReceiptFactory.create(
            eventId = EVENT_ID,
            factor = factor,
            statement =
                "Подтверждён проверяемый факт ${factor.title.lowercase()}",
            primarySource = "https://$slug.club.example/report",
            secondarySource = "https://$slug.league.example/report",
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

    private fun assertDescriptionStartsWith(prefix: String) {
        scenario.onActivity { activity ->
            val found = windowRoots(activity)
                .flatMap(::descendants)
                .any {
                    it.contentDescription
                        ?.toString()
                        ?.startsWith(prefix) == true
                }
            assertTrue("Description not visible: $prefix", found)
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
