package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.SpeechCredentialField
import io.github.mangi.eta.data.model.TtsProvider
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference

private val OPENAI_TRANSCRIPTION_MODELS = listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1")
private val OPENAI_SPEECH_MODELS = listOf("gpt-4o-mini-tts", "tts-1", "tts-1-hd")
private val OPENAI_VOICES = listOf(
    "alloy", "ash", "ballad", "cedar", "coral", "echo", "fable", "marin", "nova", "onyx", "sage", "shimmer", "verse",
)

@Composable
internal fun AsrProvider.label(): String = stringResource(
    when (this) {
        AsrProvider.ANDROID -> R.string.speech_provider_android
        AsrProvider.OPENAI -> R.string.speech_provider_openai
        AsrProvider.VLLM -> R.string.speech_provider_vllm
    },
)

@Composable
internal fun TtsProvider.label(): String = stringResource(
    when (this) {
        TtsProvider.NONE -> R.string.speech_tts_none
        TtsProvider.ANDROID -> R.string.speech_provider_android
        TtsProvider.OPENAI -> R.string.speech_provider_openai
        TtsProvider.VLLM -> R.string.speech_provider_vllm
    },
)

/** Connexion et modèle du moteur choisi ; le moteur Android n’a besoin de rien. */
@Composable
internal fun SpeechConnectionSection(store: SpeechSettingsStore, synthesis: Boolean) {
    val openAi = if (synthesis) store.settings.tts == TtsProvider.OPENAI else store.settings.asr == AsrProvider.OPENAI
    val vllm = if (synthesis) store.settings.tts == TtsProvider.VLLM else store.settings.asr == AsrProvider.VLLM
    when {
        openAi -> OpenAiConnectionSection(store, synthesis)
        vllm -> VllmConnectionSection(store, synthesis)
        else -> SpeechNote(
            stringResource(if (synthesis) R.string.speech_android_tts_note else R.string.speech_android_asr_note),
        )
    }
}

@Composable
private fun OpenAiConnectionSection(store: SpeechSettingsStore, synthesis: Boolean) {
    val settings = store.settings
    val config = settings.openAi
    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_connection)) {
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_api_key),
                    value = store.credentials.openAi,
                    secret = true,
                    onChange = { store.credential(SpeechCredentialField.OPENAI, it.trim()) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            if (synthesis) {
                SpeechChoice(
                    title = stringResource(R.string.speech_model),
                    options = OPENAI_SPEECH_MODELS,
                    selected = config.speechModel,
                    onSelect = { store.edit(settings.copy(openAi = config.copy(speechModel = it))) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SpeechChoice(
                    title = stringResource(R.string.speech_voice),
                    options = OPENAI_VOICES,
                    selected = config.voice,
                    onSelect = { store.edit(settings.copy(openAi = config.copy(voice = it))) },
                )
                SpeechFieldColumn {
                    SpeechField(
                        label = stringResource(R.string.speech_instructions),
                        value = config.instructions,
                        singleLine = false,
                        onChange = { store.edit(settings.copy(openAi = config.copy(instructions = it.take(500)))) },
                    )
                }
            } else {
                SpeechChoice(
                    title = stringResource(R.string.speech_model),
                    options = OPENAI_TRANSCRIPTION_MODELS,
                    selected = config.transcriptionModel,
                    onSelect = { store.edit(settings.copy(openAi = config.copy(transcriptionModel = it))) },
                )
            }
        }
        SpeechNote(stringResource(if (synthesis) R.string.speech_openai_tts_note else R.string.speech_openai_asr_note))
    }
}

@Composable
private fun VllmConnectionSection(store: SpeechSettingsStore, synthesis: Boolean) {
    val settings = store.settings
    val config = settings.vllm
    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_connection)) {
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(
                        if (store.hasProviderVllm) R.string.speech_vllm_address_optional else R.string.speech_vllm_address,
                    ),
                    value = config.baseUrl,
                    onChange = { store.edit(settings.copy(vllm = config.copy(baseUrl = it.trim()))) },
                )
                SpeechField(
                    label = stringResource(R.string.speech_vllm_api_key),
                    value = store.credentials.vllm,
                    secret = true,
                    onChange = { store.credential(SpeechCredentialField.VLLM, it.trim()) },
                )
                if (synthesis) {
                    SpeechField(
                        label = stringResource(R.string.speech_model),
                        value = config.speechModel,
                        onChange = { store.edit(settings.copy(vllm = config.copy(speechModel = it.trim()))) },
                    )
                    SpeechField(
                        label = stringResource(R.string.speech_vllm_voice),
                        value = config.voice,
                        onChange = { store.edit(settings.copy(vllm = config.copy(voice = it.trim()))) },
                    )
                } else {
                    SpeechField(
                        label = stringResource(R.string.speech_model),
                        value = config.transcriptionModel,
                        onChange = { store.edit(settings.copy(vllm = config.copy(transcriptionModel = it.trim()))) },
                    )
                }
            }
        }
        SpeechNote(stringResource(if (synthesis) R.string.speech_vllm_tts_note else R.string.speech_vllm_asr_note))
    }
}

/** Liste COUI native ; une valeur saisie ailleurs (ancienne config) reste affichée en tête. */
@Composable
private fun SpeechChoice(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val items = if (selected.isBlank() || selected in options) options else listOf(selected) + options
    OverlayDropdownPreference(
        title = title,
        items = items,
        selectedIndex = items.indexOf(selected).coerceAtLeast(0),
        onSelectedIndexChange = { index -> items.getOrNull(index)?.let(onSelect) },
    )
}
