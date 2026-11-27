package io.github.mangi.eta.devpreview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.LocalChatImagePreviewer
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DevReviewScreen(
    review: DevReview,
    installedId: String?,
    build: DevBuild?,
    installState: DevInstallState,
    sending: Boolean,
    answerError: Boolean,
    notice: Int?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPreview: (String) -> Unit,
    onInstall: (DevBuild) -> Unit,
    onConfirm: () -> Unit,
    onAnswer: (approve: Boolean, choice: String?, comment: String) -> Unit,
    initialChoice: String? = null,
    offline: Boolean = false,
    image: @Composable (String) -> Unit,
) {
    var choice by rememberSaveable(review.id) { mutableStateOf(initialChoice) }
    var comment by rememberSaveable(review.id) { mutableStateOf("") }
    val needsBuild = review.buildId != null && review.buildId != installedId
    val canReply = review.isOpen && !sending
    CouiScaffoldPage(
        title = stringResource(R.string.dev_hub_review_detail),
        onBack = onBack,
        compactTitle = true,
        actions = { CouiTopAppBarIconButton(COUIIcons.Refresh, stringResource(R.string.dev_hub_refresh), onRefresh) },
    ) {
        if (offline) HubMessage(R.string.dev_hub_offline, R.string.dev_hub_offline_detail)
        HubCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(review.title, style = COUITheme.textStyles.displayXS, color = COUITheme.colorScheme.labelPrimary)
                Text(review.branch, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary)
                if (review.body.isNotBlank()) {
                    Text(review.body, style = COUITheme.textStyles.bodyL, color = COUITheme.colorScheme.labelPrimary)
                }
            }
            review.images.forEach { image(it) }
        }
        if (notice != null) HubMessage(notice)
        if (needsBuild || review.previewFqn != null) {
            HubCard {
                CardListGroup {
                    if (needsBuild) {
                        item {
                            ArrowPreference(
                                modifier = DevHubRowModifier,
                                title = stringResource(R.string.dev_hub_install_this_build),
                                summary = if (build == null) stringResource(R.string.dev_hub_unavailable_build) else null,
                                enabled = build != null && !installState.installing,
                                onClick = { build?.let(onInstall) },
                            )
                        }
                    }
                    if (review.buildId != null && installState.buildId == review.buildId &&
                        (installState.installing || installState.error != null)) {
                        item { InstallRow(installState, onConfirm) }
                    }
                    review.previewFqn?.let { fqn ->
                        item { LivePreviewRow(fqn, needsBuild, onPreview) }
                    }
                }
            }
        }
        if (review.options.isNotEmpty()) PreferenceCategory(stringResource(R.string.dev_hub_review_options))
        review.options.forEach { option ->
            val selected = if (review.isOpen) choice == option.id else review.answer?.choice == option.id
            val onSelect = { choice = option.id.takeUnless { selected } }
            HubCard {
                BasicComponent(
                    title = stringResource(R.string.dev_hub_option, option.id, option.label),
                    modifier = DevHubRowModifier,
                    role = Role.RadioButton,
                    selected = selected,
                    onClick = onSelect.takeIf { canReply },
                    endActions = { RadioButton(selected = selected, onClick = null, enabled = review.isOpen) },
                )
                option.image?.let { image(it) }
                option.previewFqn?.let { LivePreviewRow(it, needsBuild, onPreview) }
            }
        }
        if (review.isOpen) {
            if (answerError) HubMessage(R.string.dev_hub_answer_failed)
            if (sending) HubMessage(R.string.dev_hub_sending, loading = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val commentLabel = stringResource(R.string.dev_hub_comment)
                CouiTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = commentLabel,
                    fieldModifier = Modifier.semantics { contentDescription = commentLabel },
                    enabled = !sending,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                )
                CouiButton(
                    text = stringResource(R.string.dev_hub_approve),
                    onClick = { onAnswer(true, choice, comment) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canReply,
                    role = CouiButtonRole.Primary,
                    minHeight = 44.dp,
                    maxLines = 2,
                )
                CouiButton(
                    text = stringResource(R.string.dev_hub_request_changes),
                    onClick = { onAnswer(false, null, comment) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canReply,
                    minHeight = 44.dp,
                    maxLines = 2,
                )
            }
        } else {
            val answer = review.answer
            val verdict = when (answer?.verdict) {
                "choice" -> stringResource(R.string.dev_hub_review_choice, answer.choice.orEmpty())
                "approved" -> stringResource(R.string.dev_hub_review_approved)
                "changes" -> stringResource(R.string.dev_hub_review_changes_requested)
                else -> stringResource(R.string.dev_hub_review_cancelled)
            }
            HubCard { BasicComponent(title = verdict, summary = answer?.comment?.takeIf(String::isNotBlank)) }
        }
    }
}

@Composable
private fun LivePreviewRow(fqn: String, needsBuild: Boolean, onPreview: (String) -> Unit) {
    ArrowPreference(
        modifier = DevHubRowModifier,
        title = stringResource(R.string.dev_hub_open_live),
        summary = if (needsBuild) stringResource(R.string.dev_hub_live_needs_build) else null,
        enabled = !needsBuild,
        onClick = { onPreview(fqn) },
    )
}

@Composable
internal fun DevReviewLoading(
    connection: DevHubConnection,
    unavailable: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    CouiScaffoldPage(
        title = stringResource(R.string.dev_hub_review_detail),
        onBack = onBack,
        compactTitle = true,
        actions = {
            if (connection != DevHubConnection.NotConfigured) {
                CouiTopAppBarIconButton(COUIIcons.Refresh, stringResource(R.string.dev_hub_refresh), onRefresh)
            }
        },
    ) {
        when {
            unavailable -> HubMessage(R.string.dev_hub_unavailable_review)
            connection == DevHubConnection.Offline -> HubMessage(R.string.dev_hub_offline, R.string.dev_hub_offline_detail)
            connection == DevHubConnection.NotConfigured -> HubMessage(R.string.dev_hub_not_configured)
            else -> HubMessage(R.string.dev_hub_loading, loading = true)
        }
    }
}

@Composable
internal fun DevReviewImage(client: DevHubClient?, reviewId: String, name: String) {
    val viewer = LocalChatImagePreviewer.current
    var retry by rememberSaveable(reviewId, name) { mutableStateOf(0) }
    val image by produceState<Result<ImageBitmap>?>(null, reviewId, name, retry) {
        value = null
        try {
            val bytes = client?.image(reviewId, name) ?: error("Hub not configured")
            val bitmap = withContext(Dispatchers.IO) { decodeImage(bytes) } ?: error("Invalid image")
            value = Result.success(bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            value = Result.failure(error)
        }
    }
    val bitmap = image?.getOrNull()
    when {
        image == null -> BasicComponent(title = stringResource(R.string.dev_hub_image_loading))
        bitmap == null -> ArrowPreference(
            modifier = DevHubRowModifier,
            title = stringResource(R.string.dev_hub_image_unavailable),
            onClick = { retry += 1 },
        )
        else -> Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.dev_hub_image_open, name),
            contentScale = ContentScale.Fit,
            modifier = DevReviewImageModifier.clickable(role = Role.Button) { viewer?.openBitmap(bitmap) },
        )
    }
}

internal val DevReviewImageModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(96.dp)

private fun decodeImage(bytes: ByteArray): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val longest = maxOf(options.outWidth, options.outHeight)
    var downsample = 1
    while (longest / downsample > 3072) downsample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = downsample
    })?.asImageBitmap()
}
