package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class SpeechInputState(
    val phase: EtaSpeechPhase = EtaSpeechPhase.IDLE,
    val preview: String = "",
    val level: Float = 0f,
    val progress: String = "",
    val error: String? = null,
    val configureAvailable: Boolean = false,
) {
    val active: Boolean get() = phase != EtaSpeechPhase.IDLE
}

/**
 * Saisie vocale commune à la conversation, à l’assistant (bouton marche) et au test des réglages.
 * - Android : le moteur du téléphone écoute, découpe et transcrit lui-même.
 * - OpenAI / vLLM : Jean-Calcul enregistre en PCM, détecte la fin de phrase localement puis envoie un WAV.
 */
internal class SpeechInputController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val automaticEndpoint: Boolean = false,
    private val beforeCapture: () -> Unit = {},
    private val afterCapture: () -> Unit = {},
    private val refiner: SpeechTranscriptRefiner = SpeechTranscriptRefiner(),
    private val createRecorder: () -> SpeechAudioCapture = ::SpeechRecorder,
    private val createDeviceRecognizer: () -> DeviceSpeechRecognition = { AndroidSpeechRecognition(context) },
    private val resolveSettings: suspend (SpeechSettings, SpeechCredentials) -> Pair<SpeechSettings, SpeechCredentials> =
        { settings, credentials -> SpeechSettingsRepository.resolveVllmFallback(settings, credentials) },
    private val recognizeFile: suspend (SpeechSettings, SpeechCredentials, ByteArray) -> String = ::recognizeSpeechFile,
    private val onResult: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(SpeechInputState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var finishRequested = false
    private var job: Job? = null
    private var recorder: SpeechAudioCapture? = null
    private var device: DeviceSpeechRecognition? = null
    private var lease: SpeechAudioLease? = null
    private var capturing = false

    /**
     * [holdToTalk] : l’utilisateur maintient le micro, seul le relâchement termine la saisie.
     * L’envoi automatique après un silence ne concerne que les entrées construites avec [automaticEndpoint]
     * (l’assistant) ; la dictée de conversation attend toujours « Terminé ».
     */
    fun start(settings: SpeechSettings? = null, credentials: SpeechCredentials? = null, holdToTalk: Boolean = false) {
        cancel()
        val session = generation
        mutableState.value = SpeechInputState(phase = EtaSpeechPhase.STARTING, progress = "Starting microphone")
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val rawConfig = settings ?: SpeechSettingsRepository.settings()
                if (!rawConfig.recognitionEnabled) return@launch
                lease = SpeechAudioLease(context) { cancel() }.also { it.acquire(playback = false) }
                val rawSecrets = credentials ?: if (rawConfig.asr == AsrProvider.ANDROID) {
                    SpeechCredentials()
                } else {
                    SpeechSettingsRepository.credentials(context)
                }
                val (config, secrets) = resolveSettings(rawConfig, rawSecrets)
                validateSpeechSettings(config, secrets, synthesis = false)
                val endpointSilenceMs = if (automaticEndpoint && !holdToTalk) config.autoSendSilenceMs else 0
                fun update(transform: (SpeechInputState) -> SpeechInputState) {
                    scope.launch(Dispatchers.Main.immediate) {
                        if (generation == session) mutableState.value = transform(mutableState.value)
                    }
                }
                val text = if (config.asr == AsrProvider.ANDROID) {
                    recognizeOnDevice(config, endpointSilenceMs, ::update)
                } else {
                    recognizeRecording(session, config, secrets, endpointSilenceMs, ::update)
                }
                if (text.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "No speech recognized. Try again.")
                val final = refined(session, config.refineTranscript, text)
                if (generation == session) {
                    cancel()
                    onResult(final)
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                if (generation == session) {
                    val failure = error.speechFailure()
                    AndroidAgentLogger.warn("Jean-Calcul speech recognition failed: code=${failure.code} type=${error.javaClass.simpleName}")
                    cancel()
                    mutableState.value = SpeechInputState(
                        error = failure.userMessage,
                        configureAvailable = failure.code in CONFIGURABLE_FAILURES,
                    )
                }
            } finally {
                if (generation == session) cancel()
            }
        }
    }

    private suspend fun recognizeOnDevice(
        config: SpeechSettings,
        endpointSilenceMs: Int,
        update: ((SpeechInputState) -> SpeechInputState) -> Unit,
    ): String {
        val recognizer = createDeviceRecognizer()
        device = recognizer
        beforeCapture()
        capturing = true
        try {
            return withTimeout(DEVICE_TIMEOUT_MS) {
                recognizer.recognize(
                    language = config.language,
                    endpointSilenceMs = endpointSilenceMs,
                    onReady = { update { it.copy(phase = EtaSpeechPhase.LISTENING, progress = "Listening…") } },
                    onLevel = { level -> update { it.copy(level = level) } },
                    onPreview = { preview -> update { it.copy(preview = preview) } },
                )
            }
        } finally {
            device = null
            releaseCapture()
        }
    }

    private suspend fun recognizeRecording(
        session: Long,
        config: SpeechSettings,
        secrets: SpeechCredentials,
        endpointSilenceMs: Int,
        update: ((SpeechInputState) -> SpeechInputState) -> Unit,
    ): String {
        val audio = createRecorder()
        recorder = audio
        val endpoint = SpeechEndpointDetector(endpointSilenceMs)
        val wav = coroutineScope {
            beforeCapture()
            capturing = true
            val recording = launch {
                try {
                    audio.record(
                        onReady = { update { it.copy(phase = EtaSpeechPhase.LISTENING, progress = "Listening…") } },
                        onLevel = { level ->
                            update { it.copy(level = level) }
                            when (endpoint.accept(level)) {
                                SpeechEndpointDetector.Decision.CONTINUE -> Unit
                                SpeechEndpointDetector.Decision.END,
                                SpeechEndpointDetector.Decision.NO_SPEECH,
                                -> audio.finish()
                            }
                        },
                    )
                } finally {
                    if (generation == session) releaseCapture()
                }
                update { it.copy(phase = EtaSpeechPhase.RECOGNIZING, level = 0f, progress = "Recognizing speech") }
            }
            try {
                withTimeout(RECORDING_TIMEOUT_MS) { collectSpeechAudio(audio.frames) }
            } finally {
                audio.cancel()
                recording.cancel()
            }
        }
        if (endpoint.silent) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "No speech heard. Try again.")
        // Fermer le canal peut réveiller la collecte : une session remplacée ne doit pas envoyer sa requête.
        kotlin.coroutines.coroutineContext.ensureActive()
        if (generation != session) throw CancellationException("Speech session replaced")
        update { it.copy(phase = EtaSpeechPhase.RECOGNIZING, level = 0f, progress = "Recognizing speech") }
        return recognizeFile(config, secrets, wav)
    }

    /** Un échec de correction n’empêche pas la livraison ; la progression n’est montrée que pour la session courante. */
    private suspend fun refined(session: Long, refineTranscript: Boolean, text: String): String {
        if (!refineTranscript || generation != session) return text
        mutableState.value = mutableState.value.copy(
            phase = EtaSpeechPhase.RECOGNIZING, preview = text, progress = "Proofreading", level = 0f,
        )
        return try {
            refiner.refine(text)
        } catch (error: Exception) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            val failure = error.speechFailure()
            AndroidAgentLogger.warn("Jean-Calcul speech refine skipped: code=${failure.code} type=${error.javaClass.simpleName}")
            text
        }
    }

    fun finish() {
        if (!mutableState.value.active || finishRequested) return
        finishRequested = true
        if (mutableState.value.phase == EtaSpeechPhase.STARTING) { cancel(); return }
        recorder?.finish()
        device?.finish()
        mutableState.value = mutableState.value.copy(phase = EtaSpeechPhase.RECOGNIZING, progress = "Recognizing speech", level = 0f)
    }

    fun cancel() {
        generation++
        finishRequested = false
        // Annuler d’abord la coroutine : la fermeture du canal ne doit pas passer pour une fin d’enregistrement.
        job?.cancel(); job = null
        recorder?.cancel(); recorder = null
        device = null
        releaseCapture()
        lease?.close(); lease = null
        mutableState.value = SpeechInputState()
    }

    private fun releaseCapture() {
        if (capturing) afterCapture()
        capturing = false
    }

    private companion object {
        const val DEVICE_TIMEOUT_MS = 90_000L
        const val RECORDING_TIMEOUT_MS = 65_000L
        val CONFIGURABLE_FAILURES = setOf(
            SpeechErrorCode.CONFIGURATION,
            SpeechErrorCode.CREDENTIALS,
            SpeechErrorCode.AUTHENTICATION,
            SpeechErrorCode.UNAVAILABLE,
        )
    }
}

/**
 * Détection locale de fin de parole pour les moteurs serveur, à partir du niveau RMS de chaque trame (~200 ms).
 * Le seuil suit le plancher de bruit (minimum des ~5 dernières secondes), borné pour qu’une parole continue
 * ne devienne jamais « bruit » ; il faut au moins [MIN_VOICED_FRAMES] trames parlées avant de couper.
 * [silenceMs] = 0 désactive la coupure (dictée, maintien du bouton).
 */
internal class SpeechEndpointDetector(
    private val silenceMs: Int,
    private val frameMs: Int = SpeechRecorder.FRAME_MS,
) {
    enum class Decision { CONTINUE, END, NO_SPEECH }

    private val history = ArrayDeque<Float>()
    private var frames = 0
    private var voicedFrames = 0
    private var silentRun = 0

    /** Vrai si l’enregistrement s’est arrêté faute de parole. */
    @Volatile
    var silent = false
        private set

    fun accept(level: Float): Decision {
        if (silenceMs <= 0) return Decision.CONTINUE
        frames++
        val floor = history.minOrNull() ?: 0f
        val threshold = (floor * NOISE_RATIO).coerceIn(MIN_VOICE_LEVEL, MAX_VOICE_LEVEL)
        history.addLast(level)
        if (history.size > NOISE_WINDOW_FRAMES) history.removeFirst()
        val voiced = level >= threshold
        if (voiced) {
            voicedFrames++
            silentRun = 0
        } else {
            silentRun++
        }
        if (voicedFrames >= MIN_VOICED_FRAMES && silentRun * frameMs >= silenceMs) return Decision.END
        if (voicedFrames < MIN_VOICED_FRAMES && frames * frameMs >= NO_SPEECH_MS) {
            silent = true
            return Decision.NO_SPEECH
        }
        return Decision.CONTINUE
    }

    private companion object {
        const val MIN_VOICE_LEVEL = 0.06f
        const val NOISE_RATIO = 2.5f
        const val MAX_VOICE_LEVEL = 0.2f
        const val NOISE_WINDOW_FRAMES = 25
        const val MIN_VOICED_FRAMES = 2
        const val NO_SPEECH_MS = 8_000
    }
}
