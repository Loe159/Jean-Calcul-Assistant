package fr.loevan.jeancalcul.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AssistantRoleOnboarding(
    status: AssistantRoleStatus,
    onRequestRole: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Jean-Calcul Assistant", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = status.description(),
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
            if (status == AssistantRoleStatus.Available) {
                Button(onClick = onRequestRole) {
                    Text("Choisir comme assistant")
                }
            }
            Button(
                modifier = Modifier.padding(top = 12.dp),
                onClick = onOpenSystemSettings,
            ) {
                Text("Ouvrir les paramètres système")
            }
        }
    }
}

private fun AssistantRoleStatus.description(): String =
    when (this) {
        AssistantRoleStatus.Active -> "Cet appareil utilise déjà Jean-Calcul Assistant."
        AssistantRoleStatus.Available -> "Choisissez Jean-Calcul Assistant comme assistant numérique."
        AssistantRoleStatus.Unavailable ->
            "Ce système ne permet pas de demander le rôle automatiquement. Ouvrez les paramètres."
    }
