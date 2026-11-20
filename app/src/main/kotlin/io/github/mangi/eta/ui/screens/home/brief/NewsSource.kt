package io.github.mangi.eta.ui.screens.home.brief

import java.net.URI
import java.time.Instant

internal enum class NewsCategory(val label: String, val tone: BriefDemoTone) {
    Tech("IA & tech", BriefDemoTone.Purple),
    Economy("Économie", BriefDemoTone.Green),
    Local("Actualité", BriefDemoTone.Red),
}

internal data class NewsArticle(
    val title: String,
    val source: String,
    val url: URI,
    val date: Instant,
    val category: NewsCategory,
    val readingMinutes: Int,
    val summary: List<String>,
    val image: URI? = null,
)

internal fun interface NewsSource {
    suspend fun articles(): List<NewsArticle>
}

internal object DemoNewsSource : NewsSource {
    override suspend fun articles(): List<NewsArticle> = BriefDemoData.news
}
