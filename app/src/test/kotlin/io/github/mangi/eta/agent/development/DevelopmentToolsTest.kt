package io.github.mangi.eta.agent.development

import io.github.mangi.eta.agent.model.*
import io.github.mangi.eta.agent.runtime.AgentRunController
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DevelopmentToolsTest {
    private val base = "a".repeat(40)
    private val tree = "b".repeat(40)
    private val blob = "c".repeat(40)
    private val commit = "d".repeat(40)
    private class State : DevelopmentState {
        override var enabled = true
        var current: JSONObject? = null
        override fun token() = "fixture-github-token-never-persisted"
        override fun proposal() = current
        override fun saveProposal(proposal: JSONObject) { current = JSONObject(proposal.toString()) }
    }
    private fun args(action: String) = JSONObject().put("action", action).put("title", "fix: capitalize label")
        .put("summary", "Capitalize a visible label")
        .put("files", org.json.JSONArray().put(JSONObject().put("path", "app/src/main/Label.kt").put("blob_sha", blob)
            .put("edits", org.json.JSONArray().put(JSONObject().put("before", "tools").put("after", "Tools")))))
    private fun call(args: JSONObject) = AgentModelClient.ToolCall("fixture-call", DevelopmentPolicy.TOOL, args.toString())

    @Test fun proposesRealGitObjectsAndPrWithoutUploadingScreenshotContextOrLeakingToken() {
        val state = State()
        val seen = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        val responses = ArrayDeque(listOf(
            """{"sha":"$base","commit":{"tree":{"sha":"$tree"}}}""",
            """{"type":"file","encoding":"base64","size":5,"sha":"$blob","content":"dG9vbHM="}""",
            """{"sha":"$tree"}""", """{"sha":"$commit"}""", "[]", "{}", "[]",
            """{"number":63,"html_url":"https://github.com/Loe159/Jean-Calcul-Assistant/pull/63"}""",
            """{"state":"open","merged":false,"html_url":"https://github.com/Loe159/Jean-Calcul-Assistant/pull/63","head":{"sha":"$commit"}}""",
            """{"workflow_runs":[]}""",
        ))
        val client = DevelopmentGitHubClient(state::token) { request ->
            seen += request
            val buffer = okio.Buffer(); request.body?.writeTo(buffer); bodies += buffer.readUtf8()
            responses.removeFirst()
        }
        val tools = DevelopmentTools(state, client, "fixture-run", "Here, capitalize the label", "Settings", true)
        var executed: AgentModelClient.ToolResult? = null
        var rounds = 0
        val provider = object : AgentProviderClient {
            override val id = "development-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                rounds++
                assertTrue((0 until request.tools.length()).any { request.tools.getJSONObject(it).getJSONObject("function").getString("name") == DevelopmentPolicy.TOOL })
                return if (rounds == 1) {
                    assertTrue(request.messages.toString().contains("private-fixture-app-context"))
                    ProviderResponse(JSONObject().put("role", "assistant").put("content", "")
                        .put("tool_calls", org.json.JSONArray().put(JSONObject().put("id", "develop-fixture").put("type", "function")
                            .put("function", JSONObject().put("name", DevelopmentPolicy.TOOL).put("arguments", args("propose").toString()))))
                        .put("finish_reason", "tool_calls"))
                } else {
                    assertTrue(request.messages.toString().contains("/pull/63"))
                    ProviderResponse(JSONObject().put("role", "assistant").put("content", "PR ready").put("finish_reason", "stop"))
                }
            }
        }
        val completed = AgentModelClient.complete(
            config = AgentModelClient.ModelConfig(baseUrl = "http://127.0.0.1:8000/v1", model = "fixture", browserTools = false),
            prompt = "Here, capitalize the label", provider = provider,
            additionalTools = org.json.JSONArray().put(DevelopmentTools.schema()), assistantScreenContext = "private-fixture-app-context",
            toolExecutor = AgentModelClient.ToolExecutor { tools.execute(it).also { result -> executed = result } },
        )
        assertEquals("PR ready", completed.content)
        assertEquals(2, rounds)
        val result = executed!!
        assertTrue(JSONObject(result.content).getBoolean("ok"))
        assertTrue(result.sensitive)
        assertEquals(63, state.proposal()!!.getInt("number"))
        assertTrue(seen.all { it.url.host == "api.github.com" && it.header("Authorization") == "Bearer ${state.token()}" })
        assertEquals(1, seen.count { it.url.encodedPath.endsWith("/pulls") && it.method == "POST" })
        assertTrue(bodies.any { it.contains("Tools") })
        assertFalse(result.content.contains(state.token()!!))
        assertFalse(bodies.any { it.contains("data:image") || it.contains("assistant_screen_context") || it.contains("private-fixture-app-context") })
        assertTrue(responses.isEmpty())
    }
    @Test fun changedSourceAndDisabledContextCannotWrite() {
        val state = State()
        val requests = mutableListOf<Request>()
        val responses = ArrayDeque(listOf(
            """{"sha":"$base","commit":{"tree":{"sha":"$tree"}}}""",
            """{"type":"file","encoding":"base64","size":5,"sha":"${"e".repeat(40)}","content":"dG9vbHM="}""",
        ))
        val client = DevelopmentGitHubClient(state::token) { requests += it; responses.removeFirst() }
        val tools = DevelopmentTools(state, client, "fixture-run", "Fix this label", "Settings", true)
        assertFalse(JSONObject(tools.execute(call(args("propose"))).content).getBoolean("ok"))
        assertTrue(requests.all { it.method == "GET" })
        val denied = DevelopmentTools(state, client, "other", "Fix this", "ForeignApp", false)
        val count = requests.size
        assertFalse(JSONObject(denied.execute(call(args("propose"))).content).getBoolean("ok"))
        assertEquals(count, requests.size)
    }
    @Test fun logDownloadsOnlyAcceptGithubActionsStorageWithoutUserInfoOrInsecureSchemes() {
        assertTrue(DevelopmentGitHubClient.trustedLogHost("https://fixture.blob.core.windows.net/job/log?sig=fixture"))
        assertTrue(DevelopmentGitHubClient.trustedLogHost("https://pipelines.actions.githubusercontent.com/job/log"))
        for (url in listOf("https://evil.example/log", "http://fixture.blob.core.windows.net/log", "https://fixture.blob.core.windows.net.evil.example/log", "https://user@fixture.blob.core.windows.net/log")) {
            assertFalse(DevelopmentGitHubClient.trustedLogHost(url))
        }
    }
    @Test fun cannotMergeAPrWhoseHeadChangedAfterUserReview() {
        val state = State()
        val requests = mutableListOf<Request>()
        val client = DevelopmentGitHubClient(state::token) { request ->
            requests += request
            """{"state":"open","merged":false,"base":{"ref":"${DevelopmentPolicy.BASE}"},"head":{"ref":"self-improve/fixture","sha":"${"f".repeat(40)}","repo":{"full_name":"${DevelopmentPolicy.REPOSITORY}"}}}"""
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.approve(JSONObject().put("number", 63).put("branch", "self-improve/fixture").put("head", commit))
        }
        assertTrue(requests.all { it.method == "GET" })
    }
}
