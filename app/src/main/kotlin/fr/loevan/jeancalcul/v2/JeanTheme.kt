package fr.loevan.jeancalcul.v2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme

internal object JeanTokens {
    val edge = 16.dp
    val elevation = 6.dp
    val capsule = RoundedCornerShape(28.dp)
    val outgoing = RoundedCornerShape(22.dp, 6.dp, 22.dp, 22.dp)
    val incoming = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp)
    @Composable fun userBubble() = if (CouiTheme.isDark) Color(0xFF183858) else Color(0xFFE4EFFB)
}

@Composable
internal fun JeanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) CouiPresets.Dark.copy(
        background = Color(0xFF101112), surface = Color(0xFF222426), card = Color(0xFF222426),
        onSurfaceVariant = Color(0xFFB5B9BE),
    ) else CouiPresets.Light.copy(
        background = Color(0xFFF7F7F7), onSurfaceVariant = Color(0xFF5C6168),
    )
    CouiTheme(darkTheme = dark, colorScheme = colors, content = content)
}
