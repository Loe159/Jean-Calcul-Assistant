package io.github.mangi.eta.devpreview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.update.PreviewUpdateInstaller
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.components.ChatImagePreviewHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class DevHubActivity : ComponentActivity() {
    internal val client by lazy { DevHubClient.configured() }
    internal var catalog = emptyList<DevPreviewEntry>()
        private set
    internal var installed: DevBuildInfo? = null
        private set
    internal var liveLink by mutableStateOf(false)
        private set
    internal var section by mutableStateOf(DevHubSection.Reviews)
        private set
    internal var connection by mutableStateOf(DevHubConnection.Loading)
        private set
    internal var snapshot by mutableStateOf<DevHubState?>(null)
        private set
    internal var reviewId by mutableStateOf<String?>(null)
        private set
    internal var review by mutableStateOf<DevReview?>(null)
        private set
    internal var reviewUnavailable by mutableStateOf(false)
        private set
    internal var answerInProgress by mutableStateOf(false)
        private set
    internal var answerError by mutableStateOf(false)
        private set
    internal var notice by mutableStateOf<Int?>(null)
        private set
    private var pendingInstall: String? = null
    private var permissionBuild: String? = null
    private var refreshJob: Job? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && liveLink) notice = R.string.dev_hub_notification_permission
    }

    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = permissionBuild
        permissionBuild = null
        if (id != null && packageManager.canRequestPackageInstalls()) {
            val build = snapshot?.findBuild(id)
            if (build != null) startInstall(build)
            else {
                pendingInstall = id
                refresh()
            }
        } else if (id != null) {
            notice = R.string.dev_hub_install_permission
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEV_PREVIEW) {
            finish()
            return
        }
        enableEdgeToEdge()
        DevPackageInstaller.restore(this)
        installed = DevPackageInstaller.installed(this)
        catalog = DevPreviewEntry.read(this)
        liveLink = client != null && DevLinkService.enabled(this)
        if (client == null) connection = DevHubConnection.NotConfigured
        savedInstanceState?.let {
            section = DevHubSection.valueOf(it.getString("section", DevHubSection.Reviews.name))
            reviewId = it.getString("review")
            pendingInstall = it.getString("pendingInstall")
            permissionBuild = it.getString("permissionBuild")
        } ?: route(intent)
        lifecycleScope.launch { DevLinkService.refreshSignal.drop(1).collect { refresh() } }
        setContent { DevHubContent() }
    }

    override fun onStart() {
        super.onStart()
        if (!BuildConfig.DEV_PREVIEW) return
        DevLinkService.startIfEnabled(this)
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!BuildConfig.DEV_PREVIEW) return
        setIntent(intent)
        route(intent)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("section", section.name)
        outState.putString("review", reviewId)
        outState.putString("pendingInstall", pendingInstall)
        outState.putString("permissionBuild", permissionBuild)
    }

    private fun route(intent: Intent) {
        // Only the non-exported activity can receive notification actions.
        if (intent.component?.className != DevHubActivity::class.java.name) return
        val path = intent.data?.pathSegments.orEmpty()
        if (path.size != 2) return
        val (target, id) = path
        when (target) {
            TARGET_REVIEW -> openReview(id, refresh = false)
            TARGET_BUILD, TARGET_INSTALL -> {
                closeReview()
                section = DevHubSection.Builds
                if (target == TARGET_INSTALL) pendingInstall = id
            }
        }
    }

    internal fun refresh() {
        val hub = client ?: return
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val id = reviewId
            try {
                val latest = hub.state()
                snapshot = latest
                connection = DevHubConnection.Online
                pendingInstall?.let { requested ->
                    pendingInstall = null
                    val build = latest.findBuild(requested)
                    if (build != null) startInstall(build) else notice = R.string.dev_hub_unavailable_build
                }
                if (id != null && id == reviewId) {
                    val detail = latest.reviews.firstOrNull { it.id == id } ?: try {
                        hub.review(id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (id == reviewId) {
                        reviewUnavailable = detail == null
                        review = detail ?: review
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                connection = DevHubConnection.Offline
            }
        }
    }

    internal fun openReview(id: String, refresh: Boolean = true) {
        section = DevHubSection.Reviews
        reviewId = id
        review = snapshot?.reviews?.firstOrNull { it.id == id }
        reviewUnavailable = false
        answerError = false
        notice = null
        if (refresh) refresh()
    }

    internal fun selectSection(section: DevHubSection) {
        this.section = section
    }

    internal fun closeReview() {
        reviewId = null
        review = null
        notice = null
    }

    internal fun toggleLiveLink(enabled: Boolean) {
        notice = null
        if (!DevLinkService.setEnabled(this, enabled)) {
            notice = R.string.dev_hub_live_failed
            return
        }
        liveLink = enabled
        if (enabled && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    internal fun startInstall(build: DevBuild) {
        val hub = client ?: return
        if (DevPackageInstaller.state.value.installing || DevPackageInstaller.installed(this)?.id == build.info.id) return
        notice = null
        if (!packageManager.canRequestPackageInstalls()) {
            permissionBuild = build.info.id
            installPermission.launch(PreviewUpdateInstaller.permissionIntent(this))
        } else {
            DevPackageInstaller.install(this, hub, build)
        }
    }

    internal fun confirmInstall() {
        DevPackageInstaller.state.value.confirmation?.let {
            if (runCatching { startActivity(it) }.isFailure) notice = R.string.dev_hub_install_failed
        }
    }

    internal fun openPreview(fqn: String) {
        val preview = Intent().setClassName(packageName, "androidx.compose.ui.tooling.PreviewActivity")
            .putExtra("composable", fqn)
        if (runCatching { startActivity(preview) }.isFailure) notice = R.string.dev_hub_open_live_failed
    }

    internal fun submit(id: String, approve: Boolean, choice: String?, comment: String, installedId: String?) {
        val hub = client ?: return
        if (answerInProgress) return
        answerError = false
        answerInProgress = true
        lifecycleScope.launch {
            try {
                val answered = hub.answer(id, DevHubJson.answerPayload(approve, choice, comment, installedId))
                if (reviewId == id) review = answered
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (reviewId == id) answerError = true
            } finally {
                answerInProgress = false
            }
        }
    }

    companion object {
        internal const val TARGET_REVIEW = "review"
        internal const val TARGET_BUILD = "build"
        internal const val TARGET_INSTALL = "install"

        internal fun openIntent(context: Context, target: String?, id: String?) =
            Intent(context, DevHubActivity::class.java).apply {
                if (target != null && id != null) {
                    data = Uri.Builder().scheme("jeancalcul-preview").authority("hub")
                        .appendPath(target).appendPath(id).build()
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}

@Composable
private fun DevHubActivity.DevHubContent() {
    val installState by DevPackageInstaller.state.collectAsStateWithLifecycle()
    val currentBuild = installState.installed ?: installed
    val currentReview = review
    AgentAppTheme(appearance = AppearanceSettings(), applyInterfaceScale = false) {
        BackHandler(enabled = reviewId != null, onBack = ::closeReview)
        ChatImagePreviewHost {
            if (reviewId == null) {
                DevHubScreen(
                    section = section,
                    installed = currentBuild,
                    liveLink = liveLink,
                    snapshot = snapshot,
                    connection = connection,
                    catalog = catalog,
                    installState = installState,
                    notice = notice,
                    onBack = ::finish,
                    onRefresh = ::refresh,
                    onSection = ::selectSection,
                    onToggleLiveLink = ::toggleLiveLink,
                    onReview = { openReview(it.id) },
                    onInstall = ::startInstall,
                    onConfirm = ::confirmInstall,
                    onPreview = ::openPreview,
                )
            } else if (currentReview != null) {
                DevReviewScreen(
                    review = currentReview,
                    installedId = currentBuild?.id,
                    build = snapshot?.findBuild(currentReview.buildId),
                    installState = installState,
                    sending = answerInProgress,
                    answerError = answerError,
                    notice = notice,
                    onBack = ::closeReview,
                    onRefresh = ::refresh,
                    onPreview = ::openPreview,
                    onInstall = ::startInstall,
                    onConfirm = ::confirmInstall,
                    onAnswer = { approve, choice, comment -> submit(currentReview.id, approve, choice, comment, currentBuild?.id) },
                    offline = connection == DevHubConnection.Offline,
                    image = { name -> DevReviewImage(client, currentReview.id, name) },
                )
            } else {
                DevReviewLoading(connection, reviewUnavailable, ::closeReview, ::refresh)
            }
        }
    }
}
