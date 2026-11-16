package io.github.mangi.eta.agent.development

import android.content.Context
import io.github.mangi.eta.agent.model.*
import io.github.mangi.eta.agent.runtime.AgentRunController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DevelopmentRecoveryTest {
    private val revision = "a".repeat(40)
    private val blob = "b".repeat(40)
    private val path = "app/src/main/SettingsScreen.kt"
    private lateinit var context: Context
    private fun store() = DevelopmentStore(context)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("jeancalcul_development", Context.MODE_PRIVATE)
            .edit().clear().putBoolean("enabled", true).commit()
    }

    @Test fun grantAndTaskSurviveBeforePrAfterRecreationButDoNotReachOtherConversations() {
        store().authorizeConversation("assistant-task", "voice-key", "Settings", "Translate smart notification settings to English", revision)
        val reopened = store()
        assertTrue(reopened.mayContinue("assistant-task"))
        assertFalse(reopened.mayContinue("assistant-other"))
        assertFalse(reopened.mayContinue(""))
        assertFalse(reopened.questionPending())
        assertNull(reopened.proposal())
        // A later capture for this conversation must not replace its task/source with "continue".
        reopened.authorizeConversation("assistant-task", "voice-key", "Home", "continue", "c".repeat(40))
        assertEquals(revision, store().context()!!.getString("source_revision"))
        assertEquals("Settings", store().context()!!.getString("screen"))
        assertEquals("Home", store().context()!!.getString("latest_screen"))
        assertEquals("Translate smart notification settings to English", store().context()!!.getString("task"))
        reopened.disconnect()
        assertFalse(store().mayContinue("assistant-task"))
        assertNull(store().context())
    }

    @Test fun newConversationStartsWithItsOwnTaskAndJournal() {
        store().authorizeConversation("first", "key-1", "Settings", "First task", revision)
        store().saveContext(store().context()!!.put("recent_actions", JSONArray().put("old-read")))
        store().authorizeConversation("second", "key-2", "Home", "Second task", revision)
        assertFalse(store().mayContinue("first"))
        assertTrue(store().mayContinue("second"))
        assertEquals("Second task", store().context()!!.getString("task"))
        assertFalse(store().context()!!.has("recent_actions"))
        store().revokeConversation("first")
        assertTrue(store().mayContinue("second"))
        store().revokeConversation("second")
        assertFalse(store().mayContinue("second"))
        assertNull(store().context())
    }

    @Test fun journalIsBoundedAndKeepsOnlyRereadPointers() {
        var journal: JSONObject? = null
        repeat(40) {
            journal = DevelopmentSessionContext.record(journal, "read", JSONObject(),
                JSONObject().put("ok", true).put("path", path).put("revision", revision).put("blob_sha", blob)
                    .put("offset", it * 1000).put("next_offset", (it + 1) * 1000)
                    .put("content", "private-source").put("log_tail", "private-log").put("token", "private-token"))
        }
        assertEquals(16, journal!!.getJSONArray("recent_actions").length())
        assertEquals(24000, journal!!.getJSONArray("recent_actions").getJSONObject(0).getInt("offset"))
        assertFalse(journal.toString().contains("private-"))
        assertTrue(journal.toString().contains(blob))
    }

    @Test fun stoppedReadThenContinueRehydratesTaskAndCanRereadSourceWithRedactedHistory() {
        store().authorizeConversation("assistant-task", "voice-key", "Settings", "Translate notification settings to English", revision)
        val reads = mutableListOf<String>()
        val github = DevelopmentGitHubClient({ "synthetic-token-not-in-model-context" }) { request ->
            reads += request.url.toString()
            assertEquals("GET", request.method)
            """{"type":"file","encoding":"base64","size":5,"sha":"$blob","content":"dG9vbHM="}"""
        }
        fun tools(run: String) = DevelopmentTools(store(), github, run, "continue", "Settings", true)
        fun config() = AgentModelClient.ModelConfig(baseUrl = "http://127.0.0.1:8000/v1", apiKey = "",
            model = "fixture", browserTools = false, systemPrompt = DevelopmentSessionContext.instructions(store()))
        fun toolResponse(id: String) = ProviderResponse(JSONObject().put("role", "assistant").put("content", "")
            .put("tool_calls", JSONArray().put(JSONObject().put("id", id).put("type", "function")
                .put("function", JSONObject().put("name", DevelopmentPolicy.TOOL).put("arguments",
                    JSONObject().put("action", "read").put("path", path).toString()))))
            .put("finish_reason", "tool_calls"))
        var rounds = 0
        val first = object : AgentProviderClient {
            override val id = "stopped-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                if (++rounds == 1) return toolResponse("read-before-stop")
                assertEquals("tools", JSONObject((0 until request.messages.length()).map { request.messages.getJSONObject(it) }.last { it.optString("role") == "tool" }.getString("content")).getString("content"))
                runController.cancel()
                runController.throwIfCancelled()
                error("Cancellation must throw")
            }
        }
        val failure = assertThrows(AgentModelExecutionException::class.java) {
            AgentModelClient.complete(config(), "Translate notification settings to English", tools("first")::execute,
                additionalTools = JSONArray().put(DevelopmentTools.schema()), provider = first)
        }
        assertTrue(store().mayContinue("assistant-task"))
        assertTrue(failure.transcript.any { it.role == "tool" && it.content.contains("not saved in persistent history") })
        assertFalse(failure.transcript.toString().contains("dG9vbHM="))
        assertEquals(path, store().context()!!.getJSONArray("recent_actions").getJSONObject(0).getString("path"))
        rounds = 0
        val resumed = object : AgentProviderClient {
            override val id = "resumed-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                assertTrue(request.config.systemPrompt.contains("Translate notification settings to English"))
                assertTrue(request.config.systemPrompt.contains("Settings"))
                assertTrue(request.config.systemPrompt.contains(revision))
                assertTrue(request.config.systemPrompt.contains(path))
                assertTrue(request.config.systemPrompt.contains(blob))
                assertTrue(request.config.systemPrompt.contains(DevelopmentPolicy.REPOSITORY))
                assertFalse(request.config.systemPrompt.contains("synthetic-token-not-in-model-context"))
                return if (++rounds == 1) toolResponse("reread-after-stop") else {
                    assertEquals("tools", JSONObject((0 until request.messages.length()).map { request.messages.getJSONObject(it) }.last { it.optString("role") == "tool" }.getString("content")).getString("content"))
                    ProviderResponse(JSONObject().put("role", "assistant").put("content", "Source recovered").put("finish_reason", "stop"))
                }
            }
        }
        val result = AgentModelClient.complete(config(), "continue", tools("second")::execute,
            history = failure.transcript, additionalTools = JSONArray().put(DevelopmentTools.schema()), provider = resumed)
        assertEquals("Source recovered", result.content)
        assertEquals(2, reads.size)
        assertTrue(reads.all { it.contains(revision) })
    }
}
