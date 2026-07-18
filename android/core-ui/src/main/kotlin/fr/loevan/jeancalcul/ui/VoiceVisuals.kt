@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "ktlint:standard:function-naming",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

enum class GradientOrbState {
    Idle,
    Invoked,
    Listening,
    Transcribing,
    Thinking,
    ProposingAction,
    WaitingApproval,
    Executing,
    Speaking,
    Completed,
    Cancelled,
    Error,
    Offline,
}

enum class OrbMotionMode {
    Animated,
    Reduced,
    Static,
    NoShader,
}

enum class VoiceWaveState {
    Waiting,
    Listening,
    Silence,
    Speaking,
    Bluetooth,
    MicrophoneUnavailable,
    Static,
}

/**
 * A presentational voice focal point. The host owns microphone state and only passes [Listening]
 * when Android has actually started capture.
 */
@Composable
fun GradientOrb(
    state: GradientOrbState,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    progress: Float = 0f,
    motionMode: OrbMotionMode = OrbMotionMode.Animated,
    orbSize: Dp = 144.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val effects = JeanCalculDesign.effects.resolved()
    val useAnimation = motionMode == OrbMotionMode.Animated && effects.animate
    val transition = rememberInfiniteTransition(label = "orb")
    val animatedPulse by
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(JeanCalculDesign.tokens.motion.breatheMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "orb-pulse",
        )
    val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val pulse = if (useAnimation) animatedPulse else 0.94f + normalizedProgress * 0.10f
    val useGradient = motionMode != OrbMotionMode.NoShader && effects.useGradients
    val semanticLabel = orbDescription(state)
    val primary = MaterialTheme.colorScheme.secondary
    val active = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val offline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier.size(orbSize).semantics { contentDescription = semanticLabel },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val base = min(size.width, size.height) / 2f
            val activityScale = 1f + normalizedAmplitude * 0.16f
            val radius = base * 0.42f * pulse * activityScale
            val haloRadius = base * 0.92f * pulse
            val coreColor =
                when (state) {
                    GradientOrbState.Error -> error
                    GradientOrbState.Offline -> offline
                    GradientOrbState.WaitingApproval,
                    GradientOrbState.ProposingAction,
                    -> active

                    else -> primary
                }
            if (useGradient) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(active.copy(alpha = 0.28f), Color.Transparent),
                            center = center,
                            radius = haloRadius,
                        ),
                    radius = haloRadius,
                    center = center,
                )
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(coreColor, active.copy(alpha = 0.60f), coreColor.copy(alpha = 0.35f)),
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                    center = center,
                )
            } else {
                drawCircle(color = coreColor.copy(alpha = 0.22f), radius = haloRadius * 0.72f)
                drawCircle(color = coreColor, radius = radius)
            }
        }
        content()
    }
}

@Composable
fun VoiceWave(
    state: VoiceWaveState,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    progress: Float = 0f,
    barCount: Int = 5,
) {
    val effects = JeanCalculDesign.effects.resolved()
    val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val movement by
        transition.animateFloat(
            initialValue = 0.20f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(JeanCalculDesign.tokens.motion.pulseMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "wave-movement",
        )
    val dynamicAmplitude =
        if (effects.animate && state in setOf(VoiceWaveState.Listening, VoiceWaveState.Speaking)) {
            maxOf(normalizedAmplitude, movement * 0.42f)
        } else {
            maxOf(normalizedAmplitude, normalizedProgress * 0.5f)
        }
    val color =
        when (state) {
            VoiceWaveState.MicrophoneUnavailable -> MaterialTheme.colorScheme.error
            VoiceWaveState.Bluetooth -> MaterialTheme.colorScheme.tertiary
            VoiceWaveState.Listening -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Canvas(
        modifier =
            modifier.semantics {
                contentDescription = waveDescription(state)
            },
    ) {
        val safeCount = barCount.coerceIn(3, 9)
        val gap = size.width * 0.06f
        val barWidth = (size.width - gap * (safeCount - 1)) / safeCount
        repeat(safeCount) { index ->
            val phase = (index + 1).toFloat() / (safeCount + 1)
            val heightScale = 0.28f + dynamicAmplitude * (0.42f + phase * 0.30f)
            val height = size.height * heightScale
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = color.copy(alpha = if (state == VoiceWaveState.Silence) 0.48f else 1f),
                topLeft = Offset(left, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
fun AmbientGlow(
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val effects = JeanCalculDesign.effects.resolved()
    if (!effects.useGradients) return
    val color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent)),
            radius = maxOf(size.width, size.height) * 0.7f,
            center = center,
        )
    }
}

private fun orbDescription(state: GradientOrbState): String =
    when (state) {
        GradientOrbState.Listening -> "Microphone actif, assistant en écoute"
        GradientOrbState.Transcribing -> "Transcription en cours"
        GradientOrbState.Thinking -> "Réflexion en cours"
        GradientOrbState.ProposingAction -> "Action proposée"
        GradientOrbState.WaitingApproval -> "Approbation requise"
        GradientOrbState.Executing -> "Action en cours d’exécution"
        GradientOrbState.Speaking -> "Réponse vocale en cours"
        GradientOrbState.Completed -> "Tâche terminée"
        GradientOrbState.Cancelled -> "Tâche annulée"
        GradientOrbState.Error -> "Erreur de l’assistant"
        GradientOrbState.Offline -> "Assistant hors connexion"
        GradientOrbState.Invoked -> "Assistant invoqué"
        GradientOrbState.Idle -> "Assistant en attente"
    }

private fun waveDescription(state: VoiceWaveState): String =
    when (state) {
        VoiceWaveState.Listening -> "Microphone actif"
        VoiceWaveState.Bluetooth -> "Microphone Bluetooth sélectionné"
        VoiceWaveState.MicrophoneUnavailable -> "Microphone indisponible"
        VoiceWaveState.Speaking -> "Lecture vocale en cours"
        VoiceWaveState.Silence -> "Silence détecté"
        VoiceWaveState.Waiting -> "Onde vocale en attente"
        VoiceWaveState.Static -> "Onde vocale statique"
    }
