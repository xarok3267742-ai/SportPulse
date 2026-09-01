package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioForkTest {
    private val now = 1_750_000_000_000L

    @Test
    fun emptyDraftStartsWithPrimaryScenario() {
        val result = ScenarioForkEngine.evaluate(
            draft = draft(thesis = "", alternative = "", stop = ""),
            decision = decision(
                status = DecisionDeskStatus.STOP,
                missing = listOf(
                    DecisionDeskField.THESIS,
                    DecisionDeskField.COUNTERARGUMENT,
                    DecisionDeskField.STOP_CONDITION
                )
            )
        )

        assertEquals(
            ScenarioForkState.PRIMARY_REQUIRED,
            result.state
        )
        assertEquals(DecisionDeskField.THESIS, result.targetField)
        assertTrue(result.primaryScenario.contains("не сформулирован"))
        assertNull(result.distinguishingFactor)
    }

    @Test
    fun completeStoriesRequestDistinguishingFactor() {
        val result = ScenarioForkEngine.evaluate(
            draft = draft(),
            decision = decision(
                status = DecisionDeskStatus.OBSERVE,
                factor = SignalFactor.LINEUP
            )
        )

        assertEquals(
            ScenarioForkState.EVIDENCE_REQUIRED,
            result.state
        )
        assertEquals(SignalFactor.LINEUP, result.distinguishingFactor)
        assertTrue(result.explanation.contains("Состав"))
        assertNull(result.targetField)
    }

    @Test
    fun factsReadyClosesForkWithoutPredictingOutcome() {
        val result = ScenarioForkEngine.evaluate(
            draft = draft(),
            decision = decision(
                status = DecisionDeskStatus.FACTS_READY
            )
        )

        assertEquals(ScenarioForkState.VERIFIED, result.state)
        assertTrue(result.explanation.contains("не прогноз"))
        assertNull(result.distinguishingFactor)
    }

    @Test
    fun completeBlockedDecisionRemainsStop() {
        val result = ScenarioForkEngine.evaluate(
            draft = draft(),
            decision = decision(
                status = DecisionDeskStatus.STOP,
                factor = SignalFactor.LINEUP
            )
        )

        assertEquals(ScenarioForkState.BLOCKED, result.state)
        assertEquals("Развилка остановлена", result.headline)
        assertNull(result.distinguishingFactor)
    }

    private fun draft(
        thesis: String = "Хозяева сохраняют темп после перерыва",
        alternative: String = "Ротация снижает интенсивность",
        stop: String = "Два игрока основы не выходят"
    ): DecisionDeskDraft {
        return DecisionDeskDraftFactory.create(
            eventId = "event-1",
            marketKind = MarketKind.TOTAL,
            thesis = thesis,
            counterargument = alternative,
            stopCondition = stop,
            updatedAt = now
        )
    }

    private fun decision(
        status: DecisionDeskStatus,
        missing: List<DecisionDeskField> = emptyList(),
        factor: SignalFactor? = null
    ): DecisionDeskResult {
        return DecisionDeskResult(
            status = status,
            missingFields = missing,
            marketStatus = MarketLensStatus.CHECK,
            counterVerdict = CounterViewVerdict.OPEN,
            nextFactor = factor,
            headline = "Проверка",
            explanation = "Тестовое состояние",
            actionTitle = "Продолжить"
        )
    }
}
