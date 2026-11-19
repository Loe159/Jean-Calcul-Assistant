package io.github.mangi.eta.agent.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.basic.Text
import androidx.compose.foundation.LocalIndication
import io.github.suqi8.coui.kmp.utils.SinkFeedback

internal enum class EtaSpeechPhase { IDLE, STARTING, LISTENING, RECOGNIZING }

internal data class EtaSpeechState(
    val phase: EtaSpeechPhase = EtaSpeechPhase.IDLE,
    val errorRes: Int? = null,
    val message: String? = null,
    val configureAvailable: Boolean = false,
    val feedbackIsError: Boolean = true,
    /** 用户正按住麦克风说话：松手才结束，界面保持输入栏而不切到语音态。 */
    val holdToTalk: Boolean = false,
) {
    val active: Boolean get() = phase != EtaSpeechPhase.IDLE
    val hasFeedback: Boolean get() = !message.isNullOrBlank() || errorRes != null
}

@Composable
internal fun EtaSpeechFeedback(
    speech: EtaSpeechState,
    colors: EtaVoicePanelColors,
    onOpenSpeechSettings: () -> Unit,
) {
    val message = speech.message ?: speech.errorRes?.let { stringResource(it) }
    val dotColor = if (speech.feedbackIsError) colors.error else colors.accent
    val actionTint = colors.accent
    val actionBackground = actionTint.copy(alpha = if (colors.dark) 0.22f else 0.12f)
    val rowSource = remember { MutableInteractionSource() }
    val buttonSource = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(120)) + shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .couiAssistantCapsule(
                        surfaceColor = colors.chipBackground,
                        strokeColor = colors.chipStroke,
                        dark = colors.dark,
                        elevation = AssistantCapsuleElevation.Chip,
                    )
                    .then(
                        if (speech.configureAvailable) {
                            Modifier.clickable(
                                interactionSource = rowSource,
                                indication = SinkFeedback(),
                                role = Role.Button,
                                onClick = onOpenSpeechSettings,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = 14.dp,
                        end = if (speech.configureAvailable) 6.dp else 14.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.5.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = message.orEmpty(),
                    color = colors.inputPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (speech.configureAvailable) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(actionBackground, CircleShape)
                            .clickable(
                                interactionSource = buttonSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = onOpenSpeechSettings,
                            )
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.speech_open_settings),
                            color = actionTint,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
