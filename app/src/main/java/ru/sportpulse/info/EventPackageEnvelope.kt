package ru.sportpulse.info

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal enum class EventPackageTrustLevel {
    LOCAL,
    AUTHENTICATED
}

internal enum class EventPackageKeyEnvironment {
    DEVELOPMENT,
    PRODUCTION
}

internal data class EventPackageAuthenticity(
    val level: EventPackageTrustLevel,
    val keyId: String?,
    val keyLabel: String?,
    val keyEnvironment: EventPackageKeyEnvironment?,
    val keyFingerprint: String?,
    val signatureFingerprint: String?
) {
    val isAuthenticated: Boolean
        get() = level == EventPackageTrustLevel.AUTHENTICATED

    val shortKeyFingerprint: String?
        get() = keyFingerprint?.take(12)?.uppercase()

    val shortSignatureFingerprint: String?
        get() = signatureFingerprint?.take(12)?.uppercase()

    companion object {
        val LOCAL = EventPackageAuthenticity(
            level = EventPackageTrustLevel.LOCAL,
            keyId = null,
            keyLabel = null,
            keyEnvironment = null,
            keyFingerprint = null,
            signatureFingerprint = null
        )
    }
}

internal data class TrustedEventPackageKey(
    val id: String,
    val label: String,
    val sourceLabel: String,
    val environment: EventPackageKeyEnvironment,
    val algorithm: String,
    val publicKeyDerBase64: String
) {
    val publicKeyBytes: ByteArray
        get() = Base64.getDecoder().decode(publicKeyDerBase64)

    val fingerprint: String
        get() = EventPackageEnvelopeCodec.sha256(publicKeyBytes)
}

internal object TrustedEventPackageKeyRegistry {
    private val keys = listOf(
        TrustedEventPackageKey(
            id = "sport-pulse-local-dev-2026",
            label = "Локальный ключ разработки",
            sourceLabel = "Локальный тестовый пакет",
            environment = EventPackageKeyEnvironment.DEVELOPMENT,
            algorithm = EventPackageEnvelopeCodec.ALGORITHM,
            publicKeyDerBase64 =
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA00Mla2ttfe4WzadIJIXxtzDQ1EL70YU311Cnpct13Dh9GAGcf1hlMmGOVOfw9cyDRSqWyrObi+K10eABI4m0GEzd2eWwpry6MY22UKm1i2iTEy3BMy9sIN+gAZARVudn0mvymtnf7wjQz1Rg/TyjyLx0+GDbmKPbOEHZoOIvQLhSG0PiCyAPTQjBAffMRdAWMDJgdgDNe4o5ylPKdwlye199wFw/30c+Lr50+q+Em34ZO41KgPgXLn4g+tqkyt2X+iaPOm33IbHQizjATAO09kyg37Xu0a5OzkPmDodpU+HJbO6FNP4/5QCnibgDDh2IzhxB9y3RHQCHNbH23aan4wIDAQAB"
        )
    ).associateBy(TrustedEventPackageKey::id)

    fun find(keyId: String): TrustedEventPackageKey? = keys[keyId]
}

internal object EventPackageDocumentCodec {
    const val MAX_DOCUMENT_BYTES = 384 * 1024

    fun decode(
        json: String,
        now: Long,
        requireFresh: Boolean = true,
        keyResolver: (String) -> TrustedEventPackageKey? =
            TrustedEventPackageKeyRegistry::find
    ): SportEventPackage {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) {
            fail("Файл пакета пуст.")
        }
        if (bytes.size > MAX_DOCUMENT_BYTES) {
            fail("Документ Event Pack превышает лимит 384 КБ.")
        }
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            fail("Файл не является корректным JSON-пакетом.")
        }
        return if (root.has("envelopeVersion")) {
            EventPackageEnvelopeCodec.decode(
                documentJson = json,
                now = now,
                requireFresh = requireFresh,
                keyResolver = keyResolver
            )
        } else {
            EventPackageCodec.decode(
                json = json,
                now = now,
                requireFresh = requireFresh
            )
        }
    }

    private fun fail(message: String): Nothing {
        throw EventPackageValidationException(message)
    }
}

internal object EventPackageEnvelopeCodec {
    const val ENVELOPE_VERSION = 1
    const val ALGORITHM = "SHA256withRSA"
    const val PAYLOAD_ENCODING = "base64"
    const val DOMAIN = "SPORT_PULSE_EVENT_PACK_ENVELOPE_V1"

    private val idPattern = Regex("[A-Za-z0-9_-]{3,64}")

    fun decode(
        documentJson: String,
        now: Long,
        requireFresh: Boolean = true,
        keyResolver: (String) -> TrustedEventPackageKey? =
            TrustedEventPackageKeyRegistry::find
    ): SportEventPackage {
        val documentBytes = documentJson.toByteArray(Charsets.UTF_8)
        if (documentBytes.size > EventPackageDocumentCodec.MAX_DOCUMENT_BYTES) {
            fail("Документ Event Pack превышает лимит 384 КБ.")
        }
        val root = try {
            JSONObject(documentJson)
        } catch (_: Exception) {
            fail("Защищенный Event Pack должен быть корректным JSON.")
        }
        requireOnlyKeys(
            root,
            setOf(
                "envelopeVersion",
                "keyId",
                "algorithm",
                "payloadEncoding",
                "payload",
                "signature"
            )
        )
        val version = integralInt(root, "envelopeVersion")
        if (version != ENVELOPE_VERSION) {
            fail("Версия защищенного конверта $version не поддерживается.")
        }
        val keyId = string(root, "keyId", 3, 64)
        if (!idPattern.matches(keyId)) {
            fail("ID ключа содержит недопустимые символы.")
        }
        val algorithm = string(root, "algorithm", 3, 40)
        if (algorithm != ALGORITHM) {
            fail("Алгоритм подписи «$algorithm» не поддерживается.")
        }
        val payloadEncoding = string(
            root,
            "payloadEncoding",
            3,
            20
        )
        if (payloadEncoding != PAYLOAD_ENCODING) {
            fail("Кодировка payload «$payloadEncoding» не поддерживается.")
        }
        val payloadBytes = base64(
            string(
                root,
                "payload",
                1,
                encodedLength(EventPackageCodec.MAX_JSON_BYTES)
            ),
            "payload"
        )
        if (payloadBytes.isEmpty()) {
            fail("Подписанный payload пуст.")
        }
        if (payloadBytes.size > EventPackageCodec.MAX_JSON_BYTES) {
            fail("Payload превышает лимит 256 КБ.")
        }
        val signatureBytes = base64(
            string(root, "signature", 32, 1_024),
            "signature"
        )
        if (signatureBytes.size !in 128..512) {
            fail("Размер подписи не поддерживается.")
        }
        val trustedKey = keyResolver(keyId)
            ?: fail("Ключ «$keyId» не входит в доверенный реестр.")
        if (trustedKey.algorithm != algorithm) {
            fail("Алгоритм не соответствует доверенному ключу.")
        }
        if (
            !verify(
                key = trustedKey,
                keyId = keyId,
                algorithm = algorithm,
                payloadBytes = payloadBytes,
                signatureBytes = signatureBytes
            )
        ) {
            fail("Криптографическая подпись Event Pack недействительна.")
        }
        val payloadJson = strictUtf8(payloadBytes)
        val eventPackage = EventPackageCodec.decode(
            json = payloadJson,
            now = now,
            requireFresh = requireFresh
        )
        if (
            !EventPackageIdentity.isSameSource(
                trustedKey.sourceLabel,
                eventPackage.sourceLabel
            )
        ) {
            fail(
                "Ключ «$keyId» не разрешен для источника " +
                    "«${eventPackage.sourceLabel}»."
            )
        }
        return eventPackage.copy(
            authenticity = EventPackageAuthenticity(
                level = EventPackageTrustLevel.AUTHENTICATED,
                keyId = trustedKey.id,
                keyLabel = trustedKey.label,
                keyEnvironment = trustedKey.environment,
                keyFingerprint = trustedKey.fingerprint,
                signatureFingerprint = sha256(signatureBytes)
            )
        )
    }

    internal fun signingPrefix(
        keyId: String,
        algorithm: String = ALGORITHM
    ): ByteArray {
        return "$DOMAIN\n$keyId\n$algorithm\n"
            .toByteArray(Charsets.UTF_8)
    }

    internal fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun verify(
        key: TrustedEventPackageKey,
        keyId: String,
        algorithm: String,
        payloadBytes: ByteArray,
        signatureBytes: ByteArray
    ): Boolean {
        return try {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(
                    X509EncodedKeySpec(key.publicKeyBytes)
                )
            Signature.getInstance(algorithm).run {
                initVerify(publicKey)
                update(signingPrefix(keyId, algorithm))
                update(payloadBytes)
                verify(signatureBytes)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun strictUtf8(bytes: ByteArray): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            fail("Payload должен быть корректным UTF-8 JSON.")
        }
    }

    private fun requireOnlyKeys(
        value: JSONObject,
        allowed: Set<String>
    ) {
        val unsupported = value.keys().asSequence()
            .firstOrNull { it !in allowed }
        if (unsupported != null) {
            fail(
                "Защищенный конверт содержит неподдерживаемое " +
                    "поле «$unsupported»."
            )
        }
    }

    private fun integralInt(value: JSONObject, name: String): Int {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует числовое поле «$name».")
        }
        val number = value.opt(name) as? Number
            ?: fail("Поле «$name» должно быть числом.")
        val double = number.toDouble()
        val long = number.toLong()
        if (
            !double.isFinite() ||
            double != long.toDouble() ||
            long !in Int.MIN_VALUE..Int.MAX_VALUE
        ) {
            fail("Поле «$name» должно быть целым числом.")
        }
        return long.toInt()
    }

    private fun string(
        value: JSONObject,
        name: String,
        minLength: Int,
        maxLength: Int
    ): String {
        if (!value.has(name) || value.isNull(name)) {
            fail("Отсутствует строковое поле «$name».")
        }
        val raw = value.opt(name) as? String
            ?: fail("Поле «$name» должно быть строкой.")
        if (raw.length !in minLength..maxLength) {
            fail(
                "Длина поля «$name» должна быть от $minLength " +
                    "до $maxLength символов."
            )
        }
        if (raw.any(Char::isISOControl)) {
            fail("Поле «$name» содержит управляющие символы.")
        }
        return raw
    }

    private fun base64(value: String, label: String): ByteArray {
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            fail("Поле «$label» содержит некорректный Base64.")
        }
    }

    private fun encodedLength(rawBytes: Int): Int {
        return 4 * ((rawBytes + 2) / 3)
    }

    private fun fail(message: String): Nothing {
        throw EventPackageValidationException(message)
    }
}
