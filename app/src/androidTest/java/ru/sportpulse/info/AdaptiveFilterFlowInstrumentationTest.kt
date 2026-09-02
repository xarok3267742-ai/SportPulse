package ru.sportpulse.info

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
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
class AdaptiveFilterFlowInstrumentationTest {
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
    fun demoFiltersStayVisibleWithoutHidingFirstAction() {
        scenario.onActivity { activity ->
            val views = descendants(activity.window.decorView)
            val group = views
                .filterIsInstance<AdaptiveWrapLayout>()
                .first { it.tag == AdaptiveGroupTags.SPORT_FILTERS }
            val titles = (0 until group.childCount).map { index ->
                (group.getChildAt(index) as TextView).text.toString()
            }
            assertEquals(
                listOf(
                    "Все",
                    "Футбол",
                    "Хоккей",
                    "Баскетбол",
                    "Киберспорт",
                    "Другие"
                ),
                titles
            )
            repeat(group.childCount) { index ->
                val child = group.getChildAt(index)
                assertTrue(child.left >= group.paddingLeft)
                assertTrue(
                    child.right <= group.width - group.paddingRight
                )
            }

            val configuration = activity.resources.configuration
            if (
                configuration.fontScale < 1.3f &&
                configuration.screenWidthDp >= 390 &&
                configuration.screenHeightDp >= 840
            ) {
                val action = views
                    .filterIsInstance<TextView>()
                    .first { it.text.toString() == "Открыть анализ ›" }
                val visible = Rect()
                assertTrue(action.getGlobalVisibleRect(visible))
                assertTrue(
                    "First analysis action is clipped: " +
                        "visible=${visible.width()}x${visible.height()}, " +
                        "actual=${action.width}x${action.height}",
                    visible.width() >= action.width - 2 &&
                        visible.height() >= action.height - 2
                )
            }
        }
    }

    @Test
    fun matchdayBriefingCopyIsVisibleAndNeverOverlaps() {
        scenario.onActivity { activity ->
            val textViews = descendants(activity.window.decorView)
                .filterIsInstance<TextView>()
            val safety = textViews.first {
                it.text.toString() == "ИНФОРМАЦИЯ • НЕ БК"
            }
            val title = textViews.first {
                it.text.toString() == "Матч-день"
            }
            val timeline = textViews.first {
                it.text.toString().startsWith("Сейчас ") &&
                    "Проверить" in it.text.toString()
            }
            val catalog = textViews.first {
                "событ" in it.text.toString() &&
                    "сохранено" in it.text.toString()
            }
            val compactViewport =
                activity.resources.configuration.fontScale < 1.3f &&
                    activity.resources.configuration.screenHeightDp < 840
            if (compactViewport) {
                assertEquals(View.GONE, timeline.visibility)
                assertEquals(View.GONE, catalog.visibility)
            }
            val stack = if (compactViewport) {
                listOf(safety, title)
            } else {
                listOf(safety, title, timeline, catalog)
            }
            val bounds = stack.map { view ->
                Rect().also { visible ->
                    assertTrue(
                        "Briefing item is not visible: ${view.text}",
                        view.getGlobalVisibleRect(visible)
                    )
                    assertTrue(
                        "Briefing item is clipped: ${view.text}, " +
                            "visible=${visible.width()}x${visible.height()}, " +
                            "actual=${view.width}x${view.height}",
                        visible.width() >= view.width - 2 &&
                            visible.height() >= view.height - 2
                    )
                }
            }
            bounds.zipWithNext().forEach { (first, second) ->
                assertTrue(
                    "Briefing items overlap: $first and $second",
                    first.bottom <= second.top
                )
            }
        }
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
