package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoHabit
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoOccasion
import io.github.mangi.eta.ui.screens.home.brief.briefProgress
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.ChipDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SuggestionChip
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ContactsBook
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefHabitsCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    counts: Map<String, Int>,
    onCountChange: (String, Int) -> Unit,
    onDemoAction: () -> Unit,
) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val completeToday = demo.habits.count { (counts[it.title] ?: it.completed) >= it.target }
    BriefCard(BriefCardId.HABITS, expanded, onToggleDensity, motionAllowed, modifier, "${demo.habitStreak} jours de suite", preview = true) {
        HabitWeek(completeToday)
        Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 5.dp), horizontalArrangement = Arrangement.End) {
            Text("Aujourd’hui · $completeToday / ${demo.habits.size}", color = colors.labelSecondary, style = styles.bodyXS)
        }
        val habits = if (expanded) demo.habits else demo.habits.take(3)
        habits.forEachIndexed { index, habit ->
            if (index > 0) BriefRowDivider()
            val count = counts[habit.title] ?: habit.completed
            HabitRow(habit, index, count, motionAllowed) { onCountChange(habit.title, it) }
        }
        if (expanded) {
            BriefSection("Objectifs de la semaine")
            demo.goals.forEach { goal ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(goal.title, Modifier.weight(1f), color = colors.labelPrimary, style = styles.bodyS)
                    Text("${goal.completed} / ${goal.target}", color = colors.labelSecondary, style = styles.bodyXS)
                    BriefGauge(
                        briefProgress(goal.completed, goal.target), listOf(goal.tone.color()),
                        motionAllowed, Modifier.width(70.dp).height(6.dp), goal.title,
                    )
                }
            }
            BriefSection("À venir")
        }
        OccasionRow(demo.birthday, birthday = true, onAction = onDemoAction)
        if (expanded) {
            demo.occasions.forEach { OccasionRow(it) }
            BriefSection("Petites réussites")
            demo.achievements.forEachIndexed { index, achievement ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (index == 0) Icons.Rounded.EmojiEvents else Icons.Rounded.LocalFireDepartment,
                        null, Modifier.size(19.dp), tint = briefTintedLabel(colors.orange),
                    )
                    Text(achievement, color = colors.labelPrimary, style = styles.bodyS)
                }
            }
        }
    }
}

@Composable
private fun HabitWeek(completeToday: Int) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        demo.weekDays.forEachIndexed { index, day ->
            val today = index == demo.today
            val done = if (today) completeToday else demo.habitWeek.getOrNull(index) ?: 0
            val achieved = done >= demo.habitsPerDay
            val status = when {
                index > demo.today -> "À venir"
                achieved -> "Objectif atteint"
                done > 0 -> "Partiellement terminé"
                else -> "À faire"
            }
            val fill = when {
                achieved -> colors.purple
                done > 0 -> colors.purple.copy(alpha = .28f)
                else -> colors.labelPrimary.copy(alpha = .08f)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (today) "$day, aujourd’hui, $status" else "$day, $status"
                },
            ) {
                Box(
                    Modifier.size(30.dp).background(fill, CircleShape)
                        .then(if (today) Modifier.border(2.dp, colors.purple, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done > 0) {
                        val icon = if (achieved) Icons.Rounded.Check else COUIIcons.More
                        Icon(icon, null, Modifier.size(16.dp), tint = if (achieved) colors.onPrimary else briefTintedLabel(colors.purple))
                    }
                }
                Text(day.first().toString(), color = if (today) colors.labelPrimary else colors.labelSecondary, style = styles.bodyXS)
            }
        }
    }
}

@Composable
private fun HabitRow(habit: BriefDemoHabit, index: Int, count: Int, motionAllowed: Boolean, onCountChange: (Int) -> Unit) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val checked = count >= habit.target
    val icon = when (index) {
        0 -> Icons.Rounded.Opacity
        1 -> COUIIcons.ContactsBook
        2 -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.LocalFireDepartment
    }
    val label: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BriefIconTile(icon, colors.purple, 34.dp)
            Column {
                Text(habit.title, color = colors.labelPrimary, style = styles.headlineXS)
                Text(
                    if (habit.target > 1) "$count / ${habit.target} verres" else if (checked) "Fait" else "À faire",
                    color = colors.labelSecondary, style = styles.bodyXS,
                )
            }
        }
    }
    if (habit.target == 1) {
        BriefCheckRow(checked, { onCountChange(if (it) 1 else 0) }, content = label)
    } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { label() }
            BriefGauge(
                briefProgress(count, habit.target), listOf(colors.purple), motionAllowed,
                Modifier.width(55.dp).height(6.dp), habit.title,
            )
            CouiTopAppBarIconButton(
                COUIIcons.Add, "Ajouter un verre d’eau", { onCountChange(count + 1) },
                enabled = !checked, tint = briefTintedLabel(colors.purple),
            )
        }
    }
}

@Composable
private fun OccasionRow(occasion: BriefDemoOccasion, birthday: Boolean = false, onAction: (() -> Unit)? = null) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).then(
            if (birthday) Modifier.squircleSurface(colors.red.copy(alpha = .14f), cornerRadius = 18.dp)
                .padding(horizontal = 9.dp, vertical = 8.dp)
            else Modifier.padding(vertical = 2.dp),
        ),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BriefDateTile(occasion.month, occasion.day, if (birthday) colors.red else colors.purple)
        Column(Modifier.weight(1f)) {
            Text(occasion.title, color = colors.labelPrimary, style = styles.headlineXS)
            Text(occasion.detail, color = colors.labelSecondary, style = styles.bodyXS)
        }
        if (onAction != null) {
            SuggestionChip(
                label = "Écrire",
                onClick = onAction,
                minHeight = 48.dp,
                colors = ChipDefaults.chipColors(
                    containerColor = colors.red.copy(alpha = .15f),
                    contentColor = briefTintedLabel(colors.red),
                ),
                icon = { Icon(COUIIcons.Send, null, Modifier.size(15.dp)) },
            )
        }
    }
}
