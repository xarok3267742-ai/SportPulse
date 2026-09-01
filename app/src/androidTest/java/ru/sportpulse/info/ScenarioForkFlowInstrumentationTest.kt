package ru.sportpulse.info

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
class ScenarioForkFlowInstrumentationTest {
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
            .putString("selected_event", "rpl_zenit_krasnodar")
            .commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun forkRoutesEachScenarioToItsInput() {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .first {
                    it.contentDescription == "Раздел Штаб"
                }
                .performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
                .first {
                    it.text.toString() == "Сформулировать тезис" &&
                        it.isClickable
                }
                .performClick()
        }
        instrumentation.waitForIdleSync()

        assertForkFocuses(
            contentDescriptionPrefix = "Сценарий A:",
            hintPrefix = "Например:"
        )
        assertForkFocuses(
            contentDescriptionPrefix = "Сценарий B:",
            hintPrefix = "Что сильнее"
        )
        assertForkFocuses(
            contentDescriptionPrefix = "Стоп-линия:",
            hintPrefix = "Какой наблюдаемый"
        )
    }

    private fun assertForkFocuses(
        contentDescriptionPrefix: String,
        hintPrefix: String
    ) {
        scenario.onActivity { activity ->
            val views = descendants(activity.window.decorView)
            views.first {
                it.contentDescription?.toString()
                    ?.startsWith(contentDescriptionPrefix) == true
            }.performClick()
            val input = views
                .filterIsInstance<EditText>()
                .first {
                    it.hint?.toString()
                        ?.startsWith(hintPrefix) == true
                }
            assertTrue(
                "$contentDescriptionPrefix did not focus its input",
                input.isFocused
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun descendants(root: View): List<View> {
        return buildList {
            add(root)
            if (root is ViewGroup) {
                repeat(root.childCount) { index ->
                    addAll(descendants(root.getChildAt(index)))
                }
            }
        }
    }
}
