package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredEventPackageCodecTest {
    @Test
    fun exactJsonSurvivesStorageRoundTrip() {
        val json = """
            {
              "source": "Россия и СНГ",
              "match": "Север — Столица"
            }
        """.trimIndent() + "\n"

        val stored = StoredEventPackageCodec.encode(json)
        val restored = StoredEventPackageCodec.decode(stored)

        assertTrue(stored.startsWith("base64:"))
        assertEquals(json, restored)
    }

    @Test
    fun legacyPlainJsonRemainsReadable() {
        val legacy = "{\"schemaVersion\":1}\n    "

        assertEquals(
            legacy,
            StoredEventPackageCodec.decode(legacy)
        )
    }

    @Test
    fun malformedEncodedValueIsRejected() {
        assertNull(
            StoredEventPackageCodec.decode(
                "base64:not-valid-***"
            )
        )
    }
}
