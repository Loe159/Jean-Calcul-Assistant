package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoPosition
import io.github.mangi.eta.ui.screens.home.brief.BriefPortfolioPeriod
import io.github.mangi.eta.ui.screens.home.brief.BriefValueChange
import io.github.mangi.eta.ui.screens.home.brief.briefBudgetPace
import io.github.mangi.eta.ui.screens.home.brief.briefDecimal
import io.github.mangi.eta.ui.screens.home.brief.briefEuro
import io.github.mangi.eta.ui.screens.home.brief.briefPortfolioChange
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.mangi.eta.ui.screens.home.brief.briefValueChange
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.BankCards
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefFinanceCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
) {
    BriefCard(BriefCardId.FINANCE, expanded, onToggleDensity, motionAllowed, modifier, "Budget du mois") {
        BudgetSummary(motionAllowed)
        if (expanded) {
            BudgetCategories()
            RecentExpenses()
        }
        UpcomingPayments()
        Portfolio(expanded)
    }
}

@Composable
private fun BudgetSummary(motionAllowed: Boolean) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val spent = demo.budgetCategories.sumOf { it.cents }
    val pace = briefBudgetPace(spent, demo.budgetCents, demo.budgetDay, demo.budgetDays)
    val tint = if (pace.aheadOfPace) colors.orange else colors.green
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Dépensé en novembre", style = styles.bodyXS, color = colors.labelSecondary)
            Text(briefEuro(spent, decimals = 0), style = styles.headlineL, color = colors.labelPrimary)
            Text("sur ${briefEuro(demo.budgetCents, decimals = 0)}", style = styles.bodyS, color = colors.labelSecondary)
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${briefDecimal(pace.differencePoints, decimals = 0, signed = true)} pts vs rythme",
                modifier = Modifier.squircleSurface(tint.copy(alpha = .14f), cornerRadius = 8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = styles.bodyXS,
                color = briefTintedLabel(tint),
                textAlign = TextAlign.End,
            )
            Text(
                if (pace.remainingCents >= 0) "${briefEuro(pace.remainingCents, decimals = 0)} restants"
                else "${briefEuro(-pace.remainingCents, decimals = 0)} de dépassement",
                style = styles.bodyXS, color = colors.labelSecondary, textAlign = TextAlign.End,
            )
        }
    }
    BriefGauge(
        pace.spentFraction.toFloat().coerceIn(0f, 1f), listOf(tint), motionAllowed,
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp).height(8.dp),
        description = "Budget utilisé à ${briefDecimal(pace.spentFraction * 100, decimals = 0)} %, " +
            "rythme attendu à ${briefDecimal(pace.elapsedFraction * 100, decimals = 0)} %",
        marker = pace.elapsedFraction.toFloat(),
    )
    Text("Jour ${demo.budgetDay} sur ${demo.budgetDays}", style = styles.bodyXS, color = colors.labelSecondary)
}

@Composable
private fun BudgetCategories() {
    val categories = BriefDemoData.budgetCategories
    val total = categories.sumOf { it.cents }
    val tints = categories.map { it.tone.color() }
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Par catégorie")
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        val gap = 2.dp.toPx()
        val width = (size.width - gap * categories.lastIndex).coerceAtLeast(0f)
        var x = 0f
        categories.forEachIndexed { index, category ->
            val segment = width * (category.cents.toFloat() / total)
            drawRect(tints[index], Offset(x, 0f), Size(segment, size.height))
            x += segment + gap
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        categories.forEachIndexed { index, category ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(tints[index], CircleShape))
                Text("${category.label} ${briefEuro(category.cents, decimals = 0)}", style = styles.bodyXS, color = colors.labelSecondary)
            }
        }
    }
}

@Composable
private fun RecentExpenses() {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Dépenses récentes")
    BriefDemoData.recentExpenses.forEachIndexed { index, expense ->
        if (index > 0) BriefRowDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefIconTile(COUIIcons.BankCards, expense.tone.color(), 32.dp)
            Column(Modifier.weight(1f)) {
                Text(expense.label, style = styles.headlineXS, color = colors.labelPrimary)
                Text(expense.detail, style = styles.bodyXS, color = colors.labelSecondary)
            }
            Text(briefEuro(-expense.cents), style = styles.bodyS, color = colors.labelPrimary)
        }
    }
}

@Composable
private fun UpcomingPayments() {
    val payments = BriefDemoData.upcomingPayments
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Prochains prélèvements", briefEuro(payments.sumOf { it.cents }))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        payments.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { payment ->
                    val tint = payment.tone.color()
                    Column(
                        Modifier.weight(1f).briefInset(14.dp)
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(payment.detail, style = styles.bodyXS, color = briefTintedLabel(tint))
                        Text(briefEuro(payment.cents), style = styles.headlineXS, color = colors.labelPrimary)
                        Text(payment.label, style = styles.bodyXS, color = colors.labelSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Portfolio(expanded: Boolean) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val positions = BriefDemoData.portfolio
    val total = positions.sumOf { it.valueCents }
    val variation = briefPortfolioChange(positions)
    var period by rememberSaveable { mutableStateOf(BriefPortfolioPeriod.Month) }
    val activePeriod = if (expanded) period else BriefPortfolioPeriod.Month
    val history = remember(activePeriod) { BriefDemoData.portfolioHistory(activePeriod) }
    val series = remember(history) { history.map(Long::toFloat) }
    val chartTint = briefChangeTint(history.last() - history.first())
    val description = "Valeur du portefeuille ${activePeriod.description}, de ${briefEuro(history.first())} à ${briefEuro(history.last())}"
    BriefSection("Bourse")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(briefEuro(total), style = if (expanded) styles.headlineL else styles.headlineS, color = colors.labelPrimary)
            Text(
                "${briefEuro(variation.cents, signed = true)} · ${variation.percentText()} aujourd’hui",
                style = styles.bodyXS, color = briefTintedLabel(briefChangeTint(variation.cents)),
            )
        }
        if (!expanded) BriefSparkline(series, chartTint, description, Modifier.width(88.dp).height(40.dp))
    }
    if (expanded) {
        CouiChipTabs(PortfolioTabs, period, { period = it }, Modifier.padding(top = 6.dp))
        BriefSparkline(series, chartTint, description, Modifier.fillMaxWidth().height(84.dp))
        BriefSection("Positions")
        positions.forEachIndexed { index, position ->
            if (index > 0) BriefRowDivider()
            PortfolioPosition(position)
        }
    }
}

private val PortfolioTabs = BriefPortfolioPeriod.entries.map { it to it.label }

@Composable
private fun PortfolioPosition(position: BriefDemoPosition) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val change = briefValueChange(position.valueCents, position.previousValueCents)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(position.name, style = styles.headlineXS, color = colors.labelPrimary)
            Text("${position.ticker} · ${position.quantity} parts", style = styles.bodyXS, color = colors.labelSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(briefEuro(position.valueCents), style = styles.bodyS, color = colors.labelPrimary)
            Text(change.percentText(), style = styles.bodyXS, color = briefTintedLabel(briefChangeTint(change.cents)))
        }
    }
}

private fun BriefValueChange.percentText(): String =
    percent?.let { "${briefDecimal(it, decimals = 2, signed = true)} %" } ?: "—"

@Composable
private fun briefChangeTint(change: Long): Color = with(COUITheme.colorScheme) {
    when {
        change > 0 -> green
        change < 0 -> red
        else -> labelSecondary
    }
}
