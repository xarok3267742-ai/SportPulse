package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactTimeSliceTest {
    @Test
    fun fewerThanTwoActiveReceiptsIsInsufficient() {
        val empty = FactTimeSliceEngine.create(register())
        val one = FactTimeSliceEngine.create(
            register(
                SignalFactor.FORM to valid(
                    receipt(SignalFactor.FORM, NOW)
                )
            )
        )

        assertEquals(FactTimeSliceStatus.INSUFFICIENT, empty.status)
        assertEquals(FactTimeSliceStatus.INSUFFICIENT, one.status)
        assertEquals(1, one.activeCount)
        assertNull(one.spreadMillis)
        assertNull(one.suggestedFactor)
    }

    @Test
    fun closeChecksCreateAlignedSlice() {
        val slice = slice(
            formCheckedAt = NOW - FreshnessPolicy.HOUR_MILLIS,
            lineupCheckedAt = NOW
        )

        assertEquals(FactTimeSliceStatus.ALIGNED, slice.status)
        assertEquals(FreshnessPolicy.HOUR_MILLIS, slice.spreadMillis)
        assertEquals(
            FreshnessPolicy.validForMillis(SignalFactor.LINEUP) / 4L,
            slice.syncWindowMillis
        )
        assertEquals(SignalFactor.LINEUP, slice.referenceFactor)
        assertNull(slice.suggestedFactor)
    }

    @Test
    fun moderateSpreadCreatesVisibleDrift() {
        val slice = slice(
            formCheckedAt = NOW - 2L * FreshnessPolicy.HOUR_MILLIS,
            lineupCheckedAt = NOW
        )

        assertEquals(FactTimeSliceStatus.DRIFTING, slice.status)
        assertEquals(SignalFactor.FORM, slice.oldestFactor)
        assertEquals(SignalFactor.LINEUP, slice.newestFactor)
        assertEquals(SignalFactor.FORM, slice.suggestedFactor)
    }

    @Test
    fun wideSpreadCreatesSplitAndSelectsOldestFact() {
        val slice = slice(
            formCheckedAt = NOW - 4L * FreshnessPolicy.HOUR_MILLIS,
            lineupCheckedAt = NOW
        )

        assertEquals(FactTimeSliceStatus.SPLIT, slice.status)
        assertEquals(SignalFactor.FORM, slice.suggestedFactor)
    }

    @Test
    fun expiredReceiptDoesNotPretendToBeAnActiveTimePoint() {
        val lineupWindow = FreshnessPolicy.validForMillis(
            SignalFactor.LINEUP
        )
        val slice = FactTimeSliceEngine.create(
            register(
                SignalFactor.FORM to valid(
                    receipt(SignalFactor.FORM, NOW)
                ),
                SignalFactor.LINEUP to valid(
                    receipt(
                        SignalFactor.LINEUP,
                        NOW - 2L * lineupWindow
                    )
                )
            )
        )

        assertEquals(FactTimeSliceStatus.INSUFFICIENT, slice.status)
        assertEquals(listOf(SignalFactor.FORM), slice.points.map { it.factor })
    }

    @Test
    fun fingerprintChangesWithUnderlyingReceipt() {
        val first = slice(
            formCheckedAt = NOW - FreshnessPolicy.HOUR_MILLIS,
            lineupCheckedAt = NOW
        )
        val changed = FactTimeSliceEngine.create(
            register(
                SignalFactor.FORM to valid(
                    receipt(
                        factor = SignalFactor.FORM,
                        checkedAt = NOW - FreshnessPolicy.HOUR_MILLIS,
                        statement = "Подтверждён другой проверяемый факт события"
                    )
                ),
                SignalFactor.LINEUP to valid(
                    receipt(SignalFactor.LINEUP, NOW)
                )
            )
        )

        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    private fun slice(
        formCheckedAt: Long,
        lineupCheckedAt: Long
    ): FactTimeSlice {
        return FactTimeSliceEngine.create(
            register(
                SignalFactor.FORM to valid(
                    receipt(SignalFactor.FORM, formCheckedAt)
                ),
                SignalFactor.LINEUP to valid(
                    receipt(SignalFactor.LINEUP, lineupCheckedAt)
                )
            )
        )
    }

    private fun register(
        vararg overrides: Pair<SignalFactor, FactReceiptReadResult>
    ): FactRegister {
        val reads = SignalFactor.values().associateWith {
            FactReceiptReadResult(
                integrity = FactReceiptIntegrity.EMPTY,
                receipt = null
            )
        }.toMutableMap()
        overrides.forEach { (factor, read) -> reads[factor] = read }
        return FactRegisterEngine.create(
            eventId = EVENT_ID,
            reads = reads,
            now = NOW
        )
    }

    private fun valid(receipt: FactReceipt): FactReceiptReadResult {
        return FactReceiptReadResult(
            integrity = FactReceiptIntegrity.VALID,
            receipt = receipt
        )
    }

    private fun receipt(
        factor: SignalFactor,
        checkedAt: Long,
        statement: String = "Подтверждён проверяемый факт выбранного события"
    ): FactReceipt {
        return FactReceiptFactory.create(
            eventId = EVENT_ID,
            factor = factor,
            statement = statement,
            primarySource =
                "https://${factor.name.lowercase()}.example/report",
            secondarySource =
                "https://league.example/${factor.name.lowercase()}",
            sourceAuditState = SourceAuditState.INDEPENDENT,
            coverage = FactReceiptCoverage.DETAILS,
            checkedAt = checkedAt
        )
    }

    private companion object {
        const val EVENT_ID = "api_football_9101"
        const val NOW = 1_780_000_000_000L
    }
}
