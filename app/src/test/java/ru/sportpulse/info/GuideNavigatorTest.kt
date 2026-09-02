package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideNavigatorTest {
    @Test
    fun navigatorMovesThroughAllFiveEvidenceStages() {
        val event = mission(hasSelectedEvent = false)
        val plan = mission()
        val fact = mission(missingPlanFields = emptyList())
        val decision = mission(
            missingPlanFields = emptyList(),
            recordedFactCount = 1
        )
        val review = mission(
            missingPlanFields = emptyList(),
            recordedFactCount = 1,
            hasDecision = true
        )
        val complete = mission(
            missingPlanFields = emptyList(),
            recordedFactCount = 1,
            hasDecision = true,
            reviewFinalized = true
        )

        assertEquals(GuideNavigatorStage.EVENT, event.stage)
        assertEquals(GuideNavigatorStage.PLAN, plan.stage)
        assertEquals(GuideNavigatorStage.FACT, fact.stage)
        assertEquals(GuideNavigatorStage.DECISION, decision.stage)
        assertEquals(GuideNavigatorStage.REVIEW, review.stage)
        assertEquals(GuideNavigatorStage.COMPLETE, complete.stage)
        assertEquals(5, complete.completedSteps)
        assertEquals("Пройдено 5 из 5 шагов", complete.progressDescription)
    }

    @Test
    fun planPointsToTheFirstMissingQuestion() {
        val mission = mission(
            missingPlanFields = listOf(
                DecisionDeskField.COUNTERARGUMENT,
                DecisionDeskField.STOP_CONDITION
            ),
            recordedFactCount = 2
        )

        assertEquals(GuideNavigatorStage.PLAN, mission.stage)
        assertEquals(
            DecisionDeskField.COUNTERARGUMENT,
            mission.planField
        )
        assertEquals("Записать возражение", mission.actionTitle)
        assertNull(mission.factor)
    }

    @Test
    fun factUsesTheComputedWeakFactor() {
        val mission = mission(
            missingPlanFields = emptyList(),
            nextFactor = SignalFactor.LINEUP
        )

        assertEquals(GuideNavigatorStage.FACT, mission.stage)
        assertEquals(SignalFactor.LINEUP, mission.factor)
        assertEquals("Открыть «Состав»", mission.actionTitle)
    }

    @Test
    fun savedDecisionTakesPriorityOverChangedDraftAndFacts() {
        val mission = mission(
            missingPlanFields = DecisionDeskField.values().toList(),
            recordedFactCount = 0,
            hasDecision = true
        )

        assertEquals(GuideNavigatorStage.REVIEW, mission.stage)
        assertEquals(4, mission.completedSteps)
    }

    private fun mission(
        hasSelectedEvent: Boolean = true,
        missingPlanFields: List<DecisionDeskField> =
            DecisionDeskField.values().toList(),
        recordedFactCount: Int = 0,
        hasDecision: Boolean = false,
        reviewFinalized: Boolean = false,
        nextFactor: SignalFactor = SignalFactor.FORM
    ): GuideNavigatorMission {
        return GuideNavigatorEngine.evaluate(
            GuideNavigatorInput(
                hasSelectedEvent = hasSelectedEvent,
                missingPlanFields = missingPlanFields,
                recordedFactCount = recordedFactCount,
                hasDecision = hasDecision,
                reviewFinalized = reviewFinalized,
                nextFactor = nextFactor
            )
        )
    }
}
