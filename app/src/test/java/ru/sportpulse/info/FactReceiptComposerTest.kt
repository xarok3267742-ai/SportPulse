package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactReceiptComposerTest {
    @Test
    fun requiredMinimumAdvancesFromStatementToPrimarySource() {
        val missingStatement = compose(
            statement = "Коротко",
            primarySource = "Официальный сайт"
        )
        val missingPrimary = compose(
            statement = "Проверяемый факт",
            primarySource = ""
        )

        assertEquals(
            FactReceiptComposerStatus.STATEMENT_REQUIRED,
            missingStatement.status
        )
        assertFalse(missingStatement.canSave)
        assertEquals(
            FactReceiptComposerStatus.PRIMARY_SOURCE_REQUIRED,
            missingPrimary.status
        )
        assertFalse(missingPrimary.canSave)
    }

    @Test
    fun oneSourceIsExplicitlyReadyWithoutTechnicalAudit() {
        val result = compose()

        assertEquals(
            FactReceiptComposerStatus.SINGLE_SOURCE_READY,
            result.status
        )
        assertTrue(result.canSave)
        assertEquals(
            SourceAuditState.UNAUDITED,
            result.effectiveAudit
        )
    }

    @Test
    fun enabledCrossCheckRequiresSecondSource() {
        val result = compose(
            includeSecondSource = true,
            secondarySource = ""
        )

        assertEquals(
            FactReceiptComposerStatus.SECONDARY_SOURCE_REQUIRED,
            result.status
        )
        assertFalse(result.canSave)
    }

    @Test
    fun unauditedPairCanSaveButDoesNotClaimQuorum() {
        val result = compose(
            includeSecondSource = true,
            secondarySource = "Независимое издание",
            selectedAudit = SourceAuditState.UNAUDITED
        )

        assertEquals(
            FactReceiptComposerStatus.SOURCE_RELATION_REQUIRED,
            result.status
        )
        assertTrue(result.canSave)
        assertEquals(
            SourceAuditState.UNAUDITED,
            result.effectiveAudit
        )
    }

    @Test
    fun duplicateDomainOverridesIndependentSelection() {
        val result = compose(
            primarySource = "https://club.example/news/1",
            includeSecondSource = true,
            secondarySource = "https://www.club.example/news/2",
            selectedAudit = SourceAuditState.INDEPENDENT
        )

        assertEquals(
            FactReceiptComposerStatus.SHARED_LINEAGE,
            result.status
        )
        assertEquals(
            SourceAuditState.SHARED_LINEAGE,
            result.effectiveAudit
        )
    }

    @Test
    fun independentAndConflictRemainDistinctFinalStates() {
        val independent = compose(
            includeSecondSource = true,
            secondarySource = "Независимое издание",
            selectedAudit = SourceAuditState.INDEPENDENT
        )
        val conflict = compose(
            includeSecondSource = true,
            secondarySource = "Независимое издание",
            selectedAudit = SourceAuditState.CONFLICT
        )

        assertEquals(
            FactReceiptComposerStatus.INDEPENDENT_QUORUM,
            independent.status
        )
        assertEquals(
            FactReceiptComposerStatus.CONFLICT,
            conflict.status
        )
        assertTrue(independent.canSave)
        assertTrue(conflict.canSave)
    }

    private fun compose(
        statement: String = "Проверяемый факт",
        primarySource: String = "Официальный сайт",
        includeSecondSource: Boolean = false,
        secondarySource: String = "",
        selectedAudit: SourceAuditState = SourceAuditState.UNAUDITED
    ): FactReceiptComposerResult {
        return FactReceiptComposer.evaluate(
            statement = statement,
            primarySource = primarySource,
            includeSecondSource = includeSecondSource,
            secondarySource = secondarySource,
            selectedAudit = selectedAudit
        )
    }
}
