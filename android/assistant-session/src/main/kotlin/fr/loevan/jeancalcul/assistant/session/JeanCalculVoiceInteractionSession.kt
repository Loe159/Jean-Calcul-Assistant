package fr.loevan.jeancalcul.assistant.session

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView

/**
 * Full-screen voice session that preserves sight of the underlying activity while it owns input.
 */
class JeanCalculVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {
    private val visualState = mutableStateOf(AssistantSessionVisualState.INVOKED)
    private val windowController = SessionWindowController(::closeSession)
    private var isClosing = false

    override fun onCreate() {
        super.onCreate()
        windowController.prepare(getWindow())
    }

    override fun onCreateContentView(): View =
        ComposeView(context).apply {
            setContent {
                transparentAssistantSessionContent(
                    visualState = visualState.value,
                    onClose = ::closeSession,
                )
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
        visualState.value = AssistantSessionStateReducer.reduce(AssistantSessionEvent.SHOWN)
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
        windowController.release()
        super.onDestroy()
    }

    private fun closeSession() {
        if (isClosing) return

        isClosing = true
        finish()
    }

    private companion object {
        const val LOG_TAG = "AssistantSession"
    }
}
