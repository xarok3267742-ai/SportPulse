package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceIntegrityEngineTest {
    @Test
    fun unauditedQuorumIsCappedAtSingleSource() {
        val result = SourceIntegrityEngine.evaluate(
            claimedEvidence = quorumEvidence(),
            audit = SourceAuditAssessment.unaudited()
        )

        assertEquals(SourceIntegrityVerdict.OPEN, result.verdict)
        assertEquals(5, result.claimedQuorumCount)
        assertEquals(0, result.acceptedQuorumCount)
        assertEquals(5, result.unauditedQuorumCount)
        assertEquals(
            List(5) { EvidenceLevel.SINGLE_SOURCE },
            result.effectiveEvidence.levels
        )
        assertEquals(SignalFactor.values().toList(), result.cappedFactors)
    }

    @Test
    fun sharedLineageCannotCreateIndependentQuorum() {
        val audit = SourceAuditAssessment(
            List(5) { SourceAuditState.SHARED_LINEAGE }
        )

        val result = SourceIntegrityEngine.evaluate(
            claimedEvidence = quorumEvidence(),
            audit = audit
        )

        assertEquals(SourceIntegrityVerdict.ECHO, result.verdict)
        assertEquals(5, result.echoQuorumCount)
        assertEquals(0, result.acceptedQuorumCount)
        assertEquals(
            List(5) { EvidenceLevel.SINGLE_SOURCE },
            result.effectiveEvidence.levels
        )
    }

    @Test
    fun independentAuditAcceptsButNeverRaisesClaim() {
        val audit = SourceAuditAssessment(
            List(5) { SourceAuditState.INDEPENDENT }
        )
        val claimed = EvidenceAssessment(
            listOf(
                EvidenceLevel.QUORUM,
                EvidenceLevel.SINGLE_SOURCE,
                EvidenceLevel.UNCONFIRMED,
                EvidenceLevel.QUORUM,
                EvidenceLevel.SINGLE_SOURCE
            )
        )

        val result = SourceIntegrityEngine.evaluate(claimed, audit)

        assertEquals(SourceIntegrityVerdict.AUDITED, result.verdict)
        assertEquals(claimed, result.effectiveEvidence)
        assertEquals(2, result.acceptedQuorumCount)
        assertEquals(emptyList<SignalFactor>(), result.cappedFactors)
    }

    @Test
    fun conflictForcesUnconfirmedEvidence() {
        val audit = SourceAuditAssessment.unaudited()
            .withState(
                SignalFactor.LINEUP,
                SourceAuditState.CONFLICT
            )
        val claimed = EvidenceAssessment.singleSource()
            .withLevel(
                SignalFactor.LINEUP,
                EvidenceLevel.QUORUM
            )

        val result = SourceIntegrityEngine.evaluate(claimed, audit)

        assertEquals(SourceIntegrityVerdict.CONFLICT, result.verdict)
        assertEquals(1, result.conflictCount)
        assertEquals(
            EvidenceLevel.UNCONFIRMED,
            result.effectiveEvidence.level(SignalFactor.LINEUP)
        )
        assertEquals(
            listOf(SignalFactor.LINEUP),
            result.cappedFactors
        )
    }

    @Test
    fun noQuorumClaimDoesNotDemandIndependenceAudit() {
        val result = SourceIntegrityEngine.evaluate(
            claimedEvidence = EvidenceAssessment.singleSource(),
            audit = SourceAuditAssessment.unaudited()
        )

        assertEquals(
            SourceIntegrityVerdict.NO_QUORUM,
            result.verdict
        )
        assertEquals(0, result.claimedQuorumCount)
        assertEquals(
            EvidenceAssessment.singleSource(),
            result.effectiveEvidence
        )
    }

    @Test
    fun updatesAreImmutableAndFingerprintIsDeterministic() {
        val original = SourceAuditAssessment.unaudited()
        val updated = original.withState(
            SignalFactor.SOURCES,
            SourceAuditState.INDEPENDENT
        )
        val first = SourceIntegrityEngine.evaluate(
            quorumEvidence(),
            updated
        )
        val second = SourceIntegrityEngine.evaluate(
            quorumEvidence(),
            updated
        )

        assertEquals(
            SourceAuditState.UNAUDITED,
            original.state(SignalFactor.SOURCES)
        )
        assertEquals(
            SourceAuditState.INDEPENDENT,
            updated.state(SignalFactor.SOURCES)
        )
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(64, first.fingerprint.length)
        assertEquals(8, first.shortFingerprint.length)
        assertNotEquals(
            first.fingerprint,
            SourceIntegrityEngine.evaluate(
                quorumEvidence(),
                original
            ).fingerprint
        )
    }

    @Test
    fun assessmentRequiresExactlyFiveStates() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceAuditAssessment(
                listOf(SourceAuditState.UNAUDITED)
            )
        }
    }

    private fun quorumEvidence(): EvidenceAssessment {
        return EvidenceAssessment(
            List(5) { EvidenceLevel.QUORUM }
        )
    }
}
