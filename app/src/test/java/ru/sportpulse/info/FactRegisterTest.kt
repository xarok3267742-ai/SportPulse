package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactRegisterTest {
    @Test
    fun emptyRegisterHasFiveOrderedEntriesAndStableFingerprint() {
        val register = register()
        val again = register()

        assertEquals(FactRegisterStatus.EMPTY, register.status)
        assertEquals(
            SignalFactor.values().toList(),
            register.entries.map(FactRegisterEntry::factor)
        )
        assertEquals(0, register.validCount)
        assertEquals(SignalFactor.FORM, register.nextFactor)
        assertEquals(register.fingerprint, again.fingerprint)
    }

    @Test
    fun oneReceiptCreatesPartialRegister() {
        val form = receipt(
            factor = SignalFactor.FORM,
            secondarySource = null,
            audit = SourceAuditState.UNAUDITED
        )
        val register = register(
            SignalFactor.FORM to valid(form)
        )

        assertEquals(FactRegisterStatus.PARTIAL, register.status)
        assertEquals(1, register.validCount)
        assertEquals(0, register.quorumCount)
        assertEquals(
            FactRegisterEntryState.SINGLE_SOURCE,
            register.entries.first().state
        )
        assertEquals(SignalFactor.LINEUP, register.nextFactor)
    }

    @Test
    fun conflictGetsAttentionBeforeEmptyFactors() {
        val conflict = receipt(
            factor = SignalFactor.LOAD,
            secondarySource = "https://league.example/load",
            audit = SourceAuditState.CONFLICT
        )
        val register = register(
            SignalFactor.LOAD to valid(conflict)
        )

        assertEquals(FactRegisterStatus.ATTENTION, register.status)
        assertEquals(1, register.issueCount)
        assertEquals(SignalFactor.LOAD, register.nextFactor)
    }

    @Test
    fun tamperedReceiptHasHighestPriority() {
        val conflict = receipt(
            factor = SignalFactor.FORM,
            secondarySource = "https://league.example/form",
            audit = SourceAuditState.CONFLICT
        )
        val register = register(
            SignalFactor.FORM to valid(conflict),
            SignalFactor.SOURCES to FactReceiptReadResult(
                integrity = FactReceiptIntegrity.TAMPERED,
                receipt = null
            )
        )

        assertEquals(FactRegisterStatus.ATTENTION, register.status)
        assertEquals(SignalFactor.SOURCES, register.nextFactor)
    }

    @Test
    fun sharedLineageIsVisibleAndDoesNotCreateQuorum() {
        val shared = receipt(
            factor = SignalFactor.CONTEXT,
            primarySource = "https://club.example/a",
            secondarySource = "https://club.example/b",
            audit = SourceAuditState.INDEPENDENT
        )
        val register = register(
            SignalFactor.CONTEXT to valid(shared)
        )

        assertEquals(
            FactRegisterEntryState.SHARED_LINEAGE,
            register.entries.first {
                it.factor == SignalFactor.CONTEXT
            }.state
        )
        assertEquals(0, register.quorumCount)
    }

    @Test
    fun fiveIndependentReceiptsCreateReadyRegister() {
        val register = fullIndependentRegister()

        assertEquals(FactRegisterStatus.READY, register.status)
        assertEquals(5, register.validCount)
        assertEquals(5, register.quorumCount)
        assertEquals(0, register.issueCount)
        assertNull(register.nextFactor)
    }

    @Test
    fun expiringQuorumCreatesProactiveRegisterStatus() {
        val lineupWindow = FreshnessPolicy.validForMillis(
            SignalFactor.LINEUP
        )
        val register = fullIndependentRegister(
            checkedAtOverrides = mapOf(
                SignalFactor.LINEUP to
                    (NOW - lineupWindow * 3L / 4L)
            )
        )

        assertEquals(FactRegisterStatus.EXPIRING, register.status)
        assertEquals(5, register.quorumCount)
        assertEquals(1, register.expiringCount)
        assertEquals(SignalFactor.LINEUP, register.nextFactor)
        assertEquals(
            FreshnessStatus.EXPIRING,
            register.entries.first {
                it.factor == SignalFactor.LINEUP
            }.freshness?.status
        )
    }

    @Test
    fun agedQuorumDegradesAndStopsCountingAsCurrentQuorum() {
        val lineupWindow = FreshnessPolicy.validForMillis(
            SignalFactor.LINEUP
        )
        val register = fullIndependentRegister(
            checkedAtOverrides = mapOf(
                SignalFactor.LINEUP to (NOW - lineupWindow)
            )
        )

        assertEquals(FactRegisterStatus.PARTIAL, register.status)
        assertEquals(4, register.quorumCount)
        assertEquals(1, register.degradedCount)
        assertEquals(0, register.expiredCount)
        assertEquals(SignalFactor.LINEUP, register.nextFactor)
        assertEquals(
            EvidenceLevel.SINGLE_SOURCE,
            register.entries.first {
                it.factor == SignalFactor.LINEUP
            }.freshness?.effectiveLevel
        )
    }

    @Test
    fun expiredReceiptGetsAttentionBeforeFreshFactors() {
        val lineupWindow = FreshnessPolicy.validForMillis(
            SignalFactor.LINEUP
        )
        val register = fullIndependentRegister(
            checkedAtOverrides = mapOf(
                SignalFactor.LINEUP to
                    (NOW - lineupWindow * 2L)
            )
        )

        assertEquals(FactRegisterStatus.ATTENTION, register.status)
        assertEquals(4, register.quorumCount)
        assertEquals(1, register.expiredCount)
        assertEquals(1, register.issueCount)
        assertEquals(SignalFactor.LINEUP, register.nextFactor)
    }

    @Test
    fun fingerprintChangesOnlyWhenFreshnessStageChanges() {
        val lineup = receipt(
            factor = SignalFactor.LINEUP,
            secondarySource = "https://league.example/lineup",
            audit = SourceAuditState.INDEPENDENT,
            checkedAt = CHECKED_AT
        )
        val fresh = registerAt(
            CHECKED_AT + FreshnessPolicy.HOUR_MILLIS,
            SignalFactor.LINEUP to valid(lineup)
        )
        val sameStage = registerAt(
            CHECKED_AT + 2L * FreshnessPolicy.HOUR_MILLIS,
            SignalFactor.LINEUP to valid(lineup)
        )
        val degraded = registerAt(
            CHECKED_AT + FreshnessPolicy.validForMillis(
                SignalFactor.LINEUP
            ),
            SignalFactor.LINEUP to valid(lineup)
        )

        assertEquals(fresh.fingerprint, sameStage.fingerprint)
        assertNotEquals(fresh.fingerprint, degraded.fingerprint)
    }

    @Test
    fun registerFingerprintChangesWhenReceiptChanges() {
        val first = receipt(
            factor = SignalFactor.LINEUP,
            coverage = FactReceiptCoverage.CORE
        )
        val changed = receipt(
            factor = SignalFactor.LINEUP,
            coverage = FactReceiptCoverage.DETAILS
        )

        assertNotEquals(
            register(SignalFactor.LINEUP to valid(first)).fingerprint,
            register(SignalFactor.LINEUP to valid(changed)).fingerprint
        )
    }

    private fun register(
        vararg overrides: Pair<SignalFactor, FactReceiptReadResult>
    ): FactRegister = registerAt(NOW, *overrides)

    private fun registerAt(
        now: Long,
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
            now = now
        )
    }

    private fun fullIndependentRegister(
        checkedAtOverrides: Map<SignalFactor, Long> = emptyMap()
    ): FactRegister {
        val pairs = SignalFactor.values().map { factor ->
            factor to valid(
                receipt(
                    factor = factor,
                    primarySource =
                        "https://${factor.name.lowercase()}.example/a",
                    secondarySource =
                        "https://league.example/${factor.name.lowercase()}",
                    audit = SourceAuditState.INDEPENDENT,
                    checkedAt = checkedAtOverrides[factor]
                        ?: CHECKED_AT
                )
            )
        }.toTypedArray()
        return registerAt(NOW, *pairs)
    }

    private fun valid(receipt: FactReceipt): FactReceiptReadResult {
        return FactReceiptReadResult(
            integrity = FactReceiptIntegrity.VALID,
            receipt = receipt
        )
    }

    private fun receipt(
        factor: SignalFactor,
        statement: String = "Подтверждён проверяемый факт выбранного события",
        primarySource: String = "https://club.example/report",
        secondarySource: String? = null,
        audit: SourceAuditState = SourceAuditState.UNAUDITED,
        coverage: FactReceiptCoverage = FactReceiptCoverage.CORE,
        checkedAt: Long = CHECKED_AT
    ): FactReceipt {
        return FactReceiptFactory.create(
            eventId = EVENT_ID,
            factor = factor,
            statement = statement,
            primarySource = primarySource,
            secondarySource = secondarySource,
            sourceAuditState = audit,
            coverage = coverage,
            checkedAt = checkedAt
        )
    }

    private companion object {
        const val EVENT_ID = "api_football_9101"
        const val CHECKED_AT = 1_780_000_000_000L
        const val NOW = CHECKED_AT + FreshnessPolicy.HOUR_MILLIS
    }
}
