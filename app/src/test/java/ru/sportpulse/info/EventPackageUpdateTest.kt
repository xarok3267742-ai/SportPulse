package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPackageUpdateTest {
    @Test
    fun firstAndIdenticalVersionsAreRecognized() {
        val current = eventPackage()

        assertEquals(
            EventPackageUpdateStatus.FIRST_VERSION,
            EventPackageUpdatePolicy.evaluate(null, current)
        )
        assertEquals(
            EventPackageUpdateStatus.IDENTICAL,
            EventPackageUpdatePolicy.evaluate(current, current.copy())
        )
    }

    @Test
    fun newerVersionFromSameSourceIsAccepted() {
        val current = eventPackage(generatedAt = 1_000L)
        val candidate = eventPackage(
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(
            EventPackageUpdateStatus.ACCEPTED,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun rollbackFromSameSourceIsRejected() {
        val current = eventPackage(generatedAt = 2_000L)
        val candidate = eventPackage(
            generatedAt = 1_000L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(
            EventPackageUpdateStatus.ROLLBACK,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun conflictingVersionAtSameTimeIsRejected() {
        val current = eventPackage(generatedAt = 2_000L)
        val candidate = eventPackage(
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(
            EventPackageUpdateStatus.CONFLICT,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun differentSourceCanReplaceCatalog() {
        val current = eventPackage(
            sourceLabel = "Источник A",
            generatedAt = 2_000L
        )
        val candidate = eventPackage(
            sourceLabel = "Источник B",
            generatedAt = 1_000L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(
            EventPackageUpdateStatus.ACCEPTED,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun authenticatedCatalogRejectsUnsignedReplacement() {
        val current = eventPackage(
            authenticity = authenticated()
        )
        val candidate = eventPackage(
            sourceLabel = "Другой источник",
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64)
        )

        assertEquals(
            EventPackageUpdateStatus.TRUST_DOWNGRADE,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
        val error = failure {
            EventPackageUpdatePolicy.requireImportable(
                current,
                candidate
            )
        }
        assertTrue(
            error.message.orEmpty().contains(
                "Снятие подтвержденной подписи"
            )
        )
    }

    @Test
    fun signedCopyCanUpgradeIdenticalLocalPayload() {
        val current = eventPackage()
        val candidate = current.copy(
            authenticity = authenticated()
        )

        assertEquals(
            EventPackageUpdateStatus.ACCEPTED,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun productionKeyCannotBeReplacedByDevelopmentKey() {
        val current = eventPackage(
            authenticity = authenticated(
                EventPackageKeyEnvironment.PRODUCTION
            )
        )
        val candidate = eventPackage(
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64),
            authenticity = authenticated(
                EventPackageKeyEnvironment.DEVELOPMENT
            )
        )

        assertEquals(
            EventPackageUpdateStatus.TRUST_DOWNGRADE,
            EventPackageUpdatePolicy.evaluate(current, candidate)
        )
    }

    @Test
    fun runtimeIdentitySurvivesPackageRevisionButNotSourceChange() {
        val first = EventPackageIdentity.runtimeEventId(
            "Поставщик",
            "match_101"
        )
        val cosmeticRename = EventPackageIdentity.runtimeEventId(
            "ПОСТАВЩИК",
            "match_101"
        )
        val anotherSource = EventPackageIdentity.runtimeEventId(
            "Другой поставщик",
            "match_101"
        )

        assertEquals(first, cosmeticRename)
        assertNotEquals(first, anotherSource)
        assertTrue(
            first.matches(
                Regex("pack_[a-f0-9]{20}_match_101")
            )
        )
    }

    @Test
    fun importGuardExplainsRollbackAndConflict() {
        val current = eventPackage(generatedAt = 2_000L)
        val rollback = eventPackage(
            generatedAt = 1_000L,
            fingerprint = "b".repeat(64)
        )
        val conflict = eventPackage(
            generatedAt = 2_000L,
            fingerprint = "c".repeat(64)
        )

        val rollbackError = failure {
            EventPackageUpdatePolicy.requireImportable(
                current,
                rollback
            )
        }
        val conflictError = failure {
            EventPackageUpdatePolicy.requireImportable(
                current,
                conflict
            )
        }

        assertTrue(
            rollbackError.message.orEmpty().contains(
                "Откат заблокирован"
            )
        )
        assertTrue(
            conflictError.message.orEmpty().contains(
                "одинаковым временем"
            )
        )
    }

    @Test
    fun deltaDetectsEverySupportedChange() {
        val unchanged = event(id = "unchanged")
        val rescheduled = event(
            id = "changed",
            match = "Север — Столица",
            startAt = 5_000L
        )
        val previous = eventPackage(
            events = listOf(
                event(id = "removed"),
                rescheduled,
                unchanged
            )
        )
        val current = eventPackage(
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64),
            events = listOf(
                event(id = "added"),
                rescheduled.copy(
                    match = "Север — Столица, новый стадион",
                    startAt = 8_000L,
                    note = "Новая подтвержденная заметка",
                    seedAssessment = SignalAssessment(
                        listOf(60, 70, 50, 40, 80)
                    )
                ),
                unchanged
            )
        )

        val delta = EventPackageDeltaEngine.compare(previous, current)

        assertFalse(delta.sourceChanged)
        assertEquals(3, delta.changes.size)
        assertEquals(1, delta.addedCount)
        assertEquals(1, delta.removedCount)
        assertEquals(1, delta.rescheduledCount)
        assertEquals(1, delta.assessmentChangedCount)
        assertEquals(1, delta.detailsChangedCount)
        val changed = delta.changes.single { it.eventId == "changed" }
        assertTrue(changed.isRescheduled)
        assertEquals(
            setOf(EventDetailField.MATCH, EventDetailField.NOTE),
            changed.detailChanges
        )
        assertEquals(
            listOf(
                SignalFactor.LINEUP,
                SignalFactor.SOURCES
            ),
            changed.assessmentChanges.map(EventAssessmentChange::factor)
        )
    }

    @Test
    fun identicalCatalogHasNoChangesEvenWithNewFingerprint() {
        val previous = eventPackage()
        val current = previous.copy(
            generatedAt = 2_000L,
            fingerprint = "b".repeat(64)
        )

        val delta = EventPackageDeltaEngine.compare(previous, current)

        assertFalse(delta.hasChanges)
        assertTrue(delta.changes.isEmpty())
    }

    @Test
    fun signatureUpgradeAppearsInUpdateRadar() {
        val previous = eventPackage()
        val current = previous.copy(
            authenticity = authenticated()
        )

        val delta = EventPackageDeltaEngine.compare(
            previous,
            current
        )

        assertTrue(delta.trustChanged)
        assertTrue(delta.hasChanges)
        assertEquals(1, delta.changeCount)
        assertTrue(delta.changes.isEmpty())
    }

    @Test
    fun sourceChangeIsRenderedAsFullReplacement() {
        val previous = eventPackage(
            sourceLabel = "Источник A",
            events = listOf(event(id = "shared"))
        )
        val current = eventPackage(
            sourceLabel = "Источник B",
            fingerprint = "b".repeat(64),
            events = listOf(event(id = "shared"))
        )

        val delta = EventPackageDeltaEngine.compare(previous, current)

        assertTrue(delta.sourceChanged)
        assertEquals(1, delta.addedCount)
        assertEquals(1, delta.removedCount)
    }

    private fun eventPackage(
        sourceLabel: String = "Поставщик",
        generatedAt: Long = 1_000L,
        fingerprint: String = "a".repeat(64),
        events: List<PackagedSportEvent> = listOf(event()),
        authenticity: EventPackageAuthenticity =
            EventPackageAuthenticity.LOCAL
    ): SportEventPackage {
        return SportEventPackage(
            schemaVersion = 1,
            packageId = "package_101",
            sourceLabel = sourceLabel,
            generatedAt = generatedAt,
            validUntil = generatedAt + 10_000L,
            events = events,
            fingerprint = fingerprint,
            authenticity = authenticity
        )
    }

    private fun authenticated(
        environment: EventPackageKeyEnvironment =
            EventPackageKeyEnvironment.DEVELOPMENT
    ): EventPackageAuthenticity {
        return EventPackageAuthenticity(
            level = EventPackageTrustLevel.AUTHENTICATED,
            keyId = "test_key",
            keyLabel = "Тестовый ключ",
            keyEnvironment = environment,
            keyFingerprint = "c".repeat(64),
            signatureFingerprint = "d".repeat(64)
        )
    }

    private fun event(
        id: String = "match_101",
        match: String = "Север — Столица",
        startAt: Long = 5_000L
    ): PackagedSportEvent {
        return PackagedSportEvent(
            id = id,
            sport = "Футбол",
            tournament = "Премьер-лига",
            region = "Россия",
            match = match,
            startAt = startAt,
            focus = "Составы и темп",
            note = "Проверить официальные заявки",
            tags = listOf("вечер"),
            seedAssessment = SignalAssessment(
                listOf(60, 50, 50, 40, 50)
            )
        )
    }

    private fun failure(
        block: () -> Unit
    ): EventPackageValidationException {
        return try {
            block()
            throw AssertionError(
                "Expected EventPackageValidationException"
            )
        } catch (error: EventPackageValidationException) {
            error
        }
    }
}
