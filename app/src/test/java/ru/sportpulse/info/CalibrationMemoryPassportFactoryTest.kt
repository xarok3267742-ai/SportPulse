package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationMemoryPassportFactoryTest {
    @Test
    fun passportRequiresAtLeastOneReview() {
        val memory = CalibrationMemoryEngine.evaluate(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            CalibrationMemoryPassportFactory.create(
                memory,
                generatedAt = 123L
            )
        }
    }

    @Test
    fun fileNameIsDeterministicAndSafe() {
        val passport = CalibrationMemoryPassportFactory.create(
            memory(),
            generatedAt = 123L
        )

        assertEquals(
            "sport_pulse_memory_123.png",
            CalibrationMemoryPassportFactory.fileName(passport)
        )
    }

    @Test
    fun shareTextCarriesMetricsChainAndDisclaimer() {
        val passport = CalibrationMemoryPassportFactory.create(
            memory(),
            generatedAt = 123L
        )
        val text = CalibrationMemoryPassportFactory.shareText(
            passport
        )

        assertTrue(text.contains("Разборов: 1"))
        assertTrue(text.contains("Качество данных: 100/100"))
        assertTrue(
            text.contains(passport.memory.shortFingerprint)
        )
        assertTrue(text.contains("финансовые результаты"))
    }

    private fun memory(): CalibrationMemory {
        val savedAt = 1_000L
        val snapshot = DecisionSnapshotFactory.create(
            eventId = "memory_test",
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = SignalAssessment(List(5) { 70 }),
            evidence = EvidenceAssessment.singleSource(),
            timeline = EvidenceTimeline(List(5) { savedAt })
        )
        var review = PostEventReviewFactory.start(
            snapshot,
            savedAt + 1L
        )
        SignalFactor.values().forEach { factor ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = snapshot,
                factor = factor,
                outcome = PostEventOutcome.CONFIRMED,
                now = review.updatedAt + 1L
            )
        }
        review = PostEventReviewFactory.finalize(
            review,
            snapshot,
            review.updatedAt + 1L
        )
        return CalibrationMemoryEngine.evaluate(
            listOf(CalibrationRecord(snapshot, review))
        )
    }
}
