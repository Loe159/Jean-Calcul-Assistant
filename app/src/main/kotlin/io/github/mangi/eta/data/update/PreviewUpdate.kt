package io.github.mangi.eta.data.update

import io.github.mangi.eta.BuildConfig
import org.json.JSONObject

/** The channel manifest is published only after the matching CI build passes. */
internal data class PreviewUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val commit: String = "",
) {
    companion object {
        const val REPOSITORY = "https://github.com/Loe159/Jean-Calcul-Assistant"
        fun parse(json: JSONObject): PreviewUpdate {
            val update = PreviewUpdate(
                json.getLong("versionCode"), json.getString("versionName"),
                json.getString("apkUrl"), json.getString("sha256"), json.getLong("size"), json.optString("commit"),
            )
            require(json.getString("applicationId") == BuildConfig.APPLICATION_ID) { "Wrong application ID" }
            require(json.getString("channel") == BuildConfig.UPDATE_CHANNEL) { "Wrong update channel" }
            require(update.commit.isEmpty() || update.commit.matches(Regex("[0-9a-f]{40}"))) { "Invalid source commit" }
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
