package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionReceiptComposerTest {
    @Test
    fun choiceOnlyPreparesReceiptWithoutWritingAction() {
        val result = compose(selectedDecision = null)

        assertEquals(
            DecisionReceiptStatus.CHOICE_REQUIRED,
            result.status
        )
        assertEquals(DecisionReceiptAction.NONE, result.action)
        assertFalse(result.canAct)
    }

    @Test
    fun safeOutcomesAreReadyForExplicitCommit() {
        val skip = compose(selectedDecision = SavedDecision.SKIP)
        val observe = compose(
            selectedDecision = SavedDecision.OBSERVE,
            decisionCeiling = SavedDecision.OBSERVE
        )

        assertEquals(DecisionReceiptStatus.READY_SKIP, skip.status)
        assertEquals(
            DecisionReceiptStatus.READY_OBSERVE,
            observe.status
        )
        assertEquals(DecisionReceiptAction.COMMIT, skip.action)
        assertEquals(DecisionReceiptAction.COMMIT, observe.action)
        assertTrue(skip.canAct)
        assertTrue(observe.canAct)
    }

    @Test
    fun counterviewPreventsOutcomeAboveItsCeiling() {
        val result = compose(
            selectedDecision = SavedDecision.DATA_READY,
            decisionCeiling = SavedDecision.OBSERVE
        )

        assertEquals(
            DecisionReceiptStatus.COUNTERVIEW_LIMIT,
            result.status
        )
        assertFalse(result.canAct)
        assertTrue(result.body.contains("наблюдать"))
    }

    @Test
    fun strongestOutcomeRequiresAttentionAndDistanceGates() {
        val attention = compose(
            selectedDecision = SavedDecision.DATA_READY,
            attentionBudgetStatus = AttentionBudgetStatus.EXHAUSTED,
            distanceClearanceValid = false
        )
        val distance = compose(
            selectedDecision = SavedDecision.DATA_READY,
            distanceClearanceValid = false
        )
        val ready = compose(
            selectedDecision = SavedDecision.DATA_READY,
            distanceClearanceValid = true
        )

        assertEquals(
            DecisionReceiptStatus.ATTENTION_EXHAUSTED,
            attention.status
        )
        assertEquals(
            DecisionReceiptAction.SHOW_ATTENTION,
            attention.action
        )
        assertEquals(
            DecisionReceiptStatus.DISTANCE_REQUIRED,
            distance.status
        )
        assertEquals(
            DecisionReceiptAction.OPEN_DISTANCE,
            distance.action
        )
        assertEquals(DecisionReceiptStatus.READY_DATA, ready.status)
        assertEquals(DecisionReceiptAction.COMMIT, ready.action)
    }

    @Test
    fun damagedLedgerOffersRepairInsteadOfCommit() {
        val result = compose(
            selectedDecision = SavedDecision.SKIP,
            ledgerIntegrity = DecisionLedgerIntegrity.TAMPERED
        )

        assertEquals(
            DecisionReceiptStatus.LEDGER_TAMPERED,
            result.status
        )
        assertEquals(
            DecisionReceiptAction.SHOW_LEDGER,
            result.action
        )
    }

    @Test
    fun closedWindowsOverrideAnyPreparedChoice() {
        val finalized = compose(
            selectedDecision = SavedDecision.SKIP,
            reviewFinalized = true
        )
        val started = compose(
            selectedDecision = SavedDecision.SKIP,
            decisionWindowOpen = false
        )

        assertEquals(
            DecisionReceiptStatus.REVIEW_FINALIZED,
            finalized.status
        )
        assertEquals(
            DecisionReceiptStatus.WINDOW_CLOSED,
            started.status
        )
        assertFalse(finalized.canAct)
        assertFalse(started.canAct)
    }

    private fun compose(
        selectedDecision: SavedDecision?,
        reviewFinalized: Boolean = false,
        decisionWindowOpen: Boolean = true,
        ledgerIntegrity: DecisionLedgerIntegrity =
            DecisionLedgerIntegrity.INTACT,
        decisionCeiling: SavedDecision = SavedDecision.DATA_READY,
        attentionBudgetStatus: AttentionBudgetStatus =
            AttentionBudgetStatus.OPEN,
        distanceClearanceValid: Boolean = true
    ): DecisionReceiptComposerResult {
        return DecisionReceiptComposer.evaluate(
            selectedDecision = selectedDecision,
            reviewFinalized = reviewFinalized,
            decisionWindowOpen = decisionWindowOpen,
            ledgerIntegrity = ledgerIntegrity,
            decisionCeiling = decisionCeiling,
            attentionBudgetStatus = attentionBudgetStatus,
            distanceClearanceValid = distanceClearanceValid
        )
    }
}
