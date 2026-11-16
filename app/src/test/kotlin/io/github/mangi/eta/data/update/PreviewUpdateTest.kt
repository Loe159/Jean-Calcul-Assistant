package io.github.mangi.eta.data.update

import io.github.mangi.eta.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewUpdateTest {
    private fun manifest() = JSONObject().put("applicationId", BuildConfig.APPLICATION_ID)
        .put("channel", BuildConfig.UPDATE_CHANNEL).put("versionCode", 2030000101)
        .put("versionName", "2.0.0-preview.2030000101-debug")
        .put("apkUrl", "${PreviewUpdate.REPOSITORY}/releases/download/${BuildConfig.UPDATE_CHANNEL}/JeanCalcul-2030000101.apk")
        .put("sha256", "a".repeat(64)).put("size", 1024)

    @Test fun acceptsPublishedManifest() {
        assertEquals(2030000101L, PreviewUpdate.parse(manifest()).versionCode)
    }
    @Test fun recordsSourceRevisionAndRejectsMalformedRevision() {
        assertEquals("a".repeat(40), PreviewUpdate.parse(manifest().put("commit", "a".repeat(40))).commit)
        assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("commit", "not-a-revision")) }
    }
    @Test fun rejectsAnotherApplication() {
        assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("applicationId", "other.app")) }
    }
    @Test fun rejectsAnotherChannel() {
        assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("channel", "another-channel")) }
    }
    @Test fun rejectsUntrustedHostAndPaths() {
        for (url in listOf("https://evil.example/update.apk", "http://github.com/update.apk",
            "${PreviewUpdate.REPOSITORY}/releases/download/other/JeanCalcul-2030000101.apk")) {
            assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("apkUrl", url)) }
        }
    }
    @Test fun rejectsMissingIntegrityDataAndOversizedApks() {
        assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("sha256", "")) }
        assertThrows(IllegalArgumentException::class.java) { PreviewUpdate.parse(manifest().put("size", PreviewUpdate.MAX_APK_BYTES + 1)) }
    }
}
