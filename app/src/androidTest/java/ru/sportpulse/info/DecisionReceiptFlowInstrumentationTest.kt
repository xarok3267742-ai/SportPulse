package ru.sportpulse.info

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecisionReceiptFlowInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()
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
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun choiceDoesNotWriteUntilExplicitCommit() {
        clickText("Штаб")
        clickText("Подробно")
        clickTextStartingWith("Решение\n")

        assertTextPresent("ШАГ 1 ИЗ 2")
        assertTextPresent("Выберите честный итог")
        assertTextPresent("Выберите итог")
        assertNoDecisionStored()

        clickRadioStartingWith("Пропустить\n")

        assertTextPresent("Готово: пропустить")
        assertTextPresent("Зафиксировать: Пропустить")
        assertNoDecisionStored()

        clickText("Зафиксировать: Пропустить")

        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            val snapshot = store.decisionSnapshot(EVENT_ID)
            val ledger = store.decisionLedger()
            assertEquals(
                SavedDecision.SKIP,
                requireNotNull(snapshot).decision
            )
            assertEquals(DecisionLedgerIntegrity.INTACT, ledger.integrity)
            assertEquals(1L, requireNotNull(ledger.ledger).totalRecordCount)
        }
        assertTextPresent("Выберите честный итог")
    }

    private fun assertNoDecisionStored() {
        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            assertNull(store.decisionSnapshot(EVENT_ID))
            assertEquals(
                DecisionLedgerIntegrity.EMPTY,
                store.decisionLedger().integrity
            )
        }
    }

    private fun clickText(title: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString() == title && it.isClickable
                }
                ?.performClick()
                ?: error("Clickable text not found: $title")
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
                ?: error("Clickable text not found by prefix: $prefix")
        }
        instrumentation.waitForIdleSync()
    }

    private fun clickRadioStartingWith(prefix: String) {
        scenario.onActivity { activity ->
            windowRoots(activity)
                .flatMap(::descendants)
                .filterIsInstance<RadioButton>()
                .firstOrNull {
                    it.text.toString().startsWith(prefix) &&
                        it.isEnabled
                }
                ?.performClick()
                ?: error("Radio choice not found by prefix: $prefix")
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertTextPresent(title: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Text not found: $title",
                windowRoots(activity)
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString() == title }
            )
        }
    }

    private fun windowRoots(activity: MainActivity): Sequence<View> {
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            WindowInspector.getGlobalWindowViews().asSequence()
        } else {
            sequenceOf(activity.window.decorView)
        }
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            repeat(root.childCount) { index ->
                yieldAll(descendants(root.getChildAt(index)))
            }
        }
    }

    private companion object {
        const val EVENT_ID = "rpl_zenit_krasnodar"
    }
}
