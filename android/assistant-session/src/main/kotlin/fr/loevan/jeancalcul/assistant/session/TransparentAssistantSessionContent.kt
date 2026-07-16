package fr.loevan.jeancalcul.assistant.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.ui.jeanCalculTheme

/**
 * Translucent fallback is always present, so the session remains legible when system blur is absent.
 */
@Composable
internal fun transparentAssistantSessionContent(
    visualState: AssistantSessionVisualState,
    onClose: () -> Unit,
) {
    jeanCalculTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(TransparentSessionScrim),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .size(width = 280.dp, height = 208.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = visualState.title(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = visualState.message(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onClose) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

private fun AssistantSessionVisualState.title(): String =
    when (this) {
        AssistantSessionVisualState.INVOKED -> "Assistant invoqué"
        AssistantSessionVisualState.LISTENING -> "À l’écoute"
        AssistantSessionVisualState.ERROR -> "Assistant indisponible"
    }

private fun AssistantSessionVisualState.message(): String =
    when (this) {
        AssistantSessionVisualState.INVOKED -> "Préparation de la session"
        AssistantSessionVisualState.LISTENING -> "La reconnaissance vocale sera disponible prochainement."
        AssistantSessionVisualState.ERROR -> "Le contexte de l’application n’a pas pu être chargé."
    }

private val TransparentSessionScrim = Color(0x33050B16)
