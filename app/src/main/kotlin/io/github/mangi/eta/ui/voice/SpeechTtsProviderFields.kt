package io.github.mangi.eta.ui.voice

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.GeminiTtsModel
import io.github.mangi.eta.data.model.MiniMaxTtsModel
import io.github.mangi.eta.data.model.SpeechCredentialField
import io.github.mangi.eta.data.model.TtsProvider
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.HorizontalDivider

@Composable
internal fun GeminiConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.geminiTts
    val items = listOf(
        stringResource(R.string.speech_gemini_flash),
        stringResource(R.string.speech_gemini_flash_lite),
    )
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.geminiTts,
        onBaseUrl = { store.edit(store.settings.copy(geminiTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.GEMINI_TTS, it) },
        note = stringResource(R.string.speech_gemini_connection_note),
    ) {
        OverlayDropdownPreference(
            title = stringResource(R.string.speech_tts_model),
            items = items,
            selectedIndex = config.model.ordinal,
            onSelectedIndexChange = { index ->
                GeminiTtsModel.entries.getOrNull(index)?.let {
                    store.edit(store.settings.copy(geminiTts = config.copy(model = it)))
                }
            },
        )
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
    }
}

@Composable
internal fun MiniMaxConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.minimaxTts
    val items = listOf(
        stringResource(R.string.speech_minimax_turbo),
        stringResource(R.string.speech_minimax_hd),
    )
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.minimaxTts,
        onBaseUrl = { store.edit(store.settings.copy(minimaxTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.MINIMAX_TTS, it) },
        note = stringResource(R.string.speech_minimax_connection_note),
    ) {
        OverlayDropdownPreference(
            title = stringResource(R.string.speech_tts_model),
            items = items,
            selectedIndex = config.model.ordinal,
            onSelectedIndexChange = { index ->
                MiniMaxTtsModel.entries.getOrNull(index)?.let {
                    store.edit(store.settings.copy(minimaxTts = config.copy(model = it)))
                }
            },
        )
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
    }
}

@Composable
internal fun MimoTtsConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.mimoTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.mimoTts,
        onBaseUrl = { store.edit(store.settings.copy(mimoTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.MIMO_TTS, it) },
        note = stringResource(R.string.speech_mimo_tts_connection_note),
    )
}

@Composable
internal fun DeepSeekTtsConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.deepseekTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.deepseekTts,
        onBaseUrl = { store.edit(store.settings.copy(deepseekTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.DEEPSEEK_TTS, it) },
        note = stringResource(
            if (store.hasProviderDeepSeekKey) {
                R.string.speech_deepseek_tts_connection_note_with_provider
            } else {
                R.string.speech_deepseek_tts_connection_note
            },
        ),
    )
}

@Composable
internal fun SpeechDeliveryFields(store: SpeechSettingsStore) {
    when (store.settings.tts) {
        TtsProvider.GEMINI, TtsProvider.MIMO -> {
            val settings = store.settings
            val mimo = settings.tts == TtsProvider.MIMO
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_style),
                    value = if (mimo) settings.mimoTts.style else settings.geminiTts.style,
                    singleLine = false,
                    onChange = {
                        store.edit(
                            if (mimo) settings.copy(mimoTts = settings.mimoTts.copy(style = it))
                            else settings.copy(geminiTts = settings.geminiTts.copy(style = it)),
                        )
                    },
                )
            }
        }
        TtsProvider.MINIMAX -> {
            val config = store.settings.minimaxTts
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                .plus(config.speed).distinct().sorted()
            val items = speeds.map { "$it×" }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            OverlayDropdownPreference(
                title = stringResource(R.string.speech_speed),
                items = items,
                selectedIndex = speeds.indexOf(config.speed),
                onSelectedIndexChange = { index ->
                    speeds.getOrNull(index)?.let {
                        store.edit(store.settings.copy(minimaxTts = config.copy(speed = it)))
                    }
                },
            )
        }
        TtsProvider.DEEPSEEK -> {
            val config = store.settings.deepseekTts
            val onToggle: (Boolean) -> Unit = { checked ->
                store.edit(store.settings.copy(deepseekTts = config.copy(filterMarkdown = checked)))
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.speech_filter_markdown),
                checked = config.filterMarkdown,
                onCheckedChange = onToggle,
            )
        }
        TtsProvider.NONE, TtsProvider.QWEN, TtsProvider.DOUBAO -> Unit
    }
}
