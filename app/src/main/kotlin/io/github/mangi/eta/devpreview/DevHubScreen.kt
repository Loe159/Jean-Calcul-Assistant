package io.github.mangi.eta.devpreview

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.preview.previewAction
import io.github.mangi.eta.ui.preview.previewActionOf
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.TabRow
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Instant

internal enum class DevHubSection(val title: Int) {
    Reviews(R.string.dev_hub_reviews),
    Builds(R.string.dev_hub_builds),
    Components(R.string.dev_hub_components),
}

internal enum class DevHubConnection { NotConfigured, Loading, Online, Offline }

@Composable
internal fun DevHubScreen(
    section: DevHubSection,
    installed: DevBuildInfo?,
    liveLink: Boolean,
    snapshot: DevHubState?,
    connection: DevHubConnection,
    catalog: List<DevPreviewEntry>,
    installState: DevInstallState,
    notice: Int?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSection: (DevHubSection) -> Unit,
    onToggleLiveLink: (Boolean) -> Unit,
    onReview: (DevReview) -> Unit,
    onInstall: (DevBuild) -> Unit,
    onConfirm: () -> Unit,
    onPreview: (String) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val tabs = DevHubSection.entries.map { stringResource(it.title) }
    var search by rememberSaveable { mutableStateOf("") }
    val listState = key(section) { rememberLazyListState() }
    CouiLazyScaffoldPage(
        title = stringResource(R.string.dev_hub_title),
        onBack = onBack,
        compactTitle = true,
        listState = listState,
        actions = {
            if (connection != DevHubConnection.NotConfigured) {
                CouiTopAppBarIconButton(COUIIcons.Refresh, stringResource(R.string.dev_hub_refresh), onRefresh)
            }
        },
    ) {
        item(key = "header") {
            HubCard {
                CardListGroup {
                    item {
                        val metadata = installed?.let { buildMetadata(it, now) }
                        val summary = listOfNotNull(installed?.branch, metadata).joinToString("\n")
                        BasicComponent(
                            title = stringResource(R.string.dev_hub_installed_build),
                            summary = summary.ifEmpty { stringResource(R.string.dev_hub_unknown_build) },
                        )
                    }
                    item {
                        SwitchPreference(
                            modifier = DevHubRowModifier,
                            title = stringResource(R.string.dev_hub_live_link),
                            summary = stringResource(R.string.dev_hub_live_summary),
                            checked = liveLink,
                            enabled = connection != DevHubConnection.NotConfigured,
                            onCheckedChange = onToggleLiveLink,
                        )
                    }
                }
            }
        }
        item(key = "tabs") {
            TabRow(
                tabs = tabs,
                selectedTabIndex = section.ordinal,
                onTabSelected = { onSection(DevHubSection.entries[it]) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (notice != null) item(key = "notice") { HubMessage(notice) }
        when (connection) {
            DevHubConnection.NotConfigured -> if (section != DevHubSection.Components) {
                item(key = "no_hub") { HubMessage(R.string.dev_hub_not_configured, R.string.dev_hub_not_configured_detail) }
            }
            DevHubConnection.Offline -> item(key = "offline") {
                HubMessage(R.string.dev_hub_offline, R.string.dev_hub_offline_detail)
            }
            DevHubConnection.Loading -> if (section != DevHubSection.Components) {
                item(key = "loading") { HubMessage(R.string.dev_hub_loading, loading = true) }
            }
            DevHubConnection.Online -> Unit
        }
        when (section) {
            DevHubSection.Reviews -> if (snapshot != null) reviewRows(snapshot.reviews, onReview)
            DevHubSection.Builds -> if (snapshot != null) {
                buildRows(snapshot.branches, installed, installState, now, onInstall, onConfirm)
            }
            DevHubSection.Components -> {
                item(key = "search") {
                    CouiTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = stringResource(R.string.dev_hub_search),
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                        trailingIcon = {
                            Icon(COUIIcons.Search, contentDescription = null, tint = COUITheme.colorScheme.labelSecondary)
                        },
                    )
                }
                componentRows(catalog, search.trim(), onPreview)
            }
        }
    }
}

private fun LazyListScope.reviewRows(reviews: List<DevReview>, onReview: (DevReview) -> Unit) {
    item(key = "review_count") {
        val openCount = reviews.count { it.isOpen }
        PreferenceCategory(title = pluralStringResource(R.plurals.dev_hub_open_reviews, openCount, openCount))
    }
    if (reviews.isEmpty()) {
        item(key = "empty_reviews") { HubMessage(R.string.dev_hub_no_reviews) }
        return
    }
    item(key = "review_list") {
        HubCard {
            CardListGroup {
                reviews.forEach { review ->
                    item {
                        ArrowPreference(
                            modifier = DevHubRowModifier,
                            title = review.title,
                            summary = stringResource(R.string.dev_hub_build_meta, review.branch, reviewStatus(review)),
                            onClick = { onReview(review) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.buildRows(
    branches: List<DevBranch>,
    installed: DevBuildInfo?,
    installState: DevInstallState,
    now: Long,
    onInstall: (DevBuild) -> Unit,
    onConfirm: () -> Unit,
) {
    if (branches.none { it.builds.isNotEmpty() }) {
        item(key = "empty_builds") { HubMessage(R.string.dev_hub_no_builds) }
    }
    items(branches, key = { "branch:${it.branch}" }) { branch ->
        val latest = branch.builds.firstOrNull() ?: return@items
        val isInstalled = installed?.id == latest.info.id
        val installedMarker = when {
            isInstalled -> stringResource(R.string.dev_hub_installed)
            installed?.branch == branch.branch -> stringResource(R.string.dev_hub_previous_installed, installed.commit.take(7))
            else -> null
        }
        val statusForBranch = installState.takeIf { status -> branch.builds.any { it.info.id == status.buildId } }
        HubCard {
            CardListGroup {
                item {
                    BasicComponent(
                        modifier = DevHubRowModifier,
                        title = branch.branch,
                        summary = listOfNotNull(
                            latest.message.takeIf(String::isNotBlank),
                            buildMetadata(latest.info, now),
                            installedMarker,
                        ).joinToString("\n"),
                        endActions = if (!isInstalled && !installState.installing) {
                            {
                                Icon(
                                    COUIIcons.Download, contentDescription = null,
                                    modifier = Modifier.size(24.dp), tint = COUITheme.colorScheme.primary,
                                )
                            }
                        } else null,
                        onClickLabel = stringResource(R.string.dev_hub_install),
                        onClick = if (!isInstalled && !installState.installing) ({ onInstall(latest) }) else null,
                    )
                }
                if (statusForBranch != null && (statusForBranch.installing || statusForBranch.error != null)) {
                    item { InstallRow(statusForBranch, onConfirm) }
                }
            }
        }
    }
    if (installState.buildId != null && branches.none { branch -> branch.builds.any { it.info.id == installState.buildId } } &&
        (installState.installing || installState.error != null)
    ) {
        item(key = "install_status") { HubCard { InstallRow(installState, onConfirm) } }
    }
}

private fun LazyListScope.componentRows(entries: List<DevPreviewEntry>, search: String, onPreview: (String) -> Unit) {
    val files = entries.filter { it.label.contains(search, ignoreCase = true) || it.file.contains(search, ignoreCase = true) ||
        it.fqn.contains(search, ignoreCase = true) }.groupBy { it.file }
    if (entries.isNotEmpty()) item(key = "components_hint") { PreferenceFooter(text = stringResource(R.string.dev_hub_components_hint)) }
    if (files.isEmpty()) item(key = "empty_components") {
        HubMessage(if (entries.isEmpty()) R.string.dev_hub_no_components else R.string.dev_hub_no_search_results)
    }
    files.forEach { (path, previews) ->
        item(key = "file:$path") {
            val name = path.substringAfterLast('/')
            val title = if (files.keys.count { it.substringAfterLast('/') == name } == 1) name else path
            PreferenceCategory(title)
            HubCard {
                CardListGroup {
                    previews.forEach { preview ->
                        item {
                            ArrowPreference(
                                modifier = DevHubRowModifier,
                                title = preview.label,
                                onClick = { onPreview(preview.fqn) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Preserve a 40 dp target inside the COUI row's content padding.
internal val DevHubRowModifier = Modifier.heightIn(min = 66.dp)

@Composable
internal fun HubCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) { content() }
}

@Composable
internal fun HubMessage(title: Int, summary: Int? = null, loading: Boolean = false) {
    HubCard {
        BasicComponent(
            title = stringResource(title),
            summary = summary?.let { stringResource(it) },
            endActions = if (loading) ({ InfiniteProgressIndicator(size = 20.dp) }) else null,
        )
    }
}

@Composable
internal fun InstallRow(state: DevInstallState, onConfirm: () -> Unit) {
    when {
        state.confirmation != null -> ArrowPreference(
            modifier = DevHubRowModifier,
            title = stringResource(R.string.dev_hub_confirm_install),
            onClick = onConfirm,
        )
        state.installing -> BasicComponent(
            title = stringResource(R.string.dev_hub_installing),
            endActions = { InfiniteProgressIndicator(size = 20.dp) },
        )
        state.error != null -> BasicComponent(title = stringResource(R.string.dev_hub_install_failed), summary = state.error)
    }
}

@Composable
private fun buildMetadata(info: DevBuildInfo, now: Long): String {
    val timestamp = remember(info.createdAt) {
        runCatching { Instant.parse(info.createdAt).toEpochMilli() }.getOrNull()
    }
    val age = timestamp?.let { DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString() }
    return listOfNotNull(
        listOfNotNull(info.commit.take(7), age).joinToString(" · "),
        stringResource(R.string.dev_hub_dirty).takeIf { info.dirty },
    ).joinToString("\n")
}

@Composable
private fun reviewStatus(review: DevReview): String = stringResource(when (review.status) {
    "answered" -> R.string.dev_hub_review_answered
    "cancelled" -> R.string.dev_hub_review_cancelled
    else -> R.string.dev_hub_review_open
})

private object DevHubPreviewData {
    val now = Instant.parse("2026-01-10T12:30:00Z").toEpochMilli()
    val build = DevBuild(
        DevBuildInfo("example-build", "feat/example", "a1b2c3d" + "0".repeat(33), true, "2026-01-10T12:00:00Z"),
        "Example", "Tighten the planning card", "2.0.0-preview.dev", "0".repeat(64), 400_000,
    )
    val secondBuild = build.copy(
        info = build.info.copy(id = "other-build", branch = "feat/example-layout", commit = "acb431d" + "0".repeat(33), dirty = false),
        message = "Adjust the card layout",
    )
    val installed = build.info.copy(id = "installed-example", branch = "feat/example-ready", dirty = false)
    val review = DevReview(
        id = "review-example",
        branch = build.info.branch,
        buildId = build.info.id,
        title = "Planning card density",
        body = "Which layout reads better at a glance?",
        images = listOf("comparison.png"),
        options = listOf(
            DevReviewOption("A", "Compact", "compact.png", "example.PlanningCardKt.CompactPreview"),
            DevReviewOption("B", "More detail", null, null),
        ),
        previewFqn = null,
        status = "open",
        createdAt = "2026-01-10T12:05:00Z",
    )
    val snapshot = DevHubState(
        3,
        listOf(DevBranch(build.info.branch, "Example", listOf(build)), DevBranch(secondBuild.info.branch, "Example", listOf(secondBuild)),
            DevBranch(installed.branch, "Example", listOf(build.copy(info = installed, message = "Align the settings rows")))),
        listOf(review, review.copy(id = "review-other", title = "Settings row spacing", buildId = null, status = "answered")),
    )
    val catalog = listOf(
        DevPreviewEntry("example.PlanningCardKt.CompactPreview", "example/PlanningCard.kt", "CompactPreview"),
        DevPreviewEntry("example.PlanningCardKt.ExpandedPreview", "example/PlanningCard.kt", "ExpandedPreview"),
        DevPreviewEntry("example.SettingsRowsKt.NotificationRowPreview", "example/SettingsRows.kt", "NotificationRowPreview"),
    )
}

@Composable
private fun DevHubSample(initialSection: DevHubSection, install: DevInstallState = DevInstallState(), configured: Boolean = true) {
    var section by remember { mutableStateOf(initialSection) }
    var liveLink by remember { mutableStateOf(configured) }
    CouiPreviewTheme {
        DevHubScreen(
            section = section,
            installed = DevHubPreviewData.installed.takeIf { configured },
            liveLink = liveLink,
            snapshot = DevHubPreviewData.snapshot.takeIf { configured },
            connection = if (configured) DevHubConnection.Online else DevHubConnection.NotConfigured,
            catalog = DevHubPreviewData.catalog,
            installState = install,
            notice = null,
            onBack = previewAction("onBack"),
            onRefresh = previewAction("onRefresh"),
            onSection = { section = it },
            onToggleLiveLink = { liveLink = it },
            onReview = previewActionOf("onReview"),
            onInstall = previewActionOf("onInstall"),
            onConfirm = previewAction("onConfirm"),
            onPreview = previewActionOf("onPreview"),
            now = DevHubPreviewData.now,
        )
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubReviewsPreview() = DevHubSample(DevHubSection.Reviews)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubBuildsInstallingPreview() =
    DevHubSample(DevHubSection.Builds, DevInstallState(DevHubPreviewData.build.info.id, installing = true))

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubBuildsErrorPreview() =
    DevHubSample(DevHubSection.Builds, DevInstallState(DevHubPreviewData.secondBuild.info.id, error = "APK checksum mismatch"))

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubComponentsPreview() = DevHubSample(DevHubSection.Components)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubNotConfiguredPreview() = DevHubSample(DevHubSection.Reviews, configured = false)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun DevHubReviewDetailPreview() {
    val answer = previewActionOf<String>("onAnswer")
    CouiPreviewTheme {
        DevReviewScreen(
            review = DevHubPreviewData.review,
            installedId = DevHubPreviewData.build.info.id,
            build = DevHubPreviewData.build,
            installState = DevInstallState(),
            sending = false,
            answerError = false,
            notice = null,
            onBack = previewAction("onBack"),
            onRefresh = previewAction("onRefresh"),
            onPreview = previewActionOf("onPreview"),
            onInstall = previewActionOf("onInstall"),
            onConfirm = previewAction("onConfirm"),
            onAnswer = { approve, choice, comment ->
                answer(if (approve) "approve ${choice.orEmpty()} $comment".trim() else "changes $comment".trim())
            },
            initialChoice = "A",
            image = { DevHubSampleImage() },
        )
    }
}

@Composable
private fun DevHubSampleImage() {
    val colors = COUITheme.colorScheme
    Canvas(DevReviewImageModifier) {
        drawRect(colors.container4)
        val corner = CornerRadius(8.dp.toPx())
        for (column in 0..1) {
            val x = size.width * (0.12f + column * 0.41f)
            drawRoundRect(colors.surface, Offset(x, size.height * 0.16f), Size(size.width * 0.35f, size.height * 0.68f), corner)
            drawRoundRect(colors.labelSecondary, Offset(x + size.width * 0.025f, size.height * 0.30f),
                Size(size.width * 0.16f, size.height * 0.055f), corner)
            drawRoundRect(colors.divider, Offset(x + size.width * 0.025f, size.height * 0.48f),
                Size(size.width * 0.25f, size.height * 0.045f), corner)
            if (column == 1) drawRoundRect(colors.divider, Offset(x + size.width * 0.025f, size.height * 0.62f),
                Size(size.width * 0.21f, size.height * 0.045f), corner)
        }
    }
}
