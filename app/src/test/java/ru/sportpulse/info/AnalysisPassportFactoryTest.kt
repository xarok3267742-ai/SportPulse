package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPassportFactoryTest {
    private val event = SportEvent(
        id = "match / unsafe:id",
        sport = "Футбол",
        tournament = "Тестовая лига",
        region = "Россия",
        match = "Команда А — Команда Б",
        time = "Сегодня, 19:00",
        focus = "Тест",
        note = "Тест",
        tags = listOf("Тест"),
        imageRes = 0,
        seedAssessment = SignalAssessment(listOf(80, 75, 70, 85, 90))
    )

    @Test
    fun snapshotKeepsInputsAndCalculatedSignal() {
        val assessment = SignalAssessment(listOf(80, 75, 70, 85, 90))
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.OBSERVE,
            generatedAt = 123L
        )

        assertSame(event, snapshot.event)
        assertSame(assessment, snapshot.assessment)
        assertEquals(SignalEngine.evaluate(assessment), snapshot.result)
        assertEquals(null, snapshot.verificationRoute)
        assertEquals(null, snapshot.signalStress)
        assertEquals(null, snapshot.confidenceShadow)
        assertEquals(null, snapshot.decisionCorridor)
        assertEquals(SavedDecision.OBSERVE, snapshot.decision)
        assertEquals(123L, snapshot.generatedAt)
    }

    @Test
    fun fileNameRemovesUnsafePathCharacters() {
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = event.seedAssessment,
            decision = null,
            generatedAt = 123L
        )

        assertEquals(
            "sport_pulse_match_unsafe_id_123.png",
            AnalysisPassportFactory.fileName(snapshot)
        )
    }

    @Test
    fun shareTextCarriesResultAndResponsibleDisclaimer() {
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = event.seedAssessment,
            decision = SavedDecision.DATA_READY,
            generatedAt = 123L
        )
        val text = AnalysisPassportFactory.shareText(snapshot)

        assertTrue(text.contains(event.match))
        assertTrue(text.contains("${snapshot.result.readiness}/100"))
        assertTrue(text.contains("не прогноз"))
    }

    @Test
    fun passportUsesOnlyEvidenceBackedValues() {
        val assessment = SignalAssessment(listOf(90, 90, 90, 90, 90))
        val evidence = EvidenceAssessment(
            List(SignalFactor.values().size) {
                EvidenceLevel.UNCONFIRMED
            }
        )
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.SKIP,
            evidence = evidence,
            generatedAt = 123L
        )

        assertEquals(listOf(25, 25, 25, 25, 25), snapshot.assessment.values)
        assertEquals(SignalVerdict.SKIP, snapshot.result.verdict)
        assertNotNull(snapshot.evidenceResult)
        assertTrue(AnalysisPassportFactory.shareText(snapshot).contains("0/5"))
    }

    @Test
    fun passportCannotBypassSourceIntegrityCaps() {
        val claimed = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        val integrity = SourceIntegrityEngine.evaluate(
            claimedEvidence = claimed,
            audit = SourceAuditAssessment.unaudited()
        )
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = SignalAssessment(List(5) { 90 }),
            decision = SavedDecision.OBSERVE,
            evidence = claimed,
            sourceIntegrity = integrity,
            generatedAt = 123L
        )

        assertEquals(
            List(5) { 60 },
            snapshot.assessment.values
        )
        assertEquals(0, snapshot.evidenceResult?.quorumCount)
        assertSame(integrity, snapshot.sourceIntegrity)
        assertTrue(
            AnalysisPassportFactory.shareText(snapshot)
                .contains("принято 0 из 5")
        )
        assertTrue(
            AnalysisPassportFactory.shareText(snapshot)
                .contains(integrity.shortFingerprint)
        )
    }

    @Test
    fun passportCarriesCounterViewCeilingAndMark() {
        val review = CounterReviewAssessment.unchecked()
            .withState(
                SignalFactor.FORM,
                CounterReviewState.CLEAR
            )
            .withState(
                SignalFactor.LINEUP,
                CounterReviewState.CLEAR
            )
            .withState(
                SignalFactor.LOAD,
                CounterReviewState.CLEAR
            )
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = SignalAssessment(List(5) { 80 }),
            decision = SavedDecision.OBSERVE,
            evidence = EvidenceAssessment(
                List(5) { EvidenceLevel.QUORUM }
            ),
            counterReview = review,
            generatedAt = 123L
        )
        val counterView = requireNotNull(snapshot.counterView)
        val shareText = AnalysisPassportFactory.shareText(
            snapshot
        )

        assertEquals(3, counterView.reviewedCount)
        assertEquals(
            SavedDecision.OBSERVE,
            counterView.decisionCeiling
        )
        assertTrue(shareText.contains("Контрракурс"))
        assertTrue(
            shareText.contains(counterView.shortFingerprint)
        )
        assertTrue(shareText.contains("проверено 3/5"))
    }

    @Test
    fun passportDegradesEvidenceThatPassedItsDeadline() {
        val generatedAt = 100L * FreshnessPolicy.HOUR_MILLIS
        val assessment = SignalAssessment(listOf(90, 90, 90, 90, 90))
        val evidence = EvidenceAssessment(
            List(SignalFactor.values().size) { EvidenceLevel.QUORUM }
        )
        val timeline = EvidenceTimeline(
            List(SignalFactor.values().size) {
                generatedAt - 7L * FreshnessPolicy.HOUR_MILLIS
            }
        )

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.OBSERVE,
            evidence = evidence,
            timeline = timeline,
            generatedAt = generatedAt
        )

        assertEquals(60, snapshot.assessment.value(SignalFactor.LINEUP))
        assertEquals(4, snapshot.evidenceResult?.quorumCount)
        assertEquals(
            listOf(SignalFactor.LINEUP),
            snapshot.freshnessResult?.degradedFactors
        )
        assertTrue(
            AnalysisPassportFactory.shareText(snapshot)
                .contains("устаревшие факторы")
        )
    }

    @Test
    fun passportExplainsCurrentFactsLimit() {
        val assessment = SignalAssessment(listOf(62, 38, 58, 70, 46))
        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.OBSERVE,
            evidence = EvidenceAssessment.singleSource(),
            generatedAt = 123L
        )

        assertEquals(
            VerificationRouteStatus.FACTS_LIMIT,
            snapshot.verificationRoute?.status
        )
        assertTrue(
            AnalysisPassportFactory.shareText(snapshot)
                .contains("Предел текущих фактов: 50/72")
        )
    }

    @Test
    fun passportCarriesDecisionTraceAndControlMark() {
        val savedAt = 100L * FreshnessPolicy.HOUR_MILLIS
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        val timeline = EvidenceTimeline(List(5) { savedAt })
        val baseline = SignalAssessment(List(5) { 60 })
        val decisionSnapshot = DecisionSnapshotFactory.create(
            eventId = event.id,
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = baseline,
            evidence = evidence,
            timeline = timeline
        )
        val current = baseline.withValue(SignalFactor.LINEUP, 80)

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = current,
            decision = SavedDecision.OBSERVE,
            evidence = evidence,
            timeline = timeline,
            decisionSnapshot = decisionSnapshot,
            generatedAt = savedAt
        )

        assertEquals(
            1,
            snapshot.decisionTrace?.changedFactors?.size
        )
        assertTrue(
            AnalysisPassportFactory.shareText(snapshot)
                .contains(decisionSnapshot.shortFingerprint)
        )
    }

    @Test
    fun passportCarriesStressTestAndFreshnessDeadline() {
        val generatedAt = 0L
        val assessment = SignalAssessment(List(5) { 80 })
        val evidence = EvidenceAssessment.singleSource()
        val timeline = EvidenceTimeline(List(5) { generatedAt })

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.OBSERVE,
            evidence = evidence,
            timeline = timeline,
            generatedAt = generatedAt
        )
        val shareText = AnalysisPassportFactory.shareText(snapshot)

        assertEquals(SignalStressStatus.ROBUST, snapshot.signalStress?.status)
        assertEquals(
            24L * FreshnessPolicy.HOUR_MILLIS,
            snapshot.signalStress?.firstVerdictChange?.at
        )
        assertTrue(shareText.contains("один сбой не меняет статус"))
        assertTrue(shareText.contains("статус изменится через 1 д"))
    }

    @Test
    fun passportCarriesConfidenceShadowAndVerdictShift() {
        val assessment = SignalAssessment(List(5) { 90 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.UNCONFIRMED }
        )

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.SKIP,
            evidence = evidence,
            generatedAt = 123L
        )
        val shareText = AnalysisPassportFactory.shareText(snapshot)

        assertEquals(
            ConfidenceShadowStatus.VERDICT_SHIFT,
            snapshot.confidenceShadow?.status
        )
        assertEquals(65, snapshot.confidenceShadow?.readinessGap)
        assertEquals(
            SignalVerdict.SKIP,
            snapshot.confidenceShadow?.supportedSignal?.verdict
        )
        assertTrue(shareText.contains("Тень уверенности: -65"))
        assertTrue(shareText.contains("меняет статус"))
    }

    @Test
    fun passportCarriesNearestDecisionBoundary() {
        val assessment = SignalAssessment(List(5) { 70 })
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = assessment,
            decision = SavedDecision.OBSERVE,
            evidence = evidence,
            generatedAt = 123L
        )
        val corridor = snapshot.decisionCorridor
        val shareText = AnalysisPassportFactory.shareText(snapshot)

        assertNotNull(corridor?.lowerBoundary)
        assertNotNull(corridor?.upperBoundary)
        assertNotNull(corridor?.nearestBoundary)
        assertTrue(shareText.contains("Коридор решения"))
        assertTrue(shareText.contains("ближайшая граница"))
    }

    @Test
    fun passportCarriesFinalizedPostEventReview() {
        val savedAt = 100L * FreshnessPolicy.HOUR_MILLIS
        val evidence = EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
        val timeline = EvidenceTimeline(List(5) { savedAt })
        val decisionSnapshot = DecisionSnapshotFactory.create(
            eventId = event.id,
            decision = SavedDecision.OBSERVE,
            savedAt = savedAt,
            assessment = SignalAssessment(List(5) { 70 }),
            evidence = evidence,
            timeline = timeline
        )
        var review = PostEventReviewFactory.start(
            decisionSnapshot,
            savedAt + 1L
        )
        SignalFactor.values().forEach { factor ->
            review = PostEventReviewFactory.setOutcome(
                review = review,
                snapshot = decisionSnapshot,
                factor = factor,
                outcome = PostEventOutcome.CONFIRMED,
                now = review.updatedAt + 1L
            )
        }
        review = PostEventReviewFactory.finalize(
            review,
            decisionSnapshot,
            review.updatedAt + 1L
        )

        val snapshot = AnalysisPassportFactory.create(
            event = event,
            assessment = decisionSnapshot.assessment,
            decision = decisionSnapshot.decision,
            evidence = evidence,
            timeline = timeline,
            decisionSnapshot = decisionSnapshot,
            postEventReview = review,
            generatedAt = review.updatedAt
        )
        val result = snapshot.postEventReviewResult
        val shareText = AnalysisPassportFactory.shareText(snapshot)

        assertEquals(PostEventReviewStatus.RELIABLE, result?.status)
        assertEquals(100, result?.reliabilityScore)
        assertTrue(shareText.contains("После свистка"))
        assertTrue(shareText.contains(review.shortFingerprint))
    }
}
