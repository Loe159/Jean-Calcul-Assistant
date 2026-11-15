package io.github.mangi.eta.data.update

import io.github.mangi.eta.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** The channel manifest is published only after the matching CI build passes. */
internal data class PreviewUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
) {
    companion object {
        const val REPOSITORY = "https://github.com/Loe159/Jean-Calcul-Assistant"
        fun parse(json: JSONObject): PreviewUpdate {
            val update = PreviewUpdate(
                json.getLong("versionCode"), json.getString("versionName"),
                json.getString("apkUrl"), json.getString("sha256"), json.getLong("size"),
            )
            require(json.getString("applicationId") == BuildConfig.APPLICATION_ID) { "Wrong application ID" }
            require(json.getString("channel") == BuildConfig.UPDATE_CHANNEL) { "Wrong update channel" }
            require(update.versionCode in 1..2_100_000_000L) { "Invalid version code" }
            require(update.versionName.isNotBlank()) { "Missing version name" }
            require(update.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid APK digest" }
            require(update.size in 1..MAX_APK_BYTES) { "Invalid APK size" }
            require(update.apkUrl.startsWith("$REPOSITORY/releases/download/${BuildConfig.UPDATE_CHANNEL}/") &&
                update.apkUrl.substringAfterLast('/').matches(Regex("JeanCalcul-[0-9]+\\.apk"))) { "Untrusted APK URL" }
            return update
        }
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }
}

internal object PreviewUpdateSource {
    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).build()

    fun fetch(): PreviewUpdate? {
        val channel = BuildConfig.UPDATE_CHANNEL
        if (channel.isBlank()) return null
        val request = Request.Builder()
            .url("${PreviewUpdate.REPOSITORY}/releases/download/$channel/update.json")
            .header("Cache-Control", "no-cache").header("User-Agent", "JeanCalcul-Updater").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // No successful preview has been published yet.
            if (!response.isSuccessful) throw IOException("Update check failed (HTTP ${response.code})")
            val body = response.body.byteStream().readNBytes(64 * 1024 + 1)
            require(body.size <= 64 * 1024) { "Update manifest is too large" }
            return PreviewUpdate.parse(JSONObject(body.toString(Charsets.UTF_8)))
        }
    }
}
