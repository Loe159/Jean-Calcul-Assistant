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
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
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
    val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.FRENCH) }
    var entries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(20) }

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
    LaunchedEffect(Unit) { refresh() }

    CouiScaffoldPage(title = "Archives", onBack = onBack, compactTitle = true) {
        Footer(text = "Copies retirées de Mon briefing, conservées jusqu’à 7 jours sur cet appareil. " +
            "Les actions ici ne modifient pas les notifications Android.")
        if (error) Footer(text = "Archives indisponibles. Réessayez ou revenez plus tard.")
        Group(title = "Archives du briefing") {
            when {
                loading -> CouiPreference(title = "Chargement des archives…", showChevron = false)
                entries.isEmpty() -> CouiPreference(
                    title = if (error) "Réessayer" else "Aucune copie archivée",
                    onClick = if (error) ::refresh else null,
                )
                else -> entries.take(visibleCount).forEach { entry ->
                    val appName = remember(entry.packageName) {
                        runCatching {
                            val pm = context.packageManager
                            pm.getApplicationLabel(pm.getApplicationInfo(entry.packageName, 0)).toString()
                        }.getOrDefault(entry.packageName.substringAfterLast('.'))
                    }
                    val date = remember(entry.postedAt) {
                        dateFormat.format(Instant.ofEpochMilli(entry.postedAt)
                            .atZone(ZoneId.systemDefault()))
                    }
                    CouiPreference(
                        title = entry.title.ifBlank { appName },
                        summary = "$appName · $date",
                        onClick = { expandedKey = if (expandedKey == entry.key) null else entry.key },
                    )
                    if (expandedKey == entry.key) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            if (entry.text.isNotBlank()) {
                                CouiText(text = entry.text, kind = CouiTextKind.ItemSummary,
                                    color = colors.summary)
                            }
                            CouiButton(
                                text = "Restaurer dans Mon briefing",
                                enabled = !restoring,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                onClick = {
                                    if (!restoring) {
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
                                },
                            )
                        }
                    }
                }
            }
            if (entries.size > visibleCount) {
                CouiButton(
                    text = "Afficher les archives suivantes",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    onClick = { visibleCount += 20 },
                )
            }
        }
        CouiPageBottomSpacer()
    }
}
