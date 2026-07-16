package fr.loevan.jeancalcul.assistant.session

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Full-screen voice session that preserves sight of the underlying activity while it owns input.
 */
@Suppress("TooManyFunctions")
class JeanCalculVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {
    private val visualState = mutableStateOf(AssistantSessionVisualState.INVOKED)
    private val lifecycleOwner = SessionLifecycleOwner()
    private val windowController = SessionWindowController(::closeSession)
    private lateinit var voiceSessionController: VoiceSessionController
    private var isClosing = false

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.create()
        getWindow()?.window?.decorView?.installSessionViewTreeOwners(lifecycleOwner)
        windowController.prepare(getWindow())
        voiceSessionController =
            VoiceSessionController(
                speechToTextProvider = AndroidSpeechToTextProvider(context),
                textToSpeechProvider = AndroidTextToSpeechProvider(context),
            )
    }

    override fun onCreateContentView(): View {
        getWindow()?.window?.decorView?.installSessionViewTreeOwners(lifecycleOwner)
        return ComposeView(context).apply {
            installSessionViewTreeOwners(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val voiceState by voiceSessionController.state.collectAsState()
                transparentAssistantSessionContent(
                    visualState = visualState.value,
                    voiceState = voiceState,
                    actions =
                        object : VoiceSessionActions {
                            override fun startListening() = startListeningIfAllowed()

                            override fun requestMicrophonePermission() {
                                startVoiceActivity(
                                    Intent(ACTION_REQUEST_MICROPHONE_PERMISSION).setClassName(
                                        context,
                                        MAIN_ACTIVITY_CLASS_NAME,
                                    ),
                                )
                            }

                            override fun cancelVoice() = voiceSessionController.cancelActiveWork()

                            override fun speakTestResponse() = voiceSessionController.speakTestResponse()

                            override fun textChanged(text: String) = voiceSessionController.updateTextFallback(text)

                            override fun submitText() = voiceSessionController.submitTextFallback()
                        },
                )
            }
        }
    }

    override fun onPrepareShow(
        args: Bundle?,
        showFlags: Int,
    ) {
        super.onPrepareShow(args, showFlags)
        visualState.value = AssistantSessionStateReducer.reduce(AssistantSessionEvent.PREPARED)
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int,
    ) {
        super.onShow(args, showFlags)
        lifecycleOwner.show()
        visualState.value = AssistantSessionStateReducer.reduce(AssistantSessionEvent.SHOWN)
        startListeningIfAllowed()
    }

    override fun onAssistStructureFailure(failure: Throwable?) {
        super.onAssistStructureFailure(failure)
        visualState.value = AssistantSessionStateReducer.reduce(AssistantSessionEvent.RECOVERABLE_ERROR)
        Log.w(LOG_TAG, "Assistant context unavailable")
    }

    override fun onBackPressed() = closeSession()

    override fun onCloseSystemDialogs() = closeSession()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowController.reconfigure(getWindow())
    }

    override fun onDestroy() {
        if (::voiceSessionController.isInitialized) {
            voiceSessionController.close()
        }
        lifecycleOwner.destroy()
        windowController.release()
        super.onDestroy()
    }

    private fun closeSession() {
        if (isClosing) return

        isClosing = true
        if (::voiceSessionController.isInitialized) {
            voiceSessionController.close()
        }
        finish()
    }

    private fun startListeningIfAllowed() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceSessionController.startListening()
        } else {
            voiceSessionController.requireMicrophonePermission()
        }
    }

    private companion object {
        const val LOG_TAG = "AssistantSession"
        const val ACTION_REQUEST_MICROPHONE_PERMISSION =
            "fr.loevan.jeancalcul.action.REQUEST_MICROPHONE_PERMISSION"
        const val MAIN_ACTIVITY_CLASS_NAME = "fr.loevan.jeancalcul.app.MainActivity"
    }
}
