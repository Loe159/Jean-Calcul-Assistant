@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "TooManyFunctions",
    "ktlint:standard:function-naming",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
 * Layered assistant focal point. The host owns microphone state and may only pass [Listening]
 * after Android has actually started capture. No layer relies on a backdrop blur.
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
    val animate = motionMode == OrbMotionMode.Animated && effects.animate
    val phase = if (animate) animatedOrbPhase() else progress.coerceIn(0f, 1f)
    val useGradient =
        motionMode != OrbMotionMode.NoShader &&
            motionMode != OrbMotionMode.Reduced &&
            effects.useGradients
    val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
    val visual = orbVisual(state)
    val blue = MaterialTheme.colorScheme.secondary
    val cyan = Color(0xFFC5F1FF)
    val deepBlue = JeanCalculDesign.tokens.materials.ambientBlue
    val violet = JeanCalculDesign.tokens.materials.ambientViolet

    Box(
        modifier = modifier.size(orbSize).semantics { contentDescription = orbDescription(state) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val half = min(size.width, size.height) / 2f
            val breathing = 0.97f + sin(phase * TWO_PI).toFloat() * visual.breathe
            val amplitudeScale = 1f + normalizedAmplitude * visual.amplitudeResponse
            val coreRadius = half * visual.coreScale * breathing * amplitudeScale
            val shellRadius = coreRadius * 1.28f
            val haloRadius = half * visual.haloScale * breathing
            val organicOffset =
                Offset(
                    x = cos(phase * TWO_PI).toFloat() * half * visual.drift,
                    y = sin(phase * TWO_PI).toFloat() * half * visual.drift * 0.62f,
                )
            val orbCenter = center + organicOffset

            if (useGradient) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    visual.halo.copy(alpha = visual.haloAlpha),
                                    violet.copy(alpha = visual.violetHaloAlpha),
                                    Color.Transparent,
                                ),
                            center = orbCenter + Offset(half * 0.12f, half * 0.09f),
                            radius = haloRadius,
                        ),
                    radius = haloRadius,
                    center = orbCenter,
                )
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(blue.copy(alpha = visual.blueHaloAlpha), Color.Transparent),
                            center = orbCenter - Offset(half * 0.18f, half * 0.08f),
                            radius = haloRadius * 0.76f,
                        ),
                    radius = haloRadius * 0.76f,
                    center = orbCenter - Offset(half * 0.14f, 0f),
                )
            } else {
                drawCircle(
                    color = visual.halo.copy(alpha = visual.haloAlpha * 0.52f),
                    radius = haloRadius * 0.72f,
                    center = orbCenter,
                )
            }

            drawCircle(
                color = visual.ring.copy(alpha = visual.ringAlpha),
                radius = shellRadius * (1.04f + normalizedAmplitude * 0.05f),
                center = orbCenter,
                style = Stroke(width = maxOf(1f, half * 0.012f)),
            )
            drawCircle(
                color = visual.ring.copy(alpha = visual.ringAlpha * 0.46f),
                radius = shellRadius * 1.17f,
                center = orbCenter + Offset(half * 0.035f, -half * 0.018f),
                style = Stroke(width = maxOf(1f, half * 0.008f)),
            )

            if (useGradient) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    blue.copy(alpha = 0.50f * visual.energy),
                                    deepBlue.copy(alpha = 0.42f * visual.energy),
                                    Color.Transparent,
                                ),
                            center = orbCenter - Offset(coreRadius * 0.18f, coreRadius * 0.22f),
                            radius = shellRadius,
                        ),
                    radius = shellRadius,
                    center = orbCenter,
                )
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to cyan.copy(alpha = visual.highlightAlpha),
                                    0.22f to visual.coreLight,
                                    0.68f to visual.core,
                                    1.0f to visual.coreDeep,
                                ),
                            center = orbCenter - Offset(coreRadius * 0.30f, coreRadius * 0.34f),
                            radius = coreRadius * 1.65f,
                        ),
                    radius = coreRadius,
                    center = orbCenter,
                )
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.28f * visual.energy), Color.Transparent),
                            center = orbCenter - Offset(coreRadius * 0.30f, coreRadius * 0.36f),
                            radius = coreRadius * 0.48f,
                        ),
                    radius = coreRadius * 0.48f,
                    center = orbCenter - Offset(coreRadius * 0.25f, coreRadius * 0.28f),
                )
            } else {
                drawCircle(color = visual.coreDeep, radius = shellRadius, center = orbCenter)
                drawCircle(color = visual.core, radius = coreRadius, center = orbCenter)
                drawCircle(
                    color = visual.coreLight.copy(alpha = 0.74f),
                    radius = coreRadius * 0.42f,
                    center = orbCenter - Offset(coreRadius * 0.25f, coreRadius * 0.28f),
                )
            }

            if (state == GradientOrbState.Listening) {
                drawMicrophone(center = orbCenter, radius = coreRadius, color = Color.White.copy(alpha = 0.90f))
            }
        }
        content()
    }
}

@Composable
private fun animatedOrbPhase(): Float {
    val transition = rememberInfiniteTransition(label = "orb-breathe")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = JeanCalculDesign.tokens.motion.breatheMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "orb-phase",
        )
    return phase
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
    val animated = effects.animate && state in setOf(VoiceWaveState.Listening, VoiceWaveState.Speaking)
    val phase = if (animated) animatedWavePhase() else progress.coerceIn(0f, 1f)
    val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
    val palette = wavePalette(state)

    Canvas(modifier = modifier.semantics { contentDescription = waveDescription(state) }) {
        val count = barCount.coerceIn(3, 9)
        val compact = size.height <= 36.dp.toPx()
        val gap = if (compact) 3.dp.toPx() else 5.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
        val activity = waveActivity(state, normalizedAmplitude)

        repeat(count) { index ->
            val weight = WAVE_WEIGHTS[index % WAVE_WEIGHTS.size]
            val dephased =
                ((sin((phase * TWO_PI) + index * 0.93f) + 1f) / 2f) *
                    ((cos((phase * TWO_PI * 0.62f) - index * 0.57f) + 1f) / 2f)
            val motion = if (animated) dephased else deterministicWave(index, phase)
            val stateMotion = if (state == VoiceWaveState.Silence) 0.08f else motion
            val heightFraction =
                (palette.minimum + activity * (weight * 0.48f + stateMotion * 0.42f))
                    .coerceIn(palette.minimum, 1f)
            val height = (size.height * heightFraction).coerceAtLeast(4.dp.toPx())
            val left = index * (barWidth + gap)
            val color = if (index % 2 == 0) palette.primary else palette.secondary
            drawRoundRect(
                color = color.copy(alpha = palette.alpha),
                topLeft = Offset(left, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun animatedWavePhase(): Float {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(JeanCalculDesign.tokens.motion.pulseMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "wave-phase",
        )
    return phase
}

/** Broad, low-opacity activity light. It is intentionally absent in reduced/no-shader modes. */
@Composable
fun AmbientGlow(
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val effects = JeanCalculDesign.effects.resolved()
    if (!effects.useGradients) return
    val violet = JeanCalculDesign.tokens.materials.ambientViolet
    val blue = JeanCalculDesign.tokens.materials.ambientBlue
    Canvas(modifier) {
        val radius = maxOf(size.width, size.height) * 0.82f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(blue.copy(alpha = if (active) 0.13f else 0.08f), Color.Transparent),
                    center = center - Offset(size.width * 0.12f, size.height * 0.05f),
                    radius = radius,
                ),
            radius = radius,
            center = center - Offset(size.width * 0.10f, 0f),
        )
        if (active) {
            drawCircle(
                brush = Brush.radialGradient(listOf(violet.copy(alpha = 0.16f), Color.Transparent)),
                radius = radius * 0.88f,
                center = center + Offset(size.width * 0.13f, size.height * 0.08f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMicrophone(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val bodyWidth = radius * 0.24f
    val bodyHeight = radius * 0.45f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - bodyWidth / 2f, center.y - bodyHeight * 0.62f),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyWidth / 2f, bodyWidth / 2f),
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - bodyWidth * 0.82f, center.y - bodyHeight * 0.28f),
        size = Size(bodyWidth * 1.64f, bodyHeight * 0.72f),
        style = Stroke(width = maxOf(1f, radius * 0.055f), cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y + bodyHeight * 0.36f),
        end = Offset(center.x, center.y + bodyHeight * 0.56f),
        strokeWidth = maxOf(1f, radius * 0.055f),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(center.x - bodyWidth * 0.52f, center.y + bodyHeight * 0.56f),
        end = Offset(center.x + bodyWidth * 0.52f, center.y + bodyHeight * 0.56f),
        strokeWidth = maxOf(1f, radius * 0.055f),
        cap = StrokeCap.Round,
    )
}

private fun orbVisual(state: GradientOrbState): OrbVisual {
    val blue = Color(0xFF58A6FF)
    val blueLight = Color(0xFFA4C9FF)
    val deepBlue = Color(0xFF075C9D)
    val violet = Color(0xFF8A5BF5)
    val success = Color(0xFF8FD8A6)
    val error = Color(0xFFFF8D84)
    val offline = Color(0xFF8A9499)
    val base = OrbVisual(core = blue, coreLight = blueLight, coreDeep = deepBlue, halo = blue, ring = blueLight)
    return when (state) {
        GradientOrbState.Idle -> base.copy(energy = 0.72f, coreScale = 0.34f, haloAlpha = 0.08f, ringAlpha = 0.10f)
        GradientOrbState.Invoked -> base.copy(energy = 0.86f, coreScale = 0.36f, haloAlpha = 0.13f, ringAlpha = 0.18f)
        GradientOrbState.Listening ->
            base.copy(
                energy = 1f,
                coreScale = 0.38f,
                haloAlpha = 0.16f,
                blueHaloAlpha = 0.24f,
                ringAlpha = 0.32f,
                amplitudeResponse = 0.20f,
                breathe = 0.045f,
            )

        GradientOrbState.Transcribing ->
            base.copy(coreScale = 0.36f, haloAlpha = 0.12f, ringAlpha = 0.28f, drift = 0.018f)

        GradientOrbState.Thinking ->
            base.copy(
                coreScale = 0.35f,
                halo = violet,
                haloAlpha = 0.17f,
                violetHaloAlpha = 0.20f,
                ring = violet,
                ringAlpha = 0.34f,
                drift = 0.028f,
            )

        GradientOrbState.ProposingAction ->
            base.copy(
                halo = violet,
                haloAlpha = 0.16f,
                violetHaloAlpha = 0.24f,
                ring = violet,
                ringAlpha = 0.42f,
                coreScale = 0.36f,
            )

        GradientOrbState.WaitingApproval ->
            base.copy(
                energy = 0.90f,
                halo = violet,
                haloAlpha = 0.14f,
                violetHaloAlpha = 0.22f,
                ring = violet,
                ringAlpha = 0.52f,
                coreScale = 0.35f,
                breathe = 0.01f,
            )

        GradientOrbState.Executing ->
            base.copy(
                energy = 1f,
                coreScale = 0.38f,
                haloAlpha = 0.17f,
                blueHaloAlpha = 0.26f,
                ringAlpha = 0.44f,
                drift = 0.035f,
            )

        GradientOrbState.Speaking ->
            base.copy(
                coreScale = 0.39f,
                haloAlpha = 0.18f,
                ringAlpha = 0.30f,
                amplitudeResponse = 0.24f,
                breathe = 0.05f,
            )

        GradientOrbState.Completed ->
            base.copy(energy = 0.84f, coreScale = 0.34f, halo = success, ring = success, ringAlpha = 0.42f)

        GradientOrbState.Cancelled ->
            base.copy(energy = 0.54f, coreScale = 0.30f, haloAlpha = 0.04f, ringAlpha = 0.08f, breathe = 0f)

        GradientOrbState.Error ->
            base.copy(
                energy = 0.76f,
                core = Color(0xFF3489D0),
                coreDeep = Color(0xFF183E5D),
                halo = error,
                ring = error,
                haloAlpha = 0.13f,
                ringAlpha = 0.48f,
                coreScale = 0.34f,
            )

        GradientOrbState.Offline ->
            base.copy(
                energy = 0.48f,
                core = offline,
                coreLight = Color(0xFFB7C2C7),
                coreDeep = Color(0xFF4B565B),
                halo = offline,
                ring = offline,
                haloAlpha = 0.05f,
                ringAlpha = 0.18f,
                coreScale = 0.32f,
                breathe = 0f,
            )
    }
}

private fun wavePalette(state: VoiceWaveState): WavePalette {
    val blue = Color(0xFFA4C9FF)
    val cyan = Color(0xFFC5F1FF)
    val violet = Color(0xFFD0BCFF)
    return when (state) {
        VoiceWaveState.Listening -> WavePalette(blue, cyan, minimum = 0.16f)
        VoiceWaveState.Speaking -> WavePalette(cyan, blue, minimum = 0.20f)
        VoiceWaveState.Bluetooth -> WavePalette(violet, blue, minimum = 0.18f)
        VoiceWaveState.MicrophoneUnavailable ->
            WavePalette(Color(0xFFFFB4AB), Color(0xFFFF8D84), minimum = 0.10f, alpha = 0.82f)

        VoiceWaveState.Silence -> WavePalette(blue, violet, minimum = 0.10f, alpha = 0.44f)
        VoiceWaveState.Waiting -> WavePalette(blue, violet, minimum = 0.12f, alpha = 0.58f)
        VoiceWaveState.Static -> WavePalette(blue, violet, minimum = 0.15f, alpha = 0.82f)
    }
}

private fun waveActivity(
    state: VoiceWaveState,
    amplitude: Float,
): Float =
    when (state) {
        VoiceWaveState.Listening -> maxOf(0.30f, amplitude)
        VoiceWaveState.Speaking -> maxOf(0.48f, amplitude)
        VoiceWaveState.Bluetooth -> maxOf(0.34f, amplitude)
        VoiceWaveState.Static -> maxOf(0.42f, amplitude)
        VoiceWaveState.Waiting -> maxOf(0.18f, amplitude * 0.44f)
        VoiceWaveState.Silence -> 0.10f
        VoiceWaveState.MicrophoneUnavailable -> 0.14f
    }

private fun deterministicWave(
    index: Int,
    progress: Float,
): Float =
    ((sin(progress * TWO_PI + index * 1.31f) + 1f) / 2f) * 0.72f +
        WAVE_WEIGHTS[index % WAVE_WEIGHTS.size] * 0.28f

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

private data class OrbVisual(
    val core: Color,
    val coreLight: Color,
    val coreDeep: Color,
    val halo: Color,
    val ring: Color,
    val energy: Float = 0.92f,
    val coreScale: Float = 0.36f,
    val haloScale: Float = 0.94f,
    val haloAlpha: Float = 0.12f,
    val violetHaloAlpha: Float = 0.06f,
    val blueHaloAlpha: Float = 0.16f,
    val ringAlpha: Float = 0.22f,
    val highlightAlpha: Float = 0.90f,
    val breathe: Float = 0.025f,
    val drift: Float = 0.012f,
    val amplitudeResponse: Float = 0.12f,
)

private data class WavePalette(
    val primary: Color,
    val secondary: Color,
    val minimum: Float,
    val alpha: Float = 1f,
)

private const val TWO_PI = (PI * 2.0).toFloat()
private val WAVE_WEIGHTS = floatArrayOf(0.44f, 0.84f, 0.58f, 1f, 0.66f, 0.91f, 0.52f, 0.76f, 0.47f)
