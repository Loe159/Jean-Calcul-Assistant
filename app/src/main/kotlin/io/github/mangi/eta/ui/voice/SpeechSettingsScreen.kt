package io.github.mangi.eta.ui.voice

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.HorizontalDivider

@Composable
internal fun SpeechSettingsScreen(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SpeechSettingsRepository.settingsFlow().collectAsState(initial = null)
    val current = settings

    CouiScaffoldPage(
        title = stringResource(R.string.speech_settings_title),
        onBack = onBack,
    ) {
        if (current == null) {
            SpeechLoadingState(failure = null)
            return@CouiScaffoldPage
        }
        SpeechGroupCard(title = stringResource(R.string.speech_group_service)) {
            ArrowPreference(
                title = stringResource(R.string.speech_recognition_title),
                summary = if (current.recognitionEnabled) current.asr.label()
                else stringResource(R.string.speech_recognition_disabled),
                onClick = { onNavigate(AppRoute.SpeechRecognition) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            ArrowPreference(
                title = stringResource(R.string.speech_synthesis_title),
                summary = current.tts.label(),
                onClick = { onNavigate(AppRoute.SpeechSynthesis) },
            )
        }
        SpeechGroupCard(title = stringResource(R.string.speech_group_assistant)) {
            SwitchPreference(
                title = stringResource(R.string.speech_auto_speak),
                summary = stringResource(R.string.speech_auto_speak_summary),
                checked = current.autoSpeak,
                enabled = current.tts != TtsProvider.NONE,
                onCheckedChange = { enabled ->
                    scope.launch {
                        SpeechSettingsRepository.save(current.copy(autoSpeak = enabled))
                    }
                },
            )
        }
        SpeechNote(stringResource(R.string.speech_overview_note))
        CouiPageBottomSpacer()
    }
}
