package io.github.mangi.eta.agent.model

import org.json.JSONObject

/**
 * 个人数据检索与记忆工具结果摘要。
 * 面向用户展示自然流畅的结论，不暴露私密正文，不堆砌底层字节数或行数等机器指标。
 */
internal object AgentPersonalResultFormatter {
    fun summarizeArguments(toolName: String, argumentsJson: String): String? = runCatching {
        val args = JSONObject(argumentsJson)
        val isCharacter = toolName.startsWith("character_")
        val label = if (isCharacter) "Story memory" else "Memory"
        when (toolName) {
            "memory_get", "character_memory_get" -> {
                val query = args.optString("query").trim()
                if (query.isNotEmpty()) "Search$label" else "Read$label"
            }
            "memory_write", "character_memory_write" -> {
                when (args.optString("mode")) {
                    "append" -> "Append$label"
                    "clear" -> "Clear$label"
                    else -> "Update$label"
                }
            }
            else -> null
        }
    }.getOrNull()

    fun summarize(toolName: String, json: JSONObject): String? {
        if (json.opt("ok") == false || json.optString("status") in setOf("unconfirmed", "failed")) return null

        when (toolName) {
            "memory_get", "character_memory_get" -> return summarizeMemoryGet(toolName, json)
            "memory_write", "character_memory_write" -> return summarizeMemoryWrite(toolName, json)
            "read_sms_code" -> {
                val count = itemCount(json)
                return if (count != null && count > 0) "Verification code read" else "No new verification code"
            }
            "read_personal_item" -> return "Details read"
            "summarize_bills" -> return "Bills summarized"
        }

        val target = SEARCH_TARGETS[toolName] ?: return null
        val count = itemCount(json) ?: return null
        return if (count == 0) {
            target.emptyText
        } else {
            val base = "Found $count ${target.unit}"
            val suffix = buildList {
                if (json.optBoolean("has_more") || json.optBoolean("truncated")) add("More available")
                if (json.optBoolean("partial") || json.optBoolean("scan_limited")) add("Limited collection range")
            }.joinToString(" · ")
            if (suffix.isNotEmpty()) "$base · $suffix" else base
        }
    }

    private fun summarizeMemoryGet(toolName: String, json: JSONObject): String {
        val isCharacter = toolName.startsWith("character_")
        val noun = if (isCharacter) "Story" else "Memory"
        val matchedLines = json.optInt("matched_lines", -1)
        if (matchedLines >= 0) {
            return if (matchedLines > 0) "Found $matchedLines related entries$noun" else "No related entries$noun"
        }
        val label = if (isCharacter) "Story memory" else "Memory"
        return if (json.optBoolean("has_more")) "Beginning read$label" else "Read$label"
    }

    private fun summarizeMemoryWrite(toolName: String, json: JSONObject): String {
        val isCharacter = toolName.startsWith("character_")
        val noun = if (isCharacter) "Story memory" else "Memory"
        return when (json.optString("mode")) {
            "clear" -> "Cleared$noun"
            "append" -> "Saved$noun"
            else -> "Updated$noun"
        }
    }

    private fun itemCount(json: JSONObject): Int? =
        (json.optJSONArray("items") ?: json.optJSONArray("results"))?.length()
            ?: (json.opt("count") as? Number)?.toInt()?.takeIf { it >= 0 }

    private data class SearchTarget(val unit: String, val emptyText: String)

    private val SEARCH_TARGETS = mapOf(
        "search_system_memories" to SearchTarget(" system memories", "No related system memories"),
        "search_coloros_memories" to SearchTarget(" system memories", "No related system memories"),
        "search_notes" to SearchTarget(" notes", "No related notes"),
        "search_coloros_notes" to SearchTarget(" notes", "No related notes"),
        "search_calendar_events" to SearchTarget(" events", "No related events"),
        "search_contacts" to SearchTarget(" contacts", "No matching contacts"),
        "search_messages" to SearchTarget(" messages", "No related messages"),
        "search_call_history" to SearchTarget(" call records", "No call records"),
        "search_saved_places" to SearchTarget(" places", "No related places"),
        "search_personal_orders" to SearchTarget(" orders", "No related orders"),
        "search_bills" to SearchTarget(" bills", "No bill records"),
        "search_todos" to SearchTarget(" tasks", "No tasks"),
        "search_calendar_todos" to SearchTarget(" tasks", "No tasks"),
        "search_flights" to SearchTarget(" flights", "No flights"),
        "search_hotels" to SearchTarget(" hotel bookings", "No bookings"),
        "search_trains" to SearchTarget(" trains", "No train trips"),
        "search_daily_events" to SearchTarget(" life events", "No life events"),
        "search_memory_collections" to SearchTarget(" memory collections", "No memory collections"),
        "search_recordings" to SearchTarget(" recordings", "No recordings"),
        "search_coloros_recordings" to SearchTarget(" recordings", "No recordings"),
        "search_recording_summaries" to SearchTarget(" recording summaries", "No recording summaries"),
        "recent_notifications" to SearchTarget(" notifications", "No notifications"),
        "search_notification_history" to SearchTarget(" notifications", "No notifications"),
        "search_clipboard_history" to SearchTarget(" clipboard entries", "No clipboard entries"),
        "search_downloads" to SearchTarget(" downloads", "No downloads"),
        "search_media" to SearchTarget(" media files", "No matching media files"),
        "search_audio" to SearchTarget(" audio files", "No matching audio files"),
        "search_files" to SearchTarget(" files", "No matching files"),
        "search_qq_chat_images" to SearchTarget(" chat images", "No chat images"),
        "search_wechat_chat_images" to SearchTarget(" chat images", "No chat images"),
    )
}
