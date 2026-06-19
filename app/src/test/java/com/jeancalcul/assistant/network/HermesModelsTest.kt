package com.jeancalcul.assistant.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesModelsTest {
    @Test fun `health ok est insensible a la casse`() {
        assertTrue(HermesHealth(status = "OK").isOk)
        assertTrue(HermesHealth(status = "ok").isOk)
        assertFalse(HermesHealth(status = "degraded").isOk)
    }

    @Test fun `displayText prefere le message puis le brut puis le fallback`() {
        val raw = buildJsonObject { put("custom", "value") }

        assertEquals("Salut", HermesResponse(message = "Salut", raw = raw).displayText)
        assertEquals(raw.toString(), HermesResponse(raw = raw).displayText)
        assertEquals("Réponse vide", HermesResponse().displayText)
    }

    @Test fun `deserialize une reponse Hermes avec champs inconnus`() {
        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HermesResponse>("""
            {"requestId":"abc","message":"Terminé","unknown":"ignore"}
        """.trimIndent())

        assertEquals("abc", response.requestId)
        assertEquals("Terminé", response.displayText)
    }
}
