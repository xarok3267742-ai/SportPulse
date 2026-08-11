package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationRecipeEngineTest {
    @Test
    fun everyFactorProducesThreeCompleteSteps() {
        SignalFactor.values().forEach { factor ->
            val recipe = VerificationRecipeEngine.create(
                factor = factor,
                evidenceLevel = EvidenceLevel.UNCONFIRMED
            )

            assertEquals(factor, recipe.factor)
            assertEquals(3, recipe.steps.size)
            assertTrue(recipe.question.isNotBlank())
            assertTrue(recipe.steps.all { it.body.isNotBlank() })
            assertEquals(64, recipe.fingerprint.length)
        }
    }

    @Test
    fun sourceRecipeRejectsEchoAsQuorum() {
        val recipe = VerificationRecipeEngine.create(
            factor = SignalFactor.SOURCES,
            evidenceLevel = EvidenceLevel.SINGLE_SOURCE
        )

        assertTrue(
            recipe.steps.any {
                it.body.contains("не образуют кворум")
            }
        )
        assertTrue(recipe.completionRule.contains("независимым"))
    }

    @Test
    fun evidenceLevelChangesCompletionRuleAndFingerprint() {
        val unconfirmed = VerificationRecipeEngine.create(
            SignalFactor.LINEUP,
            EvidenceLevel.UNCONFIRMED
        )
        val quorum = VerificationRecipeEngine.create(
            SignalFactor.LINEUP,
            EvidenceLevel.QUORUM
        )

        assertNotEquals(
            unconfirmed.completionRule,
            quorum.completionRule
        )
        assertNotEquals(
            unconfirmed.fingerprint,
            quorum.fingerprint
        )
    }

    @Test
    fun sameInputProducesStableFingerprint() {
        val first = VerificationRecipeEngine.create(
            SignalFactor.LOAD,
            EvidenceLevel.SINGLE_SOURCE
        )
        val second = VerificationRecipeEngine.create(
            SignalFactor.LOAD,
            EvidenceLevel.SINGLE_SOURCE
        )

        assertEquals(first.fingerprint, second.fingerprint)
    }
}
