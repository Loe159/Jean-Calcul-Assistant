package io.github.mangi.eta.agent.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AgentPersonalResultFormatterTest {
    @Test
    fun memoryGetPresentsNaturalQueryOrReadStatusWithoutRawLineOrByteNumbers() {
        val querySuccess = summarize("memory_get", """{"matched_lines":3,"line_count":500,"bytes":15000}""")
        assertEquals("Found 3 related Memory entries", querySuccess)
        assertFalse(querySuccess!!.contains("行"))
        assertFalse(querySuccess.contains("字节"))

        val queryEmpty = summarize("memory_get", """{"matched_lines":0,"line_count":500,"bytes":15000}""")
        assertEquals("No related Memory entries", queryEmpty)

        val fullRead = summarize("memory_get", """{"line_count":200,"bytes":8000}""")
        assertEquals("Read Memory", fullRead)
        assertFalse(fullRead!!.contains("行"))
        assertFalse(fullRead.contains("字节"))

        val partialRead = summarize("memory_get", """{"has_more":true,"line_count":200,"bytes":8000}""")
        assertEquals("Beginning read Memory", partialRead)

        // Story memory
        val charQuery = summarize("character_memory_get", """{"matched_lines":2}""")
        assertEquals("Found 2 related Story entries", charQuery)
        val charEmpty = summarize("character_memory_get", """{"matched_lines":0}""")
        assertEquals("No related Story entries", charEmpty)
        val charRead = summarize("character_memory_get", """{}""")
        assertEquals("Read Story memory", charRead)
    }

    @Test
    fun memoryWritePresentsActionConclusionWithoutByteOrLineCounts() {
        assertEquals("Saved Memory", summarize("memory_write", """{"mode":"append","bytes":5000,"line_count":120}"""))
        assertEquals("Updated Memory", summarize("memory_write", """{"mode":"replace_range","bytes":5000,"line_count":120}"""))
        assertEquals("Cleared Memory", summarize("memory_write", """{"mode":"clear"}"""))

        assertEquals("Saved Story memory", summarize("character_memory_write", """{"mode":"append"}"""))
        assertEquals("Updated Story memory", summarize("character_memory_write", """{"mode":"replace_range"}"""))
        assertEquals("Cleared Story memory", summarize("character_memory_write", """{"mode":"clear"}"""))
    }

    @Test
    fun memoryArgumentsAreCleanAndConcise() {
        assertEquals("Append Memory", AgentPersonalResultFormatter.summarizeArguments("memory_write", """{"mode":"append","content":"abc"}"""))
        assertEquals("Update Memory", AgentPersonalResultFormatter.summarizeArguments("memory_write", """{"mode":"replace_range","content":"abc"}"""))
        assertEquals("Clear Memory", AgentPersonalResultFormatter.summarizeArguments("memory_write", """{"mode":"clear"}"""))
        assertEquals("Search Memory", AgentPersonalResultFormatter.summarizeArguments("memory_get", """{"query":"咖啡偏好"}"""))
        assertEquals("Read Memory", AgentPersonalResultFormatter.summarizeArguments("memory_get", """{}"""))

        assertEquals("Append Story memory", AgentPersonalResultFormatter.summarizeArguments("character_memory_write", """{"mode":"append"}"""))
        assertEquals("Search Story memory", AgentPersonalResultFormatter.summarizeArguments("character_memory_get", """{"query":"身世"}"""))
    }

    @Test
    fun personalSearchesPresentHumanReadableUnitsAndEmptyStates() {
        val cases = listOf(
            Triple("search_system_memories", "Found 3 system memories", "No related system memories"),
            Triple("search_notes", "Found 3 notes", "No related notes"),
            Triple("search_calendar_events", "Found 3 events", "No related events"),
            Triple("search_contacts", "Found 3 contacts", "No matching contacts"),
            Triple("search_messages", "Found 3 messages", "No related messages"),
            Triple("search_call_history", "Found 3 call records", "No call records"),
            Triple("search_saved_places", "Found 3 places", "No related places"),
            Triple("search_personal_orders", "Found 3 orders", "No related orders"),
            Triple("search_bills", "Found 3 bills", "No bill records"),
            Triple("search_todos", "Found 3 tasks", "No tasks"),
            Triple("search_flights", "Found 3 flights", "No flights"),
            Triple("search_hotels", "Found 3 hotel bookings", "No bookings"),
            Triple("search_trains", "Found 3 trains", "No train trips"),
            Triple("search_daily_events", "Found 3 life events", "No life events"),
            Triple("search_memory_collections", "Found 3 memory collections", "No memory collections"),
            Triple("search_recordings", "Found 3 recordings", "No recordings"),
            Triple("search_recording_summaries", "Found 3 recording summaries", "No recording summaries"),
            Triple("recent_notifications", "Found 3 notifications", "No notifications"),
            Triple("search_clipboard_history", "Found 3 clipboard entries", "No clipboard entries"),
            Triple("search_downloads", "Found 3 downloads", "No downloads"),
            Triple("search_media", "Found 3 media files", "No matching media files"),
            Triple("search_qq_chat_images", "Found 3 chat images", "No chat images"),
        )

        cases.forEach { (tool, expectedFound, expectedEmpty) ->
            val found = summarize(tool, """{"items":[{},{},{}]}""")
            assertEquals("Tool $tool found summary mismatch", expectedFound, found)
            val empty = summarize(tool, """{"items":[]}""")
            assertEquals("Tool $tool empty summary mismatch", expectedEmpty, empty)
        }
    }

    @Test
    fun personalSearchWithPaginationAndPartialScopePreservesIndicators() {
        val result = summarize("search_messages", """{"items":[{},{}],"has_more":true,"partial":true}""")
        assertEquals("Found 2 messages · More available · Limited collection range", result)
    }

    @Test
    fun verificationCodeAndSpecialTools() {
        assertEquals("Verification code read", summarize("read_sms_code", """{"items":[{"code":"123456"}]}"""))
        assertEquals("No new verification code", summarize("read_sms_code", """{"items":[]}"""))
        assertEquals("Details read", summarize("read_personal_item", """{"id":"1"}"""))
        assertEquals("Bills summarized", summarize("summarize_bills", """{"total":100}"""))
    }

    private fun summarize(toolName: String, json: String): String? =
        AgentPersonalResultFormatter.summarize(toolName, JSONObject(json).also {
            if (!it.has("ok")) it.put("ok", true)
        })
}
