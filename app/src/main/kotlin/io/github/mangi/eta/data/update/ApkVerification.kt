package io.github.mangi.eta.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.Call

internal object ApkVerification {
    fun download(call: Call, target: File, expectedSize: Long, sha256: String, maxSize: Long) {
        require(expectedSize in 1..maxSize) { "Invalid APK size" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("APK download failed (HTTP ${response.code})")
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        size += count
                        require(size <= expectedSize) { "APK exceeds expected size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        require(size == expectedSize) { "Incomplete APK download" }
        require(digest.digest().joinToString("") { "%02x".format(it) } == sha256) {
            "APK checksum mismatch. Check again."
        }
    }

    @Suppress("DEPRECATION")
    fun verifyPackage(context: Context, file: File, expectedVersion: Long? = null, allowSameVersion: Boolean = false) {
        val manager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = requireNotNull(manager.getPackageArchiveInfo(file.path, flags)) { "Invalid APK" }
        val installed = manager.getPackageInfo(context.packageName, flags)
        verifyIdentity(
            archive = ApkIdentity(archive.packageName, archive.longVersionCode, archive.signingInfo?.apkContentsSigners.signers()),
            installed = ApkIdentity(context.packageName, installed.longVersionCode, installed.signingInfo?.apkContentsSigners.signers()),
            expectedVersion = expectedVersion ?: installed.longVersionCode,
            allowSameVersion = allowSameVersion,
        )
    }

    fun verifyIdentity(archive: ApkIdentity, installed: ApkIdentity, expectedVersion: Long, allowSameVersion: Boolean) {
        require(archive.packageName == installed.packageName) { "APK belongs to another application" }
        require(archive.version == expectedVersion &&
            (archive.version > installed.version || allowSameVersion && archive.version == installed.version)
        ) { "APK version does not match the update" }
        require(installed.signers.isNotEmpty() && archive.signers == installed.signers) {
            "APK signature differs from this installation. Install the preview-channel APK once to enable future updates."
        }
    }

    private fun Array<Signature>?.signers() = orEmpty().map(Signature::toCharsString).toSet()
}

internal data class ApkIdentity(val packageName: String, val version: Long, val signers: Set<String>)
