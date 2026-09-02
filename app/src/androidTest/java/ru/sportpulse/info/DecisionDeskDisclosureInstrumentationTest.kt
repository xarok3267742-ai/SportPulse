package ru.sportpulse.info

import android.content.Context
import android.os.SystemClock
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
class DecisionDeskDisclosureInstrumentationTest {
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
    fun overviewLeadsToOneFocusedWorkspaceAndRemembersDisclosure() {
        clickText("Штаб")

        assertTextPresent("ВОПРОСЫ • 0/3")
        assertTextPresent("Идея ещё не проверена")
        assertTextPresent("Карта данных")
        assertTextAbsent("Рабочая форма")
        assertTextAbsent("Развилка матча")

        clickText("Записать идею матча")
        assertTextPresent("Рабочая форма")
        assertTextPresent("Развилка матча")
        assertEventuallyFocusedInput()

        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextPresent("Рабочая форма")

        clickText("Свернуть рабочую форму")
        assertTextAbsent("Рабочая форма")
        assertTextAbsent("Развилка матча")

        scenario.recreate()
        instrumentation.waitForIdleSync()
        assertTextAbsent("Рабочая форма")
        assertTextPresent("Карта данных")
    }

    @Test
    fun factorActionClosesWorkspaceAndOpensTheRequestedAuditSection() {
        scenario.onActivity { activity ->
            val eventId = requireNotNull(
                UserStateStore(activity).selectedEventId
            )
            UserStateStore(activity).saveDecisionDeskDraft(
                DecisionDeskDraftFactory.create(
                    eventId = eventId,
                    marketKind = MarketKind.ONE_X_TWO,
                    thesis = "Высокий темп сохранится после перерыва",
                    counterargument =
                        "Гости замедлят игру позиционными атаками",
                    stopCondition =
                        "Темп заметно падает в первые десять минут",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        scenario.recreate()
        instrumentation.waitForIdleSync()

        clickText("Штаб")
        assertTextPresent("Открыть рабочую форму")
        assertTextAbsent("Рабочая форма")
        clickTextStartingWith("Проверить:")

        assertTextPresent("Навигатор подробного разбора")
        assertTextPresent("Ручная оценка проверки")
        assertTextAbsent("Рабочая форма")
    }

    @Test
    fun profileShowsOnlyExactlyLinkedReviewCoverage() {
        scenario.onActivity { activity ->
            val store = UserStateStore(activity)
            val now = System.currentTimeMillis()
            val first = store.saveDecision(
                eventId = "profile-event-1",
                eventLabel = "Первый матч",
                decision = SavedDecision.SKIP,
                assessment = SignalAssessment(List(5) { 70 }),
                evidence = EvidenceAssessment(
                    List(5) { EvidenceLevel.SINGLE_SOURCE }
                ),
                timeline = EvidenceTimeline(List(5) { now }),
                counterReview = CounterReviewAssessment.unchecked(),
                savedAt = now
            )
            var review = PostEventReviewFactory.start(
                snapshot = first,
                now = now + 1L
            )
            SignalFactor.values().forEach { factor ->
                review = PostEventReviewFactory.setOutcome(
                    review = review,
                    snapshot = first,
                    factor = factor,
                    outcome = PostEventOutcome.CONFIRMED,
                    now = review.updatedAt + 1L
                )
            }
            review = PostEventReviewFactory.finalize(
                review = review,
                snapshot = first,
                now = review.updatedAt + 1L
            )
            store.savePostEventReview(review)
            store.saveDecision(
                eventId = "profile-event-2",
                eventLabel = "Второй матч",
                decision = SavedDecision.SKIP,
                assessment = SignalAssessment(List(5) { 65 }),
                evidence = EvidenceAssessment(
                    List(5) { EvidenceLevel.SINGLE_SOURCE }
                ),
                timeline = EvidenceTimeline(List(5) { now + 10L }),
                counterReview = CounterReviewAssessment.unchecked(),
                savedAt = now + 10L
            )
        }
        scenario.recreate()
        instrumentation.waitForIdleSync()

        clickText("Штаб")
        clickContentDescription("Штаб: Профиль")

        assertTextPresent("Замкнут цикл • 50%")
        assertTextPresent("Связано с завершённым разбором того же снимка: 1 из 2 решений в доступном окне.")
        assertTextPresent("Добавить разбор после матча")
        assertContentDescriptionStartingWith("Цикл дисциплины")
    }

    private fun assertEventuallyFocusedInput() {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        var focused = false
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                focused = descendants(activity.window.decorView)
                    .filterIsInstance<EditText>()
                    .any { it.hasFocus() }
            }
            if (!focused) Thread.sleep(16L)
        } while (
            !focused &&
            SystemClock.uptimeMillis() < deadline
        )
        assertTrue("Primary action did not focus the first missing field", focused)
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

    private fun clickTextStartingWith(prefix: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
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

    private fun clickContentDescription(description: String) {
        scenario.onActivity { activity ->
            descendants(activity.window.decorView)
                .firstOrNull {
                    it.contentDescription?.toString() == description &&
                        it.isClickable
                }
                ?.performClick()
                ?: error(
                    "Clickable content description not found: " +
                        description
                )
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

    private fun assertTextAbsent(title: String) {
        scenario.onActivity { activity ->
            assertTrue(
                "Unexpected text found: $title",
                descendants(activity.window.decorView)
                    .filterIsInstance<TextView>()
                    .none { it.text.toString() == title }
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
