package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseLabNavigatorEngineTest {
    @Test
    fun unknownStoredSectionFallsBackToRoute() {
        assertEquals(
            PulseLabSection.ROUTE,
            PulseLabSection.fromStored("REMOVED")
        )
    }

    @Test
    fun incompleteIndependentEvidenceRoutesToFacts() {
        val result = evaluate(
            confirmed = 5,
            independent = 2
        )

        assertEquals(PulseLabSection.FACTS, result.recommendedSection)
        assertTrue(result.body.contains("2 из 5"))
        assertEquals("СВЕРЕНО 2 ИЗ 5", result.status(PulseLabSection.FACTS))
    }

    @Test
    fun completeEvidenceWithoutDecisionRoutesToDecision() {
        val result = evaluate(
            confirmed = 5,
            independent = 5
        )

        assertEquals(PulseLabSection.DECISION, result.recommendedSection)
        assertEquals(
            "НЕ СОЗДАНО",
            result.status(PulseLabSection.DECISION)
        )
    }

    @Test
    fun startedEventRoutesToHonestDecisionReview() {
        val result = evaluate(
            confirmed = 5,
            independent = 5,
            hasDecision = false,
            eventStarted = true
        )

        assertEquals(PulseLabSection.DECISION, result.recommendedSection)
        assertTrue(result.body.contains("задним числом"))
        assertEquals("НЕТ ЗАПИСИ", result.status(PulseLabSection.DECISION))
    }

    @Test
    fun savedDecisionBeforeStartReturnsToRoute() {
        val result = evaluate(
            confirmed = 5,
            independent = 5,
            hasDecision = true
        )

        assertEquals(PulseLabSection.ROUTE, result.recommendedSection)
    }

    @Test
    fun finalizedReviewReturnsToCompletedRoute() {
        val result = evaluate(
            confirmed = 5,
            independent = 5,
            hasDecision = true,
            eventStarted = true,
            reviewFinalized = true
        )

        assertEquals(PulseLabSection.ROUTE, result.recommendedSection)
        assertEquals("Цикл проверки завершён", result.headline)
        assertEquals(
            "РАЗБОР ГОТОВ",
            result.status(PulseLabSection.DECISION)
        )
    }

    private fun evaluate(
        confirmed: Int,
        independent: Int,
        hasDecision: Boolean = false,
        eventStarted: Boolean = false,
        reviewFinalized: Boolean = false
    ): PulseLabNavigatorSummary {
        return PulseLabNavigatorEngine.evaluate(
            PulseLabNavigatorInput(
                confirmedFactorCount = confirmed,
                independentlyVerifiedCount = independent,
                totalFactorCount = 5,
                hasDecisionSnapshot = hasDecision,
                eventStarted = eventStarted,
                reviewFinalized = reviewFinalized
            )
        )
    }
}
