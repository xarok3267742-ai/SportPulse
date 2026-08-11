package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionGuardPassportFactoryTest {
    @Test
    fun fileNameIsStableAndFilesystemSafe() {
        val passport = passport(
            eventId = "event/with spaces",
            generatedAt = 123_456L
        )

        assertEquals(
            "sport_pulse_guard_event_with_spaces_123456.png",
            DecisionGuardPassportFactory.fileName(passport)
        )
    }

    @Test
    fun shareTextNamesSealAndCriticalCondition() {
        val passport = passport()
        val condition = requireNotNull(
            passport.guard.plan.condition
        )
        val text = DecisionGuardPassportFactory.shareText(
            passport
        )

        assertTrue(text.contains(passport.guard.plan.shortSeal))
        assertTrue(text.contains(condition.factor.title))
        assertTrue(text.contains("стоп-линия"))
        assertTrue(text.contains("не прогноз"))
        assertTrue(text.contains("не ставка"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun passportRejectsAnotherEvent() {
        val source = passport()

        DecisionGuardPassportFactory.create(
            event = source.event.copy(id = "another_event"),
            guard = source.guard,
            generatedAt = source.generatedAt
        )
    }

    private fun passport(
        eventId: String = "guard_event",
        generatedAt: Long = NOW
    ): DecisionGuardPassport {
        val assessment = SignalAssessment(List(5) { 80 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        val timeline = EvidenceTimeline(List(5) { NOW })
        val snapshot = DecisionSnapshotFactory.create(
            eventId = eventId,
            decision = SavedDecision.DATA_READY,
            savedAt = NOW,
            assessment = assessment,
            evidence = evidence,
            timeline = timeline
        )
        val guard = DecisionGuardEngine.evaluate(
            snapshot = snapshot,
            currentAssessment = assessment,
            currentEvidence = evidence,
            currentTimeline = timeline,
            now = NOW
        )
        return DecisionGuardPassportFactory.create(
            event = SportEvent(
                id = eventId,
                sport = "Футбол",
                tournament = "Тестовая лига",
                region = "Россия",
                match = "Команда А - Команда Б",
                time = "Сегодня, 19:00 МСК",
                focus = "Проверка",
                note = "Тестовое событие",
                tags = listOf("тест"),
                imageRes = 0,
                seedAssessment = assessment
            ),
            guard = guard,
            generatedAt = generatedAt
        )
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
