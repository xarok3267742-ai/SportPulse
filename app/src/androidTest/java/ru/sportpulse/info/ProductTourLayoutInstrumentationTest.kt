package ru.sportpulse.info

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
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
        assertAgeGateHeader()
        assertCompletelyVisible("Мне есть 18 лет")
        assertCompletelyVisible("Выйти")
        clickText("Мне есть 18 лет")

        val titles = listOf(
            "Выберите матч",
            "Сначала прочитайте статус",
            "Разведите варианты A и B",
            "Закройте один пробел",
            "Сначала выберите, потом запишите"
        )
        val stages = listOf(
            "СОБЫТИЕ",
            "ТАБЛО",
            "СЦЕНАРИЙ",
            "ИСТОЧНИК",
            "РЕШЕНИЕ"
        )
        val primaryActions = listOf(
            "Далее: табло",
            "Далее: сценарий",
            "Далее: факт",
            "Далее: решение",
            "Начать с Матчей"
        )
        repeat(5) { index ->
            val step = index + 1
            waitForText("ШАГ $step ИЗ 5")
            audit("Обучение / шаг $step", failures)
            assertTourHeader(step, stages[index], titles[index])
            val action = primaryActions[index]
            assertCompletelyVisible(action)
            assertCompletelyVisible("Закрыть")
            if (step > 1) assertCompletelyVisible("Назад")
            if (step == 1) assertProductTourSeen(expected = false)
            clickText(action)
        }
        waitForProductTourSeen()

        assertTrue(
            failures.joinToString(
                separator = "\n",
                prefix = "Product tour layout failures:\n"
            ),
            failures.isEmpty()
        )
    }

    private fun assertTourHeader(
        step: Int,
        stageText: String,
        titleText: String
    ) {
        scenario.onActivity { activity ->
            val roots = windowRoots(activity).toList()
            val views = roots.asSequence()
                .flatMap(::descendants)
                .toList()
            val label = views.filterIsInstance<TextView>()
                .first { it.text.toString() == "ШАГ $step ИЗ 5" }
            val progress = views.firstOrNull {
                it.contentDescription?.toString() ==
                    "Маршрут обучения: шаг $step из 5"
            } ?: error("Product tour progress is missing for step $step")
            val image = views.filterIsInstance<ImageView>()
                .firstOrNull {
                    it.contentDescription?.toString() ==
                        TOUR_IMAGE_DESCRIPTION
                }
                ?: error("Product tour image is missing")
            val stage = views.filterIsInstance<TextView>()
                .first { it.text.toString() == stageText }
            val title = views.filterIsInstance<TextView>()
                .first { it.text.toString() == titleText }
            val labelBounds = visibleBounds(label)
            val progressBounds = visibleBounds(progress)
            val imageBounds = visibleBounds(image)
            val stageBounds = visibleBounds(stage)
            val titleBounds = visibleBounds(title)
            assertTrue(
                "Tour label overlaps progress: $labelBounds and $progressBounds",
                labelBounds.bottom <= progressBounds.top
            )
            assertTrue(
                "Tour progress overlaps image: $progressBounds and $imageBounds",
                progressBounds.bottom <= imageBounds.top
            )
            assertTrue(
                "Tour image overlaps stage: $imageBounds and $stageBounds",
                imageBounds.bottom <= stageBounds.top
            )
            assertTrue(
                "Tour stage overlaps title: $stageBounds and $titleBounds",
                stageBounds.bottom <= titleBounds.top
            )
        }
    }

    private fun assertProductTourSeen(expected: Boolean) {
        val actual = instrumentation.targetContext
            .getSharedPreferences("sport_pulse_state", Context.MODE_PRIVATE)
            .getBoolean(PRODUCT_TOUR_SEEN_KEY, false)
        if (expected) {
            assertTrue("Product tour completion was not stored", actual)
        } else {
            assertFalse("Product tour was stored before an explicit exit", actual)
        }
    }

    private fun waitForProductTourSeen() {
        repeat(50) {
            val seen = instrumentation.targetContext
                .getSharedPreferences("sport_pulse_state", Context.MODE_PRIVATE)
                .getBoolean(PRODUCT_TOUR_SEEN_KEY, false)
            if (seen) return
            Thread.sleep(20)
        }
        assertProductTourSeen(expected = true)
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

    private fun assertAgeGateHeader() {
        scenario.onActivity { activity ->
            val roots = windowRoots(activity).toList()
            val image = roots.asSequence()
                .flatMap(::descendants)
                .filterIsInstance<ImageView>()
                .firstOrNull {
                    it.contentDescription?.toString() ==
                        AGE_IMAGE_DESCRIPTION
                }
                ?: error("Age gate image is missing")
            val label = roots.asSequence()
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .first { it.text.toString() == "ТОЛЬКО 18+" }
            val warning = roots.asSequence()
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .first {
                    it.text.toString() ==
                        "Нет 18 лет — выберите «Выйти»."
                }
            val title = roots.asSequence()
                .flatMap(::descendants)
                .filterIsInstance<TextView>()
                .first { it.text.toString() == "Подтвердите возраст" }
            val labelBounds = visibleBounds(label)
            val warningBounds = visibleBounds(warning)
            val imageBounds = visibleBounds(image)
            val titleBounds = visibleBounds(title)
            assertTrue(
                "Age label overlaps the warning: " +
                    "$labelBounds and $warningBounds",
                labelBounds.bottom <= warningBounds.top
            )
            assertTrue(
                "Age warning overlaps the image: " +
                    "$warningBounds and $imageBounds",
                warningBounds.bottom <= imageBounds.top
            )
            assertTrue(
                "Age image overlaps the title: " +
                    "$imageBounds and $titleBounds",
                imageBounds.bottom <= titleBounds.top
            )
        }
    }

    private fun visibleBounds(view: View): Rect {
        return Rect().also { bounds ->
            assertTrue(
                "View is outside the window: " +
                    (view.contentDescription ?: view.javaClass.simpleName),
                view.getGlobalVisibleRect(bounds)
            )
            assertTrue(
                "View is clipped: " +
                    (view.contentDescription ?: view.javaClass.simpleName),
                bounds.width() >= view.width - TOLERANCE_PX &&
                    bounds.height() >= view.height - TOLERANCE_PX
            )
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
        const val AGE_IMAGE_DESCRIPTION =
            "Порог 18+: закрытый доступ к инструментам проверки спортивных данных"
        const val TOUR_IMAGE_DESCRIPTION =
            "Маршрут проверки: событие, табло, сценарий, источник и решение"
        const val PRODUCT_TOUR_SEEN_KEY = "product_tour_seen_v1"
    }
}
