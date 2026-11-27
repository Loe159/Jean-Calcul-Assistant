package io.github.mangi.eta.devpreview

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.R
import io.github.mangi.eta.data.update.ApkVerification
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun devHubPreferences(context: Context) = context.getSharedPreferences("dev_preview_hub", Context.MODE_PRIVATE)

internal data class DevInstallState(
    val buildId: String? = null,
    val installing: Boolean = false,
    val error: String? = null,
    val confirmation: Intent? = null,
    val installed: DevBuildInfo? = null,
)

internal object DevPackageInstaller {
    private const val PENDING_BUILD = "pending_build"
    private const val PENDING_SESSION = "pending_session"
    private const val INSTALLED_BUILD = "installed_build"
    private const val INSTALLED_UPDATE_TIME = "installed_update_time"
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(DevInstallState())
    val state = mutableState.asStateFlow()

    fun restore(context: Context) {
        if (!BuildConfig.DEV_PREVIEW) return
        val prefs = devHubPreferences(context)
        val pending = read(context, PENDING_BUILD)
        if (pending != null && !state.value.installing) {
            val sessionId = prefs.getInt(PENDING_SESSION, -1)
            if (context.packageManager.packageInstaller.getSessionInfo(sessionId) != null) {
                mutableState.value = DevInstallState(buildId = pending.id, installing = true)
            } else {
                val installed = installed(context)
                clearPending(context)
                mutableState.value = DevInstallState(
                    buildId = pending.id,
                    error = if (installed?.id == pending.id) null else context.getString(R.string.dev_hub_install_interrupted),
                    installed = installed,
                )
            }
        }
    }

    fun install(context: Context, client: DevHubClient, build: DevBuild) {
        if (!BuildConfig.DEV_PREVIEW || state.value.installing) return
        val app = context.applicationContext
        mutableState.value = DevInstallState(buildId = build.info.id, installing = true)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { stage(app, client, build) }
            } catch (error: Exception) {
                mutableState.value = DevInstallState(
                    buildId = build.info.id,
                    error = error.message ?: app.getString(R.string.dev_hub_install_failed),
                )
            }
        }
    }

    private fun stage(context: Context, client: DevHubClient, build: DevBuild) {
        check(context.packageManager.canRequestPackageInstalls()) { context.getString(R.string.dev_hub_install_permission) }
        val apk = File.createTempFile("dev-update-", ".apk", context.cacheDir)
        try {
            ApkVerification.download(client.apk(build.info.id), apk, build.size, build.sha256, MAX_APK_BYTES)
            ApkVerification.verifyPackage(context, apk, allowSameVersion = BuildConfig.DEV_PREVIEW)
            commit(context, apk, build.info)
        } finally {
            apk.delete()
        }
    }

    private fun commit(context: Context, apk: File, info: DevBuildInfo) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            requestUpdateOwnership(context, this)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                check(devHubPreferences(context).edit()
                    .putString(PENDING_BUILD, info.toJson())
                    .putInt(PENDING_SESSION, sessionId)
                    .commit()) { context.getString(R.string.dev_hub_install_failed) }
                val callback = PendingIntent.getBroadcast(
                    context, sessionId, Intent(context, DevInstallStatusReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            clearPending(context)
            throw error
        }
    }

    // Only the dev manifest declares this permission. Normal lint also analyzes the shared class.
    @SuppressLint("MissingPermission")
    private fun requestUpdateOwnership(context: Context, params: PackageInstaller.SessionParams) {
        if (Build.VERSION.SDK_INT >= 34 &&
            context.checkSelfPermission(Manifest.permission.ENFORCE_UPDATE_OWNERSHIP) == PackageManager.PERMISSION_GRANTED
        ) {
            params.setRequestUpdateOwnership(true)
        }
    }

    fun handleStatus(context: Context, intent: Intent) {
        if (!BuildConfig.DEV_PREVIEW) return
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (session < 0 || session != devHubPreferences(context).getInt(PENDING_SESSION, -1)) return
        val info = read(context, PENDING_BUILD) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmation != null) {
                    mutableState.value = DevInstallState(info.id, installing = true, confirmation = confirmation)
                    runCatching { context.startActivity(confirmation) }
                    DevHubNotifications.confirmInstall(context, confirmation)
                    return
                }
                finish(context, info, error = context.getString(R.string.dev_hub_install_failed))
            }
            PackageInstaller.STATUS_SUCCESS -> finish(context, info)
            else -> finish(context, info, error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                .takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.dev_hub_install_failed))
        }
    }

    fun installed(context: Context): DevBuildInfo? {
        if (!BuildConfig.DEV_PREVIEW) return null
        DevBuildInfo.read(context)?.let { return it }
        val savedTime = devHubPreferences(context).getLong(INSTALLED_UPDATE_TIME, 0)
        return if (savedTime > 0 && savedTime == packageUpdateTime(context)) read(context, INSTALLED_BUILD) else null
    }

    fun onPackageReplaced(context: Context): DevBuildInfo? {
        val info = installed(context) ?: read(context, PENDING_BUILD)
        if (info != null) finish(context, info)
        return info
    }

    private fun finish(context: Context, info: DevBuildInfo, error: String? = null) {
        val prefs = devHubPreferences(context).edit().remove(PENDING_BUILD).remove(PENDING_SESSION)
        val installed = if (error == null) DevBuildInfo.read(context) ?: info else null
        if (installed != null) prefs.putString(INSTALLED_BUILD, installed.toJson())
            .putLong(INSTALLED_UPDATE_TIME, packageUpdateTime(context))
        prefs.apply()
        DevHubNotifications.clearConfirmation(context)
        mutableState.value = DevInstallState(buildId = info.id, error = error, installed = installed)
    }

    private fun clearPending(context: Context) {
        devHubPreferences(context).edit().remove(PENDING_BUILD).remove(PENDING_SESSION).apply()
    }

    private fun read(context: Context, key: String): DevBuildInfo? =
        devHubPreferences(context).getString(key, null)?.let { runCatching { DevBuildInfo.parse(JSONObject(it)) }.getOrNull() }

    @Suppress("DEPRECATION")
    private fun packageUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    private fun DevBuildInfo.toJson() = JSONObject()
        .put("id", id).put("branch", branch).put("commit", commit)
        .put("dirty", dirty).put("createdAt", createdAt).toString()
}

class DevInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEV_PREVIEW) DevPackageInstaller.handleStatus(context, intent)
    }
}

class DevPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEV_PREVIEW || intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val installed = DevPackageInstaller.onPackageReplaced(context)
        DevHubNotifications.installed(context, installed?.branch)
        DevLinkService.startIfEnabled(context)
    }
}
