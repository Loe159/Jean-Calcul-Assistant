package io.github.mangi.eta.devpreview

import android.content.Context
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.data.update.AppUpdateChecker
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal data class DevBuildInfo(
    val id: String,
    val branch: String,
    val commit: String,
    val dirty: Boolean,
    val createdAt: String,
) {
    companion object {
        fun read(context: Context): DevBuildInfo? = if (BuildConfig.DEV_PREVIEW) {
            val json = runCatching { context.assets.open("dev-build.json").bufferedReader().use { it.readText() } }.getOrNull()
            parseAsset(json)
        } else null

        fun parseAsset(text: String?): DevBuildInfo? =
            text?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

        fun parse(json: JSONObject) = DevBuildInfo(
            id = json.nullableString("id") ?: json.getString("buildId"),
            branch = json.getString("branch"),
            commit = json.getString("commit"),
            dirty = json.getBoolean("dirty"),
            createdAt = json.nullableString("createdAt") ?: json.getString("builtAt"),
        )
    }
}

internal data class DevBuild(
    val info: DevBuildInfo,
    val worktree: String,
    val message: String,
    val versionName: String,
    val sha256: String,
    val size: Long,
)

internal data class DevBranch(val branch: String, val worktree: String, val builds: List<DevBuild>)

internal data class DevReviewOption(val id: String, val label: String, val image: String?, val previewFqn: String?)

internal data class DevReviewAnswer(
    val verdict: String,
    val choice: String?,
    val comment: String,
    val answeredAt: String,
    val installedBuildId: String?,
)

internal data class DevReview(
    val id: String,
    val branch: String,
    val buildId: String?,
    val title: String,
    val body: String,
    val images: List<String>,
    val options: List<DevReviewOption>,
    val previewFqn: String?,
    val status: String,
    val createdAt: String,
    val answer: DevReviewAnswer? = null,
) {
    val isOpen get() = status == "open"
}

internal data class DevHubState(val seq: Long, val branches: List<DevBranch>, val reviews: List<DevReview>) {
    fun findBuild(id: String?) = branches.asSequence().flatMap { it.builds.asSequence() }.firstOrNull { it.info.id == id }
}

internal data class DevEvent(val seq: Long, val type: String, val id: String, val branch: String)
internal data class DevEvents(val seq: Long, val events: List<DevEvent>, val resync: Boolean)

internal data class DevPreviewEntry(val fqn: String, val file: String, val name: String) {
    /** "SettingsPageSpecimenPreview" → "Settings page specimen". */
    val label: String get() = humanize(name.removeSuffix("Preview"))

    companion object {
        // The Hub's own screens are tooling, not product: listing them shows a Hub inside the Hub.
        private const val HUB_PACKAGE = "io.github.mangi.eta.devpreview."

        fun read(context: Context): List<DevPreviewEntry> = if (BuildConfig.DEV_PREVIEW) {
            runCatching {
                context.assets.open("dev-previews.json").bufferedReader().use { catalog(parse(JSONArray(it.readText()))) }
            }.getOrDefault(emptyList())
        } else emptyList()

        fun catalog(entries: List<DevPreviewEntry>) = entries.filterNot { it.fqn.startsWith(HUB_PACKAGE) }

        fun humanize(identifier: String): String {
            val words = identifier.split(Regex("(?<=[a-z0-9])(?=[A-Z])|_")).filter { it.isNotBlank() }
            return words.mapIndexed { i, word -> if (i == 0) word else word.lowercase() }.joinToString(" ").ifEmpty { identifier }
        }

        fun parse(json: JSONArray) = json.mapItems { array, i ->
            val entry = array.getJSONObject(i)
            DevPreviewEntry(entry.getString("fqn"), entry.getString("file"), entry.getString("name"))
        }
    }
}

internal object DevHubJson {
    fun state(json: JSONObject) = DevHubState(
        json.getLong("seq"),
        json.getJSONArray("branches").objects { branch ->
            DevBranch(branch.getString("branch"), branch.getString("worktree"), branch.getJSONArray("builds").objects(::build))
        },
        json.getJSONArray("reviews").objects(::review).sortedByDescending { it.isOpen },
    )

    private fun build(json: JSONObject) = DevBuild(
        DevBuildInfo.parse(json), json.getString("worktree"), json.getString("message"),
        json.getString("versionName"), json.getString("sha256"), json.getLong("size"),
    )

    fun review(json: JSONObject) = DevReview(
        id = json.getString("id"),
        branch = json.getString("branch"),
        buildId = json.nullableString("buildId"),
        title = json.getString("title"),
        body = json.optString("body"),
        images = json.optJSONArray("images").mapItems(JSONArray::getString),
        options = json.optJSONArray("options").objects {
            DevReviewOption(it.getString("id"), it.getString("label"), it.nullableString("image"), it.nullableString("previewFqn"))
        },
        previewFqn = json.nullableString("previewFqn"),
        status = json.getString("status"),
        createdAt = json.getString("createdAt"),
        answer = json.optJSONObject("answer")?.let {
            DevReviewAnswer(it.getString("verdict"), it.nullableString("choice"), it.optString("comment"),
                it.getString("answeredAt"), it.nullableString("installedBuildId"))
        },
    )

    fun events(json: JSONObject) = DevEvents(
        json.getLong("seq"),
        json.optJSONArray("events").objects {
            DevEvent(it.getLong("seq"), it.getString("type"), it.getString("id"), it.getString("branch"))
        },
        json.optBoolean("resync"),
    )

    fun answerPayload(approve: Boolean, choice: String?, comment: String, installedBuildId: String?): JSONObject =
        JSONObject().apply {
            put("verdict", when {
                !approve -> "changes"
                choice != null -> "choice"
                else -> "approved"
            })
            if (approve && choice != null) put("choice", choice)
            if (comment.isNotBlank()) put("comment", comment.trim())
            if (installedBuildId != null) put("installedBuildId", installedBuildId)
        }
}

private fun JSONObject.nullableString(key: String): String? = optString(key).takeUnless { isNull(key) }
private fun <T> JSONArray?.mapItems(read: (JSONArray, Int) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { read(this, it) }
private fun <T> JSONArray?.objects(read: (JSONObject) -> T) = mapItems { array, i -> read(array.getJSONObject(i)) }

internal class DevHubClient private constructor(baseUrl: String) {
    private val base = baseUrl.toHttpUrl()
    // Long transfers: the 25 s event long-poll and APK downloads.
    private val client = AppUpdateChecker.client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    // Screen requests fail fast, so a stalled link shows Offline + retry instead of an endless spinner
    // (an APK download on a slow link can otherwise hold every other response back for minutes).
    private val quick = AppUpdateChecker.client.newBuilder().callTimeout(QUICK_TIMEOUT_S, TimeUnit.SECONDS).build()

    suspend fun state() = json(request("state"), DevHubJson::state)
    suspend fun review(id: String) = json(request("reviews", id), DevHubJson::review)

    suspend fun events(since: Long): DevEvents {
        val url = url("events").newBuilder()
            .addQueryParameter("since", since.toString()).addQueryParameter("wait", "25").build()
        return json(Request.Builder().url(url).build(), DevHubJson::events, client)
    }

    fun apk(id: String): Call = client.newCall(request("builds", id, "apk"))

    suspend fun answer(id: String, payload: JSONObject): DevReview {
        val request = Request.Builder().url(url("reviews", id, "answer"))
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return json(request, DevHubJson::review)
    }

    suspend fun image(id: String, name: String): ByteArray = fetch(request("reviews", id, "images", name)) {
        val limit = 8 * 1024 * 1024
        it.body.byteStream().readNBytes(limit + 1).also { bytes ->
            require(bytes.size <= limit) { "Image exceeds the download limit" }
        }
    }

    private fun url(vararg segments: String) = base.newBuilder().addPathSegments("api/v1").apply {
        segments.forEach { addPathSegment(it) }
    }.build()

    private fun request(vararg segments: String) = Request.Builder().url(url(*segments)).build()

    private suspend fun <T> json(request: Request, parse: (JSONObject) -> T, http: OkHttpClient = quick): T =
        fetch(request, http) { parse(JSONObject(it.body.string())) }

    private suspend fun <T> fetch(request: Request, http: OkHttpClient = quick, read: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw IOException("Hub request failed (HTTP ${it.code})")
                            read(it)
                        }
                    }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }

    companion object {
        private const val QUICK_TIMEOUT_S = 20L

        fun configured(): DevHubClient? = BuildConfig.DEV_HUB_URL.takeIf { BuildConfig.DEV_PREVIEW && it.isNotBlank() }
            ?.let { runCatching { DevHubClient(it) }.getOrNull() }
    }
}
