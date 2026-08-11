package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossSourceMapTest {
    @Test
    fun emptyRegisterCreatesEmptySourceMap() {
        val map = CrossSourceMapEngine.create(register())

        assertEquals(CrossSourceStatus.EMPTY, map.status)
        assertEquals(0, map.sourceMentionCount)
        assertEquals(0, map.uniqueOriginCount)
        assertNull(map.dominantOrigin)
        assertNull(map.diversificationFactor)
    }

    @Test
    fun oneCoveredFactorKeepsMapInBuildingState() {
        val map = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.FORM,
                    primary = "https://club.example/form",
                    secondary = "https://league.example/form"
                )
            )
        )

        assertEquals(CrossSourceStatus.BUILDING, map.status)
        assertEquals(2, map.sourceMentionCount)
        assertEquals(2, map.uniqueOriginCount)
        assertEquals(1, map.coveredFactorCount)
    }

    @Test
    fun distinctOriginsAcrossFactorsAreDistributed() {
        val map = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.FORM,
                    primary = "https://club.example/form",
                    secondary = "https://league.example/form"
                ),
                receipt(
                    factor = SignalFactor.LINEUP,
                    primary = "https://team.example/lineup",
                    secondary = "https://press.example/lineup"
                )
            )
        )

        assertEquals(CrossSourceStatus.DISTRIBUTED, map.status)
        assertEquals(4, map.uniqueOriginCount)
        assertEquals(0, map.reusedOriginCount)
        assertNull(map.diversificationFactor)
    }

    @Test
    fun repeatedHostAcrossFactorsCreatesCrossEcho() {
        val map = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.FORM,
                    primary = "https://www.club.example/form",
                    secondary = "https://league.example/form"
                ),
                receipt(
                    factor = SignalFactor.LINEUP,
                    primary = "https://club.example/lineup",
                    secondary = "https://press.example/lineup"
                )
            )
        )

        assertEquals(CrossSourceStatus.REUSED, map.status)
        assertEquals(1, map.reusedOriginCount)
        assertEquals(2, map.reusedFactorCount)
        assertEquals("club.example", map.dominantOrigin?.identity)
        assertEquals(
            listOf(SignalFactor.FORM, SignalFactor.LINEUP),
            map.dominantOrigin?.factors
        )
        assertEquals(SignalFactor.FORM, map.diversificationFactor)
    }

    @Test
    fun duplicateWithinOneFactorIsNotCrossFactorEcho() {
        val map = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.CONTEXT,
                    primary = "https://club.example/a",
                    secondary = "https://club.example/b"
                )
            )
        )

        assertEquals(CrossSourceStatus.BUILDING, map.status)
        assertEquals(1, map.uniqueOriginCount)
        assertEquals(2, map.sourceMentionCount)
        assertEquals(0, map.reusedOriginCount)
    }

    @Test
    fun normalizedTextLabelsCanRevealCrossEcho() {
        val map = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.LOAD,
                    primary = "Официальный сайт клуба",
                    secondary = null
                ),
                receipt(
                    factor = SignalFactor.CONTEXT,
                    primary = "официальный---сайт клуба",
                    secondary = null
                )
            )
        )

        assertEquals(CrossSourceStatus.REUSED, map.status)
        assertEquals(1, map.uniqueOriginCount)
        assertEquals(
            "Официальный сайт клуба",
            map.dominantOrigin?.label
        )
    }

    @Test
    fun fingerprintIsStableAndChangesWithOriginMap() {
        val first = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.SOURCES,
                    primary = "https://source-a.example/report",
                    secondary = null
                )
            )
        )
        val same = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.SOURCES,
                    primary = "https://source-a.example/report",
                    secondary = null
                )
            )
        )
        val changed = CrossSourceMapEngine.create(
            register(
                receipt(
                    factor = SignalFactor.SOURCES,
                    primary = "https://source-b.example/report",
                    secondary = null
                )
            )
        )

        assertEquals(first.fingerprint, same.fingerprint)
        assertNotEquals(first.fingerprint, changed.fingerprint)
    }

    private fun register(vararg receipts: FactReceipt): FactRegister {
        val byFactor = receipts.associateBy(FactReceipt::factor)
        val reads = SignalFactor.values().associateWith { factor ->
            byFactor[factor]?.let { receipt ->
                FactReceiptReadResult(
                    integrity = FactReceiptIntegrity.VALID,
                    receipt = receipt
                )
            } ?: FactReceiptReadResult(
                integrity = FactReceiptIntegrity.EMPTY,
                receipt = null
            )
        }
        return FactRegisterEngine.create(
            eventId = EVENT_ID,
            reads = reads,
            now = NOW
        )
    }

    private fun receipt(
        factor: SignalFactor,
        primary: String,
        secondary: String?
    ): FactReceipt {
        return FactReceiptFactory.create(
            eventId = EVENT_ID,
            factor = factor,
            statement = "Подтверждён проверяемый факт по фактору ${factor.title}",
            primarySource = primary,
            secondarySource = secondary,
            sourceAuditState = if (secondary == null) {
                SourceAuditState.UNAUDITED
            } else {
                SourceAuditState.INDEPENDENT
            },
            coverage = FactReceiptCoverage.DETAILS,
            checkedAt = 1_780_000_000_000L
        )
    }

    private companion object {
        const val EVENT_ID = "api_football_9101"
        const val CHECKED_AT = 1_780_000_000_000L
        const val NOW = CHECKED_AT + FreshnessPolicy.HOUR_MILLIS
    }
}
