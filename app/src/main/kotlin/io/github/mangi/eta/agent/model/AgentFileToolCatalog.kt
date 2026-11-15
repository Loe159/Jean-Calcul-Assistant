package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.terminal.FileToolSearch
import org.json.JSONArray
import org.json.JSONObject

internal object AgentFileToolCatalog {
    val names = setOf("read_file", "write_file", "edit_file", "stat_file", "list_directory", "glob_files", "grep_files")

    fun appendTo(tools: JSONArray) {
        tools.put(tool("read_file", "Read UTF-8 text directly from a known path. next_offset_bytes is the exact continuation cursor; also pass expected_revision. start_line supports bounded line seeking; long lines may be split. Use media tools for binary content.", properties()
            .put("path", path())
            .put("offset_bytes", integer("Byte offset, default 0. Continue with previous next_offset_bytes; never calculate it yourself.", 0))
            .put("max_bytes", integer("Text byte budget, default 16000. Larger legacy values are accepted but at most 16000 bytes are returned.", 4, 262144))
            .put("start_line", integer("Optional 1-based line number. Omit offset_bytes or set it to 0 for line reads. Distant seeking is bounded; inspect start_line_reached.", 1))
            .put("max_lines", integer("Maximum lines, default 200; long lines also obey the byte budget", 1, 2000))
            .put("expected_revision", revision()), listOf("path")))
        tools.put(tool("write_file", "Create, fully overwrite or append a UTF-8 file, creating parents as needed. Use edit_file for partial edits. expected_revision prevents stale overwrites; atomic describes backend replacement.", properties()
            .put("path", path()).put("content", text("Complete or appended content, at most 512 KiB encoded as UTF-8", 524288))
            .put("append", JSONObject().put("type", "boolean").put("default", false))
            .put("expected_revision", revision()), listOf("path", "content")))
        tools.put(tool("edit_file", "Replace exact old_text in a UTF-8 file up to 512 KiB. Require a unique match by default; no write on missing/multiple matches. Reread or add context. Recheck old content before writing; atomic does not provide mutual exclusion with external programs.", properties()
            .put("path", path()).put("old_text", text("Exact nonempty old file text to match", 524288).put("minLength", 1))
            .put("new_text", text("Replacement text; empty deletes the matching segment", 524288))
            .put("replace_all", JSONObject().put("type", "boolean").put("default", false))
            .put("expected_revision", revision()), listOf("path", "old_text", "new_text")))
        tools.put(tool("stat_file", "Read a known file/directory's canonical path, type, size and revision without reading content.", properties()
            .put("path", path()), listOf("path")))
        tools.put(tool("list_directory", "List direct children with structured entries and next_offset. Continue with the same path, next_offset and expected_revision. Only has_more=false confirms completion.", properties()
            .put("path", path()).put("show_hidden", JSONObject().put("type", "boolean").put("default", false))
            .put("limit", integer("Raw entries per page, default 80; hidden-entry filtering may return fewer", 1, 200))
            .put("offset", integer("Directory enumeration offset, default 0. Continue with next_offset.", 0))
            .put("expected_revision", revision()), emptyList()))
        tools.put(tool("glob_files", "Recursively find files by path pattern, supporting *, ? and **. Skip hidden directories by default and do not follow directory symlinks. Results are bounded; continue with the unchanged cursor. partial=true does not cover all files.", properties()
            .put("path", path()).put("pattern", text("Pattern relative to the search directory, e.g. **/*.json", 500).put("minLength", 1))
            .put("limit", integer("Maximum entries; default 80", 1, 200))
            .put("cursor", text("Opaque continuation cursor; keep every search condition unchanged", FileToolSearch.MAX_CURSOR_CHARS))
            .put("include_hidden", JSONObject().put("type", "boolean").put("default", false)), listOf("pattern")))
        tools.put(tool("grep_files", "Search literal UTF-8 text in workspace files, returning paths, line numbers and matching lines. Case-sensitive by default, without depending on rg. Scan in bounded chunks with cursor continuation. Skip binary/inaccessible entries and report incomplete scope.", properties()
            .put("path", path()).put("query", text("Literal text, not a regular expression", 500).put("minLength", 1))
            .put("glob", text("File pattern relative to the directory; default **/*", 500))
            .put("limit", integer("Maximum matches; default 50", 1, 200))
            .put("cursor", text("Continuation cursor from the previous search; keep all conditions unchanged", FileToolSearch.MAX_CURSOR_CHARS))
            .put("case_sensitive", JSONObject().put("type", "boolean").put("default", true))
            .put("include_hidden", JSONObject().put("type", "boolean").put("default", false)), listOf("query")))
    }

    private fun properties(): JSONObject = JSONObject()
        .put("environment", JSONObject().put("type", "string").put("enum", JSONArray(listOf("android")))
            .put("description", "Environnement Android ; utiliser les chemins accessibles au téléphone."))
        .put("identity", JSONObject().put("type", "string").put("enum", JSONArray(listOf("user", "root")))
            .put("description", "Optional host identity, matching terminal defaults: confirmed Android root uses root, otherwise user. Linux resolves by backend: PRoot uses user and chroot uses root."))
        .put("cwd", text("Optional working directory, sharing the terminal's default workspace. Actual path is in this execution context.", 4096))

    private fun path(): JSONObject = text("Absolute path or path relative to cwd. Filesystem paths only, not content URIs. Imported file paths refer to copies.", 4096)
    private fun revision(): JSONObject = text("Revision from the previous file/directory result. Never mix revisions across identities or environments.", 256)
    private fun text(description: String, max: Int): JSONObject = JSONObject().put("type", "string").put("description", description).put("maxLength", max)
    private fun integer(description: String, minimum: Int, maximum: Int? = null): JSONObject =
        JSONObject().put("type", "integer").put("description", description).put("minimum", minimum)
            .also { if (maximum != null) it.put("maximum", maximum) }

    private fun tool(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject {
        val schema = JSONObject().put("type", "object").put("properties", properties)
            .put("additionalProperties", false).put("required", JSONArray(required))
        return AgentToolSchema.function(name, description, schema)
    }
}
