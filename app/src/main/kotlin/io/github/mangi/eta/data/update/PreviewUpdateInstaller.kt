package io.github.mangi.eta.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.Request

internal object PreviewUpdateInstaller {
    fun download(context: Context, update: PreviewUpdate): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val temporary = File(directory, "download.part")
        val target = File(directory, "update.apk")
        try {
            val request = Request.Builder().url(update.apkUrl).build()
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            AppUpdateChecker.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("APK download failed (HTTP ${response.code})")
                response.body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            size += count
                            require(size <= update.size && size <= PreviewUpdate.MAX_APK_BYTES) { "APK exceeds expected size" }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            require(size == update.size) { "Incomplete APK download" }
            require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256) { "APK checksum mismatch. Check again." }
            verifyPackage(context, temporary, update)
            target.delete()
            check(temporary.renameTo(target)) { "Could not save downloaded update" }
            return target
        } finally {
            temporary.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, file: File, update: PreviewUpdate) {
        val manager = context.packageManager
        val archive = requireNotNull(manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)) { "Invalid APK" }
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(archive.packageName == context.packageName) { "APK belongs to another application" }
        require(archive.longVersionCode == update.versionCode && archive.longVersionCode > installed.longVersionCode) { "APK version does not match the update" }
        val expected = installed.signingInfo?.apkContentsSigners?.toSet()
        require(!expected.isNullOrEmpty() && archive.signingInfo?.apkContentsSigners?.toSet() == expected) {
            "APK signature differs from this installation. Install the preview-channel APK once to enable future updates."
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
}
