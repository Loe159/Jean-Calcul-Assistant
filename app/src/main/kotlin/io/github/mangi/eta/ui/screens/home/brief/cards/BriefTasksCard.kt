package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoTask
import io.github.mangi.eta.ui.screens.home.brief.briefPercent
import io.github.mangi.eta.ui.screens.home.brief.briefProgress
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Merge
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefTasksCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    checked: Set<String>,
    onCheck: (String, Boolean) -> Unit,
    onDemoAction: () -> Unit,
) {
    val demo = BriefDemoData
    val (complete, open) = demo.tasks.partition { it.title in checked }
    val progress = briefProgress(complete.size, demo.tasks.size)
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val meta = when (open.size) {
        0 -> "Tout est fait"
        1 -> "1 tâche restante"
        else -> "${open.size} tâches restantes"
    }
    BriefCard(BriefCardId.TASKS, expanded, onToggleDensity, motionAllowed, modifier, meta, preview = true) {
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefRings(listOf(BriefRing(progress, colors.violet)), 56.dp, 6.dp, motionAllowed) {
                Text("${complete.size}/${demo.tasks.size}", color = colors.labelPrimary, style = styles.headlineXS)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BriefGauge(progress, listOf(colors.violet), motionAllowed, Modifier.fillMaxWidth().height(6.dp), "Tâches terminées")
                Text("${briefPercent(progress)} % de la journée", color = colors.labelSecondary, style = styles.bodyXS)
            }
        }
        if (open.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BriefIconTile(Icons.Rounded.Check, colors.green, 42.dp)
                Text("Journée terminée", Modifier.padding(top = 6.dp), color = colors.labelPrimary, style = styles.headlineS)
            }
        } else if (expanded) {
            val (priorities, other) = open.partition { it.priority }
            if (priorities.isNotEmpty()) {
                BriefSection("Prioritaires")
                TaskRows(priorities, false, onCheck)
            }
            if (other.isNotEmpty()) {
                BriefSection("Ensuite")
                TaskRows(other, false, onCheck)
            }
        } else {
            TaskRows(open.take(3), false, onCheck)
        }
        if (demo.garageCall !in checked && demo.tyreAppointment !in checked) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp)
                    .squircleSurface(colors.violet.copy(alpha = .14f), cornerRadius = 16.dp)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(COUIIcons.Merge, null, Modifier.size(19.dp), tint = briefTintedLabel(colors.violet))
                Text(
                    "Regroupe « ${demo.garageCall} » et « ${demo.tyreAppointment} » en une seule tâche.",
                    Modifier.weight(1f), color = colors.labelPrimary, style = styles.bodyXS,
                )
                BriefDemoButton("Regrouper", onDemoAction)
            }
        }
        if (expanded && complete.isNotEmpty()) {
            BriefSection("Terminées", complete.size.toString())
            TaskRows(complete, true, onCheck)
        }
    }
}

@Composable
private fun TaskRows(tasks: List<BriefDemoTask>, checked: Boolean, onCheck: (String, Boolean) -> Unit) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    tasks.forEachIndexed { index, task ->
        if (index > 0) BriefRowDivider()
        BriefCheckRow(checked, { onCheck(task.title, it) }, checkFirst = true) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title, color = if (checked) colors.labelTertiary else colors.labelPrimary, style = styles.headlineXS,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority && !checked) Box(Modifier.size(6.dp).background(colors.orange, CircleShape))
                    Text(
                        if (checked) "Terminée" else task.due,
                        color = colors.labelSecondary, style = styles.bodyXS,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BriefRowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(COUITheme.colorScheme.labelPrimary.copy(alpha = .08f)))
}
