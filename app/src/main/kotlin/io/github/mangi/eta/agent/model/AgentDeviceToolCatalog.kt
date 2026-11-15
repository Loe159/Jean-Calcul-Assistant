package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 常用设备能力的结构化 schema；按风险组决定是否向模型公开。 */
internal object AgentDeviceToolCatalog {
    fun appendTo(
        tools: JSONArray,
        directTools: Boolean,
        sensitiveReadTools: Boolean,
        sensitiveActionTools: Boolean,
    ) {
        AgentPhoneToolCatalog.appendTo(tools, directTools, sensitiveReadTools, sensitiveActionTools)
        if (directTools) appendDirectTools(tools)
        if (sensitiveReadTools) appendSensitiveReadTools(tools)
        if (sensitiveActionTools) appendSensitiveActionTools(tools)
    }

    private fun appendDirectTools(tools: JSONArray) {
        tools
            .put(function(
                "inspect_app",
                "Read an app's version, UID, installation source/path, enabled state and requested permissions without requiring a launcher entry. Only Jean-Calcul's Android user is supported, not work profiles. Paginate permissions with permission_offset/permission_limit.",
                properties(
                    "package_name" to string("Exact Android package name", 255),
                    "user_id" to userId(),
                    "permission_offset" to integer("Permission list offset; default 0", 0, Int.MAX_VALUE),
                    "permission_limit" to integer("Maximum returned permissions; default 100", 1, 200),
                ),
                "package_name",
            ))
            .put(
                function(
                    "set_alarm",
                    "Create a system alarm directly without GUI. Resolve relative dates with get_current_context; hour/minute use device local time. If direct creation is unsupported, the clock page may open instead.",
                    properties(
                        "hour" to integer("0–23", 0, 23),
                        "minute" to integer("0–59", 0, 59),
                        "label" to string("Alarm label, at most 100 characters", 100),
                        "repeat_days" to stringArray(
                            "Repeat weekdays; omitted means the next occurrence only",
                            "mon", "tue", "wed", "thu", "fri", "sat", "sun",
                        ),
                        "vibrate" to boolean("Vibrate; default true"),
                    ),
                    "hour", "minute",
                ),
            )
            .put(
                function(
                    "set_timer",
                    "Create a system timer directly without GUI. duration_seconds must be 1–86400.",
                    properties(
                        "duration_seconds" to integer("Timer duration in seconds", 1, 86_400),
                        "label" to string("Timer label, at most 100 characters", 100),
                    ),
                    "duration_seconds",
                ),
            )
            .put(emptyFunction("device_status", "Read battery, memory, storage, system version and uptime."))
            .put(emptyFunction("network_info", "Read connection type, validation state and basic current Wi-Fi information, excluding saved passwords."))
            .put(limitFunction("top_memory_apps", "List processes with the highest current RSS memory usage."))
            .put(limitFunction("top_storage_apps", "List apps with the highest total application/data/cache storage usage."))
            .put(
                function(
                    "media_control",
                    "Control the current media session directly, without player GUI.",
                    properties(
                        "action" to enumString(
                            "Media action",
                            "play", "pause", "play_pause", "next", "previous", "stop",
                        ),
                    ),
                    "action",
                ),
            )
            .put(
                function(
                    "set_volume",
                    "Set system volume directly without GUI.",
                    properties(
                        "stream" to enumString("Audio stream", "media", "alarm", "ring", "notification", "voice_call", "system"),
                        "percent" to integer("Volume percentage, 0–100", 0, 100),
                        "user_id" to userId(),
                    ),
                    "stream", "percent",
                ),
            )
    }

    private fun appendSensitiveReadTools(tools: JSONArray) {
        AgentPersonalSearchToolCatalog.appendTo(tools)
        tools
            .put(
                function(
                    "get_setting",
                    "Read an Android Settings value. Results may contain sensitive device identifiers and are not persisted.",
                    properties(
                        "namespace" to enumString("Settings namespace", "system", "secure", "global"),
                        "key" to string("Exact settings key", 200),
                        "user_id" to userId(),
                    ),
                    "namespace", "key",
                ),
            )
            .put(
                function(
                    "wifi_credentials",
                    "Read saved Wi-Fi names and passwords. Raw results are not persisted.",
                    properties(
                        "ssid" to string("Optional exact Wi-Fi name", 128),
                        "limit" to integer("Maximum results; default 20", 1, 50),
                    ),
                ),
            )
            .put(
                function(
                    "recent_notifications",
                    "Read current notification titles and bodies. Results are not saved in persistent history.",
                    properties(
                        "package_name" to string("Optional exact app package filter", 255),
                        "limit" to integer("Maximum results; default 10", 1, 20),
                    ),
                ),
            )
            .put(
                function(
                    "search_notification_history",
                    "Search the last 7 days of notification history collected after the user granted access. Raw results are not persisted.",
                    properties(
                        "query" to string("Optional title/body keyword", 200),
                        "package_name" to string("Optional exact package name", 255),
                        "max_age_hours" to integer("Lookback hours; default 24", 1, 168),
                        "limit" to integer("Maximum results; default 20", 1, 50),
                    ),
                ),
            )
            .put(
                function(
                    "recent_app_activity",
                    "Read recently opened apps chronologically. Requires usage access.",
                    properties(
                        "package_name" to string("Optional exact package name", 255),
                        "max_age_hours" to integer("Lookback hours; default 24", 1, 168),
                        "limit" to integer("Maximum results; default 20", 1, 50),
                    ),
                ),
            )
            .put(
                function(
                    "app_usage_summary",
                    "Summarize recent app usage by foreground time. Requires usage access.",
                    properties(
                        "max_age_hours" to integer("Hours to summarize; default 24", 1, 168),
                        "limit" to integer("Maximum results; default 20", 1, 50),
                    ),
                ),
            )
            .put(emptyFunction("get_current_location", "Read the system's last known location without continuous monitoring or activating GPS."))
            .put(emptyFunction("get_device_environment", "Read lock state, Do Not Disturb, ringer, audio outputs and external displays."))
            .put(
                function(
                    "list_alarms",
                    "Read original clock alarm IDs, times, repeat rules and enabled states, with filtering and pagination.",
                    properties(
                        "enabled_only" to boolean("Only enabled alarms; default true"),
                        "query" to string("Optional label keyword", 200),
                        "hour" to integer("Specific hour", 0, 23),
                        "minute" to integer("Specific minute", 0, 59),
                        "offset" to integer("Pagination offset", 0, 10000),
                        "limit" to integer("Maximum results; default 20", 1, 50),
                    ),
                ),
            )
            .put(
                function(
                    "list_active_timers",
                    "Read running or paused timers from the ColorOS clock.",
                    properties("limit" to integer("Maximum results; default 20", 1, 50)),
                ),
            )
            .put(searchFunction("search_clipboard_history", "Search clipboard history saved by the current system keyboard."))
            .put(
                function(
                    "get_health_summary",
                    "Summarize steps, sleep, workouts, heart rate, weight and blood oxygen from system health data; no raw measurement series.",
                    properties("days" to integer("Days to summarize; default 7", 1, 30)),
                ),
            )
            .put(
                function(
                    "read_sms_code",
                    "Extract only 4–8 digit verification codes, sender and time from recent SMS, excluding full message bodies.",
                    properties(
                        "max_age_minutes" to integer("Lookback minutes; default 10", 1, 1_440),
                    ),
                ),
            )
            .put(
                function(
                    "get_logcat",
                    "Read a bounded device log buffer sample; root required. Collect recent scan_lines filtered by PID/tag/level/buffer, then apply since/query and return the latest max_lines. No matches do not prove older records are absent. Inspect scan_start/end, scan_limited, has_more and complete_within_scan. Logs are device-wide.",
                    properties(
                        "query" to string("Optional text filter", 200),
                        "max_lines" to integer("Maximum matching lines; default 200, also subject to a total character limit", 1, 500),
                        "scan_lines" to integer("Recent lines to collect; default 2000, distinct from the return limit", 1, 10_000),
                        "pid" to integer("Collect only this process PID", 1, Int.MAX_VALUE),
                        "tag" to string("Exact log tag: letters, digits, underscores, dots or hyphens", 100)
                            .put("pattern", "^[A-Za-z0-9_.-]{1,100}$"),
                        "level" to enumString("Minimum priority; default V", "V", "D", "I", "W", "E", "F"),
                        "buffer" to enumString("Log buffer; default default", "main", "system", "crash", "events", "radio", "all", "default"),
                        "since" to string("Start time within the sample, ISO 8601 with timezone, e.g. 2026-10-05T08:00:00Z", 64),
                    ),
                ),
            )
            .put(searchFunction("search_media", "Search current media-library images by filename or album path, returning newest-first metadata and content URIs. Use this for latest images; use match=content for historical text/description/tag indexes. Does not read image contents."))
            .put(searchFunction("search_audio", "Search local music and audio by title or filename."))
            .put(searchFunction("search_recordings", "Search local recording files from the media library without reading transcripts or audio content."))
            .put(searchFunction("search_files", "Search shared-storage documents and downloads by filename. Does not traverse other apps' private directories."))
            .put(searchFunction("search_calendar_events", "Search the live calendar. Supply both start_time/end_time for a window of at most 366 days to return instances; without a window, return original event definitions."))
            .put(searchFunction("search_contacts", "Search system contacts, returning names and lookup URIs."))
            .put(searchFunction("search_call_history", "Search call records by number or cached contact name."))
            .put(searchFunction("search_messages", "Search SMS by sender or body keyword. Results are sensitive personal content."))
            .put(searchFunction("search_downloads", "Search system downloads by filename or description."))
            .put(searchFunction("search_notes", "Search notes by title, body or time, preferring the original source. If unavailable, return a clearly marked historical index. Set current_only=true when current state is required."))
            .put(searchFunction("search_coloros_recordings", "Search regular and call recordings from ColorOS Recorder, returning name, duration, type and path."))
            .put(searchFunction("search_recording_summaries", "Search transcripts and notes linked to ColorOS recordings, only when the recorder generated them."))
            .put(searchFunction("search_system_memories", "Search system memory history, summaries and source text. Use domain-specific tools for bills/travel. Does not read the agent's MEMORY.md."))
            .put(searchFunction("search_saved_places", "Search places saved or recognized by system memory."))
            .put(searchFunction("search_personal_orders", "Search food, shopping, parcel, ticket and travel orders recognized by system memory."))
            .put(searchFunction("search_qq_chat_images", "Search QQ chat image cache, returning recent timestamps, sizes, types and private paths. Requires QQ and an existing cache."))
            .put(searchFunction("search_wechat_chat_images", "Search WeChat chat image cache, returning recent timestamps, sizes and private paths. Requires WeChat and an existing cache."))
    }

    private fun appendSensitiveActionTools(tools: JSONArray) {
        tools
            .put(
                function(
                    "set_setting",
                    "Change an Android Settings value and verify readback. user_id defaults to Jean-Calcul's user; global affects the device. Only verified=true confirms the target value. Do not replay unconfirmed changes blindly.",
                    properties(
                        "namespace" to enumString("Settings namespace", "system", "secure", "global"),
                        "key" to string("Exact settings key", 200),
                        "value" to string("New value", 2_000),
                        "user_id" to userId(),
                    ),
                    "namespace", "key", "value",
                ),
            )
            .put(
                function(
                    "set_device_state",
                    "Set Wi-Fi, Bluetooth, mobile data, airplane mode, location, NFC, battery saver, auto-rotate, auto brightness or dark mode, then read back state. Pending asynchronous switches return unconfirmed; check state before replaying.",
                    properties(
                        "target" to JSONObject().put("type", "string").put("enum", JSONArray(io.github.mangi.eta.agent.tool.SystemStateControls.targets)),
                        "enabled" to boolean("true enables; false disables"),
                        "user_id" to userId(),
                    ),
                    "target", "enabled",
                ),
            )
            .put(
                function(
                    "app_state_control",
                    "Stop, freeze or unfreeze an exact package in Jean-Calcul's Android user, including system apps, then read back stopped/enabled state. Only verified=true confirms success; does not cross users or work profiles.",
                    properties(
                        "package_name" to string("Exact Android package name", 255),
                        "action" to enumString("Action", "force_stop", "freeze", "unfreeze"),
                        "user_id" to userId(),
                    ),
                    "package_name", "action",
                ),
            )
    }

    private fun emptyFunction(name: String, description: String): JSONObject =
        function(name, description, properties())

    private fun limitFunction(name: String, description: String): JSONObject =
        function(
            name,
            description,
            properties("limit" to integer("Maximum results; default 10", 1, 30)),
        )

    private fun searchFunction(name: String, description: String): JSONObject =
        function(
            name,
            description,
            if (name in setOf("search_notes", "search_system_memories", "search_calendar_events", "search_media", "search_files")) {
                AgentPersonalSearchToolCatalog.searchProperties().also { properties ->
                    if (name == "search_notes") properties.put("current_only", boolean("Use only the current original notes source without historical index fallback"))
                    if (name in setOf("search_media", "search_files")) properties.put("match", enumString("name searches current filenames; content searches the historical content index", "name", "content"))
                }
            } else properties("query" to string("Optional keyword, at most 200 characters", 200), "limit" to integer("Maximum results; default 10", 1, 30)),
        )

    private fun function(
        name: String,
        description: String,
        properties: JSONObject,
        vararg required: String,
    ): JSONObject =
        AgentToolSchema.function(
            name = name,
            description = description,
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .also { schema ->
                    if (required.isNotEmpty()) schema.put("required", JSONArray(required.toList()))
                },
        )

    private fun properties(vararg entries: Pair<String, JSONObject>): JSONObject =
        JSONObject().also { target -> entries.forEach { (name, schema) -> target.put(name, schema) } }

    private fun string(description: String, maxLength: Int? = null): JSONObject =
        JSONObject()
            .put("type", "string")
            .put("description", description)
            .also { schema -> maxLength?.let { schema.put("maxLength", it) } }

    private fun boolean(description: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", description)

    private fun userId(): JSONObject =
        integer("Android user ID, defaulting to Jean-Calcul's user. Other users/work profiles return USER_SCOPE_UNSUPPORTED.", 0, Int.MAX_VALUE)

    private fun integer(description: String, minimum: Int, maximum: Int): JSONObject =
        JSONObject()
            .put("type", "integer")
            .put("minimum", minimum)
            .put("maximum", maximum)
            .put("description", description)

    private fun enumString(description: String, vararg values: String): JSONObject =
        string(description).put("enum", JSONArray(values.toList()))

    private fun stringArray(description: String, vararg values: String): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("items", enumString(description, *values))
            .put("uniqueItems", true)
            .put("maxItems", 7)
            .put("description", description)
}
