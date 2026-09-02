package ru.sportpulse.info

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideNavigatorInstrumentationTest {
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
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun navigatorUsesTheAutomaticallySelectedEvent() {
        clickText("Гид")

        assertTextPresent("Навигатор проверки")
        assertTextPresent("Соберите план из трёх вопросов")
        assertTextPresent("Пройдено 1 из 5 шагов")
        assertTextPresent("Записать идею матча")
        assertContentDescriptionStartingWith(
            "Навигатор проверки: пять этапов"
        )
    }

    @Test
    fun selectedEventRoutesToTheFirstPlanQuestion() {
        scenario.onActivity { activity ->
            UserStateStore(activity).selectedEventId = EVENT_ID
        }
        scenario.recreate()
        instrumentation.waitForIdleSync()

        clickText("Гид")
        assertTextPresent("Соберите план из трёх вопросов")
        assertTextPresent("Записать идею матча")
        clickText("Записать идею матча")

        assertTextPresent("Рабочая форма")
        assertTrue(
            "The first plan input must receive focus",
            focusedInput() != null
        )
    }

    @Test
    fun dictionaryDialogKeepsItsCloseActionVisible() {
        clickText("Гид")
        openGuideReference("Словарь терминов")

        onView(withText("Словарь экрана"))
            .check(matches(isDisplayed()))
        onView(withText("Закрыть"))
            .check(matches(isCompletelyDisplayed()))
            .perform(click())
    }

    @Test
    fun quickStartDialogKeepsItsCloseActionVisible() {
        clickText("Гид")
        openGuideReference("Быстрый старт")
        onView(withText("Выберите событие"))
            .check(matches(isDisplayed()))
        onView(withText("Показать обучение по шагам"))
            .check(matches(withText("Показать обучение по шагам")))
        onView(withText("Закрыть"))
            .check(matches(isCompletelyDisplayed()))
            .perform(click())
    }

    private fun focusedInput(): EditText? {
        var focused: EditText? = null
        scenario.onActivity { activity ->
            focused = descendants(activity.window.decorView)
                .filterIsInstance<EditText>()
                .firstOrNull { it.hasFocus() }
        }
        return focused
    }

    private fun openGuideReference(title: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString() == title && it.isClickable
                }
                ?.performClick()
                ?: error("Guide reference not found: $title")
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(750)
    }

    private fun clickText(title: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .firstOrNull {
                    it.text.toString() == title && it.isClickable
                }
                ?.performClick()
                ?: error("Clickable text not found: $title")
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertTextPresent(title: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Text not found: $title",
                descendants(activity.window.decorView)
                    .filterIsInstance<TextView>()
                    .any { it.text.toString() == title }
            )
        }
    }

    private fun assertContentDescriptionStartingWith(
        prefix: String
    ) {
        scenario.onActivity { activity ->
            assertTrue(
                "Content description not found: $prefix",
                descendants(activity.window.decorView).any {
                    it.contentDescription?.toString()
                        ?.startsWith(prefix) == true
                }
            )
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
