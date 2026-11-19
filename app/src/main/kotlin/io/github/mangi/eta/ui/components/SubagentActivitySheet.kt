package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.subagent.SubagentActivitySnapshot
import io.github.mangi.eta.agent.subagent.SubagentStepSnapshot
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.ui.unit.DpSize
import io.github.mangi.eta.ui.components.coui.panelBlur
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet

/**
 * 聊天时间线中的 Subagent 节点行：保持内联极简与留白，点击呼出实时检视面板。
 */
@Composable
internal fun SubagentActivityRow(
    message: ToolActivityMessageUi,
    snapshot: SubagentActivitySnapshot,
    elapsedSeconds: Int?,
    modifier: Modifier = Modifier,
) {
    var showInspector by rememberSaveable(message.id) { mutableStateOf(false) }
    val running = message.status == ToolActivityStatusUi.Running
    val failed = message.status == ToolActivityStatusUi.Failed ||
        snapshot.status == SubagentActivitySnapshot.STATUS_FAILED
    val typeLabel = snapshot.subagentType.ifBlank { "subagent" }
    val taskTitle = snapshot.description.ifBlank { message.argumentsSummary.ifBlank { "Subagent" } }
    val subtitle = when {
        running -> snapshot.currentAction.takeIf { it.isNotBlank() }
            ?: if (snapshot.toolStepCount > 0) "Completed ${snapshot.toolStepCount} steps" else "Running…"
        failed -> snapshot.error?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: message.outcomeLine()
            ?: "Execution incomplete"
        else -> when {
            snapshot.toolStepCount > 0 -> "Completed · ${snapshot.toolStepCount} tool calls"
            else -> message.outcomeLine() ?: "Done"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WorkRowHeight)
                .clip(RoundedCornerShape(10.dp))
                .clickable { showInspector = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubagentTypeBadge(type = typeLabel, active = running)
                    Spacer(modifier = Modifier.width(6.dp))
                    ShimmerText(
                        text = taskTitle,
                        active = running,
                        style = COUITheme.textStyles.bodyM,
                        color = if (running) {
                            COUITheme.colorScheme.labelPrimary
                        } else {
                            COUITheme.colorScheme.labelSecondary
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = COUITheme.textStyles.bodyXS,
                        color = if (failed && !running) {
                            StatusError
                        } else {
                            COUITheme.colorScheme.labelSecondary.copy(alpha = 0.75f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (elapsedSeconds != null && elapsedSeconds >= 3) {
                Text(
                    text = stringResource(R.string.tool_elapsed_seconds, elapsedSeconds),
                    style = COUITheme.textStyles.bodyXS,
                    color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "View subagent details",
                modifier = Modifier
                    .padding(start = 6.dp, end = 8.dp)
                    .size(14.dp),
                tint = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.6f),
            )
        }
    }

    val dark = COUITheme.isDark
    val sheetBlur = panelBlur(MaterialBlurDefaults::bottomSheet)
    OverlayBottomSheet(
        show = showInspector,
        title = taskTitle,
        startAction = {
            IconButton(onClick = { showInspector = false }) {
                Icon(
                    imageVector = COUIIcons.Basic.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = COUITheme.colorScheme.labelPrimary,
                )
            }
        },
        backgroundModifier = sheetBlur?.backgroundModifier ?: Modifier,
        backgroundColor = sheetBlur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
        onDismissRequest = { showInspector = false },
        insideMargin = DpSize(16.dp, 0.dp),
        materialStroke = sheetBlur != null,
    ) {
        SubagentInspectorContent(
            snapshot = snapshot,
            running = running,
            failed = failed,
        )
    }
}

@Composable
private fun SubagentTypeBadge(
    type: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (active) {
        COUITheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        COUITheme.colorScheme.surfaceWithCard
    }
    val contentColor = if (active) {
        COUITheme.colorScheme.primary
    } else {
        COUITheme.colorScheme.labelSecondary
    }
    Box(
        modifier = modifier
            .squircleSurface(color = containerColor, cornerRadius = 6.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type,
            style = COUITheme.textStyles.bodyXS.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubagentInspectorContent(
    snapshot: SubagentActivitySnapshot,
    running: Boolean,
    failed: Boolean,
) {
    val density = LocalDensity.current
    val maxSheetHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.76f
    var promptExpanded by rememberSaveable(snapshot.subagentId) { mutableStateOf(false) }
    val pulse = rememberDrawPhasePulse(active = running)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 顶部状态与元信息横条：留白克制，只展示关键身份与实时状态
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = pulse?.value ?: 1f }
                    .clip(CircleShape)
                    .background(
                        when {
                            running -> COUITheme.colorScheme.primary
                            failed -> StatusError
                            else -> StatusSuccess
                        },
                    ),
            )
            SubagentTypeBadge(type = snapshot.subagentType.ifBlank { "subagent" }, active = running)
            if (snapshot.subagentId.isNotBlank()) {
                Text(
                    text = snapshot.subagentId,
                    style = COUITheme.textStyles.bodyXS.copy(fontFamily = FontFamily.Monospace),
                    color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.75f),
                )
            }
            if (snapshot.model.isNotBlank()) {
                Text(
                    text = "· ${snapshot.model}",
                    style = COUITheme.textStyles.bodyXS,
                    color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        // 任务指令（Prompt）：浅色容器，长文本默认收起，点按展开
        if (snapshot.prompt.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        color = COUITheme.colorScheme.surfaceWithCard,
                        cornerRadius = 12.dp,
                    )
                    .clickable { promptExpanded = !promptExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (snapshot.resumed) "Prompt (Resumed)" else "Prompt",
                        style = COUITheme.textStyles.bodyXS.copy(fontFamily = FontFamily.Monospace),
                        color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                    ExpandChevron(
                        expanded = promptExpanded,
                        contentDescription = null,
                    )
                }
                Text(
                    text = snapshot.prompt,
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelSecondary,
                    maxLines = if (promptExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 内部执行时间线：沿用主聊天界面的 1dp 细线与圆点徽章
        if (snapshot.steps.isNotEmpty() || running) {
            Column(modifier = Modifier.fillMaxWidth()) {
                snapshot.steps.forEachIndexed { index, step ->
                    key(step.id) {
                        val isLastStep = index == snapshot.steps.lastIndex && !running
                        WorkTimelineNode(
                            icon = if (step.kind == SubagentStepSnapshot.KIND_THINKING) {
                                Icons.Rounded.Lightbulb
                            } else {
                                iconForTool(step.toolName)
                            },
                            tone = when (step.status) {
                                SubagentStepSnapshot.STATUS_RUNNING -> WorkNodeTone.Running
                                SubagentStepSnapshot.STATUS_FAILED -> WorkNodeTone.Failed
                                else -> WorkNodeTone.Done
                            },
                            isLast = isLastStep,
                            connectTop = index > 0,
                        ) {
                            SubagentStepRow(step = step)
                        }
                    }
                }
                if (running && snapshot.steps.none { it.status == SubagentStepSnapshot.STATUS_RUNNING }) {
                    WorkTimelineNode(
                        icon = Icons.Rounded.Lightbulb,
                        tone = WorkNodeTone.Running,
                        isLast = true,
                        connectTop = snapshot.steps.isNotEmpty(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = WorkRowHeight)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            ShimmerText(
                                text = snapshot.currentAction.ifBlank { "Running…" },
                                active = true,
                                style = COUITheme.textStyles.bodyM,
                                color = COUITheme.colorScheme.labelPrimary,
                            )
                        }
                    }
                }
            }
        }

        // 错误信息
        val error = snapshot.error?.takeIf { it.isNotBlank() && failed }
        if (error != null) {
            Text(
                text = error,
                style = COUITheme.textStyles.bodyS,
                color = StatusError,
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        color = StatusError.copy(alpha = 0.08f),
                        cornerRadius = 12.dp,
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        // 实时或最终汇报（Output）
        if (snapshot.output.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (running) "Streaming" else "Summary",
                    style = COUITheme.textStyles.bodyXS.copy(fontFamily = FontFamily.Monospace),
                    color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.7f),
                )
                SelectionContainer {
                    Text(
                        text = snapshot.output,
                        style = COUITheme.textStyles.bodyM,
                        color = COUITheme.colorScheme.labelPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleSurface(
                                color = COUITheme.colorScheme.surfaceWithCard,
                                cornerRadius = 12.dp,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubagentStepRow(
    step: SubagentStepSnapshot,
) {
    val hasDetail = step.detail.isNotBlank()
    var expanded by rememberSaveable(step.id) {
        mutableStateOf(step.kind == SubagentStepSnapshot.KIND_THINKING && step.status == SubagentStepSnapshot.STATUS_RUNNING)
    }
    val running = step.status == SubagentStepSnapshot.STATUS_RUNNING
    val failed = step.status == SubagentStepSnapshot.STATUS_FAILED

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WorkRowHeight)
                .clip(RoundedCornerShape(8.dp))
                .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerText(
                text = step.title.ifBlank { step.toolName.ifBlank { "Steps" } },
                active = running,
                style = COUITheme.textStyles.bodyM,
                color = when {
                    running -> COUITheme.colorScheme.labelPrimary
                    failed -> StatusError
                    else -> COUITheme.colorScheme.labelSecondary
                },
                modifier = Modifier.weight(1f),
            )
            if (hasDetail) {
                ExpandChevron(
                    expanded = expanded,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded && hasDetail) {
            SelectionContainer {
                Text(
                    text = step.detail,
                    style = if (step.kind == SubagentStepSnapshot.KIND_TOOL) {
                        COUITheme.textStyles.bodyXS.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        COUITheme.textStyles.bodyXS
                    },
                    color = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.85f),
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .squircleSurface(
                            color = COUITheme.colorScheme.surfaceWithCard,
                            cornerRadius = 10.dp,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
