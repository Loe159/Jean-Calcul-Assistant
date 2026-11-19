package io.github.mangi.eta.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.menu.OverlayIconDropdownMenu
import io.github.mangi.eta.agent.model.AgentFileReference
import io.github.mangi.eta.agent.model.AgentFileReferenceKind
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

internal val ChatInputPopupMargin = 8.dp
internal val ChatInputActionSize = 40.dp
internal val ChatInputActionIconSize = 24.dp

@Composable
internal fun AgentAttachmentPickerButton(
    popupMaxHeight: Dp,
    onAttachImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
    onAttachFilePath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showPathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onAttachImage(uri.toString())
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onAttachFiles(uris.map { it.toString() })
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onAttachFolder(uri.toString())
    }

    val options = listOf(
        Icons.Rounded.Image to stringResource(R.string.attachment_image),
        Icons.Rounded.Description to stringResource(R.string.attachment_file),
        Icons.Rounded.Folder to stringResource(R.string.attachment_folder),
        Icons.Rounded.Edit to stringResource(R.string.attachment_enter_path),
    )
    val onSelectOption: (Int) -> Unit = { index ->
        when (index) {
            0 -> photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            1 -> filePicker.launch(arrayOf("*/*"))
            2 -> folderPicker.launch(null)
            3 -> {
                pathInput = ""
                showPathDialog = true
            }
        }
    }

    val entry = DropdownEntry(
        items = options.mapIndexed { index, (icon, label) ->
            DropdownItem(
                text = label,
                icon = { iconModifier -> Icon(icon, contentDescription = null, modifier = iconModifier) },
                onClick = { onSelectOption(index) },
            )
        },
    )
    OverlayIconDropdownMenu(
        entry = entry,
        modifier = modifier,
        maxHeight = popupMaxHeight,
        minWidth = 34.dp,
        minHeight = 34.dp,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.ui_add_attachment_dba9e8),
            modifier = Modifier.size(21.dp),
            tint = COUITheme.colorScheme.labelPrimary.copy(alpha = 0.72f),
        )
    }

    CouiInputDialog(
        visible = showPathDialog,
        title = stringResource(R.string.ui_input_file_path_36d474),
        message = stringResource(R.string.ui_supports_files_and_folders_under_internal_storage_or_520786),
        value = pathInput,
        onValueChange = { pathInput = it },
        placeholder = stringResource(R.string.ui_absolute_path_9ac6fc),
        confirmText = stringResource(R.string.attachment_add),
        confirmEnabled = pathInput.trim().startsWith('/'),
        onConfirm = {
            val path = pathInput.trim()
            showPathDialog = false
            onAttachFilePath(path)
        },
        onDismissRequest = { showPathDialog = false },
    )
}

@Composable
internal fun PendingFileReferenceStrip(
    references: List<PendingFileReferenceUi>,
    onRemoveReference: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(references.size) }
    LaunchedEffect(references.size) {
        if (references.size > previousCount && references.isNotEmpty()) {
            listState.animateScrollToItem(references.lastIndex)
        }
        previousCount = references.size
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = references,
            key = { it.id },
        ) { pending ->
            val reference = pending.reference
            Row(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        fadeOutSpec = tween(durationMillis = 120),
                    )
                    .attachmentItemEntrance()
                    .height(42.dp)
                    .widthIn(max = 250.dp)
                    .squircleSurface(
                        color = COUITheme.colorScheme.cardPressed,
                        cornerRadius = 12.dp,
                    )
                    .squircleBorder(
                        width = 0.5.dp,
                        color = COUITheme.colorScheme.outline.copy(alpha = 0.5f),
                        cornerRadius = 12.dp,
                    )
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (reference.kind == AgentFileReferenceKind.Directory) {
                        Icons.Rounded.FolderOpen
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = COUITheme.colorScheme.primary,
                )
                Text(
                    text = reference.displayName +
                        if (reference.kind == AgentFileReferenceKind.Directory) "/" else "",
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveReference(pending.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_remove_file_reference_04bbfc),
                        modifier = Modifier.size(15.dp),
                        tint = COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
        }
    }
}

/**
 * 附件胶囊与缩略图的入场动效：在 graphicsLayer 阶段驱动缩放、纵向微移与透明度，避免引发组合期重组。
 */
internal fun Modifier.attachmentItemEntrance(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 420f,
            ),
        )
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.9f + 0.1f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 6.dp.toPx()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SentFileReferenceFlow(
    references: List<AgentFileReference>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        references.forEach { reference ->
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .widthIn(max = 280.dp)
                    .squircleSurface(
                        color = COUITheme.colorScheme.background,
                        cornerRadius = 12.dp,
                    )
                    .squircleBorder(
                        width = 0.5.dp,
                        color = COUITheme.colorScheme.outline.copy(alpha = 0.45f),
                        cornerRadius = 12.dp,
                    )
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (reference.kind == AgentFileReferenceKind.Directory) {
                        Icons.Rounded.FolderOpen
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = COUITheme.colorScheme.primary,
                )
                Text(
                    text = reference.displayName +
                        if (reference.kind == AgentFileReferenceKind.Directory) "/" else "",
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
