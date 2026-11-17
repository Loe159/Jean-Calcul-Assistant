package io.github.mangi.eta.ui.screens.home

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Pure presentation preference. Does not change the wallpaper, filtering policy or data.
 * Unknown/stale saved values fall back to the closest native COUI dark style.
 */
internal enum class NowBriefCardStyle(val label: String, val detail: String) {
    ETA("A · ETA fidèle", "Sombre, sobre et proche des panneaux ETA"),
    GLASS("B · ETA Glass", "Reflets fins et profondeur, lisibilité renforcée"),
    DASHBOARD("C · ETA Dashboard", "Contraste élevé, lecture rapide et dense");

    companion object {
        fun parse(value: String?): NowBriefCardStyle =
            entries.firstOrNull { it.name == value } ?: ETA
    }
}

internal object NowBriefStyleSettings {
    private const val PREFERENCES = "now_brief_appearance"
    private const val STYLE = "card_style_v1"

    fun read(context: Context): NowBriefCardStyle =
        NowBriefCardStyle.parse(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(STYLE, null))

    fun save(context: Context, style: NowBriefCardStyle) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(STYLE, style.name).apply()
    }
}

internal data class NowBriefGlassPalette(
    val stops: List<Color>,
    val rimStops: List<Color>,
    val rimWidthDp: Float,
    val cornerDp: Int,
    val metricColor: Color,
)

internal fun nowBriefGlassPalette(style: NowBriefCardStyle, dark: Boolean, prominent: Boolean = false): NowBriefGlassPalette {
    val boost = if (prominent) 0.025f else 0f
    return when (style) {
        NowBriefCardStyle.ETA -> NowBriefGlassPalette(
            stops = if (dark) listOf(Color(0xFF161719).copy(alpha = 0.985f),
                Color(0xFF1C1D20).copy(alpha = 0.98f))
                else listOf(Color(0xFFF5F6F8).copy(alpha = 0.96f), Color(0xFFE9ECF0).copy(alpha = 0.95f)),
            rimStops = if (dark) listOf(Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.025f), Color.White.copy(alpha = 0.07f))
                else listOf(Color.White.copy(alpha = 0.86f), Color(0xFF7F8899).copy(alpha = 0.13f)),
            rimWidthDp = 0.5f,
            cornerDp = if (prominent) 30 else 28,
            metricColor = if (dark) Color(0xFF404550).copy(alpha = 0.65f)
                else Color.White.copy(alpha = 0.72f),
        )
        NowBriefCardStyle.GLASS -> NowBriefGlassPalette(
            stops = if (dark) listOf(Color(0xFF3D4658).copy(alpha = 0.89f + boost),
                Color(0xFF222A39).copy(alpha = 0.86f + boost),
                Color(0xFF273047).copy(alpha = 0.88f + boost))
                else listOf(Color.White.copy(alpha = 0.88f + boost),
                    Color(0xFFE8EDF6).copy(alpha = 0.83f + boost)),
            rimStops = if (dark) listOf(Color.White.copy(alpha = 0.38f),
                Color(0xFF91D8CB).copy(alpha = 0.15f), Color(0xFFFFCCAD).copy(alpha = 0.25f))
                else listOf(Color.White.copy(alpha = 0.9f), Color(0xFF9BA9C8).copy(alpha = 0.37f)),
            rimWidthDp = 1.0f,
            cornerDp = if (prominent) 27 else 24,
            metricColor = if (dark) Color(0xFF586379).copy(alpha = 0.56f)
                else Color.White.copy(alpha = 0.75f),
        )
        NowBriefCardStyle.DASHBOARD -> NowBriefGlassPalette(
            stops = if (dark) listOf(Color(0xFF181C25).copy(alpha = 0.97f),
                Color(0xFF202530).copy(alpha = 0.96f))
                else listOf(Color(0xFFE9EEF5).copy(alpha = 0.98f),
                    Color(0xFFEDF0F5).copy(alpha = 0.97f)),
            rimStops = if (dark) listOf(Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.04f))
                else listOf(Color.White.copy(alpha = 0.8f), Color(0xFF8D96A8).copy(alpha = 0.12f)),
            rimWidthDp = 0.7f,
            cornerDp = if (prominent) 21 else 19,
            metricColor = if (dark) Color(0xFF353C4B).copy(alpha = 0.68f)
                else Color.White.copy(alpha = 0.75f),
        )
    }
}
