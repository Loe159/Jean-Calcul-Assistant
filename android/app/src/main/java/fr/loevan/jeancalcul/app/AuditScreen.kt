@file:Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditOutcome
import fr.loevan.jeancalcul.ui.ContentState
import fr.loevan.jeancalcul.ui.ContentStateMessage
import fr.loevan.jeancalcul.ui.FilterChip
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AuditScreen(
    state: AuditUiState,
    actions: AuditScreenActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Journal d'audit", style = MaterialTheme.typography.headlineMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AuditTimeWindow.entries) { window ->
                FilterChip(
                    label = window.label(),
                    selected = state.timeWindow == window,
                    onClick = { actions.timeWindowChanged(window) },
                )
            }
        }
        OutlinedTextField(
            value = state.filter.toolName.orEmpty(),
            onValueChange = actions.toolNameChanged,
            label = { Text("Filtrer par outil exact") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    label = "Tous les résultats",
                    selected = state.filter.outcome == null,
                    onClick = { actions.outcomeChanged(null) },
                )
            }
            items(AuditOutcome.entries.filterNot { it == AuditOutcome.PENDING }) { outcome ->
                FilterChip(
                    label = outcome.label(),
                    selected = state.filter.outcome == outcome,
                    onClick = { actions.outcomeChanged(outcome) },
                )
            }
        }
        if (state.events.isEmpty()) {
            ContentStateMessage(
                state = ContentState.Empty,
                title = "Aucune tentative",
                message = "Les décisions et exécutions d'outils apparaîtront ici.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.events, key = AuditEvent::actionId) { event -> AuditEventCard(event) }
                if (state.canLoadMore) {
                    item {
                        JeanCalculButton(
                            label = "Charger plus",
                            modifier = Modifier.fillMaxWidth(),
                            variant = JeanCalculButtonVariant.Secondary,
                            onClick = actions.loadMore,
                        )
                    }
                }
            }
        }
        state.errorMessage?.let { ContentStateMessage(ContentState.Error, "Audit indisponible", it) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(7, 30, 90)) { days ->
                FilterChip(
                    label = "$days jours",
                    selected = state.retentionDays == days,
                    onClick = { actions.retentionChanged(days) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JeanCalculButton(
                label = "Exporter",
                modifier = Modifier.weight(1f),
                onClick = actions.export,
            )
            JeanCalculButton(
                label = "Purger",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Destructive,
                onClick = actions.purgeExpired,
            )
        }
    }
}

@Composable
private fun AuditEventCard(event: AuditEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${event.toolName} · ${event.outcome.label()}", style = MaterialTheme.typography.titleMedium)
            Text("v${event.toolVersion} · ${formatDate(event.occurredAtEpochMillis)}")
            Text(event.redactedArguments, style = MaterialTheme.typography.bodySmall)
            event.policy?.let { Text("${it.decision.name.lowercase()} — ${it.justification}") }
            event.execution?.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

internal data class AuditScreenActions(
    val timeWindowChanged: (AuditTimeWindow) -> Unit,
    val toolNameChanged: (String) -> Unit,
    val outcomeChanged: (AuditOutcome?) -> Unit,
    val retentionChanged: (Int) -> Unit,
    val loadMore: () -> Unit,
    val purgeExpired: () -> Unit,
    val export: () -> Unit,
)

private fun AuditTimeWindow.label(): String =
    when (this) {
        AuditTimeWindow.ALL -> "Tout"
        AuditTimeWindow.LAST_DAY -> "24 h"
        AuditTimeWindow.LAST_WEEK -> "7 jours"
    }

private fun AuditOutcome.label(): String =
    when (this) {
        AuditOutcome.PENDING -> "En cours"
        AuditOutcome.SUCCESS -> "Réussite"
        AuditOutcome.FAILURE -> "Échec"
        AuditOutcome.DENIED -> "Refus"
        AuditOutcome.CANCELLED -> "Annulation"
        AuditOutcome.EXPIRED -> "Expiration"
    }

private fun formatDate(epochMillis: Long): String = DateFormat.getDateTimeInstance().format(Date(epochMillis))
