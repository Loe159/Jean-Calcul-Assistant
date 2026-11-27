package io.github.mangi.eta.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import okhttp3.Request

internal object PreviewUpdateInstaller {
    fun download(context: Context, update: PreviewUpdate): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val temporary = File(directory, "download.part")
        val target = File(directory, "update.apk")
        try {
            val request = Request.Builder().url(update.apkUrl).build()
            ApkVerification.download(
                AppUpdateChecker.client.newCall(request), temporary, update.size, update.sha256, PreviewUpdate.MAX_APK_BYTES,
            )
            ApkVerification.verifyPackage(context, temporary, update.versionCode)
            target.delete()
            check(temporary.renameTo(target)) { "Could not save downloaded update" }
            return target
        } finally {
            temporary.delete()
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
}
