package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.DemoNewsSource
import io.github.mangi.eta.ui.screens.home.brief.NewsArticle
import io.github.mangi.eta.ui.screens.home.brief.NewsCategory
import io.github.mangi.eta.ui.screens.home.brief.NewsSource
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.ChipTabRow
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.random.Random

private val NewsTabs = listOf("Pour toi") + NewsCategory.entries.map { it.label }

@Composable
internal fun BriefNewsCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    source: NewsSource = DemoNewsSource,
) {
    val articles by produceState<List<NewsArticle>?>(null, source) {
        value = source.articles().sortedByDescending { it.date }
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var summaryUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val category = NewsCategory.entries.getOrNull(selected - 1)
    val filtered = remember(articles, category) {
        articles?.filter { category == null || it.category == category }
    }

    BriefCard(BriefCardId.NEWS, expanded, onToggleDensity, motionAllowed, modifier, "Sélection du jour") {
        ChipTabRow(
            tabs = NewsTabs,
            selectedTabIndex = selected,
            onTabSelected = { selected = it },
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        )
        val featured = filtered?.firstOrNull()
        if (featured != null) {
            FeaturedArticle(featured)
            val url = featured.url.toString()
            val summaryOpen = summaryUrl == url
            if (featured.summary.isNotEmpty()) {
                BriefDemoButton(
                    if (summaryOpen) "Masquer le résumé" else "Résumé en 20 secondes",
                    onClick = { summaryUrl = if (summaryOpen) null else url },
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (summaryOpen) ArticleSummary(featured.summary)
            }
            val others = filtered.drop(1).let { if (expanded) it else it.take(2) }
            others.forEachIndexed { index, article ->
                if (index > 0) BriefRowDivider()
                ArticleRow(article)
            }
        } else if (filtered != null) {
            Text(
                "Aucun article dans cette sélection",
                modifier = Modifier.padding(vertical = 12.dp),
                style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
            )
        }
    }
}

@Composable
private fun FeaturedArticle(article: NewsArticle) {
    val styles = COUITheme.textStyles
    val tint = article.category.tone.color()
    Box(
        Modifier.fillMaxWidth().padding(top = 5.dp)
            .squircleSurface(tint, cornerRadius = 18.dp)
            .newsArtwork(tint, article.url.hashCode(), scrim = true)
            .heightIn(min = 162.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(article.category.label, style = styles.bodyXS, color = Color.White.copy(alpha = .86f))
            Text(article.title, style = styles.headlineS, color = Color.White)
            Text("${article.source} · ${article.readingMinutes} min de lecture", style = styles.bodyXS, color = Color.White.copy(alpha = .86f))
        }
    }
}

@Composable
private fun ArticleRow(article: NewsArticle) {
    val tint = article.category.tone.color()
    val styles = COUITheme.textStyles
    val colors = COUITheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).squircleSurface(tint, cornerRadius = 13.dp).newsArtwork(tint, article.url.hashCode()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(article.category.label, style = styles.bodyXS, color = briefTintedLabel(tint))
            Text(article.title, style = styles.headlineXS, color = colors.labelPrimary)
            Text("${article.source} · ${article.readingMinutes} min", style = styles.bodyXS, color = colors.labelSecondary)
        }
    }
}

@Composable
private fun ArticleSummary(lines: List<String>) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Column(
        Modifier.fillMaxWidth().padding(bottom = 5.dp)
            .briefInset(14.dp).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("L’essentiel", style = styles.headlineXS, color = colors.labelPrimary)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•", style = styles.bodyS, color = briefTintedLabel(colors.purple))
                Text(line, Modifier.weight(1f), style = styles.bodyS, color = colors.labelPrimary)
            }
        }
    }
}

private fun Modifier.newsArtwork(tint: Color, seed: Int, scrim: Boolean = false): Modifier = drawWithCache {
    val random = Random(seed)
    val circles = List(5) {
        val center = Offset(size.width * random.nextFloat(), size.height * random.nextFloat() * .8f)
        val radius = size.minDimension * (.17f + random.nextFloat() * .25f)
        center to radius
    }
    val base = Brush.linearGradient(listOf(lerp(tint, Color.Black, .24f), lerp(tint, Color.Black, .82f)))
    val ribbon = Path().apply {
        moveTo(0f, size.height * .43f)
        cubicTo(size.width * .26f, size.height * .16f, size.width * .56f, size.height * .86f, size.width, size.height * .23f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    val shade = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .05f),
        .35f to Color.Black.copy(alpha = .52f),
        1f to Color.Black.copy(alpha = .82f),
    )
    onDrawBehind {
        drawRect(base)
        circles.forEachIndexed { index, (center, radius) ->
            val color = lerp(tint, Color.White, .10f + index * .12f)
            drawCircle(color.copy(alpha = .26f), radius, center)
            drawCircle(Color.White.copy(alpha = .24f), 1.5.dp.toPx(), center)
        }
        drawPath(ribbon, Color.Black.copy(alpha = .22f))
        if (scrim) drawRect(shade)
    }
}
