package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

// Cuisine glyphs absent from COUIIcons, drawn on its 24 dp grid.
internal object CuisineIcons {
    val Utensils = outline("Ustensiles", "M5 4v5a3 3 0 0 0 6 0V4M8 4v16M18 4c-2 1-3 3-3 6v3h3m0-9v16")
    val Basket = outline("Panier", "M3 9h18l-2 10H5L3 9m4 0 4-5m6 5-4-5m-4 8v4m6-4v4")
    val Cart = outline("Courses", "M3 5h3l2 10h10l2-7H7m3 10a1 1 0 1 0 0 2 1 1 0 0 0 0-2m7 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2")
    val Fridge = outline("Frigo", "M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2m-2 7h12M9 6v1m0 6v2")
    val Freezer = outline("Congélateur", "M12 3v18m-8-13 16 8M4 16l16-8M9 5l3 3 3-3m-6 14 3-3 3 3M5 11l4-1-1-4m11 7-4 1 1 4M5 13l4 1-1 4m11-7-4-1 1-4")
    val Pantry = outline("Placard", "M5 4h14v16H5zm0 9h14m-9-5h4m-4 8h4")
    val Pot = outline("Casserole", "M5 10h14v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4zm-3 1h3m14 0h3M9 4v3m6-3v3")
    val Flame = outline("Feu", "M12 3c.4 3.2-4 5-4 9.5a4 4 0 0 0 8 0c0-1.5-.6-2.6-1.5-3.5 0 1.5-.7 2.2-1.5 2.2C12.8 10 12.8 6 12 3zM12 20c-1.2 0-2-.9-2-2 0-1.3 1.2-1.8 2-3.2.8 1.4 2 1.9 2 3.2 0 1.1-.8 2-2 2z")
    val Sparkle = outline("Assistant", "M12 3.5c.6 4.2 2.3 5.9 6.5 6.5-4.2.6-5.9 2.3-6.5 6.5-.6-4.2-2.3-5.9-6.5-6.5 4.2-.6 5.9-2.3 6.5-6.5zm6.5 13c.2 1.4.8 2 2 2.2-1.2.2-1.8.8-2 2.1-.2-1.3-.8-1.9-2-2.1 1.2-.2 1.8-.8 2-2.2z")
    val Warning = outline("Alerte", "M12 4 3 20h18L12 4zm0 6v4m0 3h.01")
    val Minus = outline("Moins", "M6 12h12")
    val Swap = outline("Déplacer", "M4 8h15m-4-4 4 4-4 4M20 16H5m4-4-4 4 4 4")
    val Offline = outline("Hors connexion", "M3 8a15 15 0 0 1 18 0M7 12a8 8 0 0 1 10 0m-7 4a3 3 0 0 1 4 0m-2 4h.01M4 4l16 16")
    val ChevronDown = outline("Déplier", "m6.5 9.5 5.5 5.5 5.5-5.5")
    val ChevronUp = outline("Replier", "m6.5 14.5 5.5-5.5 5.5 5.5")

    private fun outline(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()
}

@Composable
internal fun cuisineTint(ink: Color): Color {
    val colors = COUITheme.colorScheme
    return lerp(ink, colors.labelPrimary.copy(alpha = 1f), if (COUITheme.isDark) 0.24f else 0.52f)
}

@Composable
internal fun CuisineBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ink: Color = COUITheme.colorScheme.labelPrimary,
    fill: Color = COUITheme.colorScheme.container8,
    compact: Boolean = false,
) {
    Surface(modifier, shape = SquircleShape(24.dp), color = fill, contentColor = ink) {
        Row(
            Modifier.heightIn(min = if (compact) 24.dp else 30.dp)
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(if (compact) 14.dp else 16.dp), tint = ink)
            Text(text, style = if (compact) COUITheme.textStyles.bodyXS else COUITheme.textStyles.bodyS, color = ink)
        }
    }
}

@Composable
internal fun CuisineIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    fill: Color = Color.Unspecified,
    ink: Color = COUITheme.colorScheme.labelPrimary,
) {
    IconButton(
        onClick, modifier.size(size).semantics { contentDescription = label },
        enabled = enabled, material = false, backgroundColor = fill, cornerRadius = size / 2,
        minHeight = size, minWidth = size,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) ink else COUITheme.colorScheme.disabledNeutral)
    }
}

@Composable
internal fun CuisineMessage(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val colors = COUITheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(28.dp), tint = if (isError) colors.error else colors.labelSecondary)
        Text(title, style = COUITheme.textStyles.headlineXS, textAlign = TextAlign.Center,
            color = if (isError) cuisineTint(colors.error) else colors.labelPrimary)
        if (detail != null) Text(detail, style = COUITheme.textStyles.bodyS, color = colors.labelSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) Button(onRetry, minWidth = 48.dp, minHeight = 48.dp) {
            Text(stringResource(R.string.food_retry), style = COUITheme.textStyles.buttonM)
        }
    }
}

@Composable
internal fun CuisineLoading(modifier: Modifier = Modifier, lines: Int = 2) {
    val label = stringResource(R.string.food_loading)
    Column(
        modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        repeat(lines) { index ->
            Surface(
                Modifier.fillMaxWidth(if (index == 0) 0.72f else 0.48f).height(9.dp),
                shape = SquircleShape(6.dp), color = COUITheme.colorScheme.container8,
            ) {}
        }
    }
}
