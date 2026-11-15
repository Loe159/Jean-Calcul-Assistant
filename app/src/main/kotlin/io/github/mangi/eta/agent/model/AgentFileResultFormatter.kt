package io.github.mangi.eta.agent.model

import java.util.Locale
import org.json.JSONObject

/** 文件轨迹只保留操作结果与规模，不把路径、正文或搜索条件带入持久摘要。 */
internal object AgentFileResultFormatter {
    fun summarize(toolName: String, json: JSONObject): String? = when (toolName) {
        "write_file" -> summarizeWrite(json)
        "read_file" -> summarizeRead(json)
        "stat_file" -> summarizeStat(json)
        "edit_file" -> json.nonNegativeLong("replacements")?.let { "Replaced $it occurrences" }
        "list_directory" -> json.optJSONArray("entries")?.let { entries ->
            if (entries.length() == 0) "Directory is empty"
            else withScope("Listed ${entries.length()} entries", json, "More available")
        }
        "glob_files", "grep_files" -> json.optJSONArray("matches")?.let { matches ->
            val count = matches.length()
            val label = when {
                count == 0 && toolName == "glob_files" -> "No matching files"
                count == 0 -> "No matching content"
                toolName == "glob_files" -> "Found $count files"
                else -> "Found $count matches"
            }
            if (count == 0) {
                if (json.opt("partial") == true) "$label · limited scan range" else label
            } else {
                withScope(label, json, "More available")
            }
        }
        else -> null
    }

    private fun summarizeWrite(json: JSONObject): String? {
        val bytes = json.nonNegativeLong("bytes_written") ?: return null
        val action = when (json.optString("mode")) {
            "append" -> "Appended"
            "overwrite" -> "Written"
            else -> return null
        }
        return "$action ${formatBytes(bytes)}"
    }

    private fun summarizeRead(json: JSONObject): String? {
        val bytes = json.nonNegativeLong("bytes_read") ?: return null
        if (json.opt("start_line_reached") == false) {
            return when (json.opt("truncated") as? Boolean) {
                true -> "Target line not reached · continue reading"
                false -> "Target line not reached · end of file"
                null -> "Target line not reached"
            }
        }
        val isTruncated = json.opt("truncated") == true || json.opt("has_more") == true
        val label = if (isTruncated) "Read first ${formatBytes(bytes)}" else "Read ${formatBytes(bytes)}"
        return withScope(label, json, "More available")
    }

    private fun summarizeStat(json: JSONObject): String? {
        val file = json.optJSONObject("file") ?: return null
        return when (file.optString("kind")) {
            "file" -> file.nonNegativeLong("size_bytes")?.let { "File · ${formatBytes(it)}" }
            "directory" -> "Directory"
            "symlink" -> "Symbolic link"
            "other" -> "Other file type"
            else -> null
        }
    }

    private fun withScope(label: String, json: JSONObject, continuation: String): String =
        buildList {
            add(label)
            val hasMore = json.opt("truncated") == true || json.opt("has_more") == true
            if (hasMore) add(continuation)
            if (json.opt("partial") == true) add("Limited scan range")
        }.joinToString(" · ")

    private fun JSONObject.nonNegativeLong(key: String): Long? =
        (opt(key) as? Number)?.toString()?.toLongOrNull()?.takeIf { it >= 0 }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes bytes"
        val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
        var size = bytes / 1024.0
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return "${String.format(Locale.ROOT, "%.1f", size).removeSuffix(".0")} ${units[unit]}"
    }
}
