package io.github.mangi.eta.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.mangi.eta.ui.components.coui.PreferenceSection
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.ExpandableGroupHeader
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ArchivedBriefNotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationHistoryRepository(context) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.FRENCH) }
    var entries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(20) }
    val appNames = remember { mutableMapOf<String, String>() }

    fun appName(packageName: String): String = appNames.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                entries = withContext(Dispatchers.IO) { repository.archived(limit = 1_000) }
                error = false
            } catch (_: Exception) {
                error = true
            } finally {
                loading = false
            }
        }
    }

    fun restore(entry: NotificationHistoryRepository.RecentEntry) {
        if (restoring) return
        restoring = true
        scope.launch {
            try {
                entries = withContext(Dispatchers.IO) {
                    repository.restoreToBrief(listOf(entry))
                    repository.feedback(entry.key, entry.postedAt, NotificationUserFeedback.RESTORED)
                    repository.archived(limit = 1_000)
                }
                expandedKey = null
                NotificationActivityFeed.notifyChanged()
                error = false
            } catch (_: Exception) {
                error = true
            } finally {
                restoring = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    CouiScaffoldPage(title = "Archives", onBack = onBack, compactTitle = true) {
        PreferenceFooter(text = "Copies retirées de Mon briefing, conservées jusqu’à 7 jours sur cet appareil. " +
            "Les actions ici ne modifient pas les notifications Android.")
        if (error) PreferenceFooter(text = "Archives indisponibles. Réessayez ou revenez plus tard.")
        PreferenceSection(title = "Archives du briefing") {
            CardListGroup {
                when {
                    loading -> item { BasicComponent(title = "Chargement des archives…") }
                    entries.isEmpty() -> item {
                        if (error) ArrowPreference(title = "Réessayer", onClick = ::refresh)
                        else BasicComponent(title = "Aucune copie archivée")
                    }
                    else -> entries.take(visibleCount).forEach { entry ->
                        val name = appName(entry.packageName)
                        val date = dateFormat.format(Instant.ofEpochMilli(entry.postedAt).atZone(ZoneId.systemDefault()))
                        val expanded = expandedKey == entry.key
                        item(key = entry.key) {
                            ExpandableGroupHeader(
                                title = entry.title.ifBlank { name },
                                expanded = expanded,
                                onExpandedChange = { open -> expandedKey = if (open) entry.key else null },
                                summary = "$name · $date",
                            )
                        }
                        if (expanded) item(key = "${entry.key}_details", showDivider = false) {
                            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                                if (entry.text.isNotBlank()) {
                                    Text(
                                        text = entry.text,
                                        style = COUITheme.textStyles.bodyS,
                                        color = COUITheme.colorScheme.labelSecondary,
                                    )
                                }
                                CouiButton(
                                    text = "Restaurer dans Mon briefing",
                                    enabled = !restoring,
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    onClick = { restore(entry) },
                                )
                            }
                        }
                    }
                }
                if (!loading && entries.size > visibleCount) item {
                    ArrowPreference(title = "Afficher les archives suivantes", onClick = { visibleCount += 20 })
                }
            }
        }
    }
}
