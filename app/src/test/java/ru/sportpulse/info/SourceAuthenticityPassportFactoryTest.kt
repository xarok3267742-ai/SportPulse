package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAuthenticityPassportFactoryTest {
    @Test
    fun fileNameIsDeterministicAndSafe() {
        val passport = SourceAuthenticityPassportFactory.create(
            eventPackage = eventPackage().copy(
                packageId = "source package/31"
            ),
            generatedAt = 123L
        )

        assertEquals(
            "sport_pulse_source_source_package_31_123.png",
            SourceAuthenticityPassportFactory.fileName(passport)
        )
    }

    @Test
    fun signedShareTextCarriesKeyAndLimit() {
        val passport = SourceAuthenticityPassportFactory.create(
            eventPackage(authenticated = true),
            generatedAt = 123L
        )

        val text = SourceAuthenticityPassportFactory.shareText(
            passport
        )

        assertTrue(text.contains("Подпись проверена"))
        assertTrue(text.contains("source_key_2026"))
        assertTrue(text.contains("ключ разработки"))
        assertTrue(text.contains("не истинность спортивных фактов"))
    }

    @Test
    fun localShareTextDoesNotImplyAuthentication() {
        val passport = SourceAuthenticityPassportFactory.create(
            eventPackage(authenticated = false),
            generatedAt = 123L
        )

        val text = SourceAuthenticityPassportFactory.shareText(
            passport
        )

        assertTrue(text.contains("подпись отсутствует"))
        assertTrue(text.contains("автор файла не подтвержден"))
    }

    private fun eventPackage(
        authenticated: Boolean = false
    ): SportEventPackage {
        return SportEventPackage(
            schemaVersion = 1,
            packageId = "source_pack_31",
            sourceLabel = "Тестовый источник",
            generatedAt = 1_000L,
            validUntil = 2_000L,
            events = listOf(
                PackagedSportEvent(
                    id = "event_101",
                    sport = "Футбол",
                    tournament = "Лига",
                    region = "Россия",
                    match = "Север — Столица",
                    startAt = 1_500L,
                    focus = "Составы",
                    note = "Проверить протокол",
                    tags = listOf("матч"),
                    seedAssessment = SignalAssessment(
                        List(5) { 50 }
                    )
                )
            ),
            fingerprint = "a".repeat(64),
            authenticity = if (authenticated) {
                EventPackageAuthenticity(
                    level =
                        EventPackageTrustLevel.AUTHENTICATED,
                    keyId = "source_key_2026",
                    keyLabel = "Тестовый ключ",
                    keyEnvironment =
                        EventPackageKeyEnvironment.DEVELOPMENT,
                    keyFingerprint = "b".repeat(64),
                    signatureFingerprint = "c".repeat(64)
                )
            } else {
                EventPackageAuthenticity.LOCAL
            }
        )
    }
}
