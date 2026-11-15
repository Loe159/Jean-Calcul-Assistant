package io.github.mangi.eta.data.provider

import io.github.mangi.eta.data.model.GeminiProviderSetting
import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes

internal object BuiltinProviders {
    const val DEFAULT_SYSTEM_PROMPT =
        "You are Jean-Calcul, a personal Android assistant. Respond in the user’s language, " +
            "concisely and naturally. Use available tools for requested tasks and verify their results."

    const val OPENAI_ID = "builtin-openai"
    const val ANTHROPIC_ID = "builtin-anthropic"
    const val BAILIAN_ID = "builtin-dashscope"
    const val DEEPSEEK_ID = "builtin-deepseek"
    const val KIMI_ID = "builtin-kimi"
    const val MIMO_ID = "builtin-mimo"
    const val MINIMAX_ID = "builtin-minimax"
    const val STEPFUN_ID = "builtin-stepfun"
    const val SILICONFLOW_ID = "builtin-siliconflow"
    const val OPENROUTER_ID = "builtin-openrouter"
    const val XAI_ID = "builtin-xai"
    const val GEMINI_ID = "builtin-gemini"

    const val VLLM_ID = "builtin-vllm"
    val PROVIDERS: List<ProviderSetting> = listOf(
        OpenAiCompatibleProviderSetting(
            id = OPENAI_ID,
            name = "ChatGPT / Codex",
            baseUrl = "https://chatgpt.com/backend-api/codex",
            sourceType = ProviderSourceTypes.OPENAI,
            authMode = io.github.mangi.eta.data.model.ProviderAuthModes.OPENAI_CHATGPT,
            isBuiltIn = true,
            sortOrder = 0,
            systemPrompt = DEFAULT_SYSTEM_PROMPT,
            endpointMode = OpenAiEndpointMode.RESPONSES,
        ),
        OpenAiCompatibleProviderSetting(
            id = VLLM_ID,
            name = "vLLM local",
            baseUrl = "http://127.0.0.1:8000/v1",
            sourceType = ProviderSourceTypes.CUSTOM,
            isBuiltIn = true,
            sortOrder = 1,
            systemPrompt = DEFAULT_SYSTEM_PROMPT,
            endpointMode = OpenAiEndpointMode.CHAT_COMPLETIONS,
        ),
    )

    fun providerById(id: String): ProviderSetting? =
        PROVIDERS.firstOrNull { it.id == id }
}
