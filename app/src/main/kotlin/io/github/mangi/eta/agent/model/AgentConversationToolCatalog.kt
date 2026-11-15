package io.github.mangi.eta.agent.model

import org.json.JSONObject

/** 只声明当前会话的历史检索，不允许模型指定其他会话身份。 */
internal object AgentConversationToolCatalog {
    const val READ_HISTORY = "conversation_history"

    fun schema(): JSONObject = AgentToolSchema.function(
        name = READ_HISTORY,
        description = "Search or expand full redacted conversation history, including early messages folded by context compaction." +
            "With query, search keywords and return excerpts, message_index, offset and total_chars." +
            "Without query, expand an original message by message_index/offset or paginate sequentially using next_message_index/next_offset." +
            "Continuation uses the same history snapshot; restart from zero to refresh. A page is not the full history. Sensitive raw tool data and temporary images are not persisted.",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()
            .put("query", JSONObject().put("type", "string").put("maxLength", 500))
            .put("message_index", JSONObject().put("type", "integer").put("minimum", 0))
            .put("offset", JSONObject().put("type", "integer").put("minimum", 0))
            .put("max_chars", JSONObject().put("type", "integer").put("minimum", 256).put("maximum", 8000))),
    )
}
