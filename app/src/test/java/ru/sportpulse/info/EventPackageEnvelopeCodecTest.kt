package ru.sportpulse.info

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class EventPackageEnvelopeCodecTest {
    private val now = 1_800_000_000_000L
    private val keyPair: KeyPair = KeyPairGenerator
        .getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val trustedKey = TrustedEventPackageKey(
        id = "test_source_2026",
        label = "Тестовый ключ",
        sourceLabel = "Тестовый источник",
        environment = EventPackageKeyEnvironment.DEVELOPMENT,
        algorithm = EventPackageEnvelopeCodec.ALGORITHM,
        publicKeyDerBase64 = Base64.getEncoder()
            .encodeToString(keyPair.public.encoded)
    )

    @Test
    fun rawPackRemainsExplicitlyLocal() {
        val decoded = EventPackageDocumentCodec.decode(
            json = payload(),
            now = now
        )

        assertFalse(decoded.authenticity.isAuthenticated)
        assertEquals(
            EventPackageAuthenticity.LOCAL,
            decoded.authenticity
        )
    }

    @Test
    fun validEnvelopeAuthenticatesExactPayload() {
        val payload = payload()
        val decoded = EventPackageDocumentCodec.decode(
            json = envelope(payload.toByteArray()),
            now = now,
            keyResolver = ::resolve
        )

        assertTrue(decoded.authenticity.isAuthenticated)
        assertEquals(trustedKey.id, decoded.authenticity.keyId)
        assertEquals(
            trustedKey.fingerprint,
            decoded.authenticity.keyFingerprint
        )
        assertEquals(
            EventPackageKeyEnvironment.DEVELOPMENT,
            decoded.authenticity.keyEnvironment
        )
        assertEquals(
            EventPackageEnvelopeCodec.sha256(
                payload.toByteArray()
            ),
            decoded.fingerprint
        )
        assertEquals(
            12,
            decoded.authenticity
                .shortSignatureFingerprint
                ?.length
        )
    }

    @Test
    fun changedPayloadCannotReuseSignature() {
        val original = JSONObject(
            envelope(payload().toByteArray())
        )
        val changedPayload = payload().replace(
            "\"form\":70",
            "\"form\":71"
        )
        original.put(
            "payload",
            Base64.getEncoder().encodeToString(
                changedPayload.toByteArray()
            )
        )

        val error = failure {
            EventPackageDocumentCodec.decode(
                original.toString(),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "подпись Event Pack недействительна"
            )
        )
    }

    @Test
    fun changedSignatureIsRejected() {
        val root = JSONObject(
            envelope(payload().toByteArray())
        )
        val signature = Base64.getDecoder().decode(
            root.getString("signature")
        )
        signature[0] = (signature[0].toInt() xor 1).toByte()
        root.put(
            "signature",
            Base64.getEncoder().encodeToString(signature)
        )

        val error = failure {
            EventPackageDocumentCodec.decode(
                root.toString(),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "подпись Event Pack недействительна"
            )
        )
    }

    @Test
    fun unknownKeyNeverFallsBackToLocalTrust() {
        val root = JSONObject(
            envelope(payload().toByteArray())
        ).put("keyId", "unknown_key")

        val error = failure {
            EventPackageDocumentCodec.decode(
                root.toString(),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "не входит в доверенный реестр"
            )
        )
    }

    @Test
    fun trustedKeyCannotClaimAnotherSource() {
        val foreignPayload = payload().replace(
            "Тестовый источник",
            "Чужой источник"
        )

        val error = failure {
            EventPackageDocumentCodec.decode(
                envelope(foreignPayload.toByteArray()),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "не разрешен для источника"
            )
        )
    }

    @Test
    fun envelopeRejectsAlgorithmSubstitutionAndUnknownFields() {
        val algorithmChanged = JSONObject(
            envelope(payload().toByteArray())
        ).put("algorithm", "NONEwithRSA")
        val extraField = JSONObject(
            envelope(payload().toByteArray())
        ).put("certificateUrl", "https://invalid.example")

        val algorithmError = failure {
            EventPackageDocumentCodec.decode(
                algorithmChanged.toString(),
                now,
                keyResolver = ::resolve
            )
        }
        val fieldError = failure {
            EventPackageDocumentCodec.decode(
                extraField.toString(),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            algorithmError.message.orEmpty().contains(
                "Алгоритм подписи"
            )
        )
        assertTrue(
            fieldError.message.orEmpty().contains(
                "неподдерживаемое поле"
            )
        )
    }

    @Test
    fun signedMalformedUtf8IsRejectedAfterVerification() {
        val malformed = byteArrayOf(
            0x7b,
            0x22,
            0x78,
            0x22,
            0x3a,
            0xc3.toByte(),
            0x28,
            0x7d
        )

        val error = failure {
            EventPackageDocumentCodec.decode(
                envelope(malformed),
                now,
                keyResolver = ::resolve
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "корректным UTF-8"
            )
        )
    }

    private fun resolve(keyId: String): TrustedEventPackageKey? {
        return trustedKey.takeIf { it.id == keyId }
    }

    private fun envelope(payloadBytes: ByteArray): String {
        val signature = Signature.getInstance(
            EventPackageEnvelopeCodec.ALGORITHM
        ).run {
            initSign(keyPair.private)
            update(
                EventPackageEnvelopeCodec.signingPrefix(
                    trustedKey.id
                )
            )
            update(payloadBytes)
            sign()
        }
        return JSONObject()
            .put(
                "envelopeVersion",
                EventPackageEnvelopeCodec.ENVELOPE_VERSION
            )
            .put("keyId", trustedKey.id)
            .put(
                "algorithm",
                EventPackageEnvelopeCodec.ALGORITHM
            )
            .put(
                "payloadEncoding",
                EventPackageEnvelopeCodec.PAYLOAD_ENCODING
            )
            .put(
                "payload",
                Base64.getEncoder().encodeToString(payloadBytes)
            )
            .put(
                "signature",
                Base64.getEncoder().encodeToString(signature)
            )
            .toString()
    }

    private fun payload(): String {
        return """
            {
              "schemaVersion":1,
              "packageId":"secure_pack_1",
              "source":"Тестовый источник",
              "generatedAt":${now - 60_000L},
              "validUntil":${now + 3_600_000L},
              "events":[{
                "id":"secure_event_1",
                "sport":"Футбол",
                "tournament":"Премьер-лига",
                "region":"Россия",
                "match":"Север — Столица",
                "startAt":${now + 1_800_000L},
                "focus":"Составы и нагрузка",
                "note":"Проверить официальный протокол.",
                "tags":["подпись"],
                "assessment":{
                  "form":70,
                  "lineup":60,
                  "load":50,
                  "context":65,
                  "sources":80
                }
              }]
            }
        """.trimIndent()
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
