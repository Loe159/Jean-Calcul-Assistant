package fr.loevan.jeancalcul.assistant.session

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.PolicyAuditLogger
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.observability.AndroidPerformanceTrace
import fr.loevan.jeancalcul.observability.PerformanceTraceEvent
import fr.loevan.jeancalcul.toolbridge.AudioManagerVolumeController
import fr.loevan.jeancalcul.toolbridge.createVolumeToolRegistry
import fr.loevan.jeancalcul.toolbridge.volumeToolAvailabilityContext
import fr.loevan.jeancalcul.voice.AndroidVoicePipelineFactory
import fr.loevan.jeancalcul.voice.VoiceEngineSelection
import fr.loevan.jeancalcul.voice.VoicePipeline
import fr.loevan.jeancalcul.voice.VoicePipelineFactory

/**
 * Full-screen voice session that preserves sight of the underlying activity while it owns input.
 */
@Suppress("TooManyFunctions")
class JeanCalculVoiceInteractionSession(
    context: Context,
    private val voicePipelineFactory: VoicePipelineFactory = AndroidVoicePipelineFactory(context),
) : VoiceInteractionSession(context) {
    private val lifecycleOwner = SessionLifecycleOwner()
    private val windowController = SessionWindowController(::closeSession)
    private val performanceTrace = AndroidPerformanceTrace(context)
    private lateinit var voicePipeline: VoicePipeline
    private lateinit var voiceSessionController: VoiceSessionController
    private var isClosing = false

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.create()
        getWindow()?.window?.decorView?.installSessionViewTreeOwners(lifecycleOwner)
        windowController.prepare(getWindow())
        voicePipeline = voicePipelineFactory.create(VoiceEngineSelection())
        voiceSessionController =
            VoiceSessionController(
                speechToTextProvider = voicePipeline.speechToTextProvider,
                textToSpeechProvider = voicePipeline.textToSpeechProvider,
                amplitudeSource = voicePipeline.amplitudeSource,
                activityDetector = voicePipeline.activityDetector,
                audioFocusController = voicePipeline.audioFocusController,
                audioRouteSource = voicePipeline.audioRouteSource,
                voiceCommandProcessor =
                    VolumeCommandProcessor(
                        interpreter = DeterministicVolumeCommandInterpreter(),
                        toolRegistry =
                            createVolumeToolRegistry(
                                AudioManagerVolumeController(
                                    requireNotNull(context.getSystemService(AudioManager::class.java)),
                                ),
                            ),
                        availabilityContext = {
                            volumeToolAvailabilityContext(
                                isDeviceLocked =
                                    context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true,
                            )
                        },
                        policyEngine =
                            PolicyEngine(
                                PolicyAuditLogger { event ->
                                    Log.i(
                                        POLICY_AUDIT_TAG,
                                        "${event.stage}:${event.decision}:${event.reason}:" +
                                            "${event.toolName}:${event.toolVersion}:${event.actionId}",
                                    )
                                },
                            ),
                        performanceTrace = performanceTrace,
                    ),
                performanceTrace = performanceTrace,
                initialLocaleTag = context.resources.configuration.locales[0].toLanguageTag(),
            )
    }

    override fun onCreateContentView(): View {
        getWindow()?.window?.decorView?.installSessionViewTreeOwners(lifecycleOwner)
        return ComposeView(context).apply {
            markFirstFrame(this)
            installSessionViewTreeOwners(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val voiceState by voiceSessionController.state.collectAsState()
                transparentAssistantSessionContent(
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

                            override fun interruptVoice() = voiceSessionController.interruptActiveWork()

                            override fun confirmVoiceCommand() = voiceSessionController.confirmPendingCommand()

                            override fun rejectVoiceCommand() = voiceSessionController.cancelActiveWork()

                            override fun openSystemPanel() {
                                startVoiceActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                                voiceSessionController.cancelActiveWork()
                            }

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
        performanceTrace.startInvocation()
        performanceTrace.captureMemory("session_invocation")
        voiceSessionController.invoke()
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int,
    ) {
        super.onShow(args, showFlags)
        lifecycleOwner.show()
        startListeningIfAllowed()
    }

    override fun onAssistStructureFailure(failure: Throwable?) {
        super.onAssistStructureFailure(failure)
        voiceSessionController.reportRecoverableError("Le contexte Android de l'assistant est indisponible.")
        Log.w(LOG_TAG, "Assistant context unavailable")
    }

    override fun onBackPressed() = closeSession()

    override fun onCloseSystemDialogs() = closeSession()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        windowController.reconfigure(getWindow())
        voiceSessionController.updateLocale(newConfig.locales[0].toLanguageTag())
    }

    override fun onDestroy() {
        if (::voiceSessionController.isInitialized) {
            voiceSessionController.close()
        }
        lifecycleOwner.destroy()
        windowController.release()
        performanceTrace.captureMemory("session_destroy")
        performanceTrace.finishInvocation("session_destroy")
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

    private fun markFirstFrame(view: View) {
        view.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (view.viewTreeObserver.isAlive) {
                        view.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    performanceTrace.mark(PerformanceTraceEvent.FIRST_FRAME)
                    performanceTrace.captureMemory("session_first_frame")
                    return true
                }
            },
        )
    }

    private companion object {
        const val POLICY_AUDIT_TAG = "PolicyEngineAudit"
        const val LOG_TAG = "AssistantSession"
        const val ACTION_REQUEST_MICROPHONE_PERMISSION =
            "fr.loevan.jeancalcul.action.REQUEST_MICROPHONE_PERMISSION"
        const val MAIN_ACTIVITY_CLASS_NAME = "fr.loevan.jeancalcul.app.MainActivity"
    }
}
