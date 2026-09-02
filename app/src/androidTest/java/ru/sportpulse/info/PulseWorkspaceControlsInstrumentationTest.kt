package ru.sportpulse.info

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
class PulseWorkspaceControlsInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()
        context.getSharedPreferences(
            "sport_pulse_state",
            Context.MODE_PRIVATE
        ).edit()
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
    fun depthControlPrecedesEvidenceBoardAndKeepsContext() {
        clickText("Штаб")

        assertVerticalOrder(
            firstText = "Записать идею матча",
            firstMustBeClickable = true,
            secondText = "Глубина разбора",
            thirdText = "Статус источников, а не шанс победы"
        )
        assertDescriptionPresent(
            "Два режима глубины: общий обзор и подробная проверка. Данные остаются теми же"
        )
        assertSelectedMode("Коротко")

        clickText("Подробно")

        assertSelectedMode("Подробно")
        assertTextPresent("ПОДРОБНО • ПОЛНЫЙ АУДИТ ФАКТОВ")
        assertEventuallyCompletelyVisible("Подробно")

        clickText("Коротко")

        assertSelectedMode("Коротко")
        assertTextPresent("КОРОТКО • ИТОГ И ОДИН ШАГ")
        assertEventuallyCompletelyVisible("Коротко")
    }

    private fun assertVerticalOrder(
        firstText: String,
        firstMustBeClickable: Boolean,
        secondText: String,
        thirdText: String
    ) {
        scenario.onActivity { activity ->
            val first = findTextView(activity, firstText) {
                !firstMustBeClickable || it.isClickable
            }
            val second = findTextView(activity, secondText) { true }
            val third = findTextView(activity, thirdText) { true }
            val firstTop = screenTop(first)
            val secondTop = screenTop(second)
            val thirdTop = screenTop(third)
            assertTrue(
                "Depth control must follow the decision command: " +
                    "$firstTop !< $secondTop",
                firstTop < secondTop
            )
            assertTrue(
                "Depth control must precede the evidence board: " +
                    "$secondTop !< $thirdTop",
                secondTop < thirdTop
            )
        }
    }

    private fun assertSelectedMode(title: String) {
        scenario.onActivity { activity ->
            val view = descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.contentDescription?.toString() ==
                        "Режим анализа: $title"
                }
                ?: error("Workspace mode not found: $title")
            assertTrue("Workspace mode is not selected: $title", view.isSelected)
        }
    }

    private fun assertDescriptionPresent(value: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Description not found: $value",
                descendants(activity.window.decorView).any {
                    it.contentDescription?.toString() == value
                }
            )
        }
    }

    private fun assertTextPresent(value: String) {
        scenario.onActivity { activity ->
            findTextView(activity, value) { true }
        }
    }

    private fun assertEventuallyCompletelyVisible(value: String) {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var visible = false
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val view = findTextView(activity, value) { it.isClickable }
                val bounds = Rect()
                visible = view.getGlobalVisibleRect(bounds) &&
                    bounds.width() == view.width &&
                    bounds.height() == view.height
            }
            if (!visible) Thread.sleep(16L)
        } while (!visible && SystemClock.uptimeMillis() < deadline)
        assertTrue("Workspace mode is not completely visible: $value", visible)
    }

    private fun clickText(value: String) {
        scenario.onActivity { activity ->
            findTextView(activity, value) { it.isClickable }.performClick()
        }
        instrumentation.waitForIdleSync()
    }

    private fun findTextView(
        activity: MainActivity,
        value: String,
        predicate: (TextView) -> Boolean
    ): TextView {
        return descendants(activity.window.decorView)
            .filterIsInstance<TextView>()
            .firstOrNull {
                it.text.toString() == value && predicate(it)
            }
            ?: error("Text control not found: $value")
    }

    private fun screenTop(view: View): Int {
        return IntArray(2).also(view::getLocationOnScreen)[1]
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
