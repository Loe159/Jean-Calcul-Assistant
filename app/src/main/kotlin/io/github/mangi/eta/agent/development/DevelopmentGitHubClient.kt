package io.github.mangi.eta.agent.development

import io.github.mangi.eta.agent.model.AgentHttpClient
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal class DevelopmentGitHubClient(
    private val token: () -> String?,
    private val transport: (Request) -> String = ::send,
) {
    fun api(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        require((path.isEmpty() || path.startsWith('/')) && !path.contains("..")) { "Invalid GitHub API path" }
        val credential = token()?.takeIf { it.isNotBlank() } ?: error("Connect GitHub in Settings > App improvements first")
        val url = "https://api.github.com/repos/${DevelopmentPolicy.REPOSITORY}$path".toHttpUrl()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $credential")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(method, if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody(JSON))
            .build()
        val text = transport(request)
        return if (text.trimStart().startsWith('[')) JSONObject().put("items", JSONArray(text)) else JSONObject(text)
    }

    fun revision(ref: String): JSONObject {
        require(ref == DevelopmentPolicy.BASE || ref.matches(Regex("[0-9a-f]{40}")) ||
            runCatching { DevelopmentPolicy.requireBranch(ref) }.isSuccess) { "Invalid source ref" }
        return api("/commits/${encode(ref)}")
    }

    fun file(path: String, ref: String): JSONObject {
        DevelopmentPolicy.requirePath(path)
        val result = api("/contents/${path.split('/').joinToString("/") { encode(it) }}?ref=${encode(ref)}")
        require(result.optString("type") == "file" && result.optString("encoding") == "base64") { "Not a readable source file" }
        require(result.getLong("size") <= DevelopmentPolicy.MAX_FILE_BYTES) { "Source file exceeds the size limit" }
        val bytes = Base64.getMimeDecoder().decode(result.getString("content"))
        require(bytes.size <= DevelopmentPolicy.MAX_FILE_BYTES && !bytes.contains(0)) { "Only bounded text source files are supported" }
        return JSONObject().put("sha", result.getString("sha")).put("content", bytes.toString(Charsets.UTF_8))
    }

    fun runs(branch: String): JSONArray = api("/actions/workflows/v2-preview.yml/runs?branch=${encode(DevelopmentPolicy.requireBranch(branch))}&event=push&per_page=10")
        .getJSONArray("workflow_runs")

    fun status(proposal: JSONObject): JSONObject {
        val number = proposal.optInt("number")
        val pr = if (number > 0) api("/pulls/$number") else null
        return JSONObject(proposal.toString()).put("ok", true)
            .put("pull_request", pr?.let { JSONObject().put("url", it.getString("html_url"))
                .put("state", it.getString("state")).put("merged", it.optBoolean("merged"))
                .put("head", it.getJSONObject("head").getString("sha")) })
            .put("runs", JSONArray().also { out ->
                val runs = runs(proposal.getString("branch"))
                for (i in 0 until runs.length()) {
                    val run = runs.getJSONObject(i)
                    out.put(JSONObject().put("id", run.getLong("id")).put("head_sha", run.getString("head_sha"))
                        .put("status", run.getString("status")).put("conclusion", run.opt("conclusion"))
                        .put("url", run.getString("html_url")))
                }
            })
    }

    /** Called only by the native approval button, never exposed as a model tool. */
    fun approve(proposal: JSONObject): JSONObject {
        val number = proposal.getInt("number")
        val pr = api("/pulls/$number")
        require(pr.getJSONObject("base").getString("ref") == DevelopmentPolicy.BASE &&
            pr.getJSONObject("head").getString("ref") == DevelopmentPolicy.requireBranch(proposal.getString("branch")) &&
            pr.getJSONObject("head").getJSONObject("repo").getString("full_name") == DevelopmentPolicy.REPOSITORY) { "Unexpected pull request" }
        if (pr.optBoolean("merged")) return JSONObject().put("merged", true)
        val revision = pr.getJSONObject("head").getString("sha")
        require(revision == proposal.getString("head")) { "The PR changed. Refresh and review its latest version before approving" }
        require(pr.optString("state") == "open" && pr.optBoolean("mergeable")) { "The PR is closed, conflicted or still being checked" }
        require(DevelopmentPolicy.successfulRuns(runs(proposal.getString("branch")), revision)) { "Wait for the complete Android workflow to succeed before approving" }
        val mergeRevision = DevelopmentPolicy.requireSha(pr.getString("merge_commit_sha"))
        val mergeChecks = api("/commits/$mergeRevision/check-runs?per_page=100").getJSONArray("check_runs")
        require(DevelopmentPolicy.successfulChecks(mergeChecks)) { "Wait for the PR merge checks to succeed before approving" }
        return api("/pulls/$number/merge", "PUT", JSONObject().put("sha", revision).put("merge_method", "merge"))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val client by lazy { AgentHttpClient.client.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(false).build() }
        private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private fun send(request: Request): String {
            try {
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error(when (response.code) {
                        401, 403 -> "GitHub authorization failed. Check token expiry and repository permissions"
                        409, 422 -> "GitHub rejected this change. Refresh the source and PR state before retrying"
                        404 -> "GitHub source or PR not found; check repository access"
                        else -> "GitHub request failed (HTTP ${response.code})"
                    })
                    val source = response.body?.source() ?: error("GitHub returned an empty response")
                    require(!source.request(4L * 1024 * 1024 + 1)) { "GitHub response exceeds the size limit" }
                    source.readUtf8()
                }
            } catch (_: IOException) { error("Could not reach GitHub. Check your connection and retry") }
        }
    }
}
