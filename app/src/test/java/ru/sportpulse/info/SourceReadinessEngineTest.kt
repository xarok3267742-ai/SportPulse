package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReadinessEngineTest {
    @Test
    fun freshOnlineFeedIsReadyWithoutClaimingPrediction() {
        val result = evaluate(
            apiFeed = feed(NOW - 30L * 60L * 1000L),
            apiActive = true
        )

        assertEquals(SourceReadinessLevel.READY, result.level)
        assertEquals(SourceReadinessMode.ONLINE, result.mode)
        assertEquals("Можно начинать разбор", result.verdict)
        assertEquals(3, result.checks.size)
        assertTrue(result.checks.last().detail.contains("не доказывает исход"))
    }

    @Test
    fun oldOnlineFeedStopsTheWorkflow() {
        val result = evaluate(
            apiFeed = feed(
                NOW - ScheduleFreshnessPolicy.STALE_AFTER_MILLIS - 1L
            ),
            apiActive = true
        )

        assertEquals(SourceReadinessLevel.STOP, result.level)
        assertEquals("Расписание устарело", result.verdict)
        assertEquals("СНАЧАЛА ОБНОВИТЕ", result.badge)
    }

    @Test
    fun configuredButUnloadedSourceRemainsDemo() {
        val result = evaluate(apiConfigured = true)

        assertEquals(SourceReadinessLevel.DEMO, result.level)
        assertEquals(SourceReadinessMode.DEMO, result.mode)
        assertEquals("Не используйте как расписание", result.verdict)
    }

    @Test
    fun failedRefreshDoesNotPresentDemoAsCurrentData() {
        val result = evaluate(
            apiConfigured = true,
            apiError = "network"
        )

        assertEquals(SourceReadinessLevel.STOP, result.level)
        assertEquals(SourceReadinessMode.OFFLINE, result.mode)
        assertEquals("Показан учебный каталог", result.verdict)
    }

    @Test
    fun emptyFreshFeedExplainsWhyDemoEventsAreVisible() {
        val result = evaluate(
            apiFeed = feed(NOW - 5L * 60L * 1000L),
            apiConfigured = true
        )

        assertEquals(SourceReadinessLevel.VERIFY, result.level)
        assertEquals(
            SourceReadinessMode.EMPTY_ONLINE_WINDOW,
            result.mode
        )
        assertTrue(result.summary.contains("не вернул матчи"))
    }

    @Test
    fun emptyWindowBetweenSixAndTwentyFourHoursNeedsReview() {
        val result = evaluate(
            apiFeed = feed(
                NOW - ScheduleFreshnessPolicy.VERIFY_AFTER_MILLIS - 1L
            ),
            apiConfigured = true
        )

        assertEquals(SourceReadinessLevel.VERIFY, result.level)
        assertEquals("Нужна сверка", result.checks[1].value)
    }

    @Test
    fun activeOnlineModeWithoutFeedFailsClosed() {
        val result = evaluate(
            apiFeed = null,
            apiActive = true,
            apiConfigured = true
        )

        assertEquals(SourceReadinessLevel.STOP, result.level)
        assertEquals(SourceReadinessMode.OFFLINE, result.mode)
    }

    @Test
    fun productionSignedPackageIsReadyWhileUnsignedPackageNeedsReview() {
        val signed = SourceReadinessEngine.evaluate(
            now = NOW,
            eventPackage = eventPackage(
                authenticity = EventPackageAuthenticity(
                    level = EventPackageTrustLevel.AUTHENTICATED,
                    keyId = "production-key",
                    keyLabel = "Рабочий ключ",
                    keyEnvironment = EventPackageKeyEnvironment.PRODUCTION,
                    keyFingerprint = "key-fingerprint",
                    signatureFingerprint = "signature-fingerprint"
                )
            ),
            apiFeed = null,
            apiActive = false,
            apiConfigured = false,
            refreshing = false,
            apiError = null
        )
        val unsigned = SourceReadinessEngine.evaluate(
            now = NOW,
            eventPackage = eventPackage(
                authenticity = EventPackageAuthenticity.LOCAL
            ),
            apiFeed = null,
            apiActive = false,
            apiConfigured = false,
            refreshing = false,
            apiError = null
        )

        assertEquals(SourceReadinessLevel.READY, signed.level)
        assertEquals(SourceReadinessMode.SIGNED_PACKAGE, signed.mode)
        assertEquals(SourceReadinessLevel.VERIFY, unsigned.level)
        assertEquals(SourceReadinessMode.LOCAL_PACKAGE, unsigned.mode)
    }

    @Test
    fun expiredPackageCannotLookCurrent() {
        val result = SourceReadinessEngine.evaluate(
            now = NOW,
            eventPackage = eventPackage(
                authenticity = EventPackageAuthenticity.LOCAL,
                validUntil = NOW
            ),
            apiFeed = null,
            apiActive = false,
            apiConfigured = true,
            refreshing = false,
            apiError = null
        )

        assertEquals(SourceReadinessLevel.STOP, result.level)
        assertEquals(SourceReadinessMode.EXPIRED_PACKAGE, result.mode)
        assertEquals("Срок данных истёк", result.verdict)
    }

    private fun evaluate(
        apiFeed: ApiFootballFeed? = null,
        apiActive: Boolean = false,
        apiConfigured: Boolean = false,
        apiError: String? = null
    ): SourceReadinessResult {
        return SourceReadinessEngine.evaluate(
            now = NOW,
            eventPackage = null,
            apiFeed = apiFeed,
            apiActive = apiActive,
            apiConfigured = apiConfigured,
            refreshing = false,
            apiError = apiError
        )
    }

    private fun feed(fetchedAt: Long): ApiFootballFeed {
        return ApiFootballFeed(
            fixtures = emptyList(),
            fetchedAt = fetchedAt,
            fromDate = "2026-09-02",
            toDate = "2026-09-04",
            remainingRequests = null
        )
    }

    private fun eventPackage(
        authenticity: EventPackageAuthenticity,
        validUntil: Long = NOW + 60_000L
    ): SportEventPackage {
        return SportEventPackage(
            schemaVersion = EventPackageCodec.SCHEMA_VERSION,
            packageId = "source-readiness-test",
            sourceLabel = "Тестовый источник",
            generatedAt = NOW - 60_000L,
            validUntil = validUntil,
            events = emptyList(),
            fingerprint = "0123456789abcdef",
            authenticity = authenticity
        )
    }

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
