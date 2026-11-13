package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatGptProviderTest {
    private fun frame(value: JSONObject) = "data: $value\n\n"
    private fun outputText(text: String) = JSONObject().put("type", "message").put("role", "assistant")
        .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))
    private fun completed(output: JSONArray) = frame(JSONObject().put("type", "response.completed")
        .put("response", JSONObject().put("status", "completed").put("output", output)))
    private fun manager(fixture: ChatGptFixture): ChatGptAccounts {
        val account = fixture.exchange().copy(model = "fixture-model")
        return ChatGptAccounts(ChatGptMemoryStore(ChatGptRegistration(accounts = listOf(account), active = account.clientId, enabled = true)), fixture.oauth)
    }
    @Test fun planUsesResponsesWithStoreFalseAndRealCatalogModel() = runBlocking {
        ChatGptFixture().use { fixture ->
            val manager = manager(fixture)
            fixture.responseBody = frame(JSONObject().put("type", "response.output_text.delta").put("delta", "Bon")) +
                frame(JSONObject().put("type", "response.output_text.delta").put("delta", "jour")) +
                completed(JSONArray().put(outputText("Bonjour")))
            val chunks = mutableListOf<String>()
            val calls = ChatGptProvider(manager, fixture.clientId).turn(listOf(ChatMessage("user", "Bonjour synthétique"))) { chunks.add(it) }
            assertTrue(calls.isEmpty()); assertEquals(listOf("Bon", "jour"), chunks)
            val request = fixture.requests.last { it.path == "/v1/responses" }
            val body = JSONObject(request.body.clone().readUtf8())
            assertFalse(body.getBoolean("store")); assertTrue(body.getBoolean("stream"))
            assertEquals("fixture-model", body.getString("model"))
            assertEquals("Bearer synthetic-access-1", request.getHeader("Authorization"))
            assertEquals("current_time", body.getJSONArray("tools").getJSONObject(0).getString("name"))
            assertTrue(fixture.requests.none { it.path.orEmpty().contains("backend-api") || it.path.orEmpty().contains("chat/completions") })
        }
    }
    @Test fun toolsPreserveCallIdsAndEncryptedReasoningAcrossStatelessTurns() = runBlocking {
        ChatGptFixture().use { fixture ->
            val manager = manager(fixture)
            val reasoning = JSONObject().put("type", "reasoning").put("id", "rs_fixture").put("summary", JSONArray()).put("encrypted_content", "synthetic-encrypted-reasoning")
            val function = JSONObject().put("type", "function_call").put("id", "fc_fixture").put("call_id", "call_fixture")
                .put("name", "current_time").put("arguments", "{}")
            fixture.responseBody = completed(JSONArray().put(reasoning).put(function))
            val provider = ChatGptProvider(manager, fixture.clientId)
            val calls = provider.turn(listOf(ChatMessage("user", "Heure"))) { fail("No text in this tool turn") }
            assertEquals("call_fixture", calls.single().id)
            fixture.responseBody = completed(JSONArray().put(outputText("Fin synthétique")))
            val text = mutableListOf<String>()
            provider.turn(listOf(ChatMessage("user", "Heure"), ChatMessage("assistant", "", calls),
                ChatMessage("tool", "UTC synthétique", toolCallId = calls.single().id))) { text.add(it) }
            assertEquals(listOf("Fin synthétique"), text)
            val input = JSONObject(fixture.requests.last { it.path == "/v1/responses" }.body.clone().readUtf8()).getJSONArray("input")
            assertEquals("synthetic-encrypted-reasoning", input.getJSONObject(1).getString("encrypted_content"))
            assertEquals("call_fixture", input.getJSONObject(2).getString("call_id"))
            assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
            val another = ChatGptProvider.input(listOf(ChatMessage("assistant", "", calls)), "another-account:another-model")
            assertEquals(1, another.length())
            assertEquals("function_call", another.getJSONObject(0).getString("type"))
            assertFalse(another.toString().contains("synthetic-encrypted-reasoning"))
        }
    }
    @Test fun quotaIncompleteAndTruncatedStreamsRemainFailures() = runBlocking {
        ChatGptFixture().use { fixture ->
            val manager = manager(fixture)
            for ((body, expected) in listOf(
                frame(JSONObject().put("type", "response.output_text.delta").put("delta", "Partiel")) to RunError.PROTOCOL,
                frame(JSONObject().put("type", "response.incomplete")) to RunError.PROTOCOL,
                frame(JSONObject().put("type", "response.failed").put("response", JSONObject().put("error", JSONObject()
                    .put("code", "subscription_sharing_usage_limit_exceeded").put("message", "sensitive diagnostic")))) to RunError.RATE_LIMIT)) {
                fixture.responseBody = body
                try { ChatGptProvider(manager, fixture.clientId).turn(listOf(ChatMessage("user", "Test"))) { }; fail() }
                catch (failure: RunFailure) { assertEquals(expected, failure.kind); assertFalse(failure.toString().contains("sensitive")) }
            }
        }
    }
    @Test fun accountSwitchAndMissingModelCannotSendToAnotherBillingPath() = runBlocking {
        ChatGptFixture().use { fixture ->
            val manager = manager(fixture)
            manager.useApi()
            try { ChatGptProvider(manager, fixture.clientId).turn(emptyList()) { }; fail() }
            catch (failure: RunFailure) { assertEquals(RunError.CHATGPT_AUTH, failure.kind) }
            assertTrue(fixture.requests.none { it.path == "/v1/responses" })
        }
    }
}
