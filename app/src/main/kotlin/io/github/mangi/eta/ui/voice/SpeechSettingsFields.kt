package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.PreferenceSection
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceFooter

@Composable
internal fun AsrProvider.label(): String = stringResource(
    when (this) {
        AsrProvider.QWEN_REALTIME -> R.string.speech_asr_qwen_realtime
        AsrProvider.QWEN_FLASH -> R.string.speech_asr_qwen_flash
        AsrProvider.QWEN_FILE -> R.string.speech_asr_qwen_file
        AsrProvider.DOUBAO -> R.string.speech_asr_doubao
        AsrProvider.MIMO -> R.string.speech_asr_mimo
        AsrProvider.DEEPSEEK -> R.string.speech_asr_deepseek
    },
)

@Composable
internal fun TtsProvider.label(): String = stringResource(
    when (this) {
        TtsProvider.NONE -> R.string.speech_tts_none
        TtsProvider.QWEN -> R.string.speech_tts_qwen
        TtsProvider.DOUBAO -> R.string.speech_tts_doubao
        TtsProvider.GEMINI -> R.string.speech_tts_gemini
        TtsProvider.MINIMAX -> R.string.speech_tts_minimax
        TtsProvider.MIMO -> R.string.speech_tts_mimo
        TtsProvider.DEEPSEEK -> R.string.speech_tts_deepseek
    },
)

@Composable
internal fun SpeechGroupCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PreferenceSection(title = title, content = content)
}

@Composable
internal fun SpeechNote(text: String, modifier: Modifier = Modifier) {
    PreferenceFooter(text = text, modifier = modifier)
}

@Composable
internal fun SpeechLoadingState(failure: String?) {
    if (failure != null) {
        ListEmptyState(title = failure, icon = Icons.Rounded.ErrorOutline)
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            InfiniteProgressIndicator(size = 24.dp)
        }
    }
}

@Composable
internal fun SpeechFieldColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SpeechField(
    label: String,
    value: String,
    secret: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    var visible by rememberSaveable(label) { mutableStateOf(false) }
    CouiTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        enabled = enabled,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(28.dp)
                        .clickable(role = Role.Button) { visible = !visible },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(
                            if (visible) R.string.page_hide_bb0e7e else R.string.page_show_71b677,
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun SpeechSaveBlock(store: SpeechSettingsStore, validate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = stringResource(if (store.saving) R.string.speech_saving else R.string.speech_save)
        CouiButton(
            text = text,
            enabled = store.loaded && !store.saving,
            role = CouiButtonRole.Primary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { store.save(validate) },
        )
        store.message?.let { message ->
            Text(
                text = message,
                style = COUITheme.textStyles.bodyXS,
                color = if (store.messageIsError) StatusError else StatusSuccess,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
