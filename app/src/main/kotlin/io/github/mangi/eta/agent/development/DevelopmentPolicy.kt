package io.github.mangi.eta.agent.development

import org.json.JSONArray
import org.json.JSONObject

internal object DevelopmentPolicy {
    const val REPOSITORY = "Loe159/Jean-Calcul-Assistant"
    const val BASE = "main"
    const val TOOL = "develop_jean_calcul"
    const val MAX_FILE_BYTES = 512 * 1024
    private val sha = Regex("[0-9a-f]{40}")

    fun requireSha(value: String): String = value.also { require(sha.matches(it)) { "Invalid source revision" } }
    fun requireBranch(value: String): String = value.also {
        require(it.matches(Regex("self-improve/[a-z0-9-]{1,80}"))) { "Invalid improvement branch" }
    }
    fun requirePath(path: String): String = path.also {
        require(path.length in 1..300 && !path.contains('\\') && !path.contains('\u0000') &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Invalid repository path" }
        require(path.startsWith("app/src/") || path.startsWith("docs/") || path == "app/build.gradle.kts") {
            "Only application source, tests, documentation and app build configuration may be changed"
        }
        require(!path.contains("/native-sources/") && !path.endsWith(".jks") && !path.endsWith(".so")) {
            "Binary and signing files cannot be changed by the in-app agent"
        }
    }

    /** Exact, unique replacements with the blob revision checked before any write. */
    fun applyEdits(original: String, edits: JSONArray): String {
        require(edits.length() in 1..40) { "Supply 1 to 40 exact edits" }
        var text = original
        for (i in 0 until edits.length()) {
            val edit = edits.getJSONObject(i)
            val before = edit.getString("before")
            val after = edit.getString("after")
            require(before.isNotEmpty() && before != after) { "An edit must change non-empty source text" }
            val index = text.indexOf(before)
            require(index >= 0 && text.indexOf(before, index + 1) < 0) {
                "Edit context must match exactly once; read the file and use more surrounding text"
            }
            text = text.replaceRange(index, index + before.length, after)
        }
        require(text.toByteArray().size <= MAX_FILE_BYTES) { "Changed file is too large" }
        return text
    }

    fun successfulRuns(runs: JSONArray, revision: String): Boolean {
        val matching = (0 until runs.length()).map(runs::getJSONObject)
            .filter { it.optString("head_sha") == revision && it.optString("event") == "push" }
            .maxByOrNull { it.optLong("id") } ?: return false
        return matching.optString("status") == "completed" && matching.optString("conclusion") == "success"
    }

    fun successfulChecks(checks: JSONArray): Boolean {
        val latest = (0 until checks.length()).map(checks::getJSONObject)
            .groupBy { it.optString("name") }.mapValues { (_, values) -> values.maxBy { it.optLong("id") } }
        return listOf("Build APK, unit tests and lint", "Technical Android 35 E2E (no visual judgment)")
            .all { name -> latest[name]?.let { it.optString("status") == "completed" && it.optString("conclusion") == "success" } == true }
    }

    fun sourceRef(installedRevision: String): String = installedRevision.takeIf(sha::matches) ?: BASE
    fun status(ok: Boolean, message: String): JSONObject = JSONObject().put("ok", ok).put("message", message)
}
