package ru.sportpulse.info

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.ceil
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionAccessibilityInstrumentationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val context = instrumentation.targetContext
        context.filesDir.resolve("api_football_feed.json").delete()
        context.filesDir.resolve(
            "api_football_feed_previous.json"
        ).delete()
        context.getSharedPreferences(
            "sport_pulse_state",
            Context.MODE_PRIVATE
        ).edit()
            .clear()
            .putBoolean("product_tour_seen_v1", true)
            .putBoolean("age_confirmed_v1", true)
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun primaryScreensExposeNamedButtonSemanticsAndTouchTargets() {
        assertInteractiveControls("Матчи / Список")
        clickText("Инструменты")
        assertInteractiveControls("Матчи / Инструменты")

        clickTab("Штаб")
        assertInteractiveControls("Штаб")
        assertSelected("Штаб: Решение")

        listOf("Чек-листы", "Гид", "18+").forEach { tab ->
            clickTab(tab)
            assertInteractiveControls(tab)
        }
    }

    private fun assertInteractiveControls(screen: String) {
        scenario.onActivity { activity ->
            val minimumPx = ceil(
                48f * activity.resources.displayMetrics.density
            ).toInt()
            val issues = descendants(activity.window.decorView)
                .filter {
                    it.visibility == View.VISIBLE &&
                        it.isEnabled &&
                        it.isClickable
                }
                .flatMap { view ->
                    controlIssues(view, minimumPx)
                }
                .toList()
            assertTrue(
                "$screen accessibility issues:\n${issues.joinToString("\n")}",
                issues.isEmpty()
            )
        }
    }

    private fun controlIssues(
        view: View,
        minimumPx: Int
    ): Sequence<String> {
        val node = view.createAccessibilityNodeInfo()
        val label = listOf(
            view.contentDescription,
            (view as? TextView)?.text,
            (view as? TextView)?.hint,
            node.contentDescription,
            node.text
        ).firstOrNull { !it.isNullOrBlank() }
        val customTextAction = view is TextView &&
            view !is EditText &&
            view !is CheckBox &&
            view !is RadioButton
        val customLayoutAction = view is LinearLayout &&
            !view.contentDescription.isNullOrBlank()
        return sequence {
            if (view.width < minimumPx || view.height < minimumPx) {
                yield(
                    "${viewLabel(view)} target=${view.width}x${view.height}, " +
                        "required=${minimumPx}x$minimumPx"
                )
            }
            if (!view.isFocusable) {
                yield("${viewLabel(view)} is not focusable")
            }
            if (label == null) {
                yield("${viewLabel(view)} has no accessible name")
            }
            if (!node.isClickable) {
                yield("${viewLabel(view)} exposes no click action")
            }
            if (
                label?.contains("выбрано", ignoreCase = true) == true &&
                !node.isSelected
            ) {
                yield("${viewLabel(view)} is named selected but exposes false")
            }
            if (
                (customTextAction || customLayoutAction) &&
                node.className != Button::class.java.name
            ) {
                yield(
                    "${viewLabel(view)} role=${node.className}, " +
                        "expected=${Button::class.java.name}"
                )
            }
        }
    }

    private fun clickTab(title: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .first {
                    it.text.toString() == title &&
                        it.contentDescription == "Раздел $title"
                }
                .performClick()
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertSelected(contentDescription: String) {
        scenario.onActivity { activity ->
            val view = descendants(activity.window.decorView)
                .first {
                    it.contentDescription == contentDescription
                }
            assertTrue(
                "$contentDescription is not selected",
                view.createAccessibilityNodeInfo().isSelected
            )
        }
    }

    private fun clickText(title: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .first {
                    it.text.toString() == title && it.isClickable
                }
                .performClick()
        }
        instrumentation.waitForIdleSync()
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

    private fun viewLabel(view: View): String {
        val value = view.contentDescription
            ?: (view as? TextView)?.text
            ?: ""
        return "${view.javaClass.simpleName}[$value]"
    }
}
