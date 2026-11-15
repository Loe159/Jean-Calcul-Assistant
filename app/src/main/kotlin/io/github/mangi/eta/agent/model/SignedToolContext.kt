package io.github.mangi.eta.agent.model

import org.json.JSONArray

/** 签名工具链完成前保持请求前缀，避免压缩或目录刷新破坏下一轮回传。 */
internal object SignedToolContext {
    fun hasPendingResponse(messages: JSONArray): Boolean =
        AnthropicEphemeralState.hasPendingToolResponse(messages) ||
            GeminiEphemeralState.hasPendingToolResponse(messages)

    fun compactionFailure(messages: JSONArray): AgentModelFailure =
        if (GeminiEphemeralState.hasPendingToolResponse(messages)) {
            AgentModelFailure(
                "GEMINI_THINKING_CONTEXT_LOCKED",
                false,
                "Gemini tool exchange is incomplete. Signed context cannot be compacted before tool results are submitted.",
            )
        } else {
            AgentModelFailure(
                "ANTHROPIC_THINKING_CONTEXT_LOCKED",
                false,
                "Anthropic reasoning signature binds the original context. Submit tool results before compacting.",
            )
        }
}
