package ru.sportpulse.info

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
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
class ProductTourLayoutInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        context.getSharedPreferences(
            "sport_pulse_state",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve("api_football_feed_previous.json").delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("ТОЛЬКО 18+")
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun everyStepKeepsNavigationVisibleWithoutTextClipping() {
        val failures = mutableListOf<String>()
        audit("Подтверждение возраста", failures)
        assertCompletelyVisible("Мне есть 18 лет")
        assertCompletelyVisible("Выйти")
        clickText("Мне есть 18 лет")

        repeat(5) { index ->
            val step = index + 1
            waitForText("ШАГ $step ИЗ 5")
            audit("Обучение / шаг $step", failures)
            val action = if (step == 5) {
                "Начать с Матчей"
            } else {
                "Далее"
            }
            assertCompletelyVisible(action)
            assertCompletelyVisible(
                if (step == 1) "Закрыть" else "Назад"
            )
            clickText(action)
        }

        assertTrue(
            failures.joinToString(
                separator = "\n",
                prefix = "Product tour layout failures:\n"
            ),
            failures.isEmpty()
        )
    }

    private fun waitForText(value: String) {
        repeat(50) {
            var found = false
            scenario.onActivity { activity ->
                found = textViews(activity).any {
                    it.text.toString() == value
                }
            }
            if (found) return
            Thread.sleep(50)
            instrumentation.waitForIdleSync()
        }
        error("Text not found: $value")
    }

    private fun clickText(value: String) {
        scenario.onActivity { activity ->
            textViews(activity).firstOrNull {
                it.text.toString() == value && it.isClickable
            }?.performClick() ?: error("Clickable text not found: $value")
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertCompletelyVisible(value: String) {
        scenario.onActivity { activity ->
            val view = textViews(activity).firstOrNull {
                it.text.toString() == value && it.isClickable
            } ?: error("Action not found: $value")
            val visible = Rect()
            assertTrue("Action is outside the window: $value", view.getGlobalVisibleRect(visible))
            assertTrue(
                "Action is clipped: $value visible=${visible.width()}x${visible.height()} " +
                    "actual=${view.width}x${view.height}",
                visible.width() >= view.width - TOLERANCE_PX &&
                    visible.height() >= view.height - TOLERANCE_PX
            )
            val minimumPx = (48f * activity.resources.displayMetrics.density)
                .toInt()
            assertTrue(
                "Action target is smaller than 48 dp: $value " +
                    "${view.width}x${view.height}<$minimumPx",
                view.width >= minimumPx && view.height >= minimumPx
            )
            assertTrue("Action is not focusable: $value", view.isFocusable)
            val node: AccessibilityNodeInfo =
                view.createAccessibilityNodeInfo()
            assertTrue("Action exposes no click action: $value", node.isClickable)
        }
    }

    private fun audit(
        screen: String,
        failures: MutableList<String>
    ) {
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            textViews(activity)
                .filter {
                    it.visibility == View.VISIBLE &&
                        it.text.isNotBlank() &&
                        it.width > 0 &&
                        it.height > 0
                }
                .forEach { view ->
                    val layout = view.layout ?: return@forEach
                    if (layout.lineCount == 0) return@forEach
                    val label = view.text.toString()
                        .replace('\n', ' ')
                        .take(72)
                    val availableHeight = view.height -
                        view.compoundPaddingTop -
                        view.compoundPaddingBottom
                    if (layout.height > availableHeight + TOLERANCE_PX) {
                        failures +=
                            "$screen: height ${layout.height}>$availableHeight: $label"
                    }
                    val lastLine = layout.lineCount - 1
                    if (
                        layout.getLineEnd(lastLine) <
                        layout.text.toString().trimEnd().length
                    ) {
                        failures += "$screen: hidden tail: $label"
                    }
                    repeat(layout.lineCount) { line ->
                        if (layout.getEllipsisCount(line) > 0) {
                            failures += "$screen: ellipsis: $label"
                        }
                    }
                }
        }
    }

    private fun textViews(activity: MainActivity): Sequence<TextView> {
        return windowRoots(activity)
            .flatMap(::descendants)
            .filterIsInstance<TextView>()
    }

    private fun descendants(root: View): Sequence<View> {
        return sequence {
            yield(root)
            if (root is ViewGroup) {
                repeat(root.childCount) { index ->
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

    private companion object {
        const val TOLERANCE_PX = 2
    }
}
