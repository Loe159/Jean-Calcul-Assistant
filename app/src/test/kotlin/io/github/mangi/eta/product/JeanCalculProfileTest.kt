package io.github.mangi.eta.product

import io.github.mangi.eta.agent.model.*
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.tool.AgentToolCapabilities
import io.github.mangi.eta.data.model.*
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.ui.navigation.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JeanCalculProfileTest {
    @Test fun onlyChatGptAndLocalServersCanBeUsed() {
        assertEquals(setOf(BuiltinProviders.OPENAI_ID, BuiltinProviders.VLLM_ID), BuiltinProviders.PROVIDERS.map { it.id }.toSet())
        assertTrue(BuiltinProviders.PROVIDERS.all(JeanCalculProfile::allowsProvider))
        assertTrue(JeanCalculProfile.allowsProvider(CustomProviderSetting(id="legacy-local", name="local", baseUrl="http://localhost:8000/v1")))
        val chatgpt = BuiltinProviders.PROVIDERS.first { it.id == BuiltinProviders.OPENAI_ID }
        assertEquals(ProviderAuthModes.OPENAI_CHATGPT, chatgpt.authMode)
        assertFalse(JeanCalculProfile.allowsProvider((chatgpt as OpenAiCompatibleProviderSetting).copy(authMode = ProviderAuthModes.API_KEY)))
        assertFalse(JeanCalculProfile.allowsProvider(chatgpt.copy(authMode = ProviderAuthModes.XAI_GROK)))
        assertFalse(JeanCalculProfile.allowsProvider(AnthropicProviderSetting(id = "other", name = "other", baseUrl = "https://example.invalid")))
    }
    @Test fun privilegedDeviceStillCannotAdvertiseRemovedTools() {
        val catalog = AgentToolCatalog.build(terminalTools = true, browserTools = true,
            deviceSensitiveReadTools = true, deviceSensitiveActionTools = true, memoryTools = true,
            capabilities = AgentToolCapabilities(rootAvailable = true, colorOs = true, lsposedAvailable = true))
        val names = (0 until catalog.length()).map { catalog.getJSONObject(it).getJSONObject("function").getString("name") }.toSet()
        assertTrue(names.containsAll(listOf("terminal", "web_search", "create_calendar_event", "set_flashlight", "search_messages", "set_device_state", "memory_write")))
        assertFalse(names.any { !JeanCalculProfile.allowsTool(it) })
        assertFalse(names.contains("create_note"))
        assertFalse(names.contains("search_system_memories"))
        val terminal = (0 until catalog.length()).map(catalog::getJSONObject).first { it.getJSONObject("function").getString("name") == "terminal" }
        val environments = terminal.getJSONObject("function").getJSONObject("parameters").getJSONObject("properties").getJSONObject("environment").getJSONArray("enum")
        assertEquals(listOf("android"), (0 until environments.length()).map(environments::getString))
    }
    @Test fun restoredAndProgrammaticNavigationCannotReachExcludedFeatures() {
        listOf(AppRoute.Characters, AppRoute.CharacterEditor(), AppRoute.DataBackup, AppRoute.LinuxEnvironment,
            AppRoute.CommunityCatalog, AppRoute.ModelProviderNew(NewProviderType.Anthropic)).forEach { assertFalse(it.isAvailableInJeanCalcul()) }
        listOf(AppRoute.Home, AppRoute.Terminal, AppRoute.Tools, AppRoute.Memory, AppRoute.SystemEnhance,
            AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)).forEach { assertTrue(it.isAvailableInJeanCalcul()) }
    }
    @Test fun localServerNeedsNoApiKeyAndRunsTheOrdinaryAgentLoop() {
        val config = AgentModelClient.ModelConfig(baseUrl = "http://127.0.0.1:8000/v1", apiKey = "", model = "fixture", systemPrompt = "test")
        var called = false
        val provider = object : AgentProviderClient {
            override val id = "test-fixture"
            override val capabilities = OpenAiChatCompletionsProvider.capabilities
            override fun complete(request: ProviderRequest, runController: AgentRunController, onEvent: (ProviderEvent) -> Unit): ProviderResponse {
                called = true
                assertEquals("", request.config.apiKey)
                return ProviderResponse(JSONObject("""{"role":"assistant","content":"fixture reply","finish_reason":"stop"}"""))
            }
        }
        val result = AgentModelClient.complete(config, "test", AgentModelClient.ToolExecutor { error("No device action expected") }, provider = provider)
        assertTrue(called)
        assertEquals("fixture reply", (result as AgentModelClient.ModelResponse.Text).content)
    }
}
