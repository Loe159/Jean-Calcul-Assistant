package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.subagent.AgentSubagentToolCatalog
import io.github.mangi.eta.agent.subagent.SubagentActivitySnapshot
import io.github.mangi.eta.agent.subagent.SubagentProfile
import java.net.URI
import java.util.Locale
import org.json.JSONObject

/** 工具摘要面向用户展示，不包含敏感参数；终端命令通过独立字段提供给用户核对。 */
internal class AgentTraceFormatter {
    fun summarizeArguments(toolCall: AgentModelClient.ToolCall): String =
        when (toolCall.name) {
            BROWSER_TOOL_NAME -> summarizeBrowserArguments(toolCall.argumentsJson)
            "web_search" -> "Search web"
            "fetch_url" -> summarizeFetchArguments(toolCall.argumentsJson)
            "open_uri" -> summarizeOpenUriArguments(toolCall.argumentsJson)
            in AgentTerminalToolCatalog.names -> summarizeTerminalArguments(toolCall.name, toolCall.argumentsJson)
            "run_command" -> "Run command · Android"
            "write_file" -> summarizeTextLength("Write file", toolCall.argumentsJson, "content")
            "read_file" -> "Read file"
            "list_directory" -> "List directory"
            "edit_file" -> "Edit file"
            "stat_file" -> "File information"
            "glob_files" -> "Find files by path"
            "grep_files" -> "Search file contents"
            "inspect_app" -> "Inspect app"
            "type_text" -> summarizeTypeText(toolCall.argumentsJson)
            "input_text" -> summarizeTextLength("Type text", toolCall.argumentsJson, "text")
            "replace_text" -> summarizeTextLength("Replace text", toolCall.argumentsJson, "text")
            "paste_text", "set_clipboard" ->
                summarizeTextLength("Paste text", toolCall.argumentsJson, "text")
            "clear_text" -> "Clear text"
            "get_clipboard" -> "Read clipboard"
            "search_apps" -> summarizeQueryArguments("Search apps", toolCall.argumentsJson)
            "launch_app" -> "Open app"
            "present_to_user" -> "Present to main screen"
            "get_current_context" -> "Read time and location"
            "observe_screen" -> summarizeObservationArguments(toolCall.argumentsJson)
            "tap" -> summarizePointArguments("Tap screen", toolCall.argumentsJson)
            "long_press" -> summarizePointArguments("Long press screen", toolCall.argumentsJson)
            "tap_area" -> "Tap region"
            "tap_element" -> summarizeElementArguments("Tap element", toolCall.argumentsJson)
            "long_press_element" -> summarizeElementArguments("Long press element", toolCall.argumentsJson)
            "swipe" -> "Swipe screen"
            "scroll" -> summarizeScrollArguments("Scroll screen", toolCall.argumentsJson)
            "scroll_element" ->
                summarizeScrollArguments("Scroll element", toolCall.argumentsJson, withIndex = true)
            "press_key" -> summarizePressKeyArguments(toolCall.argumentsJson)
            "wait" -> summarizeWaitArguments(toolCall.argumentsJson)
            "wait_for_text" -> "Wait for text"
            "wait_for_package" -> "Wait for app"
            "open_system_panel" -> "Open system panel"
            "read_image" -> "View image"
            AgentSubagentToolCatalog.TOOL_NAME -> summarizeSubagentArguments(toolCall.argumentsJson)
            AgentSubagentToolCatalog.PROFILE_TOOL_NAME -> summarizeSubagentProfileArguments(toolCall.argumentsJson)
            AgentConversationToolCatalog.READ_HISTORY -> "Read conversation history"
            "memory_get", "character_memory_get", "memory_write", "character_memory_write" ->
                AgentPersonalResultFormatter.summarizeArguments(toolCall.name, toolCall.argumentsJson) ?: "Memory"
            "skills_list" -> "List skills"
            "skills_read" -> "Read skill"
            "skills_read_resource" -> "Read skill resource"
            "skills_list_curated" -> "Browse curated skills"
            "skills_inspect_github" -> "Skill details"
            "skills_install_from_github" -> "Install skill"
            else -> {
                val label = DEVICE_ACTION_LABELS[toolCall.name]
                    ?: AgentPhoneToolCatalog.entries.firstOrNull { it.name == toolCall.name }?.title
                    ?: io.github.mangi.eta.agent.context.PersonalSearchTools.searches.firstOrNull { it.name == toolCall.name }?.title
                    ?: when (toolCall.name) { "search_notes" -> "Search notes"; "search_system_memories" -> "Search system memories"; "read_personal_item" -> "Read search details"; "summarize_bills" -> "Summarize bills"; else -> null }
                when {
                    label == null -> "Preparing"
                    toolCall.name.startsWith("search_") ->
                        summarizeQueryArguments(label, toolCall.argumentsJson)
                    else -> label
                }
            }
        }

    /** 命令以脱敏后的用户可见投影进入运行轨迹；日志仍只记录长度。 */
    fun displayCommand(toolCall: AgentModelClient.ToolCall): String? =
        if (toolCall.name == AgentSubagentToolCatalog.TOOL_NAME) {
            SubagentActivitySnapshot.initialFromArguments(toolCall.argumentsJson)?.encode()
        } else if (toolCall.name in AgentTerminalToolCatalog.names || toolCall.name == "run_command") {
            runCatching {
                JSONObject(toolCall.argumentsJson)
                    .optString("command")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= MAX_DISPLAY_COMMAND_CHARS }
                    ?.redactDisplaySecrets()
            }.getOrNull()
        } else {
            null
        }

    private fun String.redactDisplaySecrets(): String =
        replace(SENSITIVE_ASSIGNMENT) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
            .replace(SENSITIVE_FLAG) { match ->
                "${match.groupValues[1]}<redacted>"
            }
            .replace(SENSITIVE_HEADER) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
            }

    /** 外部 URI 摘要不记录 path、query、fragment 或用户信息。 */
    fun summarizeOpenUriArguments(argumentsJson: String): String =
        runCatching {
            val raw = JSONObject(argumentsJson).optString("uri").trim()
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()?.take(24)
            val host = uri.host?.lowercase()?.take(160)
            listOfNotNull("Open in external app", scheme, host).joinToString(" · ")
        }.getOrDefault("Open in external app")

    /** browser_use 摘要只暴露动作和安全提取的 host。 */
    fun summarizeBrowserArguments(argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            val action = arguments.optString("action").browserActionLabel()
            val host = safeHttpHost(arguments.optString("url"))
            listOfNotNull(action, host).joinToString(" · ")
        }.getOrElse { "Browser action" }

    private fun summarizeFetchArguments(argumentsJson: String): String = runCatching {
        val arguments = JSONObject(argumentsJson)
        val label = if (arguments.has("document_id")) "Continue reading page" else "Read page"
        listOfNotNull(label, safeHttpHost(arguments.optString("url"))?.removePrefix("www.")).joinToString(" · ")
    }.getOrDefault("Read page")

    private fun summarizeTerminalArguments(toolName: String, argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            val action = terminalActionLabel(toolName, arguments.optString("action", if (toolName == "terminal") "exec" else ""))
            val hasEnvironment = !arguments.has("session_id") && (
                toolName == "terminal" && arguments.optString("action", "exec") in setOf("open", "exec", "open_and_exec") ||
                    arguments.optString("action") in setOf("start", "daemon_start"))
            val environment = if (hasEnvironment) arguments.optString("environment", "android").terminalEnvironmentLabel() else null
            val identity = arguments.optString("identity")
                .takeIf { it == "root" || it == "user" }
            buildList {
                add(terminalLabel(toolName))
                add(action)
                environment?.let(::add)
                identity?.let(::add)
                if (arguments.optBoolean("async", false)) add("Background")
            }.joinToString(" · ")
        }.getOrDefault("Terminal")

    private fun summarizeTextLength(
        label: String,
        argumentsJson: String,
        key: String,
    ): String =
        runCatching {
            val chars = JSONObject(argumentsJson).optString(key).length
            "$label · $chars characters"
        }.getOrDefault(label)

    /** 只展示动作与长度，不回显正文：输入内容可能是密码或私人消息。 */
    private fun summarizeTypeText(argumentsJson: String): String =
        runCatching {
            val args = JSONObject(argumentsJson)
            val chars = args.optString("text").length
            val label = when {
                chars == 0 -> "Clear text"
                args.optString("mode") == "append" -> "Append text"
                else -> "Type text"
            }
            buildString {
                append(label)
                if (chars > 0) append(" · $chars characters")
                if (args.optBoolean("submit")) append(" · submit")
            }
        }.getOrDefault("Type text")

    /** 搜索关键词是用户自己发起的查询，直接展示；仍做单行化与长度截断。 */
    private fun summarizeQueryArguments(label: String, argumentsJson: String): String =
        runCatching {
            val query = sanitizeSummaryValue(
                JSONObject(argumentsJson).optString("query"),
                MAX_QUERY_SUMMARY_CHARS,
            )
            if (query.isNotBlank()) "$label · $query" else label
        }.getOrDefault(label)

    private fun summarizePointArguments(label: String, argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            "$label · (${arguments.optInt("x")}, ${arguments.optInt("y")})"
        }.getOrDefault(label)

    private fun summarizeElementArguments(label: String, argumentsJson: String): String =
        runCatching {
            val index = JSONObject(argumentsJson).optInt("index", -1)
            if (index >= 0) "$label · #$index" else label
        }.getOrDefault(label)

    private fun summarizeScrollArguments(
        label: String,
        argumentsJson: String,
        withIndex: Boolean = false,
    ): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            buildList {
                add(label)
                if (withIndex) {
                    arguments.optInt("index", -1).takeIf { it >= 0 }?.let { add("#$it") }
                }
                arguments.optString("direction").scrollDirectionLabel()?.let(::add)
            }.joinToString(" · ")
        }.getOrDefault(label)

    private fun summarizePressKeyArguments(argumentsJson: String): String =
        runCatching {
            val button = JSONObject(argumentsJson).optString("button").pressKeyLabel()
            listOfNotNull("Key", button).joinToString(" · ")
        }.getOrDefault("Key")

    private fun summarizeWaitArguments(argumentsJson: String): String =
        runCatching {
            val durationMs = JSONObject(argumentsJson).optInt("duration_ms", 1_000)
                .coerceAtLeast(0)
            val duration = if (durationMs >= 1_000) {
                String.format(Locale.US, "%.1f", durationMs / 1_000f)
                    .trimEnd('0').trimEnd('.') + " seconds"
            } else {
                "$durationMs ms"
            }
            "Wait · $duration"
        }.getOrDefault("Wait")

    private fun summarizeObservationArguments(argumentsJson: String): String =
        runCatching {
            val options = AgentScreenObservationContract.resolve(JSONObject(argumentsJson))
            buildList {
                add("Observe screen")
                if (options.includeScreenshot) add("with screenshot")
                if (options.includeUiTree) add("with UI tree")
            }.joinToString(" · ")
        }.getOrDefault("Observe screen")

    /** 结果成败供事件与 UI 状态使用，不再依赖摘要文本里的标记。 */
    fun isSuccessResult(result: AgentModelClient.ToolResult): Boolean =
        parseResultJson(result)?.optBoolean("ok", true) ?: true

    fun summarizeResult(
        toolName: String,
        result: AgentModelClient.ToolResult,
    ): String {
        val json = parseResultJson(result)
        if (toolName == "web_search" || toolName == "fetch_url") return summarizeWebResult(toolName, json)
        // 终端 exit_code != 0 时 ok=false 但没有 code 字段，必须走专用分支保留退出码与输出
        if (toolName in AgentTerminalToolCatalog.names || toolName == "run_command") {
            return summarizeTerminalResult(toolName, json)
        }
        if (json?.optString("status") == "unconfirmed") return "Action submitted; result unconfirmed"
        if (!isSuccessResult(result)) return summarizeFailure(json)
        if (json != null) {
            val summary = AgentDeviceResultFormatter.summarize(toolName, json)
                ?: AgentPhoneResultFormatter.summarize(toolName, json)
                ?: AgentPersonalResultFormatter.summarize(toolName, json)
                ?: AgentFileResultFormatter.summarize(toolName, json)
            if (summary != null) return summary
        }
        return when (toolName) {
            BROWSER_TOOL_NAME -> json?.let(::summarizeBrowserResult) ?: "Browser action completed"
            AgentSubagentToolCatalog.TOOL_NAME -> json?.let(::summarizeSubagentResult) ?: "Done"
            AgentSubagentToolCatalog.PROFILE_TOOL_NAME -> json?.let(::summarizeSubagentProfileResult) ?: "Done"
            AgentConversationToolCatalog.READ_HISTORY -> "History page read"
            "observe_screen" -> if (result.images.isNotEmpty()) "Screen captured" else "Screen inspected"
            "tap", "tap_element", "tap_area" -> "Tapped"
            "long_press", "long_press_element" -> "Long pressed"
            "swipe" -> "Swiped"
            "scroll", "scroll_element" -> "Scrolled"
            "type_text", "input_text", "paste_text" -> "Text entered"
            "clear_text" -> "Text cleared"
            "press_key" -> "Key pressed"
            "wait" -> "Wait completed"
            "wait_for_text" -> "Target text found"
            "wait_for_package" -> "App ready"
            "inspect_app" -> "App information retrieved"
            "get_clipboard" -> "Clipboard read"
            "set_clipboard" -> "Copied to clipboard"
            "search_apps" -> json?.let(::summarizeSearchAppsResult) ?: "Done"
            "launch_app" -> json?.let(::summarizeLaunchAppResult) ?: "Opened"
            "present_to_user" -> json?.let(::summarizePresentToUserResult) ?: "Presented to main screen"
            "skills_list", "skills_list_curated", "skills_inspect_github", "skills_install_from_github" -> json?.let {
                summarizeSkillResult(toolName, it) ?: summarizeGenericResult(it, result)
            } ?: "Done"
            else -> json?.let { summarizeGenericResult(it, result) } ?: "Done"
        }
    }

    private fun parseResultJson(result: AgentModelClient.ToolResult): JSONObject? =
        runCatching { JSONObject(result.content) }.getOrNull()

    /**
     * 网页错误正文、搜索词和页面标题可能回显私密 URL，只保留计数。动作与主机已在参数摘要中，
     * 结果只给出结论，避免工作过程同一行重复。
     */
    private fun summarizeWebResult(toolName: String, json: JSONObject?): String {
        val label = if (toolName == "web_search") "Web search" else "Web reading"
        if (json == null) return "Done"
        if (!json.optBoolean("ok", true)) {
            val code = json.optString("code").takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,79}")) }
            return listOfNotNull("${label}Failed", code?.let { "code=$it" }).joinToString(" · ")
        }
        if (toolName == "web_search") {
            val count = json.optJSONArray("results")?.length() ?: json.firstNonNegativeInt("count")
            return when (count) {
                null -> "Done"
                0 -> "No matching results"
                else -> "Found $count search results"
            }
        }
        val chars = (json.opt("text") as? String)?.length
            ?: json.firstNonNegativeInt("returned_chars", "text_chars")
            ?: return "Done"
        return if (json.optBoolean("has_more")) "Read first ${formatCharCount(chars)}" else "Read ${formatCharCount(chars)}"
    }

    /** 失败摘要保留 code= 标记，供运行日志提取稳定错误码；message 是工具侧给出的中文原因。 */
    private fun summarizeFailure(json: JSONObject?): String {
        val code = json?.optString("code")?.takeIf { it.isNotBlank() }
        val reason = json?.optString("message")
            ?.let(::sanitizeSummaryValue)
            ?.takeIf { it.isNotBlank() }
        return buildList {
            add("Failed")
            reason?.let(::add)
            code?.let { add("code=$it") }
        }.joinToString(" · ")
    }

    private fun summarizeSubagentArguments(argumentsJson: String): String = runCatching {
        val args = JSONObject(argumentsJson)
        val resumeId = sanitizeSummaryValue(args.optString("resume"), 32)
        val subagentType = sanitizeSummaryValue(args.optString("subagent_type"), 32)
            .lowercase()
            .ifBlank { if (resumeId.isNotBlank()) "subagent" else SubagentProfile.DEFAULT_TYPE }
        val description = sanitizeSummaryValue(args.optString("description"), MAX_QUERY_SUMMARY_CHARS)
        listOfNotNull(
            subagentType,
            description.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { "Subagent" }
    }.getOrDefault("Subagent")

    private fun summarizeSubagentResult(json: JSONObject): String {
        val toolCalls = json.optInt("tool_calls", 0)
        return if (toolCalls > 0) "Completed · $toolCalls tool calls" else "Done"
    }

    private fun summarizeSubagentProfileArguments(argumentsJson: String): String = runCatching {
        val args = JSONObject(argumentsJson)
        val action = args.optString("action").trim().lowercase()
        val name = sanitizeSummaryValue(args.optString("name"), 32)
        val label = when (action) {
            "save" -> "Save subagent"
            "delete" -> "Delete subagent"
            "get" -> "Read subagent"
            else -> "List subagents"
        }
        if (name.isNotBlank() && action != "list") "$label · $name" else label
    }.getOrDefault("Manage subagents")

    private fun summarizeSubagentProfileResult(json: JSONObject): String =
        when (json.optString("action")) {
            "save" -> listOfNotNull("Saved", json.optString("name").takeIf(String::isNotBlank)).joinToString(" · ")
            "delete" -> listOfNotNull("Deleted", json.optString("name").takeIf(String::isNotBlank)).joinToString(" · ")
            "list" -> "${json.optJSONArray("profiles")?.length() ?: 0} subagents"
            else -> "Done"
        }

    private fun summarizeSkillResult(toolName: String, json: JSONObject): String? {
        if (toolName == "skills_install_from_github") {
            val installed = json.optJSONArray("installed") ?: return null
            return "Installed ${installed.length()} skills" +
                if (json.optString("available") == "next_turn") " · available next turn" else ""
        }
        val items = json.optJSONArray("items") ?: return null
        val installed = (0 until items.length()).count { items.optJSONObject(it)?.opt("installed") == true }
        return "Found ${items.length()} skills" + if (installed > 0) " · $installed installed" else ""
    }

    private fun summarizeGenericResult(
        json: JSONObject,
        result: AgentModelClient.ToolResult,
    ): String =
        buildList {
            if (json.has("verified") && !json.optBoolean("verified")) {
                add("Executed; result unconfirmed")
            }
            val items = json.optJSONArray("items") ?: json.optJSONArray("results")
            val matches = json.optJSONArray("matches")
            val entries = json.optJSONArray("entries")
            val apps = json.optJSONArray("apps")
            val candidates = json.optJSONArray("candidates")

            when {
                items != null -> {
                    if (items.length() == 0) add("No matching results")
                    else add("Found ${items.length()} results")
                }
                matches != null -> {
                    if (matches.length() == 0) add("No matching content")
                    else add("Found ${matches.length()} matches")
                }
                entries != null -> {
                    if (entries.length() == 0) add("No content")
                    else add("Retrieved ${entries.length()} entries")
                }
                apps != null -> {
                    if (apps.length() == 0) add("No matching apps")
                    else add("Found ${apps.length()} apps")
                }
                candidates != null -> add("${candidates.length()} candidates")
                json.has("bytes_read") -> add("Read ${json.optLong("bytes_read")} bytes")
                json.has("replacements") -> add("Replaced ${json.optInt("replacements")} occurrences")
                result.images.isNotEmpty() -> add("${result.images.size} images")
                isEmpty() -> add("Done")
            }
            if (json.optBoolean("truncated") || json.optBoolean("has_more")) add("More available")
            if (json.optBoolean("partial") || json.optBoolean("scan_limited")) add("Limited collection range")
        }.joinToString(" · ")

    private fun summarizeSearchAppsResult(json: JSONObject): String {
        val apps = json.optJSONArray("apps") ?: return "No matching apps"
        val total = apps.length()
        if (total == 0) return "No matching apps"
        val names = (0 until total).mapNotNull { index ->
            apps.optJSONObject(index)?.optString("app_name")
                ?.let(::sanitizeSummaryValue)
                ?.takeIf { it.isNotBlank() }
        }
        return buildString {
            append("Found $total apps")
            val shown = names.take(MAX_LISTED_APP_NAMES)
            if (shown.isNotEmpty()) {
                append(" · ").append(shown.joinToString("、"))
                if (total > shown.size) append(" and others")
            }
        }
    }

    private fun summarizeLaunchAppResult(json: JSONObject): String {
        val appName = sanitizeSummaryValue(json.optString("app_name"))
        val virtual = json.optString("canvas") == "virtual"
        val action = if (json.optString("launch_status") == "unconfirmed") "Open requested; awaiting confirmation" else "Opened"
        val base = if (appName.isNotBlank()) "$action · $appName" else action
        return if (virtual) "$base · virtual display" else base
    }

    private fun summarizePresentToUserResult(json: JSONObject): String {
        val appName = sanitizeSummaryValue(json.optString("app_name"))
        return if (appName.isNotBlank()) "Presented to main screen · $appName" else "Presented to main screen"
    }

    /**
     * 终端结果面向用户展示退出状态与输出预览；输出可能很长，
     * 只保留开头几行，截断时追加省略标记。
     */
    private fun summarizeTerminalResult(toolName: String, json: JSONObject?): String {
        if (json == null) return terminalLabel(toolName)
        if (json.optString("code").isNotBlank()) return summarizeFailure(json)
        if (!json.has("exit_code") || json.isNull("exit_code")) {
            val action = terminalActionLabel(toolName, json.optString("action"))
            return if (json.optBoolean("ok", true)) "${terminalLabel(toolName)} · $action" else "Failed · $action"
        }
        val exitCode = json.optInt("exit_code")
        val timedOut = json.optBoolean("timed_out", false)
        val status = when {
            timedOut -> "Failed · execution timed out"
            exitCode == 0 -> "Execution completed"
            else -> "Failed · exit code $exitCode"
        }
        val output = if (exitCode == 0) {
            json.optString("stdout", json.optString("output"))
        } else {
            json.optString("stderr").ifBlank { json.optString("stdout", json.optString("output")) }
        }
        val truncated = json.optBoolean("stdout_truncated", false) ||
            json.optBoolean("stderr_truncated", false)
        val preview = terminalOutputPreview(output, truncated) ?: return status
        return "$status\n$preview"
    }

    private fun terminalOutputPreview(output: String, truncated: Boolean): String? {
        val normalized = output.trim()
        if (normalized.isEmpty()) return null
        val allLines = normalized.lines()
        var preview = allLines.take(MAX_TERMINAL_PREVIEW_LINES).joinToString("\n")
        var capped = allLines.size > MAX_TERMINAL_PREVIEW_LINES || truncated
        if (preview.length > MAX_TERMINAL_PREVIEW_CHARS) {
            preview = preview.take(MAX_TERMINAL_PREVIEW_CHARS)
            capped = true
        }
        return if (capped) "$preview\n…" else preview
    }

    private fun summarizeBrowserResult(json: JSONObject): String {
        val page = json.optJSONObject("page")
            ?: json.optJSONObject("page_info")
            ?: json.optJSONObject("pageInfo")
        val action = json.optString("action")
            .takeIf { it in BROWSER_ACTIONS }
            ?: "unknown"
        val host = sequenceOf(json, page)
            .filterNotNull()
            .flatMap { source ->
                sequenceOf("url", "current_url", "currentUrl", "final_url", "finalUrl")
                    .map(source::optString)
            }
            .mapNotNull(::safeHttpHost)
            .firstOrNull()
        val title = sequenceOf(json, page)
            .filterNotNull()
            .map { it.opt("title") }
            .filterIsInstance<String>()
            .map(::sanitizeSummaryValue)
            .firstOrNull { it.isNotBlank() }
        val textChars = sequenceOf(json, page)
            .filterNotNull()
            .mapNotNull { source ->
                source.firstNonNegativeInt("text_length", "textLength", "text_chars", "textChars")
            }
            .firstOrNull()
            ?: sequenceOf(json, page)
                .filterNotNull()
                .flatMap { source -> sequenceOf("text", "readable", "content").map(source::opt) }
                .filterIsInstance<String>()
                .map(String::length)
                .firstOrNull()
        val elementCount = json.firstNonNegativeInt("element_count", "elementCount", "elements_count")
            ?: json.optJSONArray("elements")?.length()

        return buildList {
            add(action.browserSuccessLabel())
            host?.let(::add)
            title?.let { add("《$it》") }
            if (action in BROWSER_TEXT_ACTIONS) {
                textChars?.let { add("About ${formatCharCount(it)}") }
            }
            elementCount?.let { add("$it elements") }
            if (json.optBoolean("truncated", false)) add("Truncated")
        }.joinToString(" · ")
    }

    private fun formatCharCount(chars: Int): String =
        if (chars >= 10_000) {
            String.format(Locale.US, "%.1f", chars / 10_000f).trimEnd('0').trimEnd('.') + " ten-thousand characters"
        } else {
            "$chars characters"
        }

    private fun JSONObject.firstNonNegativeInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            if (!has(key)) return@firstNotNullOfOrNull null
            optInt(key, -1).takeIf { it >= 0 }
        }

    private fun sanitizeSummaryValue(value: String, maxChars: Int = 80): String =
        value.replace(Regex("\\s+"), " ")
            .trim()
            .replace(',', '，')
            .replace('=', '＝')
            .let { if (it.length <= maxChars) it else it.take(maxChars) + "..." }

    private fun safeHttpHost(rawUrl: String): String? =
        rawUrl.trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                runCatching {
                    val uri = URI(value)
                    uri.host
                        ?.takeIf {
                            uri.scheme.equals("http", ignoreCase = true) ||
                                uri.scheme.equals("https", ignoreCase = true)
                        }
                        ?.lowercase()
                        ?.take(160)
                }.getOrNull()
            }

    private fun String.browserActionLabel(): String = when (this) {
        "navigate" -> "Open page"
        "get_readable" -> "Extract content"
        "get_text" -> "Read text"
        "find_elements" -> "Find elements"
        "click" -> "Click page"
        "type" -> "Type content"
        "scroll" -> "Scroll page"
        "screenshot" -> "Page screenshot"
        "get_page_info" -> "Page information"
        "go_back" -> "Go back"
        "go_forward" -> "Go forward"
        "reload" -> "Refresh page"
        "wait_for_selector" -> "Wait for page element"
        else -> "Browser action"
    }

    private fun String.browserSuccessLabel(): String = when (this) {
        "navigate" -> "Opened"
        "get_readable" -> "Content extracted"
        "get_text" -> "Text read"
        "find_elements" -> "Element found"
        "click" -> "Page clicked"
        "type" -> "Content entered"
        "scroll" -> "Page scrolled"
        "screenshot" -> "Screenshot captured"
        "get_page_info" -> "Page information read"
        "go_back" -> "Went back"
        "go_forward" -> "Went forward"
        "reload" -> "Refreshed"
        "wait_for_selector" -> "Target element found"
        else -> "Browser action completed"
    }

    private fun String.scrollDirectionLabel(): String? = when (lowercase(Locale.US)) {
        "up" -> "Up"
        "down" -> "Down"
        "left" -> "Left"
        "right" -> "Right"
        else -> null
    }

    private fun String.pressKeyLabel(): String? = when (lowercase(Locale.US)) {
        "back" -> "Back"
        "home" -> "Home"
        "recents", "recent" -> "Recent apps"
        "notifications" -> "Notifications"
        "quick_settings" -> "Quick settings"
        "power" -> "Power"
        "volume_up" -> "Volume up"
        "volume_down" -> "Volume down"
        "mute" -> "Silent"
        else -> null
    }

    private fun terminalLabel(toolName: String): String = when (toolName) {
        "terminal_job" -> "Background command"
        "terminal_daemon" -> "Background service"
        else -> "Terminal"
    }

    private fun terminalActionLabel(toolName: String, action: String): String = when (toolName) {
        "terminal_job" -> when (action) {
            "start" -> "Start command"
            "list" -> "Task list"
            "read" -> "Read output"
            "wait" -> "Wait completed"
            "stop" -> "Stop command"
            else -> "Task action"
        }
        "terminal_daemon" -> when (action) {
            "start" -> "Start service"
            "list" -> "Service list"
            "logs" -> "View logs"
            "stop" -> "Stop service"
            else -> "Service action"
        }
        else -> action.terminalActionLabel()
    }

    private fun String.terminalActionLabel(): String = when (this) {
        "check_root" -> "Check root access"
        "open" -> "Create session"
        "exec" -> "Run command"
        "open_and_exec" -> "Run once"
        "read_async_result" -> "Read background output"
        "close" -> "Close terminal"
        "daemon_start" -> "Start daemon task"
        "daemon_list" -> "Daemon tasks"
        "daemon_logs" -> "View daemon logs"
        "daemon_stop" -> "Stop daemon task"
        else -> "Terminal action"
    }

    private fun String.terminalEnvironmentLabel(): String = when (this) {
        "linux" -> "Linux"
        "alpine" -> "Alpine"
        "debian" -> "Debian"
        else -> "Android"
    }

    private companion object {
        const val BROWSER_TOOL_NAME = "browser_use"
        const val MAX_DISPLAY_COMMAND_CHARS = 4_000
        const val MAX_QUERY_SUMMARY_CHARS = 30
        const val MAX_LISTED_APP_NAMES = 3
        const val MAX_TERMINAL_PREVIEW_LINES = 10
        const val MAX_TERMINAL_PREVIEW_CHARS = 800
        val SENSITIVE_ASSIGNMENT = Regex(
            """(?i)\b([A-Z0-9_]*(?:API[_-]?KEY|ACCESS[_-]?TOKEN|AUTH[_-]?TOKEN|TOKEN|PASSWORD|PASSWD|SECRET)[A-Z0-9_]*)\s*=\s*(?:"[^"]*"|'[^']*'|[^\s;&|]+)"""
        )
        val SENSITIVE_FLAG = Regex(
            """(?i)(--?(?:password|passwd|token|api[-_]?key|secret)(?:\s*=\s*|\s+))(?:"[^"]*"|'[^']*'|[^\s;&|]+)"""
        )
        val SENSITIVE_HEADER = Regex(
            """(?i)\b(Authorization|Proxy-Authorization|X-Api-Key)(\s*:\s*)[^'"\r\n;&|]+"""
        )
        val BROWSER_ACTIONS = setOf(
            "navigate",
            "get_readable",
            "get_text",
            "find_elements",
            "click",
            "type",
            "scroll",
            "screenshot",
            "get_page_info",
            "go_back",
            "go_forward",
            "reload",
            "wait_for_selector",
        )
        val BROWSER_TEXT_ACTIONS = setOf("get_readable", "get_text")

        /** 结构化设备工具只展示动作标签，不暴露任何参数。 */
        val DEVICE_ACTION_LABELS = mapOf(
            "set_alarm" to "Set alarm",
            "set_timer" to "Set timer",
            "device_status" to "Device status",
            "network_info" to "Network information",
            "top_memory_apps" to "Memory usage ranking",
            "top_storage_apps" to "Storage usage ranking",
            "media_control" to "Control playback",
            "set_volume" to "Adjust volume",
            "get_setting" to "Read system settings",
            "wifi_credentials" to "Read Wi-Fi password",
            "recent_notifications" to "Read recent notifications",
            "search_notification_history" to "Search notification history",
            "recent_app_activity" to "View app activity",
            "app_usage_summary" to "View app usage statistics",
            "get_current_location" to "Get current location",
            "get_device_environment" to "View device context",
            "list_alarms" to "List alarms",
            "list_active_timers" to "View timers",
            "search_clipboard_history" to "Search clipboard history",
            "get_health_summary" to "View health summary",
            "read_sms_code" to "Read SMS verification code",
            "get_logcat" to "Read system logs",
            "search_media" to "Search media files",
            "search_audio" to "Search audio",
            "search_recordings" to "Search recordings",
            "search_files" to "Search files",
            "search_calendar_events" to "Search events",
            "search_contacts" to "Search contacts",
            "search_call_history" to "Search calls",
            "search_messages" to "Search messages",
            "search_downloads" to "Search downloads",
            "search_coloros_notes" to "Search notes",
            "search_coloros_recordings" to "Search recorder",
            "search_recording_summaries" to "Search recording summaries",
            "search_coloros_memories" to "Search assistant memories",
            "search_saved_places" to "Search saved places",
            "search_personal_orders" to "Search orders",
            "search_qq_chat_images" to "Search QQ chat images",
            "search_wechat_chat_images" to "Search WeChat chat images",
            "set_setting" to "Change system settings",
            "set_device_state" to "Change device state",
            "app_state_control" to "Manage app state",
        )
    }
}
