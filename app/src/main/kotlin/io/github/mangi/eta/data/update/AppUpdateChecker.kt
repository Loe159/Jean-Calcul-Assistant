package io.github.mangi.eta.data.update

import io.github.mangi.eta.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** GitHub 最新 Release 信息，[version] 已去掉 tag 的 v 前缀。 */
internal data class AppLatestRelease(
    val version: String,
    val url: String,
)

/**
 * 应用更新检查：读取 GitHub 最新 Release 并与当前版本比较。
 *
 * 版本号遵循发版流程的 v 前缀 + 点分数字约定（v3.0.6），比较按段进行：两段都是数字时
 * 按数值比较，否则按字符串比较。当前版本的构建类型后缀（-debug）与 CI 构建元数据（+提交号）不参与比较。
 * 所有网络与响应错误统一抛 [IOException]，由调用方提示。
 */
internal object AppUpdateChecker {

    fun fetchLatest(): AppLatestRelease {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub release request failed (HTTP ${response.code}）")
            }
            val json = runCatching { JSONObject(response.body.string()) }
                .getOrElse { throw IOException("GitHub release response is not valid JSON", it) }
            val tag = json.optString("tag_name").trim()
            val url = json.optString("html_url").trim()
            if (tag.isEmpty() || url.isEmpty()) {
                throw IOException("GitHub release response is missing tag_name/html_url")
            }
            return AppLatestRelease(version = tag.removePrefix("v"), url = url)
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.removePrefix("v").split('.')
        val currentParts =
            current.removePrefix("v").substringBefore('+').substringBefore('-').split('.')
        for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrNull(index) ?: "0"
            val currentPart = currentParts.getOrNull(index) ?: "0"
            val latestNumber = latestPart.toIntOrNull()
            val currentNumber = currentPart.toIntOrNull()
            if (latestNumber != null && currentNumber != null) {
                if (latestNumber != currentNumber) return latestNumber > currentNumber
            } else {
                val compared = latestPart.compareTo(currentPart)
                if (compared != 0) return compared > 0
            }
        }
        return false
    }

    fun fetchPreview(): PreviewUpdate? {
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

    internal val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Loe159/Jean-Calcul-Assistant/releases/latest"
    private const val USER_AGENT = "JeanCalcul-Update-Checker"
}
